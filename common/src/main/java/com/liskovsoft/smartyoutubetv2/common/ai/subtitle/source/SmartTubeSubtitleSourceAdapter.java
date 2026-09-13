package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceCue;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.RuleSentenceBreaker;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.TranslationChunker;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the full subtitle timeline for the active video and track through the format-info
 * service and timed-text download, then normalizes and chunks it into translation units.
 *
 * <p>ADR-005: this adapter exists because displayed cues arrive too late for lookahead. It
 * reuses {@link SubtitleNormalizer}, {@link RuleSentenceBreaker}, and
 * {@link TranslationChunker}; no second timeline model is created.</p>
 */
public final class SmartTubeSubtitleSourceAdapter {
    /** Maximum characters per translation unit; conservative single-sentence default. */
    private static final int TARGET_CHARS = 60;
    private static final int MAX_CHARS = 200;

    private final SubtitleFetcher mFetcher;
    private final SubtitleDownloader mDownloader;

    /** Delivers the full timeline for one video/track pair. */
    public interface Callback {
        void onTimelineReady(SourceTrackId trackId, SourceTimeline timeline);
        void onTimelineFailed(SourceTrackId trackId, String reason);
    }

    /** Fetches the format-info subtitle list for one video. */
    public interface SubtitleFetcher {
        void fetch(String videoId, SubtitleListListener listener);
    }

    public interface SubtitleListListener {
        void onSubtitles(List<MediaSubtitle> subtitles);
        void onError(String message);
    }

    /** Downloads the timed-text content at the given URL. */
    public interface SubtitleDownloader {
        String download(String url);
    }

    public SmartTubeSubtitleSourceAdapter(SubtitleFetcher fetcher, SubtitleDownloader downloader) {
        mFetcher = fetcher;
        mDownloader = downloader;
    }

    /** Begins an async load; exactly one callback method is invoked. */
    public void load(String videoId, SourceTrackId trackId, Callback callback) {
        if (videoId == null || videoId.trim().isEmpty() || trackId == null) {
            callback.onTimelineFailed(trackId, "no video or track");
            return;
        }

        try {
            mFetcher.fetch(videoId, new SubtitleListListener() {
                @Override
                public void onSubtitles(List<MediaSubtitle> subtitles) {
                    deliverTimeline(subtitles, trackId, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onTimelineFailed(trackId, message);
                }
            });
        } catch (Exception e) {
            callback.onTimelineFailed(trackId, "fetch error: " + e.getMessage());
        }
    }

    private void deliverTimeline(List<MediaSubtitle> subtitles,
                                 SourceTrackId trackId, Callback callback) {
        try {
            SourceTimeline timeline = buildTimeline(subtitles, trackId, mDownloader);
            if (timeline == null) {
                callback.onTimelineFailed(trackId, "no matching subtitle track");
            } else {
                callback.onTimelineReady(trackId, timeline);
            }
        } catch (Exception e) {
            callback.onTimelineFailed(trackId, "parse error: " + e.getMessage());
        }
    }

    /**
     * Pure pipeline: match subtitle -> download -> parse -> normalize -> segment -> chunk.
     * Package-private for independent testing.
     */
    SourceTimeline buildTimeline(List<MediaSubtitle> subtitles, SourceTrackId trackId,
                                 SubtitleDownloader downloader) {
        MediaSubtitle matched = matchSubtitle(subtitles, trackId);
        if (matched == null || matched.getBaseUrl() == null) {
            return null;
        }

        String content = downloader.download(toVttUrl(matched.getBaseUrl()));
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        List<SourceCue> cues = new VttParser().parse(content);
        List<SourceCue> normalized = new SubtitleNormalizer().normalize(cues);
        if (normalized.isEmpty()) {
            return null;
        }

        List<SubtitleSegment> segments = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            SourceCue cue = normalized.get(i);
            segments.add(new SubtitleSegment(
                    new SubtitleSegmentId(trackId, i),
                    cue.getStartTimeMs(), cue.getEndTimeMs(), cue.getText()));
        }

        // breakSentences re-indexes the segments with a stable incrementing index.
        List<SubtitleSegment> sentences = new RuleSentenceBreaker().breakSentences(segments);
        List<TranslationUnit> units = new TranslationChunker().chunk(sentences, TARGET_CHARS, MAX_CHARS);

        return new SourceTimeline(sentences, units);
    }

    /**
     * Matches the user's selected track by language. Tries the exact language-code match
     * first, then falls back to a normalized display-name comparison that handles
     * "English (auto-generated)" style names.
     */
    static MediaSubtitle matchSubtitle(List<MediaSubtitle> subtitles, SourceTrackId trackId) {
        if (subtitles == null || subtitles.isEmpty() || trackId == null) {
            return null;
        }

        String language = extractLanguage(trackId.getTrackId());
        if (language == null) {
            return null;
        }

        String normalized = normalizeLanguage(language);

        // Prefer exact language-code match over name match (disambiguates manual vs ASR).
        MediaSubtitle codeMatch = null;
        MediaSubtitle nameMatch = null;

        for (MediaSubtitle sub : subtitles) {
            if (sub == null) continue;
            if (normalized.equalsIgnoreCase(normalizeLanguage(sub.getLanguageCode()))) {
                codeMatch = codeMatch != null ? codeMatch : sub;
            }
            if (normalized.equalsIgnoreCase(normalizeLanguage(sub.getName()))) {
                nameMatch = nameMatch != null ? nameMatch : sub;
            }
        }

        return codeMatch != null ? codeMatch : nameMatch;
    }

    /** Appends fmt=vtt to force the VTT wire format when the base URL is a timed-text URL. */
    static String toVttUrl(String baseUrl) {
        if (baseUrl == null) return null;
        if (baseUrl.contains("timedtext") && !baseUrl.contains("fmt=")) {
            return baseUrl + "&fmt=vtt";
        }
        return baseUrl;
    }

    /** Extracts the language component from a track identity like "subtitle:en:formatId". */
    static String extractLanguage(String trackIdentity) {
        if (trackIdentity == null || !trackIdentity.startsWith("subtitle:")) {
            return null;
        }
        String rest = trackIdentity.substring("subtitle:".length());
        int sep = rest.indexOf(':');
        return sep > 0 ? rest.substring(0, sep) : rest;
    }

    private static String normalizeLanguage(String language) {
        if (language == null) return "";
        return language.trim().replaceFirst(" \\(.*\\)$", "").trim();
    }
}


