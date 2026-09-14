package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider;

import com.liskovsoft.sharedutils.helpers.Helpers;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM protocol tests for Anthropic-compatible Messages normal responses.
 */
public class AnthropicMessagesAdapterTest {
    private static final String SECRET = "synthetic-anthropic-credential";
    private static final String RENDERED_PROMPT =
            "Translate the subtitle from ja to zh and return only the translation.";

    @Test
    public void successUsesTopLevelSystemAndMapsTextBlocks() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                successBody("你好"), "req-anthropic");
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingCallback callback = new RecordingCallback();

        TranslationCall call = adapter.translate(request("Hello"), callback);

        assertTrue(callback.success);
        assertEquals("你好", callback.result.getTranslatedText());
        assertEquals("req-anthropic", ((AnthropicMessagesAdapter.Call) call).getRequestId());
        assertEquals("https://api.anthropic.com/v1/messages", executor.getLastRequest().getUrl());
        assertEquals(SECRET, executor.getLastRequest().getHeaders().get("x-api-key"));
        assertEquals("2023-06-01", executor.getLastRequest().getHeaders().get("anthropic-version"));
        assertEquals(4096, number(parseBody(executor).get("max_tokens")));
        assertTrue(parseBody(executor).containsKey("system"));
        assertFalse(bodyContainsRole(parseBody(executor), "system"));
    }

    @Test
    public void bearerAuthenticationShapeIsSelectedByProfileOption() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(AnthropicMessagesAdapter.OPTION_AUTH_SCHEME,
                AnthropicMessagesAdapter.AUTH_BEARER);
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("ok"), "req-bearer");
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com/v1", null, options),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        Map<String, String> headers = executor.getLastRequest().getHeaders();
        assertEquals("Bearer " + SECRET, headers.get("Authorization"));
        assertFalse(headers.containsKey("x-api-key"));
    }

    @Test
    public void requestBodyEscapesStringsAndPreservesSourceText() {
        String source = "Hello \"quoted\"\n世界\\end";
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("ok"), "req-escape");
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request(source), new RecordingCallback());

        Map<String, Object> body = parseBody(executor);
        List<?> messages = (List<?>) body.get("messages");
        Map<?, ?> user = (Map<?, ?>) messages.get(0);
        assertEquals("user", user.get("role"));
        assertEquals(source, user.get("content"));
    }

    @Test
    public void versionAndMaxTokensCanBeOverriddenPerProfile() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(AnthropicMessagesAdapter.OPTION_VERSION, "2025-01-01");
        options.put(AnthropicMessagesAdapter.OPTION_MAX_TOKENS, "2048");
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("ok"), "req-options");
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, options),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        assertEquals("2025-01-01",
                executor.getLastRequest().getHeaders().get("anthropic-version"));
        assertEquals(2048, number(parseBody(executor).get("max_tokens")));
    }

    @Test
    public void missingCredentialFailsAsAuthWithoutHttpRequest() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("unused"), "req-none");
        RecordingCallback callback = new RecordingCallback();

        adapter(executor, profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(null)).translate(request("Hello"), callback);

        assertFailure(callback, TranslationFailureCategory.AUTH);
        assertEquals(0, executor.getExecuteCount());
    }

    @Test
    public void secretStoreFailureFailsAsAuthWithoutHttpRequest() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("unused"), "req-none");
        RecordingSecretStore secrets = new RecordingSecretStore(SECRET);
        secrets.failure = new SecretStore.Failure(SecretStore.FailureReason.INVALIDATED);
        RecordingCallback callback = new RecordingCallback();

        adapter(executor, profile("https://api.anthropic.com", null, null), secrets)
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
        assertStatusFailure(529, TranslationFailureCategory.SERVER);
        assertStatusFailure(504, TranslationFailureCategory.TIMEOUT);
    }

    @Test
    public void namedErrorBodiesMapWithoutExposingProviderMessage() {
        String body = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"private provider detail\"}}";
        FakeHttpExecutor executor = FakeHttpExecutor.success(body, "req-error");
        executor.responseStatusCode = 400;
        RecordingCallback callback = new RecordingCallback();

        adapter(executor, profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);

        assertFailure(callback, TranslationFailureCategory.AUTH);
        assertFalse(callback.failure.getMessage().contains("private provider detail"));
    }

    @Test
    public void emptyAndMalformedBodiesMapToProtocolFailure() {
        assertBodyFailure("", TranslationFailureCategory.PROTOCOL);
        assertBodyFailure("not-json", TranslationFailureCategory.PROTOCOL);
        assertBodyFailure("{\"content\":\"wrong\"}", TranslationFailureCategory.PROTOCOL);
    }

    @Test
    public void validResponseWithoutTextBlocksMapsToInvalidOutput() {
        assertBodyFailure("{\"content\":[]}", TranslationFailureCategory.INVALID_OUTPUT);
        assertBodyFailure("{\"content\":[{\"type\":\"tool_use\",\"id\":\"tool-1\"}]}",
                TranslationFailureCategory.INVALID_OUTPUT);
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
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingCallback callback = new RecordingCallback();

        TranslationCall call = adapter.translate(request("Hello"), callback);
        call.cancel();
        executor.succeed(successBody("late"), "req-late");

        assertTrue(call.isCancelled());
        assertTrue(executor.getLastCall().isCancelled());
        assertFalse(callback.completed);
    }

    @Test
    public void requestIdIsCapturedFromSuccessAndFailure() {
        FakeHttpExecutor success = FakeHttpExecutor.success(successBody("ok"), "req-ok");
        TranslationCall successCall = adapter(success,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"),
                new RecordingCallback());
        assertEquals("req-ok", ((AnthropicMessagesAdapter.Call) successCall).getRequestId());

        FakeHttpExecutor failure = FakeHttpExecutor.failure(
                new HttpRequestExecutor.HttpFailure(HttpRequestExecutor.FailureReason.NETWORK,
                        "network failed", "req-failure"));
        RecordingCallback callback = new RecordingCallback();
        TranslationCall failureCall = adapter(failure,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);
        assertFailure(callback, TranslationFailureCategory.NETWORK);
        assertEquals("req-failure", ((AnthropicMessagesAdapter.Call) failureCall).getRequestId());
    }

    @Test
    public void diagnosticsNeverContainCredentialOrSubtitleText() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("ok"), "req-redaction");
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        adapter.translate(request("private subtitle text"), new RecordingCallback());

        assertFalse(adapter.toString().contains(SECRET));
        assertFalse(executor.getLastRequest().toString().contains(SECRET));
        assertFalse(executor.getLastRequest().toString().contains("private subtitle text"));
    }

    private static AnthropicMessagesAdapter adapter(FakeHttpExecutor executor,
                                                     ProviderProfile profile,
                                                     SecretStore secrets) {
        return new AnthropicMessagesAdapter(executor, profile, secrets);
    }

    private static ProviderProfile profile(String baseUrl, Map<String, String> headers,
                                           Map<String, String> options) {
        return new ProviderProfile("profile-anthropic", "Anthropic",
                ProviderType.ANTHROPIC_COMPATIBLE, ProviderProtocol.ANTHROPIC_MESSAGES,
                baseUrl, "credential-ref-anthropic", "claude-test",
                Collections.<String>emptyList(), headers, options);
    }

    private static TranslationRequest request(String sourceText) {
        SourceTrackId track = new SourceTrackId("video-1", "track-1", "ja");
        TranslationProfile profile = new TranslationProfile("profile-anthropic",
                "anthropic-messages", "https://api.anthropic.com", "claude-test",
                "prompt-1", 1, "zh");
        TranslationSessionId session = new TranslationSessionId(
                "video-1", track, profile, TranslationSessionId.ENGINE_SCHEMA_VERSION);
        return request(sourceText, RENDERED_PROMPT);
    }

    private static TranslationRequest request(String sourceText, String renderedPrompt) {
        SourceTrackId track = new SourceTrackId("video-1", "track-1", "ja");
        TranslationProfile profile = new TranslationProfile("profile-anthropic",
                "anthropic-messages", "https://api.anthropic.com", "claude-test",
                "prompt-1", 1, "zh");
        TranslationSessionId session = new TranslationSessionId(
                "video-1", track, profile, TranslationSessionId.ENGINE_SCHEMA_VERSION);
        TranslationUnit unit = new TranslationUnit(
                Collections.singletonList(new SubtitleSegmentId(track, 0)), sourceText);
        return new TranslationRequest(session, 43, unit, renderedPrompt);
    }

    @Test
    public void renderedPromptBecomesTheTopLevelSystemField() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                successBody("ok"), "req-prompt");
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        String prompt = "PROMPT-A translate faithfully";

        adapter.translate(request("Subtitle line.", prompt), new RecordingCallback());

        Map<String, Object> body = parseBody(executor);
        assertEquals(prompt, body.get("system"));
        assertEquals(1, ((List<?>) body.get("messages")).size());
    }

    @Test
    public void differentPromptsProduceDifferentRequestBodies() {
        Map<String, Object> first = bodyFor("PROMPT-A");
        Map<String, Object> second = bodyFor("PROMPT-B");

        assertFalse("a different Prompt Profile must change the actual request",
                first.equals(second));
    }

    private static Map<String, Object> bodyFor(String prompt) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("ok"), "req-prompt");
        adapter(executor, profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET))
                .translate(request("Hello", prompt), new RecordingCallback());
        return parseBody(executor);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBody(FakeHttpExecutor executor) {
        return Helpers.convertToObj(executor.getLastRequest().getBody());
    }

    private static boolean bodyContainsRole(Map<String, Object> body, String role) {
        Object messages = body.get("messages");
        if (!(messages instanceof List)) {
            return false;
        }
        for (Object item : (List<?>) messages) {
            if (item instanceof Map && role.equals(((Map<?, ?>) item).get("role"))) {
                return true;
            }
        }
        return false;
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static String successBody(String text) {
        return "{\"id\":\"msg_1\",\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]}";
    }

    @Test
    public void namedTextDeltasBecomeCumulativeDraftsAndMessageStopCompletes() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);

        assertTrue("a streaming callback must ask for an event stream",
                executor.getLastRequest().getHeaders().get("Accept").contains("text/event-stream"));
        assertTrue(executor.getLastRequest().getBody().contains("\"stream\":true"));

        executor.emit("message_start", "{\"type\":\"message_start\"}");
        executor.emit("ping", "{\"type\":\"ping\"}");
        executor.emit("content_block_start", "{\"type\":\"content_block_start\",\"index\":0}");
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"hmm\"}}");
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"你\"}}");
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"好\"}}");
        executor.emit("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}");
        executor.emit("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}");
        executor.emit("message_stop", "{\"type\":\"message_stop\"}");
        executor.finishStream();

        assertEquals("thinking and unknown events must not become subtitles",
                Arrays.asList("你", "你好"), callback.partials);
        assertTrue(callback.result != null && callback.result.isFinal());
        assertEquals("你好", callback.result.getTranslatedText());
    }

    @Test
    public void eventsAfterMessageStopAreIgnored() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"\u4f60\u597d\"}}");
        executor.emit("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}");
        executor.emit("message_stop", "{\"type\":\"message_stop\"}");
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"TRAILING\"}}");
        executor.finishStream();

        assertEquals("\u4f60\u597d", callback.result.getTranslatedText());
    }

    @Test
    public void aMaxTokensStopIsNotASilentSuccess() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"trunc\"}}");
        executor.emit("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}");
        executor.emit("message_stop", "{\"type\":\"message_stop\"}");
        executor.finishStream();

        assertEquals(TranslationFailureCategory.INVALID_OUTPUT, callback.failure.getCategory());
        assertTrue(callback.result == null);
    }

    @Test
    public void anInterruptedStreamFailsRetryablySoAPlainRequestCanFollow() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"half\"}}");
        executor.finishStream();

        assertEquals("a cut stream must stay retryable, or no fallback can ever happen",
                TranslationFailureCategory.NETWORK, callback.failure.getCategory());
        assertTrue(callback.result == null);
    }

    @Test
    public void messageStopDeliversOnceAndClosesTheHttpRead() {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}");
        executor.emit("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}");
        executor.emit("message_stop", "{\"type\":\"message_stop\"}");

        // The answer is complete before the socket closes: the read must already be closed and
        // the outcome delivered exactly once.
        assertEquals("你好", callback.result.getTranslatedText());
        assertTrue(executor.getLastCall().isClosed());
        int deliveries = callback.terminals;
        java.util.List<String> drafts = new java.util.ArrayList<>(callback.partials);

        executor.emit("content_block_delta",
                "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"LATE\"}}");
        executor.finishStream();
        executor.failStream(new HttpRequestExecutor.HttpFailure(
                HttpRequestExecutor.FailureReason.NETWORK, "late", "req-late"));

        assertEquals("a terminal outcome is delivered exactly once", deliveries,
                callback.terminals);
        assertEquals("no draft may be published after the outcome", drafts, callback.partials);
        assertEquals("你好", callback.result.getTranslatedText());
    }

    @Test
    public void streamingStatusesAreClassifiedBeforeTheStreamIsInterpreted() {
        assertStreamingStatusFailure(401, TranslationFailureCategory.AUTH);
        assertStreamingStatusFailure(403, TranslationFailureCategory.AUTH);
        assertStreamingStatusFailure(408, TranslationFailureCategory.TIMEOUT);
        assertStreamingStatusFailure(429, TranslationFailureCategory.RATE_LIMITED);
        assertStreamingStatusFailure(500, TranslationFailureCategory.SERVER);
        assertStreamingStatusFailure(504, TranslationFailureCategory.TIMEOUT);
        assertStreamingStatusFailure(400, TranslationFailureCategory.PROTOCOL);
    }

    /** The audit's counterexample for C6: an overloaded stream must stay retryable. */
    @Test
    public void anOverloadedStreamingErrorStaysRetryable() {
        RecordingStreamCallback callback = streamError("overloaded_error");
        assertEquals(TranslationFailureCategory.SERVER, callback.failure.getCategory());
    }

    @Test
    public void streamingErrorsReuseTheNamedCategories() {
        assertEquals(TranslationFailureCategory.RATE_LIMITED,
                streamError("rate_limit_error").failure.getCategory());
        assertEquals(TranslationFailureCategory.AUTH,
                streamError("authentication_error").failure.getCategory());
        assertEquals(TranslationFailureCategory.SERVER,
                streamError("api_error").failure.getCategory());
        // Nothing known maps it, so it stays conservative rather than becoming retryable.
        assertEquals(TranslationFailureCategory.PROTOCOL,
                streamError("something_new").failure.getCategory());
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
        int bound = AnthropicMessagesAdapter.MAX_STREAM_CODE_POINTS;
        String pair = "😀";

        RecordingStreamCallback atBound = streamText(ascii(bound - 1) + pair);
        assertNotNull("a stream that ends exactly on the bound must complete", atBound.result);
        assertNull(atBound.failure);

        RecordingStreamCallback pastBound = streamText(ascii(bound) + pair);
        assertNull("a stream past the bound must not succeed", pastBound.result);
        assertEquals(TranslationFailureCategory.INVALID_OUTPUT, pastBound.failure.getCategory());

        // The same text with the surrogate pair split across two deltas: still the bound, because
        // the pair is one code point and not two.
        RecordingStreamCallback splitPair = streamText(ascii(bound - 1), "\\uD83D", "\\uDE00");
        assertNotNull("a split surrogate pair must still count as one code point", splitPair.result);
    }

    @Test
    public void aSynchronouslyDeliveredOutcomeClosesTheHandleWhenItArrives() {
        FakeHttpExecutor executor = FakeHttpExecutor.success(successBody("ok"), "req-sync");
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));

        adapter.translate(request("Hello"), new RecordingCallback());

        assertTrue("a handle that arrives after the outcome must be closed at once",
                executor.getLastCall().isClosed());
    }

    private static RecordingStreamCallback streamError(String errorType) {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);
        executor.emit("error", "{\"type\":\"error\",\"error\":{\"type\":\"" + errorType + "\"}}");

        return callback;
    }

    /** Drives one streaming request through the given text deltas, then message_stop. */
    private static RecordingStreamCallback streamText(String... deltas) {
        FakeHttpExecutor executor = FakeHttpExecutor.deferred();
        AnthropicMessagesAdapter adapter = adapter(executor,
                profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET));
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter.translate(request("Hello"), callback);

        for (String delta : deltas) {
            executor.emit("content_block_delta", "{\"type\":\"content_block_delta\","
                    + "\"delta\":{\"type\":\"text_delta\",\"text\":\"" + delta + "\"}}");
        }
        executor.emit("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}");
        executor.emit("message_stop", "{\"type\":\"message_stop\"}");

        return callback;
    }

    private static void assertStreamingStatusFailure(int status,
                                                     TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"error\":{\"message\":\"private provider detail\"}}", "req-stream-" + status);
        executor.responseStatusCode = status;
        RecordingStreamCallback callback = new RecordingStreamCallback();

        adapter(executor, profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);

        assertEquals(expected, callback.failure.getCategory());
        assertFalse(callback.failure.getMessage().contains("private provider detail"));
    }

    private static String ascii(int count) {
        StringBuilder text = new StringBuilder(count);
        for (int i = 0; i < count; i++) text.append('x');
        return text.toString();
    }

    private static void assertStatusFailure(int status,
                                            TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(
                "{\"error\":{\"message\":\"private provider detail\"}}", "req-" + status);
        executor.responseStatusCode = status;
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);

        assertFailure(callback, expected);
        assertFalse(callback.failure.getMessage().contains("private provider detail"));
    }

    private static void assertBodyFailure(String body, TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.success(body, "req-body");
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.anthropic.com", null, null),
                new RecordingSecretStore(SECRET)).translate(request("Hello"), callback);
        assertFailure(callback, expected);
    }

    private static void assertTransportFailure(HttpRequestExecutor.FailureReason reason,
                                               TranslationFailureCategory expected) {
        FakeHttpExecutor executor = FakeHttpExecutor.failure(
                new HttpRequestExecutor.HttpFailure(reason, "synthetic transport failure", "req-x"));
        RecordingCallback callback = new RecordingCallback();
        adapter(executor, profile("https://api.anthropic.com", null, null),
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
