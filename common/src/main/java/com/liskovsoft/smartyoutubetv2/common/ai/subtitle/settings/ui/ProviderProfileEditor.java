package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;

/**
 * Small mutable form state for the profile create/edit dialogs.
 *
 * <p>It keeps the initial values around so the UI can report unsaved changes before
 * discarding a dialog. It never reads or exposes the stored secret; the replacement secret
 * is only held transiently for the create/replace flow.</p>
 */
public final class ProviderProfileEditor {
    private final ProviderProfile mOriginal;
    private final String mInitialName;
    private final String mInitialBaseUrl;
    private final String mInitialModelId;
    private final String mInitialSecret;
    private final ProviderType mInitialType;
    private final ProviderProtocol mInitialProtocol;

    private String mName;
    private String mBaseUrl;
    private String mModelId;
    private String mSecret;
    private ProviderType mType;
    private ProviderProtocol mProtocol;

    private ProviderProfileEditor(ProviderProfile original, ProviderType type,
                                  ProviderProtocol protocol) {
        mOriginal = original;
        mInitialName = valueOf(original != null ? original.getName() : "");
        mInitialBaseUrl = valueOf(original != null ? original.getBaseUrl() : "");
        mInitialModelId = valueOf(original != null ? original.getModelId() : "");
        mInitialSecret = "";
        mInitialType = type;
        mInitialProtocol = protocol;

        mName = mInitialName;
        mBaseUrl = mInitialBaseUrl;
        mModelId = mInitialModelId;
        mSecret = mInitialSecret;
        mType = type;
        mProtocol = protocol;
    }

    public static ProviderProfileEditor create(ProviderType type, ProviderProtocol protocol) {
        return new ProviderProfileEditor(null, type, protocol);
    }

    public static ProviderProfileEditor edit(ProviderProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        return new ProviderProfileEditor(profile, profile.getProviderType(),
                profile.getProtocol());
    }

    public ProviderProfile getOriginal() {
        return mOriginal;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = valueOf(name);
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        mBaseUrl = valueOf(baseUrl);
    }

    public String getModelId() {
        return mModelId;
    }

    public void setModelId(String modelId) {
        mModelId = valueOf(modelId);
    }

    public String getSecret() {
        return mSecret;
    }

    public void setSecret(String secret) {
        mSecret = valueOf(secret);
    }

    public ProviderType getProviderType() {
        return mType;
    }

    public void setProviderType(ProviderType type) {
        mType = type;
    }

    public ProviderProtocol getProtocol() {
        return mProtocol;
    }

    public void setProtocol(ProviderProtocol protocol) {
        mProtocol = protocol;
    }

    public boolean hasUnsavedChanges() {
        return !sameValue(mInitialName, mName)
                || !sameValue(mInitialBaseUrl, mBaseUrl)
                || !sameValue(mInitialModelId, mModelId)
                || !sameValue(mInitialSecret, mSecret)
                || mInitialType != mType
                || mInitialProtocol != mProtocol;
    }

    private static String valueOf(String value) {
        return value != null ? value : "";
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}