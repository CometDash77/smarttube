package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.google.android.exoplayer2.text.Cue;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSession;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionSnapshot;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SmartTubeSubtitleSourceAdapter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationStream;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests for M03-C2/C4: session generation and scheduling-epoch ownership inside the
 * bridge, plus the C4 result-identity guarantees (final-only caching, session/request/coverage
 * matching). Verifies that identity changes create new generations, repeated identical events
 * stay idempotent, seeks only advance the epoch, and stale or invalid callbacks cannot mutate
 * state.
 */
public class AiSubtitleCueBridgeSessionTest {
    /** Test-only Prompt Profile; independent of any built-in or reference content. */
    private static final PromptProfile PROMPT = new PromptProfile(
            "test.prompt", "Test prompt",
            "Translate {{source_text}} into {{target_language}}.", 1, false);
    /** A profile that differs from {@link #PROMPT} in the session identity it produces. */
    private static final TranslationProfile OTHER_PROFILE = new TranslationProfile(
            "other-profile", "pending-protocol", "pending-endpoint", "pending-model",
            "pending-prompt", 1, "zh");
    private AtomicBoolean mEnabled;
    private FakeTranslationProvider mFakeProvider;
    private AiSubtitleCueBridge mBridge;
    private AtomicInteger mRefreshCount;

    @Before
    public void setUp() {
        mEnabled = new AtomicBoolean(true);
        mFakeProvider = new FakeTranslationProvider(false);
        mBridge = new AiSubtitleCueBridge(mEnabled::get, mFakeProvider, PROMPT);
        mRefreshCount = new AtomicInteger();
        mBridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
    }

    @Test
    public void seekAdvancesEpochWithoutChangingSessionIdentity() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot before = mBridge.snapshotSession();

        assertNotNull(before);
        assertEquals(0, before.getEpoch());

        mBridge.onSeek(5000);

        TranslationSessionSnapshot after = mBridge.snapshotSession();

