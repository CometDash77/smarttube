package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

/**
 * Stable identity of one {@link SubtitleSegment} inside a normalized timeline: the Source
 * Track it belongs to plus its zero-based position in that timeline.
 *
 * <p>Position is part of the identity, so identical text at different timeline positions can
 * never alias in a lookup.</p>
 */
public final class SubtitleSegmentId {
    private final SourceTrackId mSourceTrackId;
    private final int mIndex;

    public SubtitleSegmentId(SourceTrackId sourceTrackId, int index) {
        if (sourceTrackId == null) {
            throw new IllegalArgumentException("sourceTrackId must not be null");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative: " + index);
        }

        mSourceTrackId = sourceTrackId;
        mIndex = index;
    }

    public SourceTrackId getSourceTrackId() {
        return mSourceTrackId;
    }

    public int getIndex() {
        return mIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubtitleSegmentId)) {
            return false;
        }
        SubtitleSegmentId other = (SubtitleSegmentId) o;
        return mIndex == other.mIndex && sameValue(mSourceTrackId, other.mSourceTrackId);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mSourceTrackId);
        result = 31 * result + mIndex;
        return result;
    }

    @Override
    public String toString() {
        return "SubtitleSegmentId{" + mSourceTrackId + "#" + mIndex + "}";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
