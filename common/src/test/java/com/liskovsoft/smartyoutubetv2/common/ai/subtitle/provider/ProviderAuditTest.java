package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SourceTrackId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.SubtitleSegmentId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.TranslationUnit;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.TranslationSessionId;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCall;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationCallback;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Provider and secret audit: every Provider Type goes through its protocol against a fake
 * transport, and a unique credential canary is checked against every diagnostic string the
 * feature can produce. No paid provider is called.
 */
public class ProviderAuditTest {
    private static final String CANARY = "canary-3f9a-unique-secret-value";
    private static final long TIMEOUT_MS = 5_000;

    @Test
    public void everyProviderTypeResolvesToAProtocolAndCompletesThroughFakeHttp() {
        List<String> completed = new ArrayList<>();

        for (ProviderType type : ProviderType.values()) {
            ProviderPreset preset = ProviderPreset.forType(type);
            String baseUrl = "custom".equals(preset.getDisplayName().toLowerCase())
                    ? "https://api.example.test/v1" : preset.getBaseUrl();

            for (ProviderProtocol protocol : ProviderProtocol.values()) {
                if (type != ProviderType.CUSTOM && preset.getProtocol() != protocol) continue;

                ProviderProfile profile = new ProviderProfile("profile-" + type + "-" + protocol,
                        "Audit " + type, type, protocol, baseUrl, "audit-ref",
                        "audit-model", Collections.<String>emptyList(),
                        new LinkedHashMap<String, String>(), new LinkedHashMap<String, String>());
                ProviderProfileResolver.Resolution resolved =
                        new ProviderProfileResolver(new RespondingExecutor(protocol),
                                new FixedSecrets(CANARY)).resolve(profile);

                assertTrue(type + "/" + protocol + " must resolve", resolved.isResolved());

                RecordingCallback callback = new RecordingCallback();
                resolved.getAdapter().translate(request(protocol, baseUrl), callback);

                assertEquals(type + "/" + protocol + " must complete",
                        "translated", callback.result != null ? callback.result.getTranslatedText() : null);
                completed.add(type + "/" + protocol);
            }
        }

        assertTrue("every provider type must have been exercised: " + completed,
                completed.size() >= ProviderType.values().length);
    }

