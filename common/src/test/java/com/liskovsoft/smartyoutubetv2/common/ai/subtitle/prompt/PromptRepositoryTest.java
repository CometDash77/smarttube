package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PromptRepositoryTest {
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

    private static final class MemoryStore implements PromptRepository.Store {
        private String value;
        private int writeCount;
        MemoryStore() { }
        MemoryStore(String value) { this.value = value; }
        @Override public String read() { return value; }
        @Override public void write(String payload) { value = payload; writeCount++; }
    }
}
