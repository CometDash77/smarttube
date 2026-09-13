package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.OkHttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProfileResolver;

/**
 * Android-facing binding between the stored Provider Profiles and the cue bridge.
 *
 * <p>The bridge singleton is initialized with the currently selected valid profile, and the
 * settings entry point calls {@link #applyToBridge(Context)} after profile changes. Missing,
 * invalid, or failed resolution always becomes Source-Only Fallback.</p>
 */
public final class AiSubtitleRuntime {
    private AiSubtitleRuntime() {
    }

    public static TranslationProfileResolver.Resolution resolve(Context context) {
        AiSubtitleData data = AiSubtitleData.instance(context);
        SecretStore secrets = data.secrets();
        ProviderProfileResolver resolver =
                new ProviderProfileResolver(new OkHttpRequestExecutor(), secrets);
        return new TranslationProfileResolver(data.providerProfiles(), resolver,
                data.prompts(), secrets).resolve(data.getTargetLanguage());
    }

    public static void applyToBridge(Context context) {
        TranslationProfileResolver.Resolution resolved = resolve(context);
        AiSubtitleCueBridge.instance(context).onProviderChanged(
                resolved.getProvider(), resolved.getProfile());
    }
}
