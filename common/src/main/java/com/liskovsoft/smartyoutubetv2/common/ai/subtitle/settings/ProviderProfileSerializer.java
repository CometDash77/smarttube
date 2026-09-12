package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the current version-one Provider Profile JSON envelope.
 *
 * <p>The serializer never defines a credential field. A profile carries only an opaque
 * credential reference.</p>
 */
public final class ProviderProfileSerializer {
    public String serialize(ProviderProfileState state) {
        ProviderProfileState safeState = state != null ? state : ProviderProfileState.empty();
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendField(json, AiSubtitleSchema.JSON_SCHEMA_VERSION);
        json.append(AiSubtitleSchema.CURRENT_VERSION);
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_PROFILES);
        appendProfiles(json, safeState.getProfiles());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_SELECTED_PROFILE_ID);
        appendNullableString(json, safeState.getSelectedProfileId());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_DEFAULT_PROFILE_ID);
        appendNullableString(json, safeState.getDefaultProfileId());
        json.append('}');
        return json.toString();
    }

    public int readSchemaVersion(String json) {
        Map<String, Object> root = parseRoot(json);
        Integer version = asInt(root.get(AiSubtitleSchema.JSON_SCHEMA_VERSION));
        if (version == null) {
            throw new InvalidPayloadException("missing schemaVersion");
        }
        return version;
    }

    public DecodedState decodeCurrent(String json) {
        Map<String, Object> root = parseRoot(json);
        Integer version = asInt(root.get(AiSubtitleSchema.JSON_SCHEMA_VERSION));
        if (version == null || version != AiSubtitleSchema.CURRENT_VERSION) {
            throw new InvalidPayloadException("expected schemaVersion "
                    + AiSubtitleSchema.CURRENT_VERSION + " but found " + version);
        }

        boolean repaired = false;
        Object profilesValue = root.get(AiSubtitleSchema.JSON_PROFILES);
        List<ProviderProfile> profiles = new ArrayList<>();
        if (profilesValue == null) {
            repaired = true;
        } else if (!(profilesValue instanceof List)) {
            repaired = true;
        } else {
            for (Object value : (List<?>) profilesValue) {
                ProfileReadResult result = readProfile(value);
                if (!result.isValid()) {
                    repaired = true;
                } else {
                    profiles.add(result.getProfile());
                    repaired |= result.wasRepaired();
                }
            }
        }

        ReadResult<String> selected = readOptionalString(
                root.get(AiSubtitleSchema.JSON_SELECTED_PROFILE_ID));
        repaired |= selected.wasRepaired();
        ReadResult<String> defaultId = readOptionalString(
                root.get(AiSubtitleSchema.JSON_DEFAULT_PROFILE_ID));
        repaired |= defaultId.wasRepaired();

        return new DecodedState(new ProviderProfileState(
                profiles, selected.getValue(), defaultId.getValue()), repaired);
    }

    private static Map<String, Object> parseRoot(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new InvalidPayloadException("empty json");
        }
        try {
            Object value = Helpers.convertToObj(json);
            if (!(value instanceof Map)) {
                throw new InvalidPayloadException("root must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) value;
            return root;
        } catch (InvalidPayloadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidPayloadException("malformed json", e);
        }
    }

    private static ProfileReadResult readProfile(Object value) {
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return ProfileReadResult.invalid();
        }

        boolean repaired = false;
        ReadResult<String> id = readOptionalString(map.get(AiSubtitleSchema.JSON_ID));
        repaired |= id.wasRepaired();
        ReadResult<String> name = readOptionalString(map.get(AiSubtitleSchema.JSON_NAME));
        repaired |= name.wasRepaired();
        ReadResult<ProviderType> providerType =
                readProviderType(map.get(AiSubtitleSchema.JSON_PROVIDER_TYPE));
        repaired |= providerType.wasRepaired();
        ReadResult<ProviderProtocol> protocol =
                readProtocol(map.get(AiSubtitleSchema.JSON_PROTOCOL));
        repaired |= protocol.wasRepaired();
        ReadResult<String> baseUrl = readOptionalString(map.get(AiSubtitleSchema.JSON_BASE_URL));
        repaired |= baseUrl.wasRepaired();
        ReadResult<String> credentialReference = readOptionalString(
                map.get(AiSubtitleSchema.JSON_CREDENTIAL_REFERENCE));
        repaired |= credentialReference.wasRepaired();
        ReadResult<String> modelId = readOptionalString(map.get(AiSubtitleSchema.JSON_MODEL_ID));
        repaired |= modelId.wasRepaired();
        ReadResult<List<String>> availableModelIds = readStringList(
                map.get(AiSubtitleSchema.JSON_AVAILABLE_MODEL_IDS));
        repaired |= availableModelIds.wasRepaired();
        ReadResult<Map<String, String>> headers = readStringMap(
                map.get(AiSubtitleSchema.JSON_HEADERS));
        repaired |= headers.wasRepaired();
        ReadResult<Map<String, String>> options = readStringMap(
                map.get(AiSubtitleSchema.JSON_OPTIONS));
        repaired |= options.wasRepaired();

        try {
            ProviderProfile profile = new ProviderProfile(
                    id.getValue(), name.getValue(), providerType.getValue(),
                    protocol.getValue(), baseUrl.getValue(), credentialReference.getValue(),
                    modelId.getValue(), availableModelIds.getValue(), headers.getValue(),
                    options.getValue());
            return ProfileReadResult.valid(profile, repaired);
        } catch (IllegalArgumentException e) {
            return ProfileReadResult.invalid();
        }
    }

    private static ReadResult<List<String>> readStringList(Object value) {
        if (value == null) {
            return new ReadResult<>(null, false);
        }
        if (!(value instanceof List)) {
            return new ReadResult<>(null, true);
        }

        List<String> result = new ArrayList<>();
        boolean repaired = false;
        for (Object item : (List<?>) value) {
            String string = asString(item);
            if (string == null || string.trim().isEmpty()) {
                repaired = true;
            } else {
                result.add(string);
            }
        }
        return new ReadResult<>(result, repaired);
    }

    private static ReadResult<Map<String, String>> readStringMap(Object value) {
        if (value == null) {
            return new ReadResult<>(null, false);
        }
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return new ReadResult<>(null, true);
        }

        Map<String, String> result = new LinkedHashMap<>();
        boolean repaired = false;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String string = asString(entry.getValue());
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()
                    || string == null || string.trim().isEmpty()) {
                repaired = true;
            } else {
                result.put(entry.getKey(), string);
            }
        }
        return new ReadResult<>(result, repaired);
    }

    private static ReadResult<ProviderType> readProviderType(Object value) {
        ReadResult<String> string = readOptionalString(value);
        if (string.getValue() == null) {
            return new ReadResult<>(null, string.wasRepaired());
        }
        try {
            return new ReadResult<>(ProviderType.valueOf(string.getValue()), string.wasRepaired());
        } catch (IllegalArgumentException e) {
            return new ReadResult<>(null, true);
        }
    }

    private static ReadResult<ProviderProtocol> readProtocol(Object value) {
        ReadResult<String> string = readOptionalString(value);
        if (string.getValue() == null) {
            return new ReadResult<>(null, string.wasRepaired());
        }
        try {
            return new ReadResult<>(ProviderProtocol.valueOf(string.getValue()), string.wasRepaired());
        } catch (IllegalArgumentException e) {
            return new ReadResult<>(null, true);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static Integer asInt(Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        double number = ((Number) value).doubleValue();
        int integer = (int) number;
        return number == integer ? integer : null;
    }

    private static ReadResult<String> readOptionalString(Object value) {
        if (value == null) {
            return new ReadResult<>(null, false);
        }
        String string = asString(value);
        if (string == null || string.trim().isEmpty()) {
            return new ReadResult<>(null, true);
        }
        return new ReadResult<>(string, false);
    }

    private static void appendProfiles(StringBuilder json, List<ProviderProfile> profiles) {
        json.append('[');
        boolean first = true;
        for (ProviderProfile profile : profiles) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendProfile(json, profile);
        }
        json.append(']');
    }

    private static void appendProfile(StringBuilder json, ProviderProfile profile) {
        json.append('{');
        appendField(json, AiSubtitleSchema.JSON_ID);
        appendNullableString(json, profile.getId());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_NAME);
        appendString(json, profile.getName());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_PROVIDER_TYPE);
        appendString(json, profile.getProviderType().name());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_PROTOCOL);
        appendString(json, profile.getProtocol().name());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_BASE_URL);
        appendString(json, profile.getBaseUrl());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_CREDENTIAL_REFERENCE);
        appendNullableString(json, profile.getSecretReference());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_MODEL_ID);
        appendNullableString(json, profile.getModelId());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_AVAILABLE_MODEL_IDS);
        appendStringList(json, profile.getAvailableModelIds());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_HEADERS);
        appendStringMap(json, profile.getHeaders());
        json.append(',');
        appendField(json, AiSubtitleSchema.JSON_OPTIONS);
        appendStringMap(json, profile.getOptions());
        json.append('}');
    }

    private static void appendStringList(StringBuilder json, List<String> values) {
        json.append('[');
        boolean first = true;
        for (String value : values) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendString(json, value);
        }
        json.append(']');
    }

    private static void appendStringMap(StringBuilder json, Map<String, String> values) {
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendString(json, entry.getKey());
            json.append(':');
            appendString(json, entry.getValue());
        }
        json.append('}');
    }

    private static void appendField(StringBuilder json, String name) {
        appendString(json, name);
        json.append(':');
    }

    private static void appendNullableString(StringBuilder json, String value) {
        if (value == null) {
            json.append("null");
        } else {
            appendString(json, value);
        }
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                case '\b':
                    json.append("\\b");
                    break;
                case '\f':
                    json.append("\\f");
                    break;
                case '\n':
                    json.append("\\n");
                    break;
                case '\r':
                    json.append("\\r");
                    break;
                case '\t':
                    json.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                    break;
            }
        }
        json.append('"');
    }

    public static final class DecodedState {
        private final ProviderProfileState mState;
        private final boolean mRepaired;

        DecodedState(ProviderProfileState state, boolean repaired) {
            mState = state;
            mRepaired = repaired;
        }

        public ProviderProfileState getState() {
            return mState;
        }

        public boolean wasRepaired() {
            return mRepaired;
        }
    }

    private static final class ReadResult<T> {
        private final T mValue;
        private final boolean mRepaired;

        ReadResult(T value, boolean repaired) {
            mValue = value;
            mRepaired = repaired;
        }

        T getValue() {
            return mValue;
        }

        boolean wasRepaired() {
            return mRepaired;
        }
    }

    private static final class ProfileReadResult {
        private final ProviderProfile mProfile;
        private final boolean mRepaired;
        private final boolean mValid;

        ProfileReadResult(ProviderProfile profile, boolean repaired, boolean valid) {
            mProfile = profile;
            mRepaired = repaired;
            mValid = valid;
        }

        static ProfileReadResult valid(ProviderProfile profile, boolean repaired) {
            return new ProfileReadResult(profile, repaired, true);
        }

        static ProfileReadResult invalid() {
            return new ProfileReadResult(null, true, false);
        }

        ProviderProfile getProfile() {
            return mProfile;
        }

        boolean wasRepaired() {
            return mRepaired;
        }

        boolean isValid() {
            return mValid;
        }
    }

    public static final class InvalidPayloadException extends RuntimeException {
        InvalidPayloadException(String message) {
            super(message);
        }

        InvalidPayloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
