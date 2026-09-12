package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One or more contiguous Subtitle Segments translated in a single logical request and mapped
 * back without losing source coverage.
 *
 * <p>Immutable value object. The segment identities must all belong to the same Source Track
 * and form an ordered, gap-free run; a unit can never silently drop or reorder source
 * coverage.</p>
 */
public final class TranslationUnit {
    private final List<SubtitleSegmentId> mSegmentIds;
    private final String mSourceText;

    public TranslationUnit(List<SubtitleSegmentId> segmentIds, String sourceText) {
        if (segmentIds == null || segmentIds.isEmpty()) {
            throw new IllegalArgumentException("segmentIds must not be empty");
        }
        if (sourceText == null || sourceText.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceText must not be blank");
        }

        List<SubtitleSegmentId> copy = new ArrayList<>(segmentIds);

        for (int i = 0; i < copy.size(); i++) {
            SubtitleSegmentId id = copy.get(i);

            if (id == null) {
                throw new IllegalArgumentException("segmentIds[" + i + "] must not be null");
            }

            if (i > 0) {
                SubtitleSegmentId previous = copy.get(i - 1);

                if (!sameValue(id.getSourceTrackId(), previous.getSourceTrackId())) {
                    throw new IllegalArgumentException("all segment ids must share one Source Track");
                }

                if (id.getIndex() != previous.getIndex() + 1) {
                    throw new IllegalArgumentException(
                            "segment ids must be ordered, contiguous, and duplicate-free: "
                                    + previous.getIndex() + " -> " + id.getIndex());
                }
            }
        }

        mSegmentIds = copy;
        mSourceText = sourceText;
    }

    public List<SubtitleSegmentId> getSegmentIds() {
        return Collections.unmodifiableList(mSegmentIds);
    }

    public SubtitleSegmentId getFirstSegmentId() {
        return mSegmentIds.get(0);
    }

    public SubtitleSegmentId getLastSegmentId() {
        return mSegmentIds.get(mSegmentIds.size() - 1);
    }

    public int getSegmentCount() {
        return mSegmentIds.size();
    }

    public String getSourceText() {
        return mSourceText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TranslationUnit)) {
            return false;
        }
        TranslationUnit other = (TranslationUnit) o;
        return sameValue(mSegmentIds, other.mSegmentIds)
                && sameValue(mSourceText, other.mSourceText);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mSegmentIds);
        result = 31 * result + valueHash(mSourceText);
        return result;
    }

    @Override
    public String toString() {
        return "TranslationUnit{" + mSegmentIds + ", text=" + mSourceText + "}";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
