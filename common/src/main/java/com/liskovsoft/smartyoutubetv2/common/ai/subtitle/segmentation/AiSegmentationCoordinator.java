package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accepts only continuous current-generation output and retries one uncovered tail. */
public final class AiSegmentationCoordinator {
    private final BoundaryProtocolParser mParser = new BoundaryProtocolParser();
    private final BoundaryValidator mValidator = new BoundaryValidator();
    private final DeterministicSegmentationFallback mFallback = new DeterministicSegmentationFallback();
    private long mGeneration;
    private long mEpoch;
    private boolean mCancelled;

    public void begin(long generation, long epoch) {
        mGeneration = generation; mEpoch = epoch; mCancelled = false;
    }

    public void cancel() { mCancelled = true; }

    public Acceptance coordinate(List<SubtitleSegment> source, String firstOutput,
                                 TailRequester tailRequester) {
        if (source == null || source.isEmpty()) return Acceptance.sourceOnly();
        ParseResult first = parse(firstOutput);
        if (!first.valid) return Acceptance.fromFallback(mFallback.fallback(source, 0));
        BoundaryValidationResult prefix = mValidator.validate(sourceTexts(source), first.items, 0, false);
        if (!prefix.isValid()) return Acceptance.fromFallback(mFallback.fallback(source, 0));
        List<BoundaryProtocol.Item> accepted = new ArrayList<>(first.items);
        if (prefix.isComplete()) return Acceptance.complete(accepted);
        int tailStart = prefix.getAcceptedPrefixEnd() + 1;
        if (tailRequester != null) {
            String tailOutput = tailRequester.request(tailStart);
            ParseResult tail = parse(tailOutput);
            if (tail.valid) {
                BoundaryValidationResult tailResult = mValidator.validate(sourceTexts(source), tail.items, tailStart, true);
                if (tailResult.isValid() && tailResult.isComplete()) {
                    accepted.addAll(tail.items);
                    return Acceptance.complete(accepted);
                }
            }
        }
        accepted.addAll(mFallback.fallback(source, tailStart));
        return Acceptance.complete(accepted);
    }

    public boolean acceptResponse(long generation, long epoch, long requestId) {
        return !mCancelled && generation == mGeneration && epoch == mEpoch && requestId > 0;
    }

    private ParseResult parse(String output) {
        BoundaryProtocolParser.ParseResult parsed = mParser.parse(output);
        return new ParseResult(parsed.isValid(), parsed.getItems());
    }

    private static List<String> sourceTexts(List<SubtitleSegment> source) {
        List<String> texts = new ArrayList<>();
        for (SubtitleSegment segment : source) texts.add(segment.getSourceText());
        return texts;
    }

    public interface TailRequester { String request(int startIndex); }

    private static final class ParseResult {
        private final boolean valid; private final List<BoundaryProtocol.Item> items;
        ParseResult(boolean valid, List<BoundaryProtocol.Item> items) { this.valid = valid; this.items = items; }
    }

    public static final class Acceptance {
        private final List<BoundaryProtocol.Item> mItems;
        private final boolean mSourceOnly;
        private Acceptance(List<BoundaryProtocol.Item> items, boolean sourceOnly) {
            mItems = Collections.unmodifiableList(new ArrayList<>(items)); mSourceOnly = sourceOnly;
        }
        static Acceptance complete(List<BoundaryProtocol.Item> items) { return new Acceptance(items, false); }
        static Acceptance fromFallback(List<BoundaryProtocol.Item> items) { return new Acceptance(items, false); }
        static Acceptance sourceOnly() { return new Acceptance(Collections.<BoundaryProtocol.Item>emptyList(), true); }
        public List<BoundaryProtocol.Item> getItems() { return mItems; }
        public boolean isComplete() { return !mSourceOnly && !mItems.isEmpty(); }
        public boolean isSourceOnly() { return mSourceOnly; }
    }
}
