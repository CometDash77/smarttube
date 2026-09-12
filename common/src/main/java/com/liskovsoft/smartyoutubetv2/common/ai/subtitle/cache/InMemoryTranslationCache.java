package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Session-scoped in-memory {@link TranslationCache}. Not thread-safe by itself; the feature
 * owner (the cue bridge) performs every mutation under its own lock.
 */
public final class InMemoryTranslationCache implements TranslationCache {
    private final Map<TranslationCacheKey, TranslationResult> mEntries = new HashMap<>();

    @Override
    public TranslationResult get(TranslationCacheKey key) {
        return mEntries.get(key);
    }

    @Override
    public void put(TranslationCacheKey key, TranslationResult result) {
        if (key == null || result == null) {
            // Only accepted results are cacheable; a null result can never replace an entry.
            return;
        }

        mEntries.put(key, result);
    }

    @Override
    public void clear() {
        mEntries.clear();
    }

    /** Number of stored entries; a diagnostic and test seam only. */
    public int size() {
        return mEntries.size();
    }
}
