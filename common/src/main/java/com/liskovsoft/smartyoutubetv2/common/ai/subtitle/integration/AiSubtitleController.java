package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

/**
 * Owns the AI subtitle lifecycle inside SmartTube's ordered controller list.
 *
 * <p>Registered in {@code PlaybackPresenter} immediately after {@code VideoLoaderController}.
 * Every invalidating player event (video change, subtitle track change, subtitles off,
 * engine release, finish) drops prior translation state and invalidates outstanding
 * requests through the bridge; non-subtitle track events are ignored.</p>
 */
public class AiSubtitleController extends BasePlayerController {
    static final String IDENTITY_NONE = "subtitle:none";

    private static final long SEEK_POSITION_UNKNOWN = -1;

    private String mCurrentTrackIdentity;

    @Override
    public void onNewVideo(Video item) {
        mCurrentTrackIdentity = null;
        getBridge().onNewVideo(item != null ? item.videoId : null);
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        handleTrackEvent(track);
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        handleTrackEvent(track);
    }

    @Override
    public void onSeekEnd() {
        // Seek completion invalidates obsolete in-flight work; the bridge only advances its
        // scheduling epoch in M02, so the exact position is not required on this path.
        getBridge().onSeek(SEEK_POSITION_UNKNOWN);
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        // Drag seeks deliver intermediate positions; forwarding them cancels in-flight work early.
        getBridge().onSeek(positionMs);
    }

    @Override
    public void onPause() {
        getBridge().onPause();
    }

    @Override
    public void onPlay() {
        getBridge().onPlay();
    }

    @Override
    public void onEngineReleased() {
        mCurrentTrackIdentity = null;
        getBridge().onRelease();
    }

    @Override
    public void onFinish() {
        mCurrentTrackIdentity = null;
        getBridge().onRelease();
    }

    /**
     * The bridge for the current context. Reduced visibility lets tests drive the controller
     * without an Android runtime; the renderer still knows only {@code process}.
     */
    AiSubtitleCueBridge getBridge() {
        return AiSubtitleCueBridge.instance(getContext());
    }

    private void handleTrackEvent(FormatItem item) {
        String identity = toTrackIdentity(item);

        if (identity == null || identity.equals(mCurrentTrackIdentity)) {
            return;
        }

        mCurrentTrackIdentity = identity;
        getBridge().onSubtitleTrackChanged(identity);
    }

    /**
     * Subtitle events are recognized through {@link FormatItem#TYPE_SUBTITLE}; identity uses
     * the format id (unique within one video session) rather than a language-name guess.
     * "Subtitles off" appears only on the selection path as a format item without id/language.
     */
    private static String toTrackIdentity(FormatItem item) {
        if (item == null || item.getType() != FormatItem.TYPE_SUBTITLE) {
            return null;
        }

        String formatId = item.getFormatId();
        String language = item.getLanguage();

        if (formatId == null && language == null) {
            return IDENTITY_NONE;
        }

        return "subtitle:" + (language != null ? language : "und") + ":" + (formatId != null ? formatId : "");
    }
}
