package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session;

/**
 * Immutable snapshot of a Translation Session at one point in time: identity, generation,
 * scheduling epoch, and lifecycle state.
 */
public final class TranslationSessionSnapshot {
    private final TranslationSessionId mSessionId;
    private final long mGeneration;
    private final long mEpoch;
    private final TranslationSession.State mState;

    public TranslationSessionSnapshot(TranslationSessionId sessionId, long generation, long epoch,
                                      TranslationSession.State state) {
        mSessionId = sessionId;
        mGeneration = generation;
        mEpoch = epoch;
        mState = state;
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

    public TranslationSession.State getState() {
        return mState;
    }
}
