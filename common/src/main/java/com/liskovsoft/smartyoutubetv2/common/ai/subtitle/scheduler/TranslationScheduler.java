package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCacheKey;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRenderer;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptVariable;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SourceTimeline;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationContextBuilder;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationStream;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Serial scheduler for the active Translation Session. It owns request state; the cue bridge
 * keeps rendering and lifecycle bridging. Listener calls are made after the scheduler lock
 * is released so a synchronous repaint cannot re-enter while state is being mutated.
 */
public final class TranslationScheduler {
    public interface Listener {
        void onTranslationArrived();
        /** A streamed draft for the unit that is still in flight; never final text. */
        void onTranslationDraft();
        void onTranslationFailed(String reason);
    }

    private enum WorkState { PENDING, IN_FLIGHT, WAITING_RETRY, SUCCEEDED, FAILED }

    private static final int ENGINE_SCHEMA_VERSION = TranslationSessionId.ENGINE_SCHEMA_VERSION;
    private static final int SEGMENTATION_VERSION =
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.RuleSentenceBreaker.VERSION;
    private static final int BOUNDARY_VERSION =
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.BoundaryProtocol.VERSION;
    private static final String CONTEXT_FINGERPRINT_NONE = "";
    private static final long[] BACKOFF_MS = {1_000, 2_000};
    /**
     * Trailing margin kept behind the playhead regardless of the lookahead, so the bounded
     * context still finds the last few translated units after a forward jump.
     */
    private static final long MIN_HISTORY_MARGIN_MS = 30_000;
    /** Upper bound on the earlier units examined for one request's context. */
    private static final int MAX_HISTORY_UNITS = TranslationContextBuilder.MAX_HISTORY_UNITS;

    private final TranslationSession mSession;
    private final TranslationProvider mProvider;
    private final TranslationCache mCache;
    private final Listener mListener;
    private final PromptProfile mPromptProfile;
    private final PromptRenderer mPromptRenderer = new PromptRenderer();
    private final TranslationContextBuilder mContextBuilder = new TranslationContextBuilder();
    /** Keyed by the context-free unit identity; each Work freezes its context-bearing key. */
    private final Map<TranslationCacheKey, Work> mWork = new HashMap<>();
    /**
     * Units by start time. A list per start keeps every unit when two share a start time; a
     * plain map would silently drop one of them.
     */
    private final TreeMap<Long, List<TranslationUnit>> mUnitsByStart = new TreeMap<>();
    private final Map<Integer, long[]> mSegmentTimes = new HashMap<>();
    private final Random mJitter = new Random();
    private final List<Runnable> mPendingEvents = new ArrayList<>();

    private SourceTimeline mTimeline;
    private String mVideoTitle = "";
    private String mVideoDescription = "";
    private boolean mContextEnabled;
    private Work mActive;
    private long mPositionMs = -1;
    private long mLookaheadMs = 90_000;
    private long mThrottleMs = 30_000;
    private long mLastWindowDispatchMs = Long.MIN_VALUE;
    private long mRequestIdSeed;
    private int mFirstAttempts;
    private int mRetryAttempts;
    private int mFallbackAttempts;
    private int mMaxAttempts = 3;
    private int mJitterRangeMs = 250;
    private boolean mStreamingEnabled;
    private boolean mClosed;
    private boolean mPaused;

    public TranslationScheduler(TranslationSession session, TranslationProvider provider,
                                PromptProfile promptProfile, TranslationCache cache,
                                Listener listener) {
        if (session == null) throw new IllegalArgumentException("session must not be null");
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        if (promptProfile == null) {
            throw new IllegalArgumentException("promptProfile must not be null");
        }
        if (cache == null) throw new IllegalArgumentException("cache must not be null");

        mSession = session;
        mProvider = provider;
        mPromptProfile = promptProfile;
        mCache = cache;
        mListener = listener;
    }

    public void setLookaheadMs(long lookaheadMs) {
        synchronized (this) {
            if (lookaheadMs < 0) throw new IllegalArgumentException("lookahead must not be negative");
            mLookaheadMs = lookaheadMs;
        }
    }

    public void setThrottleMs(long throttleMs) {
        synchronized (this) {
            if (throttleMs < 0) throw new IllegalArgumentException("throttle must not be negative");
            mThrottleMs = throttleMs;
        }
    }

    synchronized void setJitterRangeForTesting(int jitterRangeMs) {
        if (jitterRangeMs < 0) throw new IllegalArgumentException("jitter must not be negative");
        mJitterRangeMs = jitterRangeMs;
    }

