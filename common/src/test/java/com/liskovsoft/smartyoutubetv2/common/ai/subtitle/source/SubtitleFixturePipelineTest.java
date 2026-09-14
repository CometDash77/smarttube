package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.sharedutils.helpers.Helpers;
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
import java.util.Map;
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

    /**
     * Each language must appear in both caption kinds. Checking the two sets separately would
     * pass while a whole combination — Japanese manual captions — was missing.
     */
    @Test
    public void theFixtureCoversEveryRequiredLanguageAndCaptionKind() {
        Set<String> combinations = new LinkedHashSet<>();
        Set<String> provenance = new LinkedHashSet<>();

        for (SubtitleFixture.FixtureEvent event : SubtitleFixture.independentCases()) {
            combinations.add(event.getLanguage() + "/" + event.getCaptionKind());
            provenance.add(event.getProvenanceId());
        }

        assertTrue("every language must appear in both caption kinds: " + combinations,
                combinations.containsAll(java.util.Arrays.asList(
                        "en/manual", "en/asr", "ja/manual", "ja/asr", "zh/manual", "zh/asr")));
        assertTrue("no-space, noise, fast, slow, overlap, gap, duplicate and long categories "
                        + "must all be present: " + provenance,
                provenance.containsAll(java.util.Arrays.asList("synthetic-no-space",
                        "synthetic-noise", "synthetic-fast", "synthetic-slow",
                        "synthetic-overlap", "synthetic-gap", "synthetic-duplicate",
                        "synthetic-long")));
    }

    /**
     * A line past the splitter's threshold, and only that line. The fixture as a whole has many
     * cues, so a count taken over it says nothing about whether a long line is cut.
     */
    @Test
    public void aLinePastTheThresholdIsSplitAtWordBoundaries() {
        String line = SubtitleFixture.LONG_LINE;
        assertTrue("the fixture's long line must actually be past the threshold: " + line.length(),
                line.length() > LONG_SENTENCE_CHARS);

        List<SubtitleSegment> segments = new RuleSentenceBreaker(LONG_SENTENCE_CHARS)
                .breakSentences(java.util.Collections.singletonList(
                        new SubtitleSegment(new SubtitleSegmentId(TRACK, 0), 0, 4_000, line)));

        assertEquals("a line past the threshold must be cut in two at a word boundary",
                2, segments.size());
        assertEquals("the first piece starts where the line starts",
                0, segments.get(0).getStartTimeMs());
        assertEquals("the last piece ends where the line ends",
                4_000, segments.get(segments.size() - 1).getEndTimeMs());

        StringBuilder joined = new StringBuilder();
        for (SubtitleSegment segment : segments) {
            assertTrue("no piece may exceed the threshold: " + segment.getSourceText().length(),
                    segment.getSourceText().length() <= LONG_SENTENCE_CHARS);

            if (joined.length() > 0) joined.append(' ');
            joined.append(segment.getSourceText());
        }

        assertEquals("the pieces must reproduce the line exactly", line, joined.toString());
    }

    @Test
    public void thePipelineKeepsContentAndDropsOnlyWhatItShould() {
        SourceTimeline timeline = pipeline(SubtitleFixture.independentCases());
        List<String> texts = sourceTexts(timeline);
        String all = texts.toString();

        assertTrue("the manual English caption must survive", all.contains("Manual caption."));
        assertTrue("the auto-generated caption must survive", all.contains("ASR words arrive quickly"));
        assertTrue("the labelled short cue must survive", all.contains("one two three"));
        assertTrue("Japanese without spaces must survive", all.contains("字幕試験です"));
        assertTrue("Japanese manual captions must survive", all.contains("手動で作成した日本語の字幕です"));
        assertTrue("manual Chinese must survive", all.contains("这是人工中文字幕"));
        assertTrue("auto-generated Chinese must survive", all.contains("这是自动生成的中文字幕"));
        assertTrue("slow speech must survive", all.contains("Slow speech with a pause."));
        assertTrue("the overlap must be kept, not dropped", all.contains("Overlapping event"));
        assertTrue("the long line must still be present",
                all.contains("far past the splitter threshold"));

        assertFalse("non-speech noise must be dropped", all.contains("[music]"));
        assertEquals("a repeated caption must appear once", 1, count(texts, "After a gap"));
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

    /**
     * The JSON resource is documentation unless something checks it. This is what checks it:
     * the resource and the builder must describe the same events, so a category cannot be
     * renamed in one and not the other.
     */
    @Test
    public void theFixtureResourceMatchesTheIndependentCaseBuilder() throws Exception {
        String json = readResource("/ai-subtitle/fixtures/independent-cases.json");
        Map<String, Object> root = asMap(Helpers.convertToObj(json));
        assertNotNull("the fixture resource must be readable", root);

        List<?> events = (List<?>) root.get("events");
        List<SubtitleFixture.FixtureEvent> expected = SubtitleFixture.independentCases();

        assertNotNull("the fixture resource must list events", events);
        assertEquals("the resource must describe the same events as the builder",
                expected.size(), events.size());

        for (int i = 0; i < expected.size(); i++) {
            Map<String, Object> event = asMap(events.get(i));
            SubtitleFixture.FixtureEvent want = expected.get(i);

            assertNotNull("event " + i + " must be an object", event);
            assertEquals(want.getEventId(), event.get("eventId"));
            assertEquals(want.getStartTimeMs(), number(event.get("startMs")));
            assertEquals(want.getEndTimeMs(), number(event.get("endMs")));
            assertEquals(want.getText(), event.get("text"));
            assertEquals(want.getCaptionKind(), event.get("captionKind"));
            assertEquals(want.getLanguage(), event.get("language"));
            assertEquals(want.getProvenanceId(), event.get("provenanceId"));
        }
    }

    private static String readResource(String path) throws java.io.IOException {
        java.io.InputStream input = SubtitleFixturePipelineTest.class.getResourceAsStream(path);
        assertNotNull("missing test resource: " + path, input);

        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;

            while ((read = input.read(chunk)) > 0) buffer.write(chunk, 0, read);
            return new String(buffer.toByteArray(), java.nio.charset.Charset.forName("UTF-8"));
        } finally {
            input.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : -1;
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
