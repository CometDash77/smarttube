package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

/**
 * User-facing Provider Type. The value selects an editable preset; it is not a capability
 * claim and does not determine wire behavior by itself.
 */
public enum ProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC_COMPATIBLE,
    OPENROUTER,
    DEEPSEEK,
    MIMO
}