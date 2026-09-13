package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;

import java.util.List;

/**
 * Immutable identity of one cacheable translation output: every input capable of changing the
 * translated text.
 *
 * <p>The key composes the session identity (engine schema version, video, Source Track, and
 * the resolved Provider Profile's profile id, protocol, base URL identity, model, Prompt
 * Profile id and version, and target language) with the translated unit's source coverage and
 * text fingerprint plus the optional context fingerprint and the segmentation/boundary
 * versions. Credential material is never part of this type; the base URL identity rejects
 * values that embed user information.</p>
 */
public final class TranslationCacheKey {
    private final int mEngineSchemaVersion;
    private final String mVideoId;
    private final SourceTrackId mSourceTrackId;
    private final String mSourceCoverageFingerprint;
    private final String mProviderProfileId;
    private final String mProviderProtocol;
    private final String mBaseUrlIdentity;
    private final String mModelId;
    private final String mPromptProfileId;
    private final int mPromptVersion;
    private final String mPromptContentHash;
    private final String mTargetLanguage;
    private final String mContextFingerprint;
    private final int mSegmentationVersion;
    private final int mBoundaryVersion;

    private TranslationCacheKey(int engineSchemaVersion, String videoId, SourceTrackId sourceTrackId,
                                String sourceCoverageFingerprint, String providerProfileId,
                                String providerProtocol, String baseUrlIdentity, String modelId,
                                String promptProfileId, int promptVersion, String promptContentHash,
                                String targetLanguage,
                                String contextFingerprint, int segmentationVersion,
                                int boundaryVersion) {
        mEngineSchemaVersion = engineSchemaVersion;
        mVideoId = videoId;
        mSourceTrackId = sourceTrackId;
        mSourceCoverageFingerprint = sourceCoverageFingerprint;
        mProviderProfileId = providerProfileId;
        mProviderProtocol = providerProtocol;
        mBaseUrlIdentity = baseUrlIdentity;
        mModelId = modelId;
        mPromptProfileId = promptProfileId;
        mPromptVersion = promptVersion;
        mPromptContentHash = promptContentHash;
        mTargetLanguage = targetLanguage;
        mContextFingerprint = contextFingerprint;
        mSegmentationVersion = segmentationVersion;
        mBoundaryVersion = boundaryVersion;
    }

