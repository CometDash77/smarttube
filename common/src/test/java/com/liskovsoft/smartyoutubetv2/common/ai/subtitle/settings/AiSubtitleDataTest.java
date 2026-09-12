package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import org.junit.Before;
import org.junit.Test;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.support.JdkAwareRobolectricRunner;

import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JdkAwareRobolectricRunner.class)
public class AiSubtitleDataTest {
    @Before
    public void setUp() {
        AiSubtitleData.resetInstanceForTesting();
    }

    @Test
    public void defaultsToDisabled() {
        Context context = RuntimeEnvironment.getApplication();

        assertFalse(AiSubtitleData.instance(context).isEnabled());
    }

    @Test
    public void enabledStateIsPersistedAcrossInstanceRecreation() {
        Context context = RuntimeEnvironment.getApplication();

        AiSubtitleData.instance(context).setEnabled(true);
        assertTrue(AiSubtitleData.instance(context).isEnabled());

        // Re-create the singleton: the value must come from the persisted store, not cached fields.
        AiSubtitleData.resetInstanceForTesting();
        assertTrue(AiSubtitleData.instance(context).isEnabled());
    }

    @Test
    public void disablingRestoresDefault() {
        Context context = RuntimeEnvironment.getApplication();

        AiSubtitleData.instance(context).setEnabled(true);
        AiSubtitleData.instance(context).setEnabled(false);

        assertFalse(AiSubtitleData.instance(context).isEnabled());
    }

    @Test
    public void providerProfilesPersistWithoutDisturbingTheEnabledFlag() {
        Context context = RuntimeEnvironment.getApplication();
        AiSubtitleData data = AiSubtitleData.instance(context);
        data.setEnabled(true);

        ProviderProfile saved = data.providerProfiles().create(profile(null, "First"));
        AiSubtitleData.resetInstanceForTesting();

        AiSubtitleData recreated = AiSubtitleData.instance(context);
        assertTrue(recreated.isEnabled());
        assertEquals(saved.getId(), recreated.providerProfiles().load()
                .getSelectedProfileId());
    }

    @Test
    public void providerProfilesSwitchWithSmartTubeAppProfile() {
        Context context = RuntimeEnvironment.getApplication();
        AppPrefs prefs = AppPrefs.instance(context);
        prefs.enableMultiProfiles(true);

        prefs.onAccountChanged(account("alice"));
        ProviderProfile alice = AiSubtitleData.instance(context).providerProfiles()
                .create(profile(null, "Alice OpenAI"));

        prefs.onAccountChanged(account("bob"));
        assertTrue(AiSubtitleData.instance(context).providerProfiles().load()
                .getProfiles().isEmpty());
        ProviderProfile bob = AiSubtitleData.instance(context).providerProfiles()
                .create(profile(null, "Bob OpenAI"));

        prefs.onAccountChanged(account("alice"));
        assertEquals("Alice OpenAI", AiSubtitleData.instance(context).providerProfiles()
                .load().getProfile(alice.getId()).getName());

        prefs.onAccountChanged(account("bob"));
        assertEquals("Bob OpenAI", AiSubtitleData.instance(context).providerProfiles()
                .load().getProfile(bob.getId()).getName());
    }

    private static ProviderProfile profile(String id, String name) {
        return new ProviderProfile(id, name, ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1",
                null, "model-a", Collections.singletonList("model-a"), null, null);
    }

    private static Account account(final String name) {
        return new Account() {
            @Override
            public int getId() {
                return name.hashCode();
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getEmail() {
                return name + "@example.com";
            }

            @Override
            public String getAvatarImageUrl() {
                return null;
            }

            @Override
            public boolean isSelected() {
                return true;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };
    }

    @Test
    public void corruptProviderPayloadRepairsWithoutDisablingAiSubtitles() {
        Context context = RuntimeEnvironment.getApplication();
        AiSubtitleData data = AiSubtitleData.instance(context);
        data.setEnabled(true);
        data.write("{broken");

        assertTrue(data.providerProfiles().load().getProfiles().isEmpty());
        assertTrue(AiSubtitleData.instance(context).isEnabled());
    }
}
