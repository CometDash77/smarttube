package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;

/**
 * Maps transport/status outcomes to the provider-neutral failure vocabulary.
 */
public final class TranslationFailureMapper {
    private TranslationFailureMapper() {
    }

    public static TranslationFailure fromHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new TranslationFailure(TranslationFailureCategory.AUTH,
                    "Provider authentication failed.");
        }
        if (statusCode == 408 || statusCode == 504) {
            return new TranslationFailure(TranslationFailureCategory.TIMEOUT,
                    "Provider request timed out.");
        }
        if (statusCode == 429) {
            return new TranslationFailure(TranslationFailureCategory.RATE_LIMITED,
                    "Provider rate limit reached.");
        }
        if (statusCode >= 500) {
            return new TranslationFailure(TranslationFailureCategory.SERVER,
                    "Provider server error.");
        }
        return new TranslationFailure(TranslationFailureCategory.PROTOCOL,
                "Provider rejected the request.");
    }

    public static TranslationFailure fromTransport(HttpRequestExecutor.FailureReason reason) {
        if (reason == HttpRequestExecutor.FailureReason.TIMEOUT) {
            return new TranslationFailure(TranslationFailureCategory.TIMEOUT,
                    "Provider request timed out.");
        }
        if (reason == HttpRequestExecutor.FailureReason.CANCELLED) {
            return new TranslationFailure(TranslationFailureCategory.CANCELLED,
                    "Provider request was cancelled.");
        }
        return new TranslationFailure(TranslationFailureCategory.NETWORK,
                "Provider network request failed.");
    }
}
