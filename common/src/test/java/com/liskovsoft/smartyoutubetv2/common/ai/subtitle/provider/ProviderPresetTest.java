package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests for editable Provider presets and capabilities.
 */
public class ProviderPresetTest {
    @Test
    public void allFiveProviderTypesMapToExactlyTwoProtocols() {
        assertPreset(ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        assertPreset(ProviderType.ANTHROPIC_COMPATIBLE,
                ProviderProtocol.ANTHROPIC_MESSAGES);
        assertPreset(ProviderType.OPENROUTER,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        assertPreset(ProviderType.DEEPSEEK,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        assertPreset(ProviderType.MIMO,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
    }

    @Test
    public void presetsProvideEditableDefaultsRatherThanCapabilityClaims() {
        ProviderPreset preset = ProviderPreset.forType(ProviderType.OPENROUTER);

        assertTrue(preset.getCapabilities().supportsModelDiscovery());
        assertTrue(preset.getCapabilities().supportsManualModel());
        assertTrue(preset.getCapabilities().supportsCustomBaseUrl());
        assertTrue(preset.getCapabilities().supportsCustomHeaders());
        assertEquals("https://openrouter.ai/api/v1", preset.getBaseUrl());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("HTTP-Referer", "https://example.invalid");
        ProviderProfile profile = preset.createDraft("Custom OpenRouter", "manual-model",
                "https://proxy.example/v1", headers);

        assertEquals("https://proxy.example/v1", profile.getBaseUrl());
        assertEquals("https://example.invalid", profile.getHeaders().get("HTTP-Referer"));
        assertEquals("manual-model", profile.getModelId());
        assertEquals(ProviderType.OPENROUTER, profile.getProviderType());
        assertEquals(ProviderProtocol.OPENAI_CHAT_COMPLETIONS, profile.getProtocol());
    }

    @Test
    public void presetsDoNotExposeUnsupportedProviderTypes() {
        for (ProviderType type : ProviderType.values()) {
            ProviderPreset preset = ProviderPreset.forType(type);

            assertFalse(preset.getDisplayName().trim().isEmpty());
            assertFalse(preset.getBaseUrl().trim().isEmpty());
            assertEquals(type, preset.getType());
        }
    }

    private static void assertPreset(ProviderType type, ProviderProtocol protocol) {
        ProviderPreset preset = ProviderPreset.forType(type);

        assertEquals(type, preset.getType());
        assertEquals(protocol, preset.getProtocol());
        assertFalse(preset.getDisplayName().trim().isEmpty());
        assertFalse(preset.getBaseUrl().trim().isEmpty());
        assertTrue(preset.getCapabilities().supportsModelDiscovery());
        assertTrue(preset.getCapabilities().supportsManualModel());
    }
}
