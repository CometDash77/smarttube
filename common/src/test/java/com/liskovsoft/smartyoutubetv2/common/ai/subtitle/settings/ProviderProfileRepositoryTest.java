package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM persistence contract tests. The store fake changes its active value exactly as a
 * SmartTube app-profile switch would, while every repository operation reads through to it.
 */
public class ProviderProfileRepositoryTest {
    @Test
    public void emptyStoreLoadsDefaultsAndPersistsCurrentSchemaOnce() {
        MemoryStore store = new MemoryStore();
        ProviderProfileRepository repository = repository(store);

        ProviderProfileState state = repository.load();

        assertTrue(state.getProfiles().isEmpty());
        assertNull(state.getSelectedProfileId());
        assertNull(state.getDefaultProfileId());
        assertEquals(1, store.getWriteCount());
        assertTrue(store.read().contains("\"schemaVersion\":1"));

        repository.load();
        assertEquals(1, store.getWriteCount());
    }

    @Test
    public void createAssignsStableIdAndFirstProfileBecomesSelectedAndDefault() {
        MemoryStore store = new MemoryStore();
        ProviderProfileRepository repository = repository(store, ids("generated-1"));

        ProviderProfile saved = repository.create(draft(null, "First"));

        assertEquals("generated-1", saved.getId());
        ProviderProfileState reloaded = repository(store).load();
        assertEquals(1, reloaded.getProfiles().size());
        assertEquals("generated-1", reloaded.getProfiles().get(0).getId());
        assertEquals("generated-1", reloaded.getSelectedProfileId());
        assertEquals("generated-1", reloaded.getDefaultProfileId());
    }

    @Test
    public void createUpdateDeletePreserveDeterministicSelections() {
        MemoryStore store = new MemoryStore();
        ProviderProfileRepository repository = repository(store, ids("profile-a", "profile-b", "profile-c"));

        ProviderProfile first = repository.create(draft(null, "First"));
        ProviderProfile second = repository.create(draft(null, "Second"));
        ProviderProfile third = repository.create(draft(null, "Third"));

        assertEquals("profile-a", repository.load().getSelectedProfileId());
        assertEquals("profile-a", repository.load().getDefaultProfileId());

        repository.select(second.getId());
        repository.setDefault(second.getId());
        assertEquals("profile-b", repository.load().getSelectedProfileId());
        assertEquals("profile-b", repository.load().getDefaultProfileId());

        assertTrue(repository.delete(second.getId()));
        ProviderProfileState afterDelete = repository.load();
        assertEquals(2, afterDelete.getProfiles().size());
        assertEquals("profile-a", afterDelete.getSelectedProfileId());
        assertEquals("profile-a", afterDelete.getDefaultProfileId());

        ProviderProfile updated = repository.update(new ProviderProfile(third.getId(), "Third Updated",
                third.getProviderType(), third.getProtocol(), third.getBaseUrl(),
                third.getSecretReference(), "updated-model", third.getAvailableModelIds(),
                third.getHeaders(), third.getOptions()));
        assertEquals("Third Updated", repository.load().getProfile(updated.getId()).getName());
        assertEquals("updated-model", repository.load().getProfile(updated.getId()).getModelId());
    }

    @Test
    public void appProfileSwitchReloadsProfilesFromTheNewProfileStore() {
        MemoryStore store = new MemoryStore();
        ProviderProfileRepository repository = repository(store, ids("profile-a", "profile-b"));

        repository.create(draft(null, "First"));
        store.switchAppProfile();
        assertTrue(repository.load().getProfiles().isEmpty());

        ProviderProfile second = repository.create(draft(null, "Second"));
        assertEquals("profile-b", second.getId());
        assertEquals("Second", repository.load().getProfiles().get(0).getName());

        store.switchAppProfile();
        assertEquals("First", repository.load().getProfiles().get(0).getName());
    }

