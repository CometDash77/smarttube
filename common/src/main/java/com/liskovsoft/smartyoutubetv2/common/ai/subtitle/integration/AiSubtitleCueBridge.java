package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionSnapshot;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import java.util.ArrayList;
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
 * state) lives in a {@link TranslationSession}; a video, track, or profile identity change
 * creates a new session generation and drops owned state, while a seek only advances the
 * scheduling epoch. The in-memory result lookup is still source-text-keyed and is replaced
 * by timeline/segment identity in M03-C3.</p>
 */
public class AiSubtitleCueBridge {
    private static AiSubtitleCueBridge sInstance;
    private static Context sContext;

    private static final String UNKNOWN_VIDEO_ID = "video:unknown";
    private static final String UNKNOWN_TRACK_ID = "track:unknown";
    private static final String UNKNOWN_LANGUAGE = "und";

    /**
     * M03 placeholder resolution. The real Provider/Prompt resolution arrives with M04/M05;
     * until then the bridge runs against one deterministic default profile so session and
     * cache identity stay well-defined for the current vertical slice.
     */
    private static final TranslationProfile DEFAULT_PROFILE =
            new TranslationProfile("pending-profile", "pending-model", "pending-prompt", 1, "zh");

    private final EnableState mEnabledState;
    private final TranslationProvider mProvider;
    private final Map<String, String> mCompleted = new HashMap<>();
    private final Map<String, PendingRequest> mInFlight = new HashMap<>();

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
        this(() -> AiSubtitleData.instance(context).isEnabled(), provider);
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
        mProfile = profile;
    }

    public static synchronized AiSubtitleCueBridge instance(Context context) {
        Context appContext = context.getApplicationContext();

        if (sInstance == null || sContext != appContext) {
            sInstance = new AiSubtitleCueBridge(appContext, new FakeTranslationProvider());
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
        String completed = mCompleted.get(source);

        if (completed != null) {
            return completed;
        }

        TranslationSession session = mSession;

        if (session == null || session.isPaused() || mInFlight.containsKey(source)) {
            return null;
        }

        long requestId = ++mRequestIdSeed;
        long generation = session.getGeneration();
        long epoch = session.getEpoch();
        PendingRequest pending = new PendingRequest(requestId, generation, epoch, null);

        // Track before starting so a synchronous provider callback is still accepted.
        mInFlight.put(source, pending);

        TranslationRequest request = new TranslationRequest(generation, requestId, source, null, null);

        try {
            pending.mCall = mProvider.translate(request, new BridgeCallback(source, requestId, generation, epoch));
        } catch (Exception e) {
            mInFlight.remove(source);
            return null;
        }

        // The configured provider may complete synchronously (the production Fake does): its
        // callback has already passed every identity guard and populated the cache, so the
        // result can be consumed in this same call. Deferred providers return null here and
        // stay source-only until a later rendering opportunity.
        return mCompleted.get(source);
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
        mCompleted.clear();
        mSession = newSession(nextId);
    }

    private void dropSession() {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }

        cancelInFlight();
        mCompleted.clear();
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
        private final String mSource;
        private final long mRequestId;
        private final long mRequestGeneration;
        private final long mRequestEpoch;

        private BridgeCallback(String source, long requestId, long requestGeneration, long requestEpoch) {
            mSource = source;
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
                PendingRequest pending = mInFlight.get(mSource);
                TranslationSession session = mSession;

                // The callback applies only when the request still belongs to the active
                // session generation and scheduling epoch (request-owner identity) and the
                // result repeats the request identity it answers.
                if (pending == null
                        || pending.mRequestId != mRequestId
                        || session == null
                        || !session.owns(pending.mGeneration, pending.mEpoch)
                        || result.getGeneration() != mRequestGeneration
                        || result.getRequestId() != mRequestId) {
                    return;
                }

                mInFlight.remove(mSource);
                mCompleted.put(mSource, result.getTranslatedText());
            }
        }

        @Override
        public void onFailure(Throwable error) {
            synchronized (AiSubtitleCueBridge.this) {
                PendingRequest pending = mInFlight.get(mSource);

                if (pending != null && pending.mRequestId == mRequestId) {
                    mInFlight.remove(mSource);
                }
            }
        }
    }
}
