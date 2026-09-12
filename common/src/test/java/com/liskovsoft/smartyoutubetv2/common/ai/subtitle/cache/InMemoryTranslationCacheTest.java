package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Pure-JVM contract tests for {@link InMemoryTranslationCache}.
 */
public class InMemoryTranslationCacheTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "subtitle:en:asr-1", "en");
    private static final TranslationProfile PROFILE = new TranslationProfile(
            "profile-1", "openai-chat-completions", "https://api.example.com/v1",
            "gpt-4o-mini", "prompt-1", 1, "zh");
    private static final TranslationSessionId SESSION = new TranslationSessionId(
            "video-1", TRACK, PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION);

    private InMemoryTranslationCache mCache;

    @Before
    public void setUp() {
        mCache = new InMemoryTranslationCache();
    }

    private static TranslationCacheKey keyFor(String text) {
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(TRACK, 0)), text);

        return TranslationCacheKey.from(SESSION, unit, "ctx-none", 1, 1);
    }

    @Test
    public void putAndGetRoundTrip() {
        TranslationCacheKey key = keyFor("Hello");
        TranslationResult result = new TranslationResult(1, 1, "[ZH] Hello");

        mCache.put(key, result);

        assertSame(result, mCache.get(key));
        assertEquals(1, mCache.size());
    }

    @Test
    public void missingKeyReturnsNull() {
        assertNull(mCache.get(keyFor("Hello")));
        assertEquals(0, mCache.size());
    }

    @Test
    public void putOverwritesTheExistingEntryForTheSameKey() {
        TranslationCacheKey key = keyFor("Hello");

        mCache.put(key, new TranslationResult(1, 1, "[ZH] Hello"));
        TranslationResult second = new TranslationResult(1, 2, "[ZH] Hello, again");
        mCache.put(key, second);

        assertSame(second, mCache.get(key));
        assertEquals(1, mCache.size());
    }

    @Test
    public void differentKeysDoNotAlias() {
        mCache.put(keyFor("Hello"), new TranslationResult(1, 1, "[ZH] Hello"));
        mCache.put(keyFor("World"), new TranslationResult(1, 2, "[ZH] World"));

        assertEquals("[ZH] Hello", mCache.get(keyFor("Hello")).getTranslatedText());
        assertEquals("[ZH] World", mCache.get(keyFor("World")).getTranslatedText());
        assertEquals(2, mCache.size());
    }

    @Test
    public void clearRemovesEveryEntry() {
        mCache.put(keyFor("Hello"), new TranslationResult(1, 1, "[ZH] Hello"));
        mCache.put(keyFor("World"), new TranslationResult(1, 2, "[ZH] World"));

        mCache.clear();

        assertEquals(0, mCache.size());
        assertNull(mCache.get(keyFor("Hello")));
        assertNull(mCache.get(keyFor("World")));
    }
}