        assertEquals("a seek must not change session identity", before.getSessionId(), after.getSessionId());
        assertEquals("a seek must not change the generation", before.getGeneration(), after.getGeneration());
        assertEquals(1, after.getEpoch());
    }

    @Test
    public void repeatedSeeksAdvanceTheEpochEachTime() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));

        mBridge.onSeek(1000);
        mBridge.onSeek(2000);
        mBridge.onSeek(3000);

        assertEquals(3, mBridge.snapshotSession().getEpoch());
    }

    @Test
    public void trackChangeCreatesNewGenerationWithNewIdentity() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot first = mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:ja:asr-2");
        TranslationSessionSnapshot second = mBridge.snapshotSession();

        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertTrue(second.getGeneration() > first.getGeneration());
    }

    @Test
    public void repeatedIdenticalTrackEventsDoNotCreateNewGenerations() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        TranslationSessionSnapshot first = mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        TranslationSessionSnapshot again = mBridge.snapshotSession();

        assertEquals(first.getSessionId(), again.getSessionId());
        assertEquals("duplicate lifecycle events must be idempotent", first.getGeneration(), again.getGeneration());
    }

    @Test
    public void subtitlesOffAndOnRestartFromCleanState() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        mBridge.process(cues("Hello"));
        mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:none");
        TranslationSessionSnapshot off = mBridge.snapshotSession();

        mBridge.onSubtitleTrackChanged("subtitle:en:asr-1");
        TranslationSessionSnapshot backOn = mBridge.snapshotSession();

        assertTrue("re-enabling subtitles must start a fresh generation", backOn.getGeneration() > off.getGeneration());
    }

    @Test
    public void newVideoStartsFreshSessionIdentity() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot first = mBridge.snapshotSession();

        mBridge.onNewVideo("video-2", null, null);
        TranslationSessionSnapshot second = mBridge.snapshotSession();

        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertTrue(second.getGeneration() > first.getGeneration());
    }

    @Test
    public void profileChangeCreatesNewGenerationAndRejectsStaleCallbacks() {
        StubbornProvider provider = new StubbornProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onNewVideo("video-1", null, null);

        bridge.process(cues("Hello"));
        TranslationSessionSnapshot before = bridge.snapshotSession();

        TranslationProfile other = new TranslationProfile("other-profile", "pending-protocol",
                "pending-endpoint", "pending-model", "pending-prompt", 1, "zh");
        bridge.onProfileChanged(other);
        TranslationSessionSnapshot after = bridge.snapshotSession();

        assertNotEquals("a profile change must create a new session identity",
                before.getSessionId(), after.getSessionId());
        assertTrue("a profile change must advance the generation",
                after.getGeneration() > before.getGeneration());

        provider.deliverAll();
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a stale callback must not leak into the new profile's session",
                "Hello", output.get(0).text.toString());
        assertEquals("the new session must issue its own request", 2, provider.getCallCount());
    }

    @Test
    public void pauseAndPlayAreReflectedInTheSessionState() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));

        mBridge.onPause();
        assertEquals(TranslationSession.State.PAUSED, mBridge.snapshotSession().getState());

        mBridge.onPlay();
        assertEquals(TranslationSession.State.ACTIVE, mBridge.snapshotSession().getState());
    }

    @Test
    public void releaseClosesTheActiveSession() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));

        assertNotNull(mBridge.snapshotSession());

        mBridge.onRelease();

        assertNull("release must leave no active session", mBridge.snapshotSession());
    }

    @Test
    public void resultWithMismatchedRequestIdentityIsRejected() {
        TranslationProvider mismatchedProvider = new TranslationProvider() {
            @Override
            public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
                callback.onSuccess(TranslationResult.finalResult(
                        request.getSessionId(),
                        request.getRequestId() + 999,
                        request.getUnit(),
                        "[ZH] " + request.getSourceText()));
                return new NopCall();
            }
        };

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, mismatchedProvider, PROMPT);
        bridge.onNewVideo("video-1", null, null);

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a result with mismatched request identity must never be cached",
                "Hello", output.get(0).text.toString());
    }

    @Test
    public void partialResultsNeverEnterTheCache() {
        TranslationProvider partialOnlyProvider = new TranslationProvider() {
            @Override
            public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
                callback.onSuccess(TranslationResult.partialResult(
                        request.getSessionId(),
                        request.getRequestId(),
                        request.getUnit(),
                        "[ZH] partial"));
                return new NopCall();
            }
        };

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, partialOnlyProvider, PROMPT);
        bridge.onNewVideo("video-1", null, null);

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a partial result must never be cached as final text",
                "Hello", output.get(0).text.toString());
    }

    @Test
    public void resultWithMismatchedCoverageIsRejected() {
        TranslationProvider wrongCoverageProvider = new TranslationProvider() {
            @Override
            public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
                TranslationUnit wrongUnit = new TranslationUnit(
                        Collections.singletonList(new SubtitleSegmentId(
                                request.getSessionId().getSourceTrackId(), 5)),
                        request.getSourceText());

                callback.onSuccess(TranslationResult.finalResult(
                        request.getSessionId(),
                        request.getRequestId(),
                        wrongUnit,
                        "[ZH] " + request.getSourceText()));
                return new NopCall();
            }
        };

        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, wrongCoverageProvider, PROMPT);
        bridge.onNewVideo("video-1", null, null);

        bridge.process(cues("Hello"));
        List<Cue> output = bridge.process(cues("Hello"));

        assertEquals("a result that answers different coverage must never be cached",
                "Hello", output.get(0).text.toString());
    }

    @Test
    public void identicalSchedulingChangeIsIgnored() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));

        TranslationSessionSnapshot before = mBridge.snapshotSession();
        mBridge.onSchedulingChanged(90_000, 30_000);

        assertEquals("an unchanged limit must not touch the session",
                before.getSessionId(), mBridge.snapshotSession().getSessionId());
        assertEquals(before.getGeneration(), mBridge.snapshotSession().getGeneration());
    }

    @Test
    public void schedulingChangeKeepsTheSessionAndItsCachedTranslations() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));
        mFakeProvider.flushPending();

        TranslationSessionSnapshot before = mBridge.snapshotSession();
        mBridge.onSchedulingChanged(30_000, 5_000);

        assertEquals("a lookahead change must not restart the session",
                before.getSessionId(), mBridge.snapshotSession().getSessionId());
        assertEquals(before.getGeneration(), mBridge.snapshotSession().getGeneration());

        List<Cue> output = mBridge.process(cues("Hello"));
        assertEquals("a lookahead change must not discard cached translations",
                "Hello\n[ZH] Hello", output.get(0).text.toString());
    }

    @Test
    public void schedulingChangeWhilePausedStartsNoWork() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));
        mFakeProvider.flushPending();
        mBridge.onPause();

        mBridge.onSchedulingChanged(30_000, 5_000);

        assertEquals("a settings change must never resume playback work",
                TranslationSession.State.PAUSED, mBridge.snapshotSession().getState());
    }

    @Test
    public void segmentationChangeStartsANewGenerationAndKeepsThePausedState() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));
        TranslationSessionSnapshot before = mBridge.snapshotSession();

        mBridge.onSegmentationChanged(100, 320, 100);
        TranslationSessionSnapshot after = mBridge.snapshotSession();

        assertTrue("new segmentation must invalidate the old generation",
                after.getGeneration() > before.getGeneration());
        assertEquals(TranslationSession.State.ACTIVE, after.getState());

        mBridge.onPause();
        before = mBridge.snapshotSession();
        mBridge.onSegmentationChanged(40, 120, 60);
        after = mBridge.snapshotSession();

        assertTrue(after.getGeneration() > before.getGeneration());
        assertEquals("a segmentation change must not silently resume playback work",
                TranslationSession.State.PAUSED, after.getState());
    }

    @Test
    public void segmentationChangeIsIgnoredWhenItRepeatsTheCurrentValues() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));

        TranslationSessionSnapshot before = mBridge.snapshotSession();
        mBridge.onSegmentationChanged(60, 200, 80);

        assertEquals("identical segmentation must not drop the session",
                before.getSessionId(), mBridge.snapshotSession().getSessionId());
    }

    @Test
    public void anUnconfiguredProviderToleratesLifecycleAndSettingsChanges() {
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, null, PROMPT);
        bridge.onNewVideo("video-1", null, null);
        bridge.onSubtitleTrackChanged("subtitle:en:1");

        // Every one of these used to fail constructing a scheduler for a null provider.
        bridge.onSegmentationChanged(100, 320, 100);
        bridge.onSchedulingChanged(30_000, 5_000);
        bridge.onPause();
        bridge.onPlay();
        bridge.onSeek(1_000);
        bridge.onSeekDrag(2_000);

        List<Cue> input = cues("Hello");

        assertSame("an unconfigured provider must stay source-only",
                input, bridge.process(input));
    }

    @Test
    public void aNewSourceAdapterReceivesTheStoredSegmentationLimits() {
        mBridge.onSegmentationChanged(100, 320, 100);

        SmartTubeSubtitleSourceAdapter adapter =
                new SmartTubeSubtitleSourceAdapter((videoId, listener) -> { }, url -> null);
        mBridge.setSourceAdapter(adapter);

        assertEquals(100, adapter.segmentationLimitsForTesting()[0]);
        assertEquals(320, adapter.segmentationLimitsForTesting()[1]);
        assertEquals(100, adapter.segmentationLimitsForTesting()[2]);
    }

    @Test
    public void aStaleTimelineLoadCannotOverwriteANewerOne() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        assertEquals("the track change must start a load", 1, source.loadCount());

        // A segmentation change starts a second load while the first is still in flight.
        bridge.onSegmentationChanged(100, 320, 100);
        assertEquals(2, source.loadCount());

        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nSTALE");
        source.deliver(1, "00:00:00.000 --> 00:00:02.000\nFRESH");

        bridge.onPositionUpdate(1_000);
        bridge.process(cues("displayed"));
        provider.flush();

        assertTrue("the newest load must win", provider.unitTexts().contains("FRESH"));
        assertFalse("a superseded load must never be applied",
                provider.unitTexts().contains("STALE"));
    }

    @Test
    public void bilingualOrderChangesPresentationWithoutNewRequests() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));
        mFakeProvider.flushPending();

        int requestsBefore = mFakeProvider.getTranslateCallCount();
        int refreshesBefore = mRefreshCount.get();

        mBridge.setTranslationFirst(true);

        assertEquals("reordering is presentation only", requestsBefore,
                mFakeProvider.getTranslateCallCount());
        assertEquals("reordering must repaint the current cues", refreshesBefore + 1,
                mRefreshCount.get());
        assertEquals("Hello\n[ZH] Hello".replace("Hello\n[ZH] ", "[ZH] Hello\n"),
                mBridge.process(cues("Hello")).get(0).text.toString());
    }

    @Test
    public void aStreamedDraftDecoratesTheCurrentCueBeforeTheFinalArrives() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);

        bridge.process(cues("Hello"));
        provider.emitPartial("\u4f60\u597d");

        assertEquals("a draft must reach the cue that is on screen",
                "Hello\n\u4f60\u597d", bridge.process(cues("Hello")).get(0).text.toString());
        assertEquals("a draft is not a finished translation",
                AiSubtitleCueBridge.RuntimeStatus.TRANSLATING, bridge.getRuntimeStatus());

        provider.complete("\u4f60\u597d\u4e16\u754c");

        assertEquals("the final must replace the draft",
                "Hello\n\u4f60\u597d\u4e16\u754c",
                bridge.process(cues("Hello")).get(0).text.toString());
        assertEquals(AiSubtitleCueBridge.RuntimeStatus.TRANSLATED, bridge.getRuntimeStatus());
    }

    @Test
    public void aDraftOnlyDecoratesTheUnitItWasStreamedFor() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);

        bridge.process(cues("Hello"));
        StreamingProvider.StreamHandle first = provider.capture();
        first.emitPartial("\u8349\u7a3f");

        // A different displayed cue maps to a different unit, so the draft is not its translation.
        assertEquals("Other", bridge.process(cues("Other")).get(0).text.toString());
        assertTrue("moving to another unit cancels the stream in flight", first.isCancelled());
        assertEquals("and clears the draft it had produced", "Hello",
                bridge.process(cues("Hello")).get(0).text.toString());
    }

    @Test
    public void draftRepaintsAreCoalescedWhileFinalsAlwaysRepaint() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        bridge.setDraftRefreshIntervalForTesting(Long.MAX_VALUE);

        bridge.process(cues("Hello"));

        int before = mRefreshCount.get();
        provider.emitPartial("a");
        provider.emitPartial("ab");
        provider.emitPartial("abc");

        assertEquals("drafts must be coalesced into one repaint", before + 1, mRefreshCount.get());

        provider.complete("abc");

        assertEquals("a final must always repaint", before + 2, mRefreshCount.get());
    }

    @Test
    public void everyDraftRepaintsWhenTheIntervalIsZero() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        bridge.setDraftRefreshIntervalForTesting(0);

        bridge.process(cues("Hello"));

        int before = mRefreshCount.get();
        provider.emitPartial("a");
        provider.emitPartial("ab");

        assertEquals(before + 2, mRefreshCount.get());
    }

    @Test
    public void disablingStreamingClearsTheDraftAndCancelsTheStream() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);

        bridge.process(cues("Hello"));
        StreamingProvider.StreamHandle first = provider.capture();
        first.emitPartial("\u8349\u7a3f");

        bridge.onStreamingEnabledChanged(false);

        assertTrue("turning streaming off must cancel the stream in flight", first.isCancelled());
        assertEquals("the draft must be gone", "Hello",
                bridge.process(cues("Hello")).get(0).text.toString());
    }

    /** The audit's counterexample for C10: clearing the draft must repaint, not wait for a cue. */
    @Test
    public void clearingTheVisibleDraftRepaintsImmediately() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.process(cues("Hello"));
        provider.emitPartial("draft");

        int before = mRefreshCount.get();
        bridge.onStreamingEnabledChanged(false);

        assertEquals("clearing the visible draft must repaint", before + 1, mRefreshCount.get());
        assertEquals("and the cue must be back to the source line", "Hello",
                bridge.process(cues("Hello")).get(0).text.toString());
    }

    @Test
    public void theNewestDraftIsRepaintedOnTheNextTick() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        // Long enough that a second delta inside the same instant cannot repaint on its own.
        bridge.setDraftRefreshIntervalForTesting(3_600_000);

        bridge.process(cues("Hello"));

        int before = mRefreshCount.get();
        provider.emitPartial("a");
        provider.emitPartial("ab");

        assertEquals("the first draft repaints and the second is coalesced",
                before + 1, mRefreshCount.get());

        // The tick that follows is short enough to send the one repaint that was dropped. The
        // interval is the controllable clock here; no test sleeps.
        bridge.setDraftRefreshIntervalForTesting(0);
        bridge.onPositionUpdate(1_000);

        assertEquals("the newest draft must reach the screen on the next tick",
                before + 2, mRefreshCount.get());
        assertEquals("Hello\nab", bridge.process(cues("Hello")).get(0).text.toString());

        bridge.onPositionUpdate(2_000);

        assertEquals("one dropped repaint is re-sent once, not once per tick",
                before + 2, mRefreshCount.get());
    }

    @Test
    public void aFinalRepaintsImmediatelyAndSupersedesThePendingDraft() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        bridge.setDraftRefreshIntervalForTesting(3_600_000);

        bridge.process(cues("Hello"));

        int before = mRefreshCount.get();
        provider.emitPartial("a");
        provider.emitPartial("ab");
        provider.complete("ab");

        assertEquals("a final always repaints", before + 2, mRefreshCount.get());
        assertEquals(AiSubtitleCueBridge.RuntimeStatus.TRANSLATED, bridge.getRuntimeStatus());

        bridge.setDraftRefreshIntervalForTesting(0);
        bridge.onPositionUpdate(1_000);

        assertEquals("a superseded draft must not be re-sent after the final",
                before + 2, mRefreshCount.get());
        assertEquals("the final must be what the cue shows", "Hello\nab",
                bridge.process(cues("Hello")).get(0).text.toString());
        assertEquals("one request per unit", 1, provider.getCallCount());
    }

    @Test
    public void turningStreamingOnRepaintsImmediatelyToo() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());

        bridge.process(cues("Hello"));

        int before = mRefreshCount.get();
        bridge.onStreamingEnabledChanged(true);

        assertEquals("turning streaming on cancels the request that was filling the cue",
                before + 1, mRefreshCount.get());
    }

    @Test
    public void switchingTheFeatureOffRepaintsBackToTheSourceLine() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());

        bridge.process(cues("Hello"));
        provider.emitPartial("draft");
        assertEquals("Hello\ndraft", bridge.process(cues("Hello")).get(0).text.toString());

        int before = mRefreshCount.get();
        mEnabled.set(false);
        bridge.onEnabledChanged(false);

        assertEquals("dropping the session removes what the cue was showing", before + 1,
                mRefreshCount.get());
        assertEquals("Hello", bridge.process(cues("Hello")).get(0).text.toString());
    }

    @Test
    public void aRetryableFailureDoesNotClaimTheCueIsTranslated() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());

        bridge.process(cues("Hello"));
        provider.emitPartial("draft");
        assertEquals("Hello\ndraft", bridge.process(cues("Hello")).get(0).text.toString());
        assertEquals(AiSubtitleCueBridge.RuntimeStatus.TRANSLATING, bridge.getRuntimeStatus());

        // A transient failure clears the draft and leaves the unit queued for a retry. The
        // scheduler reports that as "the visible state moved", which is not a translation.
        provider.fail(TranslationFailureCategory.NETWORK);

        assertEquals("a retry that is still coming is not a completed translation",
                AiSubtitleCueBridge.RuntimeStatus.TRANSLATING, bridge.getRuntimeStatus());
        assertEquals("and the draft must be gone with no residue", "Hello",
                bridge.process(cues("Hello")).get(0).text.toString());
    }

    @Test
    public void thePendingRepaintWaitsUntilTheIntervalHasElapsed() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        bridge.setDraftRefreshIntervalForTesting(Long.MAX_VALUE);

        bridge.process(cues("Hello"));

        int before = mRefreshCount.get();
        provider.emitPartial("a");
        provider.emitPartial("ab");

        // The test supplies the clock value rather than sleeping past a real interval. The
        // repaint is owed, but a tick inside the interval must neither send it nor forget it.
        long nowMs = System.nanoTime() / 1_000_000L;

        assertFalse("the repaint is not due inside the interval",
                bridge.flushPendingDraftRefresh(nowMs));
        assertEquals(before + 1, mRefreshCount.get());

        bridge.setDraftRefreshIntervalForTesting(0);

        assertTrue("and it is due once the interval has elapsed",
                bridge.flushPendingDraftRefresh(nowMs));
    }

    @Test
    public void seekingAwayDoesNotResurrectThePreviousDraft() {
        StreamingProvider provider = new StreamingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        bridge.onStreamingEnabledChanged(true);
        bridge.onNewVideo("video-1", null, null);
        bridge.setRefreshListener(() -> mRefreshCount.incrementAndGet());
        bridge.setDraftRefreshIntervalForTesting(3_600_000);

        bridge.process(cues("Hello"));
        provider.emitPartial("a");
        provider.emitPartial("ab");

        bridge.onSeek(30_000);

        int before = mRefreshCount.get();
        bridge.setDraftRefreshIntervalForTesting(0);
        bridge.onPositionUpdate(30_000);

        assertEquals("a seek must drop the pending repaint", before, mRefreshCount.get());
        assertEquals("Other", bridge.process(cues("Other")).get(0).text.toString());
    }

    /**
     * A track switch invalidates the timeline that belonged to the previous track. Until the new
     * track's source arrives the bridge must stay source-only rather than answer from units that
     * describe the old track.
     */
    @Test
    public void aPendingSourceReplacementNeverDispatchesTheOldTrack() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nOLD-TRACK");
        bridge.onPositionUpdate(1_000);
        provider.flush();

        int before = provider.unitTexts().size();

        bridge.onSubtitleTrackChanged("subtitle:ja:2");
        bridge.onPositionUpdate(1_000);

        assertEquals("pending new source must not dispatch old track", before,
                provider.unitTexts().size());
    }

    /**
     * Turning the feature off must stop translation work, not merely hide it: playback events
     * that arrive while the feature is off may not build a scheduler that issues requests.
     */
    @Test
    public void playbackResumeWhileDisabledStartsNoWork() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nSOURCE");
        bridge.onPositionUpdate(1_000);

        int before = provider.unitTexts().size();

        mEnabled.set(false);
        bridge.onEnabledChanged(false);
        bridge.onPlay();

        assertEquals("AI off must not translate on play", before, provider.unitTexts().size());
    }

    /**
     * Playback pause is a fact about the player, not about the session that happens to be alive.
     * Rebuilding the session for a new configuration must not turn paused playback active.
     */
    @Test
    public void aProfileChangeKeepsPausedPlaybackPaused() {
        mBridge.onNewVideo("video-1", null, null);
        mBridge.onPause();

        mBridge.onProfileChanged(new TranslationProfile("other", "protocol", "endpoint",
                "model", "prompt", 1, "zh"));

        assertEquals("a configuration change must not resume playback work",
                TranslationSession.State.PAUSED, mBridge.snapshotSession().getState());
    }

    /**
     * A source load that answers after the player moved to another video belongs to a timeline
     * the bridge no longer wants, even when the track identity happens to look the same.
     */
    @Test
    public void aLateSourceLoadForThePreviousVideoIsDiscarded() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");

        bridge.onNewVideo("video-2", null, null);
        bridge.onSubtitleTrackChanged("subtitle:en:2");

        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nPREVIOUS-VIDEO");
        bridge.onPositionUpdate(1_000);
        bridge.process(cues("displayed"));

        assertFalse("the previous video's timeline must never answer for the new one",
                provider.unitTexts().contains("PREVIOUS-VIDEO"));
    }

    /**
     * Selecting A, then B, then A again leaves the first load of A and the load of B both stale.
     * Only the newest load may be applied, whatever order the responses arrive in.
     */
    @Test
    public void aLateSourceLoadForAnAbandonedTrackIsDiscarded() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        bridge.onSubtitleTrackChanged("subtitle:ja:2");
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        bridge.onPositionUpdate(1_000);

        source.deliver(1, "00:00:00.000 --> 00:00:02.000\nABANDONED-TRACK");
        source.deliver(2, "00:00:00.000 --> 00:00:02.000\nSELECTED-TRACK");
        bridge.process(cues("displayed"));

        assertTrue("the newest load must win", provider.unitTexts().contains("SELECTED-TRACK"));
        assertFalse("an abandoned track's load must never be applied",
                provider.unitTexts().contains("ABANDONED-TRACK"));
    }

    /** With subtitles off there is no selected track, so a position tick has nothing to dispatch. */
    @Test
    public void aTickAfterSubtitlesAreDisabledStartsNoWork() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nSOURCE");
        bridge.onPositionUpdate(1_000);
        bridge.process(cues("displayed"));

        int before = provider.unitTexts().size();

        bridge.onSubtitleTrackChanged(AiSubtitleController.IDENTITY_NONE);
        bridge.onPositionUpdate(2_000);

        assertEquals("a tick after subtitles were switched off must not dispatch", before,
                provider.unitTexts().size());
    }

    /** Playback events while the feature is off may not build a scheduler that issues requests. */
    @Test
    public void latePlaybackEventsWhileDisabledStartNoWork() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nSOURCE");
        bridge.onPositionUpdate(1_000);
        bridge.process(cues("displayed"));

        int before = provider.unitTexts().size();

        mEnabled.set(false);
        bridge.onEnabledChanged(false);
        bridge.onPause();
        bridge.onPlay();
        bridge.onPositionUpdate(2_000);

        assertEquals("a disabled feature must stay silent through playback events", before,
                provider.unitTexts().size());
    }

    /** A position tick after release has no session and no source left to translate. */
    @Test
    public void aTickAfterReleaseStartsNoWork() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nSOURCE");
        bridge.onPositionUpdate(1_000);
        bridge.process(cues("displayed"));

        int before = provider.unitTexts().size();

        bridge.onRelease();
        bridge.onPositionUpdate(2_000);

        assertEquals("a released player must not start translation work", before,
                provider.unitTexts().size());
    }

    /** Changing the provider while playback is paused must not resume work for the new one. */
    @Test
    public void aProviderChangeWhilePausedKeepsPlaybackPausedAndStartsNoWork() {
        RecordingProvider provider = new RecordingProvider();

        mBridge.onNewVideo("video-1", null, null);
        mBridge.process(cues("Hello"));
        mBridge.onPause();

        mBridge.onProviderChanged(provider, OTHER_PROFILE, PROMPT);

        assertEquals("a provider change must not resume playback work",
                TranslationSession.State.PAUSED, mBridge.snapshotSession().getState());
        assertEquals("and must dispatch nothing while playback is paused", 0,
                provider.unitTexts().size());
    }

    /**
     * The bounded context changes the instruction that is actually sent, so toggling it has to
     * replace the live session: the frozen prompt of a request already in flight cannot be
     * un-sent, and a cached answer belongs to the instruction it answered.
     */
    @Test
    public void contextToggleRecreatesTheLiveSessionAndChangesWhatIsSent() {
        PromptProfile contextPrompt = new PromptProfile("test.context", "Context prompt",
                "Translate {{source_text}} into {{target_language}}. Context:{{context}}", 1, false);
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, contextPrompt);

        bridge.onNewVideo("video-1", "TITLE", null);
        bridge.process(cues("Hello"));

        assertEquals(1, provider.renderedPrompts().size());
        assertFalse("context off must not send the video title",
                provider.renderedPrompts().get(0).contains("TITLE"));

        long before = bridge.snapshotSession().getGeneration();
        bridge.onContextEnabledChanged(true);

        assertTrue("context toggle must replace the active scheduler",
                bridge.snapshotSession().getGeneration() > before);

        bridge.process(cues("Hello"));

        assertEquals(2, provider.renderedPrompts().size());
        assertTrue("context on must send the video title",
                provider.renderedPrompts().get(1).contains("TITLE"));

        bridge.onContextEnabledChanged(false);
        bridge.process(cues("Hello"));

        assertEquals(3, provider.renderedPrompts().size());
        assertFalse("context off must drop the title again",
                provider.renderedPrompts().get(2).contains("TITLE"));
    }

    /** The adapter already serving the current track must receive the new segmentation limits. */
    @Test
    public void segmentationChangeUpdatesTheExistingAdapter() {
        SmartTubeSubtitleSourceAdapter adapter =
                new SmartTubeSubtitleSourceAdapter((videoId, listener) -> { }, url -> null);
        mBridge.setSourceAdapter(adapter);

        mBridge.onSegmentationChanged(100, 320, 100);

        assertArrayEquals(new int[] {100, 320, 100}, adapter.segmentationLimitsForTesting());
    }

    /** Re-segmenting cancels the old units; it may not dispatch work while playback is paused. */
    @Test
    public void reSegmentationWhilePausedDispatchesNothing() {
        RecordingProvider provider = new RecordingProvider();
        AiSubtitleCueBridge bridge = new AiSubtitleCueBridge(mEnabled::get, provider, PROMPT);
        ManualSource source = new ManualSource();

        bridge.onNewVideo("video-1", null, null);
        bridge.setSourceAdapter(source.adapter());
        bridge.onSubtitleTrackChanged("subtitle:en:1");
        source.deliver(0, "00:00:00.000 --> 00:00:02.000\nSOURCE");
        bridge.onPositionUpdate(1_000);
        bridge.process(cues("displayed"));

        int before = provider.unitTexts().size();

        bridge.onPause();
        bridge.onSegmentationChanged(100, 320, 100);
        bridge.onPositionUpdate(1_000);

        assertEquals("re-segmentation must not resume work while playback is paused", before,
                provider.unitTexts().size());
    }

    private static List<Cue> cues(String... texts) {
        List<Cue> list = new ArrayList<>();

        for (String text : texts) {
            list.add(new Cue(text));
        }

        return list;
    }

    /** Provider double that can emit streamed drafts and then complete. */
    private static final class StreamingProvider implements TranslationProvider {
        private int mCallCount;
        private TranslationCallback mCallback;
        private TranslationRequest mRequest;
        private NopCall mCall;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            mRequest = request;
            mCallback = callback;
            mCall = new NopCall();
            return mCall;
        }

        StreamHandle capture() {
            return new StreamHandle(mCallback, mRequest, mCall);
        }

        void emitPartial(String text) {
            capture().emitPartial(text);
        }

        void complete(String text) {
            mCallback.onSuccess(TranslationResult.finalResult(mRequest.getSessionId(),
                    mRequest.getRequestId(), mRequest.getUnit(), text));
        }

        void fail(TranslationFailureCategory category) {
            mCallback.onFailure(new TranslationFailure(category, "synthetic " + category));
        }

        int getCallCount() {
            return mCallCount;
        }

        static final class StreamHandle {
            private final TranslationStream mCallback;
            private final TranslationRequest mRequest;
            private final NopCall mCall;

            StreamHandle(TranslationCallback callback, TranslationRequest request, NopCall call) {
                mCallback = (TranslationStream) callback;
                mRequest = request;
                mCall = call;
            }

            void emitPartial(String text) {
                mCallback.onPartial(TranslationResult.partialResult(mRequest.getSessionId(),
                        mRequest.getRequestId(), mRequest.getUnit(), text));
            }

            boolean isCancelled() {
                return mCall.isCancelled();
            }
        }
    }

    /** Records the rendered instruction each request carried, not just its source text. */
    private static final class RecordingProvider implements TranslationProvider {
        private final List<String> mUnitTexts = new ArrayList<>();
        private final List<String> mRenderedPrompts = new ArrayList<>();
        private final List<Runnable> mDeliveries = new ArrayList<>();

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mUnitTexts.add(request.getSourceText());
            mRenderedPrompts.add(request.getRenderedPrompt());
            mDeliveries.add(() -> callback.onSuccess(TranslationResult.finalResult(
                    request.getSessionId(), request.getRequestId(), request.getUnit(),
                    "[ZH] " + request.getSourceText())));
            return new NopCall();
        }

        List<String> unitTexts() {
            return new ArrayList<>(mUnitTexts);
        }

        List<String> renderedPrompts() {
            return new ArrayList<>(mRenderedPrompts);
        }

        void flush() {
            List<Runnable> deliveries = new ArrayList<>(mDeliveries);
            mDeliveries.clear();
            for (Runnable delivery : deliveries) {
                delivery.run();
            }
        }
    }

    /** Supplies a controllable source load so a test can deliver responses out of order. */
    private static final class ManualSource {
        private final List<SmartTubeSubtitleSourceAdapter.SubtitleListListener> mListeners =
                new ArrayList<>();
        private String mVtt = "";
        private final SmartTubeSubtitleSourceAdapter mAdapter =
                new SmartTubeSubtitleSourceAdapter(
                        (videoId, listener) -> mListeners.add(listener), url -> mVtt);

        SmartTubeSubtitleSourceAdapter adapter() {
            return mAdapter;
        }

        int loadCount() {
            return mListeners.size();
        }

        void deliver(int loadIndex, String vtt) {
            mVtt = vtt;
            mListeners.get(loadIndex).onSubtitles(Collections.singletonList(new ManualSubtitle()));
        }
    }

    private static final class ManualSubtitle implements MediaSubtitle {
        @Override public String getBaseUrl() { return "http://example.com/timedtext?v=1"; }
        @Override public boolean isTranslatable() { return true; }
        @Override public String getLanguageCode() { return "en"; }
        @Override public String getVssId() { return ".en"; }
        @Override public String getName() { return "English"; }
        @Override public String getMimeType() { return "text/vtt"; }
        @Override public String getCodecs() { return ""; }
        @Override public String getType() { return "captions"; }
    }

    /**
     * Ignores cancellation and delivers only when the test says so; used to prove that the
     * bridge itself rejects stale callbacks instead of relying on provider cooperation.
     */
    private static final class StubbornProvider implements TranslationProvider {
        private final List<Runnable> mDeliveries = new ArrayList<>();
        private int mCallCount;

        @Override
        public TranslationCall translate(TranslationRequest request, TranslationCallback callback) {
            mCallCount++;
            mDeliveries.add(() -> callback.onSuccess(TranslationResult.finalResult(
                    request.getSessionId(),
                    request.getRequestId(),
                    request.getUnit(),
                    "[ZH] " + request.getSourceText())));
            return new NopCall();
        }

        int getCallCount() {
            return mCallCount;
        }

        void deliverAll() {
            List<Runnable> deliveries = new ArrayList<>(mDeliveries);
            mDeliveries.clear();

            for (Runnable delivery : deliveries) {
                delivery.run();
            }
        }
    }

    private static final class NopCall implements TranslationCall {
        private boolean mCancelled;

        @Override
        public void cancel() {
            mCancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return mCancelled;
        }
    }
}
