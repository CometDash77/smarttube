package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.TranslationCacheKey;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PromptRepositoryTest {
    /**
     * The version this repository shipped before the indexed prompt was corrected. Fixed
     * literals, deliberately not read from {@link BuiltInSubtitlePrompts}: a migration test whose
     * "old" value came from the current code would pass whether or not the migration ran.
     */
    private static final String OLD_BASELINE_CONTENT =
            "Translate this {{source_language}} subtitle into {{target_language}}: "
                    + "{{source_text}} Reply with the translation alone.";
    private static final int OLD_BASELINE_VERSION = 1;
    private static final int OLD_INDEXED_VERSION = 1;

    @Test
    public void firstLoadAddsStableBuiltInsAndSelection() {
        MemoryStore store = new MemoryStore();
        PromptRepository repository = new PromptRepository(store);
        PromptState state = repository.load();
        assertEquals(BuiltInSubtitlePrompts.BASELINE_ID, state.getSelectedProfileId());
        assertEquals(BuiltInSubtitlePrompts.BASELINE_ID, state.getDefaultProfileId());
        assertEquals(2, state.getProfiles().size());
        assertEquals(1, store.writeCount);
        assertEquals(state, new PromptRepository(store).load());
    }

    @Test
    public void customCrudCopiesButCannotMutateBuiltInsAndVersionsChange() {
        PromptRepository repository = new PromptRepository(new MemoryStore());
        PromptProfile copy = repository.copyToCustom(BuiltInSubtitlePrompts.BASELINE_ID, "My prompt");
        assertFalse(copy.isBuiltIn());
        PromptProfile updated = repository.update(copy.getId(), "Updated", "{{source_text}}");
        assertEquals(2, updated.getVersion());
        try {
            repository.update(BuiltInSubtitlePrompts.BASELINE_ID, "No", "{{source_text}}");
            fail("built-in update must be rejected");
        } catch (IllegalArgumentException expected) { }
        try {
            repository.update(copy.getId(), "Invalid", "{{unknown}}");
            fail("invalid prompt must be rejected");
        } catch (IllegalArgumentException expected) { }
        assertTrue(repository.delete(copy.getId()));
        assertFalse(repository.delete(copy.getId()));
    }

    @Test
    public void corruptPayloadRepairsAndFuturePayloadIsPreserved() {
        MemoryStore corrupt = new MemoryStore("{broken");
        assertEquals(2, new PromptRepository(corrupt).load().getProfiles().size());
        assertTrue(corrupt.value.startsWith("{\"schemaVersion\":1"));

        String future = "{\"schemaVersion\":99,\"profiles\":[]}";
        try {
            new PromptRepository(new MemoryStore(future)).load();
            fail("future schema must be rejected");
        } catch (PromptMigration.FutureSchemaException expected) { }
    }

    /**
     * The audit's regression for the Prompt migration, kept as a permanent test. The stored
     * baseline is the version this repository used to ship and the stored indexed profile is the
     * one with its old text, both written as fixed values.
     */
    @Test
    public void anExistingBaselineMigratesOnceAndCustomPromptsSurvive() {
        PromptProfile storedBaseline = new PromptProfile(BuiltInSubtitlePrompts.BASELINE_ID,
                "Subtitle translation", OLD_BASELINE_CONTENT, OLD_BASELINE_VERSION, true);
        PromptProfile storedIndexed = new PromptProfile(BuiltInSubtitlePrompts.INDEXED_ID,
                "Indexed subtitle translation",
                "Translate subtitle unit {{unit_index}} from {{source_language}} to "
                        + "{{target_language}}. Return the translation with its unit index. "
                        + "Text: {{source_text}}", OLD_INDEXED_VERSION, true);
        PromptProfile custom = new PromptProfile("custom.review", "My own",
                "MY {{source_text}}", 7, false);

        MemoryStore store = new MemoryStore(new PromptSerializer().serialize(new PromptState(
                Arrays.asList(storedBaseline, storedIndexed, custom),
                custom.getId(), custom.getId())));

        PromptState migrated = new PromptRepository(store).load();

        PromptProfile baseline = migrated.getProfile(BuiltInSubtitlePrompts.BASELINE_ID);
        assertTrue("the built-in must be replaced by the shipped version, or a later text "
                        + "change would never reach the user",
                baseline.getVersion() > OLD_BASELINE_VERSION);
        assertTrue(baseline.getContent().contains("{{context}}"));
        assertTrue("the indexed prompt must migrate too",
                migrated.getProfile(BuiltInSubtitlePrompts.INDEXED_ID).getVersion()
                        > OLD_INDEXED_VERSION);

        assertEquals("a custom prompt must survive untouched", custom,
                migrated.getProfile(custom.getId()));
        assertEquals("and so must the user's selection", custom.getId(),
                migrated.getSelectedProfileId());

        // Exactly one write: the migration runs once and a reload changes nothing.
        assertEquals(1, store.writeCount);
        assertEquals(migrated, new PromptRepository(store).load());
        assertEquals(1, store.writeCount);
    }

    /**
     * The corrected prompt changes what a request sends, so the identity a cached translation was
     * stored under has to change with it. Otherwise the old translation would be served for the
     * new instruction. Both shipped built-ins are checked, because the migration replaces both.
     */
    @Test
    public void theMigratedBuiltInPromptsChangeTheCacheIdentity() {
        List<PromptProfile> shipped = BuiltInSubtitlePrompts.all();

        assertNotEquals("the corrected indexed prompt must not hit the old cache entry",
                keyFor(profile(BuiltInSubtitlePrompts.INDEXED_ID, OLD_INDEXED_VERSION)),
                keyFor(shippedProfile(shipped, BuiltInSubtitlePrompts.INDEXED_ID)));

        assertNotEquals("and neither must the baseline",
                keyFor(profile(BuiltInSubtitlePrompts.BASELINE_ID, OLD_BASELINE_VERSION)),
                keyFor(shippedProfile(shipped, BuiltInSubtitlePrompts.BASELINE_ID)));
    }

    private static TranslationProfile shippedProfile(List<PromptProfile> shipped, String id) {
        for (PromptProfile prompt : shipped) {
            if (prompt.getId().equals(id)) return profile(prompt.getId(), prompt.getVersion());
        }

        throw new IllegalArgumentException("no shipped prompt with id " + id);
    }

    private static TranslationProfile profile(String promptId, int promptVersion) {
        return new TranslationProfile("profile-1", "openai-chat-completions",
                "https://api.example.com/v1", "model-1", promptId, promptVersion, "zh");
    }

    private static TranslationCacheKey keyFor(TranslationProfile profile) {
        SourceTrackId track = new SourceTrackId("video-1", "subtitle:en:1", "en");
        TranslationSessionId session = new TranslationSessionId("video-1", track, profile,
                TranslationSessionId.ENGINE_SCHEMA_VERSION);
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(track, 0)), "hello");

        return TranslationCacheKey.from(session, unit, "", 1, 1);
    }

    private static final class MemoryStore implements PromptRepository.Store {
        private String value;
        private int writeCount;
        MemoryStore() { }
        MemoryStore(String value) { this.value = value; }
        @Override public String read() { return value; }
        @Override public void write(String payload) { value = payload; writeCount++; }
    }
}
