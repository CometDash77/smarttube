package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistable, non-secret identity and configuration of one Provider Profile.
 *
 * <p>The profile contains only a reference to credential material; it never contains an API
 * key. All mutable collection inputs are defensively copied and exposed as unmodifiable
 * views.</p>
 */
public final class ProviderProfile {
    private final String mId;
    private final String mName;
    private final ProviderType mProviderType;
    private final ProviderProtocol mProtocol;
    private final String mBaseUrl;
    private final String mSecretReference;
    private final String mModelId;
    private final List<String> mAvailableModelIds;
    private final Map<String, String> mHeaders;
    private final Map<String, String> mOptions;

    public ProviderProfile(String id, String name, ProviderType providerType,
                           ProviderProtocol protocol, String baseUrl, String secretReference,
                           String modelId, List<String> availableModelIds,
                           Map<String, String> headers, Map<String, String> options) {
        mId = normalizeOptional(id);
        mName = requireNonBlank(name, "name");
        mProviderType = requireValue(providerType, "providerType");
        mProtocol = requireValue(protocol, "protocol");
        mBaseUrl = requireCredentialFree(requireNonBlank(baseUrl, "baseUrl"));
        mSecretReference = normalizeOptional(secretReference);
        mModelId = normalizeOptional(modelId);
        mAvailableModelIds = immutableList(availableModelIds);
        mHeaders = immutableMap(headers);
        mOptions = immutableMap(options);
    }

    public String getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public ProviderType getProviderType() {
        return mProviderType;
    }

    public ProviderProtocol getProtocol() {
        return mProtocol;
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public String getSecretReference() {
        return mSecretReference;
    }

    public String getModelId() {
        return mModelId;
    }

    public List<String> getAvailableModelIds() {
        return mAvailableModelIds;
    }

    public Map<String, String> getHeaders() {
        return mHeaders;
    }

    public Map<String, String> getOptions() {
        return mOptions;
    }

    /** Returns a copy with an assigned stable id; used only when persisting an unsaved draft. */
    public ProviderProfile withId(String id) {
        return new ProviderProfile(id, mName, mProviderType, mProtocol, mBaseUrl,
                mSecretReference, mModelId, mAvailableModelIds, mHeaders, mOptions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderProfile)) {
            return false;
        }
        ProviderProfile other = (ProviderProfile) o;
        return sameValue(mId, other.mId)
                && sameValue(mName, other.mName)
                && mProviderType == other.mProviderType
                && mProtocol == other.mProtocol
                && sameValue(mBaseUrl, other.mBaseUrl)
                && sameValue(mSecretReference, other.mSecretReference)
                && sameValue(mModelId, other.mModelId)
                && sameValue(mAvailableModelIds, other.mAvailableModelIds)
                && sameValue(mHeaders, other.mHeaders)
                && sameValue(mOptions, other.mOptions);
    }

    @Override
    public int hashCode() {
        // Explicit Java 6-compatible hashing: java.util.Objects is API 19+.
        int result = valueHash(mId);
        result = 31 * result + valueHash(mName);
        result = 31 * result + valueHash(mProviderType);
        result = 31 * result + valueHash(mProtocol);
        result = 31 * result + valueHash(mBaseUrl);
        result = 31 * result + valueHash(mSecretReference);
        result = 31 * result + valueHash(mModelId);
        result = 31 * result + valueHash(mAvailableModelIds);
        result = 31 * result + valueHash(mHeaders);
        result = 31 * result + valueHash(mOptions);
        return result;
    }

    @Override
    public String toString() {
        return "ProviderProfile{id=" + mId
                + ", name=" + mName
                + ", type=" + mProviderType
                + ", protocol=" + mProtocol
                + ", baseUrl=" + mBaseUrl
                + ", model=" + mModelId + "}";
    }

    private static <T> T requireValue(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String requireCredentialFree(String baseUrl) {
        if (baseUrl.indexOf('@') >= 0) {
            throw new IllegalArgumentException("baseUrl must not contain embedded credentials");
        }
        return baseUrl;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static List<String> immutableList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static Map<String, String> immutableMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static boolean sameValue(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int valueHash(Object value) {
        return value != null ? value.hashCode() : 0;
    }
}