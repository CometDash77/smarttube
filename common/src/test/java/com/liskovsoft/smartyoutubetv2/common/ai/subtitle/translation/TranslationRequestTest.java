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
 * Pure-JVM contract tests for {@link TranslationRequest}: identity is carried explicitly and
 * derived accessors can never disagree with the session.
 */
public class TranslationRequestTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "track-1", "ja");
    private static final TranslationProfile PROFILE = new TranslationProfile(
            "profile-1", "openai-chat", "https://api.example.com", "model-1", "prompt-1", 1, "zh");
    private static final TranslationSessionId SESSION =
            new TranslationSessionId("video-1", TRACK, PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION);
    private static final TranslationUnit UNIT = new TranslationUnit(
            Collections.singletonList(new SubtitleSegmentId(TRACK, 0)), "こんにちは");
    private static final String PROMPT = "Translate {{source_text}} into {{target_language}}.";

    @Test
    public void derivedAccessorsComeFromSessionAndUnit() {
        TranslationRequest request = new TranslationRequest(SESSION, 3, UNIT, PROMPT);

        assertEquals(SESSION, request.getSessionId());
        assertEquals(3, request.getRequestId());
        assertEquals(UNIT, request.getUnit());
        assertEquals("こんにちは", request.getSourceText());
        assertEquals("ja", request.getSourceLanguage());
        assertEquals("zh", request.getTargetLanguage());
        assertEquals(PROMPT, request.getRenderedPrompt());
    }

    @Test
    public void blankRenderedPromptIsRejected() {
        for (String prompt : new String[] {null, "", "   "}) {
            try {
                new TranslationRequest(SESSION, 3, UNIT, prompt);
                fail("a blank rendered prompt must be rejected");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void equalityCoversTheRenderedPrompt() {
        assertFalse(new TranslationRequest(SESSION, 3, UNIT, PROMPT)
                .equals(new TranslationRequest(SESSION, 3, UNIT, PROMPT + " ")));
    }

    @Test
    public void theRenderedPromptIsNotPrintedInFull() {
        String rendered = "SECRET-SOURCE-TEXT-MARKER";

        assertFalse("a request may reach a log, so its prompt is reduced to a length",
                new TranslationRequest(SESSION, 3, UNIT, rendered).toString()
                        .contains(rendered));
    }

    @Test
    public void equalityCoversSessionRequestIdAndUnit() {
        TranslationRequest base = new TranslationRequest(SESSION, 3, UNIT, PROMPT);

        assertTrue(base.equals(new TranslationRequest(SESSION, 3, UNIT, PROMPT)));
        assertFalse(base.equals(new TranslationRequest(SESSION, 4, UNIT, PROMPT)));
        assertFalse(base.equals(new TranslationRequest(
                new TranslationSessionId("video-2", TRACK, PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION),
                3, UNIT, PROMPT)));
    }

    @Test
    public void nullSessionOrUnitIsRejected() {
        try {
            new TranslationRequest(null, 3, UNIT, PROMPT);
            fail("null session must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new TranslationRequest(SESSION, 3, null, PROMPT);
            fail("null unit must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void nonPositiveRequestIdIsRejected() {
        for (long requestId : new long[] {0, -1}) {
            try {
                new TranslationRequest(SESSION, requestId, UNIT, PROMPT);
                fail("non-positive request id must be rejected: " + requestId);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}
