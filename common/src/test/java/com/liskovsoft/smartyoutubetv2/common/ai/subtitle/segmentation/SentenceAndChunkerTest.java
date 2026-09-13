package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SentenceAndChunkerTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video", "track", "en");

    @Test
    public void ruleBreakerPrefersPunctuationAndSplitsOversizeInput() {
        SubtitleSegment segment = segment(0, 3_000,
                "First sentence. Second sentence!");
        List<SubtitleSegment> result = new RuleSentenceBreaker().breakSentences(
                java.util.Collections.singletonList(segment));
        assertEquals(2, result.size());
        assertEquals("First sentence.", result.get(0).getSourceText());
    }

    @Test
    public void statisticalBreakerResistsOneLargeGapAndKeepsOrder() {
        List<SubtitleSegment> segments = Arrays.asList(segment(0, 100, "one"),
                segment(200, 300, "two"), segment(400, 500, "Three"),
                segment(2_000, 2_100, "four"));
        List<List<SubtitleSegment>> groups = new StatisticalSentenceBreaker().group(segments);
        assertTrue(groups.size() >= 2);
        assertEquals("one", groups.get(0).get(0).getSourceText());
    }

    @Test
    public void chunkerReconstructsAllSourceTextAndKeepsSingleOversizeUnit() {
        List<SubtitleSegment> segments = Arrays.asList(segment(0, 100, "alpha"),
                segment(100, 200, "beta"), segment(200, 300, "a very long segment"));
        List<com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit> units =
                new TranslationChunker().chunk(segments, 8, 10);
        StringBuilder reconstructed = new StringBuilder();
        for (com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit unit : units) {
            if (reconstructed.length() > 0) reconstructed.append('\n');
            reconstructed.append(unit.getSourceText());
        }
        assertEquals("alpha\nbeta\na very long segment", reconstructed.toString());
    }

    private static SubtitleSegment segment(long start, long end, String text) {
        return new SubtitleSegment(new SubtitleSegmentId(TRACK, (int) start / 100), start, end, text);
    }
}
