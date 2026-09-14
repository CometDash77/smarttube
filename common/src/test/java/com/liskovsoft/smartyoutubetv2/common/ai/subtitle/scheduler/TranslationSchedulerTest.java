package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.InMemoryTranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCacheKey;
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
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationStream;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
    /** Prompt that actually consumes the bounded context, so it can be observed in the body. */
    private static final PromptProfile CONTEXT_PROMPT = new PromptProfile(
            "prompt-ctx", "Context prompt",
            "Translate {{source_text}} into {{target_language}}. Earlier: {{context}}", 1, false);
    private static final long NOW = 10_000L;

    private TranslationSession mSession;
    private RecordingProvider mProvider;
    private TranslationScheduler mScheduler;
    private List<String> mArrived;
    private List<String> mFailed;
    private List<String> mDrafts;

    @Before
    public void setUp() {
        mSession = new TranslationSession(sessionId(), 1);
        mSession.markReady();
        mSession.markActive();
        mProvider = new RecordingProvider();
        mArrived = new ArrayList<>();
        mFailed = new ArrayList<>();
        mDrafts = new ArrayList<>();
        mScheduler = new TranslationScheduler(mSession, mProvider, PROMPT, new InMemoryTranslationCache(),
                new TranslationScheduler.Listener() {
                    @Override
                    public void onTranslationArrived() {
                        mArrived.add("arrived");
                    }

                    @Override
                    public void onTranslationDraft() {
                        mDrafts.add("draft");
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

    @Test
    public void boundedContextCarriesTheTitleDescriptionAndEarlierSources() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = contextScheduler(provider);
        scheduler.setVideoMetadata("My video", "About things");
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(20_000, NOW);

        String prompt = provider.lastRequest.getRenderedPrompt();
        assertTrue(prompt.contains("Title: My video"));
        assertTrue(prompt.contains("Description: About things"));
        assertTrue(prompt.contains("Earlier subtitles: 0-20"));
        assertFalse("a later unit must never appear as history", prompt.contains("50-60"));
    }

    @Test
    public void contextIsOffUntilItIsEnabled() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, CONTEXT_PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setVideoMetadata("My video", "About things");
        scheduler.setLookaheadMs(0);
        scheduler.setTimeline(timeline());

        scheduler.onPositionUpdate(20_000, NOW);

        String prompt = provider.lastRequest.getRenderedPrompt();
        assertFalse(prompt.contains("Title: My video"));
        assertTrue("the context variable stays present but empty",
                prompt.endsWith("Earlier: "));
    }

    @Test
    public void enablingContextChangesTheRenderedPromptForTheSameUnit() {
        RecordingProvider offProvider = new RecordingProvider();
        TranslationScheduler off = new TranslationScheduler(mSession, offProvider, CONTEXT_PROMPT,
                new InMemoryTranslationCache(), null);
        off.setThrottleMs(0);
        off.setVideoMetadata("My video", null);
        off.setLookaheadMs(0);
        off.setTimeline(timeline());
        off.onPositionUpdate(20_000, NOW);

        RecordingProvider onProvider = new RecordingProvider();
        TranslationScheduler on = contextScheduler(onProvider);
        on.setVideoMetadata("My video", null);
        on.setLookaheadMs(0);

        on.onPositionUpdate(20_000, NOW);

        assertNotEquals("the context switch is part of the request identity",
                offProvider.lastRequest.getRenderedPrompt(),
                onProvider.lastRequest.getRenderedPrompt());
    }

    @Test
    public void anUnfinishedEarlierUnitContributesSourceOnly() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = contextScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        scheduler.onPositionUpdate(20_000, NOW);

        String prompt = provider.lastRequest.getRenderedPrompt();
        assertTrue(prompt.contains("Earlier subtitles: 0-20"));
        assertFalse("an unfinished unit must not contribute a translation",
                prompt.contains("=>"));
    }

    @Test
    public void anAcceptedFinalBecomesHistoryForTheNextUnit() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = contextScheduler(provider);
        scheduler.setLookaheadMs(0);
        scheduler.setTimeline(timeline());

        scheduler.onPositionUpdate(10_000, NOW);
        provider.completeLast();

        scheduler.onPositionUpdate(50_000, NOW);

        String prompt = provider.lastRequest.getRenderedPrompt();
        assertTrue("the accepted translation must be part of the next unit's context",
                prompt.contains("0-20 => [ZH] 0-20"));
    }

    @Test
    public void aLaterUnitThatFinishedFirstIsNeverHistory() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = contextScheduler(provider);

        scheduler.onPositionUpdate(10_000, NOW);
        scheduler.requestCurrentUnit(unit(1, 1, "20-40"), NOW);
        provider.completeLast();

        scheduler.requestCurrentUnit(unit(0, 0, "0-20"), NOW);

        assertFalse("a unit that comes later on the timeline is not background",
                provider.lastRequest.getRenderedPrompt().contains("Earlier subtitles:"));
    }

    @Test
    public void aFrozenRequestIsReusedVerbatimAfterTheHistoryMovesOn() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = contextScheduler(provider);
        scheduler.setLookaheadMs(0);
        scheduler.setTimeline(timeline());

        scheduler.onPositionUpdate(20_000, NOW);
        scheduler.requestCurrentUnit(unit(0, 0, "0-20"), NOW);
        provider.completeLast();

        List<String> prompts = provider.promptsFor("20-40");
        assertEquals("the unit must be sent twice, not re-frozen", 2, prompts.size());
        assertEquals("a redraw must reuse the frozen instruction verbatim",
                prompts.get(0), prompts.get(1));
    }

    private TranslationScheduler contextScheduler(RecordingProvider provider) {
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider,
                CONTEXT_PROMPT, new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setContextEnabled(true);
        scheduler.setTimeline(timeline());
        return scheduler;
    }

    /**
     * Drives a 60-cue timeline to its middle and asserts, by cache access rather than by elapsed
     * time, that building one unit's context reads only that unit and its three immediate
     * predecessors — and that the rendered body quotes neither a future cue nor a far-past one.
     */
    @Test
    public void buildingTheContextReadsOnlyTheImmediatePredecessors() {
        RecordingGetCache cache = new RecordingGetCache();
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider,
                CONTEXT_PROMPT, cache, null);
        scheduler.setThrottleMs(0);
        scheduler.setContextEnabled(true);
        scheduler.setLookaheadMs(0);
        scheduler.setTimeline(oneCuePerSecondTimeline(60));

        // Stop in the middle: the last cue has no future cue to assert about.
        for (int second = 0; second < 30; second++) {
            scheduler.onPositionUpdate(second * 1_000L, NOW + second);
            provider.completeLast();
        }

        cache.resetCount();
        scheduler.onPositionUpdate(30_000, NOW + 30);

        // One read decides whether the frozen key already holds a result, then one per candidate
        // history entry. Reading the whole timeline would be 31 reads here, and repeating one
        // key would satisfy a bare count, so the keys themselves are checked too.
        assertEquals("the context must not read the whole history", 4, cache.getCount());
        assertEquals("each read must be a different key", 4, cache.getDistinctKeyCount());

        String prompt = provider.lastRequest.getRenderedPrompt();
        assertTrue(prompt.contains("cue 27"));
        assertTrue(prompt.contains("cue 29"));
        assertFalse("a cue from far back must not be quoted", prompt.contains("cue 10"));
        assertFalse("a cue that has not been reached must not be quoted",
                prompt.contains("cue 40"));
    }

    @Test
    public void aFailureBehindThePlayheadIsNotRetried() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setLookaheadMs(0);
        scheduler.setTimeline(timeline());

        scheduler.onPositionUpdate(25_000, NOW);
        provider.failLast(TranslationFailureCategory.AUTH);
        assertTrue(scheduler.hasFailed(unit(1, 1, "20-40")));

        // Play on to the 50-60 cue. The 20-40 record stays inside the 30 s history margin, so
        // the window prune deliberately keeps it even though the playhead has passed it.
        scheduler.onPositionChanged(50_000, NOW + 1);
        assertTrue("the margin keeps the record reachable", scheduler.hasFailed(unit(1, 1, "20-40")));

        int before = provider.getCallCount();
        scheduler.retryFailed(50_000, NOW + 2);

        assertTrue("a cue the playhead has already passed stays failed",
                scheduler.hasFailed(unit(1, 1, "20-40")));
        assertEquals("and it must not cost a request", before, provider.getCallCount());
    }

    @Test
    public void aLongUnitCrossingThePlayheadCanBeRetriedManually() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setTimeline(longCueTimeline());

        scheduler.onPositionUpdate(60_000, NOW);
        provider.failLast(TranslationFailureCategory.AUTH);
        assertTrue(scheduler.hasFailed(unit(0, 2, "one long cue")));

        scheduler.retryFailed(60_000, NOW + 1);

        assertEquals("a cue that still covers the playhead must be retriable",
                2, provider.getCallCount());
        assertFalse(scheduler.hasFailed(unit(0, 2, "one long cue")));
    }

    @Test
    public void aStreamFallbackIsCountedOnceAndLaterRetriesAreOrdinaryRetries() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = streamingScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        provider.fail(TranslationFailureCategory.NETWORK);

        scheduler.onPositionUpdate(10_000,
                scheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20")));
        provider.fail(TranslationFailureCategory.SERVER);

        scheduler.onPositionUpdate(10_000,
                scheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20")));
        provider.complete("final");

        assertEquals("stream, then one plain fallback, then one plain retry", 3,
                provider.getCallCount());
        assertEquals(1, scheduler.getFirstAttemptCount());
        assertEquals(1, scheduler.getFallbackAttemptCount());
        assertEquals(1, scheduler.getRetryAttemptCount());
    }

    @Test
    public void closeReleasesTheWorkRecords() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, PROMPT,
                new InMemoryTranslationCache(), null);
        scheduler.setThrottleMs(0);
        scheduler.setLookaheadMs(0);
        scheduler.setTimeline(timeline());

        scheduler.onPositionUpdate(10_000, NOW);
        provider.completeLast();
        assertEquals("[ZH] 0-20", scheduler.getCachedTranslation(unit(0, 0, "0-20")));

        scheduler.close();

        assertEquals("close must release every work record", 0, scheduler.getWorkCount());
        assertNull("and stop resolving translations from a discarded scheduler",
                scheduler.getCachedTranslation(unit(0, 0, "0-20")));
    }

    @Test
    public void lateDeliveriesAfterCloseCannotDispatchOrRepaint() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = streamingScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        StreamingProvider.StreamHandle handle = provider.capture();

        scheduler.close();

        int calls = provider.getCallCount();
        int drafts = mDrafts.size();
        int arrived = mArrived.size();
        int failed = mFailed.size();

        handle.emitPartial("late draft");
        provider.complete("late final");
        provider.fail(TranslationFailureCategory.NETWORK);
        scheduler.onPositionUpdate(20_000, NOW + 1);
        scheduler.resume(20_000, NOW + 2);

        assertEquals("no request may start after close", calls, provider.getCallCount());
        assertEquals("a late draft must not repaint", drafts, mDrafts.size());
        assertEquals("a late final must not be published", arrived, mArrived.size());
        assertEquals("a late failure must not be published", failed, mFailed.size());
    }

    @Test
    public void streamedDraftsReachTheListenerWithoutSpendingAnAttempt() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = streamingScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        assertTrue("a streaming request must ask for drafts", provider.lastCallbackIsStreaming);

        provider.emitPartial("\u4f60");
        provider.emitPartial("\u4f60\u597d");

        assertEquals("the latest cumulative draft wins", "\u4f60\u597d",
                scheduler.getDraftTranslation(unit(0, 0, "0-20")));
        assertEquals(2, mDrafts.size());
        assertNull("a draft must never be released as final text",
                scheduler.getCachedTranslation(unit(0, 0, "0-20")));
        assertEquals("one request, no retry", 1, provider.getCallCount());
    }

    @Test
    public void aFinalReplacesTheDraftAndEntersTheCache() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = streamingScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        provider.emitPartial("draft text");
        provider.complete("final text");

        assertEquals("final text", scheduler.getCachedTranslation(unit(0, 0, "0-20")));
        assertNull("the draft must be gone once the final arrives",
                scheduler.getDraftTranslation(unit(0, 0, "0-20")));
    }

    @Test
    public void aStreamInterruptionFallsBackToAPlainRequest() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = streamingScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        provider.emitPartial("partial");
        provider.fail(TranslationFailureCategory.NETWORK);

        long due = scheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20"));
        scheduler.onPositionUpdate(10_000, due);

        assertEquals("one streamed attempt plus one plain fallback", 2, provider.getCallCount());
        assertFalse("the fallback must not stream again", provider.lastCallbackIsStreaming);
        assertNull("an interrupted stream must not leave a draft behind",
                scheduler.getDraftTranslation(unit(0, 0, "0-20")));
    }

    @Test
    public void theStreamingFallbackStillStopsAfterTheAttemptBudget() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = streamingScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        provider.fail(TranslationFailureCategory.NETWORK);
        long first = scheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20"));

        scheduler.onPositionUpdate(10_000, first);
        provider.fail(TranslationFailureCategory.NETWORK);
        long second = scheduler.getRetryDueAtMsForTesting(unit(0, 0, "0-20"));

        scheduler.onPositionUpdate(10_000, second);
        provider.fail(TranslationFailureCategory.NETWORK);

        assertEquals("the budget stays three attempts in total", 3, provider.getCallCount());
        assertTrue(scheduler.hasFailed(unit(0, 0, "0-20")));
    }

    @Test
    public void seekingClearsTheDraftAndCancelsTheStream() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = streamingScheduler(provider);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(10_000, NOW);
        StreamingProvider.StreamHandle first = provider.capture();
        first.emitPartial("draft");
        assertEquals("draft", scheduler.getDraftTranslation(unit(0, 0, "0-20")));

        mSession.advanceEpoch();
        scheduler.onPositionChanged(50_000, NOW);

        assertTrue("the stream in flight must be cancelled", first.isCancelled());
        assertNull("a seek must clear the draft", scheduler.getDraftTranslation(unit(0, 0, "0-20")));

        first.emitPartial("late");

        assertNull("a draft from a superseded stream must stay invisible",
                scheduler.getDraftTranslation(unit(0, 0, "0-20")));
    }

    private TranslationScheduler streamingScheduler(StreamingProvider provider) {
        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, PROMPT,
                new InMemoryTranslationCache(), new TranslationScheduler.Listener() {
            @Override
            public void onTranslationArrived() {
                mArrived.add("arrived");
            }

            @Override
            public void onTranslationDraft() {
                mDrafts.add("draft");
            }

            @Override
            public void onTranslationFailed(String reason) {
                mFailed.add(reason);
            }
        });
        scheduler.setThrottleMs(0);
        scheduler.setStreamingEnabled(true);
        scheduler.setTimeline(timeline());
        return scheduler;
    }

    /** Provider double that can emit streamed drafts and complete or fail explicitly. */
    private static final class StreamingProvider implements TranslationProvider {
        private int mCallCount;
        private boolean lastCallbackIsStreaming;
        private TranslationCallback lastCallback;
        private TranslationRequest lastRequest;
        private TrackingCall lastCall;

        @Override
        public TranslationCall translate(TranslationRequest request,
                                         TranslationCallback callback) {
            mCallCount++;
            lastRequest = request;
            lastCallback = callback;
            lastCallbackIsStreaming = callback instanceof TranslationStream;
            lastCall = new TrackingCall();
            return lastCall;
        }

        int getCallCount() {
            return mCallCount;
        }

        StreamHandle capture() {
            return new StreamHandle(lastCallback, lastRequest, lastCall);
        }

        void emitPartial(String text) {
            capture().emitPartial(text);
        }

        void complete(String text) {
            lastCallback.onSuccess(TranslationResult.finalResult(lastRequest.getSessionId(),
                    lastRequest.getRequestId(), lastRequest.getUnit(), text));
        }

        void fail(TranslationFailureCategory category) {
            lastCallback.onFailure(new TranslationFailure(category, "synthetic " + category));
        }

        /** One captured streaming attempt, usable after newer requests have replaced it. */
        static final class StreamHandle {
            private final TranslationStream mCallback;
            private final TranslationRequest mRequest;
            private final TrackingCall mCall;

            StreamHandle(TranslationCallback callback, TranslationRequest request,
                         TrackingCall call) {
                mCallback = (TranslationStream) callback;
                mRequest = request;
                mCall = call;
            }

            boolean isCancelled() {
                return mCall.isCancelled();
            }

            void emitPartial(String text) {
                mCallback.onPartial(TranslationResult.partialResult(mRequest.getSessionId(),
                        mRequest.getRequestId(), mRequest.getUnit(), text));
            }
        }
    }

    private static SourceTimeline singleUnitTimeline() {
        return SourceTimeline.from(
                Collections.singletonList(segment(0, 0, 20_000, "0-20")),
                Collections.singletonList(unit(0, 0, "0-20")));
    }

    /** {@code count} one-second cues, so a long timeline is cheap to drive in virtual time. */
    private static SourceTimeline oneCuePerSecondTimeline(int count) {
        List<SubtitleSegment> segments = new ArrayList<>();
        List<TranslationUnit> units = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String text = "cue " + i;
            segments.add(segment(i, i * 1_000L, i * 1_000L + 900L, text));
            units.add(unit(i, i, text));
        }

        return SourceTimeline.from(segments, units);
    }

    /** A single unit whose three segments span two minutes, so it outlives the playhead. */
    private static SourceTimeline longCueTimeline() {
        List<SubtitleSegment> segments = Arrays.asList(
                segment(0, 0, 40_000, "one long cue"),
                segment(1, 40_000, 80_000, "one long cue"),
                segment(2, 80_000, 120_000, "one long cue"));

        return SourceTimeline.from(segments,
                Collections.singletonList(unit(0, 2, "one long cue")));
    }

    /** Cache decorator that records reads, so context cost is asserted by access, not by time. */
    private static final class RecordingGetCache implements TranslationCache {
        private final InMemoryTranslationCache mDelegate = new InMemoryTranslationCache();
        private final List<TranslationCacheKey> mKeys = new ArrayList<>();

        @Override
        public TranslationResult get(TranslationCacheKey key) {
            mKeys.add(key);
            return mDelegate.get(key);
        }

        @Override
        public boolean contains(TranslationCacheKey key) {
            return mDelegate.contains(key);
        }

        @Override
        public void put(TranslationCacheKey key, TranslationResult result) {
            mDelegate.put(key, result);
        }

        @Override
        public void clear() {
            mDelegate.clear();
        }

        void resetCount() {
            mKeys.clear();
        }

        int getCount() {
            return mKeys.size();
        }

        int getDistinctKeyCount() {
            return new HashSet<>(mKeys).size();
        }
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
        private final Map<String, List<String>> mPromptsBySource = new LinkedHashMap<>();
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
            List<String> prompts = mPromptsBySource.get(request.getSourceText());
            if (prompts == null) {
                prompts = new ArrayList<>();
                mPromptsBySource.put(request.getSourceText(), prompts);
            }
            prompts.add(request.getRenderedPrompt());
            lastRequest = request;
            lastCallback = callback;
            previousCall = lastCall;
            lastCall = new TrackingCall();
            return lastCall;
        }

        List<String> unitTexts() {
            return new ArrayList<>(mUnitTexts);
        }

        List<String> promptsFor(String sourceText) {
            List<String> prompts = mPromptsBySource.get(sourceText);
            return prompts != null ? new ArrayList<>(prompts) : new ArrayList<String>();
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
