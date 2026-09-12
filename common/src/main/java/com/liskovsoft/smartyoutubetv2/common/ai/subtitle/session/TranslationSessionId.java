package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;

/**
 * Identity of one Translation Session: the video, the Source Track, the resolved Translation
 * Profile, and the engine schema version whose combination defines when translation output is
 * reusable.
 *
 * <p>Any change to one of these fields begins a new session generation; a seek does not.</p>
 */
public final class TranslationSessionId {
    /** Bumped whenever the feature's output-affecting contract changes. */
    public static final int ENGINE_SCHEMA_VERSION = 1;

    private final String mVideoId;
    private final SourceTrackId mSourceTrackId;
    private final TranslationProfile mProfile;
    private final int mEngineSchemaVersion;

    public TranslationSessionId(String videoId, SourceTrackId sourceTrackId,
                                TranslationProfile profile, int engineSchemaVersion) {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("videoId must not be blank");
        }
        if (sourceTrackId == null) {
            throw new IllegalArgumentException("sourceTrackId must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (engineSchemaVersion < 1) {
            throw new IllegalArgumentException("engineSchemaVersion must be positive: " + engineSchemaVersion);
        }

        mVideoId = videoId;
        mSourceTrackId = sourceTrackId;
        mProfile = profile;
        mEngineSchemaVersion = engineSchemaVersion;
    }

    public String getVideoId() {
        return mVideoId;
    }

    public SourceTrackId getSourceTrackId() {
        return mSourceTrackId;
    }

    public TranslationProfile getProfile() {
        return mProfile;
    }

    public int getEngineSchemaVersion() {
        return mEngineSchemaVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TranslationSessionId)) {
            return false;
        }
        TranslationSessionId other = (TranslationSessionId) o;
        return mEngineSchemaVersion == other.mEngineSchemaVersion
                && sameValue(mVideoId, other.mVideoId)
                && sameValue(mSourceTrackId, other.mSourceTrackId)
                && sameValue(mProfile, other.mProfile);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mVideoId);
        result = 31 * result + valueHash(mSourceTrackId);
        result = 31 * result + valueHash(mProfile);
        result = 31 * result + mEngineSchemaVersion;
        return result;
    }

    @Override
    public String toString() {
        return "TranslationSessionId{video=" + mVideoId + ", track=" + mSourceTrackId
                + ", profile=" + mProfile + ", engine=" + mEngineSchemaVersion + "}";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
