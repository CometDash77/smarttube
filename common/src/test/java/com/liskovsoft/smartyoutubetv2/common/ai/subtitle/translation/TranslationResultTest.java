package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link TranslationResult}: final/partial state, mapped source
 * coverage, and value equality.
 */
public class TranslationResultTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "track-1", "en");
    private static final TranslationProfile PROFILE = new TranslationProfile(
            "profile-1", "openai-chat", "https://api.example.com", "model-1", "prompt-1", 1, "zh");
    private static final TranslationSessionId SESSION =
            new TranslationSessionId("video-1", TRACK, PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION);
    private static final TranslationUnit UNIT = new TranslationUnit(
            Collections.singletonList(new SubtitleSegmentId(TRACK, 0)), "Hello");

    @Test
    public void finalResultCarriesSessionRequestCoverageAndText() {
        TranslationResult result = TranslationResult.finalResult(SESSION, 7, UNIT, "[ZH] Hello");

        assertEquals(SESSION, result.getSessionId());
        assertEquals(7, result.getRequestId());
        assertEquals(UNIT.getSegmentIds(), result.getSegmentIds());
        assertEquals("[ZH] Hello", result.getTranslatedText());
        assertTrue(result.isFinal());
        assertFalse(result.isPartial());
    }

    @Test
    public void partialResultIsNotFinal() {
        TranslationResult partial = TranslationResult.partialResult(SESSION, 7, UNIT, "[ZH] Hel");

        assertFalse(partial.isFinal());
        assertTrue(partial.isPartial());
    }

    @Test
    public void finalAndPartialWithTheSameIdentityAreNotEqual() {
        assertFalse(TranslationResult.finalResult(SESSION, 7, UNIT, "text")
                .equals(TranslationResult.partialResult(SESSION, 7, UNIT, "text")));
    }

    @Test
    public void equalityCoversSessionRequestCoverageAndText() {
        TranslationResult base = TranslationResult.finalResult(SESSION, 7, UNIT, "text");

        assertEquals(base, TranslationResult.finalResult(SESSION, 7, UNIT, "text"));
        assertFalse(base.equals(TranslationResult.finalResult(SESSION, 8, UNIT, "text")));
        assertFalse(base.equals(TranslationResult.finalResult(SESSION, 7, UNIT, "other")));
    }

    @Test
    public void coverageIsCopiedAndUnmodifiable() {
        TranslationResult result = TranslationResult.finalResult(SESSION, 1, UNIT, "text");

        try {
            result.getSegmentIds().add(new SubtitleSegmentId(TRACK, 5));
            fail("returned coverage must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void blankFinalTextIsRejected() {
        for (String blank : new String[] {null, "", "   "}) {
            try {
                TranslationResult.finalResult(SESSION, 1, UNIT, blank);
                fail("blank final text must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void nullUnitOrSessionIsRejected() {
        try {
            TranslationResult.finalResult(null, 1, UNIT, "text");
            fail("null session must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            TranslationResult.finalResult(SESSION, 1, null, "text");
            fail("null unit must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
