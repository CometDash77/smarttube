package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRenderer;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRepository;

import java.util.List;

/** Feature-owned prompt CRUD and validation boundary; no provider credential logic lives here. */
public final class PromptProfilesPresenter {
    private final PromptRepository mRepository;
    private final PromptRenderer mRenderer = new PromptRenderer();

    public PromptProfilesPresenter(PromptRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository must not be null");
        mRepository = repository;
    }

    public List<PromptProfile> getProfiles() { return mRepository.load().getProfiles(); }
    public String getSelectedProfileId() { return mRepository.load().getSelectedProfileId(); }
    public String getDefaultProfileId() { return mRepository.load().getDefaultProfileId(); }
    public PromptProfile getProfile(String id) { return mRepository.load().getProfile(id); }

    public SaveResult create(String name, String content) {
        return save(() -> mRepository.createCustom(name, content));
    }

    public SaveResult copy(String id, String name) {
        return save(() -> mRepository.copyToCustom(id, name));
    }

    public SaveResult update(String id, String name, String content) {
        return save(() -> mRepository.update(id, name, content));
    }

    public boolean delete(String id) {
        try { return mRepository.delete(id); } catch (RuntimeException e) { return false; }
    }

    public boolean select(String id) {
        try { mRepository.select(id); return true; } catch (RuntimeException e) { return false; }
    }

    public List<String> validate(String content) {
        return mRenderer.validate(content).getDiagnostics();
    }

    private SaveResult save(Operation operation) {
        try { return SaveResult.success(operation.run()); } catch (RuntimeException e) { return SaveResult.failure(e.getMessage()); }
    }

    private interface Operation { PromptProfile run(); }

    public static final class SaveResult {
        private final PromptProfile mProfile;
        private final String mError;
        private SaveResult(PromptProfile profile, String error) { mProfile = profile; mError = error; }
        static SaveResult success(PromptProfile profile) { return new SaveResult(profile, null); }
        static SaveResult failure(String error) { return new SaveResult(null, error == null ? "invalid prompt" : error); }
        public boolean isSuccess() { return mProfile != null; }
        public PromptProfile getProfile() { return mProfile; }
        public String getError() { return mError; }
    }
}
