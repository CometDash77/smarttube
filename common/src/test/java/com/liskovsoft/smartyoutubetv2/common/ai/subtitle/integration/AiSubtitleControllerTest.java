package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.FakeTranslationProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM lifecycle tests for the AI subtitle controller.
 *
 * <p>Robolectric 4.6.1 cannot instrument bytecode on JDK 17 (its ASM rejects class file
 * major version 61); controller coverage therefore runs without an Android runtime through
 * the reduced-visibility bridge hook. The deviation is recorded in the M02 worker report.</p>
 */
public class AiSubtitleControllerTest {
    private RecordingBridge mBridge;
    private AiSubtitleController mController;

    @Before
    public void setUp() {
        mBridge = new RecordingBridge();
        mController = new AiSubtitleController() {
            @Override
            AiSubtitleCueBridge getBridge() {
                return mBridge;
            }
        };
    }

    @Test
    public void newVideoForwardsVideoIdAndDropsTrackIdentity() {
        mController.onTrackSelected(subtitleItem("en", "42"));
        mController.onNewVideo(Video.from("video-2"));

        assertEquals(Arrays.asList("track:subtitle:en:42", "video:video-2"), mBridge.mCalls);

        // After a video change the same track identity must be forwarded again.
        mController.onTrackSelected(subtitleItem("en", "42"));

        assertEquals(Arrays.asList("track:subtitle:en:42", "video:video-2", "track:subtitle:en:42"), mBridge.mCalls);
    }

    @Test
    public void nullVideoIsTolerated() {
        mController.onNewVideo(null);

        assertEquals(Arrays.asList("video:null"), mBridge.mCalls);
    }

    @Test
    public void duplicateSelectionAndChangeEventsAreDeduplicated() {
        mController.onTrackSelected(subtitleItem("en", "42"));
        mController.onTrackChanged(subtitleItem("en", "42"));

        assertEquals(Arrays.asList("track:subtitle:en:42"), mBridge.mCalls);
    }

    @Test
    public void trackSwitchForwardsTheNewIdentity() {
        mController.onTrackSelected(subtitleItem("en", "42"));
        mController.onTrackSelected(subtitleItem("nl", "43"));

        assertEquals(Arrays.asList("track:subtitle:en:42", "track:subtitle:nl:43"), mBridge.mCalls);
    }

    @Test
    public void nonSubtitleTrackEventsAreIgnored() {
        mController.onTrackSelected(videoItem());
        mController.onTrackChanged(audioItem());

        assertTrue(mBridge.mCalls.isEmpty());
    }

    @Test
    public void nullTrackEventsAreIgnored() {
        mController.onTrackSelected(null);
        mController.onTrackChanged(null);

        assertTrue(mBridge.mCalls.isEmpty());
    }

    @Test
    public void subtitlesOffIsForwardedAsNoneAndReenablingRetriggers() {
        mController.onTrackSelected(subtitleItem("en", "42"));
        mController.onTrackSelected(subtitleOffItem());

        assertEquals(Arrays.asList("track:subtitle:en:42", "track:" + AiSubtitleController.IDENTITY_NONE), mBridge.mCalls);

        mController.onTrackSelected(subtitleItem("en", "42"));

        assertEquals(Arrays.asList(
                "track:subtitle:en:42",
                "track:" + AiSubtitleController.IDENTITY_NONE,
                "track:subtitle:en:42"), mBridge.mCalls);
    }

    @Test
    public void seekEventsAreForwarded() {
        mController.onSeekPositionChanged(1200);
        mController.onSeekEnd();

        assertEquals(Arrays.asList("drag:1200", "seek:1200"), mBridge.mCalls);
    }

    @Test
    public void pauseAndResumeAreForwarded() {
        mController.onPause();
        mController.onPlay();

        assertEquals(Arrays.asList("pause", "play"), mBridge.mCalls);
    }

    @Test
    public void releaseAndFinishAreForwardedAndRepeatable() {
        mController.onEngineReleased();
        mController.onFinish();
        mController.onEngineReleased();

        assertEquals(Arrays.asList("release", "release", "release"), mBridge.mCalls);
    }

    /**
     * A drag fires continuously, so only the end of the drag may dispatch a seek, and it has to
     * carry the final position rather than any of the intermediate ones.
     */
    @Test
    public void repeatedDragsDispatchOnlyOneSeekWithTheFinalPosition() {
        for (int i = 0; i < 50; i++) {
            mController.onSeekPositionChanged(i * 100);
        }
        mController.onSeekEnd();

        List<String> seeks = new ArrayList<>();
        for (String call : mBridge.mCalls) {
            if (call.startsWith("seek:")) seeks.add(call);
        }

        assertEquals(Collections.singletonList("seek:4900"), seeks);
    }

    private static FormatItem subtitleItem(String language, String formatId) {
        return new FakeFormatItem(FormatItem.TYPE_SUBTITLE, formatId, language);
    }

    private static FormatItem subtitleOffItem() {
        return new FakeFormatItem(FormatItem.TYPE_SUBTITLE, null, null);
    }

    private static FormatItem videoItem() {
        return new FakeFormatItem(FormatItem.TYPE_VIDEO, "v-1", null);
    }

    private static FormatItem audioItem() {
        return new FakeFormatItem(FormatItem.TYPE_AUDIO, "a-1", null);
    }

    private static final class FakeFormatItem implements FormatItem {
        private final int mType;
        private final String mFormatId;
        private final String mLanguage;

        private FakeFormatItem(int type, String formatId, String language) {
            mType = type;
            mFormatId = formatId;
            mLanguage = language;
        }

        @Override
        public int getId() {
            return 0;
        }

        @Override
        public String getFormatId() {
            return mFormatId;
        }

        @Override
        public CharSequence getTitle() {
            return "";
        }

        @Override
        public boolean isDefault() {
            return false;
        }

        @Override
        public boolean isSelected() {
            return false;
        }

        @Override
        public boolean isPreset() {
            return false;
        }

        @Override
        public float getFrameRate() {
            return 0;
        }

        @Override
        public String getLanguage() {
            return mLanguage;
        }

        @Override
        public int getWidth() {
            return 0;
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getType() {
            return mType;
        }

        @Override
        public MediaTrack getTrack() {
            return null;
        }
    }

    private static final class RecordingBridge extends AiSubtitleCueBridge {
        private final List<String> mCalls = new ArrayList<>();

        private RecordingBridge() {
            super(() -> true, new FakeTranslationProvider(),
                    new PromptProfile("test.prompt", "Test prompt",
                            "Translate {{source_text}} into {{target_language}}.", 1, false));
        }

        @Override
        void onNewVideo(String videoId, String title, String description) {
            mCalls.add("video:" + videoId);
        }

        @Override
        void onSubtitleTrackChanged(String trackIdentity) {
            mCalls.add("track:" + trackIdentity);
        }

        @Override
        void onSeekDrag(long positionMs) {
            mCalls.add("drag:" + positionMs);
        }

        @Override
        void onSeek(long positionMs) {
            mCalls.add("seek:" + positionMs);
        }

        @Override
        void onPause() {
            mCalls.add("pause");
        }

        @Override
        void onPlay() {
            mCalls.add("play");
        }

        @Override
        void onRelease() {
            mCalls.add("release");
        }
    }
}
