package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

/**
 * A normalized, time-bounded unit of source text that can be rendered and grouped for
 * translation.
 *
 * <p>Immutable value object. After normalization a segment must carry visible semantic text
 * (non-blank) and a real time interval (non-negative start, end strictly after the start).</p>
 */
public final class SubtitleSegment {
    private final SubtitleSegmentId mId;
    private final long mStartTimeMs;
    private final long mEndTimeMs;
    private final String mSourceText;

    public SubtitleSegment(SubtitleSegmentId id, long startTimeMs, long endTimeMs, String sourceText) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (startTimeMs < 0) {
            throw new IllegalArgumentException("startTimeMs must be non-negative: " + startTimeMs);
        }
        if (endTimeMs <= startTimeMs) {
            throw new IllegalArgumentException(
                    "endTimeMs must be after startTimeMs: " + startTimeMs + " -> " + endTimeMs);
        }
        if (sourceText == null || sourceText.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceText must not be blank");
        }

        mId = id;
        mStartTimeMs = startTimeMs;
        mEndTimeMs = endTimeMs;
        mSourceText = sourceText;
    }

    public SubtitleSegmentId getId() {
        return mId;
    }

    public long getStartTimeMs() {
        return mStartTimeMs;
    }

    public long getEndTimeMs() {
        return mEndTimeMs;
    }

    public String getSourceText() {
        return mSourceText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubtitleSegment)) {
            return false;
        }
        SubtitleSegment other = (SubtitleSegment) o;
        return mStartTimeMs == other.mStartTimeMs
                && mEndTimeMs == other.mEndTimeMs
                && sameValue(mId, other.mId)
                && sameValue(mSourceText, other.mSourceText);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mId);
        result = 31 * result + (int) (mStartTimeMs ^ (mStartTimeMs >>> 32));
        result = 31 * result + (int) (mEndTimeMs ^ (mEndTimeMs >>> 32));
        result = 31 * result + valueHash(mSourceText);
        return result;
    }

    @Override
    public String toString() {
        return "SubtitleSegment{" + mId + ", " + mStartTimeMs + ".." + mEndTimeMs
                + ", text=" + mSourceText + "}";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
