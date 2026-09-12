package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

/**
 * Migrates the persisted Provider Profile payload to the current schema without mutating
 * unrelated settings such as the M02 enabled flag.
 *
 * <p>Malformed or older data is repaired to an empty current state and reported to the
 * repository for one write-back. A schema from a newer app version is rejected and never
 * overwritten.</p>
 */
public final class ProviderProfileMigration {
    private final ProviderProfileSerializer mSerializer;

    public ProviderProfileMigration(ProviderProfileSerializer serializer) {
        mSerializer = serializer != null ? serializer : new ProviderProfileSerializer();
    }

    public MigrationResult migrate(String rawPayload) {
        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            return repairToEmpty();
        }

        final int version;
        try {
            version = mSerializer.readSchemaVersion(rawPayload);
        } catch (ProviderProfileSerializer.InvalidPayloadException e) {
            return repairToEmpty();
        }

        if (version > AiSubtitleSchema.CURRENT_VERSION) {
            throw new FutureSchemaException(version);
        }

        if (version < AiSubtitleSchema.CURRENT_VERSION) {
            // Version zero is the pre-provider M02 store: no profile payload existed yet.
            return repairToEmpty();
        }

        try {
            ProviderProfileSerializer.DecodedState decoded =
                    mSerializer.decodeCurrent(rawPayload);
            return new MigrationResult(decoded.getState(), decoded.wasRepaired());
        } catch (ProviderProfileSerializer.InvalidPayloadException e) {
            return repairToEmpty();
        }
    }

    private static MigrationResult repairToEmpty() {
        return new MigrationResult(ProviderProfileState.empty(), true);
    }

    public static final class MigrationResult {
        private final ProviderProfileState mState;
        private final boolean mRequiresWrite;

        MigrationResult(ProviderProfileState state, boolean requiresWrite) {
            mState = state;
            mRequiresWrite = requiresWrite;
        }

        public ProviderProfileState getState() {
            return mState;
        }

        public boolean requiresWrite() {
            return mRequiresWrite;
        }
    }

    public static final class FutureSchemaException extends RuntimeException {
        private final int mSchemaVersion;

        FutureSchemaException(int schemaVersion) {
            super("Provider Profile schema " + schemaVersion + " is newer than supported schema "
                    + AiSubtitleSchema.CURRENT_VERSION);
            mSchemaVersion = schemaVersion;
        }

        public int getSchemaVersion() {
            return mSchemaVersion;
        }
    }
}