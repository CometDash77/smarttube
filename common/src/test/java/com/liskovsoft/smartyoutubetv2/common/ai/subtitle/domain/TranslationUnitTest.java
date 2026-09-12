package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link TranslationUnit}.
 */
public class TranslationUnitTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video-1", "track-1", "en");
    private static final SourceTrackId OTHER_TRACK = new SourceTrackId("video-1", "track-2", "en");

    private static SubtitleSegmentId id(int index) {
        return new SubtitleSegmentId(TRACK, index);
    }

    @Test
    public void unitExposesOrderedIdsAndSourceText() {
        TranslationUnit unit = new TranslationUnit(Arrays.asList(id(0), id(1), id(2)), "one two three");

        assertEquals(3, unit.getSegmentCount());
        assertEquals(id(0), unit.getFirstSegmentId());
        assertEquals(id(2), unit.getLastSegmentId());
        assertEquals("one two three", unit.getSourceText());
        assertEquals(Arrays.asList(id(0), id(1), id(2)), unit.getSegmentIds());
    }

    @Test
    public void returnedSegmentIdsCannotBeMutatedByTheCaller() {
        List<SubtitleSegmentId> mutable = new ArrayList<>();
        mutable.add(id(0));
        mutable.add(id(1));

        TranslationUnit unit = new TranslationUnit(mutable, "text");
        mutable.add(id(2));

        assertEquals("constructor must copy its input", 2, unit.getSegmentCount());

        try {
            unit.getSegmentIds().add(id(3));
            fail("returned list must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void emptyOrNullSegmentListIsRejected() {
        try {
            new TranslationUnit(null, "text");
            fail("null segment list must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new TranslationUnit(new ArrayList<SubtitleSegmentId>(), "text");
            fail("empty segment list must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void outOfOrderSegmentIdsAreRejected() {
        try {
            new TranslationUnit(Arrays.asList(id(1), id(0)), "text");
            fail("unordered segment ids must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void nonContiguousSegmentIdsAreRejected() {
        try {
            new TranslationUnit(Arrays.asList(id(0), id(2)), "text");
            fail("non-contiguous segment ids must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void duplicateSegmentIdsAreRejected() {
        try {
            new TranslationUnit(Arrays.asList(id(1), id(1)), "text");
            fail("duplicate segment ids must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void mixedSourceTracksAreRejected() {
        try {
            new TranslationUnit(Arrays.asList(id(0), new SubtitleSegmentId(OTHER_TRACK, 1)), "text");
            fail("mixed source tracks must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void blankSourceTextIsRejected() {
        for (String blank : new String[] {null, "", "   "}) {
            try {
                new TranslationUnit(Arrays.asList(id(0)), blank);
                fail("blank source text must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void nullSegmentIdInsideTheListIsRejected() {
        List<SubtitleSegmentId> ids = new ArrayList<>();
        ids.add(id(0));
        ids.add(null);

        try {
            new TranslationUnit(ids, "text");
            fail("null segment id must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void equalUnitsAreEqualWithEqualHashes() {
        TranslationUnit first = new TranslationUnit(Arrays.asList(id(4), id(5)), "alpha");
        TranslationUnit second = new TranslationUnit(Arrays.asList(id(4), id(5)), "alpha");

        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void unitsWithDifferentCoverageOrTextAreNotEqual() {
        TranslationUnit single = new TranslationUnit(Arrays.asList(id(0)), "alpha");
        TranslationUnit doubleUnit = new TranslationUnit(Arrays.asList(id(0), id(1)), "alpha");
        TranslationUnit otherText = new TranslationUnit(Arrays.asList(id(0)), "beta");

        assertFalse(single.equals(doubleUnit));
        assertFalse(single.equals(otherText));
    }
}
