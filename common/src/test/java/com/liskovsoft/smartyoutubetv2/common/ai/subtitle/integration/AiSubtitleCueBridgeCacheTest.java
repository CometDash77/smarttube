package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM tests for M03-C3: the bridge stores results through the session-scoped
 * {@code TranslationCache} keyed by the complete output identity, so equal source text
 * across video, track, or session cannot alias, failures are never cached, and the cache
 * stays bounded to the active session scope.
 */
public class AiSubtitleCueBridgeCacheTest {
    /** Test-only Prompt Profile; independent of any built-in or reference content. */
    private static final PromptProfile PROMPT = new PromptProfile(
            "test.prompt", "Test prompt",
            "Translate {{source_text}} into {{target_language}}.", 1, false);
    private AtomicBoolean mEnabled;

    @Before
    public void setUp() {
        mEnabled = new AtomicBoolean(true);
    }

    @Test
    public void terminalFailureIsNotCachedAndKeepsSourceOnly() {
        CountingFailureProvider provider = new CountingFailureProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onNewVideo("video-1");

        List<Cue> first = bridge.process(cues("Hello"));

        assertEquals("a failure must leave the cue source-only", "Hello", first.get(0).text.toString());

        bridge.process(cues("Hello"));

        assertEquals("a terminal failed translation must not be cached or retried automatically",
                1, provider.getCallCount());
    }

    @Test
    public void sameTextOnAnotherTrackIsReRequested() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onNewVideo("video-1");
        bridge.onSubtitleTrackChanged("subtitle:en:asr-1");

        bridge.process(cues("Hello"));
        provider.flushPending();

        bridge.onSubtitleTrackChanged("subtitle:ja:asr-2");
        bridge.process(cues("Hello"));

        assertEquals("equal text on another track must not alias the cached result",
                2, provider.getTranslateCallCount());
    }

    @Test
    public void cacheIsClearedWhenSessionIdentityChanges() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);

        bridge.onNewVideo("video-1");
        bridge.process(cues("Hello"));
        provider.flushPending();

        bridge.onNewVideo("video-2");
        bridge.process(cues("Hello"));
        provider.flushPending();

        bridge.onNewVideo("video-1");
        bridge.process(cues("Hello"));

        assertEquals("the cache is bounded to the active session scope",
                3, provider.getTranslateCallCount());
    }

    @Test
    public void authFailureStillLeavesCueSourceOnly() {
        AuthFailureProvider provider = new AuthFailureProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onNewVideo("video-1");

        List<Cue> result = bridge.process(cues("Hello"));

        assertEquals("an auth failure must leave the cue source-only",
                "Hello", result.get(0).text.toString());
        assertEquals(1, provider.getCallCount());
    }


    private static List<Cue> cues(String... texts) {
        List<Cue> list = new ArrayList<>();

        for (String text : texts) {
            list.add(new Cue(text));
        }

        return list;
    }

    private static final class AuthFailureProvider implements TranslationProvider {
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            callback.onFailure(new TranslationFailure(
                    TranslationFailureCategory.AUTH, "synthetic auth failure"));
            return new NopCall();
        }

        int getCallCount() {
            return mCallCount;
        }
    }


    /** Synchronously fails every request with a normalized failure and counts them. */
    private static final class CountingFailureProvider implements TranslationProvider {
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            callback.onFailure(new TranslationFailure(
                    TranslationFailureCategory.INVALID_OUTPUT, "synthetic failure"));
            return new NopCall();
        }

        int getCallCount() {
            return mCallCount;
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
