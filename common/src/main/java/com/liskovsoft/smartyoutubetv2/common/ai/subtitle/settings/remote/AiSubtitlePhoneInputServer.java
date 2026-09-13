package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.remote;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderPreset;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui.PromptProfilesPresenter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui.ProviderProfilesPresenter;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.OkHttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ModelCatalog;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Short-lived LAN editor for TV-hostile text fields. The token is the only pairing secret;
 * the user's API key is never echoed by the server and never appears in a URL.
 */
public final class AiSubtitlePhoneInputServer {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public interface Listener {
        void onConnected(String address);
        void onDraftChanged(Draft draft, boolean connected);
        void onSaved(Draft draft, boolean success);
    }

    private final Context mContext;
    private final Listener mListener;
    private final ServerSocket mServerSocket;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mRunning = new AtomicBoolean(true);
    private final String mToken;
    private final ProviderProfilesPresenter mProfiles;
    private final PromptProfilesPresenter mPrompts;
    private final Draft mDraft = new Draft();
    private final AtomicLong mVersion = new AtomicLong(1);
    private volatile String mClientAddress;
    private final long mExpiresAt;

    private AiSubtitlePhoneInputServer(Context context, Listener listener,
                                      ServerSocket serverSocket, String token,
                                      ProviderProfile profile, PromptProfile prompt,
                                      ProviderProfilesPresenter profiles,
                                      PromptProfilesPresenter prompts) {
        mContext = context.getApplicationContext();
        mListener = listener;
        mServerSocket = serverSocket;
        mToken = token;
        mProfiles = profiles;
        mPrompts = prompts;
        mExpiresAt = System.currentTimeMillis() + 5 * 60 * 1000;
        mVersion.set(2);
        mDraft.from(profile, prompt);
        mDraft.version = mVersion.get();
        startAcceptLoop();
    }

    public static synchronized AiSubtitlePhoneInputServer start(Context context,
                                                               Listener listener,
                                                               ProviderProfile profile,
                                                               PromptProfile prompt) {
        try {
            ServerSocket socket = new ServerSocket(0, 8,
                    InetAddress.getByAddress(new byte[]{0, 0, 0, 0}));
            return new AiSubtitlePhoneInputServer(context, listener, socket, newToken(),
                    profile, prompt, null, null);
        } catch (IOException e) {
            return null;
        }
    }

    static AiSubtitlePhoneInputServer startForTesting(Context context,
                                                      Listener listener,
                                                      ProviderProfile profile,
                                                      PromptProfile prompt,
                                                      ProviderProfilesPresenter profiles,
                                                      PromptProfilesPresenter prompts) {
        try {
            ServerSocket socket = new ServerSocket(0, 8,
                    InetAddress.getByAddress(new byte[]{0, 0, 0, 0}));
            return new AiSubtitlePhoneInputServer(context, listener, socket, newToken(),
                    profile, prompt, profiles, prompts);
        } catch (IOException e) {
            return null;
        }
    }

