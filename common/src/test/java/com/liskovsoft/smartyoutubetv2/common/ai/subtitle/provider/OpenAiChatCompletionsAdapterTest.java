package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM protocol tests for OpenAI-compatible Chat Completions normal responses.
 * Every HTTP interaction goes through a fake executor; no public network is touched.
 */
public class OpenAiChatCompletionsAdapterTest {
    private static final String SECRET = "synthetic-provider-key";

    @Test
    public void successBuildsExpectedRequestAndMapsTranslationResult() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}", "req-success");
        RecordingSecretStore secrets = new RecordingSecretStore(SECRET);
        OpenAiChatCompletionsAdapter adapter = adapter(executor, profile(
                "https://api.example.com", null, null), secrets);
        RecordingCallback callback = new RecordingCallback();

        TranslationCall call = adapter.translate(request("Hello"), callback);

        assertTrue(callback.success);
        assertEquals("你好", callback.result.getTranslatedText());
        assertEquals("req-success", ((OpenAiChatCompletionsAdapter.Call) call).getRequestId());
        assertEquals(1, executor.getExecuteCount());
        HttpRequestExecutor.HttpRequest request = executor.getLastRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("https://api.example.com/v1/chat/completions", request.getUrl());
        assertEquals("Bearer " + SECRET, request.getHeaders().get("Authorization"));
        assertEquals("application/json", request.getHeaders().get("Content-Type"));
        assertTrue(request.getBody().contains("\"model\":\"model-1\""));
        assertTrue(request.getBody().contains("\"stream\":false"));
        assertTrue(request.getBody().contains("Translate"));
        assertTrue(request.getBody().contains("zh"));
        assertTrue(request.getBody().contains("Hello"));
    }

    @Test
    public void baseUrlNormalizationAvoidsDuplicateVersionAndChatPaths() {
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com"));
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com/v1/"));
        assertEquals("https://api.example.com/api/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com/api/v1"));
        assertEquals("https://api.example.com/v1beta/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com/v1beta"));
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl(
                        "https://api.example.com/v1/chat/completions/"));
    }

    @Test
    public void requestBodyEscapesQuotesBackslashesAndNewlines() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-escape");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        String source = "Hello \"quoted\"\n世界\\end";

        adapter.translate(request(source), new RecordingCallback());

        String body = executor.getLastRequest().getBody();
        assertTrue(body.contains("Hello \\\"quoted\\\"\\n世界\\\\end"));
    }

    @Test
    public void customHeadersAreSentAndAuthorizationIsNeverDuplicated() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Attribution", "smartube");
        headers.put("authorization", "Bearer wrong-value");
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-headers");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", headers, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        Map<String, String> sent = executor.getLastRequest().getHeaders();
        assertEquals("smartube", sent.get("X-Attribution"));
        int authCount = 0;
        for (Map.Entry<String, String> entry : sent.entrySet()) {
            if ("authorization".equalsIgnoreCase(entry.getKey())) {
                authCount++;
                assertEquals("Bearer " + SECRET, entry.getValue());
            }
        }
        assertEquals(1, authCount);
    }

    @Test
    public void missingCredentialFailsAsAuthWithoutHttpRequest() {
        FakeHttpExecutor executor = FakeHttpExecutor.success("{}", "req-none");
        RecordingSecretStore secrets = new RecordingSecretStore(null);
        RecordingCallback callback = new RecordingCallback();

        adapter(executor, profile("https://api.example.com/v1", null, null), secrets)
                .translate(request("Hello"), callback);

        assertFailure(callback, TranslationFailureCategory.AUTH);
        assertEquals(0, executor.getExecuteCount());
    }

    @Test
    public void secretStoreFailureFailsAsAuthWithoutHttpRequest() {
        FakeHttpExecutor executor = FakeHttpExecutor.success("{}", "req-none");
        RecordingSecretStore secrets = new RecordingSecretStore(SECRET);
        secrets.failure = new SecretStore.Failure(SecretStore.FailureReason.INVALIDATED);
        RecordingCallback callback = new RecordingCallback();

        adapter(executor, profile("https://api.example.com/v1", null, null), secrets)
                .translate(request("Hello"), callback);

        assertFailure(callback, TranslationFailureCategory.AUTH);
        assertEquals(0, executor.getExecuteCount());
    }

    @Test
    public void httpStatusesMapToNormalizedFailureCategories() {
        assertStatusFailure(400, TranslationFailureCategory.PROTOCOL);
        assertStatusFailure(401, TranslationFailureCategory.AUTH);
        assertStatusFailure(403, TranslationFailureCategory.AUTH);
        assertStatusFailure(408, TranslationFailureCategory.TIMEOUT);
        assertStatusFailure(429, TranslationFailureCategory.RATE_LIMITED);
        assertStatusFailure(500, TranslationFailureCategory.SERVER);
        assertStatusFailure(503, TranslationFailureCategory.SERVER);
        assertStatusFailure(504, TranslationFailureCategory.TIMEOUT);
    }

    @Test
    public void emptyAndMalformedBodiesMapToProtocolFailure() {
        assertBodyFailure("", "req-empty", TranslationFailureCategory.PROTOCOL);
        assertBodyFailure("not-json", "req-malformed", TranslationFailureCategory.PROTOCOL);
        assertBodyFailure("{\"choices\":\"wrong\"}", "req-shape", TranslationFailureCategory.PROTOCOL);
    }

    @Test
    public void validResponseWithoutUsableContentMapsToInvalidOutput() {
        assertBodyFailure("{\"choices\":[]}", "req-no-choice",
                TranslationFailureCategory.INVALID_OUTPUT);
        assertBodyFailure("{\"choices\":[{\"message\":{\"content\":\"  \"}}]}",
                "req-blank", TranslationFailureCategory.INVALID_OUTPUT);
    }

    @Test
    public void transportFailuresMapToTimeoutAndNetwork() {
        assertTransportFailure(HttpRequestExecutor.FailureReason.TIMEOUT,
                TranslationFailureCategory.TIMEOUT);
        assertTransportFailure(HttpRequestExecutor.FailureReason.NETWORK,
                TranslationFailureCategory.NETWORK);
    }

    @Test
    public void cancellationSuppressesLateResultAndCancelsTransport() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingCallback callback = new RecordingCallback();

        TranslationCall call = adapter.translate(request("Hello"), callback);
        call.cancel();
        executor.succeed("{\"choices\":[{\"message\":{\"content\":\"late\"}}]}", "req-late");

        assertTrue(call.isCancelled());
        assertTrue(executor.getLastCall().isCancelled());
        assertFalse(callback.completed);
    }

    @Test
    public void timeoutOptionIsPropagatedToTheHttpExecutor() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(OpenAiChatCompletionsAdapter.OPTION_TIMEOUT_MS, "1234");
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-timeout");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, options),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        assertEquals(1234, executor.getLastRequest().getTimeoutMs());
    }

    @Test
    public void requestIdIsCapturedFromSuccessAndFailure() {
        FakeHttpExecutor success = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-ok");
        OpenAiChatCompletionsAdapter successAdapter = adapter(success,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        TranslationCall successCall = successAdapter.translate(request("Hello"),
                new RecordingCallback());
        assertEquals("req-ok", ((OpenAiChatCompletionsAdapter.Call) successCall).getRequestId());

        FakeHttpExecutor failure = FakeHttpExecutor.failure(
                new HttpRequestExecutor.HttpFailure(HttpRequestExecutor.FailureReason.NETWORK,
                        "network failed", "req-failure"));
        OpenAiChatCompletionsAdapter failureAdapter = adapter(failure,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingCallback callback = new RecordingCallback();
        TranslationCall failureCall = failureAdapter.translate(request("Hello"), callback);
        assertFailure(callback, TranslationFailureCategory.NETWORK);
        assertEquals("req-failure", ((OpenAiChatCompletionsAdapter.Call) failureCall).getRequestId());
    }

    @Test
    public void diagnosticsNeverContainTheAuthorizationValue() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-redaction");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        adapter.translate(request("Hello"), new RecordingCallback());

        assertFalse(adapter.toString().contains(SECRET));
        assertFalse(executor.getLastRequest().toString().contains(SECRET));
        assertFalse(executor.getLastRequest().toString().contains("Hello"));
    }

    private static OpenAiChatCompletionsAdapter adapter(FakeHttpExecutor executor,
                                                         ProviderProfile profile,
                                                         SecretStore secrets) {
        return new OpenAiChatCompletionsAdapter(executor, profile, secrets);
    }

    private static ProviderProfile profile(String baseUrl, Map<String, String> headers,
                                           Map<String, String> options) {
        return new ProviderProfile("profile-1", "Test Provider",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl, "credential-ref-1", "model-1", Collections.<String>emptyList(),
                headers, options);
    }

    private static TranslationRequest request(String sourceText) {
        SourceTrackId track = new SourceTrackId("video-1", "track-1", "ja");
        TranslationProfile profile = new TranslationProfile("profile-1",
                "openai-chat-completions", "https://api.example.com/v1",
                "model-1", "prompt-1", 1, "zh");
        TranslationSessionId session = new TranslationSessionId(
                "video-1", track, profile, TranslationSessionId.ENGINE_SCHEMA_VERSION);
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(track, 0)), sourceText);
        return new TranslationRequest(session, 42, unit);
    }

    private static void assertStatusFailure(int status,
                                            TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"error\":{\"message\":\"private provider detail\"}}", "req-" + status);
        executor.responseStatusCode = status;
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);

        assertFailure(callback, expected);
        assertFalse(callback.failure.getMessage().contains("private provider detail"));
    }

    private static void assertBodyFailure(String body, String requestId,
                                          TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(body, requestId);
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);
        assertFailure(callback, expected);
    }

    private static void assertTransportFailure(HttpRequestExecutor.FailureReason reason,
                                               TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.failure(
                new HttpRequestExecutor.HttpFailure(reason, "synthetic transport failure", "req-x"));
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);
        assertFailure(callback, expected);
    }

    private static void assertFailure(RecordingCallback callback,
                                      TranslationFailureCategory expected) {
        assertFalse(callback.success);
        assertNotNull(callback.failure);
        assertEquals(expected, callback.failure.getCategory());
    }

    private static final class RecordingSecretStore implements SecretStore {
        private final String value;
        private SecretStore.Failure failure;

        RecordingSecretStore(String value) {
            this.value = value;
        }

        @Override
        public String get(String reference) {
            if (failure != null) {
                throw failure;
            }
            return value;
        }

        @Override
        public void put(String reference, String secret) {
        }

        @Override
        public void delete(String reference) {
        }
    }

    private static final class RecordingCallback implements TranslationCallback {
        private boolean success;
        private boolean completed;
        private TranslationResult result;
        private TranslationFailure failure;

        @Override
        public void onSuccess(TranslationResult result) {
            completed = true;
            success = true;
            this.result = result;
        }

        @Override
        public void onFailure(TranslationFailure failure) {
            completed = true;
            success = false;
            this.failure = failure;
        }
    }

    private static final class FakeHttpExecutor implements HttpRequestExecutor {
        private HttpRequest lastRequest;
        private HttpCallback lastCallback;
        private FakeHttpCall lastCall;
        private HttpResponse response;
        private HttpFailure failure;
        private boolean deferred;
        private int executeCount;
        private int responseStatusCode;

        static FakeHttpExecutor success(String body, String requestId) {
            FakeHttpExecutor executor = new FakeHttpExecutor();
            executor.response = new HttpResponse(200, body, requestId);
            return executor;
        }

        static FakeHttpExecutor failure(HttpFailure failure) {
            FakeHttpExecutor executor = new FakeHttpExecutor();
            executor.failure = failure;
            return executor;
        }

        static FakeHttpExecutor deferred() {
            FakeHttpExecutor executor = new FakeHttpExecutor();
            executor.deferred = true;
            return executor;
        }

        @Override
        public HttpCall execute(HttpRequest request, HttpCallback callback) {
            executeCount++;
            lastRequest = request;
            lastCallback = callback;
            lastCall = new FakeHttpCall();
            if (!deferred) {
                deliver();
            }
            return lastCall;
        }

        void succeed(String body, String requestId) {
            response = new HttpResponse(200, body, requestId);
            failure = null;
            deliver();
        }

        void deliver() {
            if (failure != null) {
                lastCallback.onFailure(failure);
            } else if (response != null) {
                response = new HttpResponse(responseStatusCode > 0 ? responseStatusCode : 200,
                        response.getBody(), response.getRequestId());
                lastCallback.onSuccess(response);
            }
        }

        HttpRequest getLastRequest() {
            return lastRequest;
        }

        FakeHttpCall getLastCall() {
            return lastCall;
        }

        int getExecuteCount() {
            return executeCount;
        }
    }

    private static final class FakeHttpCall implements HttpRequestExecutor.HttpCall {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
