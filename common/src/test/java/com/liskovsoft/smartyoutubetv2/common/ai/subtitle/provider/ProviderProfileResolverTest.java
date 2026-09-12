package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM resolver tests: five user-facing types share two protocol adapters.
 */
public class ProviderProfileResolverTest {
    @Test
    public void everyProviderTypeResolvesThroughASharedProtocolAdapter() {
        ProviderProfileResolver resolver = new ProviderProfileResolver(
                new UnusedExecutor(), new UnusedSecretStore());

        assertResolved(resolver, ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        assertResolved(resolver, ProviderType.ANTHROPIC_COMPATIBLE,
                ProviderProtocol.ANTHROPIC_MESSAGES);
        assertResolved(resolver, ProviderType.OPENROUTER,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        assertResolved(resolver, ProviderType.DEEPSEEK,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        assertResolved(resolver, ProviderType.MIMO,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
    }

    @Test
    public void incompleteProfileDoesNotBecomeResolved() {
        ProviderPreset preset = ProviderPreset.forType(ProviderType.OPENAI_COMPATIBLE);
        ProviderProfile profile = preset.createDraft("Incomplete", null);
        ProviderProfileResolver resolver = new ProviderProfileResolver(
                new UnusedExecutor(), new UnusedSecretStore());

        ProviderProfileResolver.Resolution resolution = resolver.resolve(profile);

        assertFalse(resolution.isResolved());
        assertNotNull(resolution.getFailure());
        assertEquals(TranslationFailureCategory.PROTOCOL,
                resolution.getFailure().getCategory());
    }

    private static void assertResolved(ProviderProfileResolver resolver, ProviderType type,
                                       ProviderProtocol protocol) {
        ProviderPreset preset = ProviderPreset.forType(type);
        ProviderProfile profile = preset.createDraft(type.name(), "model-1");
        ProviderProfileResolver.Resolution resolution = resolver.resolve(profile);

        assertTrue(resolution.isResolved());
        assertEquals(protocol, resolution.getAdapter().getProtocol());
    }

    private static final class UnusedExecutor implements HttpRequestExecutor {
        @Override
        public HttpCall execute(HttpRequest request, HttpCallback callback) {
            throw new AssertionError("resolver must not execute HTTP");
        }
    }

    private static final class UnusedSecretStore implements SecretStore {
        @Override
        public String get(String reference) {
            throw new AssertionError("resolver must not read a secret");
        }

        @Override
        public void put(String reference, String secret) {
        }

        @Override
        public void delete(String reference) {
        }
    }
}
