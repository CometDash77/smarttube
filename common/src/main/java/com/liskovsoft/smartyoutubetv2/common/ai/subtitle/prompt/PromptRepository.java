package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent CRUD boundary for built-in and custom prompt profiles. */
public final class PromptRepository {
    private final Store mStore;
    private final PromptSerializer mSerializer;
    private final PromptMigration mMigration;

    public PromptRepository(Store store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        mStore = store;
        mSerializer = new PromptSerializer();
        mMigration = new PromptMigration(mSerializer);
    }

    public synchronized PromptState load() {
        PromptMigration.MigrationResult migration = mMigration.migrate(mStore.read());
        PromptState normalized = normalize(migration.getState());
        if (migration.requiresWrite() || !normalized.equals(migration.getState())) mStore.write(mSerializer.serialize(normalized));
        return normalized;
    }

    public synchronized PromptProfile createCustom(String name, String content) {
        validateContent(content);
        PromptState state = load();
        String id;
        Set<String> ids = ids(state.getProfiles());
        do { id = "custom.subtitle." + UUID.randomUUID().toString(); } while (ids.contains(id));
        PromptProfile profile = new PromptProfile(id, name, content, 1, false);
        List<PromptProfile> profiles = new ArrayList<>(state.getProfiles());
        profiles.add(profile);
        mStore.write(mSerializer.serialize(new PromptState(profiles, state.getSelectedProfileId(), state.getDefaultProfileId())));
        return load().getProfile(id);
    }

    public synchronized PromptProfile copyToCustom(String sourceId, String name) {
        PromptProfile source = load().getProfile(sourceId);
        if (source == null) throw new IllegalArgumentException("unknown prompt profile: " + sourceId);
        return createCustom(name, source.getContent());
    }

    public synchronized PromptProfile update(String id, String name, String content) {
        validateContent(content);
        PromptState state = load();
        PromptProfile existing = state.getProfile(id);
        if (existing == null) throw new IllegalArgumentException("unknown prompt profile: " + id);
        if (existing.isBuiltIn()) throw new IllegalArgumentException("built-in prompts are immutable");
        List<PromptProfile> profiles = new ArrayList<>(state.getProfiles());
        profiles.set(profiles.indexOf(existing), existing.withCustomValues(name, content));
        mStore.write(mSerializer.serialize(new PromptState(profiles, state.getSelectedProfileId(), state.getDefaultProfileId())));
        return load().getProfile(id);
    }

    public synchronized boolean delete(String id) {
        PromptState state = load();
        PromptProfile existing = state.getProfile(id);
        if (existing == null) return false;
        if (existing.isBuiltIn()) throw new IllegalArgumentException("built-in prompts are immutable");
        List<PromptProfile> profiles = new ArrayList<>(state.getProfiles());
        profiles.remove(existing);
        mStore.write(mSerializer.serialize(new PromptState(profiles, state.getSelectedProfileId(), state.getDefaultProfileId())));
        load();
        return true;
    }

    public synchronized PromptState select(String id) {
        PromptState state = load();
        if (state.getProfile(id) == null) throw new IllegalArgumentException("unknown prompt profile: " + id);
        PromptState selected = normalize(state.withSelections(id, state.getDefaultProfileId()));
        mStore.write(mSerializer.serialize(selected));
        return selected;
    }

    public synchronized PromptState setDefault(String id) {
        PromptState state = load();
        if (state.getProfile(id) == null) throw new IllegalArgumentException("unknown prompt profile: " + id);
        PromptState updated = normalize(state.withSelections(state.getSelectedProfileId(), id));
        mStore.write(mSerializer.serialize(updated));
        return updated;
    }

    private static PromptState normalize(PromptState state) {
        List<PromptProfile> profiles = state.getProfiles();
        if (profiles.isEmpty()) return new PromptState(profiles, null, null);
        String defaultId = state.getDefaultProfileId();
        if (defaultId == null || state.getProfile(defaultId) == null) defaultId = profiles.get(0).getId();
        String selectedId = state.getSelectedProfileId();
        if (selectedId == null || state.getProfile(selectedId) == null) selectedId = defaultId;
        return new PromptState(profiles, selectedId, defaultId);
    }

    private static Set<String> ids(List<PromptProfile> profiles) {
        Set<String> ids = new HashSet<>();
        for (PromptProfile profile : profiles) ids.add(profile.getId());
        return ids;
    }

    private static void validateContent(String content) {
        PromptRenderer.RenderResult result = new PromptRenderer().validate(content);
        if (!result.isValid()) throw new IllegalArgumentException("prompt content is invalid");
    }

    public interface Store { String read(); void write(String payload); }
}
