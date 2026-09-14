package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Frames a {@code text/event-stream} body into whole events.
 *
 * <p>This is wire-format framing only, following the WHATWG server-sent events rules: UTF-8
 * lines terminated by LF, CRLF, or CR; an optional leading byte-order mark; lines starting
 * with {@code :} are comments; {@code data} lines accumulate joined by newlines; a blank line
 * dispatches the event; unknown fields including {@code retry} are ignored, so a server cannot
 * ask this client to re-time its own retries. A partial event that reaches end of stream
 * without a terminating blank line is dropped rather than reported as complete.</p>
 *
 * <p>Hard limits are enforced while reading, not after buffering, so a hostile or broken
 * server cannot make the client allocate without bound.</p>
 */
public final class SseEventReader {
    /** Maximum size of one line, in bytes. */
    public static final int MAX_LINE_BYTES = 64 * 1024;
    /** Maximum accumulated size of one event, in bytes. */
    public static final int MAX_EVENT_BYTES = 64 * 1024;
    /** Maximum size of the whole response body, in bytes. */
    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    /** Receives one complete event. */
    public interface Sink {
        void onEvent(String eventType, String data);
    }

    /** Raised when a line, an event, or the response exceeds its limit. */
    public static final class LimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;

        LimitExceededException(String message) {
            super(message);
        }
    }

    /**
     * Reads until end of stream, dispatching complete events. Returns the number of bytes
     * consumed. Throws {@link LimitExceededException} when a limit is reached; the caller owns
     * closing the stream.
     */
    public long read(InputStream input, Sink sink) throws IOException {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        if (sink == null) throw new IllegalArgumentException("sink must not be null");

        ByteArrayOutputStream line = new ByteArrayOutputStream(128);
        EventBuilder event = new EventBuilder();
        long total = 0;
        boolean pendingCarriageReturn = false;
        boolean firstLine = true;
        int value;

        while ((value = input.read()) >= 0) {
            total++;

            if (total > MAX_RESPONSE_BYTES) {
                throw new LimitExceededException("SSE response exceeded " + MAX_RESPONSE_BYTES + " bytes");
            }

            if (pendingCarriageReturn) {
                pendingCarriageReturn = false;
                if (value == '\n') continue;
            }

            if (value == '\n' || value == '\r') {
                pendingCarriageReturn = value == '\r';

                String text = decode(line);
                int lineBytes = line.size();
                line.reset();

                if (firstLine) {
                    firstLine = false;
                    if (text.startsWith(BYTE_ORDER_MARK)) text = text.substring(BYTE_ORDER_MARK.length());
                }

                handleLine(text, lineBytes, event, sink);
                continue;
            }

            if (line.size() >= MAX_LINE_BYTES) {
                throw new LimitExceededException("SSE line exceeded " + MAX_LINE_BYTES + " bytes");
            }

            line.write(value);
        }

        // A trailing line without a terminator is data; a partial event is never dispatched.
        if (line.size() > 0) {
            String text = decode(line);
            if (firstLine && text.startsWith(BYTE_ORDER_MARK)) {
                text = text.substring(BYTE_ORDER_MARK.length());
            }
            handleLine(text, line.size(), event, sink);
        }

        return total;
    }

    private static void handleLine(String line, int lineBytes, EventBuilder event, Sink sink)
            throws IOException {
        if (line.isEmpty()) {
            dispatch(event, sink);
            return;
        }

        if (line.charAt(0) == ':') return;

        int colon = line.indexOf(':');
        String field = colon >= 0 ? line.substring(0, colon) : line;
        String value = "";

        if (colon >= 0) {
            value = line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
        }

        if ("event".equals(field)) {
            event.mType = value;
        } else if ("data".equals(field)) {
            event.mData.append(value).append('\n');
            event.mBytes += lineBytes;

            if (event.mBytes > MAX_EVENT_BYTES) {
                throw new LimitExceededException("SSE event exceeded " + MAX_EVENT_BYTES + " bytes");
            }
        }
        // Every other field, "retry" included, is deliberately ignored.
    }

    private static void dispatch(EventBuilder event, Sink sink) {
        String data = event.mData.toString();
        String type = event.mType;

        event.reset();

        if (data.isEmpty()) return;
        if (data.endsWith("\n")) data = data.substring(0, data.length() - 1);
        if (data.isEmpty()) return;

        sink.onEvent(type != null ? type : "message", data);
    }

    private static String decode(ByteArrayOutputStream line) {
        return new String(line.toByteArray(), UTF_8);
    }

    private static final class EventBuilder {
        private final StringBuilder mData = new StringBuilder(256);
        private String mType;
        private int mBytes;

        private void reset() {
            mData.setLength(0);
            mType = null;
            mBytes = 0;
        }
    }
}
