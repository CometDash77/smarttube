package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable persisted state of the Provider Profile list and its deterministic selections.
 */
public final class ProviderProfileState {
    private final List<ProviderProfile> mProfiles;
    private final String mSelectedProfileId;
    private final String mDefaultProfileId;

    public ProviderProfileState(List<ProviderProfile> profiles, String selectedProfileId,
                                String defaultProfileId) {
        mProfiles = profiles == null || profiles.isEmpty()
                ? Collections.<ProviderProfile>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(profiles));
        mSelectedProfileId = normalizeOptional(selectedProfileId);
        mDefaultProfileId = normalizeOptional(defaultProfileId);
    }

    public static ProviderProfileState empty() {
        return new ProviderProfileState(null, null, null);
    }

    public List<ProviderProfile> getProfiles() {
        return mProfiles;
    }

    public String getSelectedProfileId() {
        return mSelectedProfileId;
    }

    public String getDefaultProfileId() {
        return mDefaultProfileId;
    }

    public ProviderProfile getProfile(String id) {
        if (id == null) {
            return null;
        }
        for (ProviderProfile profile : mProfiles) {
            if (id.equals(profile.getId())) {
                return profile;
            }
        }
        return null;
    }

    public ProviderProfileState withProfiles(List<ProviderProfile> profiles) {
        return new ProviderProfileState(profiles, mSelectedProfileId, mDefaultProfileId);
    }

    public ProviderProfileState withSelections(String selectedProfileId, String defaultProfileId) {
        return new ProviderProfileState(mProfiles, selectedProfileId, defaultProfileId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderProfileState)) {
            return false;
        }
        ProviderProfileState other = (ProviderProfileState) o;
        return sameValue(mProfiles, other.mProfiles)
                && sameValue(mSelectedProfileId, other.mSelectedProfileId)
                && sameValue(mDefaultProfileId, other.mDefaultProfileId);
    }

    @Override
    public int hashCode() {
        int result = valueHash(mProfiles);
        result = 31 * result + valueHash(mSelectedProfileId);
        result = 31 * result + valueHash(mDefaultProfileId);
        return result;
    }

    @Override
    public String toString() {
        return "ProviderProfileState{profiles=" + mProfiles.size()
                + ", selected=" + mSelectedProfileId
                + ", default=" + mDefaultProfileId + "}";
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}