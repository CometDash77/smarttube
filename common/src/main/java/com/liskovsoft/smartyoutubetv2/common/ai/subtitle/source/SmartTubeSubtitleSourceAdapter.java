package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source;

import androidx.annotation.VisibleForTesting;

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
    private int mTargetChars = 60;
    private int mMaxChars = 200;
    private int mLongSentenceChars = 80;

    public synchronized void configureSegmentation(int targetChars, int maxChars,
                                                   int longSentenceChars) {
        if (targetChars < 1 || maxChars < targetChars || longSentenceChars <= 0) {
            throw new IllegalArgumentException("invalid segmentation limits");
        }
        mTargetChars = targetChars;
        mMaxChars = maxChars;
        mLongSentenceChars = longSentenceChars;
    }

    @VisibleForTesting
    public synchronized int[] segmentationLimitsForTesting() {
        return new int[] {mTargetChars, mMaxChars, mLongSentenceChars};
    }


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
        List<SubtitleSegment> sentences = new RuleSentenceBreaker(mLongSentenceChars).breakSentences(segments);
        List<TranslationUnit> units = new TranslationChunker().chunk(sentences, mTargetChars, mMaxChars);

        return new SourceTimeline(sentences, units);
    }

    /**
     * Matches the selected track against the format-info subtitle list.
     *
     * <p>The track identity carries the same language string the player received for the
     * selected format, which is the caption's display name when the service provides one and
     * the language code otherwise. Name equality is therefore tried before code equality, and
     * the lossy "strip the trailing parenthetical" comparison is only used when it identifies
     * exactly one track. Anything ambiguous returns null so the caller falls back to the
     * original subtitles instead of translating a track the user did not select.</p>
     */
    static MediaSubtitle matchSubtitle(List<MediaSubtitle> subtitles, SourceTrackId trackId) {
        if (subtitles == null || subtitles.isEmpty() || trackId == null) {
            return null;
        }

        String requested = extractLanguage(trackId.getTrackId());
        if (requested == null || requested.trim().isEmpty()) {
            return null;
        }

        MediaSubtitle exactName = uniqueMatch(subtitles, requested, MATCH_NAME_EXACT);
        if (exactName != null) return exactName;

        MediaSubtitle exactCode = uniqueMatch(subtitles, requested, MATCH_CODE_EXACT);
        if (exactCode != null) return exactCode;

        return uniqueMatch(subtitles, requested, MATCH_NAME_NORMALIZED);
    }

    private static final int MATCH_NAME_EXACT = 0;
    private static final int MATCH_CODE_EXACT = 1;
    private static final int MATCH_NAME_NORMALIZED = 2;

    /** Returns the single matching entry, or null when there are none or more than one. */
    private static MediaSubtitle uniqueMatch(List<MediaSubtitle> subtitles, String requested,
                                             int mode) {
        MediaSubtitle match = null;

        for (MediaSubtitle sub : subtitles) {
            if (sub == null || !matches(sub, requested, mode)) continue;

            if (match != null) return null;
            match = sub;
        }

        return match;
    }

    private static boolean matches(MediaSubtitle sub, String requested, int mode) {
        switch (mode) {
            case MATCH_NAME_EXACT:
                return equalsIgnoringCase(requested, sub.getName());
            case MATCH_CODE_EXACT:
                return equalsIgnoringCase(requested, sub.getLanguageCode());
            case MATCH_NAME_NORMALIZED:
                String normalized = normalizeLanguage(requested);
                return !normalized.isEmpty()
                        && normalized.equalsIgnoreCase(normalizeLanguage(sub.getName()));
            default:
                return false;
        }
    }

    private static boolean equalsIgnoringCase(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }

    /**
     * Rewrites the {@code fmt} query parameter to {@code vtt} for timed-text URLs so the VTT
     * parser gets the wire format it expects. Every other parameter is preserved byte for byte,
     * and a URL without the timed-text marker is returned untouched.
     */
    static String toVttUrl(String baseUrl) {
        if (baseUrl == null) return null;
        if (!baseUrl.contains("timedtext")) return baseUrl;

        String fragment = "";
        String withoutFragment = baseUrl;
        int hash = baseUrl.indexOf('#');

        if (hash >= 0) {
            fragment = baseUrl.substring(hash);
            withoutFragment = baseUrl.substring(0, hash);
        }

        String prefix = withoutFragment;
        String query = null;
        int questionMark = withoutFragment.indexOf('?');

        if (questionMark >= 0) {
            query = withoutFragment.substring(questionMark + 1);
            prefix = withoutFragment.substring(0, questionMark);
        }

        if (query == null) return prefix + "?fmt=vtt" + fragment;

        StringBuilder rebuilt = new StringBuilder();

        for (String parameter : query.split("&", -1)) {
            if (parameter.isEmpty()) continue;

            int equals = parameter.indexOf('=');
            String name = equals >= 0 ? parameter.substring(0, equals) : parameter;

            // Any existing format request is replaced; every other parameter is preserved.
            if ("fmt".equals(name)) continue;

            if (rebuilt.length() > 0) rebuilt.append('&');
            rebuilt.append(parameter);
        }

        if (rebuilt.length() > 0) rebuilt.append('&');
        rebuilt.append("fmt=vtt");

        return prefix + "?" + rebuilt + fragment;
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


