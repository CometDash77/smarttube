package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Coverage decision separating hard protocol errors from incomplete-tail warnings. */
public final class BoundaryValidationResult {
    private final boolean mValid;
    private final boolean mComplete;
    private final int mAcceptedPrefixEnd;
    private final String mReconstructedSource;
    private final List<String> mWarnings;
    private final String mHardError;

    private BoundaryValidationResult(boolean valid, boolean complete, int prefixEnd,
                                     String source, List<String> warnings, String hardError) {
        mValid = valid; mComplete = complete; mAcceptedPrefixEnd = prefixEnd;
        mReconstructedSource = source; mWarnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        mHardError = hardError;
    }

    static BoundaryValidationResult success(boolean complete, int end, String source, List<String> warnings) {
        return new BoundaryValidationResult(true, complete, end, source, warnings, null);
    }
    static BoundaryValidationResult failure(String error) {
        return new BoundaryValidationResult(false, false, -1, "", Collections.<String>emptyList(), error);
    }
    public boolean isValid() { return mValid; }
    public boolean isComplete() { return mComplete; }
    public int getAcceptedPrefixEnd() { return mAcceptedPrefixEnd; }
    public String getReconstructedSource() { return mReconstructedSource; }
    public List<String> getWarnings() { return mWarnings; }
    public String getHardError() { return mHardError; }
}
