package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM tests for the Android store's storage/codec boundary.
 */
public class AndroidSecretStoreTest {
    @Test
    public void protectionLevelFollowsTheDocumentedApiPolicy() {
        assertEquals(SecretStore.ProtectionLevel.APP_PRIVATE_PLAINTEXT,
                AndroidSecretStore.protectionLevelForSdk(17));
        assertEquals(SecretStore.ProtectionLevel.APP_PRIVATE_PLAINTEXT,
                AndroidSecretStore.protectionLevelForSdk(22));
        assertEquals(SecretStore.ProtectionLevel.KEYSTORE_AES_256_GCM,
                AndroidSecretStore.protectionLevelForSdk(23));
    }


    @Test
    public void saveReadUpdateDeleteAndRestartUseOnlyTheEncodedValue() {
        MemoryStorage storage = new MemoryStorage();
        FakeCodec codec = new FakeCodec();
        AndroidSecretStore store = new AndroidSecretStore(storage, codec);

        store.put("profile-a", "credential-one");
        assertEquals("credential-one", store.get("profile-a"));
        assertFalse(storage.values.get(AndroidSecretStore.storageKey("profile-a"))
                .contains("credential-one"));

        store.put("profile-a", "credential-two");
        assertEquals("credential-two", new AndroidSecretStore(storage, codec).get("profile-a"));

        store.delete("profile-a");
        assertNull(store.get("profile-a"));
        store.delete("profile-a");
        assertTrue(storage.values.isEmpty());
    }

    @Test
    public void missingValueReturnsNull() {
        AndroidSecretStore store = new AndroidSecretStore(new MemoryStorage(), new FakeCodec());

        assertNull(store.get("profile-missing"));
    }

    @Test
    public void readingLegacyValueMigratesThatReferenceAndRemovesLegacyRecord() {
        MemoryStorage currentStorage = new MemoryStorage();
        MemoryStorage legacyStorage = new MemoryStorage();
        FakeCodec codec = new FakeCodec();
        String key = AndroidSecretStore.storageKey("profile-a");
        legacyStorage.values.put(key, codec.encode("profile-a", "credential-one"));
        AndroidSecretStore store = new AndroidSecretStore(currentStorage, legacyStorage, codec);

        assertEquals("credential-one", store.get("profile-a"));
        assertEquals(codec.encode("profile-a", "credential-one"), currentStorage.values.get(key));
        assertFalse(legacyStorage.values.containsKey(key));
    }

    @Test
    public void deleteRemovesCurrentAndLegacyRecordsForReference() {
        MemoryStorage currentStorage = new MemoryStorage();
        MemoryStorage legacyStorage = new MemoryStorage();
        String key = AndroidSecretStore.storageKey("profile-a");
        currentStorage.values.put(key, "encoded-current");
        legacyStorage.values.put(key, "encoded-legacy");
        AndroidSecretStore store = new AndroidSecretStore(currentStorage, legacyStorage, new FakeCodec());

        store.delete("profile-a");

        assertFalse(currentStorage.values.containsKey(key));
        assertFalse(legacyStorage.values.containsKey(key));
    }

    @Test
    public void decodeFailureNormalizesToInvalidatedAuthFailure() {
        MemoryStorage storage = new MemoryStorage();
        FakeCodec codec = new FakeCodec();
        codec.failDecode = true;
        storage.values.put(AndroidSecretStore.storageKey("profile-a"), "encoded-value");
        AndroidSecretStore store = new AndroidSecretStore(storage, codec);

        try {
            store.get("profile-a");
            fail("decode failure must be surfaced");
        } catch (SecretStore.Failure failure) {
            assertEquals(SecretStore.FailureReason.INVALIDATED, failure.getReason());
            assertEquals(TranslationFailureCategory.AUTH, failure.getFailure().getCategory());
        }
    }

    @Test
    public void storageIoFailureIsSafeAndNormalized() {
        MemoryStorage storage = new MemoryStorage();
        storage.failRead = true;
        AndroidSecretStore store = new AndroidSecretStore(storage, new FakeCodec());

        try {
            store.get("profile-a");
            fail("storage failure must be surfaced");
        } catch (SecretStore.Failure failure) {
            assertEquals(SecretStore.FailureReason.IO, failure.getReason());
            assertEquals(TranslationFailureCategory.AUTH, failure.getFailure().getCategory());
            assertFalse(failure.getMessage().contains("credential"));
        }
    }

    @Test
    public void toStringNeverContainsStoredSecretMaterial() {
        AndroidSecretStore store = new AndroidSecretStore(new MemoryStorage(), new FakeCodec());
        store.put("profile-a", "synthetic-credential-value");

        assertFalse(store.toString().contains("synthetic-credential-value"));
        assertEquals(SecretStore.ProtectionLevel.KEYSTORE_AES_256_GCM,
                store.getProtectionLevel());
    }

    @Test
    public void blankReferencesAndSecretsAreRejected() {
        AndroidSecretStore store = new AndroidSecretStore(new MemoryStorage(), new FakeCodec());

        assertRejected(store, " ", "credential");
        assertRejected(store, "profile-a", " ");
    }

    private static void assertRejected(AndroidSecretStore store, String reference, String secret) {
        try {
            store.put(reference, secret);
            fail("blank secret input must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static final class FakeCodec implements AndroidSecretStore.Codec {
        private boolean failDecode;

        @Override
        public String encode(String reference, String plaintext) {
            return "encoded:" + reference + ":" + new StringBuilder(plaintext).reverse();
        }

        @Override
        public String decode(String reference, String encoded) {
            if (failDecode) {
                throw new SecretStore.Failure(SecretStore.FailureReason.INVALIDATED);
            }
            String prefix = "encoded:" + reference + ":";
            if (!encoded.startsWith(prefix)) {
                throw new SecretStore.Failure(SecretStore.FailureReason.INVALIDATED);
            }
            return new StringBuilder(encoded.substring(prefix.length())).reverse().toString();
        }

        @Override
        public SecretStore.ProtectionLevel getProtectionLevel() {
            return SecretStore.ProtectionLevel.KEYSTORE_AES_256_GCM;
        }
    }

    private static final class MemoryStorage implements AndroidSecretStore.Storage {
        private final Map<String, String> values = new LinkedHashMap<>();
        private boolean failRead;

        @Override
        public String read(String key) {
            if (failRead) {
                throw new IllegalStateException("storage unavailable");
            }
            return values.get(key);
        }

        @Override
        public void write(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }
}
