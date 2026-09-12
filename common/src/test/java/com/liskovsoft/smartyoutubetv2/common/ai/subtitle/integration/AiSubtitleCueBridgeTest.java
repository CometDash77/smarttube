package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests: the bridge is exercised through its Android-free constructor.
 *
 * <p>Robolectric 4.6.1 cannot instrument bytecode on JDK 17 (its ASM rejects class file
 * major version 61), so cue-bridge coverage lives here instead of in a Robolectric suite;
 * the deviation is recorded in the M02 worker report.</p>
 */
public class AiSubtitleCueBridgeTest {
    private AtomicBoolean mEnabled;
    private FakeTranslationProvider mProvider;
    private AiSubtitleCueBridge mBridge;

    @Before
    public void setUp() {
        mEnabled = new AtomicBoolean(false);
        mProvider = new FakeTranslationProvider(false);
        mBridge = new AiSubtitleCueBridge(mEnabled::get, mProvider);
    }

    @Test
    public void disabledBridgeReturnsSameListWithoutRequests() {
        List<Cue> input = cues("Hello");

        List<Cue> output = mBridge.process(input);

        assertSame("disabled path must not rebuild cues", input, output);
        assertEquals(0, mProvider.getTranslateCallCount());
    }

    @Test
    public void nullAndEmptyListsPassThrough() {
        assertNull(mBridge.process(null));

        List<Cue> empty = new ArrayList<>();
        assertSame(empty, mBridge.process(empty));
    }

    @Test
    public void nullAndBlankCueTextsAreLeftUnchangedWithoutRequests() {
        enable();
        List<Cue> input = cues(null, "", "   ");

        List<Cue> output = mBridge.process(input);

        assertSame(input, output);
        assertEquals(0, mProvider.getTranslateCallCount());
    }

    @Test
    public void enabledWithNoCompletedResultKeepsSourceCue() {
        enable();
        List<Cue> input = cues("Hello");

        List<Cue> first = mBridge.process(input);

        assertSame("nothing to decorate yet", input, first);
        assertEquals(1, mProvider.getTranslateCallCount());
    }

    @Test
    public void immediateFakeDecoratesOnTheFirstAndOnlyProcessCall() {
        enable();
        FakeTranslationProvider immediateProvider = new FakeTranslationProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, immediateProvider);

        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("production Fake output must appear on this same call",
                "Hello\n[ZH] Hello", output.get(0).text.toString());
        assertEquals(1, immediateProvider.getTranslateCallCount());

        List<Cue> second = bridge.process(cues("Hello"));

