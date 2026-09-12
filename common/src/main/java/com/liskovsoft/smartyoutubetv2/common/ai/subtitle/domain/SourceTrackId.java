package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

/**
 * Stable identity of one SmartTube source caption track: the video it belongs to, the
 * caption-track identity within that video, and the language label.
 *
 * <p>Immutable value object; equality and hashing follow all three fields so a lookup keyed
 * by track identity can never alias two different tracks. Blank identity fields are rejected
 * because a track without a video id, track id, or language cannot be matched reliably.</p>
 */
public final class SourceTrackId {
    private final String mVideoId;
    private final String mTrackId;
    private final String mLanguage;

    public SourceTrackId(String videoId, String trackId, String language) {
        mVideoId = requireNonBlank(videoId, "videoId");
        mTrackId = requireNonBlank(trackId, "trackId");
        mLanguage = requireNonBlank(language, "language");
    }

    public String getVideoId() {
        return mVideoId;
    }

    public String getTrackId() {
        return mTrackId;
    }

    public String getLanguage() {
        return mLanguage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceTrackId)) {
            return false;
        }
        SourceTrackId other = (SourceTrackId) o;
        return sameValue(mVideoId, other.mVideoId)
                && sameValue(mTrackId, other.mTrackId)
                && sameValue(mLanguage, other.mLanguage);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mVideoId);
        result = 31 * result + valueHash(mTrackId);
        result = 31 * result + valueHash(mLanguage);
        return result;
    }

    @Override
    public String toString() {
        return "SourceTrackId{video=" + mVideoId + ", track=" + mTrackId + ", lang=" + mLanguage + "}";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
