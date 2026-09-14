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

    public static void applySchedulingToBridge(Context context) {
        applySettingsToBridge(AiSubtitleCueBridge.instance(context), AiSubtitleData.instance(context));
    }

    /**
     * Applies the resolved Provider and Prompt plus every stored playback setting, in one pass.
     *
     * <p>This is the single entry point for both the TV settings screens and the phone page: a
     * setting that only reached storage while the live bridge kept the old value is exactly the
     * defect this replaces. Every entry point short-circuits an unchanged value, so calling this
     * with nothing to change is cheap.</p>
     */
    public static void applyToBridge(Context context) {
        AiSubtitleData data = AiSubtitleData.instance(context);
        AiSubtitleCueBridge bridge = AiSubtitleCueBridge.instance(context);
        TranslationProfileResolver.Resolution resolved = resolve(context);

        bridge.onProviderChanged(resolved.getProvider(), resolved.getProfile(), resolved.getPrompt());
        applySettingsToBridge(bridge, data);
    }

    /**
     * Applies every stored playback setting to one live bridge.
     *
     * <p>Segmentation goes first. It is the only setting that decides how the source is cut into
     * translation units, and applying it last would let the settings before it dispatch requests
     * that the new cut immediately invalidates. Applied first, it cancels that work and leaves
     * the timeline reloading, so the settings that follow cannot dispatch against a stale cut.</p>
     */
    static void applySettingsToBridge(AiSubtitleCueBridge bridge, AiSubtitleData data) {
        bridge.onSegmentationChanged(
                data.getSegmentTargetChars(),
                data.getSegmentMaxChars(),
                data.getLongSentenceChars());
        bridge.onContextEnabledChanged(data.isContextEnabled());
        bridge.onStreamingEnabledChanged(data.isStreamingEnabled());
        bridge.onSchedulingChanged(
                data.getLookaheadSeconds() * 1_000L,
                data.getScheduleThrottleSeconds() * 1_000L);
        bridge.setTranslationFirst(data.isTranslationFirst());
    }
}