    /**
     * Builds the key for one translated unit of the given session.
     */
    public static TranslationCacheKey from(TranslationSessionId sessionId, TranslationUnit unit,
                                           String contextFingerprint, int segmentationVersion,
                                           int boundaryVersion) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }

        TranslationProfile profile = sessionId.getProfile();

        return new TranslationCacheKey(
                sessionId.getEngineSchemaVersion(),
                sessionId.getVideoId(),
                sessionId.getSourceTrackId(),
                coverageFingerprint(unit),
                profile.getProviderProfileId(),
                profile.getProviderProtocol(),
                profile.getBaseUrlIdentity(),
                profile.getModelId(),
                profile.getPromptProfileId(),
                profile.getPromptVersion(),
                profile.getPromptContentHash(),
                profile.getTargetLanguage(),
                contextFingerprint,
                segmentationVersion,
                boundaryVersion);
    }

    public int getEngineSchemaVersion() {
        return mEngineSchemaVersion;
    }

    public String getVideoId() {
        return mVideoId;
    }

    public SourceTrackId getSourceTrackId() {
        return mSourceTrackId;
    }

    public String getSourceCoverageFingerprint() {
        return mSourceCoverageFingerprint;
    }

    public String getProviderProfileId() {
        return mProviderProfileId;
    }

    public String getProviderProtocol() {
        return mProviderProtocol;
    }

    public String getBaseUrlIdentity() {
        return mBaseUrlIdentity;
    }

    public String getModelId() {
        return mModelId;
    }

    public String getPromptProfileId() {
        return mPromptProfileId;
    }

    public int getPromptVersion() {
        return mPromptVersion;
    }

    public String getPromptContentHash() {
        return mPromptContentHash;
    }

    public String getTargetLanguage() {
        return mTargetLanguage;
    }

    public String getContextFingerprint() {
        return mContextFingerprint;
    }

    public int getSegmentationVersion() {
        return mSegmentationVersion;
    }

    public int getBoundaryVersion() {
        return mBoundaryVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TranslationCacheKey)) {
            return false;
        }
        TranslationCacheKey other = (TranslationCacheKey) o;
        return mEngineSchemaVersion == other.mEngineSchemaVersion
                && mPromptVersion == other.mPromptVersion
                && mSegmentationVersion == other.mSegmentationVersion
                && mBoundaryVersion == other.mBoundaryVersion
                && sameValue(mVideoId, other.mVideoId)
                && sameValue(mSourceTrackId, other.mSourceTrackId)
                && sameValue(mSourceCoverageFingerprint, other.mSourceCoverageFingerprint)
                && sameValue(mProviderProfileId, other.mProviderProfileId)
                && sameValue(mProviderProtocol, other.mProviderProtocol)
                && sameValue(mBaseUrlIdentity, other.mBaseUrlIdentity)
                && sameValue(mModelId, other.mModelId)
                && sameValue(mPromptProfileId, other.mPromptProfileId)
                && sameValue(mPromptContentHash, other.mPromptContentHash)
                && sameValue(mTargetLanguage, other.mTargetLanguage)
                && sameValue(mContextFingerprint, other.mContextFingerprint);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = mEngineSchemaVersion;
        result = 31 * result + valueHash(mVideoId);
        result = 31 * result + valueHash(mSourceTrackId);
        result = 31 * result + valueHash(mSourceCoverageFingerprint);
        result = 31 * result + valueHash(mProviderProfileId);
        result = 31 * result + valueHash(mProviderProtocol);
        result = 31 * result + valueHash(mBaseUrlIdentity);
        result = 31 * result + valueHash(mModelId);
        result = 31 * result + valueHash(mPromptProfileId);
        result = 31 * result + mPromptVersion;
        result = 31 * result + valueHash(mPromptContentHash);
        result = 31 * result + valueHash(mTargetLanguage);
        result = 31 * result + valueHash(mContextFingerprint);
        result = 31 * result + mSegmentationVersion;
        result = 31 * result + mBoundaryVersion;
        return result;
    }

    @Override
    public String toString() {
        return "TranslationCacheKey{video=" + mVideoId
                + ", track=" + mSourceTrackId
                + ", coverage=" + mSourceCoverageFingerprint
                + ", provider=" + mProviderProfileId
                + ", protocol=" + mProviderProtocol
                + ", baseUrl=" + mBaseUrlIdentity
                + ", model=" + mModelId
                + ", prompt=" + mPromptProfileId + "@" + mPromptVersion
                + "#" + mPromptContentHash
                + ", target=" + mTargetLanguage
                + ", context=" + mContextFingerprint
                + ", seg=" + mSegmentationVersion
                + ", boundary=" + mBoundaryVersion
                + ", engine=" + mEngineSchemaVersion + "}";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }

    /**
     * Fingerprint of the unit's normalized source coverage and text. The raw subtitle text is
     * deliberately reduced to a length plus hash so the key never carries content that could
     * reach a log.
     */
    private static String coverageFingerprint(TranslationUnit unit) {
        StringBuilder builder = new StringBuilder();
        List<SubtitleSegmentId> segmentIds = unit.getSegmentIds();

        builder.append(segmentIds.size()).append(':');

        for (int i = 0; i < segmentIds.size(); i++) {
            builder.append(segmentIds.get(i).getIndex()).append(',');
        }

        String text = unit.getSourceText();
        builder.append('|').append(text.length()).append(':').append(Integer.toHexString(text.hashCode()));

        return builder.toString();
    }
}
