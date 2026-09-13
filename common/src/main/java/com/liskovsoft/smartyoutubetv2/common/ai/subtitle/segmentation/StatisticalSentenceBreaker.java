package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Robust gap-based grouping using median and MAD instead of a single fragile threshold. */
public final class StatisticalSentenceBreaker {
    public static final int VERSION = 1;

    public List<List<SubtitleSegment>> group(List<SubtitleSegment> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        if (input.size() == 1) return Collections.singletonList(input);
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < input.size(); i++) gaps.add(Math.max(0L,
                input.get(i).getStartTimeMs() - input.get(i - 1).getEndTimeMs()));
        long median = median(gaps);
        List<Long> deviations = new ArrayList<>();
        for (Long gap : gaps) deviations.add(Math.abs(gap - median));
        long mad = median(deviations);
        long boundary = median + Math.max(250L, mad * 3L);
        List<List<SubtitleSegment>> groups = new ArrayList<>();
        List<SubtitleSegment> current = new ArrayList<>();
        current.add(input.get(0));
        for (int i = 1; i < input.size(); i++) {
            SubtitleSegment previous = input.get(i - 1);
            SubtitleSegment next = input.get(i);
            long gap = Math.max(0L, next.getStartTimeMs() - previous.getEndTimeMs());
            if (gap > boundary || endsSentence(previous.getSourceText())
                    || startsNewSentence(next.getSourceText())) {
                groups.add(Collections.unmodifiableList(new ArrayList<>(current)));
                current.clear();
            }
            current.add(next);
        }
        groups.add(Collections.unmodifiableList(new ArrayList<>(current)));
        return Collections.unmodifiableList(groups);
    }

    private static boolean endsSentence(String text) {
        if (text == null || text.isEmpty()) return false;
        return ".!?。！？".indexOf(text.charAt(text.length() - 1)) >= 0;
    }

    private static boolean startsNewSentence(String text) {
        return text != null && !text.isEmpty() && Character.isUpperCase(text.charAt(0));
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
