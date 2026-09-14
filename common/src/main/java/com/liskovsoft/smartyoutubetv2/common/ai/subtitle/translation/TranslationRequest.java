package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;

/**
 * Immutable identity and payload of one translation request.
 *
 * <p>The request carries the Translation Session identity, the caller-owned request id, the
 * {@link TranslationUnit} to translate, and the already-rendered instruction the selected
 * Prompt Profile produced for that unit. Source text and the source/target languages are
 * derived from those identities instead of being copied, so a request can never disagree
 * with its session. The rendered prompt is frozen at submit time so a later settings change
 * cannot alter a request that is already in flight.</p>
 */
public final class TranslationRequest {
    private final TranslationSessionId mSessionId;
    private final long mRequestId;
    private final TranslationUnit mUnit;
    private final String mRenderedPrompt;

    public TranslationRequest(TranslationSessionId sessionId, long requestId,
                              @NonNull TranslationUnit unit, @NonNull String renderedPrompt) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (requestId < 1) {
            throw new IllegalArgumentException("requestId must be positive: " + requestId);
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }
        if (renderedPrompt == null || renderedPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("renderedPrompt must not be blank");
        }

        mSessionId = sessionId;
        mRequestId = requestId;
        mUnit = unit;
        mRenderedPrompt = renderedPrompt;
    }

    public TranslationSessionId getSessionId() {
        return mSessionId;
    }

    public long getRequestId() {
        return mRequestId;
    }

    @NonNull
    public TranslationUnit getUnit() {
        return mUnit;
    }

    /** Instruction rendered from the session's Prompt Profile; never credential material. */
    @NonNull
    public String getRenderedPrompt() {
        return mRenderedPrompt;
    }

    /** Source text of the translated unit. */
    @NonNull
    public String getSourceText() {
        return mUnit.getSourceText();
    }

    /** Language of the session's Source Track. */
    @NonNull
    public String getSourceLanguage() {
        return mSessionId.getSourceTrackId().getLanguage();
    }

    /** Target language resolved in the session's Translation Profile. */
    @NonNull
    public String getTargetLanguage() {
        return mSessionId.getProfile().getTargetLanguage();
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
        return mRequestId == other.mRequestId
                && sameValue(mSessionId, other.mSessionId)
                && sameValue(mUnit, other.mUnit)
                && sameValue(mRenderedPrompt, other.mRenderedPrompt);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+ and the app's
        // minimum SDK is 17.
        int result = valueHash(mSessionId);
        result = 31 * result + (int) (mRequestId ^ (mRequestId >>> 32));
        result = 31 * result + valueHash(mUnit);
        result = 31 * result + valueHash(mRenderedPrompt);
        return result;
    }

    @Override
    public String toString() {
        // The rendered prompt is deliberately reduced to its length: it can embed subtitle
        // text, and this string may reach a log.
        return "TranslationRequest{requestId=" + mRequestId
                + ", unit=" + mUnit
                + ", promptLength=" + mRenderedPrompt.length()
                + ", session=" + mSessionId + "}";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}