    /** Video metadata for the bounded context; only later requests see a change. */
    public void setVideoMetadata(String title, String description) {
        synchronized (this) {
            mVideoTitle = title != null ? title : "";
            mVideoDescription = description != null ? description : "";
        }
    }

    /**
     * Enables the bounded context. This changes the output identity, so the caller is expected
     * to start a new session rather than flipping it on a live one.
     */
    public void setContextEnabled(boolean enabled) {
        synchronized (this) {
            mContextEnabled = enabled;
        }
    }

    /**
     * Enables streamed drafts. A request is streamed only while streaming is enabled and the
     * unit has not already fallen back from a transient streamed failure; every other case
     * keeps the single-response contract.
     */
    public void setStreamingEnabled(boolean enabled) {
        synchronized (this) {
            mStreamingEnabled = enabled;

            if (enabled) {
                // A user who re-enables streaming gets a fresh chance on units that fell back.
                for (Work work : mWork.values()) {
                    work.mStreamingDisabled = false;
                    work.mFallbackPending = false;
                }
            }
        }
    }

    /**
     * Re-evaluates work after the streaming switch. The request in flight is cancelled and its
     * draft cleared so the new mode applies immediately; a cancelled call returns to PENDING and
     * is re-dispatched under the new mode.
     *
     * <p>The cancelled request had already reached the provider, so it counts against the
     * unit's attempt budget exactly like any other issued request, and the re-dispatch spends
     * one more. The budget is what actually bounds the network: only the mode changes here, not
     * the number of requests a unit may cost.</p>
     */
    public void onStreamingChanged(long positionMs, long nowMs) {
        synchronized (this) {
            clearDraftsLocked();
            cancelTransientWorkLocked();

            if (positionMs >= 0) mPositionMs = positionMs;
            dispatch(nowMs, true);
        }

        drainEvents();
    }

    public void setTimeline(SourceTimeline timeline) {
        synchronized (this) {
            setTimelineLocked(timeline);
        }

        drainEvents();
    }

    public void onPositionUpdate(long positionMs, long nowMs) {
        synchronized (this) {
            if (positionMs < 0) return;
            mPositionMs = positionMs;
            dispatch(nowMs, false);
        }

        drainEvents();
    }

    public void onPositionChanged(long positionMs, long nowMs) {
        synchronized (this) {
            clearDraftsLocked();
            cancelTransientWorkLocked();

            if (positionMs >= 0) {
                mPositionMs = positionMs;
            }

            dispatch(nowMs, true);
        }

        drainEvents();
    }

    /** Saves the latest drag position and cancels old work without dispatching a request. */
    public void onSeekDrag(long positionMs) {
        synchronized (this) {
            if (positionMs >= 0) mPositionMs = positionMs;
            clearDraftsLocked();
            cancelTransientWorkLocked();
        }
    }

    public void pause() {
        synchronized (this) {
            mPaused = true;
            cancelTransientWorkLocked();
        }
    }

    public void resume(long positionMs, long nowMs) {
        synchronized (this) {
            mPaused = false;
            if (positionMs >= 0) mPositionMs = positionMs;
            dispatch(nowMs, true);
        }

        drainEvents();
    }

    /**
     * Re-evaluates the request window after a settings-only change. Unlike {@link #resume} this
     * never clears the paused state, so changing a limit while playback is paused cannot start
     * new work.
     */
    public void onSettingsChanged(long positionMs, long nowMs) {
        synchronized (this) {
            if (positionMs >= 0) mPositionMs = positionMs;
            dispatch(nowMs, true);
        }

        drainEvents();
    }

    /**
     * Manual retry entry. Only terminal failures still relevant to the playhead are re-queued —
     * see {@link #isInRetryWindow} for what that means exactly — and they get a fresh attempt
     * budget because the user asked explicitly; ordinary redraws never reset the budget.
     */
    public void retryFailed(long positionMs, long nowMs) {
        synchronized (this) {
            if (positionMs >= 0) mPositionMs = positionMs;

            long windowEnd = windowEnd(mPositionMs, mLookaheadMs);

            for (Work work : mWork.values()) {
                if (work.mState == WorkState.FAILED && isInRetryWindow(work.mUnit, windowEnd)) {
                    work.mState = WorkState.PENDING;
                    work.mAttempts = 0;
                    work.mFailureMessage = "";
                }
            }

            dispatch(nowMs, true);
        }

        drainEvents();
    }

    public void requestCurrentUnit(TranslationUnit unit, long nowMs) {
        synchronized (this) {
            requestCurrentUnitLocked(unit, nowMs);
        }

        drainEvents();
    }

