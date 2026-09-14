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
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic-compatible Messages adapter for complete, non-streaming responses.
 */
public final class AnthropicMessagesAdapter implements ProtocolAdapter {
    /** Upper bound on one streamed translation, in Unicode code points. */
    public static final int MAX_STREAM_CODE_POINTS = 16 * 1024;

    public static final String DEFAULT_VERSION = "2023-06-01";
    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final String OPTION_AUTH_SCHEME = "anthropicAuthScheme";
    public static final String OPTION_VERSION = "anthropicVersion";
    public static final String OPTION_MAX_TOKENS = "maxTokens";
    public static final String AUTH_API_KEY = "x-api-key";
    public static final String AUTH_BEARER = "bearer";

    private final HttpRequestExecutor mExecutor;
    private final ProviderProfile mProfile;
    private final SecretStore mSecretStore;
    private final String mMessagesUrl;

    public AnthropicMessagesAdapter(HttpRequestExecutor executor,
                                    ProviderProfile profile,
                                    SecretStore secretStore) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (profile.getProtocol() != ProviderProtocol.ANTHROPIC_MESSAGES) {
            throw new IllegalArgumentException("profile protocol must be Anthropic Messages");
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
        mMessagesUrl = messagesUrl(profile.getBaseUrl());
    }

    @Override
    public ProviderProtocol getProtocol() {
        return ProviderProtocol.ANTHROPIC_MESSAGES;
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

        // The callback type is the streaming request: only a caller that can consume drafts
        // gets an event stream, and everyone else keeps the single-response contract.
        final boolean streaming = callback instanceof TranslationStream;

        final HttpRequestExecutor.HttpRequest httpRequest;
        try {
            httpRequest = buildRequest(request, credential, streaming);
        } catch (RuntimeException e) {
            deliverFailure(call, callback, new TranslationFailure(
                    TranslationFailureCategory.PROTOCOL,
                    "Provider request could not be constructed."));
            return call;
        }

        final HttpRequestExecutor.HttpCallback httpCallback = streaming
                ? new StreamingHttpCallback(call, (TranslationStream) callback, request)
                : new HttpRequestExecutor.HttpCallback() {
                    @Override
                    public void onSuccess(HttpRequestExecutor.HttpResponse response) {
                        handleResponse(call, callback, request, response);
                    }

                    @Override
                    public void onFailure(HttpRequestExecutor.HttpFailure failure) {
                        handleTransportFailure(call, callback, failure);
                    }
                };

        try {
            HttpRequestExecutor.HttpCall httpCall = mExecutor.execute(httpRequest, httpCallback);
            call.setHttpCall(httpCall);
        } catch (RuntimeException e) {
            deliverFailure(call, callback, TranslationFailureMapper.fromTransport(
                    HttpRequestExecutor.FailureReason.NETWORK));
        }

        return call;
    }

    @Override
    public String toString() {
        return "AnthropicMessagesAdapter{baseUrl=" + mProfile.getBaseUrl()
                + ", model=" + mProfile.getModelId() + ", credential=hidden}";
    }

    static String messagesUrl(String baseUrl) {
        final URI uri;
        try {
            uri = new URI(baseUrl != null ? baseUrl.trim() : null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("baseUrl is not a valid URI");
        }

        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        String path = uri.getRawPath();
        if (scheme == null || authority == null
                || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) URL without user info");
        }
        if (path == null || path.isEmpty()) {
            path = "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.endsWith("/messages")) {
            return scheme + "://" + authority + path;
        }
        if (path.matches(".*/v[0-9]+[A-Za-z0-9]*$")) {
            return scheme + "://" + authority + path + "/messages";
        }
        return scheme + "://" + authority + path + "/v1/messages";
    }

    private HttpRequestExecutor.HttpRequest buildRequest(TranslationRequest request,
                                                          String credential) {
        return buildRequest(request, credential, false);
    }

    private HttpRequestExecutor.HttpRequest buildRequest(TranslationRequest request,
                                                          String credential,
                                                          boolean streaming) {
        Map<String, String> headers = new LinkedHashMap<>(mProfile.getHeaders());
        removeHeader(headers, "Authorization");
        removeHeader(headers, "x-api-key");
        removeHeader(headers, "anthropic-version");
        removeHeader(headers, "Content-Type");
        removeHeader(headers, "Accept");

        headers.put("Content-Type", "application/json");
        headers.put("Accept", streaming ? "text/event-stream" : "application/json");
        headers.put("anthropic-version", resolveVersion());
        if (AUTH_BEARER.equalsIgnoreCase(resolveAuthScheme())) {
            headers.put("Authorization", "Bearer " + credential);
        } else {
            headers.put("x-api-key", credential);
        }

        String body = buildBody(request, streaming);
        return new HttpRequestExecutor.HttpRequest("POST", mMessagesUrl, headers, body, 30_000L);
    }

