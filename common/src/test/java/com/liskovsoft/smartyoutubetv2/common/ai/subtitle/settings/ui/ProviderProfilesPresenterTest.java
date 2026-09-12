package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ConnectionTestResult;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ModelCatalog;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests for the feature-owned Provider Profile management layer.
 */
public class ProviderProfilesPresenterTest {
    @Test
    public void saveCreatesProfileSecretAndSelectsFirstProfile() {
        Fixture fixture = new Fixture();

        ProviderProfilesPresenter.SaveResult result = fixture.presenter.save(
                null, "OpenAI", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "synthetic-key", true);

        assertTrue(result.isSuccess());
        assertNotNull(result.getProfile());
        assertEquals(result.getProfile().getId(), result.getProfile().getSecretReference());
        assertEquals("synthetic-key",
                fixture.secrets.get(result.getProfile().getSecretReference()));
        assertEquals(result.getProfile().getId(), fixture.presenter.getSelectedProfileId());
        assertEquals(result.getProfile().getId(), fixture.presenter.getDefaultProfileId());
    }

    @Test
    public void getProfilesListsPersistedProfiles() {
        Fixture fixture = new Fixture();
        fixture.presenter.save(null, "First", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                "model-a", "key-a", true);
        fixture.presenter.save(null, "Second", ProviderType.ANTHROPIC_COMPATIBLE,
                ProviderProtocol.ANTHROPIC_MESSAGES, "https://api.anthropic.com",
                "model-b", "key-b", true);

        List<ProviderProfile> profiles = fixture.presenter.getProfiles();

        assertEquals(2, profiles.size());
        assertEquals("First", profiles.get(0).getName());
        assertEquals("Second", profiles.get(1).getName());
    }

