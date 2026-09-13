package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable full-timeline result of the source adapter: every normalized segment paired with
 * the translation units that cover it. {@link #unitAt(long)} maps playback position back to
 * one unit without duplicating a second timeline model.
 */
public final class SourceTimeline {
    private final List<SubtitleSegment> mSegments;
    private final List<TranslationUnit> mUnits;

    SourceTimeline(List<SubtitleSegment> segments, List<TranslationUnit> units) {
        mSegments = Collections.unmodifiableList(new ArrayList<>(segments));
        mUnits = Collections.unmodifiableList(new ArrayList<>(units));
    }

    public List<SubtitleSegment> getSegments() {
        return mSegments;
    }

    public List<TranslationUnit> getUnits() {
        return mUnits;
    }

    /** Returns the unit whose time range covers {@code positionMs}, or null. */
    public TranslationUnit unitAt(long positionMs) {
        for (SubtitleSegment segment : mSegments) {
            if (positionMs >= segment.getStartTimeMs() && positionMs < segment.getEndTimeMs()) {
                return unitContainingSegment(segment.getId().getIndex());
            }
        }
        return null;
    }

    /** Returns the unit whose segment-ID range includes the given segment index. */
    TranslationUnit unitContainingSegment(int segmentIndex) {
        for (TranslationUnit unit : mUnits) {
            List<com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId> ids =
                    unit.getSegmentIds();
            if (segmentIndex >= ids.get(0).getIndex()
                    && segmentIndex <= ids.get(ids.size() - 1).getIndex()) {
                return unit;
            }
        }
        return null;
    }
}
