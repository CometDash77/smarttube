package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;

/**
 * Resolves the currently selected valid Provider Profile into the provider and
 * {@link TranslationProfile} identity used by the M03 Translation Session.
 *
 * <p>M05 will supply the real prompt and target-language policy; until then this class uses
 * explicit temporary prompt/language identity so no output-affecting field is silently
 * omitted.</p>
 */
public final class ProviderProfileRuntime {
    public static final String PROMPT_PROFILE_ID_PENDING = "pending-prompt";
    public static final int PROMPT_VERSION_PENDING = 1;
    public static final String TARGET_LANGUAGE_PENDING = "zh";

    private final ProviderProfileRepository mRepository;
    private final ProviderProfileResolver mResolver;

    public ProviderProfileRuntime(ProviderProfileRepository repository,
                                  ProviderProfileResolver resolver) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }

        mRepository = repository;
        mResolver = resolver;
    }

    public ResolvedProvider resolve() {
        try {
            ProviderProfileState state = mRepository.load();
            String selectedId = state.getSelectedProfileId();
            if (selectedId == null) {
                return ResolvedProvider.sourceOnly();
            }

            ProviderProfile profile = state.getProfile(selectedId);
            if (profile == null) {
                return ResolvedProvider.sourceOnly();
            }

            ProviderProfileResolver.Resolution resolution = mResolver.resolve(profile);
            if (!resolution.isResolved()) {
                return ResolvedProvider.sourceOnly(resolution.getFailure());
            }

            TranslationProfile translationProfile = new TranslationProfile(
                    profile.getId(), profile.getProtocol().name(), profile.getBaseUrl(),
                    profile.getModelId(), PROMPT_PROFILE_ID_PENDING,
                    PROMPT_VERSION_PENDING, TARGET_LANGUAGE_PENDING);
            return ResolvedProvider.resolved(resolution.getAdapter(), translationProfile);
        } catch (RuntimeException e) {
            return ResolvedProvider.sourceOnly();
        }
    }

    public static final class ResolvedProvider {
        private final TranslationProvider mProvider;
        private final TranslationProfile mProfile;
        private final TranslationFailure mFailure;

        private ResolvedProvider(TranslationProvider provider, TranslationProfile profile,
                                 TranslationFailure failure) {
            mProvider = provider;
            mProfile = profile;
            mFailure = failure;
        }

        static ResolvedProvider resolved(TranslationProvider provider,
                                         TranslationProfile profile) {
            return new ResolvedProvider(provider, profile, null);
        }

        static ResolvedProvider sourceOnly() {
            return new ResolvedProvider(null, null, null);
        }

        static ResolvedProvider sourceOnly(TranslationFailure failure) {
            return new ResolvedProvider(null, null, failure);
        }

        public boolean isResolved() {
            return mProvider != null && mProfile != null;
        }

        public boolean isSourceOnly() {
            return !isResolved();
        }

        public TranslationProvider getProvider() {
            return mProvider;
        }

        public TranslationProfile getProfile() {
            return mProfile;
        }

        public TranslationFailure getFailure() {
            return mFailure;
        }
    }
}