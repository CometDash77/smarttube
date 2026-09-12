package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

/**
 * Dedicated versioned preference store for AI subtitle settings.
 *
 * <p>The M02 enable switch remains in this dedicated named preference store. Provider
 * Profiles are stored as versioned, non-secret JSON in SmartTube's active app-profile data,
 * so switching app profiles switches the visible profiles without disturbing the enable
 * flag.</p>
 */
public class AiSubtitleData extends SharedPreferencesBase
        implements ProviderProfileRepository.Store {
    private static final String ENABLED = "enabled";
    private static AiSubtitleData sInstance;
    private static Context sContext;

    private AiSubtitleData(Context context) {
        super(context, AiSubtitleData.class.getName());
    }

    public static synchronized AiSubtitleData instance(Context context) {
        Context appContext = context.getApplicationContext();

        if (sInstance == null || sContext != appContext) {
            sInstance = new AiSubtitleData(appContext);
            sContext = appContext;
        }

        return sInstance;
    }

    public ProviderProfileRepository providerProfiles() {
        return new ProviderProfileRepository(this, secrets());
    }

    public SecretStore secrets() {
        return new AndroidSecretStore(getContext());
    }

    @Override
    public String read() {
        return AppPrefs.instance(getContext())
                .getProfileData(AiSubtitleSchema.PROVIDER_PROFILES_KEY);
    }

    @Override
    public void write(String payload) {
        AppPrefs.instance(getContext())
                .setProfileData(AiSubtitleSchema.PROVIDER_PROFILES_KEY, payload);
    }

    public boolean isEnabled() {
        return getBoolean(ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        putBoolean(ENABLED, enabled);
    }

    @VisibleForTesting
    static synchronized void resetInstanceForTesting() {
        sInstance = null;
        sContext = null;
    }
}
