package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-scoped in-memory {@link TranslationCache} with a hard bound on both entry count and
 * stored text size.
 *
 * <p>A plain map is enough for correctness but not for a feature that runs for the length of a
 * long video: every translated cue would stay reachable forever. This cache is a standard
 * access-ordered {@link LinkedHashMap}, so the least recently used entry is evicted first, and
 * it stops at whichever limit is reached first. Entry sizes are measured when a value is put
 * and the total is corrected when a value is replaced or removed.</p>
 *
 * <p>Not thread-safe by itself; the feature owner (the cue bridge) performs every mutation
 * under its own lock.</p>
 */
public final class InMemoryTranslationCache implements TranslationCache {
    /** Entry-count limit; an equivalent of roughly eight minutes of dense subtitles. */
    public static final int DEFAULT_MAX_ENTRIES = 512;
    /** Stored-text limit in UTF-8 bytes. */
    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final Map<TranslationCacheKey, Entry> mEntries =
            new LinkedHashMap<>(64, 0.75f, true);
    private final int mMaxEntries;
    private final long mMaxBytes;
    private long mBytes;

    public InMemoryTranslationCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_BYTES);
    }

    public InMemoryTranslationCache(int maxEntries, long maxBytes) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");

        mMaxEntries = maxEntries;
        mMaxBytes = maxBytes;
    }

    @Override
    public TranslationResult get(TranslationCacheKey key) {
        Entry entry = key != null ? mEntries.get(key) : null;
        return entry != null ? entry.mResult : null;
    }

    @Override
    public boolean contains(TranslationCacheKey key) {
        return key != null && mEntries.containsKey(key);
    }

    @Override
    public void put(TranslationCacheKey key, TranslationResult result) {
        if (key == null || result == null) {
            // Only accepted results are cacheable; a null result can never replace an entry.
            return;
        }

        Entry added = new Entry(result, utf8Length(result.getTranslatedText()));
        Entry previous = mEntries.put(key, added);

        if (previous != null) mBytes -= previous.mBytes;
        mBytes += added.mBytes;

        evict();

        // A single value larger than the whole budget is not worth keeping.
        if (mBytes > mMaxBytes && mEntries.size() == 1) {
            mEntries.clear();
            mBytes = 0;
        }
    }

    @Override
    public void clear() {
        mEntries.clear();
        mBytes = 0;
    }

    /** Number of stored entries; a diagnostic and test seam only. */
    public int size() {
        return mEntries.size();
    }

    /** Stored text size in UTF-8 bytes; a diagnostic and test seam only. */
    public long byteSize() {
        return mBytes;
    }

    private void evict() {
        if (mEntries.size() <= mMaxEntries && mBytes <= mMaxBytes) return;

        long projectedBytes = mBytes;
        List<TranslationCacheKey> expired = new ArrayList<>();

        // Collect first, remove after: iterating in access order and reading each value during
        // the walk would reorder the map under the iterator.
        for (Map.Entry<TranslationCacheKey, Entry> entry : mEntries.entrySet()) {
            if (mEntries.size() - expired.size() <= mMaxEntries && projectedBytes <= mMaxBytes) break;

            expired.add(entry.getKey());
            projectedBytes -= entry.getValue().mBytes;
        }

        for (TranslationCacheKey key : expired) {
            Entry removed = mEntries.remove(key);
            if (removed != null) mBytes -= removed.mBytes;
        }
    }

    private static long utf8Length(String value) {
        return value != null ? value.getBytes(UTF_8).length : 0;
    }

    private static final class Entry {
        private final TranslationResult mResult;
        private final long mBytes;

        private Entry(TranslationResult result, long bytes) {
            mResult = result;
            mBytes = bytes;
        }
    }
}
