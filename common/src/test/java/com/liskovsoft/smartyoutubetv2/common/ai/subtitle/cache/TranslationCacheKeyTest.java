package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link TranslationCacheKey}: every output-affecting field from
 * architecture section 9 is isolated one at a time, and credential material can never enter
 * the key's identity or debug representation.
 */
public class TranslationCacheKeyTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "subtitle:en:asr-1", "en");
    private static final TranslationProfile PROFILE = new TranslationProfile(
            "profile-1", "openai-chat-completions", "https://api.example.com/v1",
            "gpt-4o-mini", "prompt-1", 2, "zh");
    private static final TranslationSessionId SESSION = new TranslationSessionId(
            "video-1", TRACK, PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION);

    private static TranslationUnit unit(String text) {
        return new TranslationUnit(Collections.singletonList(new SubtitleSegmentId(TRACK, 0)), text);
    }

    private static TranslationCacheKey key() {
        return TranslationCacheKey.from(SESSION, unit("Hello world"), "ctx-none", 1, 1);
    }

    @Test
    public void equalInputsProduceEqualKeysWithEqualHashes() {
        TranslationCacheKey first = key();
        TranslationCacheKey second = key();

        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void engineSchemaVersionIsIsolated() {
        TranslationSessionId other = new TranslationSessionId(
                "video-1", TRACK, PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION + 1);

        assertFalse(key().equals(TranslationCacheKey.from(other, unit("Hello world"), "ctx-none", 1, 1)));
    }

    @Test
    public void videoIdIsIsolated() {
        TranslationSessionId other = new TranslationSessionId("video-2", TRACK, PROFILE,
                TranslationSessionId.ENGINE_SCHEMA_VERSION);

        assertFalse(key().equals(TranslationCacheKey.from(other, unit("Hello world"), "ctx-none", 1, 1)));
    }

    @Test
    public void sourceTrackIsIsolated() {
        SourceTrackId otherTrack = new SourceTrackId("video-1", "subtitle:ja:asr-2", "ja");
        TranslationSessionId other = new TranslationSessionId("video-1", otherTrack, PROFILE,
                TranslationSessionId.ENGINE_SCHEMA_VERSION);

        assertFalse(key().equals(TranslationCacheKey.from(other, unit("Hello world"), "ctx-none", 1, 1)));
    }

    @Test
    public void sourceCoverageIsIsolated() {
        TranslationUnit otherCoverage = new TranslationUnit(
                Arrays.asList(new SubtitleSegmentId(TRACK, 0), new SubtitleSegmentId(TRACK, 1)),
                "Hello world");

        assertFalse(key().equals(TranslationCacheKey.from(SESSION, otherCoverage, "ctx-none", 1, 1)));
    }

    @Test
    public void sourceTextIsIsolated() {
        assertFalse(key().equals(TranslationCacheKey.from(SESSION, unit("Hello there"), "ctx-none", 1, 1)));
    }

    @Test
    public void providerProfileIdIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-2", "openai-chat-completions",
                "https://api.example.com/v1", "gpt-4o-mini", "prompt-1", 2, "zh");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void providerProtocolIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-1", "anthropic-messages",
                "https://api.example.com/v1", "gpt-4o-mini", "prompt-1", 2, "zh");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void baseUrlIdentityIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-1", "openai-chat-completions",
                "https://other.example.com/v1", "gpt-4o-mini", "prompt-1", 2, "zh");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void modelIdIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-1", "openai-chat-completions",
                "https://api.example.com/v1", "other-model", "prompt-1", 2, "zh");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void promptProfileIdIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-1", "openai-chat-completions",
                "https://api.example.com/v1", "gpt-4o-mini", "prompt-2", 2, "zh");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void promptVersionIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-1", "openai-chat-completions",
                "https://api.example.com/v1", "gpt-4o-mini", "prompt-1", 3, "zh");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void promptContentHashIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-1", "openai-chat-completions",
                "https://api.example.com/v1", "gpt-4o-mini", "prompt-1", 2,
                "different-content", "zh");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void targetLanguageIsIsolated() {
        TranslationProfile other = new TranslationProfile("profile-1", "openai-chat-completions",
                "https://api.example.com/v1", "gpt-4o-mini", "prompt-1", 2, "ja");

        assertFalse(key().equals(keyWithProfile(other)));
    }

    @Test
    public void contextFingerprintIsIsolated() {
        assertFalse(key().equals(TranslationCacheKey.from(SESSION, unit("Hello world"), "ctx-other", 1, 1)));
    }

    @Test
    public void segmentationVersionIsIsolated() {
        assertFalse(key().equals(TranslationCacheKey.from(SESSION, unit("Hello world"), "ctx-none", 2, 1)));
    }

    @Test
    public void boundaryVersionIsIsolated() {
        assertFalse(key().equals(TranslationCacheKey.from(SESSION, unit("Hello world"), "ctx-none", 1, 2)));
    }

    @Test
    public void sameTextOnDifferentTracksOrVideosCannotCollide() {
        SourceTrackId otherTrack = new SourceTrackId("video-1", "subtitle:ja:asr-2", "ja");
        TranslationSessionId otherSession = new TranslationSessionId("video-1", otherTrack, PROFILE,
                TranslationSessionId.ENGINE_SCHEMA_VERSION);

        assertFalse("equal text on another track must not alias",
                key().equals(TranslationCacheKey.from(otherSession, unit("Hello world"), "ctx-none", 1, 1)));
    }

    @Test
    public void nullSessionIdOrUnitIsRejected() {
        try {
            TranslationCacheKey.from(null, unit("Hello world"), "ctx-none", 1, 1);
            fail("null session id must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            TranslationCacheKey.from(SESSION, null, "ctx-none", 1, 1);
            fail("null unit must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void toStringExposesIdentityValuesForDebugging() {
        String text = key().toString();

        assertTrue("provider profile id must be visible for diagnostics", text.contains("profile-1"));
        assertTrue("model id must be visible for diagnostics", text.contains("gpt-4o-mini"));
        assertTrue("target language must be visible for diagnostics", text.contains("zh"));
        assertTrue("video id must be visible for diagnostics", text.contains("video-1"));
    }

    @Test
    public void toStringNeverCarriesRawSourceText() {
        String text = key().toString();

        assertFalse("raw subtitle text must never appear in debug output",
                text.contains("Hello world"));
    }

    @Test
    public void cacheKeyTypeHasNoCredentialBearingFields() {
        for (Field field : TranslationCacheKey.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();

            assertFalse("credential-bearing field: " + name, name.contains("secret"));
            assertFalse("credential-bearing field: " + name, name.contains("token"));
            assertFalse("credential-bearing field: " + name, name.contains("password"));
            assertFalse("credential-bearing field: " + name, name.contains("credential"));
            assertFalse("credential-bearing field: " + name, name.contains("apikey"));
        }
    }

    private static TranslationCacheKey keyWithProfile(TranslationProfile profile) {
        TranslationSessionId session = new TranslationSessionId("video-1", TRACK, profile,
                TranslationSessionId.ENGINE_SCHEMA_VERSION);

        return TranslationCacheKey.from(session, unit("Hello world"), "ctx-none", 1, 1);
    }
}
