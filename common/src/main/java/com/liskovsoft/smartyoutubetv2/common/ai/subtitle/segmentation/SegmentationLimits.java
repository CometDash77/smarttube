package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

/**
 * The single rule that decides whether a set of segmentation limits is usable.
 *
 * <p>The three values travel together wherever they appear: the settings store that persists them,
 * the bridge that applies them to a running session, and the source adapter that cuts the timeline
 * with them. A change to what counts as valid has to reach all three, so the rule lives here
 * instead of being restated at each door.</p>
 *
 * <p>Parameters are {@code long} on purpose. The settings path range-checks values before
 * narrowing them, and an {@code int} signature would let an out-of-range value truncate into
 * something that looks valid.</p>
 */
public final class SegmentationLimits {
    private SegmentationLimits() {
    }

    public static boolean isValid(long targetChars, long maxChars, long longSentenceChars) {
        return targetChars >= 1 && maxChars >= targetChars && longSentenceChars > 0;
    }
}
