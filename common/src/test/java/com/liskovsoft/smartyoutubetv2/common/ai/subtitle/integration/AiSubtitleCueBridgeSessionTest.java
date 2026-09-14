package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionSnapshot;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SmartTubeSubtitleSourceAdapter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests for M03-C2/C4: session generation and scheduling-epoch ownership inside the
 * bridge, plus the C4 result-identity guarantees (final-only caching, session/request/coverage
 * matching). Verifies that identity changes create new generations, repeated identical events
 * stay idempotent, seeks only advance the epoch, and stale or invalid callbacks cannot mutate
 * state.
 */
public class AiSubtitleCueBridgeSessionTest {
    /** Test-only Prompt Profile; independent of any built-in or reference content. */
    private static final PromptProfile PROMPT = new PromptProfile(
            "test.prompt", "Test prompt",
            "Translate {{source_text}} into {{target_language}}.", 1, false);
    private AtomicBoolean mEnabled;
    private FakeTranslationProvider mFakeProvider;
    private AiSubtitleCueBridge mBridge;
    private AtomicInteger mRefreshCount;

    @Before
    public void setUp() {
        mEnabled = new AtomicBoolean(true);
        mFakeProvider = new FakeTranslationProvider(false);
        mBridge = new AiSubtitleCueBridge(mEnabled::get, mFakeProvider, PROMPT);
        mRefreshCount = new AtomicInteger();
        mBridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
    }

    @Test
    public void seekAdvancesEpochWithoutChangingSessionIdentity() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot before = mBridge.snapshotSession();

        assertNotNull(before);
        assertEquals(0, before.getEpoch());

        mBridge.onSeek(5000);

        TranslationSessionSnapshot after = mBridge.snapshotSession();

