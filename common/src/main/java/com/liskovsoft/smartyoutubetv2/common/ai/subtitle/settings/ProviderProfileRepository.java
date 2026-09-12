package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persists non-secret Provider Profiles and repairs their list/selection invariants on read.
 *
 * <p>The repository owns no in-memory snapshot, so an app-profile switch changes the
 * underlying {@link Store} value and the next operation reads the newly active profile.</p>
 */
public final class ProviderProfileRepository {
    private static final int MAX_ID_ATTEMPTS = 100;

    private final Store mStore;
    private final IdGenerator mIdGenerator;
    private final SecretStore mSecretStore;
    private final ProviderProfileSerializer mSerializer;
    private final ProviderProfileMigration mMigration;

    public ProviderProfileRepository(Store store) {
        this(store, new UuidIdGenerator(), SecretStore.NONE);
    }

    public ProviderProfileRepository(Store store, IdGenerator idGenerator) {
        this(store, idGenerator, SecretStore.NONE);
    }

    public ProviderProfileRepository(Store store, SecretStore secretStore) {
        this(store, new UuidIdGenerator(), secretStore);
    }

    public ProviderProfileRepository(Store store, IdGenerator idGenerator,
                                     SecretStore secretStore) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (idGenerator == null) {
            throw new IllegalArgumentException("idGenerator must not be null");
        }
        mStore = store;
        mIdGenerator = idGenerator;
        mSecretStore = secretStore != null ? secretStore : SecretStore.NONE;
        mSerializer = new ProviderProfileSerializer();
        mMigration = new ProviderProfileMigration(mSerializer);
    }

    public synchronized ProviderProfileState load() {
        ProviderProfileMigration.MigrationResult migration = mMigration.migrate(mStore.read());
        NormalizationResult normalization = normalize(migration.getState());
        ProviderProfileState state = normalization.getState();
        if (migration.requiresWrite() || normalization.wasRepaired()) {
            write(state);
        }
        return state;
    }

    public synchronized ProviderProfile create(ProviderProfile draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }

        ProviderProfileState state = load();
        String requestedId = draft.getId();
        String id = requestedId;
        if (id == null) {
            id = newUniqueId(state);
        } else if (state.getProfile(id) != null) {
            throw new IllegalArgumentException("duplicate provider profile id: " + id);
        }

        ProviderProfile saved = draft.withId(id);
        List<ProviderProfile> profiles = new ArrayList<>(state.getProfiles());
        profiles.add(saved);
        write(new ProviderProfileState(profiles, state.getSelectedProfileId(),
                state.getDefaultProfileId()));
        return load().getProfile(id);
    }

    public synchronized ProviderProfile update(ProviderProfile profile) {
        if (profile == null || profile.getId() == null) {
            throw new IllegalArgumentException("profile with an id is required");
        }

        ProviderProfileState state = load();
        int index = indexOf(state.getProfiles(), profile.getId());
        if (index < 0) {
            throw new IllegalArgumentException("unknown provider profile id: " + profile.getId());
        }

        ProviderProfile previous = state.getProfiles().get(index);
        if (!sameValue(previous.getSecretReference(), profile.getSecretReference())) {
            deleteSecret(previous.getSecretReference());
        }

        List<ProviderProfile> profiles = new ArrayList<>(state.getProfiles());
        profiles.set(index, profile);
        write(new ProviderProfileState(profiles, state.getSelectedProfileId(),
                state.getDefaultProfileId()));
        return load().getProfile(profile.getId());
    }

    public synchronized boolean delete(String id) {
        ProviderProfileState state = load();
        int index = indexOf(state.getProfiles(), id);
        if (index < 0) {
            return false;
        }

        ProviderProfile removed = state.getProfiles().get(index);
        deleteSecret(removed.getSecretReference());

        List<ProviderProfile> profiles = new ArrayList<>(state.getProfiles());
        profiles.remove(index);
        write(new ProviderProfileState(profiles, state.getSelectedProfileId(),
                state.getDefaultProfileId()));
        load();
        return true;
    }

    public synchronized void reset() {
        ProviderProfileState state = load();
        for (ProviderProfile profile : state.getProfiles()) {
            deleteSecret(profile.getSecretReference());
        }
        write(ProviderProfileState.empty());
    }

    public synchronized ProviderProfileState select(String id) {
        ProviderProfileState state = load();
        if (state.getProfile(id) == null) {
            throw new IllegalArgumentException("unknown provider profile id: " + id);
        }
        ProviderProfileState selected = state.withSelections(id, state.getDefaultProfileId());
        write(selected);
        return load();
    }

    public synchronized ProviderProfileState setDefault(String id) {
        ProviderProfileState state = load();
        if (state.getProfile(id) == null) {
            throw new IllegalArgumentException("unknown provider profile id: " + id);
        }
        ProviderProfileState updated = state.withSelections(state.getSelectedProfileId(), id);
        write(updated);
        return load();
    }

    private NormalizationResult normalize(ProviderProfileState source) {
        List<ProviderProfile> profiles = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        boolean repaired = false;

        for (ProviderProfile profile : source.getProfiles()) {
            if (profile == null) {
                repaired = true;
                continue;
            }

            ProviderProfile normalized = profile;
            String id = profile.getId();
            if (id == null || ids.contains(id)) {
                id = newUniqueId(ids);
                normalized = profile.withId(id);
                repaired = true;
            }
            ids.add(id);
            profiles.add(normalized);
        }

        String selectedId = source.getSelectedProfileId();
        String defaultId = source.getDefaultProfileId();

        if (profiles.isEmpty()) {
            if (selectedId != null || defaultId != null) {
                repaired = true;
            }
            selectedId = null;
            defaultId = null;
        } else {
            if (defaultId == null || !ids.contains(defaultId)) {
                defaultId = profiles.get(0).getId();
                repaired = true;
            }
            if (selectedId == null || !ids.contains(selectedId)) {
                selectedId = defaultId;
                repaired = true;
            }
        }

        ProviderProfileState normalizedState =
                new ProviderProfileState(profiles, selectedId, defaultId);
        return new NormalizationResult(normalizedState, repaired);
    }

    private String newUniqueId(ProviderProfileState state) {
        return newUniqueId(idsOf(state.getProfiles()));
    }

    private String newUniqueId(Set<String> existingIds) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String id = mIdGenerator.generate();
            if (id != null && !id.trim().isEmpty() && !existingIds.contains(id)) {
                return id;
            }
        }
        throw new IllegalStateException("could not generate a unique provider profile id");
    }

    private static Set<String> idsOf(List<ProviderProfile> profiles) {
        Set<String> ids = new HashSet<>();
        for (ProviderProfile profile : profiles) {
            if (profile != null && profile.getId() != null) {
                ids.add(profile.getId());
            }
        }
        return ids;
    }

    private static int indexOf(List<ProviderProfile> profiles, String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < profiles.size(); i++) {
            if (id.equals(profiles.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private void deleteSecret(String reference) {
        if (reference != null) {
            mSecretStore.delete(reference);
        }
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private void write(ProviderProfileState state) {
        mStore.write(mSerializer.serialize(state));
    }

    public interface Store {
        String read();

        void write(String payload);
    }

    public interface IdGenerator {
        String generate();
    }

    private static final class UuidIdGenerator implements IdGenerator {
        @Override
        public String generate() {
            return UUID.randomUUID().toString();
        }
    }

    private static final class NormalizationResult {
        private final ProviderProfileState mState;
        private final boolean mWasRepaired;

        NormalizationResult(ProviderProfileState state, boolean wasRepaired) {
            mState = state;
            mWasRepaired = wasRepaired;
        }

        ProviderProfileState getState() {
            return mState;
        }

        boolean wasRepaired() {
            return mWasRepaired;
        }
    }
}