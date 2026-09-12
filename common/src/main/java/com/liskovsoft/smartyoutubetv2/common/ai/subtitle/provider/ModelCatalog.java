package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers model IDs and performs normalized provider connection checks.
 */
public final class ModelCatalog {
    public static final String OPTION_TIMEOUT_MS = "timeoutMs";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final HttpRequestExecutor mExecutor;

    public ModelCatalog(HttpRequestExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        mExecutor = executor;
    }

    public DiscoveryCall discover(ProviderProfile profile, SecretStore secretStore,
                                  Callback callback) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (secretStore == null) {
            throw new IllegalArgumentException("secretStore must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        final DiscoveryCall call = new DiscoveryCall();
        final String credential;
        try {
            credential = secretStore.get(profile.getSecretReference());
        } catch (SecretStore.Failure failure) {
            deliver(call, callback, ConnectionTestResult.failure(failure.getFailure()));
            return call;
        }
        if (credential == null || credential.trim().isEmpty()) {
            deliver(call, callback, ConnectionTestResult.failure(new TranslationFailure(
                    TranslationFailureCategory.AUTH, "Provider credential is not configured.")));
            return call;
        }

        final HttpRequestExecutor.HttpRequest request;
        try {
            request = buildRequest(profile, credential);
        } catch (RuntimeException e) {
            deliver(call, callback, ConnectionTestResult.failure(new TranslationFailure(
                    TranslationFailureCategory.PROTOCOL,
                    "Model discovery request could not be constructed.")));
            return call;
        }

        try {
            HttpRequestExecutor.HttpCall httpCall = mExecutor.execute(request,
                    new HttpRequestExecutor.HttpCallback() {
                        @Override
                        public void onSuccess(HttpRequestExecutor.HttpResponse response) {
                            if (call.isCancelled()) {
                                return;
                            }
                            deliver(call, callback, fromResponse(response));
                        }

                        @Override
                        public void onFailure(HttpRequestExecutor.HttpFailure failure) {
                            if (call.isCancelled() || failure == null
                                    || failure.getReason()
                                    == HttpRequestExecutor.FailureReason.CANCELLED) {
                                return;
                            }
                            deliver(call, callback, ConnectionTestResult.failure(
                                    TranslationFailureMapper.fromTransport(failure.getReason())));
                        }
                    });
            call.setHttpCall(httpCall);
        } catch (RuntimeException e) {
            deliver(call, callback, ConnectionTestResult.failure(
                    TranslationFailureMapper.fromTransport(
                            HttpRequestExecutor.FailureReason.NETWORK)));
        }

        return call;
    }

    public static String repairSelectedModel(ProviderProfile profile,
                                             List<String> discoveredModels) {
        if (profile == null) {
            return null;
        }
        if (profile.getModelId() != null) {
            return profile.getModelId();
        }
        if (discoveredModels == null || discoveredModels.isEmpty()) {
            return null;
        }
        return discoveredModels.get(0);
    }

    public static String modelsUrl(ProviderProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        String baseUrl = profile.getBaseUrl();
        String override = profile.getOptions().get(
                ProviderPreset.OPTION_MODEL_DISCOVERY_PATH);
        if (override != null && !override.trim().isEmpty()) {
            String path = override.trim();
            if (path.startsWith("http://") || path.startsWith("https://")) {
                return path;
            }
            String base = baseUrl.trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + (path.startsWith("/") ? path : "/" + path);
        }

        URI uri = parseBaseUrl(baseUrl);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith("/models")) {
            return uri.getScheme() + "://" + uri.getRawAuthority() + path;
        }
        if (path.matches(".*/v[0-9]+[A-Za-z0-9]*$")) {
            return uri.getScheme() + "://" + uri.getRawAuthority() + path + "/models";
        }
        return uri.getScheme() + "://" + uri.getRawAuthority() + path + "/v1/models";
    }

