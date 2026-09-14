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
 * OpenAI-compatible Chat Completions adapter for complete, non-streaming responses.
 *
 * <p>This class owns endpoint construction, bearer authentication, request serialization,
 * response parsing, and transport-to-domain failure mapping. It does not schedule subtitle
 * work, retry requests, parse SSE, or touch the player.</p>
 */
public final class OpenAiChatCompletionsAdapter implements ProtocolAdapter {
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;
    public static final String OPTION_TIMEOUT_MS = "timeoutMs";
    /** Upper bound on one streamed translation, in Unicode code points. */
    public static final int MAX_STREAM_CODE_POINTS = 16 * 1024;

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
        return buildRequest(request, credential, false);
    }

    private HttpRequestExecutor.HttpRequest buildRequest(TranslationRequest request,
                                                          String credential,
                                                          boolean streaming) {
        Map<String, String> headers = new LinkedHashMap<>(mProfile.getHeaders());
        removeHeader(headers, "Authorization");
        removeHeader(headers, "Content-Type");
        removeHeader(headers, "Accept");
        headers.put("Content-Type", "application/json");
        headers.put("Accept", streaming ? "text/event-stream" : "application/json");
        headers.put("Authorization", "Bearer " + credential);

        String body = buildBody(request, streaming);
        return new HttpRequestExecutor.HttpRequest("POST", mCompletionsUrl, headers,
                body, resolveTimeout());
    }

    private String buildBody(TranslationRequest request) {
        return buildBody(request, false);
    }

    private String buildBody(TranslationRequest request, boolean streaming) {
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
        json.append("\"stream\":").append(streaming);
        json.append('}');
        return json.toString();
    }

    /**
     * Accumulates one streamed translation. Every valid text delta is published as the full
     * draft so far, so a renderer never shows a fragment as if it were the whole subtitle.
     *
     * <p>A completed translation requires an accepted completion signal: a natural
     * {@code finish_reason} of {@code stop}, or a {@code [DONE]} carrying no reason this adapter
     * knows to be a refusal. Any other reason — {@code length}, {@code content_filter}, a tool
     * call, or something this adapter has never seen — is a failure, never a silent success.
     * Text that ends without any completion signal is an interrupted stream, reported as a
     * retryable transport failure so the scheduler can retry it as a plain request.</p>
     *
     * <p>The outcome is decided as soon as the protocol says the message is complete, and the
     * underlying HTTP read is closed at that point rather than held open for the server to end
     * the socket. The cost is that the provider's request id, which arrives with the response
     * headers, is not recorded for a stream that completes early, because the read is closed
     * before the end-of-body callback that would have carried it. It stays a diagnostic that the
     * non-streaming path and transport-level failures still carry; a failure reported from inside
     * the stream carries none either, since the event hook has no request id to give.</p>
     */
    private final class StreamingHttpCallback implements HttpRequestExecutor.StreamCallback {
        private final Call mCall;
        private final TranslationStream mCallback;
        private final TranslationRequest mRequest;
        private final StringBuilder mText = new StringBuilder();
        /** Code points appended so far; kept incrementally so the bound check stays cheap. */
        private int mCodePoints;
        private String mFinishReason;
        /** A completion signal has been seen: the message is as complete as it will ever be. */
        private boolean mComplete;

        StreamingHttpCallback(Call call, TranslationStream callback, TranslationRequest request) {
            mCall = call;
            mCallback = callback;
            mRequest = request;
        }

        @Override
        public void onEvent(String eventType, String data) {
            if (mCall.isCancelled() || mCall.isDelivered() || data == null) return;

            if ("[DONE]".equals(data.trim())) {
                mComplete = true;
                deliverOutcome();
                return;
            }

            final Map<String, Object> chunk;
            try {
                chunk = asMap(Helpers.convertToObj(data));
            } catch (RuntimeException e) {
                return;
            }
            if (chunk == null) return;

            Object choicesValue = chunk.get("choices");
            if (!(choicesValue instanceof List)) return;

            List<?> choices = (List<?>) choicesValue;
            if (choices.isEmpty()) return;

            Map<String, Object> choice = asMap(choices.get(0));
            if (choice == null) return;

            Object finish = choice.get("finish_reason");
            if (finish instanceof String) {
                mFinishReason = (String) finish;

                if (!isAcceptedFinishReason(mFinishReason)) {
                    deliverFailure(mCall, mCallback, unacceptedFinishFailure(mFinishReason));
                    return;
                }

                // A natural stop ends the message; anything after it carries no new text.
                mComplete = true;
            }

            Map<String, Object> delta = asMap(choice.get("delta"));
            Object content = delta != null ? delta.get("content") : null;

            if (content instanceof String && !((String) content).isEmpty()) {
                if (!appendWithinLimit((String) content)) {
                    deliverFailure(mCall, mCallback, new TranslationFailure(
                            TranslationFailureCategory.INVALID_OUTPUT,
                            "Provider streamed more text than a subtitle can hold."));
                    return;
                }

                mCallback.onPartial(TranslationResult.partialResult(
                        mRequest.getSessionId(), mRequest.getRequestId(), mRequest.getUnit(),
                        mText.toString()));
            }

            // The protocol said the message is complete; do not wait for the socket to close.
            if (mComplete) deliverOutcome();
        }

        /**
         * Adds one delta if it fits the subtitle bound. The count is kept incrementally rather
         * than recomputed over the accumulated text, and a surrogate pair split across two
         * deltas is counted as the one code point it is.
         */
        private boolean appendWithinLimit(String text) {
            int added = text.codePointCount(0, text.length());

            if (mText.length() > 0 && text.length() > 0
                    && Character.isHighSurrogate(mText.charAt(mText.length() - 1))
                    && Character.isLowSurrogate(text.charAt(0))) {
                added--;
            }

            if (mCodePoints + added > MAX_STREAM_CODE_POINTS) return false;

            mCodePoints += added;
            mText.append(text);
            return true;
        }

        @Override
        public void onSuccess(HttpRequestExecutor.HttpResponse response) {
            if (mCall.isCancelled() || mCall.isDelivered()) return;

            mCall.setRequestId(response != null ? response.getRequestId() : null);

            // The same status judgement the non-streaming path makes, before any stream state is
            // consulted: a rejected request never carried a stream to interpret.
            int status = response != null ? response.getStatusCode() : 0;
            if (status < 200 || status >= 300) {
                deliverFailure(mCall, mCallback, TranslationFailureMapper.fromHttpStatus(status));
                return;
            }

            if (!mComplete) {
                // Text with no completion signal is a cut stream, not a finished translation.
                // The category is retryable on purpose: the scheduler's answer to an interrupted
                // stream is one plain request under the same budget.
                deliverFailure(mCall, mCallback, new TranslationFailure(
                        TranslationFailureCategory.NETWORK,
                        "Provider stream ended before the translation was complete."));
                return;
            }

            deliverOutcome();
        }

        @Override
        public void onFailure(HttpRequestExecutor.HttpFailure failure) {
            handleTransportFailure(mCall, mCallback, failure);
        }

        /** Delivers the single outcome once the protocol has declared the message complete. */
        private void deliverOutcome() {
            if (mCall.isCancelled() || mCall.isDelivered()) return;
            if (!mComplete) return;

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

        /** Only a natural stop completes a stream; a compatibility [DONE] is handled separately. */
        private boolean isAcceptedFinishReason(String reason) {
            return "stop".equals(reason);
        }

        /** A reason this adapter does not accept is a failure, whatever kind it is. */
        private TranslationFailure unacceptedFinishFailure(String reason) {
            if ("length".equals(reason) || "content_filter".equals(reason)) {
                return new TranslationFailure(TranslationFailureCategory.INVALID_OUTPUT,
                        "Provider stopped before the translation was complete.");
            }
            return new TranslationFailure(TranslationFailureCategory.PROTOCOL,
                    "Provider ended the response without a translation.");
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
        if (call.isCancelled() || call.isDelivered()) {
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
        if (call.isCancelled() || call.isDelivered() || failure == null
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

        /**
         * Whether this call has already produced its single outcome. Every terminal path checks
         * this one flag, so a second signal — a protocol end signal followed by end of stream, a
         * late delta, an error after the answer — cannot deliver or append anything again.
         */
        synchronized boolean isDelivered() {
            return mDelivered;
        }

        synchronized void setHttpCall(HttpRequestExecutor.HttpCall httpCall) {
            mHttpCall = httpCall;

            if (mHttpCall == null) return;

            // The outcome may have been decided before the handle existed: a synchronous failure
            // during execute(), or a cancel from the caller. Either way the exchange must not
            // keep reading.
            if (mCancelled) {
                mHttpCall.cancel();
            } else if (mDelivered) {
                mHttpCall.close();
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

            // The exchange has produced its one outcome; stop reading it rather than waiting for
            // the server to close a stream it has already finished describing.
            if (mHttpCall != null) {
                mHttpCall.close();
            }
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
