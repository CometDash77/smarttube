package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM tests for the version-one Provider Profile payload.
 */
public class ProviderProfileSerializerTest {
    private final ProviderProfileSerializer mSerializer = new ProviderProfileSerializer();

    @Test
    public void roundTripsProfilesAndSelections() {
        ProviderProfile first = profile("profile-a", "OpenAI", "model-a");
        ProviderProfile second = profile("profile-b", "Anthropic", "model-b");
        ProviderProfileState state = new ProviderProfileState(
                Arrays.asList(first, second), "profile-b", "profile-a");

        ProviderProfileSerializer.DecodedState decoded = mSerializer.decodeCurrent(
                mSerializer.serialize(state));

        assertFalse(decoded.wasRepaired());
        assertEquals(state, decoded.getState());
    }

    @Test
    public void serializedPayloadCarriesReferenceButNoCredentialField() {
        ProviderProfile profile = profile("profile-a", "OpenAI", "model-a");
        String json = mSerializer.serialize(new ProviderProfileState(
                Collections.singletonList(profile), "profile-a", "profile-a"));

        assertTrue(json.contains("\"credentialReference\":\"credential-a\""));
        assertFalse(json.contains("\"apiKey\""));
        assertFalse(json.contains("sk-live-secret"));
    }

    @Test
    public void malformedJsonIsRejected() {
        try {
            mSerializer.decodeCurrent("{not-json");
            fail("malformed json must be rejected");
        } catch (ProviderProfileSerializer.InvalidPayloadException expected) {
            // expected
        }
    }

    @Test
    public void invalidProfileIsSkippedAndMarkedForRepairWithoutDiscardingValidProfiles() {
        String json = "{"
                + "\"schemaVersion\":1,"
                + "\"profiles\":["
                + "{\"id\":\"good\",\"name\":\"Good\",\"providerType\":\"OPENAI_COMPATIBLE\","
                + "\"protocol\":\"OPENAI_CHAT_COMPLETIONS\",\"baseUrl\":\"https://api.example.com/v1\","
                + "\"credentialReference\":\"credential-good\",\"modelId\":\"model-good\"},"
                + "{\"id\":\"bad\",\"name\":\"Bad\",\"providerType\":\"NOT_A_PROVIDER\","
                + "\"protocol\":\"OPENAI_CHAT_COMPLETIONS\",\"baseUrl\":\"https://api.example.com/v1\"}"
                + "],"
                + "\"selectedProfileId\":\"good\","
                + "\"defaultProfileId\":\"good\""
                + "}";

        ProviderProfileSerializer.DecodedState decoded = mSerializer.decodeCurrent(json);

        assertTrue(decoded.wasRepaired());
        assertEquals(1, decoded.getState().getProfiles().size());
        assertEquals("good", decoded.getState().getProfiles().get(0).getId());
    }

    @Test
    public void schemaVersionIsReadableBeforeDecodingProfiles() {
        assertEquals(4, mSerializer.readSchemaVersion("{\"schemaVersion\":4,\"profiles\":[]}"));
    }

    private static ProviderProfile profile(String id, String name, String modelId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Example", id);
        Map<String, String> options = new LinkedHashMap<>();
        options.put("mode", "normal");
        return new ProviderProfile(id, name, ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                "credential-" + id.substring(id.length() - 1), modelId,
                Arrays.asList(modelId, modelId + "-fallback"), headers, options);
    }

    @Test
    public void invalidOptionalCollectionShapeIsMarkedForRepair() {
        String json = "{"
                + "\"schemaVersion\":1,"
                + "\"profiles\":[{\"id\":\"profile-a\",\"name\":\"OpenAI\",\"providerType\":\"OPENAI_COMPATIBLE\","
                + "\"protocol\":\"OPENAI_CHAT_COMPLETIONS\",\"baseUrl\":\"https://api.example.com/v1\","
                + "\"availableModelIds\":\"not-an-array\",\"headers\":[1,2],\"options\":7}],"
                + "\"selectedProfileId\":\"profile-a\",\"defaultProfileId\":\"profile-a\"}";

        ProviderProfileSerializer.DecodedState decoded = mSerializer.decodeCurrent(json);

        assertTrue(decoded.wasRepaired());
        assertTrue(decoded.getState().getProfiles().get(0).getAvailableModelIds().isEmpty());
        assertTrue(decoded.getState().getProfiles().get(0).getHeaders().isEmpty());
        assertTrue(decoded.getState().getProfiles().get(0).getOptions().isEmpty());
    }
}
