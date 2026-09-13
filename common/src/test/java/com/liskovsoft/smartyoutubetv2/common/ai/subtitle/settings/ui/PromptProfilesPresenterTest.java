package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptRepository;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PromptProfilesPresenterTest {
    @Test
    public void customCrudAndValidationStayFeatureOwned() {
        PromptProfilesPresenter presenter = new PromptProfilesPresenter(new PromptRepository(new Store()));
        PromptProfilesPresenter.SaveResult result = presenter.create("Custom", "{{source_text}}");
        assertTrue(result.isSuccess());
        assertTrue(presenter.select(result.getProfile().getId()));
        assertFalse(presenter.create("Bad", "{{unknown}}").isSuccess());
        assertTrue(presenter.update(result.getProfile().getId(), "Changed", "{{target_language}}").isSuccess());
        assertTrue(presenter.delete(result.getProfile().getId()));
    }

    private static final class Store implements PromptRepository.Store {
        private String value;
        @Override public String read() { return value; }
        @Override public void write(String payload) { value = payload; }
    }
}
