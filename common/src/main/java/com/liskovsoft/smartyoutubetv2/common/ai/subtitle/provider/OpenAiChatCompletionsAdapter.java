package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Chat Completions adapter for complete, non-streaming responses.
 *
 * <p>This class owns endpoint construction, bearer authentication, request serialization,
 * response parsing, and transport-to-domain failure mapping. It does not schedule subtitle
 * work, retry requests, parse SSE, or touch the player.</p>
 */
public final class OpenAiChatCompletionsAdapter implements ProtocolAdapter {
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;
    public static final String OPTION_TIMEOUT_MS = "timeoutMs";

    private final HttpRequestExecutor mExecutor;
    private final ProviderProfile mProfile;
    private final SecretStore mSecretStore;
    private final String mCompletionsUrl;

    public OpenAiChatCompletionsAdapter(HttpRequestExecutor executor,
                                        ProviderProfile profile,
                                        SecretStore secretStore) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (profile.getProtocol() != ProviderProtocol.OPENAI_CHAT_COMPLETIONS) {
            throw new IllegalArgumentException("profile protocol must be OpenAI Chat Completions");
        }
        if (profile.getModelId() == null) {
            throw new IllegalArgumentException("profile model id must not be blank");
        }
        if (secretStore == null) {
            throw new IllegalArgumentException("secretStore must not be null");
        }

