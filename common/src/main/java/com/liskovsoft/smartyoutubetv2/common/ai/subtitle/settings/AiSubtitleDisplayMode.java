package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

/**
 * How the AI subtitle feature renders a source cue after translation resolves.
 *
 * <p>Whether translation is enabled is a separate switch. When AI translation is enabled but
 * not yet ready, every mode falls back to the source cue.</p>
 */
public enum AiSubtitleDisplayMode {
    SOURCE,
    BILINGUAL,
    TRANSLATION_ONLY
}