    public synchronized String getCachedTranslation(TranslationUnit unit) {
        Work work = workForLookup(unit);

        // Nothing can be cached before the unit's first dispatch froze its request identity.
        if (work == null || work.mCacheKey == null) return null;

        TranslationResult result = mCache.get(work.mCacheKey);
        return result != null ? result.getTranslatedText() : null;
    }

    /** Latest streamed draft for a unit; never stored as final text or as history. */
    public synchronized String getDraftTranslation(TranslationUnit unit) {
        Work work = workForLookup(unit);
        return work != null ? work.mDraft : null;
    }

    public synchronized boolean hasFailed(TranslationUnit unit) {
        Work work = workForLookup(unit);
        return work != null && work.mState == WorkState.FAILED;
    }

    public synchronized String getFailureMessage(TranslationUnit unit) {
        Work work = workForLookup(unit);
        return work != null ? work.mFailureMessage : "";
    }

    /** Total first attempts issued; a diagnostic and test seam only. */
    public synchronized int getFirstAttemptCount() {
        return mFirstAttempts;
    }

    /** Attempts that followed a transient failure of the same unit; test seam only. */
    public synchronized int getRetryAttemptCount() {
        return mRetryAttempts;
    }

    /** Attempts that followed a streamed attempt falling back to a plain request. */
    public synchronized int getFallbackAttemptCount() {
        return mFallbackAttempts;
    }

    /** Number of live work records; a diagnostic for the long-video bound. */
    public synchronized int getWorkCount() {
        return mWork.size();
    }

    public synchronized long getRetryDueAtMsForTesting(TranslationUnit unit) {
        Work work = workForLookup(unit);
        return work != null ? work.mDueAtMs : -1;
    }

    public synchronized void close() {
        mClosed = true;
        clearDraftsLocked();
        cancelTransientWorkLocked();

        for (Work work : mWork.values()) {
            cancelCall(work);
        }

        // Nothing outlives the scheduler: work records, the time index, and every queued
        // notification are dropped, so a late partial, final, or error cannot dispatch, and a
        // drain that starts after this returns without delivering anything. A drain already past
        // its copy can still deliver its snapshot; that window closes when the bridge drops its
        // reference to this scheduler, which every caller does. The cancelled calls above
        // release their references too.
        mWork.clear();
        mUnitsByStart.clear();
        mSegmentTimes.clear();
        mActive = null;
        mPendingEvents.clear();
        mTimeline = null;
    }

    private void setTimelineLocked(SourceTimeline timeline) {
        mTimeline = timeline;
        mUnitsByStart.clear();
        mSegmentTimes.clear();

        if (timeline == null) return;

        for (SubtitleSegment segment : timeline.getSegments()) {
            mSegmentTimes.put(segment.getId().getIndex(), new long[] {
                    segment.getStartTimeMs(), segment.getEndTimeMs()
            });
        }

        for (TranslationUnit unit : timeline.getUnits()) {
            long start = startTime(unit);
            if (start < 0) continue;

            List<TranslationUnit> atStart = mUnitsByStart.get(start);

            if (atStart == null) {
                atStart = new ArrayList<>(2);
                mUnitsByStart.put(start, atStart);
            }

            atStart.add(unit);
        }
    }

    private void requestCurrentUnitLocked(TranslationUnit unit, long nowMs) {
        if (mClosed || mPaused || unit == null || mSession.isClosed() || mSession.isPaused()) return;

        // Every explicit request enters here, so this is where the no-timeline fallback is
        // bounded. dispatch() never sees it: without a timeline it returns before pruning.
        pruneUntimedWorkLocked(unit);

        Work work = workFor(unit);

        if (work.mState != WorkState.PENDING) return;

        if (mActive != null && !mActive.mKey.equals(work.mKey)) {
            cancelActiveLocked();
        }

        submitLocked(work, nowMs);
    }

    private void dispatch(long nowMs, boolean immediate) {
        if (mClosed || mPaused || mTimeline == null || mSession.isClosed() || mSession.isPaused()
                || mPositionMs < 0) {
            return;
        }

        long windowEnd = windowEnd(mPositionMs, mLookaheadMs);
        TranslationUnit current = mTimeline.unitAt(mPositionMs);

        if (current != null) {
            requestCurrentUnitLocked(current, nowMs);
        }

        boolean windowAllowed = immediate || mLastWindowDispatchMs == Long.MIN_VALUE
                || nowMs - mLastWindowDispatchMs >= mThrottleMs;

        if (windowAllowed) {
            pruneWorkLocked(windowEnd);
            fillFutureWindow(windowEnd, nowMs);
            mLastWindowDispatchMs = nowMs;
        }

        processDueRetries(nowMs, windowEnd);
    }