        mExecutor = executor;
        mProfile = profile;
        mSecretStore = secretStore;
        mCompletionsUrl = chatCompletionsUrl(profile.getBaseUrl());
    }

    @Override
    public ProviderProtocol getProtocol() {
        return ProviderProtocol.OPENAI_CHAT_COMPLETIONS;
    }

    @Override
    public TranslationCall translate(TranslationRequest request,
                                     @NonNull TranslationCallback callback) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        final Call call = new Call();
        final String credential;
        try {
            credential = mSecretStore.get(mProfile.getSecretReference());
        } catch (SecretStore.Failure failure) {
            deliverFailure(call, callback, failure.getFailure());
            return call;
        }

        if (credential == null || credential.trim().isEmpty()) {
            deliverFailure(call, callback, new TranslationFailure(
                    TranslationFailureCategory.AUTH,
                    "Provider credential is not configured."));
            return call;
        }

        final HttpRequestExecutor.HttpRequest httpRequest;
        try {
            httpRequest = buildRequest(request, credential);
        } catch (RuntimeException e) {
            deliverFailure(call, callback, new TranslationFailure(
                    TranslationFailureCategory.PROTOCOL,
                    "Provider request could not be constructed."));
            return call;
        }

        try {
            HttpRequestExecutor.HttpCall httpCall = mExecutor.execute(httpRequest,
                    new HttpRequestExecutor.HttpCallback() {
                        @Override
                        public void onSuccess(HttpRequestExecutor.HttpResponse response) {
                            handleResponse(call, callback, request, response);
                        }

                        @Override
                        public void onFailure(HttpRequestExecutor.HttpFailure failure) {
                            handleTransportFailure(call, callback, failure);
                        }
                    });
            call.setHttpCall(httpCall);
        } catch (RuntimeException e) {
            deliverFailure(call, callback, TranslationFailureMapper.fromTransport(
                    HttpRequestExecutor.FailureReason.NETWORK));
        }

        return call;
    }

    @Override
    public String toString() {
        return "OpenAiChatCompletionsAdapter{baseUrl=" + mProfile.getBaseUrl()
                + ", model=" + mProfile.getModelId() + ", credential=hidden}";
    }

    static String chatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }

        String value = baseUrl.trim();
        if (value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            throw new IllegalArgumentException("baseUrl must not contain query or fragment");
        }

        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("baseUrl is not a valid URI");
        }

        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        if (scheme == null || authority == null
                || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) URL without user info");
        }

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.endsWith("/chat/completions")) {
            return scheme + "://" + authority + path;
        }
        if (path.matches(".*/v[0-9]+[A-Za-z0-9]*$")) {
            return scheme + "://" + authority + path + "/chat/completions";
        }
        return scheme + "://" + authority + path + "/v1/chat/completions";
    }

    private HttpRequestExecutor.HttpRequest buildRequest(TranslationRequest request,
                                                          String credential) {
        Map<String, String> headers = new LinkedHashMap<>(mProfile.getHeaders());
        removeHeader(headers, "Authorization");
        removeHeader(headers, "Content-Type");
        removeHeader(headers, "Accept");
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + credential);

        String body = buildBody(request);
        return new HttpRequestExecutor.HttpRequest("POST", mCompletionsUrl, headers,
                body, resolveTimeout());
    }

    private String buildBody(TranslationRequest request) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        appendStringField(json, "model", mProfile.getModelId());
        json.append(',');
        json.append("\"messages\":[");
        appendMessage(json, "system", request.getRenderedPrompt());
        json.append(',');
        appendMessage(json, "user", request.getSourceText());
        json.append("],");
        json.append("\"temperature\":0,");
        json.append("\"stream\":false");
        json.append('}');
        return json.toString();
    }

    private static void appendMessage(StringBuilder json, String role, String content) {
        json.append('{');
        appendStringField(json, "role", role);
        json.append(',');
        appendStringField(json, "content", content);
        json.append('}');
    }

    private static void appendStringField(StringBuilder json, String name, String value) {
        appendString(json, name);
        json.append(':');
        appendString(json, value);
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                case '\b':
                    json.append("\\b");
                    break;
                case '\f':
                    json.append("\\f");
                    break;
                case '\n':
                    json.append("\\n");
                    break;
                case '\r':
                    json.append("\\r");
                    break;
                case '\t':
                    json.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                    break;
            }
        }
        json.append('"');
    }

    private long resolveTimeout() {
        String configured = mProfile.getOptions().get(OPTION_TIMEOUT_MS);
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            long timeout = Long.parseLong(configured);
            return timeout > 0 ? timeout : DEFAULT_TIMEOUT_MS;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private void handleResponse(Call call, TranslationCallback callback,
                                TranslationRequest request,
                                HttpRequestExecutor.HttpResponse response) {
        if (call.isCancelled()) {
            return;
        }
        call.setRequestId(response != null ? response.getRequestId() : null);
        if (response == null || response.getStatusCode() < 200
                || response.getStatusCode() >= 300) {
            int status = response != null ? response.getStatusCode() : 0;
            deliverFailure(call, callback, TranslationFailureMapper.fromHttpStatus(status));
            return;
        }

        try {
            String translated = parseTranslation(response.getBody());
            if (!call.markDelivered()) {
                return;
            }
            callback.onSuccess(TranslationResult.finalResult(
                    request.getSessionId(), request.getRequestId(), request.getUnit(), translated));
        } catch (ChatResponseException e) {
            deliverFailure(call, callback, e.getFailure());
        }
    }

    private void handleTransportFailure(Call call, TranslationCallback callback,
                                        HttpRequestExecutor.HttpFailure failure) {
        if (call.isCancelled() || failure == null
                || failure.getReason() == HttpRequestExecutor.FailureReason.CANCELLED) {
            return;
        }
        call.setRequestId(failure.getRequestId());
        deliverFailure(call, callback,
                TranslationFailureMapper.fromTransport(failure.getReason()));
    }

    private static void deliverFailure(Call call, TranslationCallback callback,
                                       TranslationFailure failure) {
        if (call.isCancelled() || !call.markDelivered()) {
            return;
        }
        callback.onFailure(failure);
    }

    private static String parseTranslation(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response was empty.");
        }

        final Map<String, Object> root;
        try {
            Object parsed = Helpers.convertToObj(body);
            root = asMap(parsed);
        } catch (RuntimeException e) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response was malformed.");
        }
        if (root == null) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response was malformed.");
        }

        Object choicesValue = root.get("choices");
        if (!(choicesValue instanceof List)) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response did not contain choices.");
        }
        List<?> choices = (List<?>) choicesValue;
        if (choices.isEmpty()) {
            throw new ChatResponseException(TranslationFailureCategory.INVALID_OUTPUT,
                    "Provider returned no translation choice.");
        }

        Map<String, Object> choice = asMap(choices.get(0));
        if (choice == null) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider choice was malformed.");
        }
        Map<String, Object> message = asMap(choice.get("message"));
        if (message == null || !(message.get("content") instanceof String)) {
            throw new ChatResponseException(TranslationFailureCategory.INVALID_OUTPUT,
                    "Provider returned no translation text.");
        }

        String translated = ((String) message.get("content")).trim();
        if (translated.isEmpty()) {
            throw new ChatResponseException(TranslationFailureCategory.INVALID_OUTPUT,
                    "Provider returned blank translation text.");
        }
        return translated;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
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

    public static final class Call implements TranslationCall {
        private boolean mCancelled;
        private boolean mDelivered;
        private HttpRequestExecutor.HttpCall mHttpCall;
        private String mRequestId;

        @Override
        public synchronized void cancel() {
            mCancelled = true;
            if (mHttpCall != null) {
                mHttpCall.cancel();
            }
        }

        @Override
        public synchronized boolean isCancelled() {
            return mCancelled;
        }

        public synchronized String getRequestId() {
            return mRequestId;
        }

        synchronized void setHttpCall(HttpRequestExecutor.HttpCall httpCall) {
            mHttpCall = httpCall;
            if (mCancelled && mHttpCall != null) {
                mHttpCall.cancel();
            }
        }

        synchronized void setRequestId(String requestId) {
            mRequestId = requestId;
        }

        synchronized boolean markDelivered() {
            if (mDelivered) {
                return false;
            }
            mDelivered = true;
            return true;
        }
    }

    private static final class ChatResponseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final TranslationFailure mFailure;

        ChatResponseException(TranslationFailureCategory category, String safeMessage) {
            super(safeMessage);
            mFailure = new TranslationFailure(category, safeMessage);
        }

        TranslationFailure getFailure() {
            return mFailure;
        }
    }
}