    static AiSubtitlePhoneInputServer startForTesting(Context context,
                                                      Listener listener,
                                                      ProviderProfile profile,
                                                      PromptProfile prompt) {
        try {
            ServerSocket socket = new ServerSocket(0, 8,
                    InetAddress.getByAddress(new byte[]{0, 0, 0, 0}));
            return new AiSubtitlePhoneInputServer(context, listener, socket, newToken(),
                    profile, prompt, null, null);
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized String getUrl() {
        InetAddress address = firstSiteLocalAddress();
        String host = address != null ? address.getHostAddress() : "192.168.1.255";
        return "http://" + host + ":" + mServerSocket.getLocalPort() + "/?k=" + mToken;
    }

    public synchronized Draft getDraft() {
        return mDraft.copy();
    }

    public synchronized boolean isClientConnected() {
        long now = System.currentTimeMillis();
        return mClientAddress != null && now - mDraft.lastSeenAt < 8000;
    }

    public void close() {
        mRunning.set(false);
        mExecutor.shutdownNow();
        closeQuietly(mServerSocket);
    }

    private void startAcceptLoop() {
        mExecutor.execute(() -> {
            try {
                while (mRunning.get() && !isSessionExpired()) {
                    Socket socket = null;
                    try {
                        socket = mServerSocket.accept();
                        if (isSessionExpired()) {
                            break;
                        }
                        socket.setSoTimeout(10000);
                        handle(socket);
                    } catch (SocketException ignored) {
                    } catch (IOException ignored) {
                    } finally {
                        closeQuietly(socket);
                    }
                }
            } finally {
                close();
            }
        });
        mListener.onConnected(getUrl());
    }

    private void handle(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), UTF_8));
        String requestLine = reader.readLine();
        if (requestLine == null || !requestLine.startsWith("GET /")
                && !requestLine.startsWith("POST /")) {
            writeResponse(socket, 405, "text/plain", "Method not allowed");
            return;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        int contentLength = 0;
        String host = null;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                String name = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                headers.put(name.toLowerCase(), value);
                if ("content-length".equalsIgnoreCase(name)) {
                    contentLength = parsePositive(value);
                } else if ("host".equalsIgnoreCase(name)) {
                    host = value;
                }
            }
        }

        String method = requestLine.substring(0, requestLine.indexOf(' '));
        String path = requestLine.substring(requestLine.indexOf(' ') + 1,
                requestLine.lastIndexOf(' '));
        String query = path.contains("?") ? path.substring(path.indexOf('?') + 1) : "";
        path = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;

        if (!mToken.equals(params(query).get("k"))) {
            writeResponse(socket, 401, "text/plain", "Unauthorized");
            return;
        }
        if (!isAllowedHost(host)) {
            writeResponse(socket, 403, "text/plain", "Forbidden");
            return;
        }
        String origin = headers.get("origin");
        if (origin != null && !origin.isEmpty()
                && !origin.equalsIgnoreCase("http://" + host)) {
            writeResponse(socket, 403, "text/plain", "Forbidden");
            return;
        }
        if (contentLength < 0 || contentLength > 256 * 1024) {
            writeResponse(socket, 413, "text/plain", "Payload too large");
            return;
        }

        Map<String, String> form = new LinkedHashMap<>();
        if ("POST".equals(method)) {
            char[] body = new char[Math.min(contentLength, 256 * 1024)];
            int read = 0;
            while (read < body.length) {
                int count = reader.read(body, read, body.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            form = params(new String(body, 0, read));
        }

        mClientAddress = socket.getInetAddress().getHostAddress();
        mDraft.lastSeenAt = System.currentTimeMillis();

        if ("/".equals(path)) {
            writeResponse(socket, 200, "text/html; charset=utf-8", buildPage());
        } else if ("/state".equals(path)) {
            JSONObject state = state();
            writeResponse(socket, 200, "application/json; charset=utf-8", state.toString());
        } else if ("/select".equals(path)) {
            if (!selectProfile(form)) {
                writeResponse(socket, 404, "application/json; charset=utf-8",
                        conflictState().toString());
                return;
            }
            mDraft.version = mVersion.incrementAndGet();
            writeResponse(socket, 200, "application/json; charset=utf-8", state().toString());
        } else if ("/test".equals(path)) {
            startTest();
            writeResponse(socket, 200, "application/json; charset=utf-8", state().toString());
        } else if ("/save".equals(path)) {
            boolean save = "/save".equals(path);
            if (save) {
                if (!applyForm(form)) {
                    writeResponse(socket, 409, "application/json; charset=utf-8",
                            conflictState().toString());
                    return;
                }
                saveDraft();
                if (mDraft.saveSucceeded) {
                    startTest();
                }
            }
            writeResponse(socket, 200, "application/json; charset=utf-8",
                    state().toString());
        } else {
            writeResponse(socket, 404, "text/plain", "Not found");
        }
    }

    private JSONObject state() {
        JSONObject state = new JSONObject();
        try {
            state.put("version", mDraft.version);
            state.put("saveSucceeded", mDraft.saveSucceeded);
            state.put("saveFailed", !mDraft.saveSucceeded && mDraft.saveAttempted);
            state.put("testSucceeded", mDraft.testSucceeded);
            state.put("testFailed", mDraft.testFailed);
            state.put("testError", valueOrEmpty(mDraft.testError));
            state.put("connected", isClientConnected());
        } catch (Exception ignored) {
        }
        return state;
    }

    private JSONObject conflictState() {
        JSONObject state = new JSONObject();
        try {
            state.put("version", mDraft.version);
            state.put("conflict", true);
            state.put("connected", isClientConnected());
        } catch (Exception ignored) {
        }
        return state;
    }

    private boolean applyForm(Map<String, String> form) {
        long incoming = parseLong(form.get("version"), -1);
        if (incoming != mDraft.version) {
            return false;
        }

        mDraft.name = nonNull(form.get("name")).trim();
        mDraft.baseUrl = nonNull(form.get("baseUrl")).trim();
        mDraft.modelId = nonNull(form.get("modelId")).trim();
        mDraft.promptName = nonNull(form.get("promptName")).trim();
        mDraft.promptContent = nonNull(form.get("prompt"));
        mDraft.targetLanguage = nonNull(form.get("targetLanguage")).trim();
        mDraft.secret = "replace".equals(form.get("secretAction"))
                ? nonNull(form.get("secret")) : "";
        mDraft.secretAction = firstNonBlank(form.get("secretAction"), "keep");
        mDraft.providerType = firstNonBlank(form.get("providerType"),
                ProviderType.OPENAI_COMPATIBLE.name());
        mDraft.protocol = firstNonBlank(form.get("protocol"),
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS.name());
        mDraft.version = mVersion.incrementAndGet();
        return true;
    }

    private void saveDraft() {
        boolean success = false;
        boolean rollbackFailed = false;
        PromptProfile originalPrompt = null;
        PromptProfilesPresenter.SaveResult promptResult = null;
        try {
            AiSubtitleData data = AiSubtitleData.instance(mContext);
            SecretStore secrets = data.secrets();
            ProviderProfileResolver resolver =
                    new ProviderProfileResolver(new OkHttpRequestExecutor(), secrets);
            ProviderProfilesPresenter profiles = mProfiles != null ? mProfiles
                    : new ProviderProfilesPresenter(data.providerProfiles(), secrets, resolver,
                            new ModelCatalog(new OkHttpRequestExecutor()));
            PromptProfilesPresenter prompts = mPrompts != null ? mPrompts
                    : new PromptProfilesPresenter(data.prompts());
            if (!TextUtils.isEmpty(mDraft.promptProfileId)) {
                originalPrompt = prompts.getProfile(mDraft.promptProfileId);
            }
            promptResult = savePrompt(prompts);

            success = promptResult == null || promptResult.isSuccess();
            if (success) {
                ProviderType type = ProviderType.valueOf(mDraft.providerType);
                ProviderProtocol protocol = ProviderProtocol.valueOf(mDraft.protocol);
                String defaultUrl = ProviderPreset.forType(type, protocol).getBaseUrl();
                String baseUrl = firstNonBlank(mDraft.baseUrl, defaultUrl);
                boolean clearSecret = "clear".equals(mDraft.secretAction)
                        && mDraft.originalProfile != null;
                boolean replaceSecret = "replace".equals(mDraft.secretAction)
                        && !TextUtils.isEmpty(mDraft.secret);

                ProviderProfilesPresenter.SaveResult result;
                if (clearSecret) {
                    result = profiles.clearSecret(mDraft.originalProfile.getId());
                } else if (mDraft.originalProfile != null) {
                    result = profiles.save(mDraft.originalProfile.getId(), mDraft.name, type,
                            protocol, baseUrl, mDraft.modelId, mDraft.secret, replaceSecret);
                } else {
                    result = profiles.save(null, mDraft.name, type, protocol, baseUrl,
                            mDraft.modelId, mDraft.secret, replaceSecret);
                }

                success = result.isSuccess() && result.getProfile() != null;
                if (success) {
                    if (clearSecret) {
                        mDraft.originalProfile = result.getProfile();
                        mDraft.originalSecretSet = false;
                        mDraft.secret = "";
                    } else {
                        mDraft.originalProfile = result.getProfile();
                        mDraft.originalSecretSet = true;
                        mDraft.secret = "";
                    }
                    if (!TextUtils.isEmpty(mDraft.targetLanguage)) {
                        data.setTargetLanguage(mDraft.targetLanguage);
                    }
                    com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.AiSubtitleRuntime.applyToBridge(mContext);
                } else {
                    rollbackFailed = !rollbackPrompt(prompts, originalPrompt, promptResult);
                }
            }
        } catch (RuntimeException e) {
            success = false;
            try {
                PromptProfilesPresenter prompts = mPrompts != null ? mPrompts
                        : new PromptProfilesPresenter(
                                AiSubtitleData.instance(mContext).prompts());
                rollbackFailed = !rollbackPrompt(prompts, originalPrompt, promptResult);
            } catch (RuntimeException rollbackError) {
                rollbackFailed = true;
            }
        }

        mDraft.saveAttempted = true;
        mDraft.saveSucceeded = success;
        mDraft.testSucceeded = false;
        mDraft.testFailed = false;
        mDraft.testError = "";
        mDraft.lastError = success ? "" : mContext.getString(R.string.ai_subtitle_phone_save_failed)
                + (rollbackFailed ? " Prompt restore failed." : "");
        if (mListener != null) {
            mListener.onSaved(mDraft.copy(), success);
        }
    }

    private PromptProfilesPresenter.SaveResult savePrompt(PromptProfilesPresenter prompts) {
        if (TextUtils.isEmpty(mDraft.promptContent)) {
            return null;
        }
        if (!TextUtils.isEmpty(mDraft.promptProfileId)) {
            for (PromptProfile profile : prompts.getProfiles()) {
                if (mDraft.promptProfileId.equals(profile.getId())
                        && !profile.isBuiltIn()) {
                    return prompts.update(profile.getId(),
                            firstNonBlank(mDraft.promptName, profile.getName()),
                            mDraft.promptContent);
                }
            }
        }
        PromptProfilesPresenter.SaveResult result = prompts.create(
                firstNonBlank(mDraft.promptName, "Phone prompt"), mDraft.promptContent);
        if (result.isSuccess() && result.getProfile() != null) {
            mDraft.promptProfileId = result.getProfile().getId();
        }
        return result;
    }

    private boolean rollbackPrompt(PromptProfilesPresenter prompts,
                                   PromptProfile originalPrompt,
                                   PromptProfilesPresenter.SaveResult saved) {
        if (saved == null || !saved.isSuccess() || saved.getProfile() == null) {
            return true;
        }
        try {
            if (originalPrompt != null) {
                return prompts.update(originalPrompt.getId(), originalPrompt.getName(),
                        originalPrompt.getContent()).isSuccess();
            }
            return prompts.delete(saved.getProfile().getId());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void startTest() {
        try {
            AiSubtitleData data = AiSubtitleData.instance(mContext);
            SecretStore secrets = data.secrets();
            ProviderProfileResolver resolver =
                    new ProviderProfileResolver(new OkHttpRequestExecutor(), secrets);
            ProviderProfilesPresenter profiles = mProfiles != null ? mProfiles
                    : new ProviderProfilesPresenter(data.providerProfiles(), secrets, resolver,
                            new ModelCatalog(new OkHttpRequestExecutor()));
            ProviderProfile profile = profiles.getProfile(mDraft.originalProfile.getId());
            if (profile == null) {
                setTestResult(false, new com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure(
                        com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory.PROTOCOL,
                        "Saved profile is missing."));
                return;
            }
            profiles.testTranslationConnection(profile,
                    firstNonBlank(mDraft.targetLanguage, data.getTargetLanguage()),
                    new ProviderProfilesPresenter.ConnectionTestListener() {
                        @Override
                        public void onStarted() {
                            mDraft.testSucceeded = false;
                            mDraft.testFailed = false;
                            mDraft.testError = "";
                            notifyDraftChanged();
                        }

                        @Override
                        public void onResult(
                                com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ConnectionTestResult result) {
                            setTestResult(result != null && result.isSuccess(),
                                    result == null ? null : result.getFailure());
                        }
                    });
        } catch (RuntimeException e) {
            setTestResult(false, new com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure(
                    com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailureCategory.NETWORK,
                    "Translation test could not start."));
        }
    }

    private synchronized void setTestResult(boolean success,
            com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationFailure failure) {
        mDraft.testSucceeded = success;
        mDraft.testFailed = !success;
        mDraft.testError = success || failure == null ? ""
                : failure.getCategory().name() + ": " + failure.getMessage();
        notifyDraftChanged();
    }

    private boolean selectProfile(Map<String, String> form) {
        try {
            String id = nonNull(form.get("profileId"));
            AiSubtitleData data = AiSubtitleData.instance(mContext);
            PromptProfilesPresenter prompts = mPrompts != null ? mPrompts
                    : new PromptProfilesPresenter(data.prompts());
            PromptProfile prompt = prompts.getProfile(prompts.getSelectedProfileId());
            if ("NEW".equals(id)) {
                mDraft.from(null, prompt);
                mDraft.isNewProfile = true;
                return true;
            }
            ProviderProfilesPresenter profiles = mProfiles != null ? mProfiles
                    : profilesPresenter(data);
            ProviderProfile profile = profiles.getProfile(id);
            if (profile == null) {
                return false;
            }
            mDraft.from(profile, prompt);
            mDraft.isNewProfile = false;
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ProviderProfilesPresenter profilesPresenter(AiSubtitleData data) {
        SecretStore secrets = data.secrets();
        return new ProviderProfilesPresenter(data.providerProfiles(), secrets,
                new ProviderProfileResolver(new OkHttpRequestExecutor(), secrets),
                new ModelCatalog(new OkHttpRequestExecutor()));
    }

    private void notifyDraftChanged() {
        if (mListener != null) {
            mListener.onDraftChanged(mDraft.copy(), isClientConnected());
        }
    }

    private String buildPage() {
        String currentProfile = mDraft.originalProfile != null
                ? mDraft.originalProfile.getId() : "NEW";
        StringBuilder profiles = new StringBuilder();
        if (mDraft.originalProfile != null) {
            profiles.append("<option value=\"").append(htmlEscape(currentProfile))
                    .append("\" selected>").append(htmlEscape(mDraft.name)).append("</option>");
        }
        try {
            ProviderProfilesPresenter presenter = mProfiles;
            if (presenter == null) {
                presenter = profilesPresenter(AiSubtitleData.instance(mContext));
            }
            for (ProviderProfile profile : presenter.getProfiles()) {
                if (profile.getId().equals(currentProfile)) {
                    continue;
                }
                profiles.append("<option value=\"").append(htmlEscape(profile.getId()))
                        .append("\">").append(htmlEscape(profile.getName())).append("</option>");
            }
        } catch (RuntimeException ignored) {
        }
        profiles.append("<option value=\"NEW\"").append(mDraft.originalProfile == null
                ? " selected" : "").append(">New profile</option>");

        ProviderType[] types = ProviderType.values();
        ProviderProtocol[] protocols = ProviderProtocol.values();
        StringBuilder typeOptions = new StringBuilder();
        for (ProviderType type : types) {
            typeOptions.append("<option value=\"").append(type.name()).append('\"')
                    .append(type.name().equals(mDraft.providerType) ? " selected" : "")
                    .append('>').append(htmlEscape(ProviderPreset.forType(type).getDisplayName()))
                    .append("</option>");
        }
        StringBuilder protocolOptions = new StringBuilder();
        for (ProviderProtocol protocol : protocols) {
            protocolOptions.append("<option value=\"").append(protocol.name()).append('\"')
                    .append(protocol.name().equals(mDraft.protocol) ? " selected" : "")
                    .append('>').append(htmlEscape(protocol.name())).append("</option>");
        }

        return "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>SmartTube AI</title>"
                + "<style>body{font-family:system-ui;background:#111;color:#eee;padding:16px}"
                + "label{display:block;margin:14px 0 6px}input,select,textarea{width:100%;"
                + "font-size:17px;padding:10px;border-radius:8px;border:1px solid #555;"
                + "background:#222;color:#fff}textarea{min-height:150px}button{width:100%;"
                + "padding:12px;margin-top:10px;border:0;border-radius:8px;background:#0af;"
                + "color:#fff;font-size:17px}.secondary{background:#345}.status{margin:12px 0;"
                + "min-height:40px;white-space:pre-wrap}</style></head><body>"
                + "<h1>AI 字幕手机输入</h1><form id=\"editor\" autocomplete=\"off\">"
                + "<label>Profile</label><select id=\"profileId\">" + profiles + "</select>"
                + "<label>配置名称</label><input id=\"name\" value=\""
                + htmlEscape(mDraft.name) + "\">"
                + "<label>Provider</label><select id=\"providerType\">" + typeOptions + "</select>"
                + "<label>Protocol</label><select id=\"protocol\">" + protocolOptions + "</select>"
                + "<label>接口地址</label><input id=\"baseUrl\" value=\""
                + htmlEscape(mDraft.baseUrl) + "\">"
                + "<label>模型 ID</label><input id=\"modelId\" value=\""
                + htmlEscape(mDraft.modelId) + "\">"
                + "<label>目标语言</label><input id=\"targetLanguage\" value=\""
                + htmlEscape(mDraft.targetLanguage) + "\">"
                + "<label>API 密钥动作</label><select id=\"secretAction\">"
                + "<option value=\"keep\">保留</option><option value=\"replace\">替换</option>"
                + "<option value=\"clear\">清除</option></select>"
                + "<input id=\"secret\" type=\"password\">"
                + "<label>Prompt 名称</label><input id=\"promptName\" value=\""
                + htmlEscape(mDraft.promptName) + "\">"
                + "<label>Prompt</label><textarea id=\"prompt\">"
                + htmlEscape(mDraft.promptContent) + "</textarea>"
                + "<button type=\"button\" id=\"save\">保存并测试</button>"
                + "<button type=\"button\" id=\"test\" class=\"secondary\">测试当前已保存配置</button>"
                + "</form><div id=\"status\"></div>"
                + "<script>const v={version:" + mDraft.version + "};"
                + "function val(id){return document.getElementById(id).value;}"
                + "function data(){return {version:v.version,name:val('name'),"
                + "providerType:val('providerType'),protocol:val('protocol'),"
                + "baseUrl:val('baseUrl'),modelId:val('modelId'),"
                + "targetLanguage:val('targetLanguage'),secretAction:val('secretAction'),"
                + "secret:val('secret'),promptName:val('promptName'),prompt:val('prompt')};}"
                + "function send(path,body,done){fetch('/'+path+'?k=" + mToken
                + "',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:new URLSearchParams(body).toString()})"
                + ".then(function(r){return r.text();})"
                + ".then(function(text){var x;try{x=JSON.parse(text);}catch(e){x={};}"
                + "done({ok:true,body:x});})"
                + ".catch(function(){status('断线，请检查局域网');});}"
                + "function status(text){document.getElementById('status').textContent=text;}"
                + "function saveState(x){v.version=x.body.version||v.version;"
                + "if(x.body.conflict){status('保存冲突：页面已过期，请手动重载。');return;}"
                + "let text=x.body.saveSucceeded?'已保存':'保存失败：请检查必填项和密钥。';"
                + "if(x.body.testSucceeded){text+='\\n测试成功。';}"
                + "else if(x.body.testFailed){text+='\\n测试失败：'+(x.body.testError||'未知原因');}"
                + "status(text);}"
                + "document.getElementById('save').onclick=()=>send('save',data(),saveState);"
                + "document.getElementById('test').onclick=()=>send('test',{},saveState);"
                + "document.getElementById('profileId').onchange=()=>{"
                + "if(confirm('切换会丢弃未保存内容，继续？')){"
                + "send('select',{profileId:val('profileId')},()=>location.reload());}};"
                + "function poll(){fetch('/state?k=" + mToken
                + "').then(r=>r.json()).then(x=>{if(!x.saveSucceeded&&!x.testSucceeded"
                + "&&!x.testFailed&&!x.connected)return;"
                + "let text=x.saveSucceeded?'已保存':'已保存';"
                + "if(x.testSucceeded){text+='\\n测试成功。';}"
                + "else if(x.testFailed){text+='\\n测试失败：'+(x.testError||'未知原因');}"
                + "else{text+='\\n测试进行中…';} status(text);}).catch(function(){});"
                + "setTimeout(poll,1500);}poll();</script></body></html>";
    }

    private static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Map<String, String> params(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            try {
                result.put(URLDecoder.decode(key, "UTF-8"),
                        URLDecoder.decode(value, "UTF-8"));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static void writeResponse(Socket socket, int status, String contentType,
                                      String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), UTF_8));
        writer.write("HTTP/1.1 " + status + " OK\r\n");
        writer.write("Content-Type: " + contentType + "\r\n");
        writer.write("Content-Length: " + bytes.length + "\r\n");
        writer.write("Cache-Control: no-store\r\n");
        writer.write("Connection: close\r\n\r\n");
        writer.flush();
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    private static InetAddress firstSiteLocalAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()
                        || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address.isSiteLocalAddress() && address.getAddress().length == 4) {
                        return address;
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return null;
    }

    private static String newToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder token = new StringBuilder(48);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 48; i++) {
            token.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return token.toString();
    }

    private static int parsePositive(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    private synchronized boolean isSessionExpired() {
        long now = System.currentTimeMillis();
        if (now >= mExpiresAt) {
            return true;
        }
        return mDraft.lastSeenAt > 0 && now - mDraft.lastSeenAt > 60_000;
    }

    private boolean isAllowedHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }

        InetAddress address = firstSiteLocalAddress();
        String hostName = address != null ? address.getHostAddress() : "192.168.1.255";
        String expected = hostName + ":" + mServerSocket.getLocalPort();
        String loopback = "127.0.0.1:" + mServerSocket.getLocalPort();
        return host.equalsIgnoreCase(expected) || host.equalsIgnoreCase(loopback);
    }

    private static long parseLong(String value, long fallback) {

        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && first.trim().length() > 0) {
            return first;
        }
        return second != null ? second : "";
    }

    private static void closeQuietly(ServerSocket serverSocket) {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static final class Draft {
        public String profileId = "";
        public boolean isNewProfile;
        public String name = "";
        public String baseUrl = "";
        public String modelId = "";
        public String secret = "";
        public String secretAction = "keep";
        public String providerType = ProviderType.OPENAI_COMPATIBLE.name();
        public String protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS.name();
        public String targetLanguage = "";
        public String promptProfileId = "";
        public String promptName = "";
        public String promptContent = "";
        public String lastError = "";
        public String testError = "";
        public boolean originalSecretSet;
        public boolean saveAttempted;
        public boolean saveSucceeded;
        public boolean testSucceeded;
        public boolean testFailed;
        public long version = 1;
        public long lastSeenAt;
        public ProviderProfile originalProfile;

        Draft from(ProviderProfile profile, PromptProfile prompt) {
            originalProfile = profile;
            isNewProfile = profile == null;
            profileId = profile != null ? profile.getId() : "";
            name = profile != null ? nonNull(profile.getName()) : "";
            baseUrl = profile != null ? nonNull(profile.getBaseUrl()) : "";
            modelId = profile != null ? nonNull(profile.getModelId()) : "";
            originalSecretSet = profile != null
                    && !TextUtils.isEmpty(profile.getSecretReference());
            secret = "";
            secretAction = "keep";
            providerType = profile != null
                    ? profile.getProviderType().name()
                    : ProviderType.OPENAI_COMPATIBLE.name();
            protocol = profile != null
                    ? profile.getProtocol().name()
                    : ProviderProtocol.OPENAI_CHAT_COMPLETIONS.name();
            saveAttempted = false;
            saveSucceeded = false;
            testSucceeded = false;
            testFailed = false;
            testError = "";
            lastError = "";
            if (prompt != null) {
                promptProfileId = nonNull(prompt.getId());
                promptName = nonNull(prompt.getName());
                promptContent = nonNull(prompt.getContent());
            }
            return this;
        }

        Draft copy() {
            Draft copy = new Draft();
            copy.profileId = profileId;
            copy.isNewProfile = isNewProfile;
            copy.name = name;
            copy.baseUrl = baseUrl;
            copy.modelId = modelId;
            copy.secret = secret;
            copy.secretAction = secretAction;
            copy.providerType = providerType;
            copy.protocol = protocol;
            copy.targetLanguage = targetLanguage;
            copy.promptProfileId = promptProfileId;
            copy.promptName = promptName;
            copy.promptContent = promptContent;
            copy.lastError = lastError;
            copy.testError = testError;
            copy.originalSecretSet = originalSecretSet;
            copy.saveAttempted = saveAttempted;
            copy.saveSucceeded = saveSucceeded;
            copy.testSucceeded = testSucceeded;
            copy.testFailed = testFailed;
            copy.version = version;
            copy.lastSeenAt = lastSeenAt;
            copy.originalProfile = originalProfile;
            return copy;
        }
    }
}
