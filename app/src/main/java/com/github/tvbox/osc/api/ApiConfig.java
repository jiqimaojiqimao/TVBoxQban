package com.github.tvbox.osc.api;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.JsLoader;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.ProxyRule;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AES;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.M3u8;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.Proxy;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author xuameng
 * @date :2026/06/28
 * @description:  全面调整
 */
public class ApiConfig {

    private static ApiConfig instance;
    private final LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource;
    private ParseBean mDefaultParse;
    private final List<LiveChannelGroup> liveChannelGroupList;
    private final List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    private Map<String, String> myHosts;
    private List<IJKCode> ijkCodes;
    private String spider = null;

    private String currentPlaySourceKey = "";
    private String loadedLiveConfigUrl = "";
    public String wallpaper = "";
    private String danmaku = ""; // xuameng 弹幕
    public String musicwallpaper = "";   // xuameng背景图
    public String JvhuiWarning = "";   // xuameng版权提示

    private final SourceBean emptyHome = new SourceBean();

    private final JarLoader jarLoader = new JarLoader();
    private final JsLoader jsLoader = new JsLoader();
    private final Gson gson;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService configLoadExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService jarLoadExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService danmuSearchExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> warmedSearchSpiderKeys = new HashSet<>();

    private final String userAgent = "okhttp/3.15";