        assertEquals("Hello\n[ZH] Hello", second.get(0).text.toString());
        assertEquals("cached; no duplicate request", 1, immediateProvider.getTranslateCallCount());
    }

    @Test
    public void enabledAfterCompletionRendersTwoLineCue() {
        enable();
        mBridge.process(cues("Hello"));
        mProvider.flushPending();

        List<Cue> second = mBridge.process(cues("Hello"));

        assertEquals(1, second.size());
        assertEquals("Hello\n[ZH] Hello", second.get(0).text.toString());
        assertEquals("completed result must be cached", 1, mProvider.getTranslateCallCount());
    }

    @Test
    public void duplicateInFlightRequestsAreDeduplicated() {
        enable();

        mBridge.process(cues("Hello"));
        mBridge.process(cues("Hello"));
        mBridge.process(cues("Hello"));

        assertEquals(1, mProvider.getTranslateCallCount());
    }

    @Test
    public void failedRequestsKeepSourceOnly() {
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, new AlwaysFailingProvider());
        enable();

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("Hello", output.get(0).text.toString());
    }

    @Test
    public void providerExceptionIsIsolatedToSourceOnlyOutput() {
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, new ThrowingProvider());
        enable();
        List<Cue> input = cues("Hello");

        List<Cue> output = bridge.process(input);

        assertSame(input, output);
    }

    @Test
    public void newVideoInvalidatesResultsAndInFlightWork() {
        enable();
        mBridge.process(cues("Hello"));

        mBridge.onNewVideo("nextVideo");
        mProvider.flushPending();

        List<Cue> output = mBridge.process(cues("Hello"));

        assertEquals("Hello", output.get(0).text.toString());
        assertEquals("re-requested under the new generation", 2, mProvider.getTranslateCallCount());
    }

    @Test
    public void trackChangeInvalidatesResultsAndInFlightWork() {
        enable();
        mBridge.process(cues("Hello"));
        mProvider.flushPending();

        mBridge.onSubtitleTrackChanged("subtitle:7:2:en:42");

        List<Cue> output = mBridge.process(cues("Hello"));

        assertEquals("previous track state must not leak", "Hello", output.get(0).text.toString());
        assertEquals(2, mProvider.getTranslateCallCount());
    }

    @Test
    public void lateResultAfterSeekIsRejected() {
        StubbornProvider provider = new StubbornProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider);
        enable();

        bridge.process(cues("Hello"));

        bridge.onSeek(1000);
        provider.deliverAll();

        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("stale result must not leak into the current session", "Hello", output.get(0).text.toString());
        assertEquals("stale result must not be cached", 2, provider.getCallCount());
    }

    @Test
    public void seekKeepsPreviouslyCompletedResults() {
        enable();
        mBridge.process(cues("Hello"));
        mProvider.flushPending();

        mBridge.onSeek(600000);

        List<Cue> output = mBridge.process(cues("Hello"));

        assertEquals("Hello\n[ZH] Hello", output.get(0).text.toString());
        assertEquals("cache hit, no new request", 1, mProvider.getTranslateCallCount());
    }

    @Test
    public void pauseBlocksNewWorkAndResumeAllowsItAgain() {
        enable();
        mBridge.onPause();

        mBridge.process(cues("Hello"));
        assertEquals(0, mProvider.getTranslateCallCount());

        mBridge.onPlay();
        mBridge.process(cues("Hello"));
        assertEquals(1, mProvider.getTranslateCallCount());
    }

    @Test
    public void releaseCancelsInFlightWorkAndClearsState() {
        enable();
        mBridge.process(cues("Hello"));

        mBridge.onRelease();
        mProvider.flushPending();

        List<Cue> output = mBridge.process(cues("Hello"));

        assertEquals("Hello", output.get(0).text.toString());
        assertEquals(2, mProvider.getTranslateCallCount());
    }

    @Test
    public void disablingImmediatelyCancelsInFlightWork() {
        CancelTrackingProvider provider = new CancelTrackingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider);
        enable();

        bridge.process(cues("Hello"));

        mEnabled.set(false);
        bridge.onEnabledChanged(false);

        assertTrue("in-flight call must be cancelled during the disable notification",
                provider.isCancelled());
    }

    @Test
    public void stubbornLateCallbackAfterDisableIsRejected() {
        StubbornProvider provider = new StubbornProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider);
        enable();

        bridge.process(cues("Hello"));

        mEnabled.set(false);
        bridge.onEnabledChanged(false);
        provider.deliverAll();

        enable();
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("late result after disable must not leak", "Hello", output.get(0).text.toString());
        assertEquals("re-enable starts from clean state", 2, provider.getCallCount());

        provider.deliverAll();
        List<Cue> completed = bridge.process(cues("Hello"));

        assertEquals("re-enable must complete a fresh translation",
                "Hello\n[ZH] Hello", completed.get(0).text.toString());
        assertEquals("completed result is cached; no third request", 2, provider.getCallCount());
    }

    @Test
    public void disablingTheSettingClearsStateAndRestoresSourceOnly() {
        enable();
        mBridge.process(cues("Hello"));
        mProvider.flushPending();

        mEnabled.set(false);
        mBridge.onEnabledChanged(false);
        List<Cue> input = cues("Hello");
        List<Cue> disabled = mBridge.process(input);
        assertSame(input, disabled);

        enable();
        List<Cue> reenabled = mBridge.process(cues("Hello"));

        assertEquals("previous state must not leak across disable", "Hello", reenabled.get(0).text.toString());
        assertEquals(2, mProvider.getTranslateCallCount());
    }

    @Test
    public void sourceTextWithNewlineIsDecoratedAsIs() {
        enable();
        mBridge.process(cues("line one\nline two"));
        mProvider.flushPending();

        List<Cue> output = mBridge.process(cues("line one\nline two"));

        assertEquals("line one\nline two\n[ZH] line one\nline two", output.get(0).text.toString());
    }

    private void enable() {
        mEnabled.set(true);
    }

    private static List<Cue> cues(String... texts) {
        List<Cue> list = new ArrayList<>();

        for (String text : texts) {
            list.add(new Cue(text));
        }

        return list;
    }

    /** Synchronously fails every request with a normalized failure. */
    private static final class AlwaysFailingProvider implements TranslationProvider {
        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            callback.onFailure(new TranslationFailure(
                    TranslationFailureCategory.INVALID_OUTPUT, "synthetic failure"));
            return new NopCall();
        }
    }

    /** Throws synchronously from translate() to prove bridge-level isolation. */
    private static final class ThrowingProvider implements TranslationProvider {
        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            throw new IllegalStateException("synthetic provider crash");
        }
    }

    /** Never completes and exposes the issued call so tests can prove cancellation on disable. */
    private static final class CancelTrackingProvider implements TranslationProvider {
        private final CancelTrackingCall mCall = new CancelTrackingCall();

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            return mCall;
        }

        boolean isCancelled() {
            return mCall.mCancelled;
        }

        private static final class CancelTrackingCall implements TranslationCall {
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
