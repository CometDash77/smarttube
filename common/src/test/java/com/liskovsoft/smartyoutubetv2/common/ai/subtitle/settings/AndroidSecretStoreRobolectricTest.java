package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;
import java.io.File;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.support.JdkAwareRobolectricRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * API-floor policy test for the documented plaintext compatibility band.
 */
@RunWith(JdkAwareRobolectricRunner.class)
@Config(sdk = 17)
public class AndroidSecretStoreRobolectricTest {
    @Test
    public void api17UsesExplicitAppPrivatePlaintextFallback() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(AndroidSecretStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        AndroidSecretStore store = new AndroidSecretStore(context);

        assertEquals(SecretStore.ProtectionLevel.APP_PRIVATE_PLAINTEXT,
                store.getProtectionLevel());

        store.put("profile-a", "synthetic-fallback-credential");
        assertEquals("synthetic-fallback-credential", store.get("profile-a"));

        String key = AndroidSecretStore.storageKey("profile-a");
        assertTrue(new File(new File(context.getCacheDir(), "ai-subtitle-secrets"),
                AndroidSecretStore.fileNameForKey(key)).exists());
        assertNull(context.getSharedPreferences(AndroidSecretStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE).getString(key, null));
        assertFalse(store.toString().contains("synthetic-fallback-credential"));
    }

    @Test
    @Config(sdk = 21)
    public void storageDirectoryUsesCacheOnApi17AndNoBackupOnApi21() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals(context.getCacheDir(), AndroidSecretStore.storageDirectoryForSdk(context, 17));
        assertEquals(context.getNoBackupFilesDir(),
                AndroidSecretStore.storageDirectoryForSdk(context, 21));
    }

    @Test
    public void api17MigratesLegacyPreferenceThenClearsIt() {
        Context context = RuntimeEnvironment.getApplication();
        String key = AndroidSecretStore.storageKey("profile-migration");
        context.getSharedPreferences(AndroidSecretStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putString(key, "plain-v1:legacy-credential").commit();
        File secretDirectory = new File(context.getCacheDir(), "ai-subtitle-secrets");
        new File(secretDirectory, AndroidSecretStore.fileNameForKey(key)).delete();
        AndroidSecretStore store = new AndroidSecretStore(context);

        assertEquals("legacy-credential", store.get("profile-migration"));
        assertNull(context.getSharedPreferences(AndroidSecretStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE).getString(key, null));
        assertTrue(new File(secretDirectory, AndroidSecretStore.fileNameForKey(key)).exists());
    }

    @Test
    public void api17CacheCleanupRequiresCredentialReentry() {
        Context context = RuntimeEnvironment.getApplication();
        String key = AndroidSecretStore.storageKey("profile-cache-cleanup");
        File file = new File(new File(context.getCacheDir(), "ai-subtitle-secrets"),
                AndroidSecretStore.fileNameForKey(key));
        AndroidSecretStore store = new AndroidSecretStore(context);

        store.put("profile-cache-cleanup", "cache-credential");
        assertTrue(file.delete());

        assertNull(store.get("profile-cache-cleanup"));
    }
}
