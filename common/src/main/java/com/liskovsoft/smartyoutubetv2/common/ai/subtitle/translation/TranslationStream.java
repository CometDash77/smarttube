package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

/**
 * Streaming extension of {@link TranslationCallback}: a provider may deliver draft
 * ({@code partial}) output before the final result.
 *
 * <p>Semantics: a partial result may only update the unit that owns it while it is still the
 * current request; the final {@link TranslationCallback#onSuccess} result always replaces any
 * draft for the same unit, and a partial must never be stored as final text. The non-streaming
 * callback path remains the baseline contract; streaming arrives with the mature-pipeline
 * milestone.</p>
 */
public interface TranslationStream extends TranslationCallback {
    /** Draft output that may still change; only the owning current request may consume it. */
    void onPartial(TranslationResult partialResult);
}
