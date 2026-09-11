package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable identity and payload of one translation request.
 */
public final class TranslationRequest {
    private final long mGeneration;
    private final long mRequestId;
    private final String mSourceText;
    private final String mSourceLanguage;
    private final String mTargetLanguage;

    public TranslationRequest(long generation, long requestId, @NonNull String sourceText,
                              @Nullable String sourceLanguage, @Nullable String targetLanguage) {
        mGeneration = generation;
        mRequestId = requestId;
        mSourceText = sourceText;
        mSourceLanguage = sourceLanguage;
        mTargetLanguage = targetLanguage;
    }

    public long getGeneration() {
        return mGeneration;
    }

    public long getRequestId() {
        return mRequestId;
    }

    @NonNull
    public String getSourceText() {
        return mSourceText;
    }

    @Nullable
    public String getSourceLanguage() {
        return mSourceLanguage;
    }

    @Nullable
    public String getTargetLanguage() {
        return mTargetLanguage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TranslationRequest)) {
            return false;
        }
        TranslationRequest other = (TranslationRequest) o;
        return mGeneration == other.mGeneration
                && mRequestId == other.mRequestId
                && sameValue(mSourceText, other.mSourceText)
                && sameValue(mSourceLanguage, other.mSourceLanguage)
                && sameValue(mTargetLanguage, other.mTargetLanguage);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = (int) (mGeneration ^ (mGeneration >>> 32));
        result = 31 * result + (int) (mRequestId ^ (mRequestId >>> 32));
        result = 31 * result + valueHash(mSourceText);
        result = 31 * result + valueHash(mSourceLanguage);
        result = 31 * result + valueHash(mTargetLanguage);
        return result;
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
