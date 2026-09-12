package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM contract tests for {@link TranslationFailure} and
 * {@link TranslationFailureCategory}: the normalized retryable/terminal split is the only
 * thing scheduling policy may branch on.
 */
public class TranslationFailureTest {
    @Test
    public void transientCategoriesAreRetryable() {
        assertTrue(new TranslationFailure(TranslationFailureCategory.TIMEOUT, "timed out").isRetryable());
        assertTrue(new TranslationFailure(TranslationFailureCategory.RATE_LIMITED, "slow down").isRetryable());
        assertTrue(new TranslationFailure(TranslationFailureCategory.SERVER, "boom").isRetryable());
        assertTrue(new TranslationFailure(TranslationFailureCategory.NETWORK, "unreachable").isRetryable());
    }

    @Test
    public void cancellationAuthProtocolAndInvalidOutputAreTerminal() {
        assertFalse(new TranslationFailure(TranslationFailureCategory.CANCELLED, "cancelled").isRetryable());
        assertFalse(new TranslationFailure(TranslationFailureCategory.AUTH, "bad key").isRetryable());
        assertFalse(new TranslationFailure(TranslationFailureCategory.PROTOCOL, "unexpected body").isRetryable());
        assertFalse(new TranslationFailure(TranslationFailureCategory.INVALID_OUTPUT, "unusable").isRetryable());
    }

    @Test
    public void terminalIsTheComplementOfRetryableForEveryCategory() {
        for (TranslationFailureCategory category : TranslationFailureCategory.values()) {
            TranslationFailure failure = new TranslationFailure(category, "message");

            assertEquals("retryable and terminal must be complements for " + category,
                    !failure.isRetryable(), failure.isTerminal());
        }
    }

    @Test
    public void gettersExposeCategoryAndMessage() {
        TranslationFailure failure = new TranslationFailure(
                TranslationFailureCategory.RATE_LIMITED, "quota window exceeded");

        assertEquals(TranslationFailureCategory.RATE_LIMITED, failure.getCategory());
        assertEquals("quota window exceeded", failure.getMessage());
    }
}
