package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

/** Versioned line protocol for indexed translation boundaries. */
public final class BoundaryProtocol {
    public static final int VERSION = 2;

    private BoundaryProtocol() { }

    public static String encodeItem(int startIndex, int endIndex, String translation) {
        if (startIndex < 0 || endIndex < startIndex || translation == null
                || translation.trim().isEmpty()) throw new IllegalArgumentException("invalid boundary item");
        return "v" + VERSION + "|" + startIndex + "-" + endIndex + "|"
                + translation.replace("\r", "").replace("\n", " ");
    }

    public static final class Item {
        private final int mStartIndex;
        private final int mEndIndex;
        private final String mTranslation;
        public Item(int startIndex, int endIndex, String translation) {
            if (startIndex < 0 || endIndex < startIndex || translation == null
                    || translation.trim().isEmpty()) throw new IllegalArgumentException("invalid boundary item");
            mStartIndex = startIndex; mEndIndex = endIndex; mTranslation = translation;
        }
        public int getStartIndex() { return mStartIndex; }
        public int getEndIndex() { return mEndIndex; }
        public String getTranslation() { return mTranslation; }
    }
}
