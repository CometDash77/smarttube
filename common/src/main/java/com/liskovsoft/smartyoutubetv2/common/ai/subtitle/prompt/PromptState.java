package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable prompt list and deterministic selection state. */
public final class PromptState {
    private final List<PromptProfile> mProfiles;
    private final String mSelectedId;
    private final String mDefaultId;

    public PromptState(List<PromptProfile> profiles, String selectedId, String defaultId) {
        mProfiles = profiles == null ? Collections.<PromptProfile>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(profiles));
        mSelectedId = selectedId;
        mDefaultId = defaultId;
    }

    public List<PromptProfile> getProfiles() { return mProfiles; }
    public String getSelectedProfileId() { return mSelectedId; }
    public String getDefaultProfileId() { return mDefaultId; }

    public PromptProfile getProfile(String id) {
        if (id == null) return null;
        for (PromptProfile profile : mProfiles) {
            if (id.equals(profile.getId())) return profile;
        }
        return null;
    }

    public PromptState withSelections(String selectedId, String defaultId) {
        return new PromptState(mProfiles, selectedId, defaultId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PromptState)) return false;
        PromptState state = (PromptState) other;
        return mProfiles.equals(state.mProfiles) && same(mSelectedId, state.mSelectedId)
                && same(mDefaultId, state.mDefaultId);
    }

    @Override
    public int hashCode() {
        int result = mProfiles.hashCode();
        result = 31 * result + (mSelectedId == null ? 0 : mSelectedId.hashCode());
        result = 31 * result + (mDefaultId == null ? 0 : mDefaultId.hashCode());
        return result;
    }

    private static boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
