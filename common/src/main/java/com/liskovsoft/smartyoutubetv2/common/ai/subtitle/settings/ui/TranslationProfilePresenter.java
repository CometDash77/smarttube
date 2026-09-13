package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Owns only target-language selection; provider credentials and models stay elsewhere. */
public final class TranslationProfilePresenter {
    private static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList(
            "zh", "en", "ja", "ko", "es", "fr", "de", "ru"));
    private final AiSubtitleData mData;

    public TranslationProfilePresenter(AiSubtitleData data) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        mData = data;
    }

    public List<String> getTargetLanguages() { return LANGUAGES; }
    public String getTargetLanguage() { return mData.getTargetLanguage(); }

    public boolean setTargetLanguage(String language) {
        if (language == null || !LANGUAGES.contains(language.trim())) return false;
        mData.setTargetLanguage(language);
        return true;
    }
}
