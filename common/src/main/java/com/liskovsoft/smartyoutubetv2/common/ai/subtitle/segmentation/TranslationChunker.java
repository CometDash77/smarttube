package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds bounded contiguous translation units while preserving every source segment. */
public final class TranslationChunker {
    public static final int VERSION = 1;

    public List<TranslationUnit> chunk(List<SubtitleSegment> segments, int targetChars, int maxChars) {
        if (segments == null || segments.isEmpty()) return Collections.emptyList();
        if (targetChars < 1 || maxChars < targetChars) throw new IllegalArgumentException("invalid chunk limits");
        List<TranslationUnit> result = new ArrayList<>();
        List<SubtitleSegment> current = new ArrayList<>();
        int currentLength = 0;
        for (SubtitleSegment segment : segments) {
            int nextLength = currentLength == 0 ? segment.getSourceText().length()
                    : currentLength + 1 + segment.getSourceText().length();
            if (!current.isEmpty() && nextLength > maxChars) {
                result.add(toUnit(current));
                current.clear(); currentLength = 0;
            }
            current.add(segment);
            currentLength = currentLength == 0 ? segment.getSourceText().length() : nextLength;
            if (currentLength >= targetChars) {
                result.add(toUnit(current));
                current.clear(); currentLength = 0;
            }
        }
        if (!current.isEmpty()) result.add(toUnit(current));
        return Collections.unmodifiableList(result);
    }

    private static TranslationUnit toUnit(List<SubtitleSegment> segments) {
        List<SubtitleSegmentId> ids = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (SubtitleSegment segment : segments) {
            ids.add(segment.getId());
            if (text.length() > 0) text.append('\n');
            text.append(segment.getSourceText());
        }
        return new TranslationUnit(ids, text.toString());
    }
}
