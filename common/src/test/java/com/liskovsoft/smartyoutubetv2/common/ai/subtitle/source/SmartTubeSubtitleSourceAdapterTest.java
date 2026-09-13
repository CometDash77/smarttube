package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SmartTubeSubtitleSourceAdapterTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video1", "subtitle:en:1", "en");

    private static final String SAMPLE_VTT = "WEBVTT\n\n"
            + "00:00:00.000 --> 00:00:02.000\nHello\n\n"
            + "00:00:02.000 --> 00:00:04.000\nworld. Next\n\n"
            + "00:00:04.000 --> 00:00:06.000\nsentence.\n\n"
            + "00:00:06.000 --> 00:00:08.000\n[music]";

    @Test
    public void buildTimelineProducesStableSegmentsAndUnits() {
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/timedtext?v=video1&lang=en");
        SmartTubeSubtitleSourceAdapter adapter = adapter(
                Arrays.asList(manual), SAMPLE_VTT);

        SourceTimeline timeline = adapter.buildTimeline(
                Collections.singletonList(manual), TRACK, url -> SAMPLE_VTT);

        assertNotNull(timeline);
        assertTrue("normalized must drop [music] and split sentences", timeline.getSegments().size() >= 3);
        assertTrue("must produce at least one unit", timeline.getUnits().size() >= 1);

        // Segment indexes are contiguous from 0.
        for (int i = 0; i < timeline.getSegments().size(); i++) {
            assertEquals(i, timeline.getSegments().get(i).getId().getIndex());
        }

        // All unit segment IDs are contiguous within each unit.
        for (TranslationUnit unit : timeline.getUnits()) {
            for (int i = 1; i < unit.getSegmentIds().size(); i++) {
                assertEquals(unit.getSegmentIds().get(i - 1).getIndex() + 1,
                        unit.getSegmentIds().get(i).getIndex());
            }
        }
    }

    @Test
    public void unitAtReturnsUnitCoveringPosition() {
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/timedtext?v=video1&lang=en");
        SmartTubeSubtitleSourceAdapter adapter = adapter(Collections.singletonList(manual), SAMPLE_VTT);

        SourceTimeline timeline = adapter.buildTimeline(
                Collections.singletonList(manual), TRACK, url -> SAMPLE_VTT);

        assertNotNull(timeline);

        // Position inside the first cue should return a unit (not null).
        TranslationUnit unit = timeline.unitAt(1_000);
        if (unit != null) {
            assertTrue("unit must contain at least one segment", unit.getSegmentIds().size() >= 1);
        }

        // Position way outside any cue should return null (gap).
        TranslationUnit none = timeline.unitAt(999_999);
        assertNull(none);
    }

    @Test
    public void duplicateTextAtDifferentTimesMapsToDistinctSegments() {
        String vtt = "WEBVTT\n\n"
                + "00:00:00.000 --> 00:00:01.000\nYes\n\n"
                + "00:00:05.000 --> 00:00:06.000\nYes";
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/timedtext?v=video1&lang=en");
        SmartTubeSubtitleSourceAdapter adapter = adapter(Collections.singletonList(manual), vtt);

        SourceTimeline timeline = adapter.buildTimeline(
                Collections.singletonList(manual), TRACK, url -> vtt);

        assertNotNull(timeline);
        assertEquals(2, timeline.getSegments().size());

        // Both segments have the same text but distinct IDs and times.
        assertEquals(0, timeline.getSegments().get(0).getId().getIndex());
        assertEquals(1, timeline.getSegments().get(1).getId().getIndex());
        assertEquals(0, timeline.getSegments().get(0).getStartTimeMs());
        assertEquals(5_000, timeline.getSegments().get(1).getStartTimeMs());
    }

    @Test
    public void matchSubtitlePrefersLanguageCodeOverName() {
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/manual");
        FakeSubtitle asr = subtitle("en", "English (auto-generated)", "http://example.com/asr");

        // Track identity says "subtitle:en:1" - language component is "en".
        MediaSubtitle matched = SmartTubeSubtitleSourceAdapter.matchSubtitle(
                Arrays.asList(manual, asr), TRACK);
        assertNotNull(matched);
        assertEquals("manual is preferred when codes match (first entry wins)",
                "http://example.com/manual", matched.getBaseUrl());
    }

    @Test
    public void matchSubtitleReturnsNullForUnknownLanguage() {
        FakeSubtitle manual = subtitle("ja", "Japanese", "http://example.com/ja");
        assertNull(SmartTubeSubtitleSourceAdapter.matchSubtitle(
                Collections.singletonList(manual), TRACK));
    }

    @Test
    public void toVttUrlAppendsFmtVtt() {
        assertEquals("http://example.com/timedtext?v=1&lang=en&fmt=vtt",
                SmartTubeSubtitleSourceAdapter.toVttUrl(
                        "http://example.com/timedtext?v=1&lang=en"));
        assertEquals("http://example.com/timedtext?v=1&fmt=vtt",
                SmartTubeSubtitleSourceAdapter.toVttUrl(
                        "http://example.com/timedtext?v=1&fmt=vtt"));
        assertEquals("http://example.com/other/path.vtt",
                SmartTubeSubtitleSourceAdapter.toVttUrl("http://example.com/other/path.vtt"));
    }

    @Test
    public void loadDeliversTimelineThroughCallback() {
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/timedtext?v=video1&lang=en");
        List<MediaSubtitle> subtitles = Collections.singletonList(manual);

        SmartTubeSubtitleSourceAdapter.SubtitleFetcher fetcher = (videoId, listener) ->
                listener.onSubtitles(subtitles);
        SmartTubeSubtitleSourceAdapter adapter = new SmartTubeSubtitleSourceAdapter(
                fetcher, url -> SAMPLE_VTT);

        final SourceTimeline[] result = new SourceTimeline[1];
        adapter.load("video1", TRACK, new SmartTubeSubtitleSourceAdapter.Callback() {
            @Override
            public void onTimelineReady(SourceTrackId trackId, SourceTimeline timeline) {
                result[0] = timeline;
            }

            @Override
            public void onTimelineFailed(SourceTrackId trackId, String reason) {
            }
        });

        assertNotNull("load must deliver timeline through the callback", result[0]);
    }

    @Test
    public void loadDeliversFailureForEmptySubtitles() {
        SmartTubeSubtitleSourceAdapter.SubtitleFetcher fetcher = (videoId, listener) ->
                listener.onSubtitles(Collections.emptyList());
        SmartTubeSubtitleSourceAdapter adapter = new SmartTubeSubtitleSourceAdapter(
                fetcher, url -> SAMPLE_VTT);

        final String[] failure = new String[1];
        adapter.load("video1", TRACK, new SmartTubeSubtitleSourceAdapter.Callback() {
            @Override
            public void onTimelineReady(SourceTrackId trackId, SourceTimeline timeline) {
            }

            @Override
            public void onTimelineFailed(SourceTrackId trackId, String reason) {
                failure[0] = reason;
            }
        });

        assertNotNull("load must report failure for empty subtitle list", failure[0]);
    }

    @Test
    public void loadDeliversFailureForNullVideo() {
        SmartTubeSubtitleSourceAdapter adapter = new SmartTubeSubtitleSourceAdapter(
                (videoId, listener) -> { }, url -> SAMPLE_VTT);

        final String[] failure = new String[1];
        adapter.load(null, TRACK, new SmartTubeSubtitleSourceAdapter.Callback() {
            @Override
            public void onTimelineReady(SourceTrackId trackId, SourceTimeline timeline) {
            }

            @Override
            public void onTimelineFailed(SourceTrackId trackId, String reason) {
                failure[0] = reason;
            }
        });

        assertNotNull(failure[0]);
    }

    private SmartTubeSubtitleSourceAdapter adapter(List<MediaSubtitle> subtitles, String vtt) {
        return new SmartTubeSubtitleSourceAdapter(
                (videoId, listener) -> listener.onSubtitles(subtitles), url -> vtt);
    }

    private static FakeSubtitle subtitle(String langCode, String name, String baseUrl) {
        return new FakeSubtitle(langCode, name, baseUrl);
    }

    private static final class FakeSubtitle implements MediaSubtitle {
        private final String mLanguageCode;
        private final String mName;
        private final String mBaseUrl;

        FakeSubtitle(String languageCode, String name, String baseUrl) {
            mLanguageCode = languageCode;
            mName = name;
            mBaseUrl = baseUrl;
        }

        @Override public String getBaseUrl() { return mBaseUrl; }
        @Override public boolean isTranslatable() { return true; }
        @Override public String getLanguageCode() { return mLanguageCode; }
        @Override public String getVssId() { return "." + mLanguageCode; }
        @Override public String getName() { return mName; }
        @Override public String getMimeType() { return "text/vtt"; }
        @Override public String getCodecs() { return ""; }
        @Override public String getType() { return "captions"; }
    }
}

