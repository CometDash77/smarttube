package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import java.util.ArrayList;
import java.util.List;

/** Adds or repairs the independently authored built-in prompt set. */
public final class PromptMigration {
    private final PromptSerializer mSerializer;

    public PromptMigration(PromptSerializer serializer) { mSerializer = serializer; }

    public MigrationResult migrate(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return new MigrationResult(new PromptState(BuiltInSubtitlePrompts.all(),
                    BuiltInSubtitlePrompts.BASELINE_ID, BuiltInSubtitlePrompts.BASELINE_ID), true);
        }
        PromptSerializer.DecodedState decoded;
        try {
            int version = mSerializer.readSchemaVersion(payload);
            if (version > PromptSerializer.CURRENT_VERSION) throw new FutureSchemaException(version);
            decoded = mSerializer.decodeCurrent(payload);
        } catch (PromptSerializer.InvalidPayloadException e) {
            return new MigrationResult(new PromptState(BuiltInSubtitlePrompts.all(),
                    BuiltInSubtitlePrompts.BASELINE_ID, BuiltInSubtitlePrompts.BASELINE_ID), true);
        }
        List<PromptProfile> profiles = new ArrayList<>();
        boolean foundBaseline = false;
        boolean foundIndexed = false;
        for (PromptProfile builtIn : BuiltInSubtitlePrompts.all()) {
            profiles.add(builtIn);
        }
        boolean repaired = decoded.wasRepaired();
        for (PromptProfile profile : decoded.getState().getProfiles()) {
            if (isBuiltInId(profile.getId())) {
                foundBaseline |= BuiltInSubtitlePrompts.BASELINE_ID.equals(profile.getId());
                foundIndexed |= BuiltInSubtitlePrompts.INDEXED_ID.equals(profile.getId());
                repaired |= !profile.equals(findBuiltIn(profile.getId()));
            } else {
                profiles.add(profile);
            }
        }
        repaired |= !foundBaseline || !foundIndexed;
        return new MigrationResult(new PromptState(profiles,
                decoded.getState().getSelectedProfileId(), decoded.getState().getDefaultProfileId()), repaired);
    }

    private static boolean isBuiltInId(String id) {
        return BuiltInSubtitlePrompts.BASELINE_ID.equals(id) || BuiltInSubtitlePrompts.INDEXED_ID.equals(id);
    }

    private static PromptProfile findBuiltIn(String id) {
        for (PromptProfile profile : BuiltInSubtitlePrompts.all()) if (id.equals(profile.getId())) return profile;
        return null;
    }

    public static final class MigrationResult {
        private final PromptState mState;
        private final boolean mRequiresWrite;
        MigrationResult(PromptState state, boolean requiresWrite) { mState = state; mRequiresWrite = requiresWrite; }
        public PromptState getState() { return mState; }
        public boolean requiresWrite() { return mRequiresWrite; }
    }

    public static final class FutureSchemaException extends RuntimeException {
        FutureSchemaException(int version) { super("unsupported prompt schema version: " + version); }
    }
}
