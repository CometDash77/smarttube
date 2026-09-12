package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link ProviderProfile}.
 */
public class ProviderProfileTest {
    @Test
    public void gettersExposeExactValuesWithDefensiveCopies() {
        List<String> models = Arrays.asList("model-a", "model-b");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Example", "one");
        Map<String, String> options = new LinkedHashMap<>();
        options.put("mode", "fast");

        ProviderProfile profile = profile(models, headers, options);

        assertEquals("profile-1", profile.getId());
        assertEquals("Example", profile.getName());
        assertEquals(ProviderType.OPENAI_COMPATIBLE, profile.getProviderType());
        assertEquals(ProviderProtocol.OPENAI_CHAT_COMPLETIONS, profile.getProtocol());
        assertEquals("https://api.example.com/v1", profile.getBaseUrl());
        assertEquals("secret-ref-1", profile.getSecretReference());
        assertEquals("model-a", profile.getModelId());
        assertEquals(models, profile.getAvailableModelIds());
        assertEquals(headers, profile.getHeaders());
        assertEquals(options, profile.getOptions());

        models.set(0, "mutated");
        headers.put("X-Example", "mutated");
        options.put("mode", "mutated");

        assertEquals("model-a", profile.getAvailableModelIds().get(0));
        assertEquals("one", profile.getHeaders().get("X-Example"));
        assertEquals("fast", profile.getOptions().get("mode"));
        assertThrows(UnsupportedOperationException.class, new Runnable() {
            @Override
            public void run() {
                profile.getAvailableModelIds().add("model-c");
            }
        });
        assertThrows(UnsupportedOperationException.class, new Runnable() {
            @Override
            public void run() {
                profile.getHeaders().put("X-Other", "two");
            }
        });
    }

    @Test
    public void equalValuesProduceEqualObjectsAndEveryFieldParticipates() {
        ProviderProfile base = profile(Arrays.asList("model-a", "model-b"),
                Collections.singletonMap("X-Example", "one"),
                Collections.singletonMap("mode", "fast"));

        assertTrue(base.equals(profile(Arrays.asList("model-a", "model-b"),
                Collections.singletonMap("X-Example", "one"),
                Collections.singletonMap("mode", "fast"))));
        assertEquals(base.hashCode(), profile(Arrays.asList("model-a", "model-b"),
                Collections.singletonMap("X-Example", "one"),
                Collections.singletonMap("mode", "fast")).hashCode());

        assertFalse(base.equals(new ProviderProfile("profile-1", "Example", ProviderType.DEEPSEEK,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                "secret-ref-1", "model-a", Arrays.asList("model-a", "model-b"),
                Collections.singletonMap("X-Example", "one"),
                Collections.singletonMap("mode", "fast"))));
        assertFalse(base.equals(new ProviderProfile("profile-1", "Example", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.ANTHROPIC_MESSAGES, "https://api.example.com/v1",
                "secret-ref-1", "model-a", Arrays.asList("model-a", "model-b"),
                Collections.singletonMap("X-Example", "one"),
                Collections.singletonMap("mode", "fast"))));
        assertFalse(base.equals(new ProviderProfile("profile-1", "Example", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://other.example.com/v1",
                "secret-ref-1", "model-a", Arrays.asList("model-a", "model-b"),
                Collections.singletonMap("X-Example", "one"),
                Collections.singletonMap("mode", "fast"))));
        assertFalse(base.equals(new ProviderProfile("profile-1", "Example", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                "secret-ref-2", "model-a", Arrays.asList("model-a", "model-b"),
                Collections.singletonMap("X-Example", "one"),
                Collections.singletonMap("mode", "fast"))));
    }

    @Test
    public void requiredFieldsAndCredentialBearingBaseUrlAreRejected() {
        assertInvalidName(null);
        assertInvalidName("");
        assertInvalidName("  ");

        try {
            new ProviderProfile("profile-1", "Example", null, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    "https://api.example.com/v1", null, null, null, null, null);
            fail("provider type must be required");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new ProviderProfile("profile-1", "Example", ProviderType.OPENAI_COMPATIBLE, null,
                    "https://api.example.com/v1", null, null, null, null, null);
            fail("protocol must be required");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new ProviderProfile("profile-1", "Example", ProviderType.OPENAI_COMPATIBLE,
                    ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://user:key@example.com/v1",
                    null, null, null, null, null);
            fail("credential-bearing base url must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void optionalCollectionsDefaultToEmptyAndBlankOptionalFieldsNormalizeToNull() {
        ProviderProfile profile = new ProviderProfile(null, "Example", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                " ", "", null, null, null);

        assertEquals(null, profile.getId());
        assertEquals(null, profile.getSecretReference());
        assertEquals(null, profile.getModelId());
        assertTrue(profile.getAvailableModelIds().isEmpty());
        assertTrue(profile.getHeaders().isEmpty());
        assertTrue(profile.getOptions().isEmpty());
    }

    private static ProviderProfile profile(List<String> models, Map<String, String> headers,
                                           Map<String, String> options) {
        return new ProviderProfile("profile-1", "Example", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                "secret-ref-1", "model-a", models, headers, options);
    }

    private static void assertInvalidName(String name) {
        try {
            new ProviderProfile("profile-1", name, ProviderType.OPENAI_COMPATIBLE,
                    ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                    null, null, null, null, null);
            fail("blank name must be rejected: [" + name + "]");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
            fail("expected " + expected.getSimpleName());
        } catch (Throwable actual) {
            if (!expected.isInstance(actual)) {
                throw actual;
            }
        }
    }
}