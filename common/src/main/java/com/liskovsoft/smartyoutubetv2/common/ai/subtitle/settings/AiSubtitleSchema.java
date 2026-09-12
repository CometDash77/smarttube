package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

/**
 * Version and storage-key constants for the feature-owned AI Subtitle settings store.
 */
public final class AiSubtitleSchema {
    public static final int LEGACY_VERSION = 0;
    public static final int CURRENT_VERSION = 1;
    public static final String PROVIDER_PROFILES_KEY = "provider_profiles";

    static final String JSON_SCHEMA_VERSION = "schemaVersion";
    static final String JSON_PROFILES = "profiles";
    static final String JSON_SELECTED_PROFILE_ID = "selectedProfileId";
    static final String JSON_DEFAULT_PROFILE_ID = "defaultProfileId";
    static final String JSON_ID = "id";
    static final String JSON_NAME = "name";
    static final String JSON_PROVIDER_TYPE = "providerType";
    static final String JSON_PROTOCOL = "protocol";
    static final String JSON_BASE_URL = "baseUrl";
    static final String JSON_CREDENTIAL_REFERENCE = "credentialReference";
    static final String JSON_MODEL_ID = "modelId";
    static final String JSON_AVAILABLE_MODEL_IDS = "availableModelIds";
    static final String JSON_HEADERS = "headers";
    static final String JSON_OPTIONS = "options";

    private AiSubtitleSchema() {
    }
}