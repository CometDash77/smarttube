package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;

/** Recreation-safe form state for a prompt editor. */
public final class PromptProfileEditorPresenter {
    private final String mId;
    private final boolean mBuiltIn;
    private String mName;
    private String mContent;
    private final String mInitialName;
    private final String mInitialContent;

    private PromptProfileEditorPresenter(PromptProfile profile) {
        mId = profile == null ? null : profile.getId();
        mBuiltIn = profile != null && profile.isBuiltIn();
        mName = profile == null ? "" : profile.getName();
        mContent = profile == null ? "" : profile.getContent();
        mInitialName = mName;
        mInitialContent = mContent;
    }

    public static PromptProfileEditorPresenter create() { return new PromptProfileEditorPresenter(null); }
    public static PromptProfileEditorPresenter edit(PromptProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile must not be null");
        return new PromptProfileEditorPresenter(profile);
    }
    public String getId() { return mId; }
    public String getName() { return mName; }
    public String getContent() { return mContent; }
    public boolean isBuiltIn() { return mBuiltIn; }
    public void setName(String name) { mName = name == null ? "" : name; }
    public void setContent(String content) { mContent = content == null ? "" : content; }
    public boolean hasUnsavedChanges() { return !mInitialName.equals(mName) || !mInitialContent.equals(mContent); }
}
