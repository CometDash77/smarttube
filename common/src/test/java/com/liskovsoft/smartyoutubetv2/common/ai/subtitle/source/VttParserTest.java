package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VttParserTest {
    @Test
    public void parsesBasicCues() {
        String vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:03.500\nHello world\n\n00:00:03.500 --> 00:00:05.000\nSecond cue";
        List<SourceCue> cues = new VttParser().parse(vtt);
        assertEquals(2, cues.size());
        assertEquals(1_000, cues.get(0).getStartTimeMs());
        assertEquals(3_500, cues.get(0).getEndTimeMs());
        assertEquals("Hello world", cues.get(0).getText());
        assertEquals(3_500, cues.get(1).getStartTimeMs());
        assertEquals(5_000, cues.get(1).getEndTimeMs());
    }

    @Test
    public void parsesCueIdentifierAndMultiLine() {
        String vtt = "WEBVTT\n\nsome-id\n00:00:00.000 --> 00:00:02.000\nline one\nline two";
        List<SourceCue> cues = new VttParser().parse(vtt);
        assertEquals(1, cues.size());
        assertEquals("line one\nline two", cues.get(0).getText());
    }

    @Test
    public void parsesMinutesOnlyTimestamp() {
        String vtt = "WEBVTT\n\n00:30.000 --> 01:00.500\ntext";
        List<SourceCue> cues = new VttParser().parse(vtt);
        assertEquals(30_000, cues.get(0).getStartTimeMs());
        assertEquals(60_500, cues.get(0).getEndTimeMs());
    }

    @Test
    public void handlesCRLF() {
        String vtt = "WEBVTT\r\n\r\n00:00:01.000 --> 00:00:02.000\r\nhello";
        List<SourceCue> cues = new VttParser().parse(vtt);
        assertEquals(1, cues.size());
        assertEquals("hello", cues.get(0).getText());
    }

    @Test
    public void nullInputReturnsEmpty() {
        assertTrue(new VttParser().parse(null).isEmpty());
        assertTrue(new VttParser().parse("").isEmpty());
    }

    /**
     * Inline word timings are cue text, not timings. The parser has no per-word model, so the
     * markers survive verbatim in the cue line and the normalizer strips them along with every
     * other tag — the words reach the pipeline without any per-word time. Stated here so no
     * fixture category claims word timing the pipeline cannot carry.
     */
    @Test
    public void inlineWordTimingsAreCueTextAndNotInterpretedAsWordTimings() {
        String vtt = "WEBVTT\n\n00:00:00.000 --> 00:00:02.000\n"
                + "<00:00:00.000><c>one</c> <00:00:00.500><c>two</c>";

        List<SourceCue> cues = new VttParser().parse(vtt);

        assertEquals(1, cues.size());
        assertEquals("<00:00:00.000><c>one</c> <00:00:00.500><c>two</c>", cues.get(0).getText());
    }

    @Test
    public void theNormalizerStripsInlineWordTimingTagsWithEveryOtherTag() {
        List<SourceCue> normalized = new SubtitleNormalizer().normalize(
                java.util.Collections.singletonList(new SourceCue(0, 2_000,
                        "<00:00:00.000><c>one</c> <00:00:00.500><c>two</c>")));

        assertEquals("one two", normalized.get(0).getText());
    }
}
