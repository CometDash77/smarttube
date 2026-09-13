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
import java.io.Closeable;
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
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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
    private final Draft mDraft = new Draft();
    private final AtomicLong mVersion = new AtomicLong(1);
    private volatile String mClientAddress;
    private final long mExpiresAt;

    private AiSubtitlePhoneInputServer(Context context, Listener listener,
                                      ServerSocket serverSocket, String token,
                                      ProviderProfile profile, PromptProfile prompt) {
        mContext = context.getApplicationContext();
        mListener = listener;
        mServerSocket = serverSocket;
        mToken = token;
        mExpiresAt = System.currentTimeMillis() + 5 * 60 * 1000;
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
                    profile, prompt);
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
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
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
        } else if ("/update".equals(path) || "/save".equals(path)) {
            boolean save = "/save".equals(path) || "true".equals(form.get("save"));
            applyForm(form, save);
            JSONObject state = state();
            try {
                state.put("saved", save && mDraft.saveSucceeded);
            } catch (JSONException ignored) {
            }
            writeResponse(socket, 200, "application/json; charset=utf-8", state.toString());
        } else {
            writeResponse(socket, 404, "text/plain", "Not found");
        }
    }

    private JSONObject state() {
        JSONObject state = new JSONObject();
        try {
            state.put("version", mDraft.version);
            state.put("name", valueOrEmpty(mDraft.name));
            state.put("baseUrl", valueOrEmpty(mDraft.baseUrl));
            state.put("modelId", valueOrEmpty(mDraft.modelId));
            state.put("prompt", valueOrEmpty(mDraft.promptContent));
            state.put("promptName", valueOrEmpty(mDraft.promptName));
            state.put("secretSet", mDraft.originalSecretSet || !TextUtils.isEmpty(mDraft.secret));
            state.put("connected", isClientConnected());
            state.put("lastError", valueOrEmpty(mDraft.lastError));
        } catch (Exception ignored) {
        }
        return state;
    }

    private void applyForm(Map<String, String> form, boolean save) {
        long incoming = parseLong(form.get("version"), mDraft.version);
        if (incoming >= mDraft.version) {
            mDraft.name = nonNull(form.get("name"));
            mDraft.baseUrl = nonNull(form.get("baseUrl"));
            mDraft.modelId = nonNull(form.get("modelId"));
            mDraft.promptName = nonNull(form.get("promptName"));
            mDraft.promptContent = nonNull(form.get("prompt"));
            if (!TextUtils.isEmpty(form.get("secret"))) {
                mDraft.secret = form.get("secret");
            }
            if ("true".equals(form.get("clearSecret"))) {
                mDraft.secret = "";
            }
            mDraft.version = mVersion.incrementAndGet();
        }

        if (save) {
            saveDraft();
        } else {
            mDraft.saveSucceeded = false;
            notifyDraftChanged();
        }
    }

    private void saveDraft() {
        boolean success;
        try {
            AiSubtitleData data = AiSubtitleData.instance(mContext);
            SecretStore secrets = data.secrets();
            ProviderProfileResolver resolver =
                    new ProviderProfileResolver(new OkHttpRequestExecutor(), secrets);
            ProviderProfilesPresenter profiles =
                    new ProviderProfilesPresenter(data.providerProfiles(), secrets, resolver,
                            new ModelCatalog(new OkHttpRequestExecutor()));
            ProviderProtocol protocol = mDraft.originalProfile != null
                    ? mDraft.originalProfile.getProtocol() : ProviderProtocol.OPENAI_CHAT_COMPLETIONS;
            ProviderType type = mDraft.originalProfile != null
                    ? mDraft.originalProfile.getProviderType() : ProviderType.OPENAI_COMPATIBLE;
            String defaultUrl = ProviderPreset.forType(type, protocol).getBaseUrl();
            String baseUrl = firstNonBlank(mDraft.baseUrl, defaultUrl);

            ProviderProfilesPresenter.SaveResult result;
            if (mDraft.originalProfile != null) {
                result = profiles.save(mDraft.originalProfile.getId(), mDraft.name, type,
                        protocol, baseUrl, mDraft.modelId, mDraft.secret,
                        !TextUtils.isEmpty(mDraft.secret));
            } else {
                result = profiles.save(null, mDraft.name, type, protocol, baseUrl,
                        mDraft.modelId, mDraft.secret, true);
            }
            success = result.isSuccess() && result.getProfile() != null;
            if (success) {
                mDraft.originalProfile = result.getProfile();
                mDraft.originalSecretSet = true;
                mDraft.secret = "";
                if (!TextUtils.isEmpty(mDraft.promptContent)) {
                    savePrompt(data);
                }
                com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.AiSubtitleRuntime.applyToBridge(mContext);
            }
        } catch (RuntimeException e) {
            success = false;
        }

        mDraft.saveSucceeded = success;
        mDraft.lastError = success ? "" : mContext.getString(R.string.ai_subtitle_phone_save_failed);
        if (mListener != null) {
            mListener.onSaved(mDraft.copy(), success);
        }
    }

    private void savePrompt(AiSubtitleData data) {
        PromptProfilesPresenter prompts = new PromptProfilesPresenter(data.prompts());
        if (!TextUtils.isEmpty(mDraft.promptProfileId)) {
            for (PromptProfile profile : prompts.getProfiles()) {
                if (mDraft.promptProfileId.equals(profile.getId()) && !profile.isBuiltIn()) {
                    prompts.update(profile.getId(), firstNonBlank(mDraft.promptName,
                            profile.getName()), mDraft.promptContent);
                    return;
                }
            }
        }
        PromptProfilesPresenter.SaveResult result = prompts.create(
                firstNonBlank(mDraft.promptName, "Phone prompt"), mDraft.promptContent);
        if (result.isSuccess() && result.getProfile() != null) {
            mDraft.promptProfileId = result.getProfile().getId();
        }
    }

    private void notifyDraftChanged() {
        if (mListener != null) {
            mListener.onDraftChanged(mDraft.copy(), isClientConnected());
        }
    }

    private String buildPage() {
        return "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>SmartTube AI</title>"
                + "<style>body{font-family:system-ui;background:#111;color:#eee;padding:16px}"
                + "label{display:block;margin:14px 0 6px}input,textarea{width:100%;font-size:17px;"
                + "padding:10px;border-radius:8px;border:1px solid #555;background:#222;color:#fff}"
                + "textarea{min-height:150px}button{width:100%;padding:12px;margin-top:16px;"
                + "border:0;border-radius:8px;background:#0af;color:#fff;font-size:17px}"
                + ".status{margin-top:14px;min-height:22px}</style></head><body>"
                + "<h1>AI 字幕手机输入</h1>"
                + "<label>配置名称</label><input id=\"name\">"
                + "<label>接口地址（Custom 才需要修改）</label><input id=\"baseUrl\">"
                + "<label>模型 ID</label><input id=\"modelId\">"
                + "<label>API 密钥（留空保持不变）</label>"
                + "<input id=\"secret\" type=\"password\" autocomplete=\"off\">"
                + "<label>Prompt 名称</label><input id=\"promptName\" autocomplete=\"off\"><label>Prompt</label><textarea id=\"prompt\"></textarea>"
                + "<button id=\"save\">保存并测试</button><div id=\"status\" class=\"status\"></div>"
                + "<script>const v={version:0};const ids=['name','baseUrl','modelId','prompt','promptName'];"
                + "let timer=null;function s(){return {version:v.version,save:false,"
                + "name:document.getElementById('name').value,baseUrl:document.getElementById('baseUrl').value,"
                + "modelId:document.getElementById('modelId').value,secret:document.getElementById('secret').value,"
                + "prompt:document.getElementById('prompt').value,promptName:document.getElementById('promptName').value};}"
                + "function send(url,body,onDone){fetch(url,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:new URLSearchParams(body).toString()}).then(r=>r.json()).then(onDone).catch(()=>"
                + "{document.getElementById('status').textContent='断线，请检查局域网';});}"
                + "function schedule(){clearTimeout(timer);timer=setTimeout(()=>send('/update',s(),x=>apply(x,false)),900);}"
                + "function apply(x,isSave){if(document.activeElement===document.getElementById('prompt')&&!isSave)return;"
                + "v.version=x.version;if(!isSave){document.getElementById('name').value=x.name;"
                + "document.getElementById('baseUrl').value=x.baseUrl;document.getElementById('modelId').value=x.modelId;"
                + "document.getElementById('prompt').value=x.prompt;} document.getElementById('status').textContent="
                + "x.saved?'已保存并同步到电视':(x.lastError||'草稿已同步');}"
                + "document.getElementById('save').onclick=()=>send('/save',Object.assign(s(),{save:'true'}),x=>apply(x,true));"
                + "ids.concat(['secret']).forEach(id=>document.getElementById(id).addEventListener('input',schedule));"
                + "function poll(){fetch('/state?k=" + mToken + "&v='+v.version).then(r=>r.json()).then(x=>apply(x,false)).catch("
                + "()=>{document.getElementById('status').textContent='断线，请检查局域网';});setTimeout(poll,1500);}"
                + "poll();</script></body></html>";
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
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
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
        return host.equalsIgnoreCase(expected);
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

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static final class Draft {
        public String name = "";
        public String baseUrl = "";
        public String modelId = "";
        public String secret = "";
        public String promptProfileId = "";
        public String promptName = "";
        public String promptContent = "";
        public String lastError = "";
        public boolean originalSecretSet;
        public boolean saveSucceeded;
        public long version = 1;
        public long lastSeenAt;
        public ProviderProfile originalProfile;

        Draft from(ProviderProfile profile, PromptProfile prompt) {
            originalProfile = profile;
            if (profile != null) {
                name = nonNull(profile.getName());
                baseUrl = nonNull(profile.getBaseUrl());
                modelId = nonNull(profile.getModelId());
                originalSecretSet = !TextUtils.isEmpty(profile.getSecretReference());
            }
            if (prompt != null) {
                promptProfileId = nonNull(prompt.getId());
                promptName = nonNull(prompt.getName());
                promptContent = nonNull(prompt.getContent());
            }
            return this;
        }

        Draft copy() {
            Draft copy = new Draft();
            copy.name = name;
            copy.baseUrl = baseUrl;
            copy.modelId = modelId;
            copy.secret = secret;
            copy.promptProfileId = promptProfileId;
            copy.promptName = promptName;
            copy.promptContent = promptContent;
            copy.lastError = lastError;
            copy.originalSecretSet = originalSecretSet;
            copy.saveSucceeded = saveSucceeded;
            copy.version = version;
            copy.lastSeenAt = lastSeenAt;
            copy.originalProfile = originalProfile;
            return copy;
        }
    }
}
