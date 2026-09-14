package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
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
 */
public final class OkHttpRequestExecutor implements HttpRequestExecutor {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    /** Slack allowed between the deadline firing and the socket failure surfacing. */
    private static final long DEADLINE_TOLERANCE_MS = 250;

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

        final long startedAtMs = monotonicNowMs();
        okhttp3.Call call = client.newCall(requestBuilder.build());
        OkHttpCall result = new OkHttpCall(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(okhttp3.Call cancelledCall, IOException e) {
                if (result.isCancelled() || cancelledCall.isCanceled()) {
                    callback.onFailure(new HttpFailure(FailureReason.CANCELLED,
                            "Provider request was cancelled.", null));
                    return;
                }
                FailureReason reason = e instanceof SocketTimeoutException
                        ? FailureReason.TIMEOUT : FailureReason.NETWORK;
                callback.onFailure(new HttpFailure(reason,
                        reason == FailureReason.TIMEOUT
                                ? "Provider request timed out."
                                : "Provider network request failed.",
                        null));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                int statusCode = response.code();
                String requestId = requestId(response);
                boolean successful = statusCode >= 200 && statusCode < 300;

                try {
                    if (successful && callback instanceof StreamCallback) {
                        if (isEventStream(response)) {
                            streamEvents(result, response, (StreamCallback) callback,
                                    statusCode, requestId, startedAtMs, request.getTimeoutMs());
                        } else {
                            // Asked to stream and got something else: a capability failure, never
                            // a reason to parse a JSON body line by line as if it were SSE.
                            callback.onFailure(new HttpFailure(FailureReason.IO,
                                    "Provider did not return an event stream.", requestId));
                        }
                        return;
                    }

                    String body = null;
                    try {
                        if (response.body() != null) {
                            body = response.body().string();
                        }
                    } catch (IOException e) {
                        callback.onFailure(new HttpFailure(FailureReason.IO,
                                "Provider response could not be read.", requestId));
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
     * once, and a cancelled call never delivers anything at all.
     */
    private void streamEvents(OkHttpCall result, Response response, StreamCallback callback,
                              int statusCode, String requestId, long startedAtMs,
                              long budgetMs) {
        AtomicBoolean delivered = new AtomicBoolean();

        try {
            InputStream input = response.body() != null ? response.body().byteStream() : null;

            if (input != null) {
                new SseEventReader().read(input, (eventType, data) -> {
                    if (delivered.get() || result.isCancelled()) return;
                    callback.onEvent(eventType, data);
                });
            }
        } catch (IOException e) {
            if (result.isCancelled()) return;

            if (delivered.compareAndSet(false, true)) {
                callback.onFailure(transportFailure(e, requestId, startedAtMs, budgetMs));
            }
            return;
        }

        if (result.isCancelled()) return;

        if (delivered.compareAndSet(false, true)) {
            // The read finished; the adapter still has to verify its protocol end signal.
            callback.onSuccess(new HttpResponse(statusCode, null, requestId));
        }
    }

    private static HttpFailure transportFailure(IOException e, String requestId) {
        // OkHttp reports a read timeout as interrupted I/O.
        FailureReason reason = e instanceof InterruptedIOException
                ? FailureReason.TIMEOUT : FailureReason.NETWORK;
        return new HttpFailure(reason, failureMessage(reason), requestId);
    }

    /**
     * A stream that fails at or after the call deadline is a timeout. OkHttp closes the socket
     * when the deadline fires, so the surface exception is a plain socket failure rather than an
     * interrupted read; the elapsed time against the request's own budget is what identifies it.
     */
    private static HttpFailure transportFailure(IOException e, String requestId,
                                                long startedAtMs, long budgetMs) {
        boolean deadlinePassed = budgetMs > 0
                && monotonicNowMs() - startedAtMs >= budgetMs - DEADLINE_TOLERANCE_MS;
        FailureReason reason = e instanceof InterruptedIOException || deadlinePassed
                ? FailureReason.TIMEOUT : FailureReason.NETWORK;

        return new HttpFailure(reason, failureMessage(reason), requestId);
    }

    private static String failureMessage(FailureReason reason) {
        return reason == FailureReason.TIMEOUT
                ? "Provider request timed out."
                : "Provider network request failed.";
    }

    private static long monotonicNowMs() {
        return System.nanoTime() / 1_000_000L;
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
    }
}
