package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic offline translation fake for the M02 vertical slice.
 *
 * <p>Returns {@code "[ZH] "} plus the exact source text and never touches the network.
 * Null or blank source text returns a failure instead of fabricating content. Cancelled
 * calls never deliver. When constructed with {@code immediate == false}, deliveries are
 * queued until {@link #flushPending()} is called so callers and tests can observe and
 * control in-flight state deterministically.</p>
 */
public class FakeTranslationProvider implements TranslationProvider {
    public static final String TRANSLATION_PREFIX = "[ZH] ";

    private final boolean mImmediate;
    private final List<Runnable> mPendingDeliveries = new ArrayList<>();
    private int mTranslateCallCount;

    /** Completes every valid request synchronously. */
    public FakeTranslationProvider() {
        this(true);
    }

    public FakeTranslationProvider(boolean immediate) {
        mImmediate = immediate;
    }

    @Override
    public synchronized TranslationCall translate(@Nullable TranslationRequest request, @NonNull TranslationCallback callback) {
        mTranslateCallCount++;

        FakeTranslationCall call = new FakeTranslationCall();

        if (request == null || request.getSourceText() == null || request.getSourceText().trim().isEmpty()) {
            deliver(call, () -> callback.onFailure(new IllegalArgumentException("blank source text")));
            return call;
        }

        deliver(call, () -> callback.onSuccess(new TranslationResult(
                request.getGeneration(),
                request.getRequestId(),
                TRANSLATION_PREFIX + request.getSourceText())));

        return call;
    }

    /** Number of translate() invocations, including blank and duplicate requests. */
    public synchronized int getTranslateCallCount() {
        return mTranslateCallCount;
    }

    /**
     * Delivers all queued callbacks in order. Cancelled calls are skipped.
     * No-op for immediate providers because nothing is ever queued.
     */
    public synchronized void flushPending() {
        List<Runnable> deliveries = new ArrayList<>(mPendingDeliveries);
        mPendingDeliveries.clear();

        for (Runnable delivery : deliveries) {
            delivery.run();
        }
    }

    private void deliver(FakeTranslationCall call, Runnable delivery) {
        if (mImmediate) {
            if (!call.isCancelled()) {
                delivery.run();
            }
        } else {
            mPendingDeliveries.add(() -> {
                if (!call.isCancelled()) {
                    delivery.run();
                }
            });
        }
    }

    private static final class FakeTranslationCall implements TranslationCall {
        private boolean mCancelled;

        @Override
        public synchronized void cancel() {
            mCancelled = true;
        }

        @Override
        public synchronized boolean isCancelled() {
            return mCancelled;
        }
    }
}
