package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal asynchronous HTTP boundary used by protocol adapters.
 *
 * <p>Diagnostic strings never include Authorization values, query strings, request bodies, or
 * response bodies.</p>
 */
public interface HttpRequestExecutor {
    HttpCall execute(HttpRequest request, HttpCallback callback);

    interface HttpCallback {
        void onSuccess(HttpResponse response);

        void onFailure(HttpFailure failure);
    }

    interface HttpCall {
        void cancel();

        boolean isCancelled();
    }

    enum FailureReason {
        NETWORK,
        TIMEOUT,
        CANCELLED,
        IO
    }

    final class HttpRequest {
        private final String mMethod;
        private final String mUrl;
        private final Map<String, String> mHeaders;
        private final String mBody;
        private final long mTimeoutMs;

        public HttpRequest(String method, String url, Map<String, String> headers,
                           String body, long timeoutMs) {
            if (method == null || method.trim().isEmpty()) {
                throw new IllegalArgumentException("method must not be blank");
            }
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("url must not be blank");
            }
            if (timeoutMs < 1) {
                throw new IllegalArgumentException("timeoutMs must be positive");
            }
            mMethod = method;
            mUrl = url;
            mHeaders = headers == null || headers.isEmpty()
                    ? Collections.<String, String>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            mBody = body;
            mTimeoutMs = timeoutMs;
        }

        public String getMethod() {
            return mMethod;
        }

        public String getUrl() {
            return mUrl;
        }

        public Map<String, String> getHeaders() {
            return mHeaders;
        }

        public String getBody() {
            return mBody;
        }

        public long getTimeoutMs() {
            return mTimeoutMs;
        }

        @Override
        public String toString() {
            return "HttpRequest{method=" + mMethod
                    + ", url=" + redactUrl(mUrl)
                    + ", headers=" + mHeaders.size()
                    + ", bodyLength=" + (mBody != null ? mBody.length() : 0)
                    + ", timeoutMs=" + mTimeoutMs + "}";
        }

        private static String redactUrl(String value) {
            try {
                URI uri = new URI(value);
                String host = uri.getHost();
                if (host == null) {
                    return "<redacted-url>";
                }
                int port = uri.getPort();
                String path = uri.getPath();
                return uri.getScheme() + "://" + host + (port >= 0 ? ":" + port : "")
                        + (path != null ? path : "");
            } catch (URISyntaxException e) {
                return "<redacted-url>";
            }
        }
    }

    final class HttpResponse {
        private final int mStatusCode;
        private final String mBody;
        private final String mRequestId;

        public HttpResponse(int statusCode, String body, String requestId) {
            mStatusCode = statusCode;
            mBody = body;
            mRequestId = requestId;
        }

        public int getStatusCode() {
            return mStatusCode;
        }

        public String getBody() {
            return mBody;
        }

        public String getRequestId() {
            return mRequestId;
        }

        @Override
        public String toString() {
            return "HttpResponse{status=" + mStatusCode + ", requestId=" + mRequestId + "}";
        }
    }

    final class HttpFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final FailureReason mReason;
        private final String mRequestId;

        public HttpFailure(FailureReason reason, String safeMessage, String requestId) {
            super(safeMessage != null ? safeMessage : "");
            mReason = reason != null ? reason : FailureReason.NETWORK;
            mRequestId = requestId;
        }

        public FailureReason getReason() {
            return mReason;
        }

        public String getRequestId() {
            return mRequestId;
        }

        @Override
        public String toString() {
            return "HttpFailure{reason=" + mReason
                    + ", requestId=" + mRequestId + "}";
        }
    }
}
