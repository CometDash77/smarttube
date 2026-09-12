package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Android implementation of the ADR-012 credential policy.
 *
 * <p>API 23+ stores AES-256-GCM ciphertext backed by a non-exportable AndroidKeyStore key.
 * API 17–22 uses the explicitly documented app-private plaintext compatibility fallback.</p>
 */
public final class AndroidSecretStore implements SecretStore {
    static final String PREFERENCES_NAME = AndroidSecretStore.class.getName();
    private static final String KEYSTORE_ALIAS = "ai_subtitle_provider_secret_v1";
    private static final String KEY_PREFIX = "secret:";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final Storage mStorage;
    private final Codec mCodec;

    public AndroidSecretStore(Context context) {
        this(new PreferencesStorage(context), createCodec(Build.VERSION.SDK_INT));
    }

    AndroidSecretStore(Storage storage, Codec codec) {
        if (storage == null) {
            throw new IllegalArgumentException("storage must not be null");
        }
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        mStorage = storage;
        mCodec = codec;
    }

    @Override
    public String get(String reference) {
        String key = storageKey(reference);
        String encoded;
        try {
            encoded = mStorage.read(key);
        } catch (RuntimeException e) {
            throw new Failure(FailureReason.IO);
        }
        if (encoded == null) {
            return null;
        }

        try {
            return mCodec.decode(reference, encoded);
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException e) {
            throw new Failure(FailureReason.INVALIDATED);
        }
    }

    @Override
    public void put(String reference, String secret) {
        String key = storageKey(reference);
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("secret must not be blank");
        }

        String encoded;
        try {
            encoded = mCodec.encode(reference, secret);
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException e) {
            throw new Failure(FailureReason.UNAVAILABLE);
        }

        try {
            mStorage.write(key, encoded);
        } catch (RuntimeException e) {
            throw new Failure(FailureReason.IO);
        }
    }

    @Override
    public void delete(String reference) {
        try {
            mStorage.remove(storageKey(reference));
        } catch (RuntimeException e) {
            throw new Failure(FailureReason.IO);
        }
    }

    public ProtectionLevel getProtectionLevel() {
        return mCodec.getProtectionLevel();
    }

    @Override
    public String toString() {
        return "AndroidSecretStore{protection=" + getProtectionLevel() + ", values=hidden}";
    }

    static String storageKey(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        return KEY_PREFIX + reference;
    }

    static ProtectionLevel protectionLevelForSdk(int sdkVersion) {
        return sdkVersion >= Build.VERSION_CODES.M
                ? ProtectionLevel.KEYSTORE_AES_256_GCM
                : ProtectionLevel.APP_PRIVATE_PLAINTEXT;
    }

    @SuppressWarnings("NewApi")
    private static Codec createCodec(int sdkVersion) {
        if (protectionLevelForSdk(sdkVersion) == ProtectionLevel.KEYSTORE_AES_256_GCM) {
            return new KeystoreAesGcmCodec();
        }
        return new PlaintextCodec();
    }

    interface Storage {
        String read(String key);

        void write(String key, String value);

        void remove(String key);
    }

    interface Codec {
        String encode(String reference, String plaintext);

        String decode(String reference, String encoded);

        ProtectionLevel getProtectionLevel();
    }

    private static final class PreferencesStorage implements Storage {
        private final SharedPreferences mPreferences;

        PreferencesStorage(Context context) {
            mPreferences = context.getApplicationContext()
                    .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public String read(String key) {
            return mPreferences.getString(key, null);
        }

        @Override
        public void write(String key, String value) {
            mPreferences.edit().putString(key, value).apply();
        }

        @Override
        public void remove(String key) {
            mPreferences.edit().remove(key).apply();
        }
    }

    private static final class PlaintextCodec implements Codec {
        private static final String PREFIX = "plain-v1:";

        @Override
        public String encode(String reference, String plaintext) {
            return PREFIX + plaintext;
        }

        @Override
        public String decode(String reference, String encoded) {
            if (!encoded.startsWith(PREFIX)) {
                throw new Failure(FailureReason.INVALIDATED);
            }
            return encoded.substring(PREFIX.length());
        }

        @Override
        public ProtectionLevel getProtectionLevel() {
            return ProtectionLevel.APP_PRIVATE_PLAINTEXT;
        }
    }

    @RequiresApi(23)
    private static final class KeystoreAesGcmCodec implements Codec {
        private static final String PREFIX = "keystore-v1:";
        private static final int GCM_TAG_BITS = 128;

        @Override
        public String encode(String reference, String plaintext) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
                cipher.updateAAD(reference.getBytes(UTF_8));
                byte[] ciphertext = cipher.doFinal(plaintext.getBytes(UTF_8));
                return PREFIX
                        + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                        + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            } catch (Failure failure) {
                throw failure;
            } catch (GeneralSecurityException e) {
                throw new Failure(FailureReason.UNAVAILABLE);
            }
        }

        @Override
        public String decode(String reference, String encoded) {
            if (!encoded.startsWith(PREFIX)) {
                throw new Failure(FailureReason.INVALIDATED);
            }

            String[] parts = encoded.substring(PREFIX.length()).split(":", -1);
            if (parts.length != 2) {
                throw new Failure(FailureReason.INVALIDATED);
            }

            try {
                byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
                byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, getExistingKey(),
                        new GCMParameterSpec(GCM_TAG_BITS, iv));
                cipher.updateAAD(reference.getBytes(UTF_8));
                return new String(cipher.doFinal(ciphertext), UTF_8);
            } catch (Failure failure) {
                throw failure;
            } catch (GeneralSecurityException e) {
                throw new Failure(FailureReason.INVALIDATED);
            } catch (IllegalArgumentException e) {
                throw new Failure(FailureReason.INVALIDATED);
            }
        }

        @Override
        public ProtectionLevel getProtectionLevel() {
            return ProtectionLevel.KEYSTORE_AES_256_GCM;
        }

        private static SecretKey getOrCreateKey() throws GeneralSecurityException {
            KeyStore keyStore = loadKeyStore();
            Key existing = keyStore.getKey(KEYSTORE_ALIAS, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
            if (existing != null) {
                throw new UnrecoverableKeyException("unexpected key type");
            }

            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        }

        private static SecretKey getExistingKey() throws GeneralSecurityException {
            KeyStore keyStore = loadKeyStore();
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                throw new UnrecoverableKeyException("missing key");
            }
            Key key = keyStore.getKey(KEYSTORE_ALIAS, null);
            if (!(key instanceof SecretKey)) {
                throw new UnrecoverableKeyException("missing key");
            }
            return (SecretKey) key;
        }

        private static KeyStore loadKeyStore() throws GeneralSecurityException {
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                return keyStore;
            } catch (IOException e) {
                throw new GeneralSecurityException("keystore load failed", e);
            }
        }
    }
}
