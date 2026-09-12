package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link TranslationProfile}.
 */
public class TranslationProfileTest {
    private static TranslationProfile profile() {
        return new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 2, "zh");
    }

    @Test
    public void gettersExposeExactConstructorValues() {
        TranslationProfile profile = profile();

        assertEquals("profile-1", profile.getProviderProfileId());
        assertEquals("gpt-4o-mini", profile.getModelId());
        assertEquals("prompt-1", profile.getPromptProfileId());
        assertEquals(2, profile.getPromptVersion());
        assertEquals("zh", profile.getTargetLanguage());
    }

    @Test
    public void equalValuesProduceEqualObjectsWithEqualHashes() {
        assertTrue(profile().equals(profile()));
        assertEquals(profile().hashCode(), profile().hashCode());
    }

    @Test
    public void everyFieldParticipatesInEquality() {
        TranslationProfile base = profile();

        assertFalse(base.equals(new TranslationProfile("profile-2", "gpt-4o-mini", "prompt-1", 2, "zh")));
        assertFalse(base.equals(new TranslationProfile("profile-1", "other-model", "prompt-1", 2, "zh")));
        assertFalse(base.equals(new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-2", 2, "zh")));
        assertFalse(base.equals(new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 3, "zh")));
        assertFalse(base.equals(new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 2, "ja")));
    }

    @Test
    public void blankProviderProfileIdIsRejected() {
        for (String blank : new String[] {null, "", "   "}) {
            try {
                new TranslationProfile(blank, "gpt-4o-mini", "prompt-1", 1, "zh");
                fail("blank provider profile id must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void blankModelIdIsRejected() {
        for (String blank : new String[] {null, "", "  "}) {
            try {
                new TranslationProfile("profile-1", blank, "prompt-1", 1, "zh");
                fail("blank model id must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void blankPromptProfileIdIsRejected() {
        for (String blank : new String[] {null, "", "  "}) {
            try {
                new TranslationProfile("profile-1", "gpt-4o-mini", blank, 1, "zh");
                fail("blank prompt profile id must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void blankTargetLanguageIsRejected() {
        for (String blank : new String[] {null, "", "  "}) {
            try {
                new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 1, blank);
                fail("blank target language must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void nonPositivePromptVersionIsRejected() {
        try {
            new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 0, "zh");
            fail("prompt version 0 must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", -1, "zh");
            fail("negative prompt version must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
