package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Immutable result of one translation request. Generation and request id must be validated by
 * the caller before the text is applied anywhere.
 */
public final class TranslationResult {
    private final long mGeneration;
    private final long mRequestId;
    private final String mTranslatedText;

    public TranslationResult(long generation, long requestId, @NonNull String translatedText) {
        mGeneration = generation;
        mRequestId = requestId;
        mTranslatedText = translatedText;
    }

    public long getGeneration() {
        return mGeneration;
    }

    public long getRequestId() {
        return mRequestId;
    }

    @NonNull
    public String getTranslatedText() {
        return mTranslatedText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TranslationResult)) {
            return false;
        }
        TranslationResult other = (TranslationResult) o;
        return mGeneration == other.mGeneration
                && mRequestId == other.mRequestId
                && Objects.equals(mTranslatedText, other.mTranslatedText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mGeneration, mRequestId, mTranslatedText);
    }
}
