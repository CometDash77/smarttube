package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

/**
 * Provider-neutral asynchronous translation contract for the AI subtitle feature.
 *
 * <p>M02 ships only a deterministic fake implementation. Real protocol adapters arrive in M04;
 * this interface intentionally exposes no network, retry, or provider-specific concepts.</p>
 */
public interface TranslationProvider {
    /**
     * Starts one translation request. The returned call handle may be cancelled by the caller.
     * Implementations must not block the renderer thread.
     */
    TranslationCall translate(TranslationRequest request, TranslationCallback callback);
}
