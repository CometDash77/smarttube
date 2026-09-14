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
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler.TranslationScheduler;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionSnapshot;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleDisplayMode;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SourceTimeline;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SmartTubeSubtitleSourceAdapter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renderer-facing bridge between SmartTube's decoded cue list and the AI subtitle feature.
 *
 * <p>{@code SubtitleManager} knows only {@link #process}; lifecycle methods are driven by
 * {@code AiSubtitleController}. The bridge owns no Activity, View, SubtitleView, Video, or
 * PlaybackPresenter reference. Request scheduling, retries, and request deduplication belong
 * to {@link TranslationScheduler}; the bridge only maps cues, lifecycle, and configuration.
 */
public class AiSubtitleCueBridge {
    private static AiSubtitleCueBridge sInstance;
    private static Context sContext;

    /** Minimum spacing between streamed-draft repaints; a final always repaints at once. */
    private static final long DRAFT_REFRESH_INTERVAL_MS = 100;

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
     * Placeholder resolution before a valid profile is selected; identity remains well-defined
     * and no request can pass through a null provider.
     */
    private static final TranslationProfile DEFAULT_PROFILE =
            new TranslationProfile("pending-profile", "pending-protocol", "pending-endpoint",
                    "pending-model", "pending-prompt", 1, "zh");

    private final EnableState mEnabledState;
    private final TranslationCache mCache = new InMemoryTranslationCache();
    private AiSubtitleDisplayMode mDisplayMode = AiSubtitleDisplayMode.BILINGUAL;
    private RefreshListener mRefreshListener;
    private TranslationProvider mProvider;
    private TranslationProfile mProfile = DEFAULT_PROFILE;
    /** Selected Prompt Profile content; frozen into each request when it is submitted. */
    private PromptProfile mPromptProfile;

    private String mVideoId;
    private String mVideoTitle = "";
    private String mVideoDescription = "";
    private boolean mContextEnabled;
    private boolean mStreamingEnabled;
    /** Wall clock of the last draft repaint; drafts are coalesced, finals never are. */
    private long mLastDraftRefreshMs = Long.MIN_VALUE;
    private SourceTrackId mSourceTrackId;
    private TranslationSession mSession;
    private TranslationScheduler mScheduler;
    private long mGenerationSeed;
    private boolean mWasEnabled;
    private long mLookaheadMs = 90_000;
    private long mThrottleMs = 30_000;
    private int mSegmentTargetChars = 60;
    private int mSegmentMaxChars = 200;
    private int mLongSentenceChars = 80;
    private boolean mTranslationFirst;
    /** Monotonic identity of the newest source load; older responses are discarded. */
    private long mLoadSequence;
    private long mDraftRefreshIntervalMs = DRAFT_REFRESH_INTERVAL_MS;

    /** Full normalized timeline for the active track; null until the source adapter completes. */
    private SourceTimeline mTimeline;
    /** Latest playback position in milliseconds; -1 until the first position event. */
    private long mPositionMs = -1;
    /** Optional source adapter; when null the bridge falls back to displayed-cue-only mapping. */
    private SmartTubeSubtitleSourceAdapter mSourceAdapter;

    /** Real state of the last cue decoration/request for status screens. */
    public enum RuntimeStatus {
        WAITING, TRANSLATING, TRANSLATED, FAILED
    }

    private RuntimeStatus mStatus = RuntimeStatus.WAITING;
    private String mLastError = "";

    /** Listener used to repaint the currently displayed cues when an async result arrives. */
    public interface RefreshListener {
        void onTranslationArrived();
    }

    /**
     * Minimum-SDK-safe enable-state seam. {@code java.util.function} types are API 24+, which
     * exceeds the app's API 17 floor.
     */
    interface EnableState {
        boolean isEnabled();
    }

    AiSubtitleCueBridge(Context context, TranslationProvider provider) {
        this(context, provider, DEFAULT_PROFILE);
    }

    AiSubtitleCueBridge(Context context, TranslationProvider provider,
                        TranslationProfile profile) {
        // The selected Prompt Profile is applied through onProviderChanged, together with the
        // provider, so the two can never disagree.
        this(() -> AiSubtitleData.instance(context).isEnabled(), provider, profile, null);
        AiSubtitleData data = AiSubtitleData.instance(context);
        mLookaheadMs = data.getLookaheadSeconds() * 1_000L;
        mThrottleMs = data.getScheduleThrottleSeconds() * 1_000L;
        mSegmentTargetChars = data.getSegmentTargetChars();
        mSegmentMaxChars = data.getSegmentMaxChars();
        mLongSentenceChars = data.getLongSentenceChars();
        mTranslationFirst = data.isTranslationFirst();
        mContextEnabled = data.isContextEnabled();
        mStreamingEnabled = data.isStreamingEnabled();
    }

    @VisibleForTesting
    AiSubtitleCueBridge(EnableState enabledState, TranslationProvider provider,
                        PromptProfile promptProfile) {
        this(enabledState, provider, DEFAULT_PROFILE, promptProfile);
    }

    @VisibleForTesting
    AiSubtitleCueBridge(EnableState enabledState, TranslationProvider provider,
                        TranslationProfile profile, PromptProfile promptProfile) {
        mEnabledState = enabledState;
        mProvider = provider;
        mProfile = profile != null ? profile : DEFAULT_PROFILE;
        mPromptProfile = promptProfile;
    }

    public static synchronized AiSubtitleCueBridge instance(Context context) {
        Context appContext = context.getApplicationContext();

        if (sInstance == null || sContext != appContext) {
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProfileResolver.Resolution resolved =
                    AiSubtitleRuntime.resolve(appContext);
            AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(appContext,
                    resolved.getProvider(), resolved.getProfile());
            bridge.onProviderChanged(resolved.getProvider(), resolved.getProfile(),
                    resolved.getPrompt());
            bridge.setDisplayMode(AiSubtitleData.instance(appContext).getDisplayMode());
            sInstance = bridge;
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
     * Decorates the already-normalized cue list on its way to the subtitle view. Returns the
     * input unchanged while no valid translation exists and never throws into the renderer.
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

                mStatus = RuntimeStatus.WAITING;
                mLastError = "";
                return processedSourceCues;
            }

            mWasEnabled = true;
            ensureActiveSession();

            List<Cue> decorated = null;

            for (int i = 0; i < processedSourceCues.size(); i++) {
                Cue cue = processedSourceCues.get(i);
                Cue result = decorate(cue);

                if (result != cue) {
                    if (decorated == null) decorated = new ArrayList<>(processedSourceCues);
                    decorated.set(i, result);
                }
            }

            return decorated != null ? decorated : processedSourceCues;
        } catch (Exception e) {
            return processedSourceCues;
        }
    }

    void onNewVideo(String videoId, String title, String description) {
        synchronized (this) {
            mVideoId = videoId;
            mVideoTitle = title != null ? title : "";
            mVideoDescription = description != null ? description : "";
            rebindSessionIfIdentityChanged();
        }
    }

    void onSubtitleTrackChanged(String trackIdentity) {
        synchronized (this) {
            mSourceTrackId = toSourceTrackId(mVideoId, trackIdentity);
            rebindSessionIfIdentityChanged();
            triggerTimelineLoad();
        }
    }

    void onProfileChanged(TranslationProfile profile) {
        synchronized (this) {
            if (profile == null) return;
            mProfile = profile;
            rebindSessionIfIdentityChanged();
        }
    }

    void onProviderChanged(TranslationProvider provider, TranslationProfile profile,
                           PromptProfile promptProfile) {
        synchronized (this) {
            if (profile == null || promptProfile == null) {
                mProvider = null;
                mProfile = DEFAULT_PROFILE;
                mPromptProfile = null;
                dropSession();
                return;
            }

            mProvider = provider;
            mProfile = profile;
            mPromptProfile = promptProfile;
            rebindSessionIfIdentityChanged();
        }
    }

    void onSeekDrag(long positionMs) {
        synchronized (this) {
            ensureActiveSession();
            if (positionMs >= 0) mPositionMs = positionMs;
            if (mScheduler != null) mScheduler.onSeekDrag(positionMs);
        }
    }

    void onSeek(long positionMs) {
        synchronized (this) {
            ensureActiveSession();
            if (mSession != null) mSession.advanceEpoch();
            if (positionMs >= 0) mPositionMs = positionMs;
            if (mScheduler != null) mScheduler.onPositionChanged(positionMs, monotonicNowMs());
        }
    }

    void onPause() {
        synchronized (this) {
            ensureActiveSession();
            mSession.pause();
            if (mScheduler != null) mScheduler.pause();
        }
    }

    void onPlay() {
        synchronized (this) {
            ensureActiveSession();
            mSession.resume();
            if (mScheduler != null) mScheduler.resume(mPositionMs, monotonicNowMs());
        }
    }

    void onRelease() {
        synchronized (this) {
            dropSession();
        }
    }

    public synchronized void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            dropSession();
            mWasEnabled = false;
        }
    }

    /**
     * Enables or disables the bounded context. Context changes the rendered instruction, so the
     * identity changes: this drops the current session and its cache rather than reusing them
     * under a key that no longer describes what was sent. The current cue is not re-requested
     * here; the next render picks the new mode up.
     */
    public synchronized void onContextEnabledChanged(boolean enabled) {
        if (mContextEnabled == enabled) return;

        mContextEnabled = enabled;

        if (mSession != null && !mSession.isClosed()) {
            rebindSessionIfIdentityChanged();
        }
    }

    @VisibleForTesting
    synchronized TranslationSessionSnapshot snapshotSession() {
        return mSession != null ? mSession.snapshot() : null;
    }

    /**
     * Applies a lookahead/throttle change. These only reshape the request window, so the
     * session, its cache, and any in-flight request are left alone.
     */
    public synchronized void onSchedulingChanged(long lookaheadMs, long throttleMs) {
        if (lookaheadMs < 0 || throttleMs < 0) return;
        if (lookaheadMs == mLookaheadMs && throttleMs == mThrottleMs) return;

        mLookaheadMs = lookaheadMs;
        mThrottleMs = throttleMs;

        if (mScheduler != null) {
            mScheduler.setLookaheadMs(lookaheadMs);
            mScheduler.setThrottleMs(throttleMs);
            mScheduler.onSettingsChanged(mPositionMs, monotonicNowMs());
        }
    }

    /**
     * Applies a segmentation change. The translated units, and therefore every cached
     * translation, are invalidated, so this cancels the old work, clears the cache and
     * reloads the source once with one parameter snapshot. The previous paused state is
     * restored instead of turning playback-active work back on.
     */
    public synchronized void onSegmentationChanged(int targetChars, int maxChars,
                                                   int longSentenceChars) {
        if (targetChars < 1 || maxChars < targetChars || longSentenceChars <= 0) return;
        if (targetChars == mSegmentTargetChars && maxChars == mSegmentMaxChars
                && longSentenceChars == mLongSentenceChars) {
            return;
        }

        boolean wasPaused = mSession != null && mSession.isPaused();

        mSegmentTargetChars = targetChars;
        mSegmentMaxChars = maxChars;
        mLongSentenceChars = longSentenceChars;

        dropSession();

        // Only an enabled feature with a usable provider may start work again.
        if (mProvider == null || !mEnabledState.isEnabled()) return;

        ensureActiveSession();
        if (wasPaused) mSession.pause();
        triggerTimelineLoad();
    }

    public synchronized void setSourceAdapter(SmartTubeSubtitleSourceAdapter adapter) {
        mSourceAdapter = adapter;

        if (adapter != null) {
            adapter.configureSegmentation(mSegmentTargetChars, mSegmentMaxChars,
                    mLongSentenceChars);
        }

        mTimeline = null;
        triggerTimelineLoad();
    }

    /**
     * Bilingual order for {@link AiSubtitleDisplayMode#BILINGUAL}. Order is presentation only:
     * the cached translation, the session identity, and any in-flight request are untouched.
     */
    public synchronized void setTranslationFirst(boolean translationFirst) {
        if (mTranslationFirst == translationFirst) return;

        mTranslationFirst = translationFirst;
        notifyTranslationArrived();
    }

    @VisibleForTesting
    synchronized boolean isTranslationFirst() {
        return mTranslationFirst;
    }

    /**
     * Enables or disables streamed drafts. Streaming only changes how a translation arrives, so
     * the session identity and the compatible final cache are kept; the active work is cancelled
     * and the current cue falls back to the original text until the next request completes.
     */
    public synchronized void onStreamingEnabledChanged(boolean enabled) {
        if (mStreamingEnabled == enabled) return;

        mStreamingEnabled = enabled;

        if (mScheduler != null) {
            mScheduler.setStreamingEnabled(enabled);
            mScheduler.onStreamingChanged(mPositionMs, monotonicNowMs());
        }
    }

    /** Manual retry entry; see {@link TranslationScheduler#retryFailed(long, long)}. */
    public synchronized void retryFailed() {
        if (mScheduler == null) return;

        mScheduler.retryFailed(mPositionMs, monotonicNowMs());
    }

    void onTimelineReady(long loadSequence, SourceTrackId trackId, SourceTimeline timeline) {
        synchronized (this) {
            if (loadSequence != mLoadSequence) return;

            if (trackId == null || timeline == null || mSourceTrackId == null
                    || !trackId.equals(mSourceTrackId)) {
                return;
            }

            mTimeline = timeline;
            if (mScheduler != null) {
                mScheduler.setTimeline(timeline);
                mScheduler.onPositionUpdate(mPositionMs, monotonicNowMs());
            }
        }

        notifyTranslationArrived();
    }

    void onTimelineFailed(long loadSequence, SourceTrackId trackId, String reason) {
        // Timeline stays null; the bridge continues with displayed-cue fallback.
    }

    void onPositionUpdate(long positionMs) {
        synchronized (this) {
            if (positionMs < 0) return;
            mPositionMs = positionMs;
            if (mScheduler != null) mScheduler.onPositionUpdate(positionMs, monotonicNowMs());
        }
    }

    /**
     * Changes how completed translations are presented. The listener refreshes the current
     * cue list immediately so mode switches are visible without waiting for the next cue.
     */
    public synchronized void setDisplayMode(AiSubtitleDisplayMode mode) {
        AiSubtitleDisplayMode previous = mDisplayMode;
        mDisplayMode = mode != null ? mode : AiSubtitleDisplayMode.BILINGUAL;

        if (previous != mDisplayMode) notifyTranslationArrived();
    }

    public synchronized void setRefreshListener(RefreshListener listener) {
        mRefreshListener = listener;
    }

    public synchronized RuntimeStatus getRuntimeStatus() {
        return mStatus;
    }

    public synchronized String getLastError() {
        return mLastError;
    }

    private Cue decorate(Cue cue) {
        if (cue == null) return cue;

        CharSequence text = cue.text;
        if (text == null || text.toString().trim().isEmpty()) return cue;

        if (mDisplayMode == AiSubtitleDisplayMode.SOURCE) {
            mStatus = RuntimeStatus.WAITING;
            return cue;
        }

        String source = text.toString();
        String translation = findOrRequest(source);

        if (translation == null) {
            if (mStatus != RuntimeStatus.FAILED) mStatus = RuntimeStatus.TRANSLATING;
            return cue;
        }

        if (mDisplayMode == AiSubtitleDisplayMode.TRANSLATION_ONLY) return new Cue(translation);
        if (mTranslationFirst) return new Cue(translation + "\n" + source);
        return new Cue(source + "\n" + translation);
    }

    private String findOrRequest(String source) {
        TranslationSession session = mSession;
        if (session == null || mProvider == null || mScheduler == null) return null;

        TranslationUnit unit = timelineUnitFor(session, source);
        if (unit == null) return null;

        String cached = mScheduler.getCachedTranslation(unit);
        if (cached != null) {
            mStatus = RuntimeStatus.TRANSLATED;
            mLastError = "";
            return cached;
        }

        // A streamed draft may only decorate the unit the player is showing right now; a draft
        // for a future unit must never take over the current picture.
        String draft = mScheduler.getDraftTranslation(unit);
        if (draft != null) {
            if (mStatus != RuntimeStatus.FAILED) mStatus = RuntimeStatus.TRANSLATING;
            return draft;
        }

        if (session.isPaused()) return null;

        mScheduler.requestCurrentUnit(unit, monotonicNowMs());

        String resolved = mScheduler.getCachedTranslation(unit);
        if (resolved != null) {
            mStatus = RuntimeStatus.TRANSLATED;
            mLastError = "";
        }

        return resolved;
    }

    private void triggerTimelineLoad() {
        SmartTubeSubtitleSourceAdapter adapter = mSourceAdapter;
        SourceTrackId trackId = mSourceTrackId;
        String videoId = mVideoId;

        if (adapter == null || trackId == null || isBlank(videoId)) return;

        final long loadSequence = ++mLoadSequence;

        adapter.load(videoId, trackId, new SmartTubeSubtitleSourceAdapter.Callback() {
            @Override
            public void onTimelineReady(SourceTrackId tid, SourceTimeline timeline) {
                AiSubtitleCueBridge.this.onTimelineReady(loadSequence, tid, timeline);
            }

            @Override
            public void onTimelineFailed(SourceTrackId tid, String reason) {
                AiSubtitleCueBridge.this.onTimelineFailed(loadSequence, tid, reason);
            }
        });
    }

    private void ensureActiveSession() {
        if (mSession == null || mSession.isClosed()) {
            mSession = newSession(buildSessionId());
            recreateScheduler();
        }
    }

    private TranslationSession newSession(TranslationSessionId sessionId) {
        TranslationSession session = new TranslationSession(sessionId, ++mGenerationSeed);
        session.markReady();
        session.markActive();
        return session;
    }

    private void recreateScheduler() {
        if (mScheduler != null) {
            mScheduler.close();
            mScheduler = null;
        }

        // Without a resolvable provider and prompt there is nothing to schedule; the bridge
        // stays in source-only mode instead of failing inside the scheduler constructor.
        if (mProvider == null || mPromptProfile == null) return;

        mScheduler = new TranslationScheduler(mSession, mProvider, mPromptProfile, mCache,
                new TranslationScheduler.Listener() {
                    @Override
                    public void onTranslationArrived() {
                        onSchedulerArrived();
                    }

                    @Override
                    public void onTranslationDraft() {
                        onSchedulerDraftArrived();
                    }

                    @Override
                    public void onTranslationFailed(String reason) {
                        onSchedulerFailed(reason);
                    }
                });
        mScheduler.setTimeline(mTimeline);
        mScheduler.setLookaheadMs(mLookaheadMs);
        mScheduler.setThrottleMs(mThrottleMs);
        mScheduler.setContextEnabled(mContextEnabled);
        mScheduler.setStreamingEnabled(mStreamingEnabled);
        mScheduler.setVideoMetadata(mVideoTitle, mVideoDescription);
    }

    private void onSchedulerArrived() {
        synchronized (this) {
            mStatus = RuntimeStatus.TRANSLATED;
            mLastError = "";
            mLastDraftRefreshMs = Long.MIN_VALUE;
        }

        notifyTranslationArrived();
    }

    /**
     * Repaints a streamed draft at a bounded rate. A dropped intermediate repaint costs nothing
     * because the draft is cumulative and the final result always repaints immediately.
     */
    private void onSchedulerDraftArrived() {
        long nowMs = monotonicNowMs();

        synchronized (this) {
            if (mLastDraftRefreshMs != Long.MIN_VALUE
                    && nowMs - mLastDraftRefreshMs < mDraftRefreshIntervalMs) {
                return;
            }

            mLastDraftRefreshMs = nowMs;
        }

        notifyTranslationArrived();
    }

    @VisibleForTesting
    synchronized void setDraftRefreshIntervalForTesting(long intervalMs) {
        mDraftRefreshIntervalMs = intervalMs < 0 ? 0 : intervalMs;
    }

    private void onSchedulerFailed(String reason) {
        synchronized (this) {
            mStatus = RuntimeStatus.FAILED;
            mLastError = reason != null ? reason : "";
        }

        notifyTranslationArrived();
    }

    private void rebindSessionIfIdentityChanged() {
        TranslationSessionId nextId = buildSessionId();
        TranslationSession current = mSession;

        if (current != null && !current.isClosed() && current.getSessionId().equals(nextId)) {
            return;
        }

        cancelSessionState();
        mCache.clear();
        mLoadSequence++;
        mSession = newSession(nextId);
        recreateScheduler();
    }

    private void dropSession() {
        mStatus = RuntimeStatus.WAITING;
        mLastError = "";

        cancelSessionState();
        mCache.clear();
        mLoadSequence++;
        mSession = null;
    }

    private void cancelSessionState() {
        mLastDraftRefreshMs = Long.MIN_VALUE;

        if (mSession != null) mSession.close();

        if (mScheduler != null) {
            mScheduler.close();
            mScheduler = null;
        }
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

            if (!candidate.isEmpty()) language = candidate;
        }

        return new SourceTrackId(effectiveVideoId, trackIdentity, language);
    }

    private void notifyTranslationArrived() {
        RefreshListener listener = mRefreshListener;

        if (listener != null) {
            try {
                listener.onTranslationArrived();
            } catch (Exception ignored) {
                // Rendering failure must not break translation lifecycle or callback delivery.
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static long monotonicNowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /**
     * Uses the normalized full timeline when it is available. A displayed cue never selects a
     * synthetic segment while the real time-based mapping is known.
     */
    private TranslationUnit timelineUnitFor(TranslationSession session, String source) {
        SourceTimeline timeline = mTimeline;

        if (timeline != null && mPositionMs >= 0) {
            return timeline.unitAt(mPositionMs);
        }

        return unitFor(session, source);
    }

    /**
     * Android-free fallback before the source adapter completes: one displayed cue maps to
     * one single-segment unit on the session's Source Track.
     */
    private static TranslationUnit unitFor(TranslationSession session, String source) {
        SubtitleSegmentId segmentId = new SubtitleSegmentId(
                session.getSessionId().getSourceTrackId(), 0);

        return new TranslationUnit(Collections.singletonList(segmentId), source);
    }
}
