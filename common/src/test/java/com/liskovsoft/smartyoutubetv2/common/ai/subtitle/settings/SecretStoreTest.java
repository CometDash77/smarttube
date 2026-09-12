package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM contract tests for secret masking and normalized failures.
 */
public class SecretStoreTest {
    @Test
    public void maskNeverReturnsTheFullSecret() {
        String secret = "synthetic-provider-credential-1234";

        String masked = SecretStore.Masking.mask(secret);

        assertNotEquals(secret, masked);
        assertFalse(masked.contains(secret));
        assertTrue(masked.startsWith("synt"));
        assertTrue(masked.endsWith("1234"));
    }

    @Test
    public void maskHandlesNullEmptyAndShortValuesWithoutRevealingThem() {
        assertEquals("", SecretStore.Masking.mask(null));
        assertEquals("", SecretStore.Masking.mask(""));
        assertEquals("••••••••", SecretStore.Masking.mask("short"));
    }

    @Test
    public void failuresUseAuthCategoryAndSafeMessages() {
        SecretStore.Failure failure = new SecretStore.Failure(
                SecretStore.FailureReason.INVALIDATED);

        assertEquals(SecretStore.FailureReason.INVALIDATED, failure.getReason());
        assertEquals(TranslationFailureCategory.AUTH, failure.getFailure().getCategory());
        assertFalse(failure.getMessage().contains("synthetic"));
        assertTrue(failure.getMessage().toLowerCase().contains("credential"));
        assertFalse(failure.toString().contains("synthetic"));
    }
}