    private HttpRequestExecutor.HttpRequest buildRequest(ProviderProfile profile,
                                                          String credential) {
        Map<String, String> headers = new LinkedHashMap<>(profile.getHeaders());
        removeHeader(headers, "Authorization");
        removeHeader(headers, "x-api-key");
        removeHeader(headers, "anthropic-version");
        removeHeader(headers, "Content-Type");
        removeHeader(headers, "Accept");
        headers.put("Accept", "application/json");

        if (profile.getProtocol() == ProviderProtocol.ANTHROPIC_MESSAGES) {
            String scheme = profile.getOptions().get(
                    AnthropicMessagesAdapter.OPTION_AUTH_SCHEME);
            if (AnthropicMessagesAdapter.AUTH_BEARER.equalsIgnoreCase(scheme)) {
                headers.put("Authorization", "Bearer " + credential);
            } else {
                headers.put("x-api-key", credential);
            }
            String version = profile.getOptions().get(
                    AnthropicMessagesAdapter.OPTION_VERSION);
            headers.put("anthropic-version", version != null && !version.trim().isEmpty()
                    ? version : AnthropicMessagesAdapter.DEFAULT_VERSION);
        } else {
            headers.put("Authorization", "Bearer " + credential);
        }

        return new HttpRequestExecutor.HttpRequest("GET", modelsUrl(profile), headers,
                null, resolveTimeout(profile));
    }

    private static ConnectionTestResult fromResponse(
            HttpRequestExecutor.HttpResponse response) {
        if (response == null) {
            return ConnectionTestResult.failure(new TranslationFailure(
                    TranslationFailureCategory.INVALID_OUTPUT,
                    "Provider model response was invalid."));
        }
        if (response.getStatusCode() == 404 || response.getStatusCode() == 405) {
            return ConnectionTestResult.unsupported();
        }
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            return ConnectionTestResult.failure(
                    TranslationFailureMapper.fromHttpStatus(response.getStatusCode()));
        }

        List<String> models = parseModels(response.getBody());
        if (models == null) {
            return ConnectionTestResult.failure(new TranslationFailure(
                    TranslationFailureCategory.INVALID_OUTPUT,
                    "Provider model response was invalid."));
        }
        return ConnectionTestResult.success(models);
    }

    private static List<String> parseModels(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> root = asMap(Helpers.convertToObj(body));
            Object dataValue = root != null ? root.get("data") : null;
            if (!(dataValue instanceof List)) {
                return null;
            }
            Set<String> unique = new LinkedHashSet<>();
            for (Object value : (List<?>) dataValue) {
                Map<String, Object> item = asMap(value);
                Object idValue = item != null ? item.get("id") : null;
                if (!(idValue instanceof String)) {
                    continue;
                }
                String id = ((String) idValue).trim();
                if (!id.isEmpty()) {
                    unique.add(id);
                }
            }
            return new ArrayList<>(unique);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static URI parseBaseUrl(String baseUrl) {
        final URI uri;
        try {
            uri = new URI(baseUrl != null ? baseUrl.trim() : null);
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("baseUrl is not a valid URI");
        }
        String scheme = uri.getScheme();
        if (scheme == null || uri.getRawAuthority() == null
                || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) URL without user info");
        }
        return uri;
    }

    private static long resolveTimeout(ProviderProfile profile) {
        String value = profile.getOptions().get(OPTION_TIMEOUT_MS);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            long timeout = Long.parseLong(value);
            return timeout > 0 ? timeout : DEFAULT_TIMEOUT_MS;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private static void removeHeader(Map<String, String> headers, String name) {
        String matchedKey = null;
        for (String key : headers.keySet()) {
            if (name.equalsIgnoreCase(key)) {
                matchedKey = key;
                break;
            }
        }
        if (matchedKey != null) {
            headers.remove(matchedKey);
        }
    }

    private static void deliver(DiscoveryCall call, Callback callback,
                                ConnectionTestResult result) {
        if (call.isCancelled() || !call.markDelivered()) {
            return;
        }
        callback.onResult(result);
    }

    public interface Callback {
        void onResult(ConnectionTestResult result);
    }

    public static final class DiscoveryCall {
        private boolean mCancelled;
        private boolean mDelivered;
        private HttpRequestExecutor.HttpCall mHttpCall;

        public synchronized void cancel() {
            mCancelled = true;
            if (mHttpCall != null) {
                mHttpCall.cancel();
            }
        }

        public synchronized boolean isCancelled() {
            return mCancelled;
        }

        synchronized void setHttpCall(HttpRequestExecutor.HttpCall httpCall) {
            mHttpCall = httpCall;
            if (mCancelled && mHttpCall != null) {
                mHttpCall.cancel();
            }
        }

        synchronized boolean markDelivered() {
            if (mDelivered) {
                return false;
            }
            mDelivered = true;
            return true;
        }
    }
}
