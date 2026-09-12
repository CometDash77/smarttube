package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

/**
 * One time-bounded piece of text decoded from the Source Track before AI-specific
 * normalization or segmentation.
 *
 * <p>Immutable value object. Timing must describe a real interval: non-negative start and an
 * end strictly after the start. Text may be empty (before normalization removes non-speech
 * noise) but must never be null.</p>
 */
public final class SourceCue {
    private final long mStartTimeMs;
    private final long mEndTimeMs;
    private final String mText;

    public SourceCue(long startTimeMs, long endTimeMs, String text) {
        if (startTimeMs < 0) {
            throw new IllegalArgumentException("startTimeMs must be non-negative: " + startTimeMs);
        }
        if (endTimeMs <= startTimeMs) {
            throw new IllegalArgumentException(
                    "endTimeMs must be after startTimeMs: " + startTimeMs + " -> " + endTimeMs);
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }

        mStartTimeMs = startTimeMs;
        mEndTimeMs = endTimeMs;
        mText = text;
    }

    public long getStartTimeMs() {
        return mStartTimeMs;
    }

    public long getEndTimeMs() {
        return mEndTimeMs;
    }

    public String getText() {
        return mText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceCue)) {
            return false;
        }
        SourceCue other = (SourceCue) o;
        return mStartTimeMs == other.mStartTimeMs
                && mEndTimeMs == other.mEndTimeMs
                && sameValue(mText, other.mText);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = (int) (mStartTimeMs ^ (mStartTimeMs >>> 32));
        result = 31 * result + (int) (mEndTimeMs ^ (mEndTimeMs >>> 32));
        result = 31 * result + valueHash(mText);
        return result;
    }

    @Override
    public String toString() {
        return "SourceCue{" + mStartTimeMs + ".." + mEndTimeMs + ", text=" + mText + "}";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
