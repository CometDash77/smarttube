package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TranslationProfileResolverTest {
    @Test
    public void resolvesAllRequiredPartsIntoOneImmutableProfile() {
        Fixture fixture = new Fixture();
        TranslationProfileResolver.Resolution result = fixture.resolver.resolve(" zh ");
        assertTrue(result.isResolved());
        assertEquals("zh", result.getProfile().getTargetLanguage());
        assertEquals("builtin.subtitle.baseline", result.getProfile().getPromptProfileId());
        assertNotNull(result.getProfile().getPromptContentHash());
        assertNotNull(result.getProvider());
    }

    @Test
    public void missingProviderPromptLanguageOrSecretIsSourceOnly() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.resolver.resolve(" ").isSourceOnly());
        fixture.secrets.values.clear();
        assertTrue(fixture.resolver.resolve("zh").isSourceOnly());
        fixture.secrets.values.put("secret-a", "value");
        fixture.providers.reset();
        assertTrue(fixture.resolver.resolve("zh").isSourceOnly());
    }

    @Test
    public void promptContentAndTargetLanguageChangeResolvedIdentity() {
        Fixture fixture = new Fixture();
        TranslationProfileResolver.Resolution first = fixture.resolver.resolve("zh");
        PromptProfile custom = fixture.prompts.copyToCustom("builtin.subtitle.baseline", "Custom");
        fixture.prompts.select(custom.getId());
        fixture.prompts.update(custom.getId(), "Custom", "Return {{source_text}} only.");
        TranslationProfileResolver.Resolution second = fixture.resolver.resolve("ja");
        assertFalse(first.getProfile().equals(second.getProfile()));
        assertFalse(first.getProfile().getPromptContentHash().equals(
                second.getProfile().getPromptContentHash()));
        assertEquals("ja", second.getProfile().getTargetLanguage());
    }

    private static final class Fixture {
        private final MemoryStore providerStore = new MemoryStore();
        private final MemoryPromptStore promptStore = new MemoryPromptStore();
        private final RecordingSecrets secrets = new RecordingSecrets();
        private final ProviderProfileRepository providers = new ProviderProfileRepository(providerStore);
        private final PromptRepository prompts = new PromptRepository(promptStore);
        private final TranslationProfileResolver resolver;

        Fixture() {
            ProviderProfile profile = providers.create(new ProviderProfile("profile-a", "Test",
                    ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    "https://example.test/v1", "secret-a", "model-a",
                    Collections.singletonList("model-a"), null, null));
            providers.select(profile.getId());
            secrets.values.put("secret-a", "secret-value");
            resolver = new TranslationProfileResolver(providers,
                    new ProviderProfileResolver(new NoopExecutor(), secrets), prompts, secrets);
        }
    }

    private static final class MemoryStore implements ProviderProfileRepository.Store {
        private String value;
        @Override public String read() { return value; }
        @Override public void write(String payload) { value = payload; }
    }

    private static final class MemoryPromptStore implements PromptRepository.Store {
        private String value;
        @Override public String read() { return value; }
        @Override public void write(String payload) { value = payload; }
    }

    private static final class RecordingSecrets implements SecretStore {
        private final Map<String, String> values = new LinkedHashMap<>();
        @Override public String get(String reference) { return values.get(reference); }
        @Override public void put(String reference, String secret) { values.put(reference, secret); }
        @Override public void delete(String reference) { values.remove(reference); }
    }

    private static final class NoopExecutor implements HttpRequestExecutor {
        @Override public HttpCall execute(HttpRequest request, HttpCallback callback) {
            throw new AssertionError("resolution must not execute HTTP");
        }
    }
}
