package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Network-free fallback that preserves complete source coverage one unit at a time. */
public final class DeterministicSegmentationFallback {
    public List<BoundaryProtocol.Item> fallback(List<SubtitleSegment> source, int fromIndex) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<BoundaryProtocol.Item> result = new ArrayList<>();
        int start = Math.max(0, fromIndex);
        for (int i = start; i < source.size(); i++) {
            result.add(new BoundaryProtocol.Item(i, i, source.get(i).getSourceText()));
        }
        return Collections.unmodifiableList(result);
    }
}
