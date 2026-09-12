package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

/**
 * User-editable capability flags for one Provider Profile.
 */
public final class ProviderCapabilities {
    private final boolean mSupportsModelDiscovery;
    private final boolean mSupportsManualModel;
    private final boolean mSupportsCustomBaseUrl;
    private final boolean mSupportsCustomHeaders;

    public ProviderCapabilities(boolean supportsModelDiscovery, boolean supportsManualModel,
                                boolean supportsCustomBaseUrl, boolean supportsCustomHeaders) {
        mSupportsModelDiscovery = supportsModelDiscovery;
        mSupportsManualModel = supportsManualModel;
        mSupportsCustomBaseUrl = supportsCustomBaseUrl;
        mSupportsCustomHeaders = supportsCustomHeaders;
    }

    public boolean supportsModelDiscovery() {
        return mSupportsModelDiscovery;
    }

    public boolean supportsManualModel() {
        return mSupportsManualModel;
    }

    public boolean supportsCustomBaseUrl() {
        return mSupportsCustomBaseUrl;
    }

    public boolean supportsCustomHeaders() {
        return mSupportsCustomHeaders;
    }
}
