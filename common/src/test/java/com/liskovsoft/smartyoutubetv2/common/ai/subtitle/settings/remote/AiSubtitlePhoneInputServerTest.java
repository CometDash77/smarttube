package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ModelCatalog;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.HttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui.PromptProfilesPresenter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui.ProviderProfilesPresenter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AndroidSecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ProviderProfileRepository;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.remote.AiSubtitlePhoneInputServer.Draft;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.support.JdkAwareRobolectricRunner;

@RunWith(JdkAwareRobolectricRunner.class)
@Config(sdk = 17)
public class AiSubtitlePhoneInputServerTest {
    private static final String BASE_URL = "https://api.example.com/v1";

    private AiSubtitlePhoneInputServer mServer;
    private String mToken;
    private String mHost;
    private int mPort;
    private ProviderProfile mProfile;
    private FakeHttpExecutor mExecutor;
    private RecordingListener mListener;

    @Before
    public void setUp() {
        AndroidSecretStore legacySecrets = new AndroidSecretStore(
                RuntimeEnvironment.getApplication());
        legacySecrets.delete("profile-1");
        legacySecrets.put("profile-1", "secret-value");
        AiSubtitleData data = AiSubtitleData.instance(RuntimeEnvironment.getApplication());
        data.providerProfiles().delete("profile-1");
        mExecutor = new FakeHttpExecutor();
        SecretStore secrets = data.secrets();
        ProviderProfilesPresenter profiles = new ProviderProfilesPresenter(
                new ProviderProfileRepository(data, secrets),
                secrets,
                new ProviderProfileResolver(mExecutor, secrets),
                new ModelCatalog(mExecutor));
        PromptProfilesPresenter prompts = new PromptProfilesPresenter(data.prompts());
        mProfile = profiles.save(null, "Original", ProviderType.OPENAI_COMPATIBLE,
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS, BASE_URL, "model-original",
                "secret-value", true).getProfile();
        PromptProfile prompt = prompts.create(
                "Original prompt", "Translate subtitles.").getProfile();
        mListener = new RecordingListener();
        mServer = AiSubtitlePhoneInputServer.startForTesting(
                RuntimeEnvironment.getApplication(), mListener, mProfile, prompt,
                profiles, prompts);
        assertTrue(mServer != null);
        URL url = parseUrl(mServer.getUrl());
        mHost = url.getHost();
        mPort = url.getPort();
        mToken = url.getQuery().substring(2);
    }

    @After
    public void tearDown() {
        if (mServer != null) {
            mServer.close();
        }
    }

    @Test
    public void saveWithoutTokenIsUnauthorized() throws Exception {
        HttpResponse response = request("POST", "/save", "version=2&save=true", false, false);

        assertEquals(401, response.code);
        assertFalse(response.body.contains("\"secret\""));
    }

    @Test
    public void stateDoesNotReturnSecretOrOverwriteLocalEdit() throws Exception {
        HttpResponse page = request("GET", "/", "", true, true);
        assertTrue(page.body.contains("value=\"Original\""));
        assertTrue(page.body.contains("id=\"name\""));
        assertFalse(page.body.contains("/update"));
        assertFalse(page.body.contains("setInterval"));

        HttpResponse state = request("GET", "/state", "", true, false);
        assertFalse(state.body.contains("Original"));
        assertFalse(state.body.toLowerCase().contains("secret-value"));
        assertTrue(state.body.contains("\"connected\":true"));
    }

    @Test
    public void explicitSaveCarriesVersionAndConflictKeepsDraft() throws Exception {
        Draft draft = mServer.getDraft();
        assertEquals(2, draft.version);

        HttpResponse stale = request("POST", "/save",
                saveForm("&name=Stale").replace("version=2", "version=1"), true, false);

        assertEquals(409, stale.code);
        assertEquals("Original", mServer.getDraft().name);

        HttpResponse current = request("POST", "/save",
                saveForm("&name=Edited"), true, false);

        assertEquals(200, current.code);
        assertTrue(current.body.contains("\"saveSucceeded\":true"));
        assertEquals("Edited", mServer.getDraft().name);
        assertEquals(3, mServer.getDraft().version);
    }

