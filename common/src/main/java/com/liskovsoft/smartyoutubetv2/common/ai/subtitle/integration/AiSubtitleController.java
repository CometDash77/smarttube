package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration;

import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.SmartTubeSubtitleSourceAdapter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Owns the AI subtitle lifecycle inside SmartTube's ordered controller list. It forwards
 * lifecycle and position events only; source fetching is delegated to the feature-owned
 * subtitle adapter and request scheduling is delegated to the cue bridge's scheduler.
 */
public class AiSubtitleController extends BasePlayerController {
    static final String IDENTITY_NONE = "subtitle:none";

    private static final long SEEK_POSITION_UNKNOWN = -1;
    private static final int DOWNLOAD_TIMEOUT_MS = 10_000;
    /** Hard byte limit for one timed-text download; over it the timeline is refused. */
    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final long SCHEDULER_TICK_MS = 1_000;

    private String mCurrentTrackIdentity;
    private long mSeekPositionMs = SEEK_POSITION_UNKNOWN;
    private SmartTubeSubtitleSourceAdapter mSourceAdapter;
    private Disposable mSourceFetchAction;
    private Handler mSchedulerHandler;
    private final Runnable mSchedulerTick = new Runnable() {
        @Override
        public void run() {
            updatePosition();
            scheduleSchedulerTick();
        }
    };

    @Override
    public void onNewVideo(Video item) {
        ensureSourceAdapter();
        disposeSourceFetch();
        mCurrentTrackIdentity = null;
        mSeekPositionMs = SEEK_POSITION_UNKNOWN;
        getBridge().onNewVideo(item != null ? item.videoId : null,
                item != null ? item.title : null, item != null ? item.description : null);
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
    public void onEngineInitialized() {
        ensureSourceAdapter();
        scheduleSchedulerTick();
    }

    @Override
    public void onSeekEnd() {
        long positionMs = mSeekPositionMs;

        if (positionMs == SEEK_POSITION_UNKNOWN) {
            PlaybackView player = getPlayer();
            positionMs = player != null ? player.getPositionMs() : SEEK_POSITION_UNKNOWN;
        }

        mSeekPositionMs = SEEK_POSITION_UNKNOWN;
        getBridge().onSeek(positionMs);
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        // Keep only the latest drag position; dispatching waits for seek completion.
        mSeekPositionMs = positionMs;
        getBridge().onSeekDrag(positionMs);
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
    public void onTickle() {
        updatePosition();
    }

    @Override
    public void onEngineReleased() {
        stopSchedulerTick();
        disposeSourceFetch();
        mCurrentTrackIdentity = null;
        mSeekPositionMs = SEEK_POSITION_UNKNOWN;
        getBridge().onRelease();
    }

    @Override
    public void onFinish() {
        stopSchedulerTick();
        disposeSourceFetch();
        mCurrentTrackIdentity = null;
        mSeekPositionMs = SEEK_POSITION_UNKNOWN;
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

    private void ensureSchedulerHandler() {
        if (mSchedulerHandler == null) {
            mSchedulerHandler = new Handler(Looper.getMainLooper());
        }
    }

    private void updatePosition() {
        PlaybackView player = getPlayer();

        if (player != null) {
            getBridge().onPositionUpdate(player.getPositionMs());
        }
    }

    private void scheduleSchedulerTick() {
        ensureSchedulerHandler();
        mSchedulerHandler.removeCallbacks(mSchedulerTick);
        mSchedulerHandler.postDelayed(mSchedulerTick, SCHEDULER_TICK_MS);
    }

    private void stopSchedulerTick() {
        if (mSchedulerHandler == null) return;

        mSchedulerHandler.removeCallbacks(mSchedulerTick);
    }

    private void ensureSourceAdapter() {
        if (mSourceAdapter != null) return;

        mSourceAdapter = new SmartTubeSubtitleSourceAdapter(
                new SmartTubeSubtitleSourceAdapter.SubtitleFetcher() {
                    @Override
                    public void fetch(String videoId,
                                      final SmartTubeSubtitleSourceAdapter.SubtitleListListener listener) {
                        // A new fetch supersedes the one in flight; leaving the old subscription
                        // alive would let a stale subtitle list answer for this request.
                        disposeSourceFetch();

                        mSourceFetchAction = getMediaItemService().getFormatInfoObserve(videoId)
                                .observeOn(Schedulers.io())
                                .subscribe(
                                        formatInfo -> listener.onSubtitles(formatInfo.getSubtitles()),
                                        error -> listener.onError("subtitle format info failed"));
                    }
                },
                new SmartTubeSubtitleSourceAdapter.SubtitleDownloader() {
                    @Override
                    public String download(String url) {
                        return downloadVtt(url);
                    }
                });

        getBridge().setSourceAdapter(mSourceAdapter);
    }

    private void disposeSourceFetch() {
        if (mSourceFetchAction != null) {
            mSourceFetchAction.dispose();
            mSourceFetchAction = null;
        }
    }

    private static String downloadVtt(String url) {
        if (url == null || url.trim().isEmpty()) return null;

        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(DOWNLOAD_TIMEOUT_MS);
            connection.setReadTimeout(DOWNLOAD_TIMEOUT_MS);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) return null;

            InputStream input = connection.getInputStream();
            ByteArrayOutputStream content = new ByteArrayOutputStream(64 * 1024);
            byte[] buffer = new byte[8_192];
            int length;

            while ((length = input.read(buffer)) >= 0) {
                if (content.size() + length > MAX_DOWNLOAD_BYTES) {
                    // Over the limit the timeline is refused rather than truncated, so the
                    // adapter can never claim a complete timeline it does not have.
                    return null;
                }
                content.write(buffer, 0, length);
            }

            return content.size() > 0 ? new String(content.toByteArray(), Charset.forName("UTF-8")) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Subtitle events are recognized through {@link FormatItem#TYPE_SUBTITLE}; identity uses
     * the format id (unique within one video session) rather than a language-name guess.
     */
    private static String toTrackIdentity(FormatItem item) {
        if (item == null || item.getType() != FormatItem.TYPE_SUBTITLE) return null;

        String formatId = item.getFormatId();
        String language = item.getLanguage();

        if (formatId == null && language == null) return IDENTITY_NONE;

        return "subtitle:" + (language != null ? language : "und") + ":"
                + (formatId != null ? formatId : "");
    }
}
