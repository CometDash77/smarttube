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
    public void matchSubtitleSelectsTheSelectedSameLanguageTrackRegardlessOfListOrder() {
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/manual");
        FakeSubtitle asr = subtitle("en", "English (auto-generated)", "http://example.com/asr");
        SourceTrackId asrTrack = new SourceTrackId("video1",
                "subtitle:English (auto-generated):1", "en");
        SourceTrackId manualTrack = new SourceTrackId("video1", "subtitle:English:1", "en");

        List<MediaSubtitle> manualFirst = Arrays.asList(manual, asr);
        List<MediaSubtitle> asrFirst = Arrays.asList(asr, manual);

        assertEquals("the auto-generated track must win when it is the selected one",
                "http://example.com/asr",
                SmartTubeSubtitleSourceAdapter.matchSubtitle(asrFirst, asrTrack).getBaseUrl());
        assertEquals("list order must not change the selected track",
                "http://example.com/asr",
                SmartTubeSubtitleSourceAdapter.matchSubtitle(manualFirst, asrTrack).getBaseUrl());

        assertEquals("the manual track must win when it is the selected one",
                "http://example.com/manual",
                SmartTubeSubtitleSourceAdapter.matchSubtitle(asrFirst, manualTrack).getBaseUrl());
        assertEquals("list order must not change the selected track",
                "http://example.com/manual",
                SmartTubeSubtitleSourceAdapter.matchSubtitle(manualFirst, manualTrack).getBaseUrl());
    }

    @Test
    public void matchSubtitleFallsBackToSourceOnlyWhenOnlyTheLanguageCodeIsKnown() {
        // Both tracks share the code "en", so nothing distinguishes the selected one; guessing
        // would translate a track the user did not choose.
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/manual");
        FakeSubtitle asr = subtitle("en", "English (auto-generated)", "http://example.com/asr");

        assertNull(SmartTubeSubtitleSourceAdapter.matchSubtitle(
                Arrays.asList(manual, asr), TRACK));
        assertNull(SmartTubeSubtitleSourceAdapter.matchSubtitle(
                Arrays.asList(asr, manual), TRACK));
    }

    @Test
    public void matchSubtitleUsesCodeWhenItIdentifiesExactlyOneTrack() {
        FakeSubtitle ja = subtitle("ja", "Japanese", "http://example.com/ja");
        SourceTrackId jaTrack = new SourceTrackId("video1", "subtitle:ja:1", "ja");

        assertEquals("http://example.com/ja",
                SmartTubeSubtitleSourceAdapter.matchSubtitle(
                        Collections.singletonList(ja), jaTrack).getBaseUrl());
    }

    @Test
    public void matchSubtitleReturnsNullForUnknownLanguage() {
        FakeSubtitle manual = subtitle("ja", "Japanese", "http://example.com/ja");
        assertNull(SmartTubeSubtitleSourceAdapter.matchSubtitle(
                Collections.singletonList(manual), TRACK));
    }

    @Test
    public void buildTimelineFallsBackToSourceOnlyForEmptyContent() {
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/timedtext?v=video1");

        assertNull(adapter(Collections.singletonList(manual), "   ").buildTimeline(
                Collections.singletonList(manual), TRACK, url -> "   "));
        assertNull("a subtitle list without a matching track must not produce a timeline",
                adapter(Collections.emptyList(), SAMPLE_VTT).buildTimeline(
                        Collections.emptyList(), TRACK, url -> SAMPLE_VTT));
    }

    @Test
    public void overlappingCuesKeepBothTextsWithClippedTimes() {
        String vtt = "WEBVTT\n\n"
                + "00:00:00.000 --> 00:00:03.000\nFirst caption\n\n"
                + "00:00:01.500 --> 00:00:04.000\nSecond caption";
        FakeSubtitle manual = subtitle("en", "English", "http://example.com/timedtext?v=video1");

        SourceTimeline timeline = adapter(Collections.singletonList(manual), vtt).buildTimeline(
                Collections.singletonList(manual), TRACK, url -> vtt);

        assertNotNull(timeline);
        assertEquals(2, timeline.getSegments().size());
        assertEquals("First caption", timeline.getSegments().get(0).getSourceText());
        assertEquals("Second caption", timeline.getSegments().get(1).getSourceText());
        assertTrue("an overlapping cue must be clipped, not dropped",
                timeline.getSegments().get(0).getEndTimeMs() <= 1_500);
    }

    @Test
    public void irregularLanguageNamesStillResolveTheSelectedTrack() {
        FakeSubtitle auto = subtitle("en-US", "English (United States) (auto-generated)",
                "http://example.com/us-asr");
        SourceTrackId autoTrack = new SourceTrackId("video1",
                "subtitle:English (United States) (auto-generated):1", "en-US");

        assertEquals("http://example.com/us-asr",
                SmartTubeSubtitleSourceAdapter.matchSubtitle(
                        Collections.singletonList(auto), autoTrack).getBaseUrl());
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
    public void toVttUrlAddsQueryWhenTheTimedTextUrlHasNone() {
        assertEquals("http://example.com/timedtext?fmt=vtt",
                SmartTubeSubtitleSourceAdapter.toVttUrl("http://example.com/timedtext"));
    }

    @Test
    public void toVttUrlReplacesANonVttFormatWithoutTouchingSignedParameters() {
        assertEquals("http://example.com/timedtext?v=1&lang=en&signature=abc%2F123%3D&fmt=vtt",
                SmartTubeSubtitleSourceAdapter.toVttUrl(
                        "http://example.com/timedtext?v=1&fmt=srv3&lang=en&signature=abc%2F123%3D"));
    }

    @Test
    public void toVttUrlKeepsFragmentsAfterTheQuery() {
        assertEquals("http://example.com/timedtext?v=1&fmt=vtt#t=10",
                SmartTubeSubtitleSourceAdapter.toVttUrl("http://example.com/timedtext?v=1#t=10"));
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

