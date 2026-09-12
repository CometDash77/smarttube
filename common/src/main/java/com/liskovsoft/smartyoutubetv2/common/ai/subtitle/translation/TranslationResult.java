package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of one translation request, mapped back to the source coverage it answers.
 *
 * <p>A result is either final (the accepted translation for its unit and request) or partial
 * (a draft that may still change and must never be stored as final text). Callers must
 * validate the session identity, request id, and segment coverage before the text is applied
 * anywhere.</p>
 */
public final class TranslationResult {
    private final TranslationSessionId mSessionId;
    private final long mRequestId;
    private final List<SubtitleSegmentId> mSegmentIds;
    private final String mTranslatedText;
    private final boolean mFinal;

    private TranslationResult(TranslationSessionId sessionId, long requestId,
                              List<SubtitleSegmentId> segmentIds, String translatedText,
                              boolean finalResult) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }

        mSessionId = sessionId;
        mRequestId = requestId;
        mSegmentIds = segmentIds;
        mTranslatedText = translatedText;
        mFinal = finalResult;
    }

    /** Final, accepted output for the given unit; replaces any draft for the same unit. */
    public static TranslationResult finalResult(TranslationSessionId sessionId, long requestId,
                                                TranslationUnit unit, String translatedText) {
        return new TranslationResult(sessionId, requestId, copyOf(unit), requireFinalText(translatedText), true);
    }

    /** Draft output that may still change; never stored as final text. */
    public static TranslationResult partialResult(TranslationSessionId sessionId, long requestId,
                                                  TranslationUnit unit, String translatedText) {
        if (translatedText == null) {
            throw new IllegalArgumentException("translatedText must not be null");
        }

        return new TranslationResult(sessionId, requestId, copyOf(unit), translatedText, false);
    }

    public TranslationSessionId getSessionId() {
        return mSessionId;
    }

    public long getRequestId() {
        return mRequestId;
    }

    /** Source coverage this result answers; matches the request's unit segment ids. */
    public List<SubtitleSegmentId> getSegmentIds() {
        return mSegmentIds;
    }

    @NonNull
    public String getTranslatedText() {
        return mTranslatedText;
    }

    public boolean isFinal() {
        return mFinal;
    }

    public boolean isPartial() {
        return !mFinal;
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
        return mRequestId == other.mRequestId
                && mFinal == other.mFinal
                && sameValue(mSessionId, other.mSessionId)
                && sameValue(mSegmentIds, other.mSegmentIds)
                && sameValue(mTranslatedText, other.mTranslatedText);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mSessionId);
        result = 31 * result + (int) (mRequestId ^ (mRequestId >>> 32));
        result = 31 * result + valueHash(mSegmentIds);
        result = 31 * result + valueHash(mTranslatedText);
        result = 31 * result + (mFinal ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TranslationResult{requestId=" + mRequestId
                + ", segments=" + mSegmentIds
                + ", text=" + mTranslatedText
                + (mFinal ? ", final" : ", partial") + "}";
    }

    private static List<SubtitleSegmentId> copyOf(TranslationUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }

        return Collections.unmodifiableList(new ArrayList<>(unit.getSegmentIds()));
    }

    private static String requireFinalText(String translatedText) {
        if (translatedText == null || translatedText.trim().isEmpty()) {
            throw new IllegalArgumentException("final translatedText must not be blank");
        }

        return translatedText;
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
