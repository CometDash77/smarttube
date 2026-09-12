package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;

/**
 * Normal-response protocol adapter contract. Transport details stay behind this boundary.
 */
public interface ProtocolAdapter extends TranslationProvider {
    ProviderProtocol getProtocol();
}