    private void fillFutureWindow(long windowEnd, long nowMs) {
        for (List<TranslationUnit> atStart :
                mUnitsByStart.subMap(mPositionMs, true, windowEnd, true).values()) {
            if (mActive != null) return;

            for (TranslationUnit unit : atStart) {
                if (mActive != null) return;

                Work work = workFor(unit);

                if (work.mState == WorkState.PENDING) submitLocked(work, nowMs);
            }
        }
    }

    /**
     * Drops terminal work whose unit has left the window, so a long video does not accumulate
     * one record per translated cue. Active, pending and retrying work is never touched.
     *
     * <p>A record whose result is still in the cache is kept: the frozen cache key lives on the
     * record, so dropping it early would make a cached translation unreachable and force a
     * needless re-request. That also bounds the map by the cache's own entry bound.</p>
     *
     * <p>The map's ceiling is therefore the cache's entry limit, plus the units still inside the
     * window, plus whatever the throttle interval added since the last prune, plus the one
     * request in flight. It is deliberately not "512 records": the cache is what holds finished
     * text, and the window is what bounds unfinished records.</p>
     */
    private void pruneWorkLocked(long windowEnd) {
        long earliestKept = mPositionMs - Math.max(mLookaheadMs, MIN_HISTORY_MARGIN_MS);

        List<TranslationCacheKey> expired = new ArrayList<>();

        for (Map.Entry<TranslationCacheKey, Work> entry : mWork.entrySet()) {
            Work work = entry.getValue();

            // Only the request in flight is untouchable. A pending or retrying unit that has
            // scrolled out of the window is dead weight; dropping it only means it can be
            // requested again if playback reaches it.
            if (work == mActive || work.mState == WorkState.IN_FLIGHT) {
                continue;
            }

            long start = startTime(work.mUnit);
            if (start < 0) continue;
            if (start <= windowEnd && start >= earliestKept) continue;

            if (isResultStillCached(work)) continue;

            expired.add(entry.getKey());
        }

        for (TranslationCacheKey key : expired) {
            mWork.remove(key);
        }
    }

    /**
     * Bounds the display-cue fallback, where a unit has no start time and the window prune has
     * nothing to compare against. Of the records with no start time, only the cue being
     * requested, the request in flight, and any record whose result is still cached survive;
     * the rest have left the visible cue and would otherwise stay forever. Records that do have
     * a start time are not this method's business and are left to {@link #pruneWorkLocked}.
     *
     * <p>The cue being requested is kept whatever its state, so a failed cue is not silently
     * re-created on the next redraw and cannot spend a second budget in a loop. It stops being
     * the current cue as soon as a different one is requested, which is also when it becomes
     * eligible for removal.</p>
     */
    private void pruneUntimedWorkLocked(TranslationUnit current) {
        TranslationCacheKey currentKey = identityFor(current);
        List<TranslationCacheKey> expired = new ArrayList<>();

        for (Map.Entry<TranslationCacheKey, Work> entry : mWork.entrySet()) {
            Work work = entry.getValue();

            if (entry.getKey().equals(currentKey)) continue;
            if (work == mActive || work.mState == WorkState.IN_FLIGHT) continue;
            if (startTime(work.mUnit) >= 0) continue;
            if (isResultStillCached(work)) continue;

            expired.add(entry.getKey());
        }

        for (TranslationCacheKey key : expired) {
            mWork.remove(key);
        }
    }

    private void processDueRetries(long nowMs, long windowEnd) {
        if (mActive != null) return;

        List<Work> due = new ArrayList<>();
        for (Work work : mWork.values()) {
            if (work.mState == WorkState.WAITING_RETRY
                    && work.mDueAtMs <= nowMs
                    && isInRetryWindow(work.mUnit, windowEnd)) {
                due.add(work);
            }
        }

        for (Work work : due) {
            if (mActive != null) return;

            work.mState = WorkState.PENDING;
            submitLocked(work, nowMs);
        }
    }

