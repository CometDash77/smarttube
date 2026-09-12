package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

/**
 * Normalized failure of one translation request: a {@link TranslationFailureCategory} plus a
 * short, non-sensitive human-readable message.
 *
 * <p>Message content is provided by the protocol adapter and must never contain credentials,
 * authorization headers, or full payloads.</p>
 */
public final class TranslationFailure {
    private final TranslationFailureCategory mCategory;
    private final String mMessage;

    public TranslationFailure(TranslationFailureCategory category, String message) {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }

        mCategory = category;
        mMessage = message != null ? message : "";
    }

    public TranslationFailureCategory getCategory() {
        return mCategory;
    }

    public String getMessage() {
        return mMessage;
    }

    /** Whether a bounded retry may help; derived from the category. */
    public boolean isRetryable() {
        return mCategory.isRetryable();
    }

    /** Terminal failures are not retried until configuration or credentials change. */
    public boolean isTerminal() {
        return !mCategory.isRetryable();
    }
}
