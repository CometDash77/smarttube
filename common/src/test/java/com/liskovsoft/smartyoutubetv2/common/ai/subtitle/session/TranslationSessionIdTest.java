package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link TranslationSessionId}.
 */
public class TranslationSessionIdTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "subtitle:en:asr-1", "en");
    private static final SourceTrackId OTHER_TRACK = new SourceTrackId("video-1", "subtitle:ja:asr-2", "ja");
    private static final TranslationProfile PROFILE =
            new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 1, "zh");
    private static final TranslationProfile OTHER_PROFILE =
            new TranslationProfile("profile-2", "gpt-4o-mini", "prompt-1", 1, "zh");

    @Test
    public void gettersExposeExactConstructorValues() {
        TranslationSessionId id = new TranslationSessionId("video-1", TRACK, PROFILE, 3);

        assertEquals("video-1", id.getVideoId());
        assertEquals(TRACK, id.getSourceTrackId());
        assertEquals(PROFILE, id.getProfile());
        assertEquals(3, id.getEngineSchemaVersion());
    }

    @Test
    public void equalFieldValuesProduceEqualObjectsWithEqualHashes() {
        TranslationSessionId first = new TranslationSessionId("video-1", TRACK, PROFILE, 1);
        TranslationSessionId second = new TranslationSessionId("video-1", TRACK, PROFILE, 1);

        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void identityChangeAcrossVideoTrackProfileOrVersionIsNotEqual() {
        TranslationSessionId base = new TranslationSessionId("video-1", TRACK, PROFILE, 1);

        assertFalse("different video", base.equals(new TranslationSessionId("video-2", TRACK, PROFILE, 1)));
        assertFalse("different source track", base.equals(new TranslationSessionId("video-1", OTHER_TRACK, PROFILE, 1)));
        assertFalse("different provider profile", base.equals(new TranslationSessionId("video-1", TRACK, OTHER_PROFILE, 1)));
        assertFalse("different engine schema version", base.equals(new TranslationSessionId("video-1", TRACK, PROFILE, 2)));
    }

    @Test
    public void modelPromptAndLanguageIdentityChangesAreNotEqual() {
        TranslationSessionId base = new TranslationSessionId("video-1", TRACK, PROFILE, 1);

        TranslationProfile otherModel = new TranslationProfile("profile-1", "other-model", "prompt-1", 1, "zh");
        TranslationProfile otherPrompt = new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-2", 1, "zh");
        TranslationProfile otherPromptVersion = new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 2, "zh");
        TranslationProfile otherLanguage = new TranslationProfile("profile-1", "gpt-4o-mini", "prompt-1", 1, "ja");

        assertFalse("different model", base.equals(new TranslationSessionId("video-1", TRACK, otherModel, 1)));
        assertFalse("different prompt profile", base.equals(new TranslationSessionId("video-1", TRACK, otherPrompt, 1)));
        assertFalse("different prompt version", base.equals(new TranslationSessionId("video-1", TRACK, otherPromptVersion, 1)));
        assertFalse("different target language", base.equals(new TranslationSessionId("video-1", TRACK, otherLanguage, 1)));
    }

    @Test
    public void blankVideoIdIsRejected() {
        for (String blank : new String[] {null, "", "   "}) {
            try {
                new TranslationSessionId(blank, TRACK, PROFILE, 1);
                fail("blank video id must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void nullTrackOrProfileIsRejected() {
        try {
            new TranslationSessionId("video-1", null, PROFILE, 1);
            fail("null source track must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new TranslationSessionId("video-1", TRACK, null, 1);
            fail("null profile must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void nonPositiveEngineSchemaVersionIsRejected() {
        for (int version : new int[] {0, -1}) {
            try {
                new TranslationSessionId("video-1", TRACK, PROFILE, version);
                fail("non-positive engine schema version must be rejected: " + version);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}