    private String buildBody(TranslationRequest request) {
        return buildBody(request, false);
    }

    private String buildBody(TranslationRequest request, boolean streaming) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        appendStringField(json, "model", mProfile.getModelId());
        json.append(',');
        json.append("\"max_tokens\":").append(resolveMaxTokens());
        json.append(',');
        appendStringField(json, "system", request.getRenderedPrompt());
        json.append(',');
        json.append("\"messages\":[");
        appendMessage(json, "user", request.getSourceText());
        json.append("]");
        if (streaming) json.append(",\"stream\":true");
        json.append('}');
        return json.toString();
    }

    /**
     * Accumulates one streamed translation from Anthropic's named events.
     *
     * <p>Only {@code text_delta} payloads become subtitle text; thinking and tool blocks are
     * ignored rather than rendered. A completed translation requires {@code message_stop} with
     * an accepted stop reason, so a truncated or interrupted stream is a failure rather than a
     * silent success. Each delta publishes the full draft so far.</p>
     */
    private final class StreamingHttpCallback implements HttpRequestExecutor.StreamCallback {
        private final Call mCall;
        private final TranslationStream mCallback;
        private final TranslationRequest mRequest;
        private final StringBuilder mText = new StringBuilder();
        private boolean mMessageStopped;
        private String mStopReason;

        StreamingHttpCallback(Call call, TranslationStream callback, TranslationRequest request) {
            mCall = call;
            mCallback = callback;
            mRequest = request;
        }

        @Override
        public void onEvent(String eventType, String data) {
            if (mCall.isCancelled() || data == null || mMessageStopped) return;

            final Map<String, Object> event;
            try {
                event = asMap(Helpers.convertToObj(data));
            } catch (RuntimeException e) {
                return;
            }
            if (event == null) return;

            String type = event.get("type") instanceof String ? (String) event.get("type") : eventType;
            if (type == null) return;

            switch (type) {
                case "content_block_delta":
                    handleDelta(event);
                    break;
                case "message_delta":
                    Map<String, Object> delta = asMap(event.get("delta"));
                    Object stopReason = delta != null ? delta.get("stop_reason") : null;
                    if (stopReason instanceof String) mStopReason = (String) stopReason;
                    break;
                case "message_stop":
                    mMessageStopped = true;
                    break;
                case "error":
                    deliverFailure(mCall, mCallback, new TranslationFailure(
                            TranslationFailureCategory.PROTOCOL,
                            "Provider reported a streaming error."));
                    break;
                default:
                    // message_start, content_block_start/stop, ping and unknown events carry no
                    // subtitle text.
                    break;
            }
        }

        private void handleDelta(Map<String, Object> event) {
            Map<String, Object> delta = asMap(event.get("delta"));
            if (delta == null) return;

            // Only text deltas are subtitle content; thinking and tool JSON are not text.
            if (!"text_delta".equals(delta.get("type"))) return;

            Object text = delta.get("text");
            if (!(text instanceof String) || ((String) text).isEmpty()) return;

            mText.append((String) text);

            if (mText.codePointCount(0, mText.length()) > MAX_STREAM_CODE_POINTS) {
                deliverFailure(mCall, mCallback, new TranslationFailure(
                        TranslationFailureCategory.INVALID_OUTPUT,
                        "Provider streamed more text than a subtitle can hold."));
                return;
            }

            mCallback.onPartial(TranslationResult.partialResult(
                    mRequest.getSessionId(), mRequest.getRequestId(), mRequest.getUnit(),
                    mText.toString()));
        }

        @Override
        public void onSuccess(HttpRequestExecutor.HttpResponse response) {
            if (mCall.isCancelled()) return;

            mCall.setRequestId(response != null ? response.getRequestId() : null);

            if (!mMessageStopped) {
                deliverFailure(mCall, mCallback, new TranslationFailure(
                        TranslationFailureCategory.PROTOCOL,
                        "Provider stream ended without a completion signal."));
                return;
            }

            if (!isAcceptedStopReason(mStopReason)) {
                deliverFailure(mCall, mCallback, new TranslationFailure(
                        TranslationFailureCategory.INVALID_OUTPUT,
                        "Provider stopped before the translation was complete."));
                return;
            }

            String translated = mText.toString().trim();

            if (translated.isEmpty()) {
                deliverFailure(mCall, mCallback, new TranslationFailure(
                        TranslationFailureCategory.INVALID_OUTPUT,
                        "Provider returned no translation text."));
                return;
            }

            if (!mCall.markDelivered()) return;

            mCallback.onSuccess(TranslationResult.finalResult(
                    mRequest.getSessionId(), mRequest.getRequestId(), mRequest.getUnit(),
                    translated));
        }

        @Override
        public void onFailure(HttpRequestExecutor.HttpFailure failure) {
            handleTransportFailure(mCall, mCallback, failure);
        }

        private boolean isAcceptedStopReason(String reason) {
            return "end_turn".equals(reason) || "stop_sequence".equals(reason);
        }
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

    private String resolveAuthScheme() {
        String value = mProfile.getOptions().get(OPTION_AUTH_SCHEME);
        return AUTH_BEARER.equalsIgnoreCase(value) ? AUTH_BEARER : AUTH_API_KEY;
    }

    private String resolveVersion() {
        String value = mProfile.getOptions().get(OPTION_VERSION);
        return value != null && !value.trim().isEmpty() ? value : DEFAULT_VERSION;
    }

    private int resolveMaxTokens() {
        String value = mProfile.getOptions().get(OPTION_MAX_TOKENS);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_MAX_TOKENS;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : DEFAULT_MAX_TOKENS;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_TOKENS;
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
            deliverFailure(call, callback, errorFailure(status,
                    response != null ? response.getBody() : null));
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

    private static TranslationFailure errorFailure(int status, String body) {
        TranslationFailureCategory namedCategory = namedErrorCategory(body);
        if (namedCategory != null) {
            return new TranslationFailure(namedCategory, "Provider returned an error.");
        }
        return TranslationFailureMapper.fromHttpStatus(status);
    }

    private static TranslationFailureCategory namedErrorCategory(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> root = asMap(Helpers.convertToObj(body));
            Map<String, Object> error = root != null ? asMap(root.get("error")) : null;
            String errorType = error != null && error.get("type") instanceof String
                    ? (String) error.get("type") : null;
            if (errorType == null) {
                return null;
            }
            if (errorType.contains("authentication") || errorType.contains("permission")) {
                return TranslationFailureCategory.AUTH;
            }
            if (errorType.contains("rate_limit")) {
                return TranslationFailureCategory.RATE_LIMITED;
            }
            if (errorType.contains("timeout")) {
                return TranslationFailureCategory.TIMEOUT;
            }
            if (errorType.contains("overloaded") || errorType.contains("api_error")) {
                return TranslationFailureCategory.SERVER;
            }
            if (errorType.contains("invalid_request") || errorType.contains("not_found")) {
                return TranslationFailureCategory.PROTOCOL;
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String parseTranslation(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response was empty.");
        }

        final Map<String, Object> root;
        try {
            root = asMap(Helpers.convertToObj(body));
        } catch (RuntimeException e) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response was malformed.");
        }
        if (root == null) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response was malformed.");
        }

        if ("error".equals(root.get("type"))) {
            TranslationFailureCategory category = namedErrorCategory(body);
            throw new ChatResponseException(category != null
                    ? category : TranslationFailureCategory.PROTOCOL,
                    "Provider returned an error.");
        }

        Object contentValue = root.get("content");
        if (!(contentValue instanceof List)) {
            throw new ChatResponseException(TranslationFailureCategory.PROTOCOL,
                    "Provider response did not contain content.");
        }

        StringBuilder translated = new StringBuilder();
        for (Object item : (List<?>) contentValue) {
            Map<String, Object> block = asMap(item);
            if (block == null || !"text".equals(block.get("type"))
                    || !(block.get("text") instanceof String)) {
                continue;
            }
            translated.append((String) block.get("text"));
        }

        String text = translated.toString().trim();
        if (text.isEmpty()) {
            throw new ChatResponseException(TranslationFailureCategory.INVALID_OUTPUT,
                    "Provider returned no translation text.");
        }
        return text;
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
