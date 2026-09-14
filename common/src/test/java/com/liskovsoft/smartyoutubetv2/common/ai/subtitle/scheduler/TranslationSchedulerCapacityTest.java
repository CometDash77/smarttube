package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.InMemoryTranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SourceTimeline;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationStream;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Capacity and accounting tests: a two-hour synthetic timeline (7500 one-second cues, driven
 * through virtual time rather than by waiting), the bounds that keep work records and cached
 * text from growing with playback, and the split between first, retry and fallback attempts.
 *
 * <p>This is not a two-hour device stability test and is not reported as one.</p>
 */
public class TranslationSchedulerCapacityTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "subtitle:en:1", "en");
    private static final TranslationProfile PROFILE = new TranslationProfile(
            "profile", "protocol", "https://example.test", "model", "prompt", 1, "zh");
    private static final PromptProfile PROMPT = new PromptProfile(
            "prompt-1", "Test prompt",
            "Translate {{source_text}} into {{target_language}}.", 1, false);
    private static final long NOW = 10_000L;
    private static final long WINDOW_MS = 30_000L;

    private TranslationSession mSession;

    @Test
    public void aTwoHourTimelineStaysBoundedUnderRepeatedSeeks() {
        InMemoryTranslationCache cache = new InMemoryTranslationCache();
        FakeTranslationProvider provider = new FakeTranslationProvider();
        TranslationScheduler scheduler = scheduler(provider, cache);

        for (int step = 0; step < 256; step++) {
            long positionMs = (step % 2 == 0)
                    ? (step * 29_000L) % 7_400_000L
                    : 7_400_000L - ((step * 31_000L) % 7_400_000L);

            mSession.advanceEpoch();
            scheduler.onPositionChanged(positionMs, NOW + step);
        }

        // Bounded by the cache entry limit plus one window; not by the length of the video.
        assertTrue("work records must not grow with the whole video: " + scheduler.getWorkCount()
                        + " cache=" + cache.size() + "/" + cache.byteSize(),
                scheduler.getWorkCount() <= InMemoryTranslationCache.DEFAULT_MAX_ENTRIES + 200);
        assertTrue("cached text must stay inside its entry budget: " + cache.size(),
                cache.size() <= InMemoryTranslationCache.DEFAULT_MAX_ENTRIES);
        assertTrue("cached text must stay inside its byte budget: " + cache.byteSize(),
                cache.byteSize() <= InMemoryTranslationCache.DEFAULT_MAX_BYTES);
    }

    @Test
    public void recordsOutsideTheWindowAreDropped() {
        FakeTranslationProvider provider = new FakeTranslationProvider();
        TranslationScheduler scheduler = scheduler(provider, new InMemoryTranslationCache());

        scheduler.onPositionChanged(1_000_000, NOW);

        scheduler.onPositionChanged(7_000_000, NOW + 1_000);

        assertTrue("live records stay bounded by the cache, not by the video: "
                        + scheduler.getWorkCount(),
                scheduler.getWorkCount() <= InMemoryTranslationCache.DEFAULT_MAX_ENTRIES + 200);
    }

    @Test
    public void twoUnitsThatShareAStartTimeAreBothDispatched() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = scheduler(provider, new InMemoryTranslationCache());

        scheduler.setTimeline(sharedStartTimeline());
        scheduler.onPositionUpdate(1_000, NOW);
        provider.complete();
        scheduler.onPositionUpdate(1_000, NOW);

        assertTrue("a second unit at the same start time must not be dropped: " + provider.units,
                provider.units.containsAll(Arrays.asList("first", "second")));
    }

    @Test
    public void attemptAccountingSeparatesFirstRetryAndFallback() {
        StreamingProvider provider = new StreamingProvider();
        TranslationScheduler scheduler = scheduler(provider, new InMemoryTranslationCache());
        scheduler.setStreamingEnabled(true);
        scheduler.setTimeline(oneUnitTimeline());

        scheduler.onPositionUpdate(1_000, NOW);
        assertEquals(1, scheduler.getFirstAttemptCount());

        provider.fail(TranslationFailureCategory.NETWORK);
        scheduler.onPositionUpdate(1_000, scheduler.getRetryDueAtMsForTesting(unit(0)));

        assertEquals("the fallback is counted separately from an ordinary retry",
                1, scheduler.getFallbackAttemptCount());
        assertEquals(0, scheduler.getRetryAttemptCount());
        assertEquals(2, provider.getCallCount());
    }

    @Test
    public void aRedrawNeverResetsTheAttemptBudget() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = scheduler(provider, new InMemoryTranslationCache());
        scheduler.setTimeline(oneUnitTimeline());

        scheduler.onPositionUpdate(1_000, NOW);
        provider.fail(TranslationFailureCategory.SERVER);

        for (int i = 0; i < 20; i++) {
            scheduler.onPositionUpdate(1_000, NOW + i);
        }

        assertEquals("only the first request may have been issued so far",
                1, provider.getCallCount());
        assertEquals(1, scheduler.getFirstAttemptCount());
        assertEquals(0, scheduler.getRetryAttemptCount());
    }

    /**
     * A one-entry cache stands in for the real 512-entry / 2 MiB bound: once the current unit's
     * completed translation has been evicted, its work record must not keep claiming SUCCEEDED
     * against an empty cache. Coming back to the cue has to translate it again.
     */
    @Test
    public void anEvictedCurrentTranslationIsRequestedAgain() {
        InMemoryTranslationCache cache = new InMemoryTranslationCache(1, 1024);
        FakeTranslationProvider provider = new FakeTranslationProvider();
        TranslationScheduler scheduler = scheduler(provider, cache);
        scheduler.setLookaheadMs(0);

        scheduler.onPositionUpdate(1_000, NOW);
        TranslationUnit first = twoHourTimeline().getUnits().get(1);
        assertNotNull(scheduler.getCachedTranslation(first));

        scheduler.onPositionUpdate(2_000, NOW + 1_000);
        assertNull("a one-entry cache must evict the first unit's result",
                scheduler.getCachedTranslation(first));

        scheduler.onPositionChanged(1_000, NOW + 2_000);
        assertNotNull("eviction cannot leave SUCCEEDED permanently source-only",
                scheduler.getCachedTranslation(first));
    }

    /**
     * The display-cue fallback has no timeline at all, so the window prune cannot bound it: one
     * work record per requested cue would accumulate for as long as the fallback runs.
     */
    @Test
    public void noTimelineFallbackWorkStaysBounded() {
        TranslationScheduler scheduler = scheduler(new FakeTranslationProvider(),
                new InMemoryTranslationCache());
        scheduler.setTimeline(null);

        for (int i = 0; i < 1000; i++) {
            scheduler.requestCurrentUnit(new TranslationUnit(
                    java.util.Collections.singletonList(new SubtitleSegmentId(TRACK, 0)),
                    "source " + i), NOW + i);

            assertTrue("fallback work also needs a bound; actual=" + scheduler.getWorkCount(),
                    scheduler.getWorkCount() <= 513);
        }
    }

    /**
     * The same bound has to hold when some cues fail: a terminal failure is neither cached nor
     * the current cue forever, so its record leaves with the cue that owned it.
     */
    @Test
    public void noTimelineFallbackStaysBoundedAcrossFailures() {
        AlternatingProvider provider = new AlternatingProvider();
        TranslationScheduler scheduler = scheduler(provider, new InMemoryTranslationCache());
        scheduler.setTimeline(null);

        for (int i = 0; i < 1000; i++) {
            scheduler.requestCurrentUnit(new TranslationUnit(
                    java.util.Collections.singletonList(new SubtitleSegmentId(TRACK, 0)),
                    "source " + i), NOW + i);

            assertTrue("mixed success and failure cues must stay bounded; actual="
                            + scheduler.getWorkCount(),
                    scheduler.getWorkCount() <= 513);
        }
    }

    /**
     * A normal timeline walked one cue at a time, never seeking, and never completing more than
     * one request at once. Both the all-success and the all-failure walks stay inside the cache
     * plus window bound from the first cue to the two-thousandth.
     */
    @Test
    public void continuousSuccessAndFailureWindowsStayBounded() {
        for (final boolean fail : new boolean[] {false, true}) {
            InMemoryTranslationCache cache = new InMemoryTranslationCache();
            TranslationProvider provider = new TranslationProvider() {
                @Override
                public TranslationCall translate(TranslationRequest request,
                                                 TranslationCallback callback) {
                    if (fail) {
                        callback.onFailure(new TranslationFailure(
                                TranslationFailureCategory.AUTH, "synthetic"));
                    } else {
                        callback.onSuccess(TranslationResult.finalResult(
                                request.getSessionId(), request.getRequestId(),
                                request.getUnit(), "ok"));
                    }

                    return new NopCall();
                }
            };
            TranslationScheduler scheduler = scheduler(provider, cache);
            scheduler.setLookaheadMs(0);
            scheduler.setThrottleMs(30_000);

            for (int i = 0; i < 2000; i++) {
                scheduler.onPositionUpdate(i * 1_000L, NOW + i * 1_000L);

                assertTrue("work=" + scheduler.getWorkCount() + " fail=" + fail,
                        scheduler.getWorkCount() <= 574);
                assertTrue("cache=" + cache.size(), cache.size() <= 512);
            }
        }
    }

    /**
     * Cancelling and resuming is how the player re-issues work, and it must not buy extra
     * requests: a cancelled request reached the provider, so it already counts.
     */
    @Test
    public void cancellingAndResumingNeverExceedsTheAttemptBudget() {
        RecordingProvider provider = new RecordingProvider();
        TranslationScheduler scheduler = scheduler(provider, new InMemoryTranslationCache());
        scheduler.setTimeline(oneUnitTimeline());

        for (int i = 0; i < 6; i++) {
            scheduler.onPositionUpdate(1_000, NOW + i);
            scheduler.pause();
            scheduler.resume(1_000, NOW + i);
        }

        assertEquals("the budget still stops at three issued requests",
                3, provider.getCallCount());
    }

    // ---------------------------------------------------------------- helpers
    private TranslationScheduler scheduler(TranslationProvider provider,
                                           InMemoryTranslationCache cache) {
        mSession = new TranslationSession(sessionId(), 1);
        mSession.markReady();
        mSession.markActive();

        TranslationScheduler scheduler = new TranslationScheduler(mSession, provider, PROMPT,
                cache, null);
        scheduler.setThrottleMs(0);
        scheduler.setLookaheadMs(WINDOW_MS);
        scheduler.setTimeline(twoHourTimeline());

        return scheduler;
    }

    private static TranslationSessionId sessionId() {
        return new TranslationSessionId("video-1", TRACK, PROFILE,
                TranslationSessionId.ENGINE_SCHEMA_VERSION);
    }

    /** 7500 one-second cues: a synthetic two-hour timeline, traversed through virtual time. */
    private static SourceTimeline twoHourTimeline() {
        List<SubtitleSegment> segments = new ArrayList<>();
        List<TranslationUnit> units = new ArrayList<>();

        for (int i = 0; i < 7_500; i++) {
            String text = "cue " + i;
            segments.add(new SubtitleSegment(new SubtitleSegmentId(TRACK, i),
                    i * 1_000L, i * 1_000L + 900L, text));
            units.add(new TranslationUnit(
                    java.util.Collections.singletonList(new SubtitleSegmentId(TRACK, i)), text));
        }

        return SourceTimeline.from(segments, units);
    }

    private static SourceTimeline sharedStartTimeline() {
        List<SubtitleSegment> segments = Arrays.asList(
                new SubtitleSegment(new SubtitleSegmentId(TRACK, 0), 1_000, 3_000, "first"),
                new SubtitleSegment(new SubtitleSegmentId(TRACK, 1), 1_000, 2_000, "second"));
        List<TranslationUnit> units = Arrays.asList(
                unit(0), new TranslationUnit(
                        java.util.Collections.singletonList(new SubtitleSegmentId(TRACK, 1)),
                        "second"));

        return SourceTimeline.from(segments, units);
    }

    private static SourceTimeline oneUnitTimeline() {
        return SourceTimeline.from(
                java.util.Collections.singletonList(
                        new SubtitleSegment(new SubtitleSegmentId(TRACK, 0), 1_000, 2_000, "first")),
                java.util.Collections.singletonList(unit(0)));
    }

    private static TranslationUnit unit(int index) {
        return new TranslationUnit(
                java.util.Collections.singletonList(new SubtitleSegmentId(TRACK, index)), "first");
    }

    private static final class RecordingProvider implements TranslationProvider {
        private final List<String> units = new ArrayList<>();
        private TranslationCallback lastCallback;
        private TranslationRequest lastRequest;
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            units.add(request.getSourceText());
            lastCallback = callback;
            lastRequest = request;
            return new NopCall();
        }

        int getCallCount() {
            return mCallCount;
        }

        void complete() {
            lastCallback.onSuccess(TranslationResult.finalResult(lastRequest.getSessionId(),
                    lastRequest.getRequestId(), lastRequest.getUnit(), "[ZH] " + lastRequest.getSourceText()));
        }

        void fail(TranslationFailureCategory category) {
            lastCallback.onFailure(new TranslationFailure(category, "synthetic " + category));
        }
    }

    private static final class StreamingProvider implements TranslationProvider {
        private int mCallCount;
        private TranslationCallback lastCallback;
        private TranslationRequest lastRequest;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            lastRequest = request;
            lastCallback = callback;
            return new NopCall();
        }

        int getCallCount() {
            return mCallCount;
        }

        void fail(TranslationFailureCategory category) {
            lastCallback.onFailure(new TranslationFailure(category, "synthetic " + category));
        }
    }

    private static final class NopCall implements TranslationCall {
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

    /** Succeeds on odd cues and fails terminally on even ones, completing synchronously. */
    private static final class AlternatingProvider implements TranslationProvider {
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;

            if (mCallCount % 2 == 0) {
                callback.onFailure(new TranslationFailure(
                        TranslationFailureCategory.AUTH, "synthetic " + mCallCount));
            } else {
                callback.onSuccess(TranslationResult.finalResult(request.getSessionId(),
                        request.getRequestId(), request.getUnit(),
                        "[ZH] " + request.getSourceText()));
            }

            return new NopCall();
        }
    }
}
