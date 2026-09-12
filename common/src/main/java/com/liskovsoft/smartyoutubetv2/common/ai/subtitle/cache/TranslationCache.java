package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

/**
 * In-memory cache of accepted translation results, keyed by the complete output identity
 * captured in {@link TranslationCacheKey}.
 *
 * <p>M03 scope: the cache is in-memory and bounded to the active Translation Session; its
 * owner clears it whenever session identity changes. Only accepted final results are stored;
 * failures and status sentinels are never cached as translation text.</p>
 */
public interface TranslationCache {
    TranslationResult get(TranslationCacheKey key);

    void put(TranslationCacheKey key, TranslationResult result);

    void clear();
}
