package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

/**
 * Result callback for one translation request. Exactly one of the two methods is expected;
 * callers must still tolerate late or repeated invocations by validating request identity.
 */
public interface TranslationCallback {
    void onSuccess(TranslationResult result);

    void onFailure(Throwable error);
}
