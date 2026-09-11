package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FakeTranslationProviderTest {
    private static final class CapturingCallback implements TranslationCallback {
        private TranslationResult mResult;
        private Throwable mError;

        @Override
        public void onSuccess(TranslationResult result) {
            mResult = result;
        }

        @Override
        public void onFailure(Throwable error) {
            mError = error;
        }
    }

    @Test
    public void immediateModeTranslatesWithPrefixAndSameIdentity() {
        FakeTranslationProvider provider = new FakeTranslationProvider();
        CapturingCallback callback = new CapturingCallback();

        TranslationCall call = provider.translate(new TranslationRequest(7, 3, "Hello world", "en", "zh"), callback);

        assertNotNull(call);
        assertNull(callback.mError);
        assertNotNull(callback.mResult);
        assertEquals("[ZH] Hello world", callback.mResult.getTranslatedText());
        assertEquals(7, callback.mResult.getGeneration());
        assertEquals(3, callback.mResult.getRequestId());
    }

    @Test
    public void blankSourceTextFailsInsteadOfFabricatingContent() {
        FakeTranslationProvider provider = new FakeTranslationProvider();

        for (String blank : new String[] {null, "", "   "}) {
            CapturingCallback callback = new CapturingCallback();

            provider.translate(new TranslationRequest(1, 1, blank, null, "zh"), callback);

            assertNull("no fabricated result for: [" + blank + "]", callback.mResult);
            assertNotNull("failure expected for: [" + blank + "]", callback.mError);
        }
    }

    @Test
    public void deferredModeDoesNotDeliverBeforeFlush() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);
        CapturingCallback callback = new CapturingCallback();

        provider.translate(new TranslationRequest(5, 9, "Hi", null, "zh"), callback);

        assertNull(callback.mResult);
        assertNull(callback.mError);

        provider.flushPending();

        assertNotNull(callback.mResult);
        assertEquals("[ZH] Hi", callback.mResult.getTranslatedText());
        assertEquals(5, callback.mResult.getGeneration());
        assertEquals(9, callback.mResult.getRequestId());
    }

    @Test
    public void cancelledCallIsNeverDelivered() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);
        CapturingCallback callback = new CapturingCallback();

        TranslationCall call = provider.translate(new TranslationRequest(1, 1, "Hello", null, "zh"), callback);
        call.cancel();

        assertTrue(call.isCancelled());

        provider.flushPending();

        assertNull(callback.mResult);
        assertNull(callback.mError);
    }

    @Test
    public void translateCallCountTracksEveryInvocation() {
        FakeTranslationProvider provider = new FakeTranslationProvider(false);

        provider.translate(new TranslationRequest(1, 1, "A", null, "zh"), new CapturingCallback());
        provider.translate(new TranslationRequest(1, 2, "B", null, "zh"), new CapturingCallback());
        provider.translate(new TranslationRequest(1, 3, "   ", null, "zh"), new CapturingCallback());

        assertEquals(3, provider.getTranslateCallCount());
    }
}
