package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link SourceCue}.
 */
public class SourceCueTest {
    @Test
    public void gettersExposeExactConstructorValues() {
        SourceCue cue = new SourceCue(1000, 2500, "Hello world");

        assertEquals(1000, cue.getStartTimeMs());
        assertEquals(2500, cue.getEndTimeMs());
        assertEquals("Hello world", cue.getText());
    }

    @Test
    public void equalValuesProduceEqualObjectsWithEqualHashes() {
        SourceCue first = new SourceCue(0, 1000, "text");
        SourceCue second = new SourceCue(0, 1000, "text");

        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void differentTextIsNotEqual() {
        assertFalse(new SourceCue(0, 1000, "alpha").equals(new SourceCue(0, 1000, "beta")));
    }

    @Test
    public void differentTimingIsNotEqual() {
        assertFalse(new SourceCue(0, 1000, "text").equals(new SourceCue(0, 2000, "text")));
    }

    @Test
    public void negativeStartTimeIsRejected() {
        try {
            new SourceCue(-1, 1000, "text");
            fail("negative start must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void reversedRangeIsRejected() {
        try {
            new SourceCue(2000, 1000, "text");
            fail("reversed range must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void zeroLengthRangeIsRejected() {
        try {
            new SourceCue(1000, 1000, "text");
            fail("zero-length range must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void nullTextIsRejected() {
        try {
            new SourceCue(0, 1000, null);
            fail("null text must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void emptyTextIsAllowedBeforeNormalization() {
        SourceCue cue = new SourceCue(0, 1000, "");

        assertEquals("", cue.getText());
    }
}
