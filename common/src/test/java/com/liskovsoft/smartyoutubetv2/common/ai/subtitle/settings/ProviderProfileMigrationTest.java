package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM tests for profile-payload migration and fail-closed future handling.
 */
public class ProviderProfileMigrationTest {
    private final ProviderProfileSerializer mSerializer = new ProviderProfileSerializer();
    private final ProviderProfileMigration mMigration = new ProviderProfileMigration(mSerializer);

    @Test
    public void emptyStoreMigratesToEmptyCurrentStateAndRequestsWrite() {
        ProviderProfileMigration.MigrationResult result = mMigration.migrate(null);

        assertTrue(result.getState().getProfiles().isEmpty());
        assertTrue(result.requiresWrite());
    }

    @Test
    public void versionZeroLegacyPayloadMigratesAndRequestsWrite() {
        ProviderProfileMigration.MigrationResult result = mMigration.migrate(
                "{\"schemaVersion\":0,\"profiles\":[]}");

        assertTrue(result.getState().getProfiles().isEmpty());
        assertTrue(result.requiresWrite());
    }

    @Test
    public void validCurrentPayloadDoesNotRequestWrite() {
        ProviderProfile profile = new ProviderProfile("profile-a", "OpenAI",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.com/v1", "credential-a", "model-a",
                Collections.singletonList("model-a"), null, null);
        ProviderProfileState state = new ProviderProfileState(
                Collections.singletonList(profile), "profile-a", "profile-a");

        ProviderProfileMigration.MigrationResult result = mMigration.migrate(
                mSerializer.serialize(state));

        assertEquals(state, result.getState());
        assertFalse(result.requiresWrite());
    }

    @Test
    public void corruptPayloadRepairsToEmptyStateAndRequestsWrite() {
        ProviderProfileMigration.MigrationResult result = mMigration.migrate("{broken");

        assertTrue(result.getState().getProfiles().isEmpty());
        assertTrue(result.requiresWrite());
    }

    @Test
    public void futureSchemaIsRejectedWithoutReturningRepresentableState() {
        try {
            mMigration.migrate("{\"schemaVersion\":99,\"profiles\":[]}");
            fail("future schema must be rejected");
        } catch (ProviderProfileMigration.FutureSchemaException expected) {
            assertEquals(99, expected.getSchemaVersion());
        }
    }

    @Test
    public void invalidProfileInCurrentPayloadIsRepairedAndRequestsWrite() {
        String payload = "{"
                + "\"schemaVersion\":1,"
                + "\"profiles\":[{\"id\":\"bad\",\"name\":\"Bad\",\"providerType\":\"UNKNOWN\","
                + "\"protocol\":\"OPENAI_CHAT_COMPLETIONS\",\"baseUrl\":\"https://api.example.com/v1\"}],"
                + "\"selectedProfileId\":\"bad\",\"defaultProfileId\":\"bad\"}";

        ProviderProfileMigration.MigrationResult result = mMigration.migrate(payload);

        assertTrue(result.getState().getProfiles().isEmpty());
        assertTrue(result.requiresWrite());
    }
}