    @Test
    public void failedProviderSaveRollsBackPrompt() throws Exception {
        HttpResponse response = request("POST", "/save",
                saveForm("&modelId=&promptName=Edited&prompt=Edited%20prompt"), true, false);

        assertEquals(200, response.code);
        assertTrue(response.body.contains("\"saveFailed\":true"));
        assertFalse(response.body.contains("\"saveSucceeded\":true"));
        PromptProfile prompt = new PromptProfilesPresenter(
                AiSubtitleData.instance(RuntimeEnvironment.getApplication()).prompts())
                .getProfile(mServer.getDraft().promptProfileId);
        assertEquals("Original prompt", prompt.getName());
        assertEquals("Translate subtitles.", prompt.getContent());
    }

    @Test
    public void pageUsesExplicitSaveAndSeparateTerminalStates() throws Exception {
        HttpResponse page = request("GET", "/", "", true, true);

        assertTrue(page.body.contains("id=\"save\""));
        assertTrue(page.body.contains("id=\"test\""));
        assertTrue(page.body.contains("secretAction"));
        assertTrue(page.body.contains("confirm"));
        assertFalse(page.body.contains("'/update'"));
        assertFalse(page.body.contains("setInterval"));
    }

    @Test
    public void outOfRangeSchedulingValuesAreRejectedWithoutChangingTheDraft() throws Exception {
        String before = mServer.getDraft().name;
        int lookaheadBefore = mServer.getDraft().lookaheadSeconds;

        // 4294967296 truncates to 0, which is itself a valid preset: the long must be range
        // checked before narrowing, otherwise the rejection becomes a silent acceptance.
        HttpResponse response = request("POST", "/save",
                saveForm("&lookaheadSeconds=4294967296"), true, false);

        assertEquals(409, response.code);
        assertTrue(response.body.contains("\"invalid\":true"));
        assertEquals("a rejected form must not touch the draft", before, mServer.getDraft().name);
        assertEquals(lookaheadBefore, mServer.getDraft().lookaheadSeconds);
    }

    @Test
    public void invalidSegmentationIsRejectedWithoutChangingTheDraft() throws Exception {
        int targetBefore = mServer.getDraft().segmentTargetChars;

        HttpResponse response = request("POST", "/save",
                saveForm("&segmentTargetChars=300&segmentMaxChars=200"), true, false);

        assertEquals("maxChars below targetChars must be rejected", 409, response.code);
        assertTrue(response.body.contains("\"invalid\":true"));
        assertEquals(targetBefore, mServer.getDraft().segmentTargetChars);
    }

    @Test
    public void anEmptyTargetLanguageStillSavesTheOtherSettings() throws Exception {
        HttpResponse response = request("POST", "/save",
                saveForm("&targetLanguage=&lookaheadSeconds=30"), true, false);

        assertEquals(200, response.code);
        assertTrue(response.body.contains("\"saveSucceeded\":true"));

        AiSubtitleData data = AiSubtitleData.instance(RuntimeEnvironment.getApplication());
        assertEquals("the empty language must fall back to the stored one",
                AiSubtitleData.DEFAULT_TARGET_LANGUAGE, data.getTargetLanguage());
        assertEquals("the remaining settings must still be saved",
                30, data.getLookaheadSeconds());
    }

    @Test
    public void savedSchedulingValuesAreSharedWithTheTvSide() throws Exception {
        HttpResponse response = request("POST", "/save",
                saveForm("&lookaheadSeconds=30&scheduleThrottleSeconds=15"
                        + "&segmentTargetChars=100&segmentMaxChars=320&longSentenceChars=100"
                        + "&bilingualOrder=translation"), true, false);

        assertEquals(200, response.code);
        assertTrue(response.body.contains("\"saveSucceeded\":true"));

        AiSubtitleData data = AiSubtitleData.instance(RuntimeEnvironment.getApplication());
        assertEquals(30, data.getLookaheadSeconds());
        assertEquals(15, data.getScheduleThrottleSeconds());
        assertEquals(100, data.getSegmentTargetChars());
        assertEquals(320, data.getSegmentMaxChars());
        assertEquals(100, data.getLongSentenceChars());
        assertTrue("the bilingual order must round-trip", data.isTranslationFirst());

        HttpResponse reloaded = request("GET", "/", "", true, true);
        assertTrue("the page must render the saved values",
                reloaded.body.contains("id=\"lookaheadSeconds\" type=\"number\" value=\"30\""));
        assertTrue(reloaded.body.contains("value=\"translation\" selected"));
    }

