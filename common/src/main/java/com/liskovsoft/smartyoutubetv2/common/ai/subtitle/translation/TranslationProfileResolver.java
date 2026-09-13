package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRenderer;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptState;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;

/** Resolves provider, model, prompt, and language as one all-or-source-only result. */
public final class TranslationProfileResolver {
    private final ProviderProfileRepository mProviders;
    private final ProviderProfileResolver mProviderResolver;
    private final PromptRepository mPrompts;
    private final SecretStore mSecrets;
    private final PromptRenderer mPromptRenderer = new PromptRenderer();

    public TranslationProfileResolver(ProviderProfileRepository providers,
                                      ProviderProfileResolver providerResolver,
                                      PromptRepository prompts, SecretStore secrets) {
        if (providers == null || providerResolver == null || prompts == null || secrets == null) {
            throw new IllegalArgumentException("all resolution dependencies are required");
        }
        mProviders = providers;
        mProviderResolver = providerResolver;
        mPrompts = prompts;
        mSecrets = secrets;
    }

    public Resolution resolve(String targetLanguage) {
        try {
            if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
                return Resolution.sourceOnly("target language is missing");
            }
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileState providerState =
                    mProviders.load();
            ProviderProfile profile = providerState.getProfile(providerState.getSelectedProfileId());
            if (profile == null || profile.getModelId() == null || profile.getModelId().trim().isEmpty()) {
                return Resolution.sourceOnly("provider or model is missing");
            }
            if (profile.getSecretReference() == null || blank(mSecrets.get(profile.getSecretReference()))) {
                return Resolution.sourceOnly("provider credential is missing");
            }
            ProviderProfileResolver.Resolution provider = mProviderResolver.resolve(profile);
            if (!provider.isResolved()) return Resolution.sourceOnly("provider protocol is unavailable");

            PromptState promptState = mPrompts.load();
            PromptProfile prompt = promptState.getProfile(promptState.getSelectedProfileId());
            if (prompt == null) return Resolution.sourceOnly("prompt is missing");
            PromptRenderer.RenderResult validation = mPromptRenderer.validate(prompt.getContent());
            if (!validation.isValid()) return Resolution.sourceOnly("prompt validation failed");

            TranslationProfile resolved = new TranslationProfile(profile.getId(),
                    profile.getProtocol().name(), profile.getBaseUrl(), profile.getModelId(),
                    prompt.getId(), prompt.getVersion(), prompt.getContentHash(), targetLanguage.trim());
            return Resolution.resolved(provider.getAdapter(), resolved, prompt);
        } catch (SecretStore.Failure e) {
            return Resolution.sourceOnly("provider credential is unavailable");
        } catch (RuntimeException e) {
            return Resolution.sourceOnly("translation profile is invalid");
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    public static final class Resolution {
        private final TranslationProvider mProvider;
        private final TranslationProfile mProfile;
        private final PromptProfile mPrompt;
        private final TranslationFailure mFailure;

        private Resolution(TranslationProvider provider, TranslationProfile profile,
                           PromptProfile prompt, TranslationFailure failure) {
            mProvider = provider; mProfile = profile; mPrompt = prompt; mFailure = failure;
        }
        static Resolution resolved(TranslationProvider provider, TranslationProfile profile,
                                   PromptProfile prompt) {
            return new Resolution(provider, profile, prompt, null);
        }
        static Resolution sourceOnly(String message) {
            return new Resolution(null, null, null,
                    new TranslationFailure(TranslationFailureCategory.PROTOCOL, message));
        }
        public boolean isResolved() { return mProvider != null && mProfile != null && mPrompt != null; }
        public boolean isSourceOnly() { return !isResolved(); }
        public TranslationProvider getProvider() { return mProvider; }
        public TranslationProfile getProfile() { return mProfile; }
        public PromptProfile getPrompt() { return mPrompt; }
        public TranslationFailure getFailure() { return mFailure; }
    }
}
