package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link SubtitleSegmentId} and {@link SubtitleSegment}.
 */
public class SubtitleSegmentTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "track-1", "en");
    private static final SourceTrackId OTHER_TRACK = new SourceTrackId("video-1", "track-2", "en");

    @Test
    public void segmentIdEqualityIncludesTrackAndIndex() {
        SubtitleSegmentId first = new SubtitleSegmentId(TRACK, 3);
        SubtitleSegmentId second = new SubtitleSegmentId(TRACK, 3);

        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void segmentIdsWithDifferentIndexesAreNotEqual() {
        assertFalse(new SubtitleSegmentId(TRACK, 3).equals(new SubtitleSegmentId(TRACK, 4)));
    }

    @Test
    public void segmentIdsWithDifferentTracksAreNotEqual() {
        assertFalse(new SubtitleSegmentId(TRACK, 3).equals(new SubtitleSegmentId(OTHER_TRACK, 3)));
    }

    @Test
    public void segmentIdGettersExposeExactValues() {
        SubtitleSegmentId id = new SubtitleSegmentId(TRACK, 7);

        assertEquals(TRACK, id.getSourceTrackId());
        assertEquals(7, id.getIndex());
    }

    @Test
    public void nullTrackInSegmentIdIsRejected() {
        try {
            new SubtitleSegmentId(null, 0);
            fail("null track must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void negativeSegmentIndexIsRejected() {
        try {
            new SubtitleSegmentId(TRACK, -1);
            fail("negative index must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void segmentGettersExposeExactConstructorValues() {
        SubtitleSegmentId id = new SubtitleSegmentId(TRACK, 2);
        SubtitleSegment segment = new SubtitleSegment(id, 5000, 7000, "normalized text");

        assertEquals(id, segment.getId());
        assertEquals(5000, segment.getStartTimeMs());
        assertEquals(7000, segment.getEndTimeMs());
        assertEquals("normalized text", segment.getSourceText());
    }

    @Test
    public void sameTextAtDifferentSegmentIdsIsNotEqual() {
        SubtitleSegment first = new SubtitleSegment(new SubtitleSegmentId(TRACK, 0), 0, 1000, "Yeah");
        SubtitleSegment second = new SubtitleSegment(new SubtitleSegmentId(TRACK, 9), 90000, 91000, "Yeah");

        assertFalse("same text at different timeline positions must stay distinct", first.equals(second));
    }

    @Test
    public void equalValuesProduceEqualObjectsWithEqualHashes() {
        SubtitleSegment first = new SubtitleSegment(new SubtitleSegmentId(TRACK, 1), 1000, 2000, "text");
        SubtitleSegment second = new SubtitleSegment(new SubtitleSegmentId(TRACK, 1), 1000, 2000, "text");

        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void segmentInvalidTimeRangeIsRejected() {
        SubtitleSegmentId id = new SubtitleSegmentId(TRACK, 0);

        try {
            new SubtitleSegment(id, 2000, 1000, "text");
            fail("reversed range must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new SubtitleSegment(id, 0, 0, "text");
            fail("zero-length range must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void negativeSegmentStartIsRejected() {
        try {
            new SubtitleSegment(new SubtitleSegmentId(TRACK, 0), -5, 1000, "text");
            fail("negative start must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void blankSegmentTextIsRejected() {
        SubtitleSegmentId id = new SubtitleSegmentId(TRACK, 0);

        for (String blank : new String[] {null, "", "   "}) {
            try {
                new SubtitleSegment(id, 0, 1000, blank);
                fail("blank normalized text must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void nullSegmentIdIsRejected() {
        try {
            new SubtitleSegment(null, 0, 1000, "text");
            fail("null id must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
