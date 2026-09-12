package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.support.JdkAwareRobolectricRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

        String stored = context.getSharedPreferences(AndroidSecretStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE).getString(AndroidSecretStore.storageKey("profile-a"), null);
        assertTrue(stored.contains("synthetic-fallback-credential"));
        assertFalse(store.toString().contains("synthetic-fallback-credential"));
    }
}