    @Test
    public void missingOrDanglingSelectionsRepairToFirstProfile() {
        ProviderProfile profile = draft("profile-a", "First");
        String payload = new ProviderProfileSerializer().serialize(new ProviderProfileState(
                Collections.singletonList(profile), "missing", "also-missing"));
        MemoryStore store = new MemoryStore(payload);

        ProviderProfileState repaired = repository(store).load();

        assertEquals("profile-a", repaired.getSelectedProfileId());
        assertEquals("profile-a", repaired.getDefaultProfileId());
        assertTrue(store.read().contains("\"selectedProfileId\":\"profile-a\""));
    }

    @Test
    public void corruptStoreRepairsToCurrentEmptyPayload() {
        MemoryStore store = new MemoryStore("{broken");

        ProviderProfileState state = repository(store).load();

        assertTrue(state.getProfiles().isEmpty());
        assertTrue(store.read().startsWith("{\"schemaVersion\":1"));
    }

    @Test
    public void futureSchemaIsRejectedAndPayloadIsPreserved() {
        String future = "{\"schemaVersion\":99,\"profiles\":[]}";
        MemoryStore store = new MemoryStore(future);

        try {
            repository(store).load();
            fail("future schema must be rejected");
        } catch (ProviderProfileMigration.FutureSchemaException expected) {
            assertEquals(99, expected.getSchemaVersion());
            assertEquals(0, store.getWriteCount());
            assertEquals(future, store.read());
        }
    }

    @Test
    public void duplicateIdsAreRepairedDeterministically() {
        ProviderProfile first = draft("duplicate", "First");
        ProviderProfile second = draft("duplicate", "Second");
        String payload = new ProviderProfileSerializer().serialize(new ProviderProfileState(
                java.util.Arrays.asList(first, second), "duplicate", "duplicate"));
        MemoryStore store = new MemoryStore(payload);

        ProviderProfileState repaired = repository(store).load();

        assertEquals(2, repaired.getProfiles().size());
        assertEquals("First", repaired.getProfiles().get(0).getName());
        assertEquals("generated-1", repaired.getProfiles().get(1).getId());
        assertTrue(store.getWriteCount() > 0);
    }

    @Test
    public void duplicateCreateAndUnknownUpdateAreRejectedWithoutWriting() {
        MemoryStore store = new MemoryStore();
        ProviderProfileRepository repository = repository(store, ids("profile-a", "profile-b"));
        ProviderProfile first = repository.create(draft(null, "First"));
        int writes = store.getWriteCount();

        try {
            repository.create(draft(first.getId(), "Duplicate"));
            fail("duplicate id must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals(writes, store.getWriteCount());

        try {
            repository.update(draft("missing", "Missing"));
            fail("unknown update must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals(writes, store.getWriteCount());
    }

    private static ProviderProfileRepository repository(MemoryStore store) {
        return repository(store, ids("generated-1", "generated-2", "generated-3"));
    }

    private static ProviderProfileRepository repository(MemoryStore store, IdGenerator ids) {
        return new ProviderProfileRepository(store, ids);
    }

    private static IdGenerator ids(final String... values) {
        return new IdGenerator() {
            private int mIndex;

            @Override
            public String generate() {
                return values[mIndex++];
            }
        };
    }

    private static ProviderProfile draft(String id, String name) {
        return new ProviderProfile(id, name, ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                id == null ? null : "credential-" + id, "model-a",
                Collections.singletonList("model-a"), null, null);
    }

    private interface IdGenerator extends ProviderProfileRepository.IdGenerator {
    }

    private static final class MemoryStore implements ProviderProfileRepository.Store {
        private String mFirst = null;
        private String mSecond = null;
        private boolean mSecondActive;
        private int mWriteCount;

        MemoryStore() {
        }

        MemoryStore(String initial) {
            mFirst = initial;
        }

        @Override
        public String read() {
            return mSecondActive ? mSecond : mFirst;
        }

        @Override
        public void write(String value) {
            if (mSecondActive) {
                mSecond = value;
            } else {
                mFirst = value;
            }
            mWriteCount++;
        }

        void switchAppProfile() {
            mSecondActive = !mSecondActive;
        }

        int getWriteCount() {
            return mWriteCount;
        }
    }
}
