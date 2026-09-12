package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;

/**
 * Stores provider credentials outside serializable Provider Profile data.
 *
 * <p>The reference is non-secret and may be persisted with a profile. Reads and writes never
 * expose the full secret through exceptions or {@code toString()}.</p>
 */
public interface SecretStore {
    String get(String reference);

    void put(String reference, String secret);

    void delete(String reference);

    /** No-op implementation for profile repositories that do not own credential storage. */
    SecretStore NONE = new SecretStore() {
        @Override
        public String get(String reference) {
            return null;
        }

        @Override
        public void put(String reference, String secret) {
        }

        @Override
        public void delete(String reference) {
        }
    };

    enum ProtectionLevel {
        KEYSTORE_AES_256_GCM,
        APP_PRIVATE_PLAINTEXT
    }

    enum FailureReason {
        MISSING,
        UNAVAILABLE,
        INVALIDATED,
        IO
    }

    /** Safe display helper; it never returns the complete input. */
    final class Masking {
        private static final String HIDDEN = "••••••••";

        private Masking() {
        }

        public static String mask(String secret) {
            if (secret == null || secret.isEmpty()) {
                return "";
            }
            if (secret.length() <= 8) {
                return HIDDEN;
            }
            return secret.substring(0, 4) + "…" + secret.substring(secret.length() - 4);
        }
    }

    /**
     * Safe credential failure. Raw platform exceptions and secret-bearing values are not
     * retained as causes or messages.
     */
    final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final FailureReason mReason;

        public Failure(FailureReason reason) {
            super(messageFor(reason));
            mReason = reason != null ? reason : FailureReason.IO;
        }

        public FailureReason getReason() {
            return mReason;
        }

        public TranslationFailure getFailure() {
            return new TranslationFailure(TranslationFailureCategory.AUTH, getMessage());
        }

        @Override
        public String toString() {
            return "SecretStore.Failure{reason=" + mReason + "}";
        }

        private static String messageFor(FailureReason reason) {
            if (reason == null) {
                return "Credential storage failed.";
            }
            switch (reason) {
                case MISSING:
                    return "Provider credential is not configured.";
                case INVALIDATED:
                    return "Stored provider credential can no longer be read; re-enter it.";
                case UNAVAILABLE:
                    return "Secure credential storage is unavailable on this device.";
                case IO:
                default:
                    return "Credential storage failed.";
            }
        }
    }
}
