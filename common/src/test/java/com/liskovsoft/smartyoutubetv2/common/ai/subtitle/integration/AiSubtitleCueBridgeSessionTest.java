package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionSnapshot;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests for M03-C2/C4: session generation and scheduling-epoch ownership inside the
 * bridge, plus the C4 result-identity guarantees (final-only caching, session/request/coverage
 * matching). Verifies that identity changes create new generations, repeated identical events
 * stay idempotent, seeks only advance the epoch, and stale or invalid callbacks cannot mutate
 * state.
 */
public class AiSubtitleCueBridgeSessionTest {
    private AtomicBoolean mEnabled;
    private AiSubtitleCueBridge mBridge;

    @Before
    public void setUp() {
        mEnabled = new AtomicBoolean(true);
        mBridge = new AiSubtitleCueBridge(mEnabled::get, new FakeTranslationProvider(false));
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
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider);
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

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, mismatchedProvider);
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

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, partialOnlyProvider);
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

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, wrongCoverageProvider);
        bridge.onNewVideo("video-1");

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a result that answers different coverage must never be cached",
                "Hello", output.get(0).text.toString());
    }

    private static List<Cue> cues(String... texts) {
        List<Cue> list = new ArrayList<>();

        for (String text : texts) {
            list.add(new Cue(text));
        }

        return list;
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
