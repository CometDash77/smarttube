package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OkHttp-backed asynchronous transport for protocol adapters.
 *
 * <p>Failure classification is deliberately narrow. OkHttp cancels a call itself when the call
 * deadline fires, so a cancelled call this executor did not cancel is a timeout, not a network
 * drop; only the application's own {@link HttpCall#cancel()} or {@link HttpCall#close()}
 * suppresses a terminal notification. The same rule is applied before the response headers, while
 * reading a streamed body, and while reading a plain body, so no stage can swallow a failure into
 * a different category.</p>
 */
public final class OkHttpRequestExecutor implements HttpRequestExecutor {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient mClient;

    public OkHttpRequestExecutor() {
        this(new OkHttpClient());
    }

    public OkHttpRequestExecutor(OkHttpClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        mClient = client;
    }

    @Override
    public HttpCall execute(HttpRequest request, HttpCallback callback) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        // The call timeout caps the total exchange so server heartbeats cannot extend a
        // streaming request forever; the read timeout still bounds a silent connection.
        OkHttpClient client = mClient.newBuilder()
                .connectTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .callTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();

        Request.Builder requestBuilder = new Request.Builder().url(request.getUrl());
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            if (header.getKey() != null && header.getValue() != null) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
        }
        applyMethod(requestBuilder, request);

        okhttp3.Call httpCall = client.newCall(requestBuilder.build());
        OkHttpCall result = new OkHttpCall(httpCall);
        httpCall.enqueue(new Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                // The application cancelled: the exchange is over and must stay silent. A call
                // OkHttp cancelled on its own is the deadline, which the classifier reports.
                if (result.isStopped()) return;

                FailureReason reason = transportFailureReason(e, call);
                callback.onFailure(new HttpFailure(reason, failureMessage(reason), null));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                int statusCode = response.code();
                String requestId = requestId(response);
                boolean successful = statusCode >= 200 && statusCode < 300;

                try {
                    if (successful && callback instanceof StreamCallback) {
                        if (isEventStream(response)) {
                            streamEvents(result, call, response, (StreamCallback) callback,
                                    statusCode, requestId);
                        } else {
                            // Asked to stream and got something else: a capability failure, never
                            // a reason to parse a JSON body line by line as if it were SSE.
                            if (!result.isStopped()) {
                                callback.onFailure(new HttpFailure(FailureReason.IO,
                                        "Provider did not return an event stream.", requestId));
                            }
                        }
                        return;
                    }

                    String body = null;
                    try {
                        if (response.body() != null) {
                            body = response.body().string();
                        }
                    } catch (IOException e) {
                        if (result.isStopped()) return;
                        FailureReason reason = transportFailureReason(e, call);
                        callback.onFailure(new HttpFailure(reason,
                                failureMessage(reason), requestId));
                        return;
                    }

                    callback.onSuccess(new HttpResponse(statusCode, body, requestId));
                } finally {
                    response.close();
                }
            }
        });
        return result;
    }

    @Override
    public String toString() {
        return "OkHttpRequestExecutor{sharedClient=hidden}";
    }

    /**
     * Consumes the event stream on the calling thread. Every terminal path is delivered at most
     * once, and a call the application cancelled or already closed never delivers anything.
     */
    private void streamEvents(OkHttpCall result, okhttp3.Call call, Response response,
                              StreamCallback callback, int statusCode, String requestId) {
        AtomicBoolean delivered = new AtomicBoolean();

        try {
            InputStream input = response.body() != null ? response.body().byteStream() : null;

            if (input != null) {
                new SseEventReader().read(input, (eventType, data) -> {
                    if (delivered.get() || result.isStopped()) return;
                    callback.onEvent(eventType, data);
                });
            }
        } catch (IOException e) {
            // A closed call is the adapter saying it already has its answer; reading stops here
            // on purpose and this interruption must not be reported as a new failure.
            if (result.isStopped()) return;

            if (delivered.compareAndSet(false, true)) {
                FailureReason reason = transportFailureReason(e, call);
                callback.onFailure(new HttpFailure(reason, failureMessage(reason), requestId));
            }
            return;
        }

        if (result.isStopped()) return;

        if (delivered.compareAndSet(false, true)) {
            // The read finished; the adapter still has to verify its protocol end signal.
            callback.onSuccess(new HttpResponse(statusCode, null, requestId));
        }
    }

    /**
     * Classifies an I/O failure of an exchange this executor owns.
     *
     * <p>The application's own cancel is not classified here: the callers check it first and
     * suppress the notification entirely, and a close is filtered the same way before this runs.
     * What remains is a read the call deadline interrupted — OkHttp marks its own call cancelled
     * when that deadline fires, and reports it as a plain socket failure — or an ordinary dropped
     * connection. No elapsed-time comparison is involved: nothing inside this executor ever
     * cancels the exchange of its own accord.</p>
     */
    private static FailureReason transportFailureReason(IOException e, okhttp3.Call call) {
        if (e instanceof InterruptedIOException || call.isCanceled()) {
            return FailureReason.TIMEOUT;
        }
        return FailureReason.NETWORK;
    }

    private static String failureMessage(FailureReason reason) {
        return reason == FailureReason.TIMEOUT
                ? "Provider request timed out."
                : "Provider network request failed.";
    }

    private static boolean isEventStream(Response response) {
        String contentType = response.header("Content-Type");

        return contentType != null && contentType.trim().toLowerCase(Locale.US)
                .startsWith("text/event-stream");
    }

    private static void applyMethod(Request.Builder builder, HttpRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            builder.get();
            return;
        }

        String body = request.getBody() != null ? request.getBody() : "";
        RequestBody requestBody = RequestBody.create(JSON, body);
        if ("POST".equalsIgnoreCase(method)) {
            builder.post(requestBody);
        } else {
            builder.method(method, requestBody);
        }
    }

    private static String requestId(Response response) {
        String value = response.header("x-request-id");
        if (value == null) {
            value = response.header("request-id");
        }
        if (value == null) {
            value = response.header("x-ms-request-id");
        }
        return value;
    }

    private static final class OkHttpCall implements HttpCall {
        private final okhttp3.Call mCall;
        private volatile boolean mCancelled;
        private volatile boolean mClosed;

        OkHttpCall(okhttp3.Call call) {
            mCall = call;
        }

        @Override
        public void cancel() {
            mCancelled = true;
            mCall.cancel();
        }

        @Override
        public boolean isCancelled() {
            return mCancelled;
        }

        @Override
        public void close() {
            mClosed = true;
            mCall.cancel();
        }

        /** Whether no further terminal or event callback may be delivered for this exchange. */
        boolean isStopped() {
            return mCancelled || mClosed;
        }
    }
}