    private void submitLocked(Work work, long nowMs) {
        if (mActive != null || work.mState != WorkState.PENDING) return;

        // Every dispatch path — window fill, redraw, resume, due retry, streaming toggle —
        // funnels through here, so this is the one place the budget cannot be bypassed. A
        // request that was cancelled before its answer still went to the provider and already
        // spent its attempt, so a cancel-and-retry cycle stops at the same ceiling.
        if (work.mAttempts >= mMaxAttempts) {
            work.mState = WorkState.FAILED;
            work.mFailureMessage = "Translation attempt budget exhausted.";
            queueTranslationFailed(work.mFailureMessage);
            return;
        }

        // The selected Prompt, its bounded context, and the cache identity are frozen on the
        // unit's first dispatch, so a redraw cannot rebuild them from a scrolling history and
        // a retry reuses exactly what was already sent.
        if (work.mCacheKey == null && !freezeRequestLocked(work)) return;

        TranslationResult cached = mCache.get(work.mCacheKey);
        if (cached != null && cached.isFinal()) {
            work.mState = WorkState.SUCCEEDED;
            return;
        }

        work.mAttempts++;
        work.mStreamed = isStreamingAllowed(work);

        if (work.mAttempts == 1) {
            mFirstAttempts++;
        } else if (work.mFallbackPending) {
            // Only the single streamed-to-plain conversion is a fallback. A later plain retry
            // after that is an ordinary retry, however many of them the budget allows.
            mFallbackAttempts++;
            work.mFallbackPending = false;
        } else {
            mRetryAttempts++;
        }

        work.mGeneration = mSession.getGeneration();
        work.mEpoch = mSession.getEpoch();
        work.mRequestId = ++mRequestIdSeed;
        work.mState = WorkState.IN_FLIGHT;
        mActive = work;

        TranslationRequest request = new TranslationRequest(
                mSession.getSessionId(), work.mRequestId, work.mUnit, work.mRenderedPrompt);

        try {
            work.mCall = mProvider.translate(request,
                    work.mStreamed ? new SchedulerStreamCallback(work, request)
                            : new SchedulerCallback(work, request));
        } catch (Exception e) {
            work.mCall = null;
            finishFailureLocked(work, new TranslationFailure(
                    TranslationFailureCategory.INVALID_OUTPUT, "Provider request failed."), nowMs);
        }
    }

    private void finishSuccessLocked(Work work, TranslationResult result, long nowMs) {
        work.mState = WorkState.SUCCEEDED;
        work.mDraft = null;
        mCache.put(work.mCacheKey, result);

        if (mActive == work) mActive = null;

        queueTranslationArrived();
        dispatch(nowMs, true);
    }

    private void finishFailureLocked(Work work, TranslationFailure failure, long nowMs) {
        work.mDraft = null;

        // A stream that failed for a transient reason falls back to one plain request. The
        // fallback spends the same attempt budget, so a unit still stops after three tries.
        // The flag is one-shot: it marks the next request as the fallback and nothing later.
        if (work.mStreamed && failure != null && failure.isRetryable()) {
            work.mStreamingDisabled = true;
            work.mFallbackPending = true;
        }

        if (failure == null) {
            failure = new TranslationFailure(
                    TranslationFailureCategory.INVALID_OUTPUT, "Translation failed.");
        }

        if (failure.isRetryable() && work.mAttempts < mMaxAttempts) {
            work.mState = WorkState.WAITING_RETRY;
            work.mDueAtMs = nowMs + backoffDelay(work.mAttempts);
        } else {
            work.mState = WorkState.FAILED;
            work.mFailureMessage = failure.getMessage();
        }

        if (mActive == work) mActive = null;

        if (work.mState == WorkState.FAILED) {
            queueTranslationFailed(work.mFailureMessage);
        } else {
            queueTranslationArrived();
        }
    }

    private void cancelActiveLocked() {
        if (mActive == null) return;

        Work active = mActive;
        cancelCall(active);

        if (active.mState == WorkState.IN_FLIGHT) active.mState = WorkState.PENDING;
        mActive = null;
    }

    private void cancelTransientWorkLocked() {
        cancelActiveLocked();

        for (Work work : mWork.values()) {
            if (work.mState == WorkState.WAITING_RETRY) {
                work.mState = WorkState.PENDING;
                work.mDueAtMs = 0;
            }
        }
    }

    private boolean isStreamingAllowed(Work work) {
        return mStreamingEnabled && !work.mStreamingDisabled;
    }

    private void clearDraftsLocked() {
        for (Work work : mWork.values()) {
            work.mDraft = null;
        }
    }

    private void cancelCall(Work work) {
        work.mDraft = null;

        if (work.mCall == null) return;

        try {
            work.mCall.cancel();
        } catch (Exception ignored) {
            // The scheduler's ownership checks remain authoritative.
        }

        work.mCall = null;
    }

