package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ConnectionTestResult;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ModelCatalog;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderPreset;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Pure-JVM management layer for Provider Profiles.
 *
 * <p>This presenter owns profile creation, editing, copying, deletion, selection, and
 * connection testing. It never writes credentials into a {@link ProviderProfile}; secrets are
 * stored through {@link SecretStore} under the profile id and displayed only through
 * {@link SecretStore.Masking}.</p>
 */
public final class ProviderProfilesPresenter {
    private final ProviderProfileRepository mRepository;
    private final SecretStore mSecrets;
    private final ProviderProfileResolver mResolver;
    private final ModelCatalog mCatalog;

    public ProviderProfilesPresenter(ProviderProfileRepository repository,
                                     SecretStore secrets,
                                     ProviderProfileResolver resolver,
                                     ModelCatalog catalog) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (secrets == null) {
            throw new IllegalArgumentException("secrets must not be null");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        mRepository = repository;
        mSecrets = secrets;
        mResolver = resolver;
        mCatalog = catalog;
    }

    public List<ProviderProfile> getProfiles() {
        return mRepository.load().getProfiles();
    }

    public ProviderProfile getProfile(String id) {
        return mRepository.load().getProfile(id);
    }

    public String getSelectedProfileId() {
        return mRepository.load().getSelectedProfileId();
    }

    public String getDefaultProfileId() {
        return mRepository.load().getDefaultProfileId();
    }

    public synchronized SaveResult save(String id, String name, ProviderType type,
                                        ProviderProtocol protocol, String baseUrl,
                                        String modelId, String secret,
                                        boolean replaceSecret) {
        try {
            validateEditableFields(name, type, protocol, baseUrl, modelId);

            if (id == null) {
                return create(name, type, protocol, baseUrl, modelId,
                        requireSecret(secret));
            }

            ProviderProfile existing = mRepository.load().getProfile(id);
            if (existing == null) {
                return SaveResult.failure();
            }

            String oldReference = existing.getSecretReference();
            String newReference = oldReference;
            if (replaceSecret) {
                newReference = resolveSecretReference(existing, oldReference);
                String previousSecret = readSecret(oldReference);
                boolean secretWritten = false;
                try {
                    mSecrets.put(newReference, requireSecret(secret));
                    secretWritten = true;
                    ProviderProfile replaced = new ProviderProfile(existing.getId(), name, type,
                            protocol, baseUrl, newReference, modelId,
                            existing.getAvailableModelIds(), existing.getHeaders(),
                            existing.getOptions());
                    return SaveResult.success(mRepository.update(replaced));
                } catch (RuntimeException e) {
                    if (secretWritten) {
                        restoreSecret(newReference, oldReference, previousSecret);
                    }
                    throw e;
                }
            }

            ProviderProfile updated = new ProviderProfile(existing.getId(), name, type,
                    protocol, baseUrl, newReference, modelId,
                    existing.getAvailableModelIds(), existing.getHeaders(),
                    existing.getOptions());
            return SaveResult.success(mRepository.update(updated));
        } catch (RuntimeException e) {
            return SaveResult.failure();
        }
    }

    public synchronized SaveResult copy(String id) {
        try {
            ProviderProfile source = mRepository.load().getProfile(id);
            if (source == null || !isComplete(source)) {
                return SaveResult.failure();
            }

            ProviderProfile draft = new ProviderProfile(null, source.getName() + " copy",
                    source.getProviderType(), source.getProtocol(), source.getBaseUrl(),
                    null, source.getModelId(), source.getAvailableModelIds(),
                    source.getHeaders(), source.getOptions());
            ProviderProfile created = mRepository.create(draft);

            String reference = created.getId();
            try {
                String copiedSecret = readSecret(source.getSecretReference());
                if (copiedSecret != null) {
                    mSecrets.put(reference, copiedSecret);
                }

                ProviderProfile saved = new ProviderProfile(created.getId(), created.getName(),
                        created.getProviderType(), created.getProtocol(), created.getBaseUrl(),
                        reference, created.getModelId(), created.getAvailableModelIds(),
                        created.getHeaders(), created.getOptions());
                return SaveResult.success(mRepository.update(saved));
            } catch (RuntimeException e) {
                mSecrets.delete(reference);
                mRepository.delete(created.getId());
                throw e;
            }
        } catch (RuntimeException e) {
            return SaveResult.failure();
        }
    }

    public boolean delete(String id) {
        return mRepository.delete(id);
    }

    public synchronized boolean select(String id) {
        ProviderProfile profile = mRepository.load().getProfile(id);
        if (!isComplete(profile)) {
            return false;
        }

        mRepository.select(id);
        return true;
    }

    public synchronized boolean setDefault(String id) {
        ProviderProfile profile = mRepository.load().getProfile(id);
        if (!isComplete(profile)) {
            return false;
        }

        mRepository.setDefault(id);
        return true;
    }

    public String maskSecret(ProviderProfile profile) {
        if (profile == null || isBlank(profile.getSecretReference())) {
            return "";
        }

        try {
            return SecretStore.Masking.mask(mSecrets.get(profile.getSecretReference()));
        } catch (SecretStore.Failure e) {
            return "";
        }
    }

