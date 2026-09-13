package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SubtitleFixture;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AsrTimingEstimatorTest {
    @Test
    public void whitespaceWordsGetMonotonicCappedEstimates() {
        List<SourceCue> parts = new AsrTimingEstimator().estimate(
                new SourceCue(100, 1_100, "one two three"), 800);
        assertEquals(3, parts.size());
        assertEquals(800, parts.get(parts.size() - 1).getEndTimeMs());
        for (int i = 1; i < parts.size(); i++) {
            assertTrue(parts.get(i).getStartTimeMs() >= parts.get(i - 1).getEndTimeMs());
        }
    }

    @Test
    public void noSpaceScriptDoesNotUseAsciiWordCount() {
        List<SourceCue> parts = new AsrTimingEstimator().estimate(
                SubtitleFixture.noSpaceAsr().get(0), 2_000);
        assertTrue(parts.size() > 1);
    }
}
