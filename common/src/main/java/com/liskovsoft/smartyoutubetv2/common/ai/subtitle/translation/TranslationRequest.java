package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

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
                && Objects.equals(mSourceText, other.mSourceText)
                && Objects.equals(mSourceLanguage, other.mSourceLanguage)
                && Objects.equals(mTargetLanguage, other.mTargetLanguage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mGeneration, mRequestId, mSourceText, mSourceLanguage, mTargetLanguage);
    }
}
