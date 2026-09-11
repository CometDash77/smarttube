package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.support.JdkAwareRobolectricRunner;

import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

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
}
