package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

/**
 * Handle for one in-flight translation request.
 */
public interface TranslationCall {
    /**
     * Requests cancellation. A cancelled call must not deliver a result.
     */
    void cancel();

    boolean isCancelled();
}
