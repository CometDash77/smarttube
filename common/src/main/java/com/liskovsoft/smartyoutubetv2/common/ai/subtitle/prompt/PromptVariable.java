package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

/** Fixed variable catalog accepted by subtitle prompts. */
public enum PromptVariable {
    SOURCE_TEXT("source_text"),
    SOURCE_LANGUAGE("source_language"),
    TARGET_LANGUAGE("target_language"),
    CONTEXT("context"),
    UNIT_INDEX("unit_index");

    private final String mName;

    PromptVariable(String name) { mName = name; }
    public String getName() { return mName; }

    public static PromptVariable fromName(String name) {
        for (PromptVariable variable : values()) {
            if (variable.mName.equals(name)) return variable;
        }
        return null;
    }
}
