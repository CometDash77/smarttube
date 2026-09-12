package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

/**
 * Normalized, provider-neutral failure category for one translation request.
 *
 * <p>The category is the only thing scheduling and retry policy may branch on; provider
 * brands, HTTP status text, and wire details stay behind the protocol adapters.</p>
 */
public enum TranslationFailureCategory {
    /** The caller cancelled the request; never retried and never rendered as an error. */
    CANCELLED,
    /** The request exceeded its deadline. */
    TIMEOUT,
    /** The provider asked the caller to slow down. */
    RATE_LIMITED,
    /** Missing, invalid, or rejected credentials. */
    AUTH,
    /** The provider reported an internal/server-side failure. */
    SERVER,
    /** The wire response did not follow the expected protocol shape. */
    PROTOCOL,
    /** The provider answered successfully but the output is unusable. */
    INVALID_OUTPUT,
    /** The request could not reach the provider. */
    NETWORK;

    /**
     * Whether a bounded retry may help. Cancellation, credential, protocol, and output
     * problems are terminal until a human or configuration change intervenes.
     */
    public boolean isRetryable() {
        switch (this) {
            case TIMEOUT:
            case RATE_LIMITED:
            case SERVER:
            case NETWORK:
                return true;
            default:
                return false;
        }
    }
}
