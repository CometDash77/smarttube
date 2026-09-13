package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Version-one JSON serializer for non-secret prompt profiles. */
public final class PromptSerializer {
    public static final int CURRENT_VERSION = 1;

    public String serialize(PromptState state) {
        if (state == null) state = new PromptState(null, null, null);
        StringBuilder json = new StringBuilder("{\"schemaVersion\":1,\"profiles\":[");
        boolean first = true;
        for (PromptProfile profile : state.getProfiles()) {
            if (!first) json.append(',');
            first = false;
            json.append('{');
            field(json, "id", profile.getId());
            json.append(','); field(json, "name", profile.getName());
            json.append(','); field(json, "content", profile.getContent());
            json.append(','); json.append("\"version\":").append(profile.getVersion());
            json.append(','); json.append("\"builtIn\":").append(profile.isBuiltIn());
            json.append('}');
        }
        json.append("],\"selectedProfileId\":"); nullable(json, state.getSelectedProfileId());
        json.append(",\"defaultProfileId\":"); nullable(json, state.getDefaultProfileId());
        return json.append('}').toString();
    }

    public int readSchemaVersion(String json) {
        Integer version = asInt(parseRoot(json).get("schemaVersion"));
        if (version == null) throw new InvalidPayloadException("missing schemaVersion");
        return version;
    }

    public DecodedState decodeCurrent(String json) {
        Map<String, Object> root = parseRoot(json);
        Integer version = asInt(root.get("schemaVersion"));
        if (version == null || version != CURRENT_VERSION) {
            throw new InvalidPayloadException("expected schemaVersion " + CURRENT_VERSION);
        }
        boolean repaired = false;
        List<PromptProfile> profiles = new ArrayList<>();
        Object rawProfiles = root.get("profiles");
        if (!(rawProfiles instanceof List)) {
            repaired = true;
        } else {
            for (Object raw : (List<?>) rawProfiles) {
                PromptProfile profile = readProfile(raw);
                if (profile == null) repaired = true;
                else profiles.add(profile);
            }
        }
        String selected = optional(root.get("selectedProfileId"));
        if (root.containsKey("selectedProfileId") && selected == null) repaired = true;
        String defaultId = optional(root.get("defaultProfileId"));
        if (root.containsKey("defaultProfileId") && defaultId == null) repaired = true;
        return new DecodedState(new PromptState(profiles, selected, defaultId), repaired);
    }

    private static PromptProfile readProfile(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<?, ?> map = (Map<?, ?>) raw;
        String id = optional(map.get("id"));
        String name = optional(map.get("name"));
        String content = optional(map.get("content"));
        Integer version = asInt(map.get("version"));
        Boolean builtIn = map.get("builtIn") instanceof Boolean
                ? (Boolean) map.get("builtIn") : Boolean.FALSE;
        if (id == null || name == null || content == null || version == null) return null;
        try {
            return new PromptProfile(id, name, content, version, builtIn);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Object> parseRoot(String json) {
        if (json == null || json.trim().isEmpty()) throw new InvalidPayloadException("empty json");
        try {
            Object value = Helpers.convertToObj(json);
            if (!(value instanceof Map)) throw new InvalidPayloadException("root must be an object");
            @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) value;
            return root;
        } catch (InvalidPayloadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidPayloadException("malformed json", e);
        }
    }

    private static String optional(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
    }

    private static Integer asInt(Object value) {
        if (!(value instanceof Number)) return null;
        double number = ((Number) value).doubleValue();
        int integer = (int) number;
        return number == integer ? integer : null;
    }

    private static void field(StringBuilder json, String name, String value) {
        appendString(json, name); json.append(':'); appendString(json, value);
    }

    private static void nullable(StringBuilder json, String value) {
        if (value == null) json.append("null"); else appendString(json, value);
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default: json.append(c);
            }
        }
        json.append('"');
    }

    public static final class DecodedState {
        private final PromptState mState;
        private final boolean mRepaired;
        DecodedState(PromptState state, boolean repaired) { mState = state; mRepaired = repaired; }
        public PromptState getState() { return mState; }
        public boolean wasRepaired() { return mRepaired; }
    }

    public static final class InvalidPayloadException extends RuntimeException {
        InvalidPayloadException(String message) { super(message); }
        InvalidPayloadException(String message, Throwable cause) { super(message, cause); }
    }
}
