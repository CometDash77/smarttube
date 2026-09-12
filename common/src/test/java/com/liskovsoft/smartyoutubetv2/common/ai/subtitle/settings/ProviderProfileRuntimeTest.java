package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProtocolAdapter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests for resolving the selected profile into the M03 session identity and a
 * provider adapter without exposing credentials.
 */
public class ProviderProfileRuntimeTest {
    @Test
    public void selectedValidProfileResolvesToProviderAndTranslationProfile() {
        Fixture fixture = new Fixture();
        ProviderProfile profile = fixture.presenterSave(
                "OpenAI", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                "model-a", "secret-a");

        ProviderProfileRuntime.ResolvedProvider resolved =
                fixture.runtime().resolve();

        assertTrue(resolved.isResolved());
        ProtocolAdapter adapter = (ProtocolAdapter) resolved.getProvider();
        assertEquals(ProviderProtocol.OPENAI_CHAT_COMPLETIONS, adapter.getProtocol());

        TranslationProfile translation = resolved.getProfile();
        assertEquals(profile.getId(), translation.getProviderProfileId());
        assertEquals(ProviderProtocol.OPENAI_CHAT_COMPLETIONS.name(),
                translation.getProviderProtocol());
        assertEquals("https://api.example.com/v1", translation.getBaseUrlIdentity());
        assertEquals("model-a", translation.getModelId());
        assertEquals(ProviderProfileRuntime.PROMPT_PROFILE_ID_PENDING,
                translation.getPromptProfileId());
        assertEquals(ProviderProfileRuntime.PROMPT_VERSION_PENDING,
                translation.getPromptVersion());
        assertEquals(ProviderProfileRuntime.TARGET_LANGUAGE_PENDING,
                translation.getTargetLanguage());
    }

    @Test
    public void emptyStoreResolvesToSourceOnly() {
        Fixture fixture = new Fixture();

        ProviderProfileRuntime.ResolvedProvider resolved =
                fixture.runtime().resolve();

        assertFalse(resolved.isResolved());
        assertTrue(resolved.isSourceOnly());
        assertNull(resolved.getProvider());
        assertNull(resolved.getProfile());
    }

    @Test
    public void incompleteSelectedProfileResolvesToSourceOnly() {
        Fixture fixture = new Fixture();
        fixture.repository().create(new ProviderProfile(null, "Incomplete",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", null, null,
                Collections.<String>emptyList(), null, null));

        ProviderProfileRuntime.ResolvedProvider resolved =
                fixture.runtime().resolve();

        assertFalse(resolved.isResolved());
        assertTrue(resolved.isSourceOnly());
        assertNull(resolved.getProvider());
    }

    private static final class Fixture {
        private final MemoryStore store = new MemoryStore();
        private final RecordingSecretStore secrets = new RecordingSecretStore();
        private final ProviderProfileRepository repository =
                new ProviderProfileRepository(store, secrets);
        private final ProviderProfileResolver resolver =
                new ProviderProfileResolver(new UnusedExecutor(), secrets);

        ProviderProfile presenterSave(String name, ProviderType type,
                                      ProviderProtocol protocol, String baseUrl,
                                      String modelId, String secret) {
            ProviderProfileRepository repo = repository();
            ProviderProfile draft = new ProviderProfile(null, name, type, protocol, baseUrl,
                    null, modelId, Collections.<String>emptyList(), null, null);
            ProviderProfile created = repo.create(draft);
            String reference = created.getId();
            secrets.put(reference, secret);
            ProviderProfile withSecret = new ProviderProfile(created.getId(), created.getName(),
                    type, protocol, created.getBaseUrl(), reference, created.getModelId(),
                    created.getAvailableModelIds(), created.getHeaders(), created.getOptions());
            return repo.update(withSecret);
        }

        ProviderProfileRepository repository() {
            return repository;
        }

        ProviderProfileRuntime runtime() {
            return new ProviderProfileRuntime(repository, resolver);
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
    }

    private static final class UnusedExecutor implements HttpRequestExecutor {
        @Override
        public HttpCall execute(HttpRequest request, HttpCallback callback) {
            throw new AssertionError("runtime resolution must not execute HTTP");
        }
    }
}