    @Test
    public void editWithoutReplacementKeepsStoredSecretAndMasksIt() {
        Fixture fixture = new Fixture();
        ProviderProfile profile = fixture.presenter.save(null, "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "synthetic-key", true).getProfile();

        ProviderProfilesPresenter.SaveResult result = fixture.presenter.save(
                profile.getId(), "Renamed", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-b", null, false);

        assertTrue(result.isSuccess());
        assertEquals("synthetic-key", fixture.secrets.get(profile.getSecretReference()));
        assertFalse(fixture.presenter.maskSecret(result.getProfile()).contains("synthetic-key"));
    }

    @Test
    public void editWithReplacementUpdatesSecret() {
        Fixture fixture = new Fixture();
        ProviderProfile profile = fixture.presenter.save(null, "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "old-key", true).getProfile();

        ProviderProfilesPresenter.SaveResult result = fixture.presenter.save(
                profile.getId(), "OpenAI", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "new-key", true);

        assertTrue(result.isSuccess());
        assertEquals("new-key", fixture.secrets.get(profile.getSecretReference()));
    }

    @Test
    public void replacementPersistenceFailureRestoresPreviousSecret() {
        FlakyStore store = new FlakyStore(false);
        Fixture fixture = new Fixture(store);
        ProviderProfile profile = fixture.presenter.save(null, "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "old-key", true).getProfile();
        store.failOnCredentialReference = true;

        ProviderProfilesPresenter.SaveResult result = fixture.presenter.save(
                profile.getId(), "OpenAI", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "new-key", true);

        assertFalse(result.isSuccess());
        assertEquals("old-key", fixture.secrets.get(profile.getSecretReference()));
        assertEquals("model-a", fixture.presenter.getProfile(profile.getId()).getModelId());
    }

    @Test
    public void createPersistenceFailureRollsBackProfileAndSecret() {
        Fixture fixture = new Fixture(new FlakyStore(true));

        ProviderProfilesPresenter.SaveResult result = fixture.presenter.save(null, "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "new-key", true);

        assertFalse(result.isSuccess());
        assertTrue(fixture.presenter.getProfiles().isEmpty());
        assertTrue(((RecordingSecretStore) fixture.secrets).isEmpty());
    }

    @Test
    public void copyDuplicatesConfigurationAndSecretWithoutChangingSelection() {
        Fixture fixture = new Fixture();
        ProviderProfile first = fixture.presenter.save(null, "First",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "key-a", true).getProfile();
        ProviderProfile second = fixture.presenter.save(null, "Second",
                ProviderType.ANTHROPIC_COMPATIBLE, ProviderProtocol.ANTHROPIC_MESSAGES,
                "https://api.anthropic.com", "model-b", "key-b", true).getProfile();
        fixture.presenter.select(second.getId());

        ProviderProfilesPresenter.SaveResult copied = fixture.presenter.copy(first.getId());

        assertTrue(copied.isSuccess());
        assertEquals("First copy", copied.getProfile().getName());
        assertEquals("model-a", copied.getProfile().getModelId());
        assertEquals("https://api.example.com/v1", copied.getProfile().getBaseUrl());
        assertEquals("key-a", fixture.secrets.get(copied.getProfile().getSecretReference()));
        assertEquals(second.getId(), fixture.presenter.getSelectedProfileId());
    }

    @Test
    public void invalidBaseModelAndSecretAreRejectedWithoutWriting() {
        Fixture fixture = new Fixture();

        assertFalse(fixture.presenter.save(null, "Bad Base", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "not-a-url", "model-a",
                "key", true).isSuccess());
        assertTrue(fixture.presenter.getProfiles().isEmpty());

        assertFalse(fixture.presenter.save(null, "Bad Model", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                null, "key", true).isSuccess());
        assertTrue(fixture.presenter.getProfiles().isEmpty());

        assertFalse(fixture.presenter.save(null, "Bad Secret", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                "model-a", null, true).isSuccess());
        assertTrue(fixture.presenter.getProfiles().isEmpty());
    }

    @Test
    public void saveRejectsProtocolMismatch() {
        Fixture fixture = new Fixture();

        assertFalse(fixture.presenter.save(null, "Bad Protocol", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.ANTHROPIC_MESSAGES, "https://api.example.com/v1",
                "model-a", "key", true).isSuccess());
        assertTrue(fixture.presenter.getProfiles().isEmpty());
    }

    @Test
    public void selectRejectsIncompleteProfileAndAcceptsValidProfile() {
        Fixture fixture = new Fixture();
        ProviderProfile incomplete = fixture.repository.create(new ProviderProfile(
                null, "Incomplete", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                null, null, Collections.<String>emptyList(), null, null));
        ProviderProfile valid = fixture.presenter.save(null, "Valid",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "key", true).getProfile();

        assertFalse(fixture.presenter.select(incomplete.getId()));
        assertTrue(fixture.presenter.select(valid.getId()));
        assertEquals(valid.getId(), fixture.presenter.getSelectedProfileId());
    }

    @Test
    public void setDefaultRejectsIncompleteProfileAndAcceptsValidProfile() {
        Fixture fixture = new Fixture();
        ProviderProfile incomplete = fixture.repository.create(new ProviderProfile(
                null, "Incomplete", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                null, null, Collections.<String>emptyList(), null, null));
        ProviderProfile valid = fixture.presenter.save(null, "Valid",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "key", true).getProfile();

        assertFalse(fixture.presenter.setDefault(incomplete.getId()));
        assertTrue(fixture.presenter.setDefault(valid.getId()));
        assertEquals(valid.getId(), fixture.presenter.getDefaultProfileId());
    }

    @Test
    public void deleteRepairsSelectionAndClearsSecret() {
        Fixture fixture = new Fixture();
        ProviderProfile first = fixture.presenter.save(null, "First",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "key-a", true).getProfile();
        ProviderProfile second = fixture.presenter.save(null, "Second",
                ProviderType.ANTHROPIC_COMPATIBLE, ProviderProtocol.ANTHROPIC_MESSAGES,
                "https://api.anthropic.com", "model-b", "key-b", true).getProfile();
        fixture.presenter.select(second.getId());

        assertTrue(fixture.presenter.delete(second.getId()));
        assertEquals(first.getId(), fixture.presenter.getSelectedProfileId());
        assertNull(fixture.secrets.get(second.getSecretReference()));
    }

    @Test
    public void presenterRecreationRestoresProfilesAndSelections() {
        Fixture fixture = new Fixture();
        ProviderProfile saved = fixture.presenter.save(null, "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "key", true).getProfile();

        ProviderProfilesPresenter recreated = fixture.newPresenter();

        assertEquals(1, recreated.getProfiles().size());
        assertEquals(saved.getId(), recreated.getSelectedProfileId());
        assertEquals(saved.getId(), recreated.getDefaultProfileId());
    }

    @Test
    public void connectionTestReportsStartedAndNormalizedResult() {
        Fixture fixture = new Fixture();
        ProviderProfile profile = fixture.presenter.save(null, "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "key", true).getProfile();
        final boolean[] started = new boolean[1];
        final ConnectionTestResult[] result = new ConnectionTestResult[1];
        fixture.executor.response = new HttpRequestExecutor.HttpResponse(
                200, "{\"data\":[{\"id\":\"model-a\"}]}", "req-test");

        fixture.presenter.testConnection(profile,
                new ProviderProfilesPresenter.ConnectionTestListener() {
                    @Override
                    public void onStarted() {
                        started[0] = true;
                    }

                    @Override
                    public void onResult(ConnectionTestResult value) {
                        result[0] = value;
                    }
                });

        assertTrue(started[0]);
        assertTrue(result[0].isSuccess());
        assertEquals(Collections.singletonList("model-a"), result[0].getModels());
    }

    @Test
    public void connectionTestCancellationSuppressesResult() {
        Fixture fixture = new Fixture();
        ProviderProfile profile = fixture.presenter.save(null, "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "model-a", "key", true).getProfile();
        fixture.executor.deferred = true;
        fixture.executor.response = new HttpRequestExecutor.HttpResponse(
                200, "{\"data\":[{\"id\":\"model-a\"}]}", "req-test");
        final boolean[] started = new boolean[1];
        final boolean[] delivered = new boolean[1];

        ProviderProfilesPresenter.ConnectionTest test = fixture.presenter.testConnection(
                profile, new ProviderProfilesPresenter.ConnectionTestListener() {
                    @Override
                    public void onStarted() {
                        started[0] = true;
                    }

                    @Override
                    public void onResult(ConnectionTestResult value) {
                        delivered[0] = true;
                    }
                });

        test.cancel();
        fixture.executor.deliver();

        assertTrue(started[0]);
        assertFalse(delivered[0]);
        assertTrue(test.isCancelled());
    }

    @Test
    public void editorTracksUnsavedChangesForCreateAndEdit() {
        ProviderProfileEditor createEditor = ProviderProfileEditor.create(
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        assertFalse(createEditor.hasUnsavedChanges());

        createEditor.setName("OpenAI");
        createEditor.setBaseUrl("https://api.example.com/v1");
        createEditor.setModelId("model-a");
        createEditor.setSecret("key");
        assertTrue(createEditor.hasUnsavedChanges());

        ProviderProfile original = new ProviderProfile("profile-1", "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "secret-1", "model-a",
                Collections.<String>emptyList(), null, null);
        ProviderProfileEditor editEditor = ProviderProfileEditor.edit(original);
        assertFalse(editEditor.hasUnsavedChanges());

        editEditor.setModelId("model-b");
        assertTrue(editEditor.hasUnsavedChanges());
    }

    private static final class Fixture {
        private final ProviderProfileRepository.Store store;
        private final RecordingSecretStore secrets = new RecordingSecretStore();
        private final FakeExecutor executor = new FakeExecutor();
        private final ProviderProfileRepository repository;
        private final ProviderProfileResolver resolver;
        private final ModelCatalog catalog;
        private final ProviderProfilesPresenter presenter;

        Fixture() {
            this(new MemoryStore());
        }

        Fixture(ProviderProfileRepository.Store store) {
            this.store = store;
            this.repository = new ProviderProfileRepository(store, secrets);
            this.resolver = new ProviderProfileResolver(executor, secrets);
            this.catalog = new ModelCatalog(executor);
            this.presenter = new ProviderProfilesPresenter(repository, secrets, resolver, catalog);
        }

        ProviderProfilesPresenter newPresenter() {
            return new ProviderProfilesPresenter(
                    new ProviderProfileRepository(store, secrets), secrets,
                    new ProviderProfileResolver(executor, secrets),
                    new ModelCatalog(executor));
        }
    }

    private static final class MemoryStore implements ProviderProfileRepository.Store {
        private String mData;

        @Override
        public String read() {
            return mData;
        }

        @Override
        public void write(String payload) {
            mData = payload;
        }
    }

    private static final class FlakyStore implements ProviderProfileRepository.Store {
        private String mData;
        private boolean failOnCredentialReference;

        FlakyStore(boolean failOnCredentialReference) {
            this.failOnCredentialReference = failOnCredentialReference;
        }

        @Override
        public String read() {
            return mData;
        }

        @Override
        public void write(String payload) {
            if (failOnCredentialReference && payload.contains("\"credentialReference\":\"")) {
                throw new IllegalStateException("synthetic persistence failure");
            }
            mData = payload;
        }
    }

    private static final class RecordingSecretStore implements SecretStore {
        private final Map<String, String> values = new LinkedHashMap<>();

        @Override
        public String get(String reference) {
            return values.get(reference);
        }

        @Override
        public void put(String reference, String secret) {
            values.put(reference, secret);
        }

        @Override
        public void delete(String reference) {
            values.remove(reference);
        }

        boolean isEmpty() {
            return values.isEmpty();
        }
    }

    private static final class FakeExecutor implements HttpRequestExecutor {
        private HttpResponse response;
        private HttpCallback lastCallback;
        private HttpCall lastCall;
        private boolean deferred;

        @Override
        public HttpCall execute(HttpRequest request, HttpCallback callback) {
            lastCallback = callback;
            lastCall = new FakeCall();
            if (!deferred) {
                deliver();
            }
            return lastCall;
        }

        void deliver() {
            if (lastCallback != null && response != null) {
                lastCallback.onSuccess(response);
            }
        }
    }

    private static final class FakeCall implements HttpRequestExecutor.HttpCall {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}