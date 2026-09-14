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
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationRequest;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationStream;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM protocol tests for OpenAI-compatible Chat Completions normal responses.
 * Every HTTP interaction goes through a fake executor; no public network is touched.
 */
public class OpenAiChatCompletionsAdapterTest {
    private static final String SECRET = "synthetic-provider-key";
    private static final String RENDERED_PROMPT =
            "Translate the subtitle from ja to zh and return only the translation.";

    @Test
    public void successBuildsExpectedRequestAndMapsTranslationResult() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}", "req-success");
        RecordingSecretStore secrets = new RecordingSecretStore(SECRET);
        OpenAiChatCompletionsAdapter adapter = adapter(executor, profile(
                "https://api.example.com", null, null), secrets);
        RecordingCallback callback = new RecordingCallback();

        TranslationCall call = adapter.translate(request("Hello"), callback);

        assertTrue(callback.success);
        assertEquals("你好", callback.result.getTranslatedText());
        assertEquals("req-success", ((OpenAiChatCompletionsAdapter.Call) call).getRequestId());
        assertEquals(1, executor.getExecuteCount());
        HttpRequestExecutor.HttpRequest request = executor.getLastRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("https://api.example.com/v1/chat/completions", request.getUrl());
        assertEquals("Bearer " + SECRET, request.getHeaders().get("Authorization"));
        assertEquals("application/json", request.getHeaders().get("Content-Type"));
        assertTrue(request.getBody().contains("\"model\":\"model-1\""));
        assertTrue(request.getBody().contains("\"stream\":false"));
        assertTrue(request.getBody().contains("Translate"));
        assertTrue(request.getBody().contains("zh"));
        assertTrue(request.getBody().contains("Hello"));
    }

    @Test
    public void baseUrlNormalizationAvoidsDuplicateVersionAndChatPaths() {
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com"));
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com/v1/"));
        assertEquals("https://api.example.com/api/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com/api/v1"));
        assertEquals("https://api.example.com/v1beta/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl("https://api.example.com/v1beta"));
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatCompletionsAdapter.chatCompletionsUrl(
                        "https://api.example.com/v1/chat/completions/"));
    }

    @Test
    public void requestBodyEscapesQuotesBackslashesAndNewlines() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-escape");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        String source = "Hello \"quoted\"\n世界\\end";

        adapter.translate(request(source), new RecordingCallback());

        String body = executor.getLastRequest().getBody();
        assertTrue(body.contains("Hello \\\"quoted\\\"\\n世界\\\\end"));
    }

    @Test
    public void customHeadersAreSentAndAuthorizationIsNeverDuplicated() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Attribution", "smartube");
        headers.put("authorization", "Bearer wrong-value");
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-headers");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", headers, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        Map<String, String> sent = executor.getLastRequest().getHeaders();
        assertEquals("smartube", sent.get("X-Attribution"));
        int authCount = 0;
        for (Map.Entry<String, String> entry : sent.entrySet()) {
            if ("authorization".equalsIgnoreCase(entry.getKey())) {
                authCount++;
                assertEquals("Bearer " + SECRET, entry.getValue());
            }
        }
        assertEquals(1, authCount);
    }

    @Test
    public void missingCredentialFailsAsAuthWithoutHttpRequest() {
        FakeHttpExecutor executor = FakeHttpExecutor.success("{}", "req-none");
        RecordingSecretStore secrets = new RecordingSecretStore(null);
        RecordingCallback callback = new RecordingCallback();

        adapter(executor, profile("https://api.example.com/v1", null, null), secrets)
                .translate(request("Hello"), callback);

        assertFailure(callback, TranslationFailureCategory.AUTH);
        assertEquals(0, executor.getExecuteCount());
    }

    @Test
    public void secretStoreFailureFailsAsAuthWithoutHttpRequest() {
        FakeHttpExecutor executor = FakeHttpExecutor.success("{}", "req-none");
        RecordingSecretStore secrets = new RecordingSecretStore(SECRET);
        secrets.failure = new SecretStore.Failure(SecretStore.FailureReason.INVALIDATED);
        RecordingCallback callback = new RecordingCallback();

        adapter(executor, profile("https://api.example.com/v1", null, null), secrets)
                .translate(request("Hello"), callback);

        assertFailure(callback, TranslationFailureCategory.AUTH);
        assertEquals(0, executor.getExecuteCount());
    }

    @Test
    public void httpStatusesMapToNormalizedFailureCategories() {
        assertStatusFailure(400, TranslationFailureCategory.PROTOCOL);
        assertStatusFailure(401, TranslationFailureCategory.AUTH);
        assertStatusFailure(403, TranslationFailureCategory.AUTH);
        assertStatusFailure(408, TranslationFailureCategory.TIMEOUT);
        assertStatusFailure(429, TranslationFailureCategory.RATE_LIMITED);
        assertStatusFailure(500, TranslationFailureCategory.SERVER);
        assertStatusFailure(503, TranslationFailureCategory.SERVER);
        assertStatusFailure(504, TranslationFailureCategory.TIMEOUT);
    }

    @Test
    public void emptyAndMalformedBodiesMapToProtocolFailure() {
        assertBodyFailure("", "req-empty", TranslationFailureCategory.PROTOCOL);
        assertBodyFailure("not-json", "req-malformed", TranslationFailureCategory.PROTOCOL);
        assertBodyFailure("{\"choices\":\"wrong\"}", "req-shape", TranslationFailureCategory.PROTOCOL);
    }

    @Test
    public void validResponseWithoutUsableContentMapsToInvalidOutput() {
        assertBodyFailure("{\"choices\":[]}", "req-no-choice",
                TranslationFailureCategory.INVALID_OUTPUT);
        assertBodyFailure("{\"choices\":[{\"message\":{\"content\":\"  \"}}]}",
                "req-blank", TranslationFailureCategory.INVALID_OUTPUT);
    }

    @Test
    public void transportFailuresMapToTimeoutAndNetwork() {
        assertTransportFailure(HttpRequestExecutor.FailureReason.TIMEOUT,
                TranslationFailureCategory.TIMEOUT);
        assertTransportFailure(HttpRequestExecutor.FailureReason.NETWORK,
                TranslationFailureCategory.NETWORK);
    }

    @Test
    public void cancellationSuppressesLateResultAndCancelsTransport() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingCallback callback = new RecordingCallback();

        TranslationCall call = adapter.translate(request("Hello"), callback);
        call.cancel();
        executor.succeed("{\"choices\":[{\"message\":{\"content\":\"late\"}}]}", "req-late");

        assertTrue(call.isCancelled());
        assertTrue(executor.getLastCall().isCancelled());
        assertFalse(callback.completed);
    }

    @Test
    public void timeoutOptionIsPropagatedToTheHttpExecutor() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(OpenAiChatCompletionsAdapter.OPTION_TIMEOUT_MS, "1234");
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-timeout");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, options),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        assertEquals(1234, executor.getLastRequest().getTimeoutMs());
    }

    @Test
    public void requestIdIsCapturedFromSuccessAndFailure() {
        FakeHttpExecutor success = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-ok");
        OpenAiChatCompletionsAdapter successAdapter = adapter(success,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        TranslationCall successCall = successAdapter.translate(request("Hello"),
                new RecordingCallback());
        assertEquals("req-ok", ((OpenAiChatCompletionsAdapter.Call) successCall).getRequestId());

        FakeHttpExecutor failure = FakeHttpExecutor.failure(
                new HttpRequestExecutor.HttpFailure(HttpRequestExecutor.FailureReason.NETWORK,
                        "network failed", "req-failure"));
        OpenAiChatCompletionsAdapter failureAdapter = adapter(failure,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingCallback callback = new RecordingCallback();
        TranslationCall failureCall = failureAdapter.translate(request("Hello"), callback);
        assertFailure(callback, TranslationFailureCategory.NETWORK);
        assertEquals("req-failure", ((OpenAiChatCompletionsAdapter.Call) failureCall).getRequestId());
    }

    @Test
    public void diagnosticsNeverContainTheAuthorizationValue() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-redaction");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        adapter.translate(request("Hello"), new RecordingCallback());

        assertFalse(adapter.toString().contains(SECRET));
        assertFalse(executor.getLastRequest().toString().contains(SECRET));
        assertFalse(executor.getLastRequest().toString().contains("Hello"));
    }

    private static OpenAiChatCompletionsAdapter adapter(FakeHttpExecutor executor,
                                                         ProviderProfile profile,
                                                         SecretStore secrets) {
        return new OpenAiChatCompletionsAdapter(executor, profile, secrets);
    }

    private static ProviderProfile profile(String baseUrl, Map<String, String> headers,
                                           Map<String, String> options) {
        return new ProviderProfile("profile-1", "Test Provider",
                ProviderType.OPENAI_COMPATIBLE, ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl, "credential-ref-1", "model-1", Collections.<String>emptyList(),
                headers, options);
    }

    private static TranslationRequest request(String sourceText) {
        SourceTrackId track = new SourceTrackId("video-1", "track-1", "ja");
        TranslationProfile profile = new TranslationProfile("profile-1",
                "openai-chat-completions", "https://api.example.com/v1",
                "model-1", "prompt-1", 1, "zh");
        TranslationSessionId session = new TranslationSessionId(
                "video-1", track, profile, TranslationSessionId.ENGINE_SCHEMA_VERSION);
        return request(sourceText, RENDERED_PROMPT);
    }

    private static TranslationRequest request(String sourceText, String renderedPrompt) {
        SourceTrackId track = new SourceTrackId("video-1", "track-1", "ja");
        TranslationProfile profile = new TranslationProfile("profile-1",
                "openai-chat-completions", "https://api.example.com/v1",
                "model-1", "prompt-1", 1, "zh");
        TranslationSessionId session = new TranslationSessionId(
                "video-1", track, profile, TranslationSessionId.ENGINE_SCHEMA_VERSION);
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(track, 0)), sourceText);
        return new TranslationRequest(session, 42, unit, renderedPrompt);
    }

    @Test
    public void renderedPromptBecomesTheSystemMessageAndSourceTextStaysTheUserMessage() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-prompt");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        String prompt = "PROMPT-A translate faithfully";
        String source = "Subtitle line.";

        adapter.translate(request(source, prompt), new RecordingCallback());

        String body = executor.getLastRequest().getBody();
        assertTrue("the selected prompt must be the system message",
                body.contains("{\"role\":\"system\",\"content\":\"" + prompt + "\"}"));
        assertTrue("the unit text must stay the user message",
                body.contains("{\"role\":\"user\",\"content\":\"" + source + "\"}"));
    }

    @Test
    public void differentPromptsProduceDifferentRequestBodies() {
        FakeHttpExecutor first = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-a");
        FakeHttpExecutor second = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-b");
        OpenAiChatCompletionsAdapter adapter = adapter(first,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        OpenAiChatCompletionsAdapter other = adapter(second,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello", "PROMPT-A"), new RecordingCallback());
        other.translate(request("Hello", "PROMPT-B"), new RecordingCallback());

        assertFalse("a different Prompt Profile must change the actual request",
                first.getLastRequest().getBody().equals(second.getLastRequest().getBody()));
    }

    @Test
    public void renderedPromptIsJsonEscaped() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-prompt-escape");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello", "规则：\"输出\"\n第一行\\次行"),
                new RecordingCallback());

        assertTrue(executor.getLastRequest().getBody()
                .contains("规则：\\\"输出\\\"\\n第一行\\\\次行"));
    }

    @Test
    public void streamedDeltasBecomeCumulativeDraftsAndStopCompletesTheTranslation() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);

        assertTrue("a streaming callback must ask for an event stream",
                executor.getLastRequest().getHeaders().get("Accept").contains("text/event-stream"));
        assertTrue(executor.getLastRequest().getBody().contains("\"stream\":true"));

        // A role-only chunk and a usage-only chunk carry no subtitle text.
        executor.emit("message", "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}");
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"你\"}}]}");
        executor.emit("message", "{\"choices\":[],\"usage\":{\"total_tokens\":3}}");
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"好\"}}]}");
        executor.emit("message", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        executor.emit("message", "[DONE]");
        executor.finishStream();

        assertEquals("every draft must be the full text so far",
                Arrays.asList("你", "你好"), callback.partials);
        assertTrue("a stopped stream with a completion signal is a success",
                callback.result != null && callback.result.isFinal());
        assertEquals("你好", callback.result.getTranslatedText());
    }

    @Test
    public void malformedChunksExtraChoicesAndEventsAfterTheEndSignalAreIgnored() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);

        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"\u4f60\"}}]}");
        executor.emit("message", "not json at all");
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"\u597d\"}},"
                + "{\"delta\":{\"content\":\"IGNORED\"}}]}");
        executor.emit("message", "[DONE]");
        // A server repeating events after the end signal must not change the result.
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"TRAILING\"}}]}");
        executor.emit("message", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        executor.finishStream();

        assertEquals("only the first choice of each chunk is the subtitle",
                Arrays.asList("\u4f60", "\u4f60\u597d"), callback.partials);
        assertEquals("\u4f60\u597d", callback.result.getTranslatedText());
    }

    @Test
    public void aLengthStopIsNotASilentSuccess() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"trunc\"}}]}");
        executor.emit("message", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}");
        executor.emit("message", "[DONE]");
        executor.finishStream();

        assertEquals(TranslationFailureCategory.INVALID_OUTPUT, callback.failure.getCategory());
        assertTrue(callback.result == null);
    }

    @Test
    public void anInterruptedStreamFailsRetryablySoAPlainRequestCanFollow() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"half\"}}]}");
        executor.finishStream();

        assertEquals("a cut stream must stay retryable, or no fallback can ever happen",
                TranslationFailureCategory.NETWORK, callback.failure.getCategory());
        assertTrue(callback.result == null);
    }

    /** The audit's counterexample for C6, on the streaming path. */
    @Test
    public void aStreamingRateLimitKeepsItsRetryableCategory() {
        FakeHttpExecutor executor = FakeHttpExecutor.success("{\"error\":{}}", "rate");
        executor.responseStatusCode = 429;
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);

        assertEquals(TranslationFailureCategory.RATE_LIMITED, callback.failure.getCategory());
    }

    @Test
    public void streamingStatusesAreClassifiedBeforeTheStreamIsInterpreted() {
        assertStreamingStatusFailure(401, TranslationFailureCategory.AUTH);
        assertStreamingStatusFailure(403, TranslationFailureCategory.AUTH);
        assertStreamingStatusFailure(408, TranslationFailureCategory.TIMEOUT);
        assertStreamingStatusFailure(429, TranslationFailureCategory.RATE_LIMITED);
        assertStreamingStatusFailure(500, TranslationFailureCategory.SERVER);
        assertStreamingStatusFailure(503, TranslationFailureCategory.SERVER);
        assertStreamingStatusFailure(504, TranslationFailureCategory.TIMEOUT);
        assertStreamingStatusFailure(400, TranslationFailureCategory.PROTOCOL);
    }

    /** The audit's counterexample for C7: only an accepted completion may succeed. */
    @Test
    public void anUnknownFinishReasonIsNotSuccessful() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"half\"},"
                + "\"finish_reason\":\"tool_calls\"}]}");
        executor.finishStream();

        assertTrue("only an accepted completion may succeed", callback.result == null);
        assertEquals(TranslationFailureCategory.PROTOCOL, callback.failure.getCategory());
    }

    @Test
    public void aToolCallFinishReasonIsNeverSubtitleText() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}");
        executor.emit("message", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");
        // A server that keeps the connection open after its refusal must change nothing.
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"MORE\"}}]}");

        assertTrue(callback.result == null);
        assertEquals(TranslationFailureCategory.PROTOCOL, callback.failure.getCategory());
        assertEquals("no further draft may be published after a refusal",
                Collections.singletonList("partial"), callback.partials);
    }

    @Test
    public void theProtocolEndDeliversOnceAndClosesTheHttpRead() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}");
        executor.emit("message", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");

        // The answer is complete before the socket closes: the read must already be closed and
        // the outcome delivered exactly once.
        assertEquals("你好", callback.result.getTranslatedText());
        assertTrue(executor.getLastCall().isClosed());
        int deliveries = callback.terminals;
        java.util.List<String> drafts = new java.util.ArrayList<>(callback.partials);

        executor.emit("message", "{\"choices\":[{\"delta\":{\"content\":\"LATE\"}}]}");
        executor.finishStream();
        executor.failStream(new HttpRequestExecutor.HttpFailure(
                HttpRequestExecutor.FailureReason.NETWORK, "late", "req-late"));

        assertEquals("a terminal outcome is delivered exactly once", deliveries,
                callback.terminals);
        assertEquals("no draft may be published after the outcome", drafts, callback.partials);
        assertEquals("你好", callback.result.getTranslatedText());
    }

    /**
     * The draft bound is a code-point budget at the exact boundary, counted so a surrogate pair
     * is one code point and a pair split across two deltas is still one.
     *
     * <p>The plan also requires the bound to be checked <em>before</em> the text enters the
     * buffer. That ordering is implemented but is not separable by assertion from the public
     * surface: both orders reject the same input with the same failure, and the buffer is never
     * read again once the call is terminal. The name says what this test can actually observe.</p>
     */
    @Test
    public void aStreamIsAcceptedUpToTheDraftBoundAndRejectedPastIt() {
        int bound = OpenAiChatCompletionsAdapter.MAX_STREAM_CODE_POINTS;
        String pair = "😀";

        // Exactly the bound, in one delta, ending on an astral character.
        RecordingStreamCallback atBound = stream(ascii(bound - 1) + pair);
        assertNotNull("a stream that ends exactly on the bound must complete", atBound.result);
        assertNull(atBound.failure);

        // One code point past it.
        RecordingStreamCallback pastBound = stream(ascii(bound) + pair);
        assertNull("a stream past the bound must not succeed", pastBound.result);
        assertEquals(TranslationFailureCategory.INVALID_OUTPUT, pastBound.failure.getCategory());

        // The same text with the surrogate pair split across two deltas: still the bound, because
        // the pair is one code point and not two. The escapes keep the halves as lone surrogates
        // on the wire, which is what a server splitting a pair mid-token looks like.
        RecordingStreamCallback splitPair = stream(ascii(bound - 1), "\\uD83D", "\\uDE00");
        assertNotNull("a split surrogate pair must still count as one code point", splitPair.result);
    }

    @Test
    public void aSynchronouslyDeliveredOutcomeClosesTheHandleWhenItArrives() {
        // The fake delivers inside execute(), so the outcome exists before the handle is returned.
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-sync");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        assertTrue("a handle that arrives after the outcome must be closed at once",
                executor.getLastCall().isClosed());
    }

    private static void assertStreamingStatusFailure(int status,
                                                     TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"error\":{\"message\":\"private provider detail\"}}", "req-stream-" + status);
        executor.responseStatusCode = status;
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);

        assertEquals(expected, callback.failure.getCategory());
        assertFalse(callback.failure.getMessage().contains("private provider detail"));
    }

    /** Drives one streaming request through the given text deltas, then a natural stop. */
    private static RecordingStreamCallback stream(String... deltas) {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);

        for (String delta : deltas) {
            executor.emit("message",
                    "{\"choices\":[{\"delta\":{\"content\":\"" + delta + "\"}}]}");
        }
        executor.emit("message", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");

        return callback;
    }

    private static String ascii(int count) {
        StringBuilder text = new StringBuilder(count);
        for (int i = 0; i < count; i++) text.append('x');
        return text.toString();
    }

    @Test
    public void aPlainCallbackKeepsTheSingleResponseContract() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "req-plain");
        OpenAiChatCompletionsAdapter adapter = adapter(executor,
                profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        assertEquals("application/json", executor.getLastRequest().getHeaders().get("Accept"));
        assertTrue(executor.getLastRequest().getBody().contains("\"stream\":false"));
    }

    private static void assertStatusFailure(int status,
                                            TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"error\":{\"message\":\"private provider detail\"}}", "req-" + status);
        executor.responseStatusCode = status;
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);

        assertFailure(callback, expected);
        assertFalse(callback.failure.getMessage().contains("private provider detail"));
    }

    private static void assertBodyFailure(String body, String requestId,
                                          TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(body, requestId);
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);
        assertFailure(callback, expected);
    }

    private static void assertTransportFailure(HttpRequestExecutor.FailureReason reason,
                                               TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.failure(
                new HttpRequestExecutor.HttpFailure(reason, "synthetic transport failure", "req-x"));
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.example.com/v1", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);
        assertFailure(callback, expected);
    }

    private static void assertFailure(RecordingCallback callback,
                                      TranslationFailureCategory expected) {
        assertFalse(callback.success);
        assertNotNull(callback.failure);
        assertEquals(expected, callback.failure.getCategory());
    }

    private static final class RecordingSecretStore implements SecretStore {
        private final String value;
        private SecretStore.Failure failure;

        RecordingSecretStore(String value) {
            this.value = value;
        }

        @Override
        public String get(String reference) {
            if (failure != null) {
                throw failure;
            }
            return value;
        }

        @Override
        public void put(String reference, String secret) {
        }

        @Override
        public void delete(String reference) {
        }
    }

    /** Records cumulative drafts and the single terminal outcome. */
    private static final class RecordingStreamCallback implements TranslationStream {
        private final java.util.List<String> partials = new java.util.ArrayList<>();
        private TranslationResult result;
        private TranslationFailure failure;
        private int terminals;

        @Override
        public void onPartial(TranslationResult partial) {
            partials.add(partial.getTranslatedText());
        }

        @Override
        public void onSuccess(TranslationResult result) {
            terminals++;
            this.result = result;
        }

        @Override
        public void onFailure(TranslationFailure failure) {
            terminals++;
            this.failure = failure;
        }
    }

    private static final class RecordingCallback implements TranslationCallback {
        private boolean success;
        private boolean completed;
        private TranslationResult result;
        private TranslationFailure failure;

        @Override
        public void onSuccess(TranslationResult result) {
            completed = true;
            success = true;
            this.result = result;
        }

        @Override
        public void onFailure(TranslationFailure failure) {
            completed = true;
            success = false;
            this.failure = failure;
        }
    }

    private static final class FakeHttpExecutor implements HttpRequestExecutor {
        private HttpRequest lastRequest;
        private HttpCallback lastCallback;
        private FakeHttpCall lastCall;
        private HttpResponse response;
        private HttpFailure failure;
        private boolean deferred;
        private int executeCount;
        private int responseStatusCode;

        static FakeHttpExecutor success(String body, String requestId) {
            FakeHttpExecutor executor = new FakeHttpExecutor();
            executor.response = new HttpResponse(200, body, requestId);
            return executor;
        }

        boolean lastCallbackIsStreaming() {
            return lastCallback instanceof HttpRequestExecutor.StreamCallback;
        }

        void emit(String eventType, String data) {
            ((HttpRequestExecutor.StreamCallback) lastCallback).onEvent(eventType, data);
        }

        void finishStream() {
            lastCallback.onSuccess(new HttpResponse(200, null, "req-stream"));
        }

        void failStream(HttpFailure failure) {
            lastCallback.onFailure(failure);
        }

        static FakeHttpExecutor failure(HttpFailure failure) {
            FakeHttpExecutor executor = new FakeHttpExecutor();
            executor.failure = failure;
            return executor;
        }

        static FakeHttpExecutor deferred() {
            FakeHttpExecutor executor = new FakeHttpExecutor();
            executor.deferred = true;
            return executor;
        }

        @Override
        public HttpCall execute(HttpRequest request, HttpCallback callback) {
            executeCount++;
            lastRequest = request;
            lastCallback = callback;
            lastCall = new FakeHttpCall();
            if (!deferred) {
                deliver();
            }
            return lastCall;
        }

        void succeed(String body, String requestId) {
            response = new HttpResponse(200, body, requestId);
            failure = null;
            deliver();
        }

        void deliver() {
            if (failure != null) {
                lastCallback.onFailure(failure);
            } else if (response != null) {
                response = new HttpResponse(responseStatusCode > 0 ? responseStatusCode : 200,
                        response.getBody(), response.getRequestId());
                lastCallback.onSuccess(response);
            }
        }

        HttpRequest getLastRequest() {
            return lastRequest;
        }

        FakeHttpCall getLastCall() {
            return lastCall;
        }

        int getExecuteCount() {
            return executeCount;
        }
    }

    private static final class FakeHttpCall implements HttpRequestExecutor.HttpCall {
        private boolean cancelled;
        private boolean closed;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }
    }
}
