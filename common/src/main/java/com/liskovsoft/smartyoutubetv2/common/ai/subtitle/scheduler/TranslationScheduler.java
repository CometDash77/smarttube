package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCacheKey;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRenderer;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptVariable;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SourceTimeline;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import java.util.ArrayList;
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

    private final TranslationSession mSession;
    private final TranslationProvider mProvider;
    private final TranslationCache mCache;
    private final Listener mListener;
    private final PromptProfile mPromptProfile;
    private final PromptRenderer mPromptRenderer = new PromptRenderer();
    private final Map<TranslationCacheKey, Work> mWork = new HashMap<>();
    private final TreeMap<Long, TranslationUnit> mUnitsByStart = new TreeMap<>();
    private final Map<Integer, long[]> mSegmentTimes = new HashMap<>();
    private final Random mJitter = new Random();
    private final List<Runnable> mPendingEvents = new ArrayList<>();

    private SourceTimeline mTimeline;
    private Work mActive;
    private long mPositionMs = -1;
    private long mLookaheadMs = 90_000;
    private long mThrottleMs = 30_000;
    private long mLastWindowDispatchMs = Long.MIN_VALUE;
    private long mRequestIdSeed;
    private int mMaxAttempts = 3;
    private int mJitterRangeMs = 250;
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
     * Manual retry entry. Only terminal failures inside the current window are re-queued, and
     * they get a fresh attempt budget because the user asked explicitly; ordinary redraws never
     * reset the budget.
     */
    public void retryFailed(long positionMs, long nowMs) {
        synchronized (this) {
            if (positionMs >= 0) mPositionMs = positionMs;

            long windowEnd = windowEnd(mPositionMs, mLookaheadMs);

            for (Work work : mWork.values()) {
                if (work.mState == WorkState.FAILED && startTime(work.mUnit) <= windowEnd) {
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
        if (unit == null) return null;

        TranslationResult result = mCache.get(keyFor(unit));
        return result != null ? result.getTranslatedText() : null;
    }

    public synchronized boolean hasFailed(TranslationUnit unit) {
        Work work = unit == null ? null : mWork.get(keyFor(unit));
        return work != null && work.mState == WorkState.FAILED;
    }

    public synchronized String getFailureMessage(TranslationUnit unit) {
        Work work = unit == null ? null : mWork.get(keyFor(unit));
        return work != null ? work.mFailureMessage : "";
    }

    public synchronized long getRetryDueAtMsForTesting(TranslationUnit unit) {
        Work work = unit == null ? null : mWork.get(keyFor(unit));
        return work != null ? work.mDueAtMs : -1;
    }

    public synchronized void close() {
        mClosed = true;
        cancelTransientWorkLocked();

        for (Work work : mWork.values()) {
            cancelCall(work);
        }

        mUnitsByStart.clear();
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

            if (start >= 0) mUnitsByStart.put(start, unit);
        }
    }

    private void requestCurrentUnitLocked(TranslationUnit unit, long nowMs) {
        if (mClosed || mPaused || unit == null || mSession.isClosed() || mSession.isPaused()) return;

        Work work = workFor(unit, keyFor(unit));

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
            fillFutureWindow(windowEnd, nowMs);
            mLastWindowDispatchMs = nowMs;
        }

        processDueRetries(nowMs, windowEnd);
    }

    private void fillFutureWindow(long windowEnd, long nowMs) {
        for (TranslationUnit unit : mUnitsByStart.subMap(mPositionMs, true, windowEnd, true).values()) {
            if (mActive != null) return;

            Work work = workFor(unit, keyFor(unit));

            if (work.mState == WorkState.PENDING) submitLocked(work, nowMs);
        }
    }

    private void processDueRetries(long nowMs, long windowEnd) {
        if (mActive != null) return;

        List<Work> due = new ArrayList<>();
        for (Work work : mWork.values()) {
            if (work.mState == WorkState.WAITING_RETRY
                    && work.mDueAtMs <= nowMs
                    && startTime(work.mUnit) <= windowEnd) {
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

        TranslationResult cached = mCache.get(work.mKey);
        if (cached != null && cached.isFinal()) {
            work.mState = WorkState.SUCCEEDED;
            return;
        }

        // The selected Prompt Profile is rendered before anything can reach the network, so an
        // unusable prompt is terminal and falls back to the original subtitles.
        String renderedPrompt = renderPrompt(work.mUnit);
        if (renderedPrompt == null) {
            work.mState = WorkState.FAILED;
            work.mFailureMessage = "Prompt could not be rendered.";
            queueTranslationFailed(work.mFailureMessage);
            return;
        }

        work.mAttempts++;
        work.mGeneration = mSession.getGeneration();
        work.mEpoch = mSession.getEpoch();
        work.mRequestId = ++mRequestIdSeed;
        work.mState = WorkState.IN_FLIGHT;
        mActive = work;

        TranslationRequest request = new TranslationRequest(
                mSession.getSessionId(), work.mRequestId, work.mUnit, renderedPrompt);

        try {
            work.mCall = mProvider.translate(request, new SchedulerCallback(work, request));
        } catch (Exception e) {
            work.mCall = null;
            finishFailureLocked(work, new TranslationFailure(
                    TranslationFailureCategory.INVALID_OUTPUT, "Provider request failed."), nowMs);
        }
    }

    private void finishSuccessLocked(Work work, TranslationResult result, long nowMs) {
        work.mState = WorkState.SUCCEEDED;
        mCache.put(work.mKey, result);

        if (mActive == work) mActive = null;

        queueTranslationArrived();
        dispatch(nowMs, true);
    }

    private void finishFailureLocked(Work work, TranslationFailure failure, long nowMs) {
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

    private void cancelCall(Work work) {
        if (work.mCall == null) return;

        try {
            work.mCall.cancel();
        } catch (Exception ignored) {
            // The scheduler's ownership checks remain authoritative.
        }

        work.mCall = null;
    }

    private Work workFor(TranslationUnit unit, TranslationCacheKey key) {
        Work work = mWork.get(key);

        if (work == null) {
            work = new Work(unit, key);
            mWork.put(key, work);
        }

        return work;
    }

    private TranslationCacheKey keyFor(TranslationUnit unit) {
        return TranslationCacheKey.from(mSession.getSessionId(), unit,
                CONTEXT_FINGERPRINT_NONE, SEGMENTATION_VERSION, BOUNDARY_VERSION);
    }

    /** Renders the frozen Prompt Profile for one unit; null means the prompt is unusable. */
    private String renderPrompt(TranslationUnit unit) {
        Map<String, String> values = new LinkedHashMap<>();
        TranslationSessionId sessionId = mSession.getSessionId();

        values.put(PromptVariable.SOURCE_TEXT.getName(), unit.getSourceText());
        values.put(PromptVariable.SOURCE_LANGUAGE.getName(),
                sessionId.getSourceTrackId().getLanguage());
        values.put(PromptVariable.TARGET_LANGUAGE.getName(),
                sessionId.getProfile().getTargetLanguage());
        values.put(PromptVariable.UNIT_INDEX.getName(),
                String.valueOf(unit.getFirstSegmentId().getIndex()));
        // M07 has no context builder yet. The variable is present but deliberately empty so a
        // prompt that references it is not rejected as malformed.
        values.put(PromptVariable.CONTEXT.getName(), "");

        PromptRenderer.RenderResult rendered =
                mPromptRenderer.render(mPromptProfile.getContent(), values);

        return rendered.isValid() ? rendered.getText() : null;
    }

    private long startTime(TranslationUnit unit) {
        long[] range = mSegmentTimes.get(unit.getFirstSegmentId().getIndex());
        return range != null ? range[0] : -1;
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

    private void queueTranslationFailed(final String message) {
        mPendingEvents.add(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onTranslationFailed(message);
            }
        });
    }

    private void drainEvents() {
        if (mPendingEvents.isEmpty()) return;

        List<Runnable> events = new ArrayList<>(mPendingEvents);
        mPendingEvents.clear();

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

    private final class SchedulerCallback implements TranslationCallback {
        private final Work mWork;
        private final TranslationRequest mRequest;

        private SchedulerCallback(Work work, TranslationRequest request) {
            mWork = work;
            mRequest = request;
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

    private static final class Work {
        private final TranslationUnit mUnit;
        private final TranslationCacheKey mKey;
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

