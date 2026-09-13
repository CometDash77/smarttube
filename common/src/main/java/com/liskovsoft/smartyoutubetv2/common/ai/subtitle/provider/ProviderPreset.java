package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editable defaults for one user-facing Provider Type. These are defaults, not capability
 * claims; users may override the base URL, headers, model, and protocol options.
 */
public final class ProviderPreset {
    public static final String OPTION_MODEL_DISCOVERY_PATH = "modelDiscoveryPath";

    private final ProviderType mType;
    private final String mDisplayName;
    private final ProviderProtocol mProtocol;
    private final String mBaseUrl;
    private final ProviderCapabilities mCapabilities;
    private final Map<String, String> mDefaultOptions;
    private final Map<String, String> mDefaultHeaders;

    private ProviderPreset(ProviderType type, String displayName, ProviderProtocol protocol,
                           String baseUrl, ProviderCapabilities capabilities,
                           Map<String, String> defaultOptions,
                           Map<String, String> defaultHeaders) {
        mType = type;
        mDisplayName = displayName;
        mProtocol = protocol;
        mBaseUrl = baseUrl;
        mCapabilities = capabilities;
        mDefaultOptions = defaultOptions == null || defaultOptions.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(defaultOptions));
        mDefaultHeaders = defaultHeaders == null || defaultHeaders.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(defaultHeaders));
    }

    public static ProviderPreset forType(ProviderType type) {
        return forType(type, ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
    }

    public static ProviderPreset forType(ProviderType type, ProviderProtocol protocol) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }

        ProviderCapabilities capabilities = new ProviderCapabilities(true, true, true, true);
        if (type == ProviderType.CUSTOM) {
            return create(type, "Custom",
                    protocol != null ? protocol : ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    "", capabilities, null, null);
        }
        switch (type) {
            case OPENAI_COMPATIBLE:
                return create(type, "OpenAI-Compatible",
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                        "https://api.openai.com/v1", capabilities, null, null);
            case ANTHROPIC_COMPATIBLE:
                Map<String, String> anthropic = new LinkedHashMap<>();
                anthropic.put(AnthropicMessagesAdapter.OPTION_AUTH_SCHEME,
                        AnthropicMessagesAdapter.AUTH_API_KEY);
                anthropic.put(AnthropicMessagesAdapter.OPTION_VERSION,
                        AnthropicMessagesAdapter.DEFAULT_VERSION);
                anthropic.put(AnthropicMessagesAdapter.OPTION_MAX_TOKENS,
                        String.valueOf(AnthropicMessagesAdapter.DEFAULT_MAX_TOKENS));
                return create(type, "Anthropic-Compatible",
                        ProviderProtocol.ANTHROPIC_MESSAGES,
                        "https://api.anthropic.com", capabilities, anthropic, null);
            case OPENROUTER:
                return create(type, "OpenRouter",
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                        "https://openrouter.ai/api/v1", capabilities, null, null);
            case DEEPSEEK:
                Map<String, String> deepSeek = new LinkedHashMap<>();
                deepSeek.put(OPTION_MODEL_DISCOVERY_PATH, "/models");
                return create(type, "DeepSeek",
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                        "https://api.deepseek.com", capabilities, deepSeek, null);
            case MIMO:
                return create(type, "MiMo",
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                        "https://api.xiaomimimo.com/v1", capabilities, null, null);
            default:
                throw new IllegalArgumentException("unsupported provider type: " + type);
        }
    }

    public ProviderType getType() {
        return mType;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public ProviderProtocol getProtocol() {
        return mProtocol;
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public ProviderCapabilities getCapabilities() {
        return mCapabilities;
    }

    public Map<String, String> getDefaultOptions() {
        return mDefaultOptions;
    }

    public Map<String, String> getDefaultHeaders() {
        return mDefaultHeaders;
    }

    public ProviderProfile createDraft(String name, String modelId) {
        return createDraft(name, modelId, mBaseUrl, mDefaultHeaders);
    }

    public ProviderProfile createDraft(String name, String modelId, String baseUrl,
                                       Map<String, String> headers) {
        return new ProviderProfile(null, name, mType, mProtocol,
                baseUrl != null ? baseUrl : mBaseUrl, null, modelId,
                Collections.<String>emptyList(), headers, mDefaultOptions);
    }

    private static ProviderPreset create(ProviderType type, String name,
                                         ProviderProtocol protocol, String baseUrl,
                                         ProviderCapabilities capabilities,
                                         Map<String, String> options,
                                         Map<String, String> headers) {
        return new ProviderPreset(type, name, protocol, baseUrl, capabilities, options, headers);
    }
}
