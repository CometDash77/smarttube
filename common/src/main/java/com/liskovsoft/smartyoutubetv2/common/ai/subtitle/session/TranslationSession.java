package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session;

/**
 * Lifecycle state of one Translation Session for a single {@link TranslationSessionId}.
 *
 * <p>A session owns the monotonic generation that identifies its session identity, the
 * scheduling epoch advanced by seeks, and the lifecycle state from the architecture state
 * machine. Identity changes are expressed by creating a new session with the next
 * generation; a seek only advances the epoch.</p>
 *
 * <p>All transitions are idempotent: repeated or out-of-order lifecycle events leave the
 * session in a valid state and never throw. A closed session ignores every further
 * transition.</p>
 */
public final class TranslationSession {
    public enum State {
        LOADING_SOURCE,
        READY,
        ACTIVE,
        PAUSED,
        SOURCE_ONLY,
        DEGRADED,
        CLOSED
    }

    private final TranslationSessionId mSessionId;
    private final long mGeneration;
    private long mEpoch;
    private State mState;

    public TranslationSession(TranslationSessionId sessionId, long generation) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be positive: " + generation);
        }

        mSessionId = sessionId;
        mGeneration = generation;
        mState = State.LOADING_SOURCE;
    }

    public TranslationSessionId getSessionId() {
        return mSessionId;
    }

    public long getGeneration() {
        return mGeneration;
    }

    public long getEpoch() {
        return mEpoch;
    }

    public State getState() {
        return mState;
    }

    public boolean isClosed() {
        return mState == State.CLOSED;
    }

    public boolean isPaused() {
        return mState == State.PAUSED;
    }

    public void markReady() {
        if (mState == State.LOADING_SOURCE) {
            mState = State.READY;
        }
    }

    public void markActive() {
        if (mState != State.CLOSED) {
            mState = State.ACTIVE;
        }
    }

    public void pause() {
        if (mState == State.ACTIVE) {
            mState = State.PAUSED;
        }
    }

    public void resume() {
        if (mState == State.PAUSED) {
            mState = State.ACTIVE;
        }
    }

    public void markSourceOnly() {
        if (mState != State.CLOSED) {
            mState = State.SOURCE_ONLY;
        }
    }

    public void markDegraded() {
        if (mState != State.CLOSED) {
            mState = State.DEGRADED;
        }
    }

    public void close() {
        mState = State.CLOSED;
    }

    /** Advances the scheduling epoch; used when the player seeked. */
    public void advanceEpoch() {
        if (mState != State.CLOSED) {
            mEpoch++;
        }
    }

    public TranslationSessionSnapshot snapshot() {
        return new TranslationSessionSnapshot(mSessionId, mGeneration, mEpoch, mState);
    }

    /**
     * Ownership check for one request or callback: true only while the given generation and
     * epoch still describe this session.
     */
    public boolean owns(long generation, long epoch) {
        return !isClosed() && mGeneration == generation && mEpoch == epoch;
    }
}
