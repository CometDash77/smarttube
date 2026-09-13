package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict parser; older unindexed response shapes are rejected. */
public final class BoundaryProtocolParser {
    private static final Pattern ITEM = Pattern.compile("^v" + BoundaryProtocol.VERSION
            + "\\|(\\d+)-(\\d+)\\|(.*)$");

    public ParseResult parse(String output) {
        if (output == null || output.trim().isEmpty()) return ParseResult.invalid("empty output");
        String[] lines = output.replace("\r", "").split("\n");
        List<BoundaryProtocol.Item> items = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            Matcher matcher = ITEM.matcher(line);
            if (!matcher.matches()) return ParseResult.invalid("malformed indexed boundary");
            try {
                items.add(new BoundaryProtocol.Item(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), matcher.group(3)));
            } catch (RuntimeException e) {
                return ParseResult.invalid("invalid indexed boundary");
            }
        }
        return items.isEmpty() ? ParseResult.invalid("empty output") : ParseResult.valid(items);
    }

    public static final class ParseResult {
        private final List<BoundaryProtocol.Item> mItems;
        private final String mError;
        private ParseResult(List<BoundaryProtocol.Item> items, String error) {
            mItems = items; mError = error;
        }
        static ParseResult valid(List<BoundaryProtocol.Item> items) {
            return new ParseResult(Collections.unmodifiableList(new ArrayList<>(items)), null);
        }
        static ParseResult invalid(String error) { return new ParseResult(Collections.<BoundaryProtocol.Item>emptyList(), error); }
        public boolean isValid() { return mError == null; }
        public List<BoundaryProtocol.Item> getItems() { return mItems; }
        public String getError() { return mError; }
    }
}
