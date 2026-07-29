package com.github.catvod.net;

import androidx.collection.ArrayMap;

import com.github.tvbox.osc.util.OkGoHelper;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/json;q=0.9";
    private static OkDns dns;
    private static OkHttpClient client;

    public static synchronized OkDns dns() {
        if (dns == null) dns = new OkDns();
        return dns;
    }

    public static synchronized OkHttpClient client() {
        if (client != null) return client;
        OkHttpClient base = OkGoHelper.getDefaultClient();
        if (base != null) return client = base.newBuilder().dns(dns()).addInterceptor(defaultHeaders()).build();
        return client = new OkHttpClient.Builder().dns(dns()).proxySelector(OkGoHelper.proxySelector()).proxyAuthenticator(OkGoHelper.proxyAuthenticator()).addInterceptor(defaultHeaders()).connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS).readTimeout(TIMEOUT, TimeUnit.MILLISECONDS).writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient player() {
        return client();
    }

    public static OkHttpClient client(long timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient noRedirect() {
        return noRedirect(TIMEOUT);
    }

    public static OkHttpClient noRedirect(long timeout) {
        OkHttpClient base = OkGoHelper.getNoRedirectClient();
        if (base == null) base = client();
        return base.newBuilder().dns(dns()).addInterceptor(defaultHeaders()).connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public static synchronized void reset() {
        client = null;
        dns = null;
    }

    public static synchronized void resetClient() {
        client = null;
    }

    public static OkHttpClient client(boolean redirect, long timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    /**
     * xuameng 兼容旧 spider：
     */
    public static String string(String url) { 
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = client().newCall(new Request.Builder().url(url).build()).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, long timeout) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(client(timeout), url).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, Map<String, String> headers) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(url, headers)) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 兼容旧版 spider 调用的 get 方法
     */
    public static OkResult get(String url, Map<String, String> headers) {
        try {
            Response response = newCall(url, headers);
            int code = response.code();
            String body = response.body() != null ? response.body().string() : "";
            return new OkResult(code, body, response.headers().toMultimap());
        } catch (Exception e) {
            e.printStackTrace();
            return new OkResult(500, "", Collections.emptyMap());
        }
    }

    /**
     * xuameng 原有返回Call的方法改名，兼容项目内所有原本调用该方法的其他代码
     */
    @Deprecated
    public static Call newCallInternal(String url) {
        return client().newCall(new Request.Builder().url(url).build());
    }

    /**
     * xuameng 兼容旧 spider：
     */
    @Deprecated
    public static Response newCall(String url) throws IOException {
        return newCallInternal(url).execute();
    }

    /**
     * xuameng 兼容旧 spider：
     */
    @Deprecated
    public static Response newCall(Request request) throws IOException {
        return client().newCall(request).execute();
    }

    /**
     * xuameng 兼容旧 spider：
     */
    @Deprecated
    public static Response newCall(String url, Map<String, String> headers) throws IOException {
        return newCallForResult(url, headers).execute();
    }

    public static Call newCall(String url, String tag) {
        return client().newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url, String tag) {
        return client.newCall(new Request.Builder().url(url).tag(tag).build());
    }

    /**
     * xuameng 原有返回Call的方法改名，兼容项目内所有原本调用该方法的其他代码
     */
    public static Call newCallForResult(String url, Map<String, String> headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers(headers)).build());
    }

    public static Call newCall(String url, Headers headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers).build());
    }

    public static Call newCall(String url, Map<String, String> headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(headers(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(headers(headers)).post(body).build());
    }

    public static Call newCall(String url, RequestBody body, String tag) {
        return client().newCall(new Request.Builder().url(url).post(body).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static void cancel(String tag) {
        cancel(client(), tag);
    }

    /** xuameng兼容旧 spider */
    public static Response newCallDownload(String url) throws IOException {
        return client().newCall(new Request.Builder().url(url).build()).execute();
    }

    /** xuameng兼容旧 spider */
    public static Response newCallDownload(String url, Headers headers) throws IOException {
        return newCall(url, headers).execute();
    }

    /** xuameng兼容旧 spider */
    public static void newCallDownload(String url, String path) throws IOException {
        try (Response res = client().newCall(new Request.Builder().url(url).build()).execute()) {
        writeToFile(res, path);
        }
    }

    /** xuameng兼容旧 spider：带 header 下载 */
    public static void newCallDownload(String url, Headers headers, String path) throws IOException {
        try (Response res = newCall(url, headers).execute()) {
            writeToFile(res, path);
        }
    }

    /**
     * xuameng兼容旧 spider：newCallDownload(String url, Map headers) -> Response
     */
    public static Response newCallDownload(String url, Map<String, String> headersMap) throws IOException {
        return newCall(url, headersMap);
    }

    /** xuameng公共写文件逻辑 */
    private static void writeToFile(Response res, String path) throws IOException {
        File out = new File(path);
        out.getParentFile().mkdirs();
        try (InputStream is = res.body().byteStream();
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
    }

    public static void cancel(OkHttpClient client, String tag) {
        if (client == null || tag == null) return;
        for (Call call : client.dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : client.dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    public static void cancelAll() {
        cancelAll(client());
    }

    public static void cancelAll(OkHttpClient client) {
        if (client != null) client.dispatcher().cancelAll();
    }

    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        if (params != null) for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue());
        return body.build();
    }

    private static Headers headers(Map<String, String> headers) {
        return headers == null ? new Headers.Builder().build() : Headers.of(headers);
    }

    private static Interceptor defaultHeaders() {
        return chain -> {
            Request request = chain.request();
            Request.Builder builder = request.newBuilder();
            if (request.header("User-Agent") == null) builder.header("User-Agent", USER_AGENT);
            if (request.header("Accept") == null) builder.header("Accept", ACCEPT);
            return chain.proceed(builder.build());
        };
    }

    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = HttpUrl.parse(url).newBuilder();
        if (params != null) for (Map.Entry<String, String> entry : params.entrySet()) builder.addQueryParameter(entry.getKey(), entry.getValue());
        return builder.build();
    }
}
