package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;

import java.util.Arrays;
import java.util.List;

/** Independently authored synthetic fixture builder; no reference repository data is copied. */
public final class SubtitleFixture {
    private SubtitleFixture() { }

    public static List<SourceCue> noisyOverlapAndDuplicate() {
        return Arrays.asList(new SourceCue(0, 1_000, "Hello"),
                new SourceCue(900, 1_800, "Hello"),
                new SourceCue(1_700, 2_500, "<b>world</b>"),
                new SourceCue(2_500, 3_000, "[music]"));
    }

    public static List<SourceCue> noSpaceAsr() {
        return Arrays.asList(new SourceCue(0, 1_200, "字幕測試"));
    }

    /** The metadata shape used by the independent fixture resource. */
    public static List<FixtureEvent> independentCases() {
        return Arrays.asList(
                new FixtureEvent("manual-001", 0, 900, "Manual caption.", "manual", "en", "synthetic-manual"),
                new FixtureEvent("asr-001", 900, 1_700, "ASR words arrive quickly", "asr", "en", "synthetic-asr"),
                new FixtureEvent("word-001", 1_700, 2_400, "one two three", "asr-word-timing", "en", "synthetic-word-timing"),
                new FixtureEvent("ja-001", 2_400, 3_200, "字幕試験です", "asr", "ja", "synthetic-no-space"),
                new FixtureEvent("noise-001", 3_200, 3_500, "[music]", "noise", "en", "synthetic-noise"),
                new FixtureEvent("fast-001", 3_500, 3_850, "Fast speech.", "asr", "en", "synthetic-fast"),
                new FixtureEvent("slow-001", 3_850, 5_000, "Slow speech with a pause.", "manual", "en", "synthetic-slow"),
                new FixtureEvent("overlap-001", 4_900, 5_600, "Overlapping event", "asr", "en", "synthetic-overlap"),
                new FixtureEvent("gap-001", 6_000, 6_700, "After a gap", "manual", "en", "synthetic-gap"),
                new FixtureEvent("duplicate-001", 6_700, 7_100, "After a gap", "asr", "en", "synthetic-duplicate"),
                new FixtureEvent("long-001", 7_100, 8_900, "A deliberately long subtitle segment for deterministic splitting.", "manual", "en", "synthetic-long"),
                new FixtureEvent("zh-manual-001", 8_900, 10_100, "这是人工中文字幕。", "manual", "zh", "synthetic-zh-manual"),
                new FixtureEvent("zh-asr-001", 10_100, 11_400, "这是自动生成的中文字幕", "asr", "zh", "synthetic-zh-asr"));
    }

    public static final class FixtureEvent {
        private final String mEventId;
        private final long mStartTimeMs;
        private final long mEndTimeMs;
        private final String mText;
        private final String mCaptionKind;
        private final String mLanguage;
        private final String mProvenanceId;

        FixtureEvent(String eventId, long startTimeMs, long endTimeMs, String text,
                     String captionKind, String language, String provenanceId) {
            mEventId = eventId;
            mStartTimeMs = startTimeMs;
            mEndTimeMs = endTimeMs;
            mText = text;
            mCaptionKind = captionKind;
            mLanguage = language;
            mProvenanceId = provenanceId;
        }

        public String getEventId() { return mEventId; }
        public long getStartTimeMs() { return mStartTimeMs; }
        public long getEndTimeMs() { return mEndTimeMs; }
        public String getText() { return mText; }
        public String getCaptionKind() { return mCaptionKind; }
        public String getLanguage() { return mLanguage; }
        public String getProvenanceId() { return mProvenanceId; }
    }
}
