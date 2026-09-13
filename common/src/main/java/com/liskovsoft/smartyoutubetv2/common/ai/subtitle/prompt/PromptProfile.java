package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

/** Immutable, versioned subtitle prompt profile. */
public final class PromptProfile {
    private final String mId;
    private final String mName;
    private final String mContent;
    private final int mVersion;
    private final boolean mBuiltIn;

    public PromptProfile(String id, String name, String content, int version, boolean builtIn) {
        mId = requireNonBlank(id, "id");
        mName = requireNonBlank(name, "name");
        mContent = requireNonBlank(content, "content");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive: " + version);
        }
        mVersion = version;
        mBuiltIn = builtIn;
    }

    public String getId() { return mId; }
    public String getName() { return mName; }
    public String getContent() { return mContent; }
    public int getVersion() { return mVersion; }
    public boolean isBuiltIn() { return mBuiltIn; }

    public String getContentHash() {
        return Integer.toHexString(mContent.hashCode());
    }

    public PromptProfile withId(String id) {
        return new PromptProfile(id, mName, mContent, mVersion, mBuiltIn);
    }

    public PromptProfile withCustomValues(String name, String content) {
        return new PromptProfile(mId, name, content, mVersion + 1, false);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PromptProfile)) return false;
        PromptProfile profile = (PromptProfile) other;
        return mVersion == profile.mVersion && mBuiltIn == profile.mBuiltIn
                && same(mId, profile.mId) && same(mName, profile.mName)
                && same(mContent, profile.mContent);
    }

    @Override
    public int hashCode() {
        int result = mId.hashCode();
        result = 31 * result + mName.hashCode();
        result = 31 * result + mContent.hashCode();
        result = 31 * result + mVersion;
        result = 31 * result + (mBuiltIn ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PromptProfile{id=" + mId + ", name=" + mName + ", version=" + mVersion
                + ", builtIn=" + mBuiltIn + ", contentHash=" + getContentHash() + "}";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
