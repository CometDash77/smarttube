package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;

import java.util.ArrayList;
import java.util.List;

/** Parses WebVTT caption text into timed {@link SourceCue}s. */
public final class VttParser {
    public List<SourceCue> parse(String vtt) {
        if (vtt == null) return new ArrayList<>();
        List<SourceCue> result = new ArrayList<>();
        String[] lines = vtt.split("\r?\n");
        int i = 0;
        while (i < lines.length) {
            if (lines[i].contains("-->")) {
                result.add(parseCue(lines, i));
                // Skip to end of cue block (blank line separates cues).
                while (i < lines.length && !lines[i].trim().isEmpty()) i++;
            }
            i++;
        }
        return result;
    }

    private static SourceCue parseCue(String[] lines, int timestampLine) {
        String[] times = lines[timestampLine].split("-->");
        long start = parseTimestamp(times[0].trim());
        long end = parseTimestamp(times[1].trim().split("\\s+")[0]);
        StringBuilder text = new StringBuilder();
        for (int i = timestampLine + 1; i < lines.length && !lines[i].trim().isEmpty(); i++) {
            if (text.length() > 0) text.append('\n');
            text.append(lines[i].trim());
        }
        return new SourceCue(start, end, text.toString());
    }

    static long parseTimestamp(String ts) {
        // Formats: MM:SS.mmm or HH:MM:SS.mmm
        String[] parts = ts.split(":");
        long seconds = 0;
        int first = 0;
        if (parts.length == 3) { seconds += Long.parseLong(parts[0]) * 3600; first = 1; }
        seconds += Long.parseLong(parts[first]) * 60;
        String[] secParts = parts[parts.length - 1].split("\\.");
        seconds += Long.parseLong(secParts[0]);
        long ms = secParts.length > 1 ? Long.parseLong(padRight(secParts[1], 3)) : 0;
        return seconds * 1000 + ms;
    }

    private static String padRight(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append('0');
        return sb.substring(0, width);
    }
}
