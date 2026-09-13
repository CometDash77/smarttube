package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.InMemoryTranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCacheKey;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionSnapshot;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renderer-facing bridge between SmartTube's decoded cue list and the AI subtitle feature.
 *
 * <p>{@code SubtitleManager} knows only {@link #process}; lifecycle methods are driven by
 * {@code AiSubtitleController}. The bridge owns no Activity, View, SubtitleView, Video, or
 * PlaybackPresenter reference.</p>
 *
 * <p>M03 scope: lifecycle ownership (session identity, generation, scheduling epoch, pause
 * state) lives in a {@link TranslationSession}; results are stored in a session-scoped
 * {@link TranslationCache} keyed by the complete output identity of
 * {@link TranslationCacheKey}; requests and results carry session/request/unit identity and
 * only a final result may enter the cache. The translated unit currently maps one displayed
 * cue to one single-segment unit on the session's Source Track; the real normalized timeline
 * (segment indexes from the source pipeline) arrives with the source/segmentation
 * milestones.</p>
 */
public class AiSubtitleCueBridge {
    private static AiSubtitleCueBridge sInstance;
    private static Context sContext;

    private static final String UNKNOWN_VIDEO_ID = "video:unknown";
    private static final String UNKNOWN_TRACK_ID = "track:unknown";
    private static final String UNKNOWN_LANGUAGE = "und";

    /** M03 has no context builder yet (arrives with M08); identity stays explicit and empty. */
    private static final String CONTEXT_FINGERPRINT_NONE = "";

    /** Cache identity versions for the shipped M06 segmentation and boundary contracts. */
    private static final int SEGMENTATION_VERSION =
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.RuleSentenceBreaker.VERSION;
    private static final int BOUNDARY_VERSION =
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.BoundaryProtocol.VERSION;

    /**
     * M03 placeholder resolution. The real Provider/Prompt resolution arrives with M04/M05;
     * until then the bridge runs against one deterministic default profile so session and
     * cache identity stay well-defined for the current vertical slice.
     */
    private static final TranslationProfile DEFAULT_PROFILE =
            new TranslationProfile("pending-profile", "pending-protocol", "pending-endpoint",
                    "pending-model", "pending-prompt", 1, "zh");

    private final EnableState mEnabledState;
    private TranslationProvider mProvider;
    private final TranslationCache mCache = new InMemoryTranslationCache();
    private final Map<TranslationCacheKey, PendingRequest> mInFlight = new HashMap<>();

    private TranslationProfile mProfile = DEFAULT_PROFILE;
    private String mVideoId;
    private SourceTrackId mSourceTrackId;
    private TranslationSession mSession;
    private long mGenerationSeed;
    private long mRequestIdSeed;
    private boolean mWasEnabled;

    /**
     * Minimum-SDK-safe enable-state seam. {@code java.util.function} types are API 24+, which
     * exceeds the app's API 17 floor.
     */
    interface EnableState {
        boolean isEnabled();
    }

    /**
     * Production constructor: reads the persisted opt-in switch through the dedicated
     * settings store.
     */
    AiSubtitleCueBridge(Context context, TranslationProvider provider) {
        this(context, provider, DEFAULT_PROFILE);
    }

    AiSubtitleCueBridge(Context context, TranslationProvider provider,
                        TranslationProfile profile) {
        this(() -> AiSubtitleData.instance(context).isEnabled(), provider, profile);
    }

    /**
     * Android-free constructor used by unit tests; the exact visibility is reduced where
     * tests and integration permit.
     */
    @VisibleForTesting
    AiSubtitleCueBridge(EnableState enabledState, TranslationProvider provider) {
        this(enabledState, provider, DEFAULT_PROFILE);
    }

    @VisibleForTesting
    AiSubtitleCueBridge(EnableState enabledState, TranslationProvider provider, TranslationProfile profile) {
        mEnabledState = enabledState;
        mProvider = provider;
        mProfile = profile != null ? profile : DEFAULT_PROFILE;
    }

    public static synchronized AiSubtitleCueBridge instance(Context context) {
        Context appContext = context.getApplicationContext();

        if (sInstance == null || sContext != appContext) {
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProfileResolver.Resolution resolved =
                    AiSubtitleRuntime.resolve(appContext);
            sInstance = new AiSubtitleCueBridge(appContext,
                    resolved.getProvider(), resolved.getProfile());
            sContext = appContext;
        }

        return sInstance;
    }

    @VisibleForTesting
    static synchronized void resetInstanceForTesting() {
        sInstance = null;
        sContext = null;
    }

    /**
     * Decorates the already-normalized cue list on its way to the subtitle view.
     * Returns the input unchanged while no valid translation exists, and never throws
     * into the renderer. When the configured provider completes synchronously, the
     * decorated cue is returned on this same call.
     */
    public synchronized List<Cue> process(List<Cue> processedSourceCues) {
        try {
            if (processedSourceCues == null || processedSourceCues.isEmpty()) {
                return processedSourceCues;
            }

            if (!mEnabledState.isEnabled()) {
                if (mWasEnabled) {
                    dropSession();
                    mWasEnabled = false;
                }

                return processedSourceCues;
            }

            mWasEnabled = true;
            ensureActiveSession();

            List<Cue> decorated = null;

            for (int i = 0; i < processedSourceCues.size(); i++) {
                Cue cue = processedSourceCues.get(i);
                Cue result = decorate(cue);

                if (result != cue) {
                    if (decorated == null) {
                        decorated = new ArrayList<>(processedSourceCues);
                    }

                    decorated.set(i, result);
                }
            }

            return decorated != null ? decorated : processedSourceCues;
        } catch (Exception e) {
            // Feature exceptions must never break or alter the source-only caption path.
            return processedSourceCues;
        }
    }

    void onNewVideo(String videoId) {
        synchronized (this) {
            mVideoId = videoId;
            rebindSessionIfIdentityChanged();
        }
    }

    void onSubtitleTrackChanged(String trackIdentity) {
        synchronized (this) {
            mSourceTrackId = toSourceTrackId(mVideoId, trackIdentity);
            rebindSessionIfIdentityChanged();
        }
    }

    /**
     * Notifies the bridge that the resolved Translation Profile changed. M03 has no settings
     * integration yet (arrives with M04/M05); the seam exists so identity changes and stale
     * rejection are testable today.
     */
    void onProfileChanged(TranslationProfile profile) {
        synchronized (this) {
            if (profile == null) {
                return;
            }

            mProfile = profile;
            rebindSessionIfIdentityChanged();
        }
    }

    /**
     * Applies the resolved provider and its Translation Profile after settings change. A
     * null profile means resolution failed or no valid profile is selected: the bridge keeps
     * Source-Only Fallback and drops any previous provider/session state.
     */
    void onProviderChanged(TranslationProvider provider, TranslationProfile profile) {
        synchronized (this) {
            if (profile == null) {
                mProvider = null;
                mProfile = DEFAULT_PROFILE;
                dropSession();
                return;
            }

            mProvider = provider;
            mProfile = profile;
            rebindSessionIfIdentityChanged();
        }
    }

    void onSeek(long positionMs) {
        synchronized (this) {
            cancelInFlight();

            if (mSession != null) {
                mSession.advanceEpoch();
            }
        }
    }

    void onPause() {
        synchronized (this) {
            ensureActiveSession();
            mSession.pause();
        }
    }

    void onPlay() {
        synchronized (this) {
            ensureActiveSession();
            mSession.resume();
        }
    }

    void onRelease() {
        synchronized (this) {
            dropSession();
        }
    }

    /**
     * Explicit enable-state notification for the settings entry point. Disabling behaves like
     * an invalidating event: in-flight calls are cancelled and prior results are dropped
     * immediately, before the settings callback returns, without waiting for another cue.
     * Enabling requires no action here because {@link #process} admits new work on demand.
     */
    public synchronized void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            dropSession();
            mWasEnabled = false;
        }
    }

    /** Snapshot of the active session, or null while no session is active. */
    @VisibleForTesting
    synchronized TranslationSessionSnapshot snapshotSession() {
        return mSession != null ? mSession.snapshot() : null;
    }

    private Cue decorate(Cue cue) {
        if (cue == null) {
            return null;
        }

        CharSequence text = cue.text;

        if (text == null || text.toString().trim().isEmpty()) {
            return cue;
        }

        String source = text.toString();
        String translation = findOrRequest(source);

        if (translation == null) {
            return cue;
        }

        return new Cue(source + "\n" + translation);
    }

    private String findOrRequest(String source) {
        TranslationSession session = mSession;

        if (session == null) {
            return null;
        }

        if (mProvider == null) {
            return null;
        }

        TranslationUnit unit = unitFor(session, source);
        TranslationCacheKey key = keyFor(session, unit);
        TranslationResult cached = mCache.get(key);

        if (cached != null) {
            return cached.getTranslatedText();
        }

        if (session.isPaused() || mInFlight.containsKey(key)) {
            return null;
        }

        long requestId = ++mRequestIdSeed;
        long generation = session.getGeneration();
        long epoch = session.getEpoch();
        PendingRequest pending = new PendingRequest(requestId, generation, epoch, null);

        // Track before starting so a synchronous provider callback is still accepted.
        mInFlight.put(key, pending);

        TranslationRequest request = new TranslationRequest(session.getSessionId(), requestId, unit);

        try {
            pending.mCall = mProvider.translate(request,
                    new BridgeCallback(key, unit, requestId, generation, epoch));
        } catch (Exception e) {
            mInFlight.remove(key);
            return null;
        }

        // The configured provider may complete synchronously (the production Fake does): its
        // callback has already passed every identity guard and populated the cache, so the
        // result can be consumed in this same call. Deferred providers return null here and
        // stay source-only until a later rendering opportunity.
        TranslationResult completed = mCache.get(key);

        return completed != null ? completed.getTranslatedText() : null;
    }

    /**
     * Builds the cache identity for one translated unit under the given session: the session
     * identity plus the unit's own coverage and text fingerprint.
     */
    private static TranslationCacheKey keyFor(TranslationSession session, TranslationUnit unit) {
        return TranslationCacheKey.from(
                session.getSessionId(),
                unit,
                CONTEXT_FINGERPRINT_NONE,
                SEGMENTATION_VERSION,
                BOUNDARY_VERSION);
    }

    /**
     * M03 placeholder timeline identity: one displayed cue maps to one single-segment unit on
     * the session's Source Track. The track component already prevents identical text on two
     * tracks from aliasing; segment indexes from the normalized timeline arrive with the
     * source/segmentation milestones.
     */
    private static TranslationUnit unitFor(TranslationSession session, String source) {
        SubtitleSegmentId segmentId = new SubtitleSegmentId(
                session.getSessionId().getSourceTrackId(), 0);

        return new TranslationUnit(Collections.singletonList(segmentId), source);
    }

    private void ensureActiveSession() {
        if (mSession == null || mSession.isClosed()) {
            mSession = newSession(buildSessionId());
        }
    }

    private TranslationSession newSession(TranslationSessionId sessionId) {
        TranslationSession session = new TranslationSession(sessionId, ++mGenerationSeed);
        session.markReady();
        session.markActive();
        return session;
    }

    private void rebindSessionIfIdentityChanged() {
        TranslationSessionId nextId = buildSessionId();
        TranslationSession current = mSession;

        if (current != null && !current.isClosed() && current.getSessionId().equals(nextId)) {
            // Idempotent: a repeated identical lifecycle event must not create a new generation.
            return;
        }

        cancelInFlight();
        mCache.clear();
        mSession = newSession(nextId);
    }

    private void dropSession() {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }

        cancelInFlight();
        mCache.clear();
    }

    private TranslationSessionId buildSessionId() {
        String videoId = isBlank(mVideoId) ? UNKNOWN_VIDEO_ID : mVideoId;
        SourceTrackId trackId = mSourceTrackId != null ? mSourceTrackId : defaultTrackId(videoId);

        return new TranslationSessionId(videoId, trackId, mProfile,
                TranslationSessionId.ENGINE_SCHEMA_VERSION);
    }

    private static SourceTrackId defaultTrackId(String videoId) {
        return new SourceTrackId(videoId, UNKNOWN_TRACK_ID, UNKNOWN_LANGUAGE);
    }

    /**
     * Maps the controller's track-identity string onto a Source Track identity.
     * {@code "subtitle:none"} means subtitles are off and leaves the bridge without a
     * concrete track.
     */
    private static SourceTrackId toSourceTrackId(String videoId, String trackIdentity) {
        if (trackIdentity == null || AiSubtitleController.IDENTITY_NONE.equals(trackIdentity)) {
            return null;
        }

        String effectiveVideoId = isBlank(videoId) ? UNKNOWN_VIDEO_ID : videoId;
        String language = UNKNOWN_LANGUAGE;

        if (trackIdentity.startsWith("subtitle:")) {
            String rest = trackIdentity.substring("subtitle:".length());
            int separator = rest.indexOf(':');
            String candidate = separator >= 0 ? rest.substring(0, separator) : rest;

            if (!candidate.isEmpty()) {
                language = candidate;
            }
        }

        return new SourceTrackId(effectiveVideoId, trackIdentity, language);
    }

    private void cancelInFlight() {
        for (PendingRequest pending : mInFlight.values()) {
            if (pending.mCall != null) {
                try {
                    pending.mCall.cancel();
                } catch (Exception ignored) {
                    // Cancellation is best-effort; the identity guards above stay authoritative.
                }
            }
        }

        mInFlight.clear();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class PendingRequest {
        private final long mRequestId;
        private final long mGeneration;
        private final long mEpoch;
        private TranslationCall mCall;

        private PendingRequest(long requestId, long generation, long epoch, TranslationCall call) {
            mRequestId = requestId;
            mGeneration = generation;
            mEpoch = epoch;
            mCall = call;
        }
    }

    private final class BridgeCallback implements TranslationCallback {
        private final TranslationCacheKey mKey;
        private final TranslationUnit mUnit;
        private final long mRequestId;
        private final long mRequestGeneration;
        private final long mRequestEpoch;

        private BridgeCallback(TranslationCacheKey key, TranslationUnit unit, long requestId,
                               long requestGeneration, long requestEpoch) {
            mKey = key;
            mUnit = unit;
            mRequestId = requestId;
            mRequestGeneration = requestGeneration;
            mRequestEpoch = requestEpoch;
        }

        @Override
        public void onSuccess(TranslationResult result) {
            if (result == null) {
                return;
            }

            synchronized (AiSubtitleCueBridge.this) {
                PendingRequest pending = mInFlight.get(mKey);
                TranslationSession session = mSession;

                // The callback applies only when the request still owns the active session
                // generation and scheduling epoch, the result answers this exact request and
                // unit coverage, and only a final result may enter the cache.
                if (pending == null
                        || pending.mRequestId != mRequestId
                        || session == null
                        || !session.owns(pending.mGeneration, pending.mEpoch)
                        || !result.isFinal()
                        || !session.getSessionId().equals(result.getSessionId())
                        || result.getRequestId() != mRequestId
                        || !mUnit.getSegmentIds().equals(result.getSegmentIds())) {
                    return;
                }

                mInFlight.remove(mKey);
                mCache.put(mKey, result);
            }
        }

        @Override
        public void onFailure(TranslationFailure failure) {
            synchronized (AiSubtitleCueBridge.this) {
                PendingRequest pending = mInFlight.get(mKey);

                if (pending != null && pending.mRequestId == mRequestId) {
                    mInFlight.remove(mKey);
                }
            }
        }
    }
}
