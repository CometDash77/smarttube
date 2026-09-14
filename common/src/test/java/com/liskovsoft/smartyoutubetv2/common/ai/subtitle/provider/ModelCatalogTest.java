package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM tests for model discovery and normalized connection outcomes.
 */
public class ModelCatalogTest {
    private static final String SECRET = "synthetic-discovery-key";

    @Test
    public void discoveryNormalizesDuplicatesAndBlankIds() {
        FakeExecutor executor = FakeExecutor.success(
                jsonData("model-a", " model-a ", "", "model-b"), "req-models");
        ModelCatalog catalog = new ModelCatalog(executor);
        RecordingCallback callback = new RecordingCallback();

        catalog.discover(profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET), callback);

        assertTrue(callback.result.isSuccess());
        assertEquals(Arrays.asList("model-a", "model-b"), callback.result.getModels());
    }

    @Test
    public void discoveryUsesBaseUrlAndCustomHeadersWithoutLeakingSecret() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Provider", "custom");
        FakeExecutor executor = FakeExecutor.success(jsonData(), "req-headers");
        ModelCatalog catalog = new ModelCatalog(executor);

        catalog.discover(profile("https://proxy.example/v1", headers, null),
                new RecordingSecretStore(SECRET), new RecordingCallback());

        HttpRequestExecutor.HttpRequest request = executor.getLastRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("https://proxy.example/v1/models", request.getUrl());
        assertEquals("Bearer " + SECRET, request.getHeaders().get("Authorization"));
        assertEquals("custom", request.getHeaders().get("X-Provider"));
        assertFalse(request.toString().contains(SECRET));
    }

    @Test
    public void presetModelPathOverrideIsHonored() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(ProviderPreset.OPTION_MODEL_DISCOVERY_PATH, "/models");

        assertEquals("https://api.deepseek.com/models",
                ModelCatalog.modelsUrl(profile("https://api.deepseek.com", null, options)));
        assertEquals("https://api.anthropic.com/v1/models",
                ModelCatalog.modelsUrl(profile("https://api.anthropic.com", null, null)));
    }

    @Test
    public void discoveryFailureLeavesManualModelUsable() {
        FakeExecutor executor = FakeExecutor.failure(
                new HttpRequestExecutor.HttpFailure(HttpRequestExecutor.FailureReason.NETWORK,
                        "network failed", "req-fail"));
        ModelCatalog catalog = new ModelCatalog(executor);
        RecordingCallback callback = new RecordingCallback();
        ProviderProfile profile = profile("https://api.example.com/v1", null, null);

        catalog.discover(profile, new RecordingSecretStore(SECRET), callback);

        assertTrue(callback.result.isFailure());
        assertEquals(TranslationFailureCategory.NETWORK,
                callback.result.getFailure().getCategory());
        assertEquals("manual-model",
                ModelCatalog.repairSelectedModel(profile, Collections.<String>emptyList()));
    }

    @Test
    public void blankSelectedModelRepairsToFirstDiscoveredModel() {
        ProviderProfile blank = providerProfile("https://api.example.com/v1",
                null, null, null);

        assertEquals("model-a", ModelCatalog.repairSelectedModel(
                blank, Arrays.asList("model-a", "model-b")));
    }

    @Test
    public void manualModelIsPreservedWhenDiscoveryDoesNotContainIt() {
        ProviderProfile manual = profile("https://api.example.com/v1", null, null);

        assertEquals("manual-model", ModelCatalog.repairSelectedModel(
                manual, Arrays.asList("model-a", "model-b")));
        assertNull(ModelCatalog.repairSelectedModel(
                providerProfile("https://api.example.com/v1", null, null, null),
                Collections.<String>emptyList()));
    }

    @Test
    public void unsupportedStatusesReturnUnsupportedWithoutInvalidatingProfile() {
        for (int status : new int[] {404, 405}) {
            FakeExecutor executor = FakeExecutor.success(jsonData(), "req-" + status);
            executor.responseStatusCode = status;
            RecordingCallback callback = new RecordingCallback();
            ProviderProfile profile = profile("https://api.example.com/v1", null, null);

            new ModelCatalog(executor).discover(profile,
                    new RecordingSecretStore(SECRET), callback);

            assertTrue(callback.result.isUnsupported());
            assertEquals("manual-model",
                    ModelCatalog.repairSelectedModel(profile, Collections.<String>emptyList()));
        }
    }

    @Test
    public void statusAndTransportFailuresUseNormalizedCategories() {
        assertFailure(401, TranslationFailureCategory.AUTH);
        assertFailure(429, TranslationFailureCategory.RATE_LIMITED);
        assertFailure(500, TranslationFailureCategory.SERVER);
        assertFailure(408, TranslationFailureCategory.TIMEOUT);
        assertTransportFailure(HttpRequestExecutor.FailureReason.TIMEOUT,
                TranslationFailureCategory.TIMEOUT);
        assertTransportFailure(HttpRequestExecutor.FailureReason.NETWORK,
                TranslationFailureCategory.NETWORK);
    }

    @Test
    public void malformedResponseUsesInvalidOutputWithoutExposingBody() {
        FakeExecutor executor = FakeExecutor.success(
                "not-model-data", "req-invalid");
        RecordingCallback callback = new RecordingCallback();

        new ModelCatalog(executor).discover(profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET), callback);

        assertTrue(callback.result.isFailure());
        assertEquals(TranslationFailureCategory.INVALID_OUTPUT,
                callback.result.getFailure().getCategory());
        assertFalse(callback.result.getFailure().getMessage().contains("provider detail"));
    }

    @Test
    public void missingCredentialFailsWithoutHttpRequest() {
        FakeExecutor executor = FakeExecutor.success(jsonData(), "req-none");
        RecordingCallback callback = new RecordingCallback();

        new ModelCatalog(executor).discover(profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(null), callback);

        assertTrue(callback.result.isFailure());
        assertEquals(TranslationFailureCategory.AUTH,
                callback.result.getFailure().getCategory());
        assertEquals(0, executor.getExecuteCount());
    }

    @Test
    public void cancellationSuppressesLateResultAndCancelsTransport() {
        FakeExecutor executor = FakeExecutor.deferred();
        ModelCatalog catalog = new ModelCatalog(executor);
        RecordingCallback callback = new RecordingCallback();

        ModelCatalog.DiscoveryCall call = catalog.discover(
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET), callback);
        call.cancel();
        executor.succeed(jsonData("late"), "req-late");

        assertTrue(call.isCancelled());
        assertTrue(executor.getLastCall().isCancelled());
        assertFalse(callback.completed);
    }

    private static void assertFailure(int status, TranslationFailureCategory expected) {
        FakeExecutor executor = FakeExecutor.success("not-model-data", "req-" + status);
        executor.responseStatusCode = status;
        RecordingCallback callback = new RecordingCallback();
        new ModelCatalog(executor).discover(profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET), callback);
        assertTrue(callback.result.isFailure());
        assertEquals(expected, callback.result.getFailure().getCategory());
    }

    private static void assertTransportFailure(HttpRequestExecutor.FailureReason reason,
                                               TranslationFailureCategory expected) {
        FakeExecutor executor = FakeExecutor.failure(
                new HttpRequestExecutor.HttpFailure(reason, "safe", "req-x"));
        RecordingCallback callback = new RecordingCallback();
        new ModelCatalog(executor).discover(profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET), callback);
        assertTrue(callback.result.isFailure());
        assertEquals(expected, callback.result.getFailure().getCategory());
    }

    private static String jsonData(String... ids) {
        StringBuilder json = new StringBuilder();
        json.append('{').append('"').append("data").append('"').append(':').append('[');
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('{').append('"').append("id").append('"').append(':').append('"')
                    .append(ids[i]).append('"').append('}');
        }
        return json.append(']').append('}').toString();
    }


    private static ProviderProfile profile(String baseUrl, Map<String, String> headers,
                                           Map<String, String> options) {
        return providerProfile(baseUrl, headers, options, "manual-model");
    }

    private static ProviderProfile providerProfile(String baseUrl, Map<String, String> headers,
                                                   Map<String, String> options, String modelId) {
        return new ProviderProfile("profile-1", "Test", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, baseUrl, "credential-ref-1",
                modelId, Collections.<String>emptyList(), headers, options);
    }

    private static final class RecordingSecretStore implements SecretStore {
        private final String value;

        RecordingSecretStore(String value) {
            this.value = value;
        }

        @Override
        public String get(String reference) {
            return value;
        }

        @Override
        public void put(String reference, String secret) {
        }

        @Override
        public void delete(String reference) {
        }
    }

    private static final class RecordingCallback implements ModelCatalog.Callback {
        private ConnectionTestResult result;
        private boolean completed;

        @Override
        public void onResult(ConnectionTestResult result) {
            completed = true;
            this.result = result;
        }
    }

    private static final class FakeExecutor implements HttpRequestExecutor {
        private HttpRequest lastRequest;
        private HttpCallback lastCallback;
        private FakeCall lastCall;
        private HttpResponse response;
        private HttpFailure failure;
        private boolean deferred;
        private int executeCount;

        int responseStatusCode;

        static FakeExecutor success(String body, String requestId) {
            FakeExecutor executor = new FakeExecutor();
            executor.response = new HttpResponse(200, body, requestId);
            return executor;
        }

        static FakeExecutor failure(HttpFailure failure) {
            FakeExecutor executor = new FakeExecutor();
            executor.failure = failure;
            return executor;
        }

        static FakeExecutor deferred() {
            FakeExecutor executor = new FakeExecutor();
            executor.deferred = true;
            return executor;
        }

        @Override
        public HttpCall execute(HttpRequest request, HttpCallback callback) {
            executeCount++;
            lastRequest = request;
            lastCallback = callback;
            lastCall = new FakeCall();
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

        FakeCall getLastCall() {
            return lastCall;
        }

        int getExecuteCount() {
            return executeCount;
        }
    }

    private static final class FakeCall implements HttpRequestExecutor.HttpCall {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void close() {
        }
    }
}
