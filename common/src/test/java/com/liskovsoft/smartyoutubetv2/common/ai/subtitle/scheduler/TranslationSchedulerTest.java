package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.InMemoryTranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SourceTimeline;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TranslationSchedulerTest {
    private static final SourceTrackId TRACK = new SourceTrackId(
            "video-1", "subtitle:en:1", "en");
    private static final TranslationProfile PROFILE =
            new TranslationProfile("profile", "protocol", "https://example.test",
                    "model", "prompt", 1, "zh");
    private static final PromptProfile PROMPT = new PromptProfile(
            "prompt-1", "Test prompt",
            "Translate {{source_text}} into {{target_language}}.", 1, false);
    private static final long NOW = 10_000L;

    private TranslationSession mSession;
    private RecordingProvider mProvider;
    private TranslationScheduler mScheduler;
    private List<String> mArrived;
    private List<String> mFailed;

    @Before
    public void setUp() {
        mSession = new TranslationSession(sessionId(), 1);
        mSession.markReady();
        mSession.markActive();
        mProvider = new RecordingProvider();
        mArrived = new ArrayList<>();
        mFailed = new ArrayList<>();
        mScheduler = new TranslationScheduler(mSession, mProvider, PROMPT, new InMemoryTranslationCache(),
                new TranslationScheduler.Listener() {
                    @Override
                    public void onTranslationArrived() {
                        mArrived.add("arrived");
                    }

                    @Override
                    public void onTranslationFailed(String reason) {
                        mFailed.add(reason);
                    }
                });
        mScheduler.setThrottleMs(0);
        mScheduler.setTimeline(timeline());
    }

    @Test
    public void lookaheadWindowContainsCurrentAndFutureUnitsOnly() {
        mScheduler.setLookaheadMs(30_000);
        mScheduler.onPositionUpdate(10_000, NOW);
        mProvider.completeLast();

        assertEquals(Arrays.asList("0-20", "20-40"), mProvider.unitTexts());
    }

    @Test
    public void zeroLookaheadStillTranslatesCurrentUnit() {
        mScheduler.setLookaheadMs(0);
        mScheduler.onPositionUpdate(10_000, NOW);

        assertEquals(Collections.singletonList("0-20"), mProvider.unitTexts());
    }

    @Test
    public void repeatedPositionTicksDoNotDuplicateRequests() {
        mScheduler.onPositionUpdate(10_000, NOW);

        for (int i = 0; i < 100; i++) {
            mScheduler.onPositionUpdate(10_000, NOW);
        }

        assertEquals(Collections.singletonList("0-20"), mProvider.unitTexts());
    }

    @Test
    public void currentUnitPreemptsFutureUnitWithConcurrencyOne() {
        mScheduler.setLookaheadMs(30_000);
        mScheduler.onPositionUpdate(10_000, NOW);

        // Unit 20-40 became active; an explicitly requested earlier unit preempts it.
        mScheduler.onPositionUpdate(25_000, NOW);
        mScheduler.requestCurrentUnit(unit(0, 20, "0-20"), NOW);

        assertEquals(Arrays.asList("0-20", "20-40", "0-20"), mProvider.unitTexts());
        assertTrue(mProvider.previousCall.isCancelled());
    }

    @Test
    public void pauseStopsNewWorkAndResumeDispatchesImmediately() {
        mScheduler.onPositionUpdate(10_000, NOW);
        mScheduler.pause();

        int before = mProvider.getCallCount();
        mScheduler.onPositionUpdate(10_000, NOW);
        assertEquals(before, mProvider.getCallCount());

        mScheduler.resume(10_000, NOW);

        assertEquals(before + 1, mProvider.getCallCount());
        assertEquals("0-20", mProvider.unitTexts().get(mProvider.unitTexts().size() - 1));
    }

    @Test
    public void dragKeepsLatestPositionAndSeekEndDispatchesOnlyIt() {
        mScheduler.onSeekDrag(60_000);
        mScheduler.onSeekDrag(100_000);
        mScheduler.onSeekDrag(90_000);

        assertEquals(Collections.emptyList(), mProvider.unitTexts());

        mSession.advanceEpoch();
        mScheduler.onPositionChanged(90_000, NOW);

        assertEquals(Collections.singletonList("80-100"), mProvider.unitTexts());
    }

    @Test
    public void closeCancelsActiveWorkAndStopsDispatch() {
        mScheduler.onPositionUpdate(10_000, NOW);
        mScheduler.close();

        assertTrue(mProvider.lastCall.isCancelled());

        int before = mProvider.getCallCount();
        mScheduler.onPositionUpdate(20_000, NOW);
        assertEquals(before, mProvider.getCallCount());
    }

    @Test
    public void cacheHitDoesNotIssueANewRequest() {
        FakeTranslationProvider immediate = new FakeTranslationProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, immediate, PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setTimeline(singleUnitTimeline());

        scheduler.onPositionUpdate(10_000, NOW);
        scheduler.onPositionUpdate(10_000, NOW);

        assertEquals(1, immediate.getTranslateCallCount());
    }

    @Test
    public void futureUnitsOutsideWindowAreNotPreTranslated() {
        mScheduler.setLookaheadMs(30_000);
        mScheduler.onPositionUpdate(10_000, NOW);

        assertFalse(mProvider.unitTexts().contains("50-60"));
        assertFalse(mProvider.unitTexts().contains("80-100"));
    }

    @Test
    public void timeoutUsesThreeNetworkAttemptsWithBackoff() {
        mScheduler.setTimeline(singleUnitTimeline());
        mScheduler.onPositionUpdate(10_000, NOW);
        assertEquals(1, mProvider.getCallCount());

        mProvider.failLast(TranslationFailureCategory.TIMEOUT);
        long firstDue = mScheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20"));
        assertTrue(firstDue > 0);

        mScheduler.onPositionUpdate(10_000, firstDue - 1);
        assertEquals(1, mProvider.getCallCount());

        mScheduler.onPositionUpdate(10_000, firstDue);
        assertEquals(2, mProvider.getCallCount());

        mProvider.failLast(TranslationFailureCategory.TIMEOUT);
        long secondDue = mScheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20"));
        mScheduler.onPositionUpdate(10_000, secondDue);
        assertEquals(3, mProvider.getCallCount());

        mProvider.failLast(TranslationFailureCategory.TIMEOUT);
        assertTrue(mScheduler.hasFailed(unit(0, 0, "0-20")));

        long thirdDue = mScheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20"));
        mScheduler.onPositionUpdate(10_000, Math.max(firstDue, Math.max(secondDue, thirdDue)) + 10_000);
        assertEquals(3, mProvider.getCallCount());
    }

    @Test
    public void rateLimitAndServerUseTheSameRetryBudget() {
        for (TranslationFailureCategory category : Arrays.asList(
                TranslationFailureCategory.RATE_LIMITED, TranslationFailureCategory.SERVER)) {
            TranslationSession session = new TranslationSession(sessionId(), 2);
            session.markReady();
            session.markActive();
            CategoryFailingProvider provider = new CategoryFailingProvider(category);
            TranslationScheduler scheduler = new TranslationScheduler(session, provider, PROMPT,
                    new InMemoryTranslationCache(), null);
            scheduler.setThrottleMs(0);
            scheduler.setTimeline(singleUnitTimeline());

            scheduler.onPositionUpdate(10_000, NOW);
            provider.failLast(category);
            long due = scheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20"));

            scheduler.onPositionUpdate(10_000, due);
            assertEquals(2, provider.getCallCount());
        }
    }

    @Test
    public void terminalCategoriesDoNotRetry() {
        for (TranslationFailureCategory category : Arrays.asList(
                TranslationFailureCategory.AUTH,
                TranslationFailureCategory.PROTOCOL,
                TranslationFailureCategory.INVALID_OUTPUT,
                TranslationFailureCategory.CANCELLED)) {
            TranslationSession session = new TranslationSession(sessionId(), 3);
            session.markReady();
            session.markActive();
            CategoryFailingProvider provider = new CategoryFailingProvider(category);
            TranslationScheduler scheduler = new TranslationScheduler(session, provider, PROMPT,
                    new InMemoryTranslationCache(), null);
            scheduler.setThrottleMs(0);
            scheduler.setTimeline(singleUnitTimeline());

            scheduler.onPositionUpdate(10_000, NOW);
            scheduler.onPositionUpdate(10_000, NOW + 60_000);
            provider.failLast(category);

            assertEquals(1, provider.getCallCount());
            assertTrue(scheduler.hasFailed(unit(0, 0, "0-20")));
        }
    }

    @Test
    public void partialDraftsAndWrongCoverageAreTerminalWithoutCache() {
        TranslationUnit unit = unit(0, 0, "0-20");
        TranslationUnit wrongCoverage = unit(1, 1, "wrong");

        mScheduler.onPositionUpdate(10_000, NOW);
        mProvider.deliverLast(TranslationResult.partialResult(
                mProvider.lastRequest.getSessionId(), mProvider.lastRequest.getRequestId(),
                mProvider.lastRequest.getUnit(), "draft"));

        assertTrue(mScheduler.hasFailed(unit));
        assertNull(mScheduler.getCachedTranslation(unit));

        TranslationSession session = new TranslationSession(sessionId(), 4);
        session.markReady();
        session.markActive();
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(session, provider, PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setTimeline(singleUnitTimeline());
        scheduler.onPositionUpdate(10_000, NOW);
        provider.deliverLast(TranslationResult.finalResult(
                provider.lastRequest.getSessionId(), provider.lastRequest.getRequestId(),
                wrongCoverage, "wrong"));

        assertTrue(scheduler.hasFailed(unit));
        assertNull(scheduler.getCachedTranslation(unit));
    }

    @Test
    public void retryOnlyTheFailedUnitAfterNeighborsSucceed() {
        mScheduler.setLookaheadMs(30_000);
        mScheduler.onPositionUpdate(10_000, NOW);

        mProvider.completeLast();
        mProvider.failLast(TranslationFailureCategory.SERVER);
        long due = mScheduler.getRetryDueAtMsForTesting(unit(1, 1, "20-40"));

        mScheduler.onPositionUpdate(10_000, due);

        assertEquals(Arrays.asList("0-20", "20-40", "20-40"), mProvider.unitTexts());
    }
    @Test
    public void settingsChangeNeverResumesAPausedScheduler() {
        mScheduler.onPositionUpdate(10_000, NOW);
        mScheduler.pause();

        int before = mProvider.getCallCount();
        mScheduler.setLookaheadMs(30_000);
        mScheduler.onSettingsChanged(10_000, NOW);

        assertEquals("a settings change must not start work while playback is paused",
                before, mProvider.getCallCount());

        mScheduler.resume(10_000, NOW);
        assertEquals(before + 1, mProvider.getCallCount());
    }

    @Test
    public void settingsChangeImmediatelyReshapesTheWindowWhenActive() {
        mScheduler.setLookaheadMs(0);
        mScheduler.onPositionUpdate(10_000, NOW);
        mProvider.completeLast();

        mScheduler.setLookaheadMs(30_000);
        mScheduler.onSettingsChanged(10_000, NOW);

        assertEquals("a widened window must dispatch the newly covered unit",
                Arrays.asList("0-20", "20-40"), mProvider.unitTexts());
    }

    @Test
    public void manualRetryRequeuesTerminalFailuresWithAFreshBudget() {
        mScheduler.setTimeline(singleUnitTimeline());
        mScheduler.onPositionUpdate(10_000, NOW);
        mProvider.failLast(TranslationFailureCategory.AUTH);

        assertTrue(mScheduler.hasFailed(unit(0, 0, "0-20")));

        // Ordinary ticks must not reset a terminal failure.
        mScheduler.onPositionUpdate(10_000, NOW + 60_000);
        assertEquals(1, mProvider.getCallCount());

        mScheduler.retryFailed(10_000, NOW + 60_000);

        assertEquals("a manual retry must issue the request again",
                2, mProvider.getCallCount());
        assertFalse(mScheduler.hasFailed(unit(0, 0, "0-20")));
    }

    @Test
    public void manualRetryLeavesUnitsOutsideTheWindowFailed() {
        mScheduler.setLookaheadMs(0);
        mScheduler.onPositionUpdate(10_000, NOW);
        mProvider.completeLast();

        mScheduler.onPositionChanged(80_000, NOW);
        mProvider.failLast(TranslationFailureCategory.AUTH);
        assertEquals("80-100", mProvider.unitTexts().get(mProvider.unitTexts().size() - 1));

        int before = mProvider.getCallCount();
        mScheduler.retryFailed(10_000, NOW);

        assertEquals("a retry request for another position must not dispatch the old unit",
                before, mProvider.getCallCount());
    }

    @Test
    public void anUnrenderablePromptIsTerminalWithoutTouchingTheNetwork() {
        PromptProfile broken = new PromptProfile("prompt-broken", "Broken",
                "Translate {{source_text}} using {{unknown_variable}}.", 1, false);
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, broken,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setTimeline(singleUnitTimeline());

        scheduler.onPositionUpdate(10_000, NOW);

        assertEquals("a malformed prompt must never reach the provider",
                0, provider.getCallCount());
        assertTrue(scheduler.hasFailed(unit(0, 0, "0-20")));
    }

    @Test
    public void aPromptThatReferencesTheEmptyContextStillRenders() {
        PromptProfile withContext = new PromptProfile("prompt-context", "With context",
                "Context:{{context}}|{{source_text}}", 1, false);
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, withContext,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setTimeline(singleUnitTimeline());

        scheduler.onPositionUpdate(10_000, NOW);

        assertEquals(1, provider.getCallCount());
        assertEquals("Context:|0-20", provider.lastRequest.getRenderedPrompt());
    }

    @Test
    public void lookaheadWindowSelectsExactlyTheSpecifiedUnits() {
        assertEquals(Arrays.asList("10", "25", "40"),
                dispatchedUnits(30_000, 10_000));
        assertEquals(Arrays.asList("10", "25", "40", "70", "100"),
                dispatchedUnits(90_000, 10_000));
    }

    @Test
    public void shrinkingTheWindowStopsFurtherDispatchBeyondIt() {
        TranslationSession session = new TranslationSession(sessionId(), 7);
        session.markReady();
        session.markActive();
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(session, provider, PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setLookaheadMs(90_000);
        scheduler.setTimeline(subSecondTimeline());

        scheduler.onPositionUpdate(10_000, NOW);
        provider.completeLast();

        scheduler.setLookaheadMs(30_000);
        scheduler.onSettingsChanged(10_000, NOW);

        for (int i = 0; i < 6; i++) {
            provider.completeLast();
        }

        assertEquals("a narrowed window must not keep dispatching beyond it",
                Arrays.asList("10", "25", "40"), provider.unitTexts());
    }

    @Test
    public void throttlingNeverDelaysSeek() {
        mScheduler.setThrottleMs(30_000);
        mScheduler.setLookaheadMs(0);
        mScheduler.onPositionUpdate(10_000, NOW);
        mProvider.completeLast();

        mSession.advanceEpoch();
        mScheduler.onPositionChanged(90_000, NOW + 100);

        assertEquals("a seek must dispatch immediately even inside the throttle interval",
                "80-100", mProvider.unitTexts().get(mProvider.unitTexts().size() - 1));
    }

    /**
     * Drives one lookahead window to completion with a single in-flight request at a time.
     * Units start at 10/25/40/70/100/101 seconds and are shorter than one second.
     */
    private List<String> dispatchedUnits(long lookaheadMs, long positionMs) {
        TranslationSession session = new TranslationSession(sessionId(), 6);
        session.markReady();
        session.markActive();
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(session, provider, PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setLookaheadMs(lookaheadMs);
        scheduler.setTimeline(subSecondTimeline());

        scheduler.onPositionUpdate(positionMs, NOW);

        for (int i = 0; i < 8; i++) {
            provider.completeLast();
        }

        return provider.unitTexts();
    }

    private static SourceTimeline subSecondTimeline() {
        long[] starts = {10_000, 25_000, 40_000, 70_000, 100_000, 101_000};
        List<SubtitleSegment> segments = new ArrayList<>();
        List<TranslationUnit> units = new ArrayList<>();

        for (int i = 0; i < starts.length; i++) {
            String text = String.valueOf(starts[i] / 1_000);
            segments.add(new SubtitleSegment(new SubtitleSegmentId(TRACK, i), starts[i],
                    starts[i] + 400, text));
            units.add(unit(i, i, text));
        }

        return SourceTimeline.from(segments, units);
    }

    private static SourceTimeline singleUnitTimeline() {
        return SourceTimeline.from(
                Collections.singletonList(segment(0, 0, 20_000, "0-20")),
                Collections.singletonList(unit(0, 0, "0-20")));
    }

    private static SourceTimeline timeline() {
        List<SubtitleSegment> segments = Arrays.asList(
                segment(0, 0, 20_000, "0-20"),
                segment(1, 20_000, 40_000, "20-40"),
                segment(2, 50_000, 60_000, "50-60"),
                segment(3, 80_000, 100_000, "80-100"));
        List<TranslationUnit> units = Arrays.asList(
                unit(0, 0, "0-20"),
                unit(1, 1, "20-40"),
                unit(2, 2, "50-60"),
                unit(3, 3, "80-100"));
        return SourceTimeline.from(segments, units);
    }

    private static SubtitleSegment segment(int index, long start, long end, String text) {
        return new SubtitleSegment(new SubtitleSegmentId(TRACK, index), start, end, text);
    }

    private static TranslationUnit unit(int from, int to, String text) {
        List<SubtitleSegmentId> ids = new ArrayList<>();
        for (int i = from; i <= to; i++) ids.add(new SubtitleSegmentId(TRACK, i));
        return new TranslationUnit(ids, text);
    }

    private static com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId sessionId() {
        return new com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId(
                "video-1", TRACK, PROFILE,
                com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId.ENGINE_SCHEMA_VERSION);
    }

    private static final class RecordingProvider implements TranslationProvider {
        private final List<String> mUnitTexts = new ArrayList<>();
        private TranslationCall previousCall;
        private TranslationCall lastCall;
        private TranslationRequest lastRequest;
        private com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback lastCallback;
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request,
                                         com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback callback) {
            mCallCount++;
            mUnitTexts.add(request.getSourceText());
            lastRequest = request;
            lastCallback = callback;
            previousCall = lastCall;
            lastCall = new TrackingCall();
            return lastCall;
        }

        List<String> unitTexts() {
            return new ArrayList<>(mUnitTexts);
        }
        int getCallCount() {
            return mCallCount;
        }

        void failLast(TranslationFailureCategory category) {
            lastCallback.onFailure(new TranslationFailure(category, "synthetic " + category));
        }

        void deliverLast(TranslationResult result) {
            lastCallback.onSuccess(result);
        }
        void completeLast() {
            lastCallback.onSuccess(TranslationResult.finalResult(
                    lastRequest.getSessionId(), lastRequest.getRequestId(), lastRequest.getUnit(),
                    "[ZH] " + lastRequest.getSourceText()));
        }
    }

    private static final class CategoryFailingProvider implements TranslationProvider {
        private final TranslationFailureCategory mCategory;
        private TranslationRequest mLastRequest;
        private com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback mLastCallback;
        private int mCallCount;

        CategoryFailingProvider(TranslationFailureCategory category) {
            mCategory = category;
        }

        @Override
        public TranslationCall translate(TranslationRequest request,
                                         com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback callback) {
            mCallCount++;
            mLastRequest = request;
            mLastCallback = callback;
            return new TrackingCall();
        }

        void failLast(TranslationFailureCategory category) {
            mLastCallback.onFailure(new TranslationFailure(category, "synthetic " + category));
        }

        int getCallCount() {
            return mCallCount;
        }
    }
    private static final class TrackingCall implements TranslationCall {
        private boolean mCancelled;

        @Override
        public void cancel() {
            mCancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return mCancelled;
        }
    }
}