        assertEquals("a seek must not change session identity", before.getSessionId(), after.getSessionId());
        assertEquals("a seek must not change the generation", before.getGeneration(), after.getGeneration());
        assertEquals(1, after.getEpoch());
    }

    @Test
    public void repeatedSeeksAdvanceTheEpochEachTime() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));

        mBridge.onSeek(1000);
        mBridge.onSeek(2000);
        mBridge.onSeek(3000);

        assertEquals(3, mBridge.snapshotSession().getEpoch());
    }

    @Test
    public void trackChangeCreatesNewGenerationWithNewIdentity() {
        mBridge.onNewVideo("video-1");
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot first = mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:ja:asr-2");
        TranslationSessionSnapshot second = mBridge.snapshotSession();

        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertTrue(second.getGeneration() > first.getGeneration());
    }

    @Test
    public void repeatedIdenticalTrackEventsDoNotCreateNewGenerations() {
        mBridge.onNewVideo("video-1");
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        TranslationSessionSnapshot first = mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        TranslationSessionSnapshot again = mBridge.snapshotSession();

        assertEquals(first.getSessionId(), again.getSessionId());
        assertEquals("duplicate lifecycle events must be idempotent", first.getGeneration(), again.getGeneration());
    }

    @Test
    public void subtitlesOffAndOnRestartFromCleanState() {
        mBridge.onNewVideo("video-1");
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        mBridge.process(cues("Hello"));
        mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:none");
        TranslationSessionSnapshot off = mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        TranslationSessionSnapshot backOn = mBridge.snapshotSession();

        assertTrue("re-enabling subtitles must start a fresh generation", backOn.getGeneration() > off.getGeneration());
    }

    @Test
    public void newVideoStartsFreshSessionIdentity() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot first = mBridge.snapshotSession();

        mBridge.onNewVideo("video-2");
        TranslationSessionSnapshot second = mBridge.snapshotSession();

        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertTrue(second.getGeneration() > first.getGeneration());
    }

    @Test
    public void profileChangeCreatesNewGenerationAndRejectsStaleCallbacks() {
        StubbornProvider provider = new StubbornProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onNewVideo("video-1");

        bridge.process(cues("Hello"));
        TranslationSessionSnapshot before = bridge.snapshotSession();

        TranslationProfile other = new TranslationProfile("other-profile", "pending-protocol",
                "pending-endpoint", "pending-model", "pending-prompt", 1, "zh");
        bridge.onProfileChanged(other);
        TranslationSessionSnapshot after = bridge.snapshotSession();

        assertNotEquals("a profile change must create a new session identity",
                before.getSessionId(), after.getSessionId());
        assertTrue("a profile change must advance the generation",
                after.getGeneration() > before.getGeneration());

        provider.deliverAll();
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a stale callback must not leak into the new profile's session",
                "Hello", output.get(0).text.toString());
        assertEquals("the new session must issue its own request", 2, provider.getCallCount());
    }

    @Test
    public void pauseAndPlayAreReflectedInTheSessionState() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));

        mBridge.onPause();
        assertEquals(TranslationSession.State.PAUSED, mBridge.snapshotSession().getState());

        mBridge.onPlay();
        assertEquals(TranslationSession.State.ACTIVE, mBridge.snapshotSession().getState());
    }

    @Test
    public void releaseClosesTheActiveSession() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));

        assertNotNull(mBridge.snapshotSession());

        mBridge.onRelease();

        assertNull("release must leave no active session", mBridge.snapshotSession());
    }

    @Test
    public void resultWithMismatchedRequestIdentityIsRejected() {
        TranslationProvider mismatchedProvider = new TranslationProvider() {
            @Override
            public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
                callback.onSuccess(TranslationResult.finalResult(
                        request.getSessionId(),
                        request.getRequestId() + 999,
                        request.getUnit(),
                        "[ZH] " + request.getSourceText()));
                return new NopCall();
            }
        };

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, mismatchedProvider, PROMPT);
        bridge.onNewVideo("video-1");

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a result with mismatched request identity must never be cached",
                "Hello", output.get(0).text.toString());
    }

    @Test
    public void partialResultsNeverEnterTheCache() {
        TranslationProvider partialOnlyProvider = new TranslationProvider() {
            @Override
            public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
                callback.onSuccess(TranslationResult.partialResult(
                        request.getSessionId(),
                        request.getRequestId(),
                        request.getUnit(),
                        "[ZH] partial"));
                return new NopCall();
            }
        };

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, partialOnlyProvider, PROMPT);
        bridge.onNewVideo("video-1");

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a partial result must never be cached as final text",
                "Hello", output.get(0).text.toString());
    }

    @Test
    public void resultWithMismatchedCoverageIsRejected() {
        TranslationProvider wrongCoverageProvider = new TranslationProvider() {
            @Override
            public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
                TranslationUnit wrongUnit = new TranslationUnit(
                        Collections.singletonList(new SubtitleSegmentId(
                                request.getSessionId().getSourceTrackId(), 5)),
                        request.getSourceText());

                callback.onSuccess(TranslationResult.finalResult(
                        request.getSessionId(),
                        request.getRequestId(),
                        wrongUnit,
                        "[ZH] " + request.getSourceText()));
                return new NopCall();
            }
        };

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, wrongCoverageProvider, PROMPT);
        bridge.onNewVideo("video-1");

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a result that answers different coverage must never be cached",
                "Hello", output.get(0).text.toString());
    }

    @Test
    public void identicalSchedulingChangeIsIgnored() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));

        TranslationSessionSnapshot before = mBridge.snapshotSession();
        mBridge.onSchedulingChanged(90_000, 30_000);

        assertEquals("an unchanged limit must not touch the session",
                before.getSessionId(), mBridge.snapshotSession().getSessionId());
        assertEquals(before.getGeneration(), mBridge.snapshotSession().getGeneration());
    }

    @Test
    public void schedulingChangeKeepsTheSessionAndItsCachedTranslations() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));
        mFakeProvider.flushPending();

        TranslationSessionSnapshot before = mBridge.snapshotSession();
        mBridge.onSchedulingChanged(30_000, 5_000);

        assertEquals("a lookahead change must not restart the session",
                before.getSessionId(), mBridge.snapshotSession().getSessionId());
        assertEquals(before.getGeneration(), mBridge.snapshotSession().getGeneration());

        List<Cue> output = mBridge.process(cues("Hello"));
        assertEquals("a lookahead change must not discard cached translations",
                "Hello\n[ZH] Hello", output.get(0).text.toString());
    }

    @Test
    public void schedulingChangeWhilePausedStartsNoWork() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));
        mFakeProvider.flushPending();
        mBridge.onPause();

        mBridge.onSchedulingChanged(30_000, 5_000);

        assertEquals("a settings change must never resume playback work",
                TranslationSession.State.PAUSED, mBridge.snapshotSession().getState());
    }

    @Test
    public void segmentationChangeStartsANewGenerationAndKeepsThePausedState() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot before = mBridge.snapshotSession();

        mBridge.onSegmentationChanged(100, 320, 100);
        TranslationSessionSnapshot after = mBridge.snapshotSession();

        assertTrue("new segmentation must invalidate the old generation",
                after.getGeneration() > before.getGeneration());
        assertEquals(TranslationSession.State.ACTIVE, after.getState());

        mBridge.onPause();
        before = mBridge.snapshotSession();
        mBridge.onSegmentationChanged(40, 120, 60);
        after = mBridge.snapshotSession();

        assertTrue(after.getGeneration() > before.getGeneration());
        assertEquals("a segmentation change must not silently resume playback work",
                TranslationSession.State.PAUSED, after.getState());
    }

    @Test
    public void segmentationChangeIsIgnoredWhenItRepeatsTheCurrentValues() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));

        TranslationSessionSnapshot before = mBridge.snapshotSession();
        mBridge.onSegmentationChanged(60, 200, 80);

        assertEquals("identical segmentation must not drop the session",
                before.getSessionId(), mBridge.snapshotSession().getSessionId());
    }

    @Test
    public void anUnconfiguredProviderToleratesLifecycleAndSettingsChanges() {
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, null, PROMPT);
        bridge.onNewVideo("video-1");
        bridge.onSubtitleTrackChanged("subtitle:en:1");

        // Every one of these used to fail constructing a scheduler for a null provider.
        bridge.onSegmentationChanged(100, 320, 100);
        bridge.onSchedulingChanged(30_000, 5_000);
        bridge.onPause();
        bridge.onPlay();
        bridge.onSeek(1_000);
        bridge.onSeekDrag(2_000);

        List<Cue> input = cues("Hello");

        assertSame("an unconfigured provider must stay source-only",
                input, bridge.process(input));
    }

    @Test
    public void aNewSourceAdapterReceivesTheStoredSegmentationLimits() {
        mBridge.onSegmentationChanged(100, 320, 100);

        SmartTubeSubtitleSourceAdapter adapter =
                new SmartTubeSubtitleSourceAdapter((videoId, listener) -> { }, url -> null);
        mBridge.setSourceAdapter(adapter);

        assertEquals(100, adapter.segmentationLimitsForTesting()[0]);
        assertEquals(320, adapter.segmentationLimitsForTesting()[1]);
        assertEquals(100, adapter.segmentationLimitsForTesting()[2]);
    }

    @Test
    public void aStaleTimelineLoadCannotOverwriteANewerOne() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1");
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        assertEquals("the track change must start a load", 1, source.loadCount());

        // A segmentation change starts a second load while the first is still in flight.
        bridge.onSegmentationChanged(100, 320, 100);
        assertEquals(2, source.loadCount());

        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nSTALE");
        source.deliver(1, "00:00:00.000 --> 00:00:02.000\nFRESH");

        bridge.onPositionUpdate(1_000);
        bridge.process(cues("displayed"));
        provider.flush();

        assertTrue("the newest load must win", provider.unitTexts().contains("FRESH"));
        assertFalse("a superseded load must never be applied",
                provider.unitTexts().contains("STALE"));
    }

    @Test
    public void bilingualOrderChangesPresentationWithoutNewRequests() {
        mBridge.onNewVideo("video-1");
        mBridge.process(cues("Hello"));
        mFakeProvider.flushPending();

        int requestsBefore = mFakeProvider.getTranslateCallCount();
        int refreshesBefore = mRefreshCount.get();

        mBridge.setTranslationFirst(true);

        assertEquals("reordering is presentation only", requestsBefore,
                mFakeProvider.getTranslateCallCount());
        assertEquals("reordering must repaint the current cues", refreshesBefore + 1,
                mRefreshCount.get());
        assertEquals("Hello\n[ZH] Hello".replace("Hello\n[ZH] ", "[ZH] Hello\n"),
                mBridge.process(cues("Hello")).get(0).text.toString());
    }

    private static List<Cue> cues(String... texts) {
        List<Cue> list = new ArrayList<>();

        for (String text : texts) {
            list.add(new Cue(text));
        }

        return list;
    }

    /** Records requested unit texts; deliveries are released explicitly by the test. */
    private static final class RecordingProvider implements TranslationProvider {
        private final List<String> mUnitTexts = new ArrayList<>();
        private final List<Runnable> mDeliveries = new ArrayList<>();

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mUnitTexts.add(request.getSourceText());
            mDeliveries.add(() -> callback.onSuccess(TranslationResult.finalResult(
                    request.getSessionId(), request.getRequestId(), request.getUnit(),
                    "[ZH] " + request.getSourceText())));
            return new NopCall();
        }

        List<String> unitTexts() {
            return new ArrayList<>(mUnitTexts);
        }

        void flush() {
            List<Runnable> deliveries = new ArrayList<>(mDeliveries);
            mDeliveries.clear();
            for (Runnable delivery : deliveries) {
                delivery.run();
            }
        }
    }

    /** Supplies a controllable source load so a test can deliver responses out of order. */
    private static final class ManualSource {
        private final List<SmartTubeSubtitleSourceAdapter.SubtitleListListener> mListeners =
                new ArrayList<>();
        private String mVtt = "";
        private final SmartTubeSubtitleSourceAdapter mAdapter =
                new SmartTubeSubtitleSourceAdapter(
                        (videoId, listener) -> mListeners.add(listener), url -> mVtt);

        SmartTubeSubtitleSourceAdapter adapter() {
            return mAdapter;
        }

        int loadCount() {
            return mListeners.size();
        }

        void deliver(int loadIndex, String vtt) {
            mVtt = vtt;
            mListeners.get(loadIndex).onSubtitles(Collections.singletonList(new ManualSubtitle()));
        }
    }

    private static final class ManualSubtitle implements MediaSubtitle {
        @Override public String getBaseUrl() { return "http://example.com/timedtext?v=1"; }
        @Override public boolean isTranslatable() { return true; }
        @Override public String getLanguageCode() { return "en"; }
        @Override public String getVssId() { return ".en"; }
        @Override public String getName() { return "English"; }
        @Override public String getMimeType() { return "text/vtt"; }
        @Override public String getCodecs() { return ""; }
        @Override public String getType() { return "captions"; }
    }

    /**
     * Ignores cancellation and delivers only when the test says so; used to prove that the
     * bridge itself rejects stale callbacks instead of relying on provider cooperation.
     */
    private static final class StubbornProvider implements TranslationProvider {
        private final List<Runnable> mDeliveries = new ArrayList<>();
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            mDeliveries.add(() -> callback.onSuccess(TranslationResult.finalResult(
                    request.getSessionId(),
                    request.getRequestId(),
                    request.getUnit(),
                    "[ZH] " + request.getSourceText())));
            return new NopCall();
        }

        int getCallCount() {
            return mCallCount;
        }

        void deliverAll() {
            List<Runnable> deliveries = new ArrayList<>(mDeliveries);
            mDeliveries.clear();

            for (Runnable delivery : deliveries) {
                delivery.run();
            }
        }
    }

    private static final class NopCall implements TranslationCall {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
