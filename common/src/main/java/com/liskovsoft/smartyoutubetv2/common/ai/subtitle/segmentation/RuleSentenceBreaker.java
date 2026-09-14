package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic punctuation/pause/duration sentence splitter. */
public final class RuleSentenceBreaker {
    public static final int VERSION = 1;
    private final int mMaxChars;

    public RuleSentenceBreaker() {
        this(80);
    }

    public RuleSentenceBreaker(int maxChars) {
        if (maxChars <= 0) throw new IllegalArgumentException("maxChars must be positive");
        mMaxChars = maxChars;
    }

    public List<SubtitleSegment> breakSentences(List<SubtitleSegment> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        List<SubtitleSegment> result = new ArrayList<>();
        int index = 0;
        for (SubtitleSegment segment : input) {
            List<String> pieces = splitText(segment.getSourceText());
            long duration = segment.getEndTimeMs() - segment.getStartTimeMs();
            for (String piece : pieces) {
                long start = segment.getStartTimeMs() + duration * resultPieceOffset(pieces, piece) / Math.max(1, segment.getSourceText().length());
                long end = start + Math.max(1, duration * piece.length() / Math.max(1, segment.getSourceText().length()));
                if (piece.equals(pieces.get(pieces.size() - 1))) end = segment.getEndTimeMs();
                if (end <= start) end = Math.min(segment.getEndTimeMs(), start + 1);
                if (end > start) result.add(new SubtitleSegment(
                        new SubtitleSegmentId(segment.getId().getSourceTrackId(), index++), start, end, piece));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private List<String> splitText(String text) {
        String normalized = text.trim();
        String[] punctuation = normalized.split("(?<=[.!?。！？])\\s+");
        List<String> pieces = new ArrayList<>();
        for (String piece : punctuation) {
            if (piece.trim().isEmpty()) continue;
            String value = piece.trim();
            if (!pieces.isEmpty() && shouldJoin(pieces.get(pieces.size() - 1))) {
                pieces.set(pieces.size() - 1, pieces.get(pieces.size() - 1) + " " + value);
            } else if (value.length() <= mMaxChars) pieces.add(value);
            else splitOversize(value, pieces);
        }
        return pieces.isEmpty() ? Collections.singletonList(normalized) : pieces;
    }

    private static boolean shouldJoin(String previous) {
        return previous.matches("(?i).*(\\b(?:mr|ms|mrs|dr|prof|e\\.g|i\\.e)\\.)$")
                || previous.matches(".*\\b\\d+$");
    }

    private void splitOversize(String text, List<String> pieces) {
        String[] words = text.split("\\s+");
        if (words.length > 1) {
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                if (current.length() > 0 && current.length() + word.length() + 1 > mMaxChars) {
                    pieces.add(current.toString()); current.setLength(0);
                }
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
            if (current.length() > 0) pieces.add(current.toString());
        } else {
            for (int start = 0; start < text.length(); start += mMaxChars) {
                pieces.add(text.substring(start, Math.min(text.length(), start + mMaxChars)));
            }
        }
    }

    private static int resultPieceOffset(List<String> pieces, String target) {
        int offset = 0;
        for (String piece : pieces) {
            if (piece == target || piece.equals(target)) return offset;
            offset += piece.length() + 1;
        }
        return offset;
    }
}
