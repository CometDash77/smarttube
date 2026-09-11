package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;

/**
 * Dedicated versioned preference store for AI subtitle settings.
 *
 * <p>M02 persists only the opt-in enable switch, in a dedicated named preference store
 * (the {@code HiddenPrefs} pattern). No provider, model, prompt, or profile data is
 * stored here yet; schema versioning and migrations arrive with M04.</p>
 */
public class AiSubtitleData extends SharedPreferencesBase {
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
