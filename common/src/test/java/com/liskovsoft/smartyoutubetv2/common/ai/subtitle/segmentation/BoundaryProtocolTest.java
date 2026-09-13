package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoundaryProtocolTest {
    private static final SourceTrackId TRACK = new SourceTrackId("video", "track", "en");

    @Test
    public void parserAcceptsIndexedItemsAndRejectsLegacyShape() {
        BoundaryProtocolParser parser = new BoundaryProtocolParser();
        BoundaryProtocolParser.ParseResult result = parser.parse("v2|0-0|one\nv2|1-2|two three");
        assertTrue(result.isValid());
        assertEquals(2, result.getItems().size());
        assertFalse(parser.parse("one\ntwo").isValid());
    }

    @Test
    public void validatorDistinguishesCompletePrefixFromHole() {
        List<String> source = Arrays.asList("one", "two", "three");
        BoundaryProtocolParser parser = new BoundaryProtocolParser();
        List<BoundaryProtocol.Item> prefix = parser.parse("v2|0-0|ONE\nv2|1-1|TWO").getItems();
        BoundaryValidationResult partial = new BoundaryValidator().validate(source, prefix, 0, false);
        assertTrue(partial.isValid());
        assertFalse(partial.isComplete());
        assertEquals(1, partial.getAcceptedPrefixEnd());
        assertEquals("one\ntwo", partial.getReconstructedSource());

        List<BoundaryProtocol.Item> hole = parser.parse("v2|0-0|ONE\nv2|2-2|THREE").getItems();
        assertFalse(new BoundaryValidator().validate(source, hole, 0, false).isValid());
    }

    @Test
    public void coordinatorRetriesOneTailThenFallsBackAndRejectsStaleResponses() {
        List<SubtitleSegment> source = Arrays.asList(segment(0, "one"), segment(1, "two"), segment(2, "three"));
        AiSegmentationCoordinator coordinator = new AiSegmentationCoordinator();
        coordinator.begin(4, 7);
        final int[] retries = {0};
        AiSegmentationCoordinator.Acceptance accepted = coordinator.coordinate(source, "v2|0-0|ONE",
                start -> { retries[0]++; return "v2|1-2|TWO THREE"; });
        assertEquals(1, retries[0]);
        assertTrue(accepted.isComplete());
        assertEquals(3, accepted.getItems().size());
        assertEquals("TWO THREE", accepted.getItems().get(1).getTranslation());
        assertTrue(coordinator.acceptResponse(4, 7, 1));
        coordinator.cancel();
        assertFalse(coordinator.acceptResponse(4, 7, 2));
        assertFalse(coordinator.acceptResponse(3, 7, 3));
    }

    @Test
    public void invalidOutputUsesCompleteDeterministicSourceFallback() {
        List<SubtitleSegment> source = Arrays.asList(segment(0, "one"), segment(1, "two"));
        AiSegmentationCoordinator.Acceptance accepted = new AiSegmentationCoordinator().coordinate(
                source, "not indexed", null);
        assertTrue(accepted.isComplete());
        assertEquals("one", accepted.getItems().get(0).getTranslation());
        assertEquals("two", accepted.getItems().get(1).getTranslation());
    }

    @Test
    public void metricsExposeDuplicatesAndCoverage() {
        List<SubtitleSegment> source = Arrays.asList(segment(0, "one"), segment(1, "two"));
        List<BoundaryProtocol.Item> items = Arrays.asList(new BoundaryProtocol.Item(0, 0, "ONE"),
                new BoundaryProtocol.Item(0, 1, "DUPLICATE"));
        SegmentationMetrics.Metrics metrics = new SegmentationMetrics().measure(source, items);
        assertTrue(metrics.getDuplicateCount() > 0);
        assertTrue(metrics.getOverlapCount() > 0);
        assertTrue(metrics.isComplete());
    }

    private static SubtitleSegment segment(int index, String text) {
        return new SubtitleSegment(new SubtitleSegmentId(TRACK, index), index * 100, index * 100 + 50, text);
    }
}
