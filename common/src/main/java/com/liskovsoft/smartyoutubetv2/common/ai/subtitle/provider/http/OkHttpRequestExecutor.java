package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

        OkHttpClient client = mClient.newBuilder()
                .connectTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();

        Request.Builder requestBuilder = new Request.Builder().url(request.getUrl());
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            if (header.getKey() != null && header.getValue() != null) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
        }
        applyMethod(requestBuilder, request);

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
                String body = null;
                try {
                    if (response.body() != null) {
                        body = response.body().string();
                    }
                } catch (IOException e) {
                    callback.onFailure(new HttpFailure(FailureReason.IO,
                            "Provider response could not be read.", requestId));
                    return;
                } finally {
                    response.close();
                }
                callback.onSuccess(new HttpResponse(statusCode, body, requestId));
            }
        });
        return result;
    }

    @Override
    public String toString() {
        return "OkHttpRequestExecutor{sharedClient=hidden}";
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