    public ConnectionTest testConnection(ProviderProfile profile,
                                         ConnectionTestListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        final ConnectionTest handle = new ConnectionTest();
        listener.onStarted();

        if (profile == null) {
            listener.onResult(ConnectionTestResult.failure(new TranslationFailure(
                    TranslationFailureCategory.PROTOCOL, "Provider profile is missing.")));
            return handle;
        }

        ModelCatalog.DiscoveryCall call = mCatalog.discover(profile, mSecrets,
                new ModelCatalog.Callback() {
                    @Override
                    public void onResult(ConnectionTestResult result) {
                        if (!handle.isCancelled()) {
                            listener.onResult(result);
                        }
                    }
                });
        handle.setCall(call);
        return handle;
    }

    private SaveResult create(String name, ProviderType type, ProviderProtocol protocol,
                              String baseUrl, String modelId, String secret) {
        ProviderPreset preset = ProviderPreset.forType(type);
        ProviderProfile draft = preset.createDraft(name, modelId, baseUrl, null);
        ProviderProfile created = mRepository.create(draft);

        String reference = created.getId();
        try {
            mSecrets.put(reference, secret);

            ProviderProfile saved = new ProviderProfile(created.getId(), created.getName(), type,
                    protocol, created.getBaseUrl(), reference, created.getModelId(),
                    created.getAvailableModelIds(), created.getHeaders(), created.getOptions());
            return SaveResult.success(mRepository.update(saved));
        } catch (RuntimeException e) {
            mSecrets.delete(reference);
            mRepository.delete(created.getId());
            throw e;
        }
    }

    private boolean isComplete(ProviderProfile profile) {
        if (profile == null || isBlank(profile.getModelId())
                || isBlank(profile.getSecretReference())
                || !isValidBaseUrl(profile.getBaseUrl())) {
            return false;
        }

        if (!mResolver.resolve(profile).isResolved()) {
            return false;
        }

        try {
            return !isBlank(mSecrets.get(profile.getSecretReference()));
        } catch (SecretStore.Failure e) {
            return false;
        }
    }

    private String readSecret(String reference) {
        if (isBlank(reference)) {
            return null;
        }

        try {
            return mSecrets.get(reference);
        } catch (SecretStore.Failure e) {
            return null;
        }
    }

    private void restoreSecret(String newReference, String oldReference,
                               String previousSecret) {
        if (!sameReference(newReference, oldReference)) {
            mSecrets.delete(newReference);
            if (previousSecret != null && !isBlank(oldReference)) {
                mSecrets.put(oldReference, previousSecret);
            }
        } else if (previousSecret != null) {
            mSecrets.put(oldReference, previousSecret);
        } else if (!isBlank(oldReference)) {
            mSecrets.delete(oldReference);
        }
    }

    private static boolean sameReference(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String resolveSecretReference(ProviderProfile profile, String reference) {
        if (!isBlank(reference)) {
            return reference;
        }
        if (!isBlank(profile.getId())) {
            return profile.getId();
        }
        throw new IllegalArgumentException("secret reference missing");
    }

    private static void validateEditableFields(String name, ProviderType type,
                                               ProviderProtocol protocol, String baseUrl,
                                               String modelId) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (protocol == null) {
            throw new IllegalArgumentException("protocol must not be null");
        }
        ProviderPreset preset = ProviderPreset.forType(type);
        if (preset.getProtocol() != protocol) {
            throw new IllegalArgumentException("protocol does not match provider type");
        }
        if (!isValidBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) URL");
        }
        if (isBlank(modelId)) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
    }

    private static String requireSecret(String secret) {
        if (isBlank(secret)) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        return secret;
    }

    static boolean isValidBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }

        try {
            URI uri = new URI(baseUrl.trim());
            return uri.getScheme() != null
                    && uri.getRawAuthority() != null
                    && ("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface ConnectionTestListener {
        void onStarted();

        void onResult(ConnectionTestResult result);
    }

    public static final class ConnectionTest {
        private ModelCatalog.DiscoveryCall mCall;
        private boolean mCancelled;

        public synchronized void cancel() {
            mCancelled = true;
            if (mCall != null) {
                mCall.cancel();
            }
        }

        public synchronized boolean isCancelled() {
            return mCancelled;
        }

        synchronized void setCall(ModelCatalog.DiscoveryCall call) {
            mCall = call;
            if (mCancelled && mCall != null) {
                mCall.cancel();
            }
        }
    }

    public static final class SaveResult {
        private final boolean mSuccess;
        private final ProviderProfile mProfile;

        private SaveResult(boolean success, ProviderProfile profile) {
            mSuccess = success;
            mProfile = profile;
        }

        static SaveResult success(ProviderProfile profile) {
            return new SaveResult(true, profile);
        }

        static SaveResult failure() {
            return new SaveResult(false, null);
        }

        public boolean isSuccess() {
            return mSuccess;
        }

        public ProviderProfile getProfile() {
            return mProfile;
        }
    }
}