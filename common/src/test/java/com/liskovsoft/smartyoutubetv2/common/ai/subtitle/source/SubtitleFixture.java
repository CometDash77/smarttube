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
}
