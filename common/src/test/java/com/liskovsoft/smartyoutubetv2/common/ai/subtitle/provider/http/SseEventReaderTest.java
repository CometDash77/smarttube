package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Wire-format framing tests; the rules come from the WHATWG server-sent events format. */
public class SseEventReaderTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final List<String> mEvents = new ArrayList<>();

    private SseEventReader.Sink sink() {
        return (type, data) -> mEvents.add(type + "|" + data);
    }

    private void read(String payload) throws IOException {
        new SseEventReader().read(stream(payload), sink());
    }

    @Test
    public void lfCrLfAndCrAllTerminateLines() throws IOException {
        read("data: one\n\n"
                + "data: two\r\n\r\n"
                + "data: three\r\r");

        assertEquals(Arrays.asList("message|one", "message|two", "message|three"), mEvents);
    }

    @Test
    public void aLeadingByteOrderMarkIsIgnored() throws IOException {
        read("﻿data: hello\n\n");

        assertEquals(Arrays.asList("message|hello"), mEvents);
    }

    @Test
    public void commentsAndUnknownFieldsAreIgnored() throws IOException {
        read(": keep-alive\n"
                + "retry: 100\n"
                + "id: 42\n"
                + "data: payload\n\n");

        assertEquals(Arrays.asList("message|payload"), mEvents);
    }

    @Test
    public void multipleDataLinesAreJoinedWithNewlines() throws IOException {
        read("data: first\ndata: second\n\n");

        assertEquals(Arrays.asList("message|first\nsecond"), mEvents);
    }

    @Test
    public void anEventTypeIsCarriedToTheSink() throws IOException {
        read("event: content_block_delta\ndata: {\"a\":1}\n\n");

        assertEquals(Arrays.asList("content_block_delta|{\"a\":1}"), mEvents);
    }

    @Test
    public void anEventWithNoDataIsNotDispatched() throws IOException {
        read("event: ping\n\ndata: real\n\n");

        assertEquals(Arrays.asList("message|real"), mEvents);
    }

    @Test
    public void aPartialEventAtEndOfStreamIsDropped() throws IOException {
        read("data: complete\n\ndata: never dispatched");

        assertEquals("an unterminated event must not be reported as complete",
                Arrays.asList("message|complete"), mEvents);
    }

    @Test
    public void utf8SplitAcrossReadBoundariesSurvives() throws IOException {
        byte[] payload = "data: 字幕\n\n".getBytes(UTF_8);
        InputStream input = new ByteArrayInputStream(payload) {
            @Override
            public synchronized int read() {
                // Feed one byte at a time so a multi-byte character is always split.
                return super.read();
            }

            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                return length <= 0 ? 0 : super.read(buffer, offset, 1);
            }
        };

        new SseEventReader().read(input, sink());

        assertEquals(Arrays.asList("message|字幕"), mEvents);
    }

    @Test
    public void anOversizedLineTerminatesTheRead() {
        StringBuilder payload = new StringBuilder("data: ");
        for (int i = 0; i < SseEventReader.MAX_LINE_BYTES + 16; i++) payload.append('x');
        payload.append("\n\n");

        assertLimitExceeded(payload.toString());
    }

    @Test
    public void anOversizedEventTerminatesTheRead() {
        StringBuilder payload = new StringBuilder();
        int lineLength = 8 * 1024;
        for (int i = 0; i < (SseEventReader.MAX_EVENT_BYTES / lineLength) + 4; i++) {
            payload.append("data: ");
            for (int j = 0; j < lineLength; j++) payload.append('y');
            payload.append('\n');
        }
        payload.append('\n');

        assertLimitExceeded(payload.toString());
    }

    @Test
    public void anOversizedResponseTerminatesTheRead() {
        StringBuilder payload = new StringBuilder();
        while (payload.length() < SseEventReader.MAX_RESPONSE_BYTES + 4096) {
            payload.append("data: z\n\n");
        }

        assertLimitExceeded(payload.toString());
    }

    private void assertLimitExceeded(String payload) {
        try {
            read(payload);
            fail("a limit must terminate the read instead of buffering without bound");
        } catch (SseEventReader.LimitExceededException expected) {
            assertTrue(expected.getMessage().contains("exceeded"));
        } catch (IOException unexpected) {
            fail("expected a limit failure, got " + unexpected);
        }
    }

    private static InputStream stream(String payload) {
        return new ByteArrayInputStream(payload.getBytes(UTF_8));
    }
}
