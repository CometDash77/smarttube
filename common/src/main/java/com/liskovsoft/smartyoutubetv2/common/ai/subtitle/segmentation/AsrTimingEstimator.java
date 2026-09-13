package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Estimates monotonic word/character timing for a coarse ASR cue. */
public final class AsrTimingEstimator {
    public List<SourceCue> estimate(SourceCue cue, long nextEffectiveEventStartMs) {
        if (cue == null) throw new IllegalArgumentException("cue must not be null");
        long cappedEnd = nextEffectiveEventStartMs > cue.getStartTimeMs()
                ? Math.min(cue.getEndTimeMs(), nextEffectiveEventStartMs) : cue.getEndTimeMs();
        if (cappedEnd <= cue.getStartTimeMs()) return Collections.singletonList(cue);
        List<String> tokens = tokenize(cue.getText());
        if (tokens.size() <= 1) return Collections.singletonList(cue);
        long duration = cappedEnd - cue.getStartTimeMs();
        List<SourceCue> result = new ArrayList<>();
        long start = cue.getStartTimeMs();
        for (int i = 0; i < tokens.size(); i++) {
            long end = i == tokens.size() - 1 ? cappedEnd
                    : cue.getStartTimeMs() + Math.max(1, duration * (i + 1) / tokens.size());
            if (end <= start) end = Math.min(cappedEnd, start + 1);
            if (end > start) result.add(new SourceCue(start, end, tokens.get(i)));
            start = end;
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> tokenize(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();
        String[] words = trimmed.split("\\s+");
        if (words.length > 1) {
            List<String> result = new ArrayList<>();
            Collections.addAll(result, words);
            return result;
        }
        int codePointCount = trimmed.codePointCount(0, trimmed.length());
        if (codePointCount > 1 && containsNoSpaceScript(trimmed)) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < trimmed.length();) {
                int next = trimmed.offsetByCodePoints(i, 1);
                result.add(trimmed.substring(i, next));
                i = next;
            }
            return result;
        }
        return Collections.singletonList(trimmed);
    }

    private static boolean containsNoSpaceScript(String text) {
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if ((codePoint >= 0x3040 && codePoint <= 0x30ff)
                    || (codePoint >= 0x3400 && codePoint <= 0x9fff)
                    || (codePoint >= 0xac00 && codePoint <= 0xd7af)) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }
}
