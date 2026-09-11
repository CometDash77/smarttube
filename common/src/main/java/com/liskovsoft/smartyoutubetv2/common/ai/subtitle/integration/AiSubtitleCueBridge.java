package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Renderer-facing bridge between SmartTube's decoded cue list and the AI subtitle feature.
 *
 * <p>{@code SubtitleManager} knows only {@link #process}; lifecycle methods are driven by
 * {@code AiSubtitleController}. The bridge owns no Activity, View, SubtitleView, Video, or
 * PlaybackPresenter reference.</p>
 *
 * <p>M02 scope: the in-memory lookup key is the normalized source text and results live only
 * for the current generation. M03 replaces this with timeline/segment identity before any
 * real provider is allowed.</p>
 */
public class AiSubtitleCueBridge {
    private static AiSubtitleCueBridge sInstance;
    private static Context sContext;

    private final BooleanSupplier mEnabledSupplier;
    private final TranslationProvider mProvider;
    private final Map<String, String> mCompleted = new HashMap<>();
    private final Map<String, PendingRequest> mInFlight = new HashMap<>();
    private long mGeneration;
    private long mEpoch;
    private long mRequestIdSeed;
    private boolean mPaused;
    private boolean mWasEnabled;

    /**
     * Production constructor: reads the persisted opt-in switch through the dedicated
     * settings store.
     */
    AiSubtitleCueBridge(Context context, TranslationProvider provider) {
        this(() -> AiSubtitleData.instance(context).isEnabled(), provider);
    }

    /**
     * Android-free constructor used by unit tests; the exact visibility is reduced where
     * tests and integration permit.
     */
    @VisibleForTesting
    AiSubtitleCueBridge(BooleanSupplier enabledSupplier, TranslationProvider provider) {
        mEnabledSupplier = enabledSupplier;
        mProvider = provider;
    }

    public static synchronized AiSubtitleCueBridge instance(Context context) {
        Context appContext = context.getApplicationContext();

        if (sInstance == null || sContext != appContext) {
            sInstance = new AiSubtitleCueBridge(appContext, new FakeTranslationProvider());
            sContext = appContext;
        }

        return sInstance;
    }

    @VisibleForTesting
    static synchronized void resetInstanceForTesting() {
        sInstance = null;
        sContext = null;
    }

    /**
     * Decorates the already-normalized cue list on its way to the subtitle view.
     * Returns the input unchanged while no valid translation exists, and never throws
     * into the renderer.
     */
    public synchronized List<Cue> process(List<Cue> processedSourceCues) {
        try {
            if (processedSourceCues == null || processedSourceCues.isEmpty()) {
                return processedSourceCues;
            }

            if (!mEnabledSupplier.getAsBoolean()) {
                if (mWasEnabled) {
                    invalidate();
                    mWasEnabled = false;
                }

                return processedSourceCues;
            }

            mWasEnabled = true;

            List<Cue> decorated = null;

            for (int i = 0; i < processedSourceCues.size(); i++) {
                Cue cue = processedSourceCues.get(i);
                Cue result = decorate(cue);

                if (result != cue) {
                    if (decorated == null) {
                        decorated = new ArrayList<>(processedSourceCues);
                    }

                    decorated.set(i, result);
                }
            }

            return decorated != null ? decorated : processedSourceCues;
        } catch (Exception e) {
            // Feature exceptions must never break or alter the source-only caption path.
            return processedSourceCues;
        }
    }

    void onNewVideo(String videoId) {
        synchronized (this) {
            invalidate();
        }
    }

    void onSubtitleTrackChanged(String trackIdentity) {
        synchronized (this) {
            invalidate();
        }
    }

    void onSeek(long positionMs) {
        synchronized (this) {
            cancelInFlight();
            mEpoch++;
        }
    }

    void onPause() {
        synchronized (this) {
            mPaused = true;
        }
    }

    void onPlay() {
        synchronized (this) {
            mPaused = false;
        }
    }

    void onRelease() {
        synchronized (this) {
            invalidate();
            mPaused = false;
        }
    }

    private Cue decorate(Cue cue) {
        if (cue == null) {
            return null;
        }

        CharSequence text = cue.text;

        if (text == null || text.toString().trim().isEmpty()) {
            return cue;
        }

        String source = text.toString();
        String translation = findOrRequest(source);

        if (translation == null) {
            return cue;
        }

        return new Cue(source + "\n" + translation);
    }

    private String findOrRequest(String source) {
        String completed = mCompleted.get(source);

        if (completed != null) {
            return completed;
        }

        if (mPaused || mInFlight.containsKey(source)) {
            return null;
        }

        long requestId = ++mRequestIdSeed;
        long generation = mGeneration;
        long epoch = mEpoch;
        PendingRequest pending = new PendingRequest(requestId, generation, epoch, null);

        // Track before starting so a synchronous provider callback is still accepted.
        mInFlight.put(source, pending);

        TranslationRequest request = new TranslationRequest(generation, requestId, source, null, null);

        try {
            pending.call = mProvider.translate(request, new BridgeCallback(source, requestId, generation, epoch));
        } catch (Exception e) {
            mInFlight.remove(source);
            return null;
        }

        return null;
    }

    private void invalidate() {
        mGeneration++;
        cancelInFlight();
        mCompleted.clear();
    }

    private void cancelInFlight() {
        for (PendingRequest pending : mInFlight.values()) {
            if (pending.call != null) {
                try {
                    pending.call.cancel();
                } catch (Exception ignored) {
                    // Cancellation is best-effort; the identity guards above stay authoritative.
                }
            }
        }

        mInFlight.clear();
    }

    private static final class PendingRequest {
        private final long requestId;
        private final long generation;
        private final long epoch;
        private TranslationCall call;

        private PendingRequest(long requestId, long generation, long epoch, TranslationCall call) {
            this.requestId = requestId;
            this.generation = generation;
            this.epoch = epoch;
            this.call = call;
        }
    }

    private final class BridgeCallback implements TranslationCallback {
        private final String mSource;
        private final long mRequestId;
        private final long mRequestGeneration;
        private final long mRequestEpoch;

        private BridgeCallback(String source, long requestId, long requestGeneration, long requestEpoch) {
            mSource = source;
            mRequestId = requestId;
            mRequestGeneration = requestGeneration;
            mRequestEpoch = requestEpoch;
        }

        @Override
        public void onSuccess(TranslationResult result) {
            if (result == null) {
                return;
            }

            synchronized (AiSubtitleCueBridge.this) {
                PendingRequest pending = mInFlight.get(mSource);

                // The callback applies only when both generation and request id still
                // belong to the active request of the current session and epoch.
                if (pending == null
                        || pending.requestId != mRequestId
                        || pending.generation != mRequestGeneration
                        || pending.epoch != mRequestEpoch
                        || mGeneration != mRequestGeneration
                        || mEpoch != mRequestEpoch
                        || result.getGeneration() != mRequestGeneration
                        || result.getRequestId() != mRequestId) {
                    return;
                }

                mInFlight.remove(mSource);
                mCompleted.put(mSource, result.getTranslatedText());
            }
        }

        @Override
        public void onFailure(Throwable error) {
            synchronized (AiSubtitleCueBridge.this) {
                PendingRequest pending = mInFlight.get(mSource);

                if (pending != null && pending.requestId == mRequestId) {
                    mInFlight.remove(mSource);
                }
            }
        }
    }
}