    /**
     * The shared work lookup every dispatch path uses. A record that finished translating but
     * whose result has since been evicted becomes a fresh request: a SUCCEEDED record would
     * otherwise answer "already translated" from an empty cache and leave the cue source-only
     * for the rest of the video.
     *
     * <p>Only that one transition is reset. A FAILED record keeps its terminal state — the
     * eviction is irrelevant to it — and only an explicit manual retry reopens its budget. The
     * frozen Prompt and cache identity are kept, so the rebuild sends exactly what was sent
     * before rather than re-deriving a context from a history that has moved on.</p>
     */
    private Work workFor(TranslationUnit unit) {
        TranslationCacheKey identity = identityFor(unit);
        Work work = mWork.get(identity);

        if (work == null) {
            work = new Work(unit, identity);
            mWork.put(identity, work);
        } else if (work.mState == WorkState.SUCCEEDED && !isResultStillCached(work)) {
            work.mState = WorkState.PENDING;
            work.mAttempts = 0; // a new cache fill after a completed translation
            work.mCall = null;
        }

        return work;
    }

    /**
     * Whether a record that has frozen its request identity still resolves to stored text. The
     * predicate is independent of state; only {@link #workFor} applies it to a finished record.
     */
    private boolean isResultStillCached(Work work) {
        return work.mCacheKey != null && mCache.contains(work.mCacheKey);
    }

    private Work workForLookup(TranslationUnit unit) {
        return unit == null ? null : mWork.get(identityFor(unit));
    }

    /** Context-free identity: it decides which unit a work item belongs to, not what it sends. */
    private TranslationCacheKey identityFor(TranslationUnit unit) {
        return TranslationCacheKey.from(mSession.getSessionId(), unit,
                CONTEXT_FINGERPRINT_NONE, SEGMENTATION_VERSION, BOUNDARY_VERSION);
    }

    private TranslationCacheKey cacheKeyFor(TranslationUnit unit, String contextFingerprint) {
        return TranslationCacheKey.from(mSession.getSessionId(), unit,
                contextFingerprint, SEGMENTATION_VERSION, BOUNDARY_VERSION);
    }

    /**
     * Builds and freezes one unit's context, rendered prompt, and cache identity. Returns false
     * when the Prompt cannot be rendered, which is terminal and never reaches the network.
     */
    private boolean freezeRequestLocked(Work work) {
        String context = mContextEnabled
                ? mContextBuilder.build(mVideoTitle, mVideoDescription, historyFor(work.mUnit))
                : "";
        String renderedPrompt = renderPrompt(work.mUnit, context);

        if (renderedPrompt == null) {
            work.mState = WorkState.FAILED;
            work.mFailureMessage = "Prompt could not be rendered.";
            queueTranslationFailed(work.mFailureMessage);
            return false;
        }

        work.mRenderedPrompt = renderedPrompt;
        work.mCacheKey = cacheKeyFor(work.mUnit, fingerprint(renderedPrompt));
        return true;
    }

    /** Renders the frozen Prompt Profile for one unit; null means the prompt is unusable. */
    private String renderPrompt(TranslationUnit unit, String context) {
        Map<String, String> values = new LinkedHashMap<>();
        TranslationSessionId sessionId = mSession.getSessionId();

        values.put(PromptVariable.SOURCE_TEXT.getName(), unit.getSourceText());
        values.put(PromptVariable.SOURCE_LANGUAGE.getName(),
                sessionId.getSourceTrackId().getLanguage());
        values.put(PromptVariable.TARGET_LANGUAGE.getName(),
                sessionId.getProfile().getTargetLanguage());
        values.put(PromptVariable.UNIT_INDEX.getName(),
                String.valueOf(unit.getFirstSegmentId().getIndex()));
        // Present but possibly empty: the renderer distinguishes an explicit empty value from a
        // missing variable, so a prompt that references the context is never called malformed.
        values.put(PromptVariable.CONTEXT.getName(), context != null ? context : "");

        PromptRenderer.RenderResult rendered =
                mPromptRenderer.render(mPromptProfile.getContent(), values);

        return rendered.isValid() ? rendered.getText() : null;
    }

