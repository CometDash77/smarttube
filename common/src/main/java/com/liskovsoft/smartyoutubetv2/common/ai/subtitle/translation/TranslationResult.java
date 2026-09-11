package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import androidx.annotation.NonNull;

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
                && sameValue(mTranslatedText, other.mTranslatedText);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = (int) (mGeneration ^ (mGeneration >>> 32));
        result = 31 * result + (int) (mRequestId ^ (mRequestId >>> 32));
        result = 31 * result + valueHash(mTranslatedText);
        return result;
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
