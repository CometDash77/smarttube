package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRepository;

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
    private static final String TARGET_LANGUAGE = "target_language";
    private static final String DISPLAY_MODE = "display_mode";
    public static final String DEFAULT_TARGET_LANGUAGE = "zh";
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

    public PromptRepository prompts() {
        return new PromptRepository(new PromptRepository.Store() {
            @Override
            public String read() {
                return AppPrefs.instance(getContext()).getProfileData(AiSubtitleSchema.PROMPT_PROFILES_KEY);
            }

            @Override
            public void write(String payload) {
                AppPrefs.instance(getContext()).setProfileData(AiSubtitleSchema.PROMPT_PROFILES_KEY, payload);
            }
        });
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

    public String getTargetLanguage() {
        return getString(TARGET_LANGUAGE, DEFAULT_TARGET_LANGUAGE);
    }

    public void setTargetLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("language must not be blank");
        }
        putString(TARGET_LANGUAGE, language.trim());
    }

    public AiSubtitleDisplayMode getDisplayMode() {
        String value = getString(DISPLAY_MODE, AiSubtitleDisplayMode.BILINGUAL.name());

        try {
            return AiSubtitleDisplayMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            // Values written by earlier builds were either absent or represented the old
            // hard-coded bilingual output; never let a corrupt value break subtitles.
            return AiSubtitleDisplayMode.BILINGUAL;
        }
    }

    public void setDisplayMode(AiSubtitleDisplayMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        putString(DISPLAY_MODE, mode.name());
    }

    @VisibleForTesting
    static synchronized void resetInstanceForTesting() {
        sInstance = null;
        sContext = null;
    }
}
