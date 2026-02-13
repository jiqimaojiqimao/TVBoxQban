package com.github.tvbox.osc.util;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.picasso.MyOkhttpDownLoader;
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpHeaders;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.internal.Version;
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;

/**
 * @author xuameng
 * @date :2026/2/12
 * @description:
 */

public class OkGoHelper {
    public static final long DEFAULT_MILLISECONDS = 5000;      //默认的超时时间

    // 内置doh json
    private static final String dnsConfigJson = "["
    + "{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},"
    + "{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},"
    + "{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}"
    + "]";
    static OkHttpClient ItvClient = null;

    // 初始化状态管理
    private static volatile boolean isInitializing = false;
    private static volatile boolean isInitialized = false;
    private static final Object initLock = new Object();

    static void initExoOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);

        builder.retryOnConnectionFailure(true);
        builder.followRedirects(true);
        builder.followSslRedirects(true);

        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }

        builder.dns(new CustomDns());
        ItvClient = builder.build();

        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(ItvClient);
    }

    public static volatile DnsOverHttps dnsOverHttps = null;

    public static ArrayList<String> dnsHttpsList = new ArrayList<>();

    public static boolean is_doh = false;
    public static volatile Map<String, String> myHosts = null;

    public static String getDohUrl(int type) {
        String json = Hawk.get(HawkConfig.DOH_JSON, "");
        if (json.isEmpty()) json = dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        if (type >= 1 && type < dnsHttpsList.size()) {
            JsonObject dnsConfig = jsonArray.get(type - 1).getAsJsonObject();
            if (dnsConfig.has("url")) {
                return dnsConfig.get("url").getAsString();
            } else {
                return "";
            }
        }
        return "";
    }

    public static void setDnsList() {
        // 关键修复：在添加新数据前，先清空列表
        dnsHttpsList.clear();

        String json = Hawk.get(HawkConfig.DOH_JSON, "");
        if (json.isEmpty()) json = dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();

        dnsHttpsList.add("默认");
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
            String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
            dnsHttpsList.add(name);
        }

        // 修正用户选择索引，防止越界
        int selectedIndex = Hawk.get(HawkConfig.DOH_URL, 0);
        if (selectedIndex >= dnsHttpsList.size()) {
            Hawk.put(HawkConfig.DOH_URL, 0);
        }
    }

    private static List<InetAddress> DohIps(JsonArray ips) {
        List<InetAddress> inetAddresses = new ArrayList<>();
        if (ips != null) {
            for (int j = 0; j < ips.size(); j++) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(ips.get(j).getAsString());
                    inetAddresses.add(inetAddress);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return inetAddresses;
    }

    static void initDnsOverHttps() {
        // 调用 setDnsList() 来确保列表是最新的
        setDnsList();

        Integer dohSelector = Hawk.get(HawkConfig.DOH_URL, 0);
        JsonArray ips = null;
        try {
            String json = Hawk.get(HawkConfig.DOH_JSON, "");
            if (json.isEmpty()) json = dnsConfigJson;
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
            if (dohSelector > jsonArray.size()) Hawk.put(HawkConfig.DOH_URL, 0);

            // 注意：这里不再添加列表项，列表已由 setDnsList() 生成
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
                if (dohSelector == i)
                ips = dnsConfig.has("ips") ? dnsConfig.getAsJsonArray("ips") : null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);
        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        builder.cache(new Cache(new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache"), 10 * 1024 * 1024));
        OkHttpClient dohClient = builder.build();
        String dohUrl = getDohUrl(Hawk.get(HawkConfig.DOH_URL, 0));
        if (!dohUrl.isEmpty()) is_doh = true;
        DnsOverHttps.Builder dnsBuilder = new DnsOverHttps.Builder();
        dnsBuilder.client(dohClient);
        dnsBuilder.url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl));
        if (is_doh && ips != null) {
            List<InetAddress> IPS = DohIps(ips);
            dnsOverHttps = dnsBuilder.bootstrapDnsHosts(IPS).build();
        } else {
            dnsOverHttps = dnsBuilder.build();
        }
    }

    // 自定义 DNS 解析器 - 修复线程泄漏问题
    static class CustomDns implements Dns {
        private ConcurrentHashMap<String, List<InetAddress>> map;
        private static final long DNS_TIMEOUT_MS = 3000; // 3秒超时
        private final String excludeIps = "2409:8087:6c02:14:100::14,2409:8087:6c02:14:100::18,39.134.108.253,39.134.108.245";

        // 使用线程池管理DNS查询，避免线程泄漏
        private static final ExecutorService dnsExecutor = Executors.newFixedThreadPool(3);
        private static final AtomicInteger activeQueries = new AtomicInteger(0);
        private static final int MAX_ACTIVE_QUERIES = 10;

        // DNS缓存机制
        private static final ConcurrentHashMap<String, CacheEntry> dnsCache = new ConcurrentHashMap<>();
        private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存

        static class CacheEntry {
            List<InetAddress> addresses;
            long timestamp;

            CacheEntry(List<InetAddress> addresses) {
                this.addresses = addresses;
                this.timestamp = System.currentTimeMillis();
            }

            boolean isExpired() {
                return System.currentTimeMillis() - timestamp > CACHE_DURATION;
            }
        }

        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            // 检查活跃查询数量，防止过多并发查询
            if (activeQueries.get() >= MAX_ACTIVE_QUERIES) {
                return Dns.SYSTEM.lookup(hostname);
            }

            // 检查缓存
            CacheEntry cached = dnsCache.get(hostname);
            if (cached != null && !cached.isExpired()) {
                return cached.addresses;
            }

            activeQueries.incrementAndGet();
            try {
                // 提交任务到线程池
                Future<List<InetAddress>> future = dnsExecutor.submit(() -> {
                    return doLookup(hostname);
                });

                try {
                    // 设置超时时间
                    List<InetAddress> addresses = future.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                    // 更新缓存
                    dnsCache.put(hostname, new CacheEntry(addresses));

                    // 清理过期缓存
                    cleanupExpiredCache();

                    return addresses;
                } catch (Exception e) {
                    // 取消任务
                    future.cancel(true);
                    // 超时或异常后使用系统DNS
                    return Dns.SYSTEM.lookup(hostname);
                }
            } finally {
                activeQueries.decrementAndGet();
            }
        }

        private void cleanupExpiredCache() {
            long now = System.currentTimeMillis();
            dnsCache.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > CACHE_DURATION
            );
        }

        private List<InetAddress> doLookup(String hostname) throws UnknownHostException {
            // 双重检查锁定确保myHosts只初始化一次
            if (myHosts == null) {
                synchronized (OkGoHelper.class) {
                    if (myHosts == null) {
                        myHosts = ApiConfig.get().getMyHost();
                    }
                }
            }

            // 如果myHosts不为null且非空，则进行主机名替换
            if (myHosts != null && !myHosts.isEmpty() && myHosts.containsKey(hostname)) {
                hostname = myHosts.get(hostname);
            }

            assert hostname != null;
            if (isValidIpAddress(hostname)) {
                return Collections.singletonList(InetAddress.getByName(hostname));
            } else {
                // 如果dnsOverHttps为null，回退到系统默认DNS
                DnsOverHttps localDns = dnsOverHttps;
                if (localDns != null) {
                    return localDns.lookup(hostname);
                } else {
                    return Dns.SYSTEM.lookup(hostname);
                }
            }
        }

        public synchronized void mapHosts(Map<String, String> hosts) throws UnknownHostException {
            map = new ConcurrentHashMap<>();
            for (Map.Entry<String, String> entry : hosts.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (isValidIpAddress(value)) {
                    map.put(key, Collections.singletonList(InetAddress.getByName(value)));
                } else {
                    map.put(key, getAllByName(value));
                }
            }
        }

        private List<InetAddress> getAllByName(String host) {
            try {
                // 获取所有与主机名关联的 IP 地址
                InetAddress[] allAddresses = InetAddress.getAllByName(host);
                if (excludeIps.isEmpty()) return Arrays.asList(allAddresses);
                // 创建一个列表用于存储有效的 IP 地址
                List<InetAddress> validAddresses = new ArrayList<>();
                Set<String> excludeIpsSet = new HashSet<>();
                for (String ip : excludeIps.split(",")) {
                    excludeIpsSet.add(ip.trim());
                }
                for (InetAddress address : allAddresses) {
                    if (!excludeIpsSet.contains(address.getHostAddress())) {
                        validAddresses.add(address);
                    }
                }
                return validAddresses;
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        //简单判断减少开销
        private boolean isValidIpAddress(String str) {
            if (str.indexOf('.') > 0) return isValidIPv4(str);
            return str.indexOf(':') > 0;
        }

        private boolean isValidIPv4(String str) {
            String[] parts = str.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                try {
                    Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }
    }

    static OkHttpClient defaultClient = null;
    static OkHttpClient noRedirectClient = null;

    public static OkHttpClient getDefaultClient() {
        if (!isInitialized && !isInitializing) {
            synchronized (initLock) {
                if (!isInitialized && !isInitializing) {
                    // 如果未初始化，同步初始化
                    initEssentialSync();
                    isInitialized = true;
                }
            }
        }
        return defaultClient;
    }

    public static OkHttpClient getNoRedirectClient() {
        return noRedirectClient;
    }

    public static void init() {
        if (isInitialized || isInitializing) {
            return;
        }

        synchronized (initLock) {
            if (isInitialized || isInitializing) {
                return;
            }

            isInitializing = true;

            try {
                // 第一阶段：快速初始化核心组件（在主线程执行）
                initEssentialSync();

                // 在初始化时调用 setDnsList() 确保列表被正确初始化
                setDnsList();

                // 第二阶段：延迟1秒后异步初始化耗时组件
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    new Thread(() -> {
                        try {
                            initHeavyComponentsAsync();
                            isInitialized = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            isInitializing = false;
                        }
                    }).start();
                }, 1000);
            } catch (Exception e) {
                isInitializing = false;
                throw e;
            }
        }
    }



    private static void initEssentialSync() {
        // 只初始化必要的、快速的组件
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);

        // 启动时强制使用系统DNS，避免卡死
        builder.dns(Dns.SYSTEM);

        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }

        OkHttpClient tempClient = builder.build();
        OkGo.getInstance().setOkHttpClient(tempClient);
        defaultClient = tempClient;
    }

    private static void initHeavyComponentsAsync() {
        // 异步初始化DNS-over-HTTPS
        initDnsOverHttps();

        // 使用局部变量避免竞态条件
        DnsOverHttps localDns = dnsOverHttps;

        // 重新构建客户端使用新的DNS
        OkHttpClient.Builder builder = defaultClient.newBuilder();
        builder.dns(localDns != null ? localDns : Dns.SYSTEM);

        OkHttpClient finalClient = builder.build();
        OkGo.getInstance().setOkHttpClient(finalClient);
        defaultClient = finalClient;

        // 初始化其他组件
        initExoOkHttpClient();
        initPicasso(finalClient);
    }

    static void initPicasso(OkHttpClient client) {
        client.dispatcher().setMaxRequestsPerHost(10);
        MyOkhttpDownLoader downloader = new MyOkhttpDownLoader(client);
        Picasso picasso = new Picasso.Builder(App.getInstance())
        .downloader(downloader)
        .defaultBitmapConfig(Bitmap.Config.RGB_565)
        .build();
        Picasso.setSingletonInstance(picasso);
    }

    private static synchronized void setOkHttpSsl(OkHttpClient.Builder builder) {
        try {
            // 自定义一个信任所有证书的TrustManager，添加SSLSocketFactory的时候要用到
            final X509TrustManager trustAllCert =
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            };
            final SSLSocketFactory sslSocketFactory = new SSLSocketFactoryCompat(trustAllCert);
            builder.sslSocketFactory(sslSocketFactory, trustAllCert);
            builder.hostnameVerifier(HttpsUtils.UnSafeHostnameVerifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
