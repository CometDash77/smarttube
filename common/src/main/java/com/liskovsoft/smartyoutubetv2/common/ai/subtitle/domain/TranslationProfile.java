package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

/**
 * The resolved runtime selection that drives one Translation Session: Provider Profile
 * (identity, wire protocol, and base URL identity), Model, Prompt Profile (with its content
 * version), and Target Language.
 *
 * <p>Immutable value object that never carries credential material. The base URL identity is
 * the endpoint address only; a value that looks like a URL with embedded user information is
 * rejected, so no key, token, or password can enter session or cache identity. Every field
 * participates in equality because each one is output-affecting.</p>
 */
public final class TranslationProfile {
    private final String mProviderProfileId;
    private final String mProviderProtocol;
    private final String mBaseUrlIdentity;
    private final String mModelId;
    private final String mPromptProfileId;
    private final int mPromptVersion;
    private final String mTargetLanguage;

    public TranslationProfile(String providerProfileId, String providerProtocol, String baseUrlIdentity,
                              String modelId, String promptProfileId, int promptVersion,
                              String targetLanguage) {
        mProviderProfileId = requireNonBlank(providerProfileId, "providerProfileId");
        mProviderProtocol = requireNonBlank(providerProtocol, "providerProtocol");
        mBaseUrlIdentity = requireCredentialFree(requireNonBlank(baseUrlIdentity, "baseUrlIdentity"));
        mModelId = requireNonBlank(modelId, "modelId");
        mPromptProfileId = requireNonBlank(promptProfileId, "promptProfileId");
        mTargetLanguage = requireNonBlank(targetLanguage, "targetLanguage");

        if (promptVersion < 1) {
            throw new IllegalArgumentException("promptVersion must be positive: " + promptVersion);
        }

        mPromptVersion = promptVersion;
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

    public String getTargetLanguage() {
        return mTargetLanguage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TranslationProfile)) {
            return false;
        }
        TranslationProfile other = (TranslationProfile) o;
        return mPromptVersion == other.mPromptVersion
                && sameValue(mProviderProfileId, other.mProviderProfileId)
                && sameValue(mProviderProtocol, other.mProviderProtocol)
                && sameValue(mBaseUrlIdentity, other.mBaseUrlIdentity)
                && sameValue(mModelId, other.mModelId)
                && sameValue(mPromptProfileId, other.mPromptProfileId)
                && sameValue(mTargetLanguage, other.mTargetLanguage);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mProviderProfileId);
        result = 31 * result + valueHash(mProviderProtocol);
        result = 31 * result + valueHash(mBaseUrlIdentity);
        result = 31 * result + valueHash(mModelId);
        result = 31 * result + valueHash(mPromptProfileId);
        result = 31 * result + mPromptVersion;
        result = 31 * result + valueHash(mTargetLanguage);
        return result;
    }

    @Override
    public String toString() {
        return "TranslationProfile{provider=" + mProviderProfileId
                + ", protocol=" + mProviderProtocol
                + ", baseUrl=" + mBaseUrlIdentity
                + ", model=" + mModelId
                + ", prompt=" + mPromptProfileId + "@" + mPromptVersion
                + ", target=" + mTargetLanguage + "}";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String requireCredentialFree(String baseUrlIdentity) {
        if (baseUrlIdentity.indexOf('@') >= 0) {
            throw new IllegalArgumentException(
                    "baseUrlIdentity must not contain embedded credentials");
        }
        return baseUrlIdentity;
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
