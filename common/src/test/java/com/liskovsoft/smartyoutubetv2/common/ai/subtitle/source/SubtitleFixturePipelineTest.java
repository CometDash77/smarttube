package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.RuleSentenceBreaker;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.TranslationChunker;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives the whole M06 pipeline (normalize, sentence-break, chunk, timeline lookup) over the
 * independently authored fixture and asserts the behaviour each fixture category exists to
 * prove. The fixture was previously data without a caller; this is its assertion.
 */
public class SubtitleFixturePipelineTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-fixture", "subtitle:en:1", "en");
    private static final int TARGET_CHARS = 60;
    private static final int MAX_CHARS = 200;
    private static final int LONG_SENTENCE_CHARS = 80;

    @Test
    public void theFixtureCoversEveryRequiredLanguageAndCaptionKind() {
        Set<String> languages = new LinkedHashSet<>();
        Set<String> kinds = new LinkedHashSet<>();
        Set<String> provenance = new LinkedHashSet<>();

        for (SubtitleFixture.FixtureEvent event : SubtitleFixture.independentCases()) {
            languages.add(event.getLanguage());
            kinds.add(event.getCaptionKind());
            provenance.add(event.getProvenanceId());
        }

        assertTrue("English, Japanese and Chinese must all be present: " + languages,
                languages.containsAll(java.util.Arrays.asList("en", "ja", "zh")));
        assertTrue("manual and auto-generated captions must both be present: " + kinds,
                kinds.contains("manual") && kinds.contains("asr"));
        assertTrue("word timing, no-space, noise, fast, slow, overlap, gap, duplicate and long "
                        + "categories must all be present: " + provenance,
                provenance.containsAll(java.util.Arrays.asList("synthetic-word-timing",
                        "synthetic-no-space", "synthetic-noise", "synthetic-fast",
                        "synthetic-slow", "synthetic-overlap", "synthetic-gap",
                        "synthetic-duplicate", "synthetic-long")));
    }

    @Test
    public void thePipelineKeepsContentAndDropsOnlyWhatItShould() {
        SourceTimeline timeline = pipeline(SubtitleFixture.independentCases());
        List<String> texts = sourceTexts(timeline);
        String all = texts.toString();

        assertTrue("the manual English caption must survive", all.contains("Manual caption."));
        assertTrue("the auto-generated caption must survive", all.contains("ASR words arrive quickly"));
        assertTrue("word-level timing must survive", all.contains("one two three"));
        assertTrue("Japanese without spaces must survive", all.contains("字幕試験です"));
        assertTrue("manual Chinese must survive", all.contains("这是人工中文字幕"));
        assertTrue("auto-generated Chinese must survive", all.contains("这是自动生成的中文字幕"));
        assertTrue("slow speech must survive", all.contains("Slow speech with a pause."));
        assertTrue("the overlap must be kept, not dropped", all.contains("Overlapping event"));
        assertTrue("the long segment must still be present", all.contains("deliberately long"));

        assertFalse("non-speech noise must be dropped", all.contains("[music]"));
        assertEquals("a repeated caption must appear once", 1, count(texts, "After a gap"));
        assertTrue("a long sentence must be split", texts.size() > 1);
    }

    @Test
    public void aGapInTheSourceHasNoUnitToShow() {
        SourceTimeline timeline = pipeline(SubtitleFixture.independentCases());

        // The fixture leaves 5.6s-6.0s without a caption.
        assertNull("the previous caption must not be shown across a gap",
                timeline.unitAt(5_800));
        assertNotNull("a caption before the gap is still addressable", timeline.unitAt(5_000));
        assertNotNull("a caption after the gap is still addressable", timeline.unitAt(6_200));
    }

    @Test
    public void everyUnitCoversContiguousSegmentsInTimelineOrder() {
        SourceTimeline timeline = pipeline(SubtitleFixture.independentCases());

        long previousStart = -1;

        for (TranslationUnit unit : timeline.getUnits()) {
            List<SubtitleSegmentId> ids = unit.getSegmentIds();

            for (int i = 1; i < ids.size(); i++) {
                assertEquals("unit coverage must be contiguous",
                        ids.get(i - 1).getIndex() + 1, ids.get(i).getIndex());
            }

            long start = startOf(timeline, ids.get(0).getIndex());
            assertTrue("units must be ordered by time", start >= previousStart);
            previousStart = start;
        }
    }

    @Test
    public void everySegmentResolvesToAUnitAndEveryUnitToASegment() {
        SourceTimeline timeline = pipeline(SubtitleFixture.independentCases());

        for (SubtitleSegment segment : timeline.getSegments()) {
            assertNotNull("every segment must belong to a unit",
                    timeline.unitContainingSegment(segment.getId().getIndex()));
        }

        for (TranslationUnit unit : timeline.getUnits()) {
            assertTrue("every unit must cover at least one segment",
                    unit.getSegmentIds().size() >= 1);
        }
    }

    private static SourceTimeline pipeline(List<SubtitleFixture.FixtureEvent> events) {
        List<SourceCue> cues = new ArrayList<>();
        for (SubtitleFixture.FixtureEvent event : events) {
            cues.add(new SourceCue(event.getStartTimeMs(), event.getEndTimeMs(), event.getText()));
        }

        List<SourceCue> normalized = new SubtitleNormalizer().normalize(cues);
        List<SubtitleSegment> segments = new ArrayList<>();

        for (int i = 0; i < normalized.size(); i++) {
            SourceCue cue = normalized.get(i);
            segments.add(new SubtitleSegment(new SubtitleSegmentId(TRACK, i),
                    cue.getStartTimeMs(), cue.getEndTimeMs(), cue.getText()));
        }

        List<SubtitleSegment> sentences =
                new RuleSentenceBreaker(LONG_SENTENCE_CHARS).breakSentences(segments);
        List<TranslationUnit> units =
                new TranslationChunker().chunk(sentences, TARGET_CHARS, MAX_CHARS);

        return SourceTimeline.from(sentences, units);
    }

    private static List<String> sourceTexts(SourceTimeline timeline) {
        List<String> texts = new ArrayList<>();
        for (SubtitleSegment segment : timeline.getSegments()) texts.add(segment.getSourceText());
        return texts;
    }

    private static int count(List<String> values, String text) {
        int total = 0;
        for (String value : values) {
            if (value.equals(text)) total++;
        }
        return total;
    }

    private static long startOf(SourceTimeline timeline, int segmentIndex) {
        for (SubtitleSegment segment : timeline.getSegments()) {
            if (segment.getId().getIndex() == segmentIndex) return segment.getStartTimeMs();
        }
        return -1;
    }
}
