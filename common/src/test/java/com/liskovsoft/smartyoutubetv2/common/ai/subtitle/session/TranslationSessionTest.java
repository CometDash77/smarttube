package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM contract tests for {@link TranslationSession}: generation ownership, scheduling
 * epochs, the documented lifecycle state machine, idempotent repeated events, and terminal
 * close behavior.
 */
public class TranslationSessionTest {
    private static final TranslationProfile PROFILE =
            new TranslationProfile("profile-1", "openai-chat-completions", "https://api.example.com/v1",
                    "gpt-4o-mini", "prompt-1", 1, "zh");

    private static TranslationSessionId sessionId(String videoId, String trackId) {
        return new TranslationSessionId(videoId, new SourceTrackId(videoId, trackId, "en"),
                PROFILE, TranslationSessionId.ENGINE_SCHEMA_VERSION);
    }

    private static TranslationSession newSession(long generation) {
        return new TranslationSession(sessionId("video-1", "subtitle:en:asr-1"), generation);
    }

    @Test
    public void newSessionStartsLoadingSourceWithZeroEpoch() {
        TranslationSession session = newSession(1);

        assertEquals(TranslationSession.State.LOADING_SOURCE, session.getState());
        assertEquals(0, session.getEpoch());
        assertEquals(1, session.getGeneration());
        assertFalse(session.isPaused());
        assertFalse(session.isClosed());
    }

    @Test
    public void nullSessionIdIsRejected() {
        try {
            new TranslationSession(null, 1);
            fail("null session id must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void nonPositiveGenerationIsRejected() {
        for (long generation : new long[] {0, -1}) {
            try {
                new TranslationSession(sessionId("video-1", "subtitle:en:asr-1"), generation);
                fail("non-positive generation must be rejected: " + generation);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void markReadyAndMarkActiveFollowTheStateMachine() {
        TranslationSession session = newSession(1);

        session.markReady();
        assertEquals(TranslationSession.State.READY, session.getState());

        session.markActive();
        assertEquals(TranslationSession.State.ACTIVE, session.getState());
    }

    @Test
    public void markActiveIsIdempotent() {
        TranslationSession session = newSession(1);
        session.markReady();
        session.markActive();
        session.markActive();

        assertEquals(TranslationSession.State.ACTIVE, session.getState());
    }

    @Test
    public void pauseAndResumeToggleBetweenActiveAndPaused() {
        TranslationSession session = newSession(1);
        session.markReady();
        session.markActive();

        session.pause();
        assertTrue(session.isPaused());
        assertEquals(TranslationSession.State.PAUSED, session.getState());

        session.resume();
        assertEquals(TranslationSession.State.ACTIVE, session.getState());
        assertFalse(session.isPaused());
    }

    @Test
    public void pauseOutsideActiveStateIsIgnored() {
        TranslationSession session = newSession(1);

        session.pause();

        assertEquals(TranslationSession.State.LOADING_SOURCE, session.getState());
    }

    @Test
    public void resumeOutsidePausedStateIsIgnored() {
        TranslationSession session = newSession(1);
        session.markReady();

        session.resume();

        assertEquals(TranslationSession.State.READY, session.getState());
    }

    @Test
    public void repeatedPauseEventsAreIdempotent() {
        TranslationSession session = newSession(1);
        session.markReady();
        session.markActive();

        session.pause();
        session.pause();
        session.pause();

        assertEquals(TranslationSession.State.PAUSED, session.getState());
    }

    @Test
    public void sourceOnlyAndDegradedAreReachableAndRecoverable() {
        TranslationSession session = newSession(1);
        session.markReady();
        session.markActive();

        session.markSourceOnly();
        assertEquals(TranslationSession.State.SOURCE_ONLY, session.getState());

        session.markDegraded();
        assertEquals(TranslationSession.State.DEGRADED, session.getState());

        session.markActive();
        assertEquals(TranslationSession.State.ACTIVE, session.getState());
    }

    @Test
    public void seekAdvancesEpochEachTimeWithoutChangingIdentityOrState() {
        TranslationSession session = newSession(1);
        session.markReady();
        session.markActive();
        TranslationSessionId before = session.getSessionId();

        session.advanceEpoch();

        assertEquals(1, session.getEpoch());
        assertEquals(1, session.getGeneration());
        assertEquals(TranslationSession.State.ACTIVE, session.getState());
        assertSame("a seek must not change session identity", before, session.getSessionId());

        session.advanceEpoch();

        assertEquals(2, session.getEpoch());
        assertEquals(1, session.getGeneration());
    }

    @Test
    public void closeIsTerminalAndIdempotent() {
        TranslationSession session = newSession(1);
        session.markReady();
        session.markActive();

        session.close();
        assertTrue(session.isClosed());
        assertEquals(TranslationSession.State.CLOSED, session.getState());

        session.close();
        assertTrue(session.isClosed());
    }

    @Test
    public void closedSessionIgnoresFurtherTransitions() {
        TranslationSession session = newSession(1);
        session.close();

        session.markReady();
        session.markActive();
        session.pause();
        session.resume();
        session.markSourceOnly();
        session.markDegraded();
        session.advanceEpoch();

        assertEquals(TranslationSession.State.CLOSED, session.getState());
        assertEquals(0, session.getEpoch());
    }

    @Test
    public void ownsAcceptsOnlyCurrentGenerationAndEpoch() {
        TranslationSession session = newSession(7);
        session.advanceEpoch();

        assertTrue(session.owns(7, 1));
        assertFalse("stale generation", session.owns(6, 1));
        assertFalse("future generation", session.owns(8, 1));
        assertFalse("stale epoch", session.owns(7, 0));
        assertFalse("future epoch", session.owns(7, 2));
    }

    @Test
    public void ownsRejectsClosedSession() {
        TranslationSession session = newSession(1);

        session.close();

        assertFalse("no callback may own a closed session", session.owns(1, 0));
    }

    @Test
    public void snapshotCapturesCurrentIdentityGenerationEpochAndState() {
        TranslationSession session = newSession(5);
        session.markReady();
        session.markActive();
        session.advanceEpoch();
        session.pause();

        TranslationSessionSnapshot snapshot = session.snapshot();

        assertEquals(session.getSessionId(), snapshot.getSessionId());
        assertEquals(5, snapshot.getGeneration());
        assertEquals(1, snapshot.getEpoch());
        assertEquals(TranslationSession.State.PAUSED, snapshot.getState());
    }

    @Test
    public void everyIdentityFieldChangeRequiresADistinctSessionWithNewGeneration() {
        TranslationSession videoChange = new TranslationSession(sessionId("video-1", "subtitle:en:asr-1"), 1);
        TranslationSession nextVideo = new TranslationSession(sessionId("video-2", "subtitle:en:asr-1"), 2);
        TranslationSession trackChange = new TranslationSession(sessionId("video-1", "subtitle:ja:asr-2"), 3);

        assertFalse(videoChange.getSessionId().equals(nextVideo.getSessionId()));
        assertFalse(videoChange.getSessionId().equals(trackChange.getSessionId()));
        assertNotSame(videoChange.getSessionId(), nextVideo.getSessionId());

        assertTrue(nextVideo.owns(2, 0));
        assertTrue(trackChange.owns(3, 0));
        assertFalse("the previous generation must never own callbacks of a new identity",
                videoChange.owns(2, 0));
        assertFalse(videoChange.owns(3, 0));
    }
}