    @Test
    public void theCredentialCanaryNeverAppearsInAnyDiagnostic() {
        RespondingExecutor executor = new RespondingExecutor();
        ProviderProfile profile = new ProviderProfile("profile-canary", "Canary",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.test/v1", "audit-ref", "audit-model",
                Collections.<String>emptyList(), new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>());

        TranslationProfile resolvedProfile = new TranslationProfile("profile-canary",
                "openai-chat-completions", "https://api.example.test/v1", "audit-model",
                "prompt-1", 1, "zh");
        SourceTrackId track = new SourceTrackId("video-1", "track-1", "en");
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(track, 0)), "Hello.");
        TranslationRequest request = new TranslationRequest(new TranslationSessionId(
                "video-1", track, resolvedProfile, TranslationSessionId.ENGINE_SCHEMA_VERSION),
                1, unit, "Translate the subtitle.");

        // Success, provider error, and cancellation all have to stay quiet about the credential.
        for (String response : new String[] {
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}",
                "{\"error\":{\"message\":\"" + CANARY + "\"}}"}) {
            executor.respond(response, 200);
            RecordingCallback callback = new RecordingCallback();
            OpenAiChatCompletionsAdapter adapter = new OpenAiChatCompletionsAdapter(
                    executor, profile, new FixedSecrets(CANARY));
            TranslationCall call = adapter.translate(request, callback);
            call.cancel();

            for (String diagnostic : executor.diagnostics(adapter, request, call)) {
                assertFalse("a diagnostic leaked the credential: " + diagnostic,
                        diagnostic.contains(CANARY));
            }
        }

        assertTrue("the audit must have produced diagnostics to check",
                executor.getLastRequest() != null);
    }

    @Test
    public void replacingTheSecretChangesWhatTheNextRequestCarries() {
        RespondingExecutor executor = new RespondingExecutor();
        MutableSecrets secrets = new MutableSecrets("first-secret-value");
        ProviderProfile profile = new ProviderProfile("profile-rotate", "Rotate",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.test/v1", "audit-ref", "audit-model",
                Collections.<String>emptyList(), new LinkedHashMap<String, String>(),
                new LinkedHashMap<String, String>());
        OpenAiChatCompletionsAdapter adapter = new OpenAiChatCompletionsAdapter(
                executor, profile, secrets);
        RecordingCallback callback = new RecordingCallback();

        adapter.translate(request(ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.test/v1"), callback);
        assertEquals("Bearer first-secret-value",
                executor.getLastRequest().getHeaders().get("Authorization"));

        secrets.value = "second-secret-value";
        adapter.translate(request(ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                "https://api.example.test/v1"), new RecordingCallback());

        assertEquals("a replaced credential must be used by the next request",
                "Bearer second-secret-value",
                executor.getLastRequest().getHeaders().get("Authorization"));
    }

    private static TranslationRequest request(ProviderProtocol protocol, String baseUrl) {
        SourceTrackId track = new SourceTrackId("video-1", "track-1", "en");
        TranslationProfile profile = new TranslationProfile("audit-profile",
                protocol == ProviderProtocol.ANTHROPIC_MESSAGES ? "anthropic-messages"
                        : "openai-chat-completions",
                baseUrl, "audit-model", "prompt-1", 1, "zh");
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(track, 0)), "Hello.");

        return new TranslationRequest(new TranslationSessionId("video-1", track, profile,
                TranslationSessionId.ENGINE_SCHEMA_VERSION), 1, unit,
                "Translate the subtitle into Chinese.");
    }

    private static final class FixedSecrets implements SecretStore {
        private final String mValue;

        FixedSecrets(String value) {
            mValue = value;
        }

        @Override public String get(String reference) { return mValue; }
        @Override public void put(String reference, String secret) { }
        @Override public void delete(String reference) { }
    }

    private static final class MutableSecrets implements SecretStore {
        private String value;

        MutableSecrets(String value) {
            this.value = value;
        }

        @Override public String get(String reference) { return value; }
        @Override public void put(String reference, String secret) { value = secret; }
        @Override public void delete(String reference) { value = null; }
    }

    private static final class RecordingCallback implements TranslationCallback {
        private TranslationResult result;
        private TranslationFailure failure;

        @Override public void onSuccess(TranslationResult result) { this.result = result; }
        @Override public void onFailure(TranslationFailure failure) { this.failure = failure; }
    }

    /** Fake transport that answers both protocols and collects every diagnostic it can see. */
    private static final class RespondingExecutor implements HttpRequestExecutor {
        private final ProviderProtocol mProtocol;
        private HttpRequest mLastRequest;
        private String mBody;
        private int mStatus = 200;

        RespondingExecutor(ProviderProtocol protocol) {
            mProtocol = protocol;
        }

        RespondingExecutor() {
            this(ProviderProtocol.OPENAI_CHAT_COMPLETIONS);
        }

        void respond(String body, int status) {
            mBody = body;
            mStatus = status;
        }

        HttpRequest getLastRequest() {
            return mLastRequest;
        }

        private String defaultBody() {
            return mProtocol == ProviderProtocol.ANTHROPIC_MESSAGES
                    ? "{\"content\":[{\"type\":\"text\",\"text\":\"translated\"}]}"
                    : "{\"choices\":[{\"message\":{\"content\":\"translated\"}}]}";
        }

        List<String> diagnostics(Object adapter, TranslationRequest request, Object call) {
            List<String> diagnostics = new ArrayList<>();
            diagnostics.add(String.valueOf(adapter));
            diagnostics.add(String.valueOf(request));
            diagnostics.add(String.valueOf(call));
            diagnostics.add(String.valueOf(mLastRequest));
            diagnostics.add(String.valueOf(new HttpFailure(FailureReason.NETWORK,
                    "Provider network request failed.", "req-1")));
            diagnostics.add(String.valueOf(new HttpResponse(200, mBody, "req-1")));
            return diagnostics;
        }

        @Override
        public HttpCall execute(HttpRequest request, HttpCallback callback) {
            mLastRequest = request;

            String body = mBody != null ? mBody : defaultBody();

            callback.onSuccess(new HttpResponse(mStatus, body, "req-1"));
            return new NopCall();
        }
    }

    private static final class NopCall implements HttpRequestExecutor.HttpCall {
        private boolean mCancelled;

        @Override public void cancel() { mCancelled = true; }
        @Override public boolean isCancelled() { return mCancelled; }
    }
}
