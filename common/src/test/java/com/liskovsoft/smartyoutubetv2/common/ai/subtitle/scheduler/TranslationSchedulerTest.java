package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.InMemoryTranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCache;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SourceTimeline;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
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
import static org.junit.Assert.assertTrue;

public class TranslationSchedulerTest {
    private static final SourceTrackId TRACK = new SourceTrackId(
            "video-1", "subtitle:en:1", "en");
    private static final TranslationProfile PROFILE =
            new TranslationProfile("profile", "protocol", "https://example.test",
                    "model", "prompt", 1, "zh");
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
        mScheduler = new TranslationScheduler(mSession, mProvider, new InMemoryTranslationCache(),
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
        TranslationScheduler scheduler = new TranslationScheduler(mSession, immediate,
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

        void completeLast() {
            lastCallback.onSuccess(TranslationResult.finalResult(
                    lastRequest.getSessionId(), lastRequest.getRequestId(), lastRequest.getUnit(),
                    "[ZH] " + lastRequest.getSourceText()));
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
