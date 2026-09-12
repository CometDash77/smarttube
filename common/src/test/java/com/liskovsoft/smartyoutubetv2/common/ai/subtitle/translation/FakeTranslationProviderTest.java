package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FakeTranslationProviderTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "track-1", "en");
    private static final TranslationProfile PROFILE = new TranslationProfile(
            "profile-1", "openai-chat", "https://api.example.com", "model-1", "prompt-1", 1, "zh");
    private static final TranslationSessionId SESSION = new TranslationSessionId(
            "video-1", TRACK, PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION);

    private static final class CapturingCallback implements TranslationCallback {
        private TranslationResult mResult;
        private TranslationFailure mFailure;

        @Override
        public void onSuccess(TranslationResult result) {
            mResult = result;
        }

        @Override
        public void onFailure(TranslationFailure failure) {
            mFailure = failure;
        }
    }

    private static TranslationRequest request(long requestId, String source) {
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(TRACK, 0)), source);

        return new TranslationRequest(SESSION, requestId, unit);
    }

    @Test
    public void immediateModeTranslatesWithPrefixAndSameIdentity() {
        FakeTranslationProvider provider = new FakeTranslationProvider();
        CapturingCallback callback = new CapturingCallback();

        TranslationCall call = provider.translate(request(3, "Hello world"), callback);

        assertNotNull(call);
        assertNull(callback.mFailure);
        assertNotNull(callback.mResult);
        assertEquals("[ZH] Hello world", callback.mResult.getTranslatedText());
        assertEquals(SESSION, callback.mResult.getSessionId());
        assertEquals(3, callback.mResult.getRequestId());
        assertTrue("the baseline contract is a complete final result", callback.mResult.isFinal());
    }

    @Test
    public void nullRequestFailsInsteadOfFabricatingContent() {
        FakeTranslationProvider provider = new FakeTranslationProvider();
        CapturingCallback callback = new CapturingCallback();

        provider.translate(null, callback);

        assertNull("no fabricated result for a null request", callback.mResult);
        assertNotNull("a normalized failure is expected", callback.mFailure);
        assertEquals(TranslationFailureCategory.INVALID_OUTPUT, callback.mFailure.getCategory());
    }

    @Test
    public void deferredModeDoesNotDeliverBeforeFlush() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);
        CapturingCallback callback = new CapturingCallback();

        provider.translate(request(9, "Hi"), callback);

        assertNull(callback.mResult);
        assertNull(callback.mFailure);

        provider.flushPending();

        assertNotNull(callback.mResult);
        assertEquals("[ZH] Hi", callback.mResult.getTranslatedText());
        assertEquals(SESSION, callback.mResult.getSessionId());
        assertEquals(9, callback.mResult.getRequestId());
    }

    @Test
    public void cancelledCallIsNeverDelivered() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);
        CapturingCallback callback = new CapturingCallback();

        TranslationCall call = provider.translate(request(1, "Hello"), callback);
        call.cancel();

        assertTrue(call.isCancelled());

        provider.flushPending();

        assertNull(callback.mResult);
        assertNull(callback.mFailure);
    }

    @Test
    public void translateCallCountTracksEveryInvocation() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);

        provider.translate(request(1, "A"), new CapturingCallback());
        provider.translate(request(2, "B"), new CapturingCallback());
        provider.translate(null, new CapturingCallback());

        assertEquals(3, provider.getTranslateCallCount());
    }
}
