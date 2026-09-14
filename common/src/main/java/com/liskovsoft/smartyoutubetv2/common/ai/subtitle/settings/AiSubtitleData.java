package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.SegmentationLimits;

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
    private static final String PREFS_NAME = AiSubtitleData.class.getName();
    private static final String ENABLED = "enabled";
    private static final String TARGET_LANGUAGE = "target_language";
    private static final String DISPLAY_MODE = "display_mode";
    private static final String TRANSLATION_FIRST = "translation_first";
    private static final String CONTEXT_ENABLED = "context_enabled";
    private static final String STREAMING_ENABLED = "streaming_enabled";
    private static final String LOOKAHEAD_SECONDS = "lookahead_seconds";
    private static final String SCHEDULE_THROTTLE_SECONDS = "schedule_throttle_seconds";
    private static final String SEGMENT_TARGET_CHARS = "segment_target_chars";
    private static final String SEGMENT_MAX_CHARS = "segment_max_chars";
    private static final String LONG_SENTENCE_CHARS = "long_sentence_chars";
    public static final String DEFAULT_TARGET_LANGUAGE = "zh";
    public static final int DEFAULT_LOOKAHEAD_SECONDS = 90;
    public static final int DEFAULT_SCHEDULE_THROTTLE_SECONDS = 30;
    public static final int DEFAULT_SEGMENT_TARGET_CHARS = 60;
    public static final int DEFAULT_SEGMENT_MAX_CHARS = 200;
    public static final int DEFAULT_LONG_SENTENCE_CHARS = 80;
    /** Allowed lookahead presets, in seconds. */
    public static final int[] LOOKAHEAD_SECONDS_PRESETS = {0, 30, 60, 90, 120};
    /** Allowed background-schedule presets, in seconds. */
    public static final int[] SCHEDULE_THROTTLE_SECONDS_PRESETS = {5, 15, 30};
    private static AiSubtitleData sInstance;
    private static Context sContext;

    private AiSubtitleData(Context context) {
        super(context, PREFS_NAME);
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

    public int getLookaheadSeconds() {
        long stored = getLong(LOOKAHEAD_SECONDS, DEFAULT_LOOKAHEAD_SECONDS);

        if (isPreset(stored, LOOKAHEAD_SECONDS_PRESETS)) return (int) stored;

        putLong(LOOKAHEAD_SECONDS, DEFAULT_LOOKAHEAD_SECONDS);
        return DEFAULT_LOOKAHEAD_SECONDS;
    }

    public void setLookaheadSeconds(int seconds) {
        if (!isPreset(seconds, LOOKAHEAD_SECONDS_PRESETS)) {
            throw new IllegalArgumentException("unsupported lookahead seconds: " + seconds);
        }
        putLong(LOOKAHEAD_SECONDS, seconds);
    }

    public int getScheduleThrottleSeconds() {
        long stored = getLong(SCHEDULE_THROTTLE_SECONDS, DEFAULT_SCHEDULE_THROTTLE_SECONDS);

        if (isPreset(stored, SCHEDULE_THROTTLE_SECONDS_PRESETS)) return (int) stored;

        putLong(SCHEDULE_THROTTLE_SECONDS, DEFAULT_SCHEDULE_THROTTLE_SECONDS);
        return DEFAULT_SCHEDULE_THROTTLE_SECONDS;
    }

    public void setScheduleThrottleSeconds(int seconds) {
        if (!isPreset(seconds, SCHEDULE_THROTTLE_SECONDS_PRESETS)) {
            throw new IllegalArgumentException("unsupported throttle seconds: " + seconds);
        }
        putLong(SCHEDULE_THROTTLE_SECONDS, seconds);
    }

    public int getSegmentTargetChars() {
        repairSegmentLimitsIfNeeded();
        return (int) getLong(SEGMENT_TARGET_CHARS, DEFAULT_SEGMENT_TARGET_CHARS);
    }

    public int getSegmentMaxChars() {
        repairSegmentLimitsIfNeeded();
        return (int) getLong(SEGMENT_MAX_CHARS, DEFAULT_SEGMENT_MAX_CHARS);
    }

    public int getLongSentenceChars() {
        repairSegmentLimitsIfNeeded();
        return (int) getLong(LONG_SENTENCE_CHARS, DEFAULT_LONG_SENTENCE_CHARS);
    }

    public void setSegmentLimits(int targetChars, int maxChars, int longSentenceChars) {
        requireSegmentLimits(targetChars, maxChars, longSentenceChars);
        putLong(SEGMENT_TARGET_CHARS, targetChars);
        putLong(SEGMENT_MAX_CHARS, maxChars);
        putLong(LONG_SENTENCE_CHARS, longSentenceChars);
    }

    /**
     * Writes every scheduling limit through one {@link android.content.SharedPreferences.Editor}
     * batch so a failure cannot leave the five values disagreeing with each other.
     */
    public void setSchedulingLimits(int lookaheadSeconds, int throttleSeconds, int targetChars,
                                    int maxChars, int longSentenceChars) {
        if (!isPreset(lookaheadSeconds, LOOKAHEAD_SECONDS_PRESETS)) {
            throw new IllegalArgumentException("unsupported lookahead seconds: " + lookaheadSeconds);
        }
        if (!isPreset(throttleSeconds, SCHEDULE_THROTTLE_SECONDS_PRESETS)) {
            throw new IllegalArgumentException("unsupported throttle seconds: " + throttleSeconds);
        }
        requireSegmentLimits(targetChars, maxChars, longSentenceChars);

        prefs().edit()
                .putLong(LOOKAHEAD_SECONDS, lookaheadSeconds)
                .putLong(SCHEDULE_THROTTLE_SECONDS, throttleSeconds)
                .putLong(SEGMENT_TARGET_CHARS, targetChars)
                .putLong(SEGMENT_MAX_CHARS, maxChars)
                .putLong(LONG_SENTENCE_CHARS, longSentenceChars)
                .apply();
    }

    public boolean isTranslationFirst() {
        return getBoolean(TRANSLATION_FIRST, false);
    }

    public void setTranslationFirst(boolean translationFirst) {
        putBoolean(TRANSLATION_FIRST, translationFirst);
    }

    /** Bounded reference context; off by default and off until the user asks for it. */
    public boolean isContextEnabled() {
        return getBoolean(CONTEXT_ENABLED, false);
    }

    public void setContextEnabled(boolean enabled) {
        putBoolean(CONTEXT_ENABLED, enabled);
    }

    /** Streamed drafts; off by default because not every provider or model supports them. */
    public boolean isStreamingEnabled() {
        return getBoolean(STREAMING_ENABLED, false);
    }

    public void setStreamingEnabled(boolean enabled) {
        putBoolean(STREAMING_ENABLED, enabled);
    }

    private void repairSegmentLimitsIfNeeded() {
        long target = getLong(SEGMENT_TARGET_CHARS, DEFAULT_SEGMENT_TARGET_CHARS);
        long max = getLong(SEGMENT_MAX_CHARS, DEFAULT_SEGMENT_MAX_CHARS);
        long longSentence = getLong(LONG_SENTENCE_CHARS, DEFAULT_LONG_SENTENCE_CHARS);

        if (isValidSegmentLimits(target, max, longSentence)) return;

        setSegmentLimits(DEFAULT_SEGMENT_TARGET_CHARS, DEFAULT_SEGMENT_MAX_CHARS,
                DEFAULT_LONG_SENTENCE_CHARS);
    }

    private static void requireSegmentLimits(long targetChars, long maxChars,
                                             long longSentenceChars) {
        if (!isValidSegmentLimits(targetChars, maxChars, longSentenceChars)) {
            throw new IllegalArgumentException("invalid segmentation limits");
        }
    }

    private static boolean isValidSegmentLimits(long targetChars, long maxChars,
                                                long longSentenceChars) {
        return SegmentationLimits.isValid(targetChars, maxChars, longSentenceChars);
    }

    private static boolean isPreset(long value, int[] presets) {
        for (int preset : presets) {
            if (value == preset) return true;
        }
        return false;
    }

    /** Same backing store as {@link SharedPreferencesBase}, exposed for single-batch writes. */
    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