    /**
     * Reference history for one unit: only units that start strictly earlier on the same
     * timeline, oldest first, each carrying its accepted final translation when one exists. A
     * future unit that happened to finish first never becomes history.
     *
     * <p>The timeline units are ordered by their first segment, so the current unit's position
     * is found by binary search and only the few immediately preceding units are examined.
     * Scanning the whole unit list would make every request cost one timeline walk and one
     * cache lookup per cue on the video, which on a two-hour timeline is thousands of lookups
     * to build at most three lines of context.</p>
     */
    private List<TranslationContextBuilder.Entry> historyFor(TranslationUnit unit) {
        SourceTimeline timeline = mTimeline;
        if (timeline == null) return Collections.emptyList();

        List<TranslationUnit> units = timeline.getUnits();
        int firstIndex = unit.getFirstSegmentId().getIndex();
        int insertion = firstUnitAtOrAfter(units, firstIndex);

        List<TranslationContextBuilder.Entry> history = new ArrayList<>(MAX_HISTORY_UNITS);

        for (int i = insertion - 1; i >= 0 && insertion - i <= MAX_HISTORY_UNITS; i--) {
            TranslationUnit candidate = units.get(i);

            // An overlapping unit that reaches into the current one is not background.
            if (candidate.getLastSegmentId().getIndex() >= firstIndex) continue;

            history.add(new TranslationContextBuilder.Entry(
                    candidate.getSourceText(), acceptedTranslationFor(candidate)));
        }

        Collections.reverse(history);
        return history;
    }

    /**
     * Index of the first unit whose first segment index is at least {@code firstIndex}, for a
     * unit list already ordered by first segment index. Returns {@code units.size()} when the
     * unit starts after every recorded one.
     */
    private static int firstUnitAtOrAfter(List<TranslationUnit> units, int firstIndex) {
        int low = 0;
        int high = units.size();

        while (low < high) {
            int mid = (low + high) >>> 1;

            if (units.get(mid).getFirstSegmentId().getIndex() < firstIndex) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        return low;
    }

    private String acceptedTranslationFor(TranslationUnit unit) {
        Work work = workForLookup(unit);
        if (work == null || work.mCacheKey == null) return null;

        TranslationResult result = mCache.get(work.mCacheKey);
        return result != null ? result.getTranslatedText() : null;
    }

    /** SHA-256 of the exact instruction that will be sent; never the raw context text. */
    private static String fingerprint(String renderedPrompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(renderedPrompt.getBytes(
                    java.nio.charset.Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder(hash.length * 2);

            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required on every Android API level this app supports.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private long startTime(TranslationUnit unit) {
        long[] range = mSegmentTimes.get(unit.getFirstSegmentId().getIndex());
        return range != null ? range[0] : -1;
    }

    /** Whether the unit's own time range still covers the playhead, however long it runs. */
    private boolean coversPosition(TranslationUnit unit) {
        List<SubtitleSegmentId> ids = unit.getSegmentIds();
        if (ids.isEmpty()) return false;

        long[] first = mSegmentTimes.get(ids.get(0).getIndex());
        long[] last = mSegmentTimes.get(ids.get(ids.size() - 1).getIndex());
        if (first == null || last == null) return false;

        return mPositionMs >= first[0] && mPositionMs <= last[1];
    }

    /**
     * Whether a retry may still be issued for this unit. Only work for the cue under the
     * playhead or for a cue the window has not passed yet qualifies: the retained history
     * margin exists so the bounded context can find recent text, not so a failed sentence that
     * already played can be translated again behind the viewer's back.
     *
     * <p>A unit with no time record is the display-cue fallback. There is no window to compare
     * against, and the caller is the cue the player is showing right now, so it stays eligible.</p>
     */
    private boolean isInRetryWindow(TranslationUnit unit, long windowEnd) {
        long start = startTime(unit);
        if (start < 0) return true;
        if (coversPosition(unit)) return true;
        return start >= mPositionMs && start <= windowEnd;
    }

    private long backoffDelay(int completedAttempts) {
        int index = Math.min(completedAttempts, BACKOFF_MS.length) - 1;
        long delay = BACKOFF_MS[Math.max(0, index)];

        if (mJitterRangeMs > 0) delay += mJitter.nextInt(mJitterRangeMs + 1);
        return delay;
    }

    private void queueTranslationArrived() {
        mPendingEvents.add(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onTranslationArrived();
            }
        });
    }

    private void queueTranslationDraft() {
        mPendingEvents.add(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onTranslationDraft();
            }
        });
    }

