package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Normalized result of model discovery or a provider connection test.
 */
public final class ConnectionTestResult {
    private final List<String> mModels;
    private final TranslationFailure mFailure;
    private final boolean mUnsupported;

    private ConnectionTestResult(List<String> models, TranslationFailure failure,
                                 boolean unsupported) {
        mModels = models == null || models.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(models));
        mFailure = failure;
        mUnsupported = unsupported;
    }

    public static ConnectionTestResult success(List<String> models) {
        return new ConnectionTestResult(models, null, false);
    }

    public static ConnectionTestResult unsupported() {
        return new ConnectionTestResult(null, null, true);
    }

    public static ConnectionTestResult failure(TranslationFailure failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure must not be null");
        }
        return new ConnectionTestResult(null, failure, false);
    }

    public boolean isSuccess() {
        return mFailure == null && !mUnsupported;
    }

    public boolean isUnsupported() {
        return mUnsupported;
    }

    public boolean isFailure() {
        return mFailure != null;
    }

    public List<String> getModels() {
        return mModels;
    }

    public TranslationFailure getFailure() {
        return mFailure;
    }
}
