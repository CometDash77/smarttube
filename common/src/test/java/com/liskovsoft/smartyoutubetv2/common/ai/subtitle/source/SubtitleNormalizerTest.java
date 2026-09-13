package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubtitleNormalizerTest {
    @Test
    public void cleansNoiseDeduplicatesAndClipsOverlaps() {
        List<SourceCue> normalized = new SubtitleNormalizer().normalize(
                SubtitleFixture.noisyOverlapAndDuplicate());
        assertEquals(2, normalized.size());
        assertEquals("Hello", normalized.get(0).getText());
        assertEquals(900, normalized.get(0).getEndTimeMs());
        assertEquals("world", normalized.get(1).getText());
        assertTrue(normalized.get(1).getStartTimeMs() >= normalized.get(0).getEndTimeMs());
    }
}