    private static String saveForm(String extra) {
        return "version=2&name=Edited&baseUrl=" + BASE_URL
                + "&modelId=model-original&providerType=OPENAI_COMPATIBLE"
                + "&protocol=OPENAI_CHAT_COMPLETIONS&secretAction=keep&targetLanguage=zh"
                + "&lookaheadSeconds=90&scheduleThrottleSeconds=30"
                + "&segmentTargetChars=60&segmentMaxChars=200&longSentenceChars=80"
                + "&bilingualOrder=source&save=true" + extra;
    }

    private URL parseUrl(String value) {
        Matcher matcher = Pattern.compile("http://([^:/]+):(\\d+)/\\?k=(.+)")
                .matcher(value);
        assertTrue(matcher.matches());
        try {
            return new URL("http", matcher.group(1), Integer.parseInt(matcher.group(2)),
                    "/?k=" + matcher.group(3));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse request(String method, String pathWithQuery, String form,
                                 boolean useToken, boolean acceptHtml) throws Exception {
        String separator = pathWithQuery.contains("?") ? "&" : "?";
        URL url = new URL("http", mHost, mPort, pathWithQuery
                + (useToken ? separator + "k=" + mToken : ""));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        if (form.length() > 0) {
            connection.setDoOutput(true);
            byte[] bytes = form.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        if (acceptHtml) {
            connection.setRequestProperty("Accept", "text/html");
        }
        int code = connection.getResponseCode();
        java.io.InputStream input = code < 400 ? connection.getInputStream()
                : connection.getErrorStream();
        StringBuilder body = new StringBuilder();
        if (input != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                }
            }
        }
        return new HttpResponse(code, body.toString());
    }

    @Test
    public void testRouteReportsSuccessAndAuthFailureSeparately() throws Exception {
        mExecutor.response = new HttpRequestExecutor.HttpResponse(200,
                "{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}", "req-ok");
        HttpResponse success = request("POST", "/test", "", true, false);
        assertTrue(success.body.contains("\"testSucceeded\":true"));
        assertFalse(success.body.contains("\"testFailed\":true"));

        mExecutor.response = new HttpRequestExecutor.HttpResponse(401,
                "{\"error\":{\"message\":\"bad key\"}}", "req-auth");
        HttpResponse failure = request("POST", "/test", "", true, false);
        assertTrue(failure.body.contains("\"testFailed\":true"));
        assertTrue(failure.body.contains("AUTH"));
        assertFalse(failure.body.contains("\"testSucceeded\":true"));
    }

    private static final class FakeHttpExecutor implements HttpRequestExecutor {
        private HttpRequestExecutor.HttpResponse response;
        private HttpRequestExecutor.HttpCallback lastCallback;

        @Override
        public HttpRequestExecutor.HttpCall execute(HttpRequestExecutor.HttpRequest request,
                                                    HttpRequestExecutor.HttpCallback callback) {
            lastCallback = callback;
            if (response != null) {
                lastCallback.onSuccess(response);
            }
            return new FakeHttpCall();
        }
    }

    private static final class FakeHttpCall implements HttpRequestExecutor.HttpCall {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private static final class HttpResponse {
        final int code;
        final String body;

        HttpResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static final class RecordingListener implements
            AiSubtitlePhoneInputServer.Listener {
        @Override
        public void onConnected(String address) {
        }

        @Override
        public void onDraftChanged(Draft draft, boolean connected) {
        }

        @Override
        public void onSaved(Draft draft, boolean success) {
        }
    }
}