    private void queueTranslationFailed(final String message) {
        mPendingEvents.add(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onTranslationFailed(message);
            }
        });
    }

    private void drainEvents() {
        List<Runnable> events;

        synchronized (this) {
            // close() empties the queue under this same lock. A drain that starts after it must
            // not resurrect a notification for a scheduler that is already gone.
            if (mClosed) {
                mPendingEvents.clear();
                return;
            }

            if (mPendingEvents.isEmpty()) return;

            // Copy and clear under the lock; the listeners themselves run outside it so a
            // synchronous repaint cannot re-enter while state is being mutated.
            events = new ArrayList<>(mPendingEvents);
            mPendingEvents.clear();
        }

        for (Runnable event : events) {
            event.run();
        }
    }

    private static long windowEnd(long positionMs, long lookaheadMs) {
        if (lookaheadMs <= 0 || positionMs > Long.MAX_VALUE - lookaheadMs) return positionMs;
        return positionMs + lookaheadMs;
    }

    private static long monotonicNowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private class SchedulerCallback implements TranslationCallback {
        private final Work mWork;
        private final TranslationRequest mRequest;

        private SchedulerCallback(Work work, TranslationRequest request) {
            mWork = work;
            mRequest = request;
        }

        Work getWork() {
            return mWork;
        }

        TranslationRequest getRequest() {
            return mRequest;
        }

        @Override
        public void onSuccess(final TranslationResult result) {
            synchronized (TranslationScheduler.this) {
                long nowMs = monotonicNowMs();

                if (ownsRequest() && validResponse(result)) {
                    finishSuccessLocked(mWork, result, nowMs);
                } else if (ownsRequest()) {
                    finishFailureLocked(mWork, new TranslationFailure(
                            TranslationFailureCategory.INVALID_OUTPUT,
                            "Provider response did not match the request."), nowMs);
                }
            }

            drainEvents();
        }

        @Override
        public void onFailure(TranslationFailure failure) {
            synchronized (TranslationScheduler.this) {
                long nowMs = monotonicNowMs();
                if (ownsFailure()) finishFailureLocked(mWork, failure, nowMs);
            }

            drainEvents();
        }

        private boolean ownsRequest() {
            return mWork.mState == WorkState.IN_FLIGHT
                    && mSession.owns(mWork.mGeneration, mWork.mEpoch)
                    && mWork.mRequestId == mRequest.getRequestId();
        }

        private boolean validResponse(TranslationResult result) {
            return result != null
                    && result.isFinal()
                    && mWork.mRequestId == result.getRequestId()
                    && mSession.getSessionId().equals(result.getSessionId())
                    && mWork.mUnit.getSegmentIds().equals(result.getSegmentIds());
        }

        private boolean ownsFailure() {
            return mWork.mState == WorkState.IN_FLIGHT
                    && mSession.owns(mWork.mGeneration, mWork.mEpoch)
                    && mWork.mRequestId == mRequest.getRequestId();
        }
    }

    /**
     * Streaming variant of the request callback. A draft only updates the unit that is still in
     * flight; it never releases concurrency, never spends an attempt, and never reaches the
     * cache or the context history.
     */
    private final class SchedulerStreamCallback extends SchedulerCallback
            implements TranslationStream {
        private SchedulerStreamCallback(Work work, TranslationRequest request) {
            super(work, request);
        }

        @Override
        public void onPartial(TranslationResult partialResult) {
            boolean accepted = false;

            synchronized (TranslationScheduler.this) {
                if (acceptsPartial(partialResult)) {
                    getWork().mDraft = partialResult.getTranslatedText();
                    accepted = true;
                }
            }

            if (accepted) {
                synchronized (TranslationScheduler.this) {
                    queueTranslationDraft();
                }
                drainEvents();
            }
        }

        private boolean acceptsPartial(TranslationResult result) {
            return result != null
                    && result.isPartial()
                    && getWork().mState == WorkState.IN_FLIGHT
                    && getWork().mRequestId == result.getRequestId()
                    && mSession.owns(getWork().mGeneration, getWork().mEpoch)
                    && mSession.getSessionId().equals(result.getSessionId())
                    && getWork().mUnit.getSegmentIds().equals(result.getSegmentIds());
        }
    }

    private static final class Work {
        private final TranslationUnit mUnit;
        /** Context-free unit identity; the cache key below is what carries the context. */
        private final TranslationCacheKey mKey;
        private TranslationCacheKey mCacheKey;
        private String mRenderedPrompt;
        private String mDraft;
        private boolean mStreamed;
        private boolean mStreamingDisabled;
        /** Set by a transient streamed failure; marks exactly the next request as the fallback. */
        private boolean mFallbackPending;
        private WorkState mState = WorkState.PENDING;
        private int mAttempts;
        private long mRequestId;
        private long mGeneration;
        private long mEpoch;
        private long mDueAtMs;
        private String mFailureMessage = "";
        private TranslationCall mCall;

        private Work(TranslationUnit unit, TranslationCacheKey key) {
            mUnit = unit;
            mKey = key;
        }
    }
}

