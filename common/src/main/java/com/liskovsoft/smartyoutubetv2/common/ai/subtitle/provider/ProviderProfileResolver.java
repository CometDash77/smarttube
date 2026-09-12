package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;

/**
 * Resolves one Provider Profile to one of the shared normal-response protocol adapters.
 */
public final class ProviderProfileResolver {
    private final HttpRequestExecutor mExecutor;
    private final SecretStore mSecretStore;

    public ProviderProfileResolver(HttpRequestExecutor executor, SecretStore secretStore) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (secretStore == null) {
            throw new IllegalArgumentException("secretStore must not be null");
        }
        mExecutor = executor;
        mSecretStore = secretStore;
    }

    public Resolution resolve(ProviderProfile profile) {
        if (profile == null || profile.getModelId() == null) {
            return Resolution.failure(new TranslationFailure(
                    TranslationFailureCategory.PROTOCOL,
                    "Provider profile is incomplete."));
        }

        try {
            switch (profile.getProtocol()) {
                case OPENAI_CHAT_COMPLETIONS:
                    return Resolution.resolved(new OpenAiChatCompletionsAdapter(
                            mExecutor, profile, mSecretStore));
                case ANTHROPIC_MESSAGES:
                    return Resolution.resolved(new AnthropicMessagesAdapter(
                            mExecutor, profile, mSecretStore));
                default:
                    break;
            }
        } catch (IllegalArgumentException e) {
            // fall through to normalized failure
        }

        return Resolution.failure(new TranslationFailure(
                TranslationFailureCategory.PROTOCOL,
                "Provider profile could not be resolved."));
    }

    public static final class Resolution {
        private final ProtocolAdapter mAdapter;
        private final TranslationFailure mFailure;

        private Resolution(ProtocolAdapter adapter, TranslationFailure failure) {
            mAdapter = adapter;
            mFailure = failure;
        }

        static Resolution resolved(ProtocolAdapter adapter) {
            return new Resolution(adapter, null);
        }

        static Resolution failure(TranslationFailure failure) {
            return new Resolution(null, failure);
        }

        public boolean isResolved() {
            return mAdapter != null;
        }

        public ProtocolAdapter getAdapter() {
            return mAdapter;
        }

        public TranslationFailure getFailure() {
            return mFailure;
        }
    }
}
