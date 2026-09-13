package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small observable metrics object for boundary and timeline diagnostics. */
public final class SegmentationMetrics {
    public Metrics measure(List<SubtitleSegment> source, List<BoundaryProtocol.Item> items) {
        int expected = source == null ? 0 : source.size();
        Set<Integer> covered = new HashSet<>();
        int duplicates = 0;
        int overlaps = 0;
        int invalid = 0;
        int previousEnd = -1;
        if (items != null) {
            for (BoundaryProtocol.Item item : items) {
                if (item == null || item.getStartIndex() < 0 || item.getEndIndex() >= expected
                        || item.getEndIndex() < item.getStartIndex()) {
                    invalid++;
                    continue;
                }
                if (item.getStartIndex() <= previousEnd) overlaps++;
                for (int i = item.getStartIndex(); i <= item.getEndIndex(); i++) {
                    if (!covered.add(i)) duplicates++;
                }
                previousEnd = item.getEndIndex();
            }
        }
        return new Metrics(expected, covered.size(), duplicates, overlaps, invalid,
                expected > 0 && covered.size() == expected);
    }

    public static final class Metrics {
        private final int mExpected;
        private final int mCovered;
        private final int mDuplicates;
        private final int mOverlaps;
        private final int mInvalid;
        private final boolean mComplete;

        Metrics(int expected, int covered, int duplicates, int overlaps, int invalid,
                boolean complete) {
            mExpected = expected;
            mCovered = covered;
            mDuplicates = duplicates;
            mOverlaps = overlaps;
            mInvalid = invalid;
            mComplete = complete;
        }

        public int getExpectedCount() { return mExpected; }
        public int getCoveredCount() { return mCovered; }
        public int getDuplicateCount() { return mDuplicates; }
        public int getOverlapCount() { return mOverlaps; }
        public int getInvalidCount() { return mInvalid; }
        public boolean isComplete() { return mComplete; }
    }
}