    private ApiConfig() {
        LoadapiUrlXu(); // xuameng 若接口url为空，将API_URL重置为接口
        clearLoader();
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        parseBeanList = new ArrayList<>();
        searchSourceBeanList = new ArrayList<>();
        gson = new Gson();
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        loadDefaultConfig();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public static String FindResult(String json, String configKey) {
        String content = json;
        try {
            if (AES.isJson(content)) {
                return content;
            }
            Pattern pattern = getPattern("[A-Za-z0-9]{8}\\*\\*");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                content = content.substring(content.indexOf(matcher.group()) + 10);
                content = new String(Base64.decode(content, Base64.DEFAULT));
            }
            content = content.trim();
            if (content.startsWith("2423")) {
                content = content.replaceAll("\\s+", "");
                String data = content.substring(content.indexOf("2324") + 4, content.length() - 26);
                content = new String(AES.toBytes(content)).toLowerCase();
                String key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16);
                String iv = AES.rightPadding(content.substring(content.length() - 13), "0", 16);
                json = AES.CBC(data, key, iv);
            } else if (configKey != null && !AES.isJson(content)) {
                json = AES.ECB(content, configKey);
            } else {
                json = content;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    private static byte[] getImgJar(String body) {
        Pattern pattern = getPattern("[A-Za-z0-9]{8}\\*\\*");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            body = body.substring(body.indexOf(matcher.group()) + 10);
            return Base64.decode(body, Base64.DEFAULT);
        }
        return "".getBytes();
    }

    private String TempKey = null;

    private String configUrl(String apiUrl) {
        TempKey = null;
        String configUrl = "";
        String pk = ";pk;";
        apiUrl = apiUrl.replace("file://", "clan://localhost/");
        if (apiUrl.contains(pk)) {
            String[] a = apiUrl.split(pk);
            TempKey = a[1];
            if (apiUrl.startsWith("clan")) {
                configUrl = clanToAddress(a[0]);
            } else if (apiUrl.startsWith("http")) {
                configUrl = a[0];
            } else {
                configUrl = "http://" + a[0];
            }
        } else if (apiUrl.startsWith("clan")) {
            configUrl = clanToAddress(apiUrl);
        } else if (!apiUrl.startsWith("http")) {
            configUrl = "http://" + apiUrl;
        } else {
            configUrl = apiUrl;
        }
        return configUrl;
    }

    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "http://xuameng.vicp.net:8082/jvhuiys/1/xu.json");
        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        if (useCache && cache.exists()) {
            try {
                String json = readConfigFile(cache);
                if (switchApiCollectionIfNeeded(apiUrl, json)) {
                    loadConfig(false, callback, activity);
                    return;
                }
                clearApiLinesIfUnmatched(apiUrl);
                parseJson(apiUrl, json);
                callback.success();
                return;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        String configUrl = configUrl(apiUrl);
        final String configKey = TempKey;

        fetchConfigAsync(apiUrl, configUrl, configKey, new ConfigFetchCallback() {
            @Override
            public void success(String json) {
                try {
                    if (switchApiCollectionIfNeeded(apiUrl, json)) {
                        FileUtils.saveCache(cache, json);
                        loadConfig(false, callback, activity);
                        return;
                    }
                    clearApiLinesIfUnmatched(apiUrl);
                    parseJson(apiUrl, json);
                    FileUtils.saveCache(cache, json);
                    callback.success();
                } catch (Throwable th) {
                    th.printStackTrace();
                    callback.error("聚汇影视提示您：解析配置失败！");
                }
            }

            @Override
            public void error(String error) {
                if (cache.exists()) {
                    try {
                        String json = readConfigFile(cache);
                        if (switchApiCollectionIfNeeded(apiUrl, json)) {
                            loadConfig(false, callback, activity);
                            return;
                        }
                        clearApiLinesIfUnmatched(apiUrl);
                        parseJson(apiUrl, json);
                        callback.success();
                        return;
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
                callback.error("聚汇影视提示您：拉取配置失败！\n" + error);
            }
        });
    }

    public void loadLiveConfig(boolean useCache, LoadConfigCallback callback) {
        String apiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (apiUrl.isEmpty()) {
            apiUrl = Hawk.get(HawkConfig.API_URL, "http://xuameng.vicp.net:8082/jvhuiys/1/xu.json");
        }
        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        final String liveApiUrl = apiUrl;
        String liveApiConfigUrl = configUrl(liveApiUrl);
        final String liveConfigKey = TempKey;
        File liveCache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(liveApiUrl));
        LOG.i("echo-load live config " + liveApiUrl);
        if (useCache && liveCache.exists()) {
            try {
                parseLiveConfigContent(liveApiUrl, liveCache);
                if (hasLiveConfigResult()) {
                    loadedLiveConfigUrl = liveApiUrl;
                    callback.success();
                    return;
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        fetchConfigAsync(liveApiUrl, liveApiConfigUrl, liveConfigKey, new ConfigFetchCallback() {
            @Override
            public void success(String json) {
                try {
                    parseLiveConfigContent(liveApiUrl, json);
                    if (!hasLiveConfigResult()) {
                        callback.error("聚汇影视提示您：解析直播配置失败！");
                        initLiveSettings();
                        Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                        Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
                        return;
                    }
                    loadedLiveConfigUrl = liveApiUrl;
                    FileUtils.saveCache(liveCache, json);
                    callback.success();
                } catch (Throwable th) {
                    th.printStackTrace();
                    callback.error("聚汇影视提示您：解析直播配置失败！");
                    initLiveSettings();
                    Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                    Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
                }
            }

            @Override
            public void error(String error) {
                if (liveCache.exists()) {
                    try {
                        parseLiveConfigContent(liveApiUrl, liveCache);
                        if (hasLiveConfigResult()) {
                            loadedLiveConfigUrl = liveApiUrl;
                            callback.success();
                            return;
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
                callback.error("聚汇影视提示您：直播配置拉取失败！");
                initLiveSettings();
                Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
            }
        });
    }

    private boolean hasLiveConfigResult() {
        return liveChannelGroupList != null && !liveChannelGroupList.isEmpty();
    }

    private static final int LOAD_JAR_MAX_RETRY = 1;

    public void loadJar(boolean useCache, String spider, LoadConfigCallback callback) {
        loadJar(useCache, spider, callback, 0);
    }

    private interface JarLoadCallback {
        void complete(boolean success);
    }

    private interface JarDownloadCallback {
        void complete(File file, String error);
    }

    private interface ConfigFetchCallback {
        void success(String body);

        void error(String error);
    }

    private void fetchConfigAsync(final String apiUrl, final String requestUrl, final String configKey, final ConfigFetchCallback callback) {
        configLoadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String result = "";
                String error = "";
                okhttp3.Response response = null;
                try {
                    okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(requestUrl)
                            .build();
                    okhttp3.OkHttpClient client = OkGoHelper.getDefaultClient();
                    if (client == null) {
                        client = com.github.catvod.net.OkHttp.client();
                    }
                    response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        error = "HTTP " + response.code();
                    } else if (response.body() == null) {
                        error = "empty body";
                    } else {
                        result = FindResult(response.body().string(), configKey);
                        if (apiUrl.startsWith("clan")) {
                            result = clanContentFix(clanToAddress(apiUrl), result);
                        }
                        result = fixContentPath(apiUrl, result);
                    }
                } catch (Throwable th) {
                    error = th.getMessage();
                    if (TextUtils.isEmpty(error)) {
                        error = th.toString();
                    }
                } finally {
                    if (response != null) {
                        closeQuietly(response.body());
                    }
                }
                final String finalResult = result;
                final String finalError = error;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (TextUtils.isEmpty(finalError)) {
                            callback.success(finalResult);
                        } else {
                            callback.error(finalError);
                        }
                    }
                });
            }
        });
    }

    private void loadJarAsync(File file, JarLoadCallback callback) {
        jarLoadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    success = file != null && file.exists() && jarLoader.load(file.getAbsolutePath());
                } catch (Throwable th) {
                    LOG.e("echo---jar Loader threw exception: " + th.getMessage());
                }
                final boolean result = success;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.complete(result);
                    }
                });
            }
        });
    }

    private void downloadJarAsync(String url, boolean isJarInImg, File cache, JarDownloadCallback callback) {
        jarLoadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File result = null;
                String error = "";
                okhttp3.Response response = null;
                InputStream inputStream = null;
                FileOutputStream outputStream = null;
                File temp = new File(cache.getAbsolutePath() + ".tmp");
                try {
                    File cacheDir = cache.getParentFile();
                    if (cacheDir != null && !cacheDir.exists()) {
                        cacheDir.mkdirs();
                    }
                    if (temp.exists()) {
                        temp.delete();
                    }
                    okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                            .build();
                    okhttp3.OkHttpClient client = OkGoHelper.getDefaultClient();
                    if (client == null) {
                        client = com.github.catvod.net.OkHttp.client();
                    }
                    response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        error = "HTTP " + response.code();
                    } else if (response.body() == null) {
                        error = "empty body";
                    } else if (isJarInImg) {
                        String respData = response.body().string();
                        LOG.i("echo---jar Response: " + respData);
                        byte[] imgJar = getImgJar(respData);
                        if (imgJar == null || imgJar.length == 0) {
                            error = "empty img jar";
                        } else {
                            outputStream = new FileOutputStream(temp);
                            outputStream.write(imgJar);
                            outputStream.flush();
                            closeQuietly(outputStream);
                            outputStream = null;
                            result = replaceCache(temp, cache);
                        }
                    } else {
                        inputStream = response.body().byteStream();
                        outputStream = new FileOutputStream(temp);
                        byte[] buffer = new byte[16384];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                        closeQuietly(outputStream);
                        outputStream = null;
                        result = replaceCache(temp, cache);
                    }
                } catch (Throwable th) {
                    error = th.getMessage();
                } finally {
                    closeQuietly(inputStream);
                    closeQuietly(outputStream);
                    if (response != null) {
                        closeQuietly(response.body());
                    }
                    if (result == null && temp.exists()) {
                        temp.delete();
                    }
                }
                final File finalResult = result;
                final String finalError = error;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.complete(finalResult, finalError);
                    }
                });
            }
        });
    }

    private File replaceCache(File temp, File cache) throws IOException {
        if (cache.exists() && !cache.delete()) {
            LOG.i("echo---delete old jar cache failed:" + cache.getAbsolutePath());
        }
        if (!temp.renameTo(cache)) {
            FileUtils.copyFile(temp, cache);
            temp.delete();
        }
        return cache;
    }

    private void closeQuietly(java.io.Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadJar(boolean useCache, String spider, LoadConfigCallback callback, int retryCount) {
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/" + MD5.string2MD5(jarUrl) + ".jar");

        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5))) {
                if (cache.exists()) {
                    loadJarAsync(cache, new JarLoadCallback() {
                        @Override
                        public void complete(boolean success) {
                            if (success) {
                                callback.success();
                            } else {
                                callback.error("md5失效");
                            }
                        }
                    });
                    return;
                }
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                } else {
                    callback.error("md5失效");
                }
                return;
            }
        } else {
            if (Boolean.parseBoolean(jarCache) && cache.exists() && !FileUtils.isWeekAgo(cache)) {
                LOG.i("echo-load jar jarCache:" + jarUrl);
                if (cache.exists()) {
                    loadJarAsync(cache, new JarLoadCallback() {
                        @Override
                        public void complete(boolean success) {
                            if (success) {
                                callback.success();
                            } else {
                                loadJar(false, spider, callback, retryCount);
                            }
                        }
                    });
                    return;
                }
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                    return;
                }
            }
        }

        boolean isJarInImg = jarUrl.startsWith("img+");
        jarUrl = jarUrl.replace("img+", "");
        LOG.i("echo-load jar start:" + jarUrl);
        final String requestUrl = jarUrl;
        downloadJarAsync(requestUrl, isJarInImg, cache, new JarDownloadCallback() {
            private boolean retryLoad(String reason) {
                if (retryCount >= LOAD_JAR_MAX_RETRY) {
                    return false;
                }
                if (cache.exists() && !cache.delete()) {
                    LOG.i("echo---delete bad jar cache failed:" + cache.getAbsolutePath());
                }
                LOG.i("echo---retry load jar reason:" + reason + " url:" + requestUrl + " retry:" + (retryCount + 1));
                loadJar(false, spider, callback, retryCount + 1);
                return true;
            }

            @Override
            public void complete(File file, String error) {
                if (file != null && file.exists()) {
                    loadJarAsync(file, new JarLoadCallback() {
                        @Override
                        public void complete(boolean success) {
                            if (success) {
                                LOG.i("echo---load-jar-success");
                                callback.success();
                            } else {
                                LOG.e("echo---jar Loader returned false");
                                if (retryLoad("loader_false")) {
                                    return;
                                }
                                callback.error("JAR加载失败");
                            }
                        }
                    });
                    return;
                }
                if (!TextUtils.isEmpty(error)) {
                    LOG.i("echo---jar Request failed: " + error);
                }
                if (cache.exists()) {
                    loadJarAsync(cache, new JarLoadCallback() {
                        @Override
                        public void complete(boolean success) {
                            if (success) {
                                callback.success();
                            } else {
                                if (retryLoad("request_error")) {
                                    return;
                                }
                                callback.error("");
                            }
                        }
                    });
                    return;
                }
                if (retryLoad("request_error")) {
                    return;
                }
                callback.error("");
            }
        });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        parseJson(apiUrl, readConfigFile(f));
    }

    private String readConfigFile(File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = bReader.readLine()) != null) {
            sb.append(s).append("\n");
        }
        bReader.close();
        return sb.toString();
    }

    private boolean switchApiCollectionIfNeeded(String apiUrl, String jsonStr) { //xuameng 多仓
        ArrayList<String> apiLines = parseApiCollection(jsonStr);
        if (apiLines.isEmpty()) {
            return false;
        }
        String firstApi = HistoryHelper.getApiLineUrl(apiLines.get(0));
        if (TextUtils.isEmpty(firstApi) || firstApi.equals(apiUrl)) {
            return false;
        }
        Hawk.put(HawkConfig.API_LINE_LIST, apiLines);
        Hawk.put(HawkConfig.API_LINE_SOURCE, apiUrl);
        Hawk.put(HawkConfig.API_URL, firstApi);
        HistoryHelper.setApiHistory(apiUrl);
        HistoryHelper.markAsApiLineSource(apiUrl);   //xuameng 多仓 永久标记  
        String liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (TextUtils.isEmpty(liveApiUrl) || liveApiUrl.equals(apiUrl)) {
            Hawk.put(HawkConfig.LIVE_API_URL, firstApi);
            HistoryHelper.setLiveApiHistory(firstApi);
        }
        return true;
    }

    private ArrayList<String> parseApiCollection(String jsonStr) { //xuameng 多仓
        ArrayList<String> apiLines = new ArrayList<>();
        try {
            String json = trimJsonObject(jsonStr);
            if (TextUtils.isEmpty(json)) {
                return apiLines;
            }
            JsonObject infoJson = gson.fromJson(json, JsonObject.class);
            if (infoJson == null || infoJson.has("sites") || !infoJson.has("urls") || !infoJson.get("urls").isJsonArray()) {
                return apiLines;
            }
            JsonArray urls = infoJson.get("urls").getAsJsonArray();
            for (JsonElement element : urls) {
                String name = "";
                String url = "";
                if (element.isJsonObject()) {
                    JsonObject item = element.getAsJsonObject();
                    name = DefaultConfig.safeJsonString(item, "name", "");
                    url = DefaultConfig.safeJsonString(item, "url", "");
                    if (TextUtils.isEmpty(url)) {
                        url = DefaultConfig.safeJsonString(item, "api", "");
                    }
                } else if (element.isJsonPrimitive()) {
                    url = element.getAsString();
                }
                if (!TextUtils.isEmpty(url)) {
                    apiLines.add(HistoryHelper.buildApiLine(name, url));
                }
            }
        } catch (Throwable ignored) {
        }
        return apiLines;
    }

    private String trimJsonObject(String content) { //xuameng 多仓
        if (content == null) {
            return "";
        }
        String trimContent = content.trim();
        int start = trimContent.indexOf("{");
        int end = trimContent.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimContent.substring(start, end + 1);
        }
        return trimContent;
    }

    private void resetConfigData() {
        clearSpiderCache();
        currentPlaySourceKey = "";
        sourceBeanList.clear();
        liveChannelGroupList.clear();
        parseBeanList.clear();
        searchSourceBeanList = new ArrayList<>();
        Hawk.put(HawkConfig.LIVE_GROUP_LIST,new JsonArray());
    }

    private void clearApiLinesIfUnmatched(String apiUrl) { //xuameng 多仓
        ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        if (apiLines.isEmpty()) {
            return;
        }
        for (String apiLine : apiLines) {
            if (apiUrl.equals(HistoryHelper.getApiLineUrl(apiLine))) {
                return;
            }
        }
        HistoryHelper.clearApiLineList();
    }

    private static String jarCache = "true";

    private void parseJson(String apiUrl, String jsonStr) {
        resetConfigData();
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        jarCache = DefaultConfig.safeJsonString(infoJson, "jarCache", "true");
        danmaku = DefaultConfig.safeJsonString(infoJson, "danmaku", ""); // xuameng 弹幕
        // wallpaper
        wallpaper = DefaultConfig.safeJsonString(infoJson, "wallpaper", "");
        musicwallpaper = DefaultConfig.safeJsonString(infoJson, "musicwallpaper", "");    // xuameng背景图
        JvhuiWarning = DefaultConfig.safeJsonString(infoJson, "JvhuiWarning", "");  //xuameng警告
        // 自定义站点源
        SourceBean firstSite = null;
        for (JsonElement opt : infoJson.get("sites").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.has("name") ? obj.get("name").getAsString().trim() : siteKey);
            sb.setType(obj.get("type").getAsInt());
            // xuameng api无默认值       sb.setApi(obj.get("api").getAsString().trim());
            sb.setApi(DefaultConfig.safeJsonString(obj, "api", "xuameng"));    // xuameng api可选默认值 xuameng
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            sb.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
            sb.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
            sb.setPlayerType(DefaultConfig.safeJsonInt(obj, "playerType", -1));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            sb.setTimeout(DefaultConfig.safeJsonInt(obj, "timeout", 0));
            sb.setClickSelector(DefaultConfig.safeJsonString(obj, "click", ""));
            sb.setStyle(DefaultConfig.safeJsonString(obj, "style", ""));
            if (firstSite == null) {
                firstSite = sb;
            }
            sourceBeanList.put(siteKey, sb);
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null) {
                assert firstSite != null;
                setSourceBean(firstSite);
            } else {
                setSourceBean(sh);
            }
        }
        // 需要使用vipflag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析
        parseBeanList.clear();
        if (infoJson.has("parses")) {
            JsonArray parses = infoJson.get("parses").getAsJsonArray();
            for (JsonElement opt : parses) {
                JsonObject obj = (JsonObject) opt;
                ParseBean pb = new ParseBean();
                pb.setName(obj.get("name").getAsString().trim());
                pb.setUrl(obj.get("url").getAsString().trim());
                String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
                pb.setExt(ext);
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                parseBeanList.add(pb);
            }
            if (!parseBeanList.isEmpty()) {
                addSuperParse();
            }
        }
        // 获取默认解
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse)) {
                for (ParseBean pb : parseBeanList) {
                    if (pb.getName().equals(defaultParse)) {
                        setDefaultParse(pb);
                    }
                }
            }
            if (mDefaultParse == null) {
                setDefaultParse(parseBeanList.get(0));
            }
        }

        // 直播源
        String liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (liveApiUrl.isEmpty() || apiUrl.equals(liveApiUrl)) {
            initLiveSettings();
            LOG.i("echo-load-config_live");
            if (infoJson.has("lives")) {
                JsonArray livesGroups = infoJson.get("lives").getAsJsonArray();
                if (livesGroups.size() > 0) {
                    int liveGroupIndex = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
                    if (liveGroupIndex > livesGroups.size() - 1) {           // xuameng 修复BUG
                        Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
                        Hawk.put(HawkConfig.LIVE_GROUP_LIST, livesGroups);
                        // 刷新源
                        try {
                            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
                            for (int i = 0; i < livesGroups.size(); i++) {
                                JsonObject jsonObject = livesGroups.get(i).getAsJsonObject();
                                String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "聚汇直播";
                                if (name == null || name.isEmpty()) {
                                    name = "聚汇直播";
                                }
                                LiveSettingItem liveSettingItem = new LiveSettingItem();
                                liveSettingItem.setItemIndex(i);
                                liveSettingItem.setItemName(name);
                                liveSettingItemList.add(liveSettingItem);
                            }
                            liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
                        } catch (Exception e) {
                            // 任何可能抛出的异常
                            e.printStackTrace();
                        }
                        int liveGroupIndexXu = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
                        JsonObject livesObjXu = livesGroups.get(liveGroupIndexXu).getAsJsonObject();
                        loadLiveApi(livesObjXu);
                    } else {
                        Hawk.put(HawkConfig.LIVE_GROUP_LIST, livesGroups);
                        // 刷新源
                        try {
                            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
                            for (int i = 0; i < livesGroups.size(); i++) {
                                JsonObject jsonObject = livesGroups.get(i).getAsJsonObject();
                                String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "聚汇直播";
                                if (name == null || name.isEmpty()) {
                                    name = "聚汇直播";
                                }
                                LiveSettingItem liveSettingItem = new LiveSettingItem();
                                liveSettingItem.setItemIndex(i);
                                liveSettingItem.setItemName(name);
                                liveSettingItemList.add(liveSettingItem);
                            }
                            liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
                        } catch (Exception e) {
                            // 任何可能抛出的异常
                            e.printStackTrace();
                        }
                        JsonObject livesObj = livesGroups.get(liveGroupIndex).getAsJsonObject();
                        loadLiveApi(livesObj);
                    }
                } else {
                    initLiveSettings();
                    Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                    Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
                }
            } else {
                initLiveSettings();
                Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
            }
        }

        myHosts = new HashMap<>();
        if (infoJson.has("hosts")) {
            JsonArray hostsArray = infoJson.getAsJsonArray("hosts");
            for (int i = 0; i < hostsArray.size(); i++) {
                String entry = hostsArray.get(i).getAsString();
                String[] parts = entry.split("=", 2); // 只分割一次，防止 value 中包含 =
                if (parts.length == 2) {
                    myHosts.put(parts[0], parts[1]);
                }
            }
        }

        loadProxyRules(infoJson);

        // video parse rule for host
        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule();
            for (JsonElement oneHostRule : infoJson.getAsJsonArray("rules")) {
                JsonObject obj = (JsonObject) oneHostRule;
                // 单域名规则
                if (obj.has("host")) {
                    String host = obj.get("host").getAsString();
                    if (obj.has("rule")) {
                        JsonArray ruleJsonArr = obj.getAsJsonArray("rule");
                        ArrayList<String> rule = new ArrayList<>();
                        for (JsonElement one : ruleJsonArr) {
                            String oneRule = one.getAsString();
                            rule.add(oneRule);
                        }
                        if (rule.size() > 0) {
                            VideoParseRuler.addHostRule(host, rule);
                        }
                    }
                    if (obj.has("filter")) {
                        JsonArray filterJsonArr = obj.getAsJsonArray("filter");
                        ArrayList<String> filter = new ArrayList<>();
                        for (JsonElement one : filterJsonArr) {
                            String oneFilter = one.getAsString();
                            filter.add(oneFilter);
                        }
                        if (filter.size() > 0) {
                            VideoParseRuler.addHostFilter(host, filter);
                        }
                    }
                }
                // 多域名规则
                if (obj.has("hosts") && obj.has("regex")) {
                    ArrayList<String> rule = new ArrayList<>();
                    ArrayList<String> ads = new ArrayList<>();
                    JsonArray regexArray = obj.getAsJsonArray("regex");
                    for (JsonElement one : regexArray) {
                        String regex = one.getAsString();
                        if (M3u8.isAd(regex)) {
                            ads.add(regex);
                        } else {
                            rule.add(regex);
                        }
                    }
                    JsonArray array = obj.getAsJsonArray("hosts");
                    for (JsonElement one : array) {
                        String host = one.getAsString();
                        VideoParseRuler.addHostRule(host, rule);
                        VideoParseRuler.addHostRegex(host, ads);
                    }
                }
                // 单域名脚本 click
                if (obj.has("hosts") && obj.has("script")) {
                    ArrayList<String> scripts = new ArrayList<>();
                    JsonArray scriptArray = obj.getAsJsonArray("script");
                    for (JsonElement one : scriptArray) {
                        String script = one.getAsString();
                        scripts.add(script);
                    }
                    JsonArray array = obj.getAsJsonArray("hosts");
                    for (JsonElement one : array) {
                        String host = one.getAsString();
                        VideoParseRuler.addHostScript(host, scripts);
                    }
                }
            }
        }

        if (infoJson.has("doh")) {
            String dohJson = infoJson.getAsJsonArray("doh").toString();
            if (!Hawk.get(HawkConfig.DOH_JSON, "").equals(dohJson)) {
                Hawk.put(HawkConfig.DOH_URL, 0);
                Hawk.put(HawkConfig.DOH_JSON, dohJson);
            }
        } else {
            Hawk.put(HawkConfig.DOH_JSON, "");
        }
        OkGoHelper.setDnsList();
        LOG.i("echo-api-config-----------load");
        // 追加广告
        if (infoJson.has("ads")) {
            for (JsonElement host : infoJson.getAsJsonArray("ads")) {
                if (!AdBlocker.hasHost(host.getAsString())) {
                    AdBlocker.addAdHost(host.getAsString());
                }
            }
        }
    }

    private void loadDefaultConfig() {
        String defaultIJKADS="{\"ijk\":[{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"0\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"软解码\"},{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"1\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"硬解码\"}],\"ads\":[\"mimg.0c1q0l.cn\",\"www.googletagmanager.com\",\"www.google-analytics.com\",\"mc.usihnbcq.cn\",\"mg.g1mm3d.cn\",\"mscs.svaeuzh.cn\",\"cnzz.hhttm.top\",\"tp.vinuxhome.com\",\"cnzz.mmstat.com\",\"www.baihuillq.com\",\"s23.cnzz.com\",\"z3.cnzz.com\",\"c.cnzz.com\",\"stj.v1vo.top\",\"z12.cnzz.com\",\"img.mosflower.cn\",\"tips.gamevvip.com\",\"ehwe.yhdtns.com\",\"xdn.cqqc3.com\",\"www.jixunkyy.cn\",\"sp.chemacid.cn\",\"hm.baidu.com\",\"s9.cnzz.com\",\"z6.cnzz.com\",\"um.cavuc.com\",\"mav.mavuz.com\",\"wofwk.aoidf3.com\",\"z5.cnzz.com\",\"xc.hubeijieshikj.cn\",\"tj.tianwenhu.com\",\"xg.gars57.cn\",\"k.jinxiuzhilv.com\",\"cdn.bootcss.com\",\"ppl.xunzhuo123.com\",\"xomk.jiangjunmh.top\",\"img.xunzhuo123.com\",\"z1.cnzz.com\",\"s13.cnzz.com\",\"xg.huataisangao.cn\",\"z7.cnzz.com\",\"xg.huataisangao.cn\",\"z2.cnzz.com\",\"s96.cnzz.com\",\"q11.cnzz.com\",\"thy.dacedsfa.cn\",\"xg.whsbpw.cn\",\"s19.cnzz.com\",\"z8.cnzz.com\",\"s4.cnzz.com\",\"f5w.as12df.top\",\"ae01.alicdn.com\",\"www.92424.cn\",\"k.wudejia.com\",\"vivovip.mmszxc.top\",\"qiu.xixiqiu.com\",\"cdnjs.hnfenxun.com\",\"cms.qdwght.com\"]}";
        JsonObject defaultJson = gson.fromJson(defaultIJKADS, JsonObject.class);
        // 广告
        if (AdBlocker.isEmpty()) {
            // 默认广告
            for (JsonElement host : defaultJson.getAsJsonArray("ads")) {
                AdBlocker.addAdHost(host.getAsString());
            }
        }
        // IJK
        if (ijkCodes == null) {
            ijkCodes = new ArrayList<>();
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
            JsonArray ijkJsonArray = defaultJson.get("ijk").getAsJsonArray();
            for (JsonElement opt : ijkJsonArray) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                ijkCodes.add(codec);
            }
            if (!foundOldSelect && ijkCodes.size() > 0) {
                ijkCodes.get(0).selected(true);
            }
        }
        LOG.i("echo-default-config-----------load");
    }

    private void parseLiveConfigContent(String apiUrl, File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = bReader.readLine()) != null) {
            sb.append(s).append("\n");
        }
        bReader.close();
        parseLiveConfigContent(apiUrl, sb.toString());
    }

    private void parseLiveConfigContent(String apiUrl, String content) {
        if (isLiveJsonContent(content)) {
            parseLiveJson(apiUrl, content);
        } else {
            parseLiveText(apiUrl, content);
        }
    }

    private boolean isLiveJsonContent(String content) {
        if (content == null) {
            return false;
        }
        String text = content.trim();
        if (text.startsWith("\ufeff")) {
            text = text.substring(1).trim();
        }
        return text.startsWith("{");
    }

    private void parseLiveText(String apiUrl, String content) {
        liveChannelGroupList.clear();
        liveSpider = "";
        currentLiveSpider = "";

        initLiveSettings();
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        Hawk.put(HawkConfig.EPG_URL, extractLiveTextEpg(content));
        Hawk.put(HawkConfig.LIVE_PLAY_TYPE, Hawk.get(HawkConfig.PLAY_TYPE, 0));
        Hawk.put(HawkConfig.LIVE_WEB_HEADER, null);
        JsonArray livesArray = TxtSubscribe.parseToJsonArray(content);
        loadLives(livesArray);
        LOG.i("echo-live-text-config-----------load:" + apiUrl);
    }

    private String extractLiveTextEpg(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("\ufeff")) {
                line = line.substring(1).trim();
            }
            if (!line.startsWith("#EXTM3U")) {
                continue;
            }
            String epg = extractQuotedAttr(line, "x-tvg-url");
            if (epg.isEmpty()) {
                epg = extractQuotedAttr(line, "tvg-url");
            }
            if (epg.isEmpty()) {
                epg = extractQuotedAttr(line, "url-tvg");
            }
            return epg;
        }
        return "";
    }

    private String extractQuotedAttr(String line, String key) {
        String token = key + "=\"";
        int start = line.indexOf(token);
        if (start < 0) {
            return "";
        }
        start += token.length();
        int end = line.indexOf("\"", start);
        if (end < 0) {
            return "";
        }
        return line.substring(start, end).trim();
    }

    private String liveSpider = "";

    private void parseLiveJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
        initLiveSettings();
        // 直播源
        if (infoJson.has("lives")) {
            JsonArray livesGroups = infoJson.get("lives").getAsJsonArray();
            if (livesGroups.size() > 0) {
                int liveGroupIndex = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
                if (liveGroupIndex > livesGroups.size() - 1) {           // xuameng 修复BUG
                    Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
                    Hawk.put(HawkConfig.LIVE_GROUP_LIST, livesGroups);
                    // 刷新源
                    try {
                        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
                        for (int i = 0; i < livesGroups.size(); i++) {
                            JsonObject jsonObject = livesGroups.get(i).getAsJsonObject();
                            String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "聚汇直播";
                            if (name == null || name.isEmpty()) {
                                name = "聚汇直播";
                            }
                            LiveSettingItem liveSettingItem = new LiveSettingItem();
                            liveSettingItem.setItemIndex(i);
                            liveSettingItem.setItemName(name);
                            liveSettingItemList.add(liveSettingItem);
                        }
                        liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
                    } catch (Exception e) {
                        // 任何可能抛出的异常
                        e.printStackTrace();
                    }
                    int liveGroupIndexXu = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
                    JsonObject livesObjXu = livesGroups.get(liveGroupIndexXu).getAsJsonObject();
                    loadLiveApi(livesObjXu);
                } else {
                    Hawk.put(HawkConfig.LIVE_GROUP_LIST, livesGroups);
                    // 刷新源
                    try {
                        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
                        for (int i = 0; i < livesGroups.size(); i++) {
                            JsonObject jsonObject = livesGroups.get(i).getAsJsonObject();
                            String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "聚汇直播";
                            if (name == null || name.isEmpty()) {
                                name = "聚汇直播";
                            }
                            LiveSettingItem liveSettingItem = new LiveSettingItem();
                            liveSettingItem.setItemIndex(i);
                            liveSettingItem.setItemName(name);
                            liveSettingItemList.add(liveSettingItem);
                        }
                        liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
                    } catch (Exception e) {
                        // 任何可能抛出的异常
                        e.printStackTrace();
                    }
                    JsonObject livesObj = livesGroups.get(liveGroupIndex).getAsJsonObject();
                    loadLiveApi(livesObj);
                }
            } else {
                initLiveSettings();
                Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
            }
        } else {
            initLiveSettings();
            Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
            Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
        }

        myHosts = new HashMap<>();
        if (infoJson.has("hosts")) {
            JsonArray hostsArray = infoJson.getAsJsonArray("hosts");
            for (int i = 0; i < hostsArray.size(); i++) {
                String entry = hostsArray.get(i).getAsString();
                String[] parts = entry.split("=", 2); // 只分割一次，防止 value 中包含 =
                if (parts.length == 2) {
                    myHosts.put(parts[0], parts[1]);
                }
            }
        }
        LOG.i("echo-api-live-config-----------load");
    }

    private final List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();
    private void initLiveSettings() {
		ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "多源切换", "渲染方式", "直播音柱", "退出直播"));  //xuameng 换源
        ArrayList < ArrayList < String >> itemsArrayList = new ArrayList < > ();
        ArrayList < String > sourceItems = new ArrayList < > ();
        ArrayList < String > scaleItems = new ArrayList < > (Arrays.asList("默认比例", "16:9比例", "4:3 比例", "填充比例", "原始比例", "裁剪比例"));
        ArrayList < String > playerDecoderItems = new ArrayList < > (Arrays.asList("系统解码", "IJK  硬解", "IJK  软解", "EXO硬解", "EXO软解"));   //xuameng增加exo软硬解
        ArrayList < String > timeoutItems = new ArrayList < > (Arrays.asList("超时05秒", "超时10秒", "超时15秒", "超时20秒", "超时25秒", "超时30秒"));
        ArrayList < String > personalSettingItems = new ArrayList < > (Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        ArrayList < String > yumItems = new ArrayList<>(Arrays.asList("聚汇直播"));   //xuameng新增 换源
        ArrayList < String > PlayrenderSettingItems = new ArrayList < > (Arrays.asList("Texture渲染", "Surface渲染"));   //xuameng渲染方式
        ArrayList < String > LiveMusicAnimationItems = new ArrayList < > (Arrays.asList("音柱开启", "音柱关闭"));   //xuameng音柱动画
        ArrayList < String > ExitSettingItems = new ArrayList < > (Arrays.asList("确认退出"));   //xuameng退出直播

        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);
        itemsArrayList.add(yumItems);
        itemsArrayList.add(PlayrenderSettingItems);   //xuameng渲染方式
        itemsArrayList.add(LiveMusicAnimationItems);   //xuameng音柱动画
        itemsArrayList.add(ExitSettingItems);   //xuameng退出直播

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
    }

    public List<LiveSettingGroup> getLiveSettingGroupList() {
        return liveSettingGroupList;
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        // ========== xuameng "我的收藏"频道 ==========
        // 收藏频道组（始终放在第一个位置）
        LiveChannelGroup favoriteGroup = LiveChannelItem.createFavoriteChannelGroup();
        favoriteGroup.setGroupIndex(groupIndex++);
        liveChannelGroupList.add(favoriteGroup);
        // ========== xuameng "我的收藏" ==========
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1) {
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            } else {
                liveChannelGroup.setGroupPassword("");
            }
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelLogo(DefaultConfig.safeJsonString(obj, "logo", ""));
                liveChannelItem.setChannelEpg(DefaultConfig.safeJsonString(obj, "epg", ""));
                liveChannelItem.setChannelUa(DefaultConfig.safeJsonString(obj, "ua", ""));
                liveChannelItem.setChannelClick(DefaultConfig.safeJsonString(obj, "click", ""));
                liveChannelItem.setChannelFormat(DefaultConfig.safeJsonString(obj, "format", ""));
                liveChannelItem.setChannelOrigin(DefaultConfig.safeJsonString(obj, "origin", ""));
                liveChannelItem.setChannelReferer(DefaultConfig.safeJsonString(obj, "referer", ""));
                liveChannelItem.setChannelTvgId(DefaultConfig.safeJsonString(obj, "tvg-id", ""));
                liveChannelItem.setChannelTvgName(DefaultConfig.safeJsonString(obj, "tvg-name", ""));
                if (obj.has("parse")) {
                    try {
                        liveChannelItem.setChannelParse(obj.get("parse").getAsInt());
                    } catch (Throwable ignored) {
                    }
                }
                if (obj.has("catchup")) {
                    JsonObject catchupObj = new JsonObject();
                    if (obj.get("catchup").isJsonObject()) {
                        catchupObj = obj.getAsJsonObject("catchup");
                    } else {
                        catchupObj.addProperty("type", obj.get("catchup").getAsString());
                        if (obj.has("catchup-source")) {
                            catchupObj.addProperty("source", obj.get("catchup-source").getAsString());
                        }
                        if (obj.has("catchup-replace")) {
                            catchupObj.addProperty("replace", obj.get("catchup-replace").getAsString());
                        }
                    }
                    liveChannelItem.setChannelCatchup(catchupObj);
                }
                if (obj.has("header") && obj.get("header").isJsonObject()) {
                    JsonObject headerObj = obj.getAsJsonObject("header");
                    HashMap<String, String> channelHeader = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : headerObj.entrySet()) {
                        channelHeader.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    liveChannelItem.setChannelHeader(channelHeader);
                }
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1) {
                        sourceNames.add(splitText[1]);
                    } else {
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    }
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                if (mergeLiveChannel(liveChannelGroup.getLiveChannels(), liveChannelItem)) {
                    liveChannelItem.setChannelIndex(channelIndex++);
                    liveChannelItem.setChannelNum(++channelNum);
                }
            }
            liveChannelGroupList.add(liveChannelGroup);
        }
    }

    private boolean mergeLiveChannel(ArrayList<LiveChannelItem> channelItems, LiveChannelItem newItem) {
        LiveChannelItem oldItem = findLiveChannel(channelItems, newItem.getChannelName());
        if (oldItem == null) {
            channelItems.add(newItem);
            return true;
        }
        mergeLiveChannelUrls(oldItem, newItem);
        return false;
    }

    private LiveChannelItem findLiveChannel(ArrayList<LiveChannelItem> channelItems, String channelName) {
        for (LiveChannelItem item : channelItems) {
            if (channelName != null && channelName.equals(item.getChannelName())) {
                return item;
            }
        }
        return null;
    }

    private void mergeLiveChannelUrls(LiveChannelItem oldItem, LiveChannelItem newItem) {
        ArrayList<String> oldUrls = oldItem.getChannelUrls();
        ArrayList<String> oldSourceNames = oldItem.getChannelSourceNames();
        if (oldUrls == null) {
            oldUrls = new ArrayList<>();
            oldItem.setChannelUrls(oldUrls);
        }
        if (oldSourceNames == null) {
            oldSourceNames = new ArrayList<>();
            oldItem.setChannelSourceNames(oldSourceNames);
        }
        while (oldSourceNames.size() < oldUrls.size()) {
            oldSourceNames.add("源" + Integer.toString(oldSourceNames.size() + 1));
        }
        ArrayList<String> newUrls = newItem.getChannelUrls();
        ArrayList<String> newSourceNames = newItem.getChannelSourceNames();
        if (newUrls == null) {
            return;
        }
        for (int i = 0; i < newUrls.size(); i++) {
            String url = newUrls.get(i);
            if (oldUrls.contains(url)) {
                continue;
            }
            oldUrls.add(url);
            if (newSourceNames != null && i < newSourceNames.size()) {
                oldSourceNames.add(newSourceNames.get(i));
            } else {
                oldSourceNames.add("源" + Integer.toString(oldSourceNames.size() + 1));
            }
        }
        oldItem.setChannelUrls(oldUrls);
        oldItem.setChannelSourceNames(oldSourceNames);
    }

    public void loadLiveApi(JsonObject livesObj) {
        try {
            LOG.i("echo-loadLiveApi");
            liveChannelGroupList.clear();
            currentLiveSpider = "";

            String lives = livesObj.toString();
            int index = lives.indexOf("proxy://");
            String url;
            if (index != -1) {
                int endIndex = lives.lastIndexOf("\"");
                url = lives.substring(index, endIndex);
                url = DefaultConfig.checkReplaceProxy(url);
                String extUrl = Uri.parse(url).getQueryParameter("ext");
                if (extUrl != null && !extUrl.isEmpty()) {
                    String extUrlFix;
                    if (extUrl.startsWith("http") || extUrl.startsWith("clan://")) {
                        extUrlFix = extUrl;
                    } else {
                        extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                    }
                    extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                    url = url.replace(extUrl, extUrlFix);
                }
            } else {
                String api = livesObj.has("api") ? livesObj.get("api").getAsString().trim() : "";
                String type = livesObj.has("type") ? livesObj.get("type").getAsString() : (isLiveSpiderApi(api) ? "3" : "0");
                if (type.equals("0") || type.equals("3")) {
                    url = livesObj.has("url") ? livesObj.get("url").getAsString() : "";
                    if (url.isEmpty()) {
                        url = api;
                    }
                    LOG.i("echo-liveurl" + url);
                    if (!url.startsWith("http://127.0.0.1")) {
                        if (url.startsWith("http")) {
                            url = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        }
                        url = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + url;
                    }
                    if (type.equals("3")) {
                        String jarUrl = livesObj.has("jar") ? livesObj.get("jar").getAsString().trim() : "";
                        LOG.i("echo-liveApi1" + api);
                        if (api.contains(".js")) {
                            LOG.i("echo-jsLoader.getSpider");
                            String ext = "";
                            if (livesObj.has("ext") && (livesObj.get("ext").isJsonObject() || livesObj.get("ext").isJsonArray())) {
                                ext = livesObj.get("ext").toString();
                            } else {
                                ext = DefaultConfig.safeJsonString(livesObj, "ext", "");
                            }
                            currentLiveSpider = api;
                            jsLoader.getSpider(MD5.string2MD5(api), api, ext, jarUrl);
                        }
                        if (!jarUrl.isEmpty() && !isLiveSpiderApi(api)) {
                            jarLoader.loadLiveJar(jarUrl);
                            if (TextUtils.isEmpty(currentLiveSpider)) {
                                currentLiveSpider = jarUrl;
                            }
                        } else if (!liveSpider.isEmpty() && !isLiveSpiderApi(api)) {
                            jarLoader.loadLiveJar(liveSpider);
                            if (TextUtils.isEmpty(currentLiveSpider)) {
                                currentLiveSpider = liveSpider;
                            }
                        }
                    }
                } else {
                    liveChannelGroupList.clear();
                    return;
                }
            }
            // epg
            if (livesObj.has("epg")) {
                String epg = livesObj.get("epg").getAsString();
                Hawk.put(HawkConfig.EPG_URL, epg);
            } else {
                Hawk.put(HawkConfig.EPG_URL, "");
            }
            // 直播
            if (livesObj.has("playerType")) {
                String livePlayType = livesObj.get("playerType").getAsString();
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE, livePlayType);
                HawkConfig.intLIVEPLAYTYPE = true;   // xuameng是否直播默认播放
            } else {
                HawkConfig.intLIVEPLAYTYPE = false;   // xuameng是否直播默认播放
            }
            // UA
            if (livesObj.has("timeout")) {
                int timeout = Math.max(5, Math.min(30, livesObj.get("timeout").getAsInt()));
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, (timeout + 4) / 5 - 1);
            }
            // xuameng UA信息
            if (livesObj.has("header")) {
                JsonObject headerObj = livesObj.getAsJsonObject("header");
                HashMap<String, String> liveHeader = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : headerObj.entrySet()) {
                    liveHeader.put(entry.getKey(), entry.getValue().getAsString());
                }
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader);
            } else if (livesObj.has("ua")) {
                String ua = livesObj.get("ua").getAsString();
                HashMap<String, String> liveHeader = new HashMap<>();
                liveHeader.put("User-Agent", ua);
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader);
            } else {
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, null);
            }
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setGroupName(url);
            liveChannelGroupList.clear();
            liveChannelGroupList.add(liveChannelGroup);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private String currentLiveSpider;

    public void setLiveJar(String liveJar) {
        if (liveJar.contains(".js")) {
            jsLoader.getSpider(MD5.string2MD5(liveJar), liveJar, "", "");
        } else {
            String jarUrl = !liveJar.isEmpty() ? liveJar : liveSpider;
            jarLoader.setRecentJarKey(MD5.string2MD5(jarUrl));
        }
        currentLiveSpider = liveJar;
    }

    public String getSpider() {
        return spider;
    }

    public String getDanmaku() {  // xuameng 弹幕
        return danmaku == null ? "" : danmaku;
    }

    public Spider getCSP(SourceBean sourceBean) {
        if (sourceBean.getApi().endsWith(".js") || sourceBean.getApi().contains(".js?")) {
            return jsLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
        } else {
            return jarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
        }
    }

    public void warmSearchSpiders() {  //xuameng搜索预热
        final ArrayList<SourceBean> sources = new ArrayList<>(sourceBeanList.values());
        configLoadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                LOG.i("echo-warm-spider start");
                int eligibleCount = 0;
                for (SourceBean source : sources) {
                    if (source == null || source.getType() != 3 || !source.isSearchable()) continue;
                    if (eligibleCount >= 20) break;
                    eligibleCount++;
                    String warmKey = source.getKey() + "|" + source.getApi() + "|" + source.getJar() + "|" + source.getExt();
                    synchronized (warmedSearchSpiderKeys) {
                        if (warmedSearchSpiderKeys.contains(warmKey)) continue;
                        warmedSearchSpiderKeys.add(warmKey);
                    }
                    try {
                        LOG.i("echo-warm-spider load:" + warmKey);
                        getCSP(source);
                    } catch (Throwable th) {
                        LOG.e("echo-warm-search-spider-error " + source.getKey() + ":" + th.getMessage());
                    }
                }
            }
        });
    }

    public Spider getJsCSP(String url) {
        currentLiveSpider = url;
        return jsLoader.getSpider(MD5.string2MD5(url), url, "", "");
    }

    public Spider getLiveCSP(String url) {
        return getJsCSP(url);
    }

    public void searchDanmuUi(String name, String episode, boolean longClick) {
        danmuSearchExecutor.execute(() -> {
            try {
                jarLoader.searchDanmuUi(name, episode, longClick);
            } catch (Throwable th) {
                LOG.e("ApiConfig searchDanmuUi error: " + th.getMessage());
                th.printStackTrace();
            }
        });
    }

    public boolean hasDanmuSearchUi() {
        return jarLoader.hasDanmuSearchUi();
    }

    public int getLiveConnectTimeoutSeconds() {
        return (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5;
    }

    private boolean isLiveSpiderApi(String api) {
        return api.contains(".js");
    }

    public Object[] proxyLocal(Map<String, String> param) {
        SourceBean source = getCurrentProxySource(param);
        String api = source.getApi();

        String siteKey = param.get("siteKey");
        String action = param.get("do");

        boolean isJs = "js".equals(action);
        boolean isLive = Hawk.get(HawkConfig.PLAYER_IS_LIVE, false);
        boolean isApiJs = api.contains(".js");

        boolean canUseType3 = !TextUtils.isEmpty(siteKey)
                && source.getType() == 3
                && !isJs
                && !isLive
                && !isApiJs;

        if (canUseType3) {
            try {
                Spider spider = getCSP(source);

                Object[] result = spider.proxy(param);
                if (result != null) {
                    return result;
                }

                result = jarLoader.proxyInvoke(param);
                if (result != null) {
                    return result;
                }

                result = proxyDirect(param);
                if (result != null) {
                    return result;
                }

                return null;
            } catch (Throwable th) {
                LOG.e("echo-proxy siteKey error: " + th.getMessage());
                return null;
            }
        }

        if (isJs) {
            return jsLoader.proxyInvoke(param);
        }

        if (isLive) {
            String liveApi = currentLiveSpider != null ? currentLiveSpider : "";

            if (liveApi.contains(".js")) {
                return jsLoader.proxyInvoke(param);
            }
            return jarLoader.proxyInvoke(param);
        }

        return jarLoader.proxyInvoke(param);
    }

    private Object[] proxyDirect(Map<String, String> param) {
        try {
            String url = param.get("url");
            if (TextUtils.isEmpty(url)) {
                return null;
            }
            url = URLDecoder.decode(url, "UTF-8");
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return null;
            }
            if (!DefaultConfig.isVideoFormat(url)) {
                return null;
            }
            if (url.contains(".m3u8")) {
                param.put("url", url);
                param.put("go", "live");
                param.put("type", "m3u8");
                return Proxy.itv(param);
            }
            return null;
        } catch (Throwable th) {
            LOG.e("echo-proxy direct fallback error: " + th.getMessage());
            return null;
        }
    }

    private SourceBean getCurrentProxySource(Map<String, String> param) {
        String siteKey = param.get("siteKey");
        if (TextUtils.isEmpty(siteKey)) {
            siteKey = currentPlaySourceKey;
            if (!TextUtils.isEmpty(siteKey)) {
                param.put("siteKey", siteKey);
            }
        }
        SourceBean sourceBean = TextUtils.isEmpty(siteKey) ? null : getSource(siteKey);
        return sourceBean == null ? ApiConfig.get().getHomeSourceBean() : sourceBean;
    }

    public void setCurrentPlaySourceKey(String sourceKey) {
        currentPlaySourceKey = sourceKey == null ? "" : sourceKey;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void error(String msg);

        void notice(String msg);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key)) {
            return null;
        }
        return sourceBeanList.get(key);
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null) {
            this.mDefaultParse.setDefault(false);
        }
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }

    public List<SourceBean> getSwitchSourceBeanList() {
        List<SourceBean> filteredList = new ArrayList<>();
        for (SourceBean bean : sourceBeanList.values()) {
            filteredList.add(bean);
        }
        return filteredList;
    }

    private List<SourceBean> searchSourceBeanList;

    public List<SourceBean> getSearchSourceBeanList() {
        if (searchSourceBeanList.isEmpty()) {
            LOG.i("echo-第一次getSearchSourceBeanList");
            searchSourceBeanList = new ArrayList<>();
            for (SourceBean bean : sourceBeanList.values()) {
                if (bean.isSearchable()) {
                    searchSourceBeanList.add(bean);
                }
            }
        }
        return searchSourceBeanList;
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        return vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name)) {
                return code;
            }
        }
        return ijkCodes.get(0);
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://localhost/", fix).replace("file://", fix);
    }

    String fixContentPath(String url, String content) {
        if (content.contains("\"./") || content.contains("\"../")) {
            url = url.replace("file://", "clan://localhost/");
            if (!url.startsWith("http") && !url.startsWith("clan://")) {
                url = "http://" + url;
            }
            if (url.startsWith("clan://")) {
                url = clanToAddress(url);
            }
            String base = url.substring(0, url.lastIndexOf("/") + 1);
            String parent = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            int parentEnd = parent.lastIndexOf("/");
            if (parentEnd >= 0) {
                parent = parent.substring(0, parentEnd + 1);
            }
            content = content.replace("../", parent);
            content = content.replace("./", base);
        }
        return content;
    }

    public Map<String, String> getMyHost() {
        return myHosts;
    }

    private void loadProxyRules(JsonObject infoJson) {
        if (!infoJson.has("proxy")) {
            OkGoHelper.setProxyList(null);
            return;
        }
        try {
            OkGoHelper.setProxyList(ProxyRule.arrayFrom(infoJson.get("proxy")));
        } catch (Throwable th) {
            th.printStackTrace();
            OkGoHelper.setProxyList(null);
        }
    }

    public void clearJarLoader() {
        jarLoader.clear();
    }

    private void addSuperParse() {
        ParseBean superPb = new ParseBean();
        superPb.setName("聚汇超解");
        superPb.setUrl("SuperParse");
        superPb.setExt("");
        superPb.setType(4);
        parseBeanList.add(0, superPb);
    }

    public void LoadapiUrlXu() {    // xuameng 若接口url为空，将API_URL重置为接口
        String apiUrlXu = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrlXu == null || apiUrlXu.isEmpty() || apiUrlXu.length() == 0) {
            if (Hawk.contains(HawkConfig.API_URL)) {
                Hawk.delete(HawkConfig.API_URL); // 完全删除
            }
        }
    }

    public void clearLoader() {
        jarLoader.clear();
        jsLoader.clear();
        synchronized (warmedSearchSpiderKeys) {
            warmedSearchSpiderKeys.clear();
        }
    }

    public void clearSpiderCache() {
        currentLiveSpider = "";
        clearLoader();
    }

	 /**xuameng
     * 创建默认设置组（覆盖原业务中所有9个组，接口失败时兜底）
     */
    public List<LiveSettingGroup> createDefaultLiveSettingGroupList() {
        List<LiveSettingGroup> defaultList = new ArrayList<>();

        // ==================== 1. 线路选择（索引0） ====================
        LiveSettingGroup lineGroup = new LiveSettingGroup();
        lineGroup.setGroupIndex(0);
        lineGroup.setGroupName("线路选择");
        lineGroup.setLiveSettingItems(new ArrayList<>()); // 已经是ArrayList，无需转换

        // ==================== 2. 画面比例（索引1） ====================
        LiveSettingGroup scaleGroup = new LiveSettingGroup();
        scaleGroup.setGroupIndex(1);
        scaleGroup.setGroupName("画面比例");
        List<LiveSettingItem> scaleItems = new ArrayList<>();
        scaleItems.add(createLiveSettingItem(0, "默认比例"));
        scaleItems.add(createLiveSettingItem(1, "16:9比例"));
        scaleItems.add(createLiveSettingItem(2, "4:3 比例"));
        scaleItems.add(createLiveSettingItem(3, "填充比例"));
        scaleItems.add(createLiveSettingItem(4, "原始比例"));
        scaleItems.add(createLiveSettingItem(5, "裁剪比例"));
        // 关键修改：强制转换为ArrayList
        scaleGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) scaleItems);

        // ==================== 3. 播放解码（索引2） ====================
        LiveSettingGroup decoderGroup = new LiveSettingGroup();
        decoderGroup.setGroupIndex(2);
        decoderGroup.setGroupName("播放解码");
        List<LiveSettingItem> decoderItems = new ArrayList<>();
        decoderItems.add(createLiveSettingItem(0, "系统解码"));
        decoderItems.add(createLiveSettingItem(1, "IJK  硬解"));
        decoderItems.add(createLiveSettingItem(2, "IJK  软解"));
        decoderItems.add(createLiveSettingItem(3, "EXO硬解"));
        decoderItems.add(createLiveSettingItem(4, "EXO软解"));
        // 关键修改：强制转换
        decoderGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) decoderItems);

        // ==================== 4. 超时换源（索引3） ====================
        LiveSettingGroup timeoutGroup = new LiveSettingGroup();
        timeoutGroup.setGroupIndex(3);
        timeoutGroup.setGroupName("超时换源");
        List<LiveSettingItem> timeoutItems = new ArrayList<>();
        timeoutItems.add(createLiveSettingItem(0, "超时05秒"));
        timeoutItems.add(createLiveSettingItem(1, "超时10秒"));
        timeoutItems.add(createLiveSettingItem(2, "超时15秒"));
        timeoutItems.add(createLiveSettingItem(3, "超时20秒"));
        timeoutItems.add(createLiveSettingItem(4, "超时25秒"));
        timeoutItems.add(createLiveSettingItem(5, "超时30秒"));
        // 关键修改：强制转换
        timeoutGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) timeoutItems);

        // ==================== 5. 偏好设置（索引4） ====================
        LiveSettingGroup personalGroup = new LiveSettingGroup();
        personalGroup.setGroupIndex(4);
        personalGroup.setGroupName("偏好设置");
        List<LiveSettingItem> personalItems = new ArrayList<>();
        personalItems.add(createLiveSettingItem(0, "显示时间"));
        personalItems.add(createLiveSettingItem(1, "显示网速"));
        personalItems.add(createLiveSettingItem(2, "换台反转"));
        personalItems.add(createLiveSettingItem(3, "跨选分类"));
        // 关键修改：强制转换
        personalGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) personalItems);

        // ==================== 6. 多源切换（索引5） ====================
        LiveSettingGroup yumGroup = new LiveSettingGroup();
        yumGroup.setGroupIndex(5);
        yumGroup.setGroupName("多源切换");
        List<LiveSettingItem> yumItems = new ArrayList<>();
        yumItems.add(createLiveSettingItem(0, "聚汇直播"));
        // 关键修改：强制转换
        yumGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) yumItems);

        // ==================== 7. 渲染方式（索引6） ====================
        LiveSettingGroup renderGroup = new LiveSettingGroup();
        renderGroup.setGroupIndex(6);
        renderGroup.setGroupName("渲染方式");
        List<LiveSettingItem> renderItems = new ArrayList<>();
        renderItems.add(createLiveSettingItem(0, "Texture渲染"));
        renderItems.add(createLiveSettingItem(1, "Surface渲染"));
        // 关键修改：强制转换
        renderGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) renderItems);

        // ==================== 8. 音柱动画（索引7） ====================
        LiveSettingGroup musicGroup = new LiveSettingGroup();
        musicGroup.setGroupIndex(7);
        musicGroup.setGroupName("直播音柱");
        List<LiveSettingItem> musicItems = new ArrayList<>();
        musicItems.add(createLiveSettingItem(0, "音柱开启"));
        musicItems.add(createLiveSettingItem(1, "音柱关闭"));
        // 关键修改：强制转换
        musicGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) musicItems);

        // ==================== 9. 退出直播（索引8） ====================
        LiveSettingGroup exitGroup = new LiveSettingGroup();
        exitGroup.setGroupIndex(8);
        exitGroup.setGroupName("退出直播");
        List<LiveSettingItem> exitItems = new ArrayList<>();
        exitItems.add(createLiveSettingItem(0, "确认退出"));
        // 关键修改：强制转换
        exitGroup.setLiveSettingItems((ArrayList<LiveSettingItem>) (List<?>) exitItems);

        // ==================== 将所有组加入默认列表 ====================
        defaultList.add(lineGroup);     // 索引0
        defaultList.add(scaleGroup);   // 索引1
        defaultList.add(decoderGroup);  // 索引2
        defaultList.add(timeoutGroup);  // 索引3
        defaultList.add(personalGroup); // 索引4
        defaultList.add(yumGroup);     // 索引5
        defaultList.add(renderGroup);  // 索引6
        defaultList.add(musicGroup);   // 索引7
        defaultList.add(exitGroup);    // 索引8

        return defaultList;
    }

    /**
     * xuameng
     * 创建LiveSettingItem（局部方法）
     */
    private LiveSettingItem createLiveSettingItem(int index, String name) {
        LiveSettingItem item = new LiveSettingItem();
        item.setItemIndex(index);
        item.setItemName(name);
        item.setItemSelected(false);
        return item;
    }

    /**
     * xuameng
     * 重新生成收藏组
     */
    public void rebuildFavoriteGroup() {
        List<LiveChannelGroup> groups = getChannelGroupList();
        if (groups == null) return;
        // 重新生成收藏组
        LiveChannelGroup favoriteGroup =
                LiveChannelItem.createFavoriteChannelGroup();

        for (int i = 0; i < groups.size(); i++) {
            if ("我的收藏".equals(groups.get(i).getGroupName())) {
                favoriteGroup.setGroupIndex(i);
                groups.set(i, favoriteGroup);
                return;
            }
        }

        // 如果还没创建过“我的收藏”分组，则追加
        //favoriteGroup.setGroupIndex(groups.size());
        //groups.add(favoriteGroup);
    }

}
