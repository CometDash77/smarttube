package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleDisplayMode;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationStream;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class AiSubtitleCueBridgeModeTest {
    /** Test-only Prompt Profile; independent of any built-in or reference content. */
    private static final PromptProfile PROMPT = new PromptProfile(
            "test.prompt", "Test prompt",
            "Translate {{source_text}} into {{target_language}}.", 1, false);
    private AtomicBoolean mEnabled;
    private FakeTranslationProvider mProvider;
    private AiSubtitleCueBridge mBridge;
    private AtomicInteger mRefreshCount;

    @Before
    public void setUp() {
        mEnabled = new AtomicBoolean(true);
        mProvider = new FakeTranslationProvider(false);
        mBridge = new AiSubtitleCueBridge(mEnabled::get, mProvider, PROMPT);
        mRefreshCount = new AtomicInteger();

        mBridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
    }

    @Test
    public void sourceModeDoesNotRequestTranslation() {
        mBridge.setDisplayMode(AiSubtitleDisplayMode.SOURCE);

        List<Cue> input = cues("Hello");
        List<Cue> output = mBridge.process(input);

        assertSame(input, output);
        assertEquals(0, mProvider.getTranslateCallCount());
    }

    @Test
    public void deferredTranslationNotifiesRendererToRefreshCurrentCues() {
        mBridge.process(cues("Hello"));
        assertEquals(0, mRefreshCount.get());

        mProvider.flushPending();

        assertEquals("successful async result must refresh the current cue list",
                1, mRefreshCount.get());

        List<Cue> refreshed = mBridge.process(cues("Hello"));
        assertEquals("Hello\n[ZH] Hello", refreshed.get(0).text.toString());
    }

    @Test
    public void translationOnlyModeRendersOnlyCompletedTranslation() {
        mBridge.setDisplayMode(AiSubtitleDisplayMode.TRANSLATION_ONLY);
        mBridge.process(cues("Hello"));
        mProvider.flushPending();

        List<Cue> output = mBridge.process(cues("Hello"));

        assertEquals("[ZH] Hello", output.get(0).text.toString());
    }

    @Test
    public void modeChangeNotifiesRendererForCachedResults() {
        mBridge.process(cues("Hello"));
        mProvider.flushPending();

        int before = mRefreshCount.get();
        mBridge.setDisplayMode(AiSubtitleDisplayMode.TRANSLATION_ONLY);

        assertEquals(before + 1, mRefreshCount.get());
    }

    @Test
    public void runtimeStatusTracksTranslationLifecycle() {
        assertEquals(AiSubtitleCueBridge.RuntimeStatus.WAITING, mBridge.getRuntimeStatus());

        mBridge.process(cues("Hello"));
        assertEquals(AiSubtitleCueBridge.RuntimeStatus.TRANSLATING, mBridge.getRuntimeStatus());

        mProvider.flushPending();
        assertEquals(AiSubtitleCueBridge.RuntimeStatus.TRANSLATED, mBridge.getRuntimeStatus());
    }

    @Test
    public void failureKeepsReasonWithoutCredentials() {
        TranslationProvider failingProvider = (request, callback) -> {
            callback.onFailure(new TranslationFailure(
                    TranslationFailureCategory.AUTH, "auth required"));
            return new TranslationCall() {
                @Override public void cancel() {}
                @Override public boolean isCancelled() { return false; }
            };
        };
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, failingProvider, PROMPT);

        bridge.process(cues("Hello"));

        assertEquals(AiSubtitleCueBridge.RuntimeStatus.FAILED, bridge.getRuntimeStatus());
        assertEquals("auth required", bridge.getLastError());
    }

    @Test
    public void sourceModeNeverRequestsATranslationAndKeepsTheSourceLine() {
        StreamingModeProvider provider = new StreamingModeProvider();
        AiSubtitleCueBridge bridge = streamingBridge(provider);
        bridge.setDisplayMode(AiSubtitleDisplayMode.SOURCE);

        bridge.process(cues("Hello"));

        // Without a request there is no draft and no final to show, and there never will be.
        assertEquals("Hello", bridge.process(cues("Hello")).get(0).text.toString());
        assertEquals("SOURCE mode must not request a translation", 0, provider.getCallCount());
    }

    @Test
    public void translationOnlyShowsTheDraftThenTheFinal() {
        StreamingModeProvider provider = new StreamingModeProvider();
        AiSubtitleCueBridge bridge = streamingBridge(provider);
        bridge.setDisplayMode(AiSubtitleDisplayMode.TRANSLATION_ONLY);

        bridge.process(cues("Hello"));
        provider.emitPartial("草稿");
        assertEquals("草稿", bridge.process(cues("Hello")).get(0).text.toString());

        provider.complete("译文");
        assertEquals("译文", bridge.process(cues("Hello")).get(0).text.toString());
        assertEquals("one request per unit", 1, provider.getCallCount());
    }

    @Test
    public void translationOnlyFallsBackToTheSourceLineWhenTheTranslationFails() {
        StreamingModeProvider provider = new StreamingModeProvider();
        AiSubtitleCueBridge bridge = streamingBridge(provider);
        bridge.setDisplayMode(AiSubtitleDisplayMode.TRANSLATION_ONLY);

        bridge.process(cues("Hello"));
        provider.fail(TranslationFailureCategory.AUTH);

        assertEquals("a failed translation cannot leave the cue empty", "Hello",
                bridge.process(cues("Hello")).get(0).text.toString());
        assertEquals(AiSubtitleCueBridge.RuntimeStatus.FAILED, bridge.getRuntimeStatus());
    }

    @Test
    public void bilingualShowsTheDraftThenTheFinalInTheConfiguredOrder() {
        for (boolean translationFirst : new boolean[] {false, true}) {
            StreamingModeProvider provider = new StreamingModeProvider();
            AiSubtitleCueBridge bridge = streamingBridge(provider);
            bridge.setTranslationFirst(translationFirst);

            bridge.process(cues("Hello"));
            provider.emitPartial("草稿");

            assertEquals(translationFirst ? "草稿\nHello" : "Hello\n草稿",
                    bridge.process(cues("Hello")).get(0).text.toString());

            provider.complete("译文");

            assertEquals(translationFirst ? "译文\nHello" : "Hello\n译文",
                    bridge.process(cues("Hello")).get(0).text.toString());
            assertEquals("one request per unit", 1, provider.getCallCount());
        }
    }

    private AiSubtitleCueBridge streamingBridge(StreamingModeProvider provider) {
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);

        return bridge;
    }

    /** Streams one draft at a time and can complete or fail on demand. */
    private static final class StreamingModeProvider implements TranslationProvider {
        private TranslationCallback mCallback;
        private TranslationRequest mRequest;
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            mRequest = request;
            mCallback = callback;

            return new TranslationCall() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }

        void emitPartial(String text) {
            ((TranslationStream) mCallback).onPartial(TranslationResult.partialResult(
                    mRequest.getSessionId(), mRequest.getRequestId(), mRequest.getUnit(), text));
        }

        void complete(String text) {
            mCallback.onSuccess(TranslationResult.finalResult(
                    mRequest.getSessionId(), mRequest.getRequestId(), mRequest.getUnit(), text));
        }

        void fail(TranslationFailureCategory category) {
            mCallback.onFailure(new TranslationFailure(category, "synthetic " + category));
        }

        int getCallCount() {
            return mCallCount;
        }
    }

    private static List<Cue> cues(String... texts) {
        List<Cue> list = new ArrayList<>();
        for (String text : texts) {
            list.add(new Cue(text));
        }
        return list;
    }
}
