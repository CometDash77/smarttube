package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Normalizes decoded cues without changing their source ordering. */
public final class SubtitleNormalizer {
    private static final long DUPLICATE_GAP_LIMIT_MS = 1_000;

    public List<SourceCue> normalize(List<SourceCue> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        List<SourceCue> result = new ArrayList<>();
        SourceCue previous = null;
        for (SourceCue cue : input) {
            if (cue == null) continue;
            String text = clean(cue.getText());
            if (text.isEmpty() || isNoise(text)) continue;
            SourceCue current = new SourceCue(cue.getStartTimeMs(), cue.getEndTimeMs(), text);
            if (previous != null && current.getStartTimeMs() < previous.getEndTimeMs()) {
                long clippedEnd = Math.min(previous.getEndTimeMs(), current.getStartTimeMs());
                if (clippedEnd > previous.getStartTimeMs()) {
                    result.set(result.size() - 1, new SourceCue(previous.getStartTimeMs(),
                            clippedEnd, previous.getText()));
                    previous = result.get(result.size() - 1);
                } else {
                    result.remove(result.size() - 1);
                    previous = result.isEmpty() ? null : result.get(result.size() - 1);
                }
            }
            if (previous != null && previous.getText().equals(current.getText())
                    && current.getStartTimeMs() - previous.getEndTimeMs() <= DUPLICATE_GAP_LIMIT_MS) {
                continue;
            }
            result.add(current);
            previous = current;
        }
        return Collections.unmodifiableList(result);
    }

    private static String clean(String text) {
        return text.replaceAll("<[^>]*>", "")
                .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isNoise(String text) {
        String lower = text.toLowerCase(java.util.Locale.US);
        return lower.matches("[\\[\\(](music|applause|laughter|noise|拍手|音樂|音乐)[\\]\\)]")
                || lower.matches("[-_ .…]+") || lower.matches("\\p{Punct}+");
    }
}
