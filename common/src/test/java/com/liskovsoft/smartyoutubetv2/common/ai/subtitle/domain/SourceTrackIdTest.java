package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link SourceTrackId}.
 */
public class SourceTrackIdTest {
    @Test
    public void equalFieldValuesProduceEqualObjectsWithEqualHashes() {
        SourceTrackId first = new SourceTrackId("video-1", "track-1", "en");
        SourceTrackId second = new SourceTrackId("video-1", "track-1", "en");

        assertTrue(first.equals(second));
        assertTrue(second.equals(first));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void differentVideoIdsAreNotEqual() {
        SourceTrackId first = new SourceTrackId("video-1", "track-1", "en");
        SourceTrackId second = new SourceTrackId("video-2", "track-1", "en");

        assertFalse(first.equals(second));
    }

    @Test
    public void differentTrackIdsAreNotEqual() {
        SourceTrackId first = new SourceTrackId("video-1", "track-1", "en");
        SourceTrackId second = new SourceTrackId("video-1", "track-2", "en");

        assertFalse(first.equals(second));
    }

    @Test
    public void differentLanguagesAreNotEqual() {
        SourceTrackId first = new SourceTrackId("video-1", "track-1", "en");
        SourceTrackId second = new SourceTrackId("video-1", "track-1", "ja");

        assertFalse(first.equals(second));
    }

    @Test
    public void nullIsNotEqualAndDifferentTypeIsNotEqual() {
        SourceTrackId id = new SourceTrackId("video-1", "track-1", "en");

        assertFalse(id.equals(null));
        assertFalse(id.equals("video-1"));
    }

    @Test
    public void gettersExposeExactConstructorValues() {
        SourceTrackId id = new SourceTrackId("dQw4w9WgXcQ", "asr-en-1", "en");

        assertEquals("dQw4w9WgXcQ", id.getVideoId());
        assertEquals("asr-en-1", id.getTrackId());
        assertEquals("en", id.getLanguage());
    }

    @Test
    public void blankVideoIdIsRejected() {
        for (String blank : new String[] {null, "", "   "}) {
            try {
                new SourceTrackId(blank, "track-1", "en");
                fail("blank video id must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void blankTrackIdIsRejected() {
        for (String blank : new String[] {null, "", "  "}) {
            try {
                new SourceTrackId("video-1", blank, "en");
                fail("blank track id must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void blankLanguageIsRejected() {
        for (String blank : new String[] {null, "", "  "}) {
            try {
                new SourceTrackId("video-1", "track-1", blank);
                fail("blank language must be rejected: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}
