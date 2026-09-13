package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import java.util.ArrayList;
import java.util.List;

/** Validates indexed ranges and reconstructs the covered source text. */
public final class BoundaryValidator {
    public BoundaryValidationResult validate(List<String> sourceTexts,
                                              List<BoundaryProtocol.Item> items,
                                              int requestedStart, boolean requireComplete) {
        if (sourceTexts == null || sourceTexts.isEmpty() || requestedStart < 0
                || requestedStart >= sourceTexts.size()) return BoundaryValidationResult.failure("invalid source range");
        if (items == null || items.isEmpty()) return BoundaryValidationResult.failure("no boundary items");
        if (items.get(0) == null || items.get(0).getStartIndex() != requestedStart) {
            return BoundaryValidationResult.failure("prefix does not start at request");
        }
        StringBuilder source = new StringBuilder();
        List<String> warnings = new ArrayList<>();
        int previousEnd = requestedStart - 1;
        for (BoundaryProtocol.Item item : items) {
            if (item == null || item.getStartIndex() < requestedStart
                    || item.getEndIndex() >= sourceTexts.size()) return BoundaryValidationResult.failure("boundary is out of range");
            if (item.getEndIndex() < item.getStartIndex()) {
                return BoundaryValidationResult.failure("boundary range is reversed");
            }
            if (item.getStartIndex() <= previousEnd) return BoundaryValidationResult.failure("duplicate or overlapping boundary");
            if (item.getStartIndex() != previousEnd + 1) return BoundaryValidationResult.failure("boundary has a coverage hole");
            for (int i = item.getStartIndex(); i <= item.getEndIndex(); i++) {
                if (source.length() > 0) source.append('\n');
                source.append(sourceTexts.get(i));
            }
            previousEnd = item.getEndIndex();
        }
        boolean complete = previousEnd == sourceTexts.size() - 1;
        if (!complete) {
            warnings.add("tail is uncovered");
            if (requireComplete) return BoundaryValidationResult.failure("tail is uncovered");
        }
        return BoundaryValidationResult.success(complete, previousEnd, source.toString(), warnings);
    }
}
