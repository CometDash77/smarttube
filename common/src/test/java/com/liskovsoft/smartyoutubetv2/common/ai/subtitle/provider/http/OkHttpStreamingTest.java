package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end transport tests against a local socket server. No public network is touched and no
 * paid provider is called; the server writes raw HTTP so fragments, delays, and truncation can
 * be controlled byte by byte.
 */
public class OkHttpStreamingTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ServerSocket mServer;

    @Before
    public void setUp() throws IOException {
        mServer = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
    }

    @After
    public void tearDown() throws IOException {
        mServer.close();
    }

    @Test
    public void aSplitUtf8CharacterIsReassembledIntoOneEvent() throws Exception {
        String url = serve("text/event-stream", splitEveryByte("data: 字幕\n\n".getBytes(UTF_8)),
                0, false);
        RecordingStream callback = new RecordingStream();

        execute(url, callback, 5_000);

        assertTrue(callback.await());
        assertEquals(Collections.singletonList("message|字幕"), callback.events);
        assertNotNull("the HTTP read must be reported as complete", callback.response);
        assertNull(callback.failure);
    }

    @Test
    public void multipleDataLinesAndCommentsAreFramedCorrectly() throws Exception {
        String body = ": heartbeat\ndata: first\ndata: second\n\ndata: third\n\n";
        String url = serve("text/event-stream",
                Collections.singletonList(body.getBytes(UTF_8)), 0, false);
        RecordingStream callback = new RecordingStream();

        execute(url, callback, 5_000);

        assertTrue(callback.await());
        assertEquals(Arrays.asList("message|first\nsecond", "message|third"), callback.events);
    }

    @Test
    public void aJsonResponseToAStreamingRequestIsNotParsedAsSse() throws Exception {
        String url = serve("application/json",
                Collections.singletonList("{\"choices\":[]}".getBytes(UTF_8)), 0, false);
        RecordingStream callback = new RecordingStream();

        execute(url, callback, 5_000);

        assertTrue(callback.await());
        assertTrue("a JSON body must never be read line by line as SSE",
                callback.events.isEmpty());
        assertNotNull(callback.failure);
    }

    @Test
    public void aDisconnectWithoutATerminatingBlankLineDropsThePartialEvent() throws Exception {
        String url = serve("text/event-stream",
                Collections.singletonList("data: half".getBytes(UTF_8)), 0, false);
        RecordingStream callback = new RecordingStream();

        execute(url, callback, 5_000);

        assertTrue(callback.await());
        assertTrue("an unterminated event must be dropped", callback.events.isEmpty());
        assertNotNull("the HTTP read itself finished", callback.response);
    }

    @Test
    public void cancellingStopsTheReadAndDeliversNoTerminalCallback() throws Exception {
        String url = serve("text/event-stream", heartbeats(60), 100, true);
        RecordingStream callback = new RecordingStream();

        HttpRequestExecutor.HttpCall call = execute(url, callback, 10_000);
        Thread.sleep(300);
        call.cancel();
        Thread.sleep(600);

        assertNull("a cancelled call must not report success", callback.response);
        assertNull("a cancelled call must not report failure", callback.failure);
        assertTrue("only the events already read may arrive", callback.events.size() <= 4);
    }

    @Test
    public void theCallTimeoutBoundsANeverEndingStream() throws Exception {
        String url = serve("text/event-stream", heartbeats(200), 120, true);
        RecordingStream callback = new RecordingStream();

        execute(url, callback, 700);

        assertTrue("a steady heartbeat must not extend the total deadline", callback.await());
        assertNull(callback.response);
        assertNotNull(callback.failure);
        assertEquals(HttpRequestExecutor.FailureReason.TIMEOUT, callback.failure.getReason());
    }

    @Test
    public void anOversizedLineTerminatesTheStream() throws Exception {
        StringBuilder line = new StringBuilder("data: ");
        for (int i = 0; i < SseEventReader.MAX_LINE_BYTES + 32; i++) line.append('x');
        line.append("\n\n");
        String url = serve("text/event-stream",
                Collections.singletonList(line.toString().getBytes(UTF_8)), 0, true);
        RecordingStream callback = new RecordingStream();

        execute(url, callback, 20_000);

        assertTrue(callback.await());
        assertNotNull("an oversized line must fail the call, not buffer forever", callback.failure);
    }

    private HttpRequestExecutor.HttpCall execute(String url, RecordingStream callback, long timeoutMs) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");

        return new OkHttpRequestExecutor().execute(
                new HttpRequestExecutor.HttpRequest("POST", url, headers, "{}", timeoutMs),
                callback);
    }

    private static List<byte[]> heartbeats(int count) {
        List<byte[]> fragments = new ArrayList<>();
        for (int i = 0; i < count; i++) fragments.add(": ping\n\n".getBytes(UTF_8));
        return fragments;
    }

    private static List<byte[]> splitEveryByte(byte[] payload) {
        List<byte[]> fragments = new ArrayList<>();
        for (byte value : payload) fragments.add(new byte[] {value});
        return fragments;
    }

    /**
     * Answers exactly one request with the given content type and body fragments, then closes
     * the connection so the client sees end of stream.
     */
    private String serve(String contentType, final List<byte[]> fragments, final long delayMs,
                         boolean keepOpen) {
        final ServerSocket server = mServer;

        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                readRequest(socket.getInputStream());
                OutputStream output = socket.getOutputStream();
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType
                        + "\r\nConnection: close\r\n\r\n").getBytes(UTF_8));
                output.flush();

                for (byte[] fragment : fragments) {
                    output.write(fragment);
                    output.flush();
                    if (delayMs > 0) Thread.sleep(delayMs);
                }

                if (keepOpen) Thread.sleep(30_000);
            } catch (Exception ignored) {
                // The client cancelling closes the socket; the assertions are in the test body.
            }
        });
        thread.setDaemon(true);
        thread.start();

        return "http://127.0.0.1:" + mServer.getLocalPort() + "/v1/chat/completions";
    }

    /** Consumes the request head so the client's write can finish. */
    private static void readRequest(InputStream input) throws IOException {
        int matched = 0;
        int value;

        while ((value = input.read()) >= 0) {
            switch (matched) {
                case 0:
                case 2:
                    matched = value == '\r' ? matched + 1 : 0;
                    break;
                case 1:
                case 3:
                    matched = value == '\n' ? matched + 1 : (value == '\r' ? 1 : 0);
                    break;
                default:
                    return;
            }

            if (matched == 4) return;
        }
    }

    private static final class RecordingStream implements HttpRequestExecutor.StreamCallback {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch mDone = new CountDownLatch(1);
        private volatile HttpRequestExecutor.HttpResponse response;
        private volatile HttpRequestExecutor.HttpFailure failure;

        @Override
        public void onEvent(String eventType, String data) {
            events.add(eventType + "|" + data);
        }

        @Override
        public void onSuccess(HttpRequestExecutor.HttpResponse response) {
            this.response = response;
            mDone.countDown();
        }

        @Override
        public void onFailure(HttpRequestExecutor.HttpFailure failure) {
            this.failure = failure;
            mDone.countDown();
        }

        boolean await() throws InterruptedException {
            return mDone.await(20, TimeUnit.SECONDS);
        }
    }
}
