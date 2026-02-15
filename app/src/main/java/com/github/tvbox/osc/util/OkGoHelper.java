package com.github.tvbox.osc.util;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;  //xuameng新增

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.picasso.MyOkhttpDownLoader;
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat;
import com.google.gson.JsonArray;  //xuameng新增
import com.google.gson.JsonObject; //xuameng新增
import com.google.gson.JsonParser; //xuameng新增
import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpHeaders;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.net.InetAddress;  //xuameng新增
import java.net.UnknownHostException;  //xuameng新增
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.Dns;  //xuameng新增
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.internal.Version;
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import android.util.Log; 
import java.util.concurrent.Callable;

/**
 * @author xuameng
 * @date :2026/2/12
 * @description:
 */
public class OkGoHelper {
    public static final long DEFAULT_MILLISECONDS = 10000;      //默认的超时时间

    // 内置doh json
    private static final String dnsConfigJson = "["   //xuameng新增
            + "{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},"
            + "{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},"
            + "{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}"
            + "]";
    static OkHttpClient ItvClient = null;   //xuameng新增完
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

//        builder.dns(dnsOverHttps);
        builder.dns(new CustomDns());  //xuameng新增
        ItvClient=builder.build();

        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(ItvClient); //xuameng新增完
    }

    public static DnsOverHttps dnsOverHttps = null;

    public static ArrayList<String> dnsHttpsList = new ArrayList<>();

    public static boolean is_doh = false;  //xuameng新增
    public static Map<String, String> myHosts = null;  //xuameng新增

    public static String getDohUrl(int type) {  //xuameng新增
        String json=Hawk.get(HawkConfig.DOH_JSON,"");
        if(json.isEmpty())json=dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        if (type >= 1 && type < dnsHttpsList.size()) {
            JsonObject dnsConfig = jsonArray.get(type - 1).getAsJsonObject();
            if (dnsConfig.has("url")) {     //XUAMENG修复DNS URL为空问题
                return dnsConfig.get("url").getAsString();    // 获取对应的 URL
            } else {
                return ""; // 或返回默认DNS地址如 "https://1.1.1.1/dns-query"
            }
        }
        return ""; //xuameng新增完
    }

    public static void setDnsList() {  //xuameng新增
        dnsHttpsList.clear();
        String json=Hawk.get(HawkConfig.DOH_JSON,"");
        if(json.isEmpty())json=dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        dnsHttpsList.add("默认");
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
            String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
            dnsHttpsList.add(name);
        }
        if(Hawk.get(HawkConfig.DOH_URL, 0)+1>dnsHttpsList.size())Hawk.put(HawkConfig.DOH_URL, 0);

    }

    private static List<InetAddress> DohIps(JsonArray ips) {
        List<InetAddress> inetAddresses = new ArrayList<>();
        if (ips != null) {
            for (int j = 0; j < ips.size(); j++) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(ips.get(j).getAsString());
                    inetAddresses.add(inetAddress);  // 添加到 List 中
                } catch (Exception e) {
                    e.printStackTrace();  // 处理无效的 IP 字符串
                }
            }
        }
        return inetAddresses;
    }  //xuameng新增完

    static void initDnsOverHttps() {   //xuameng新增
        Integer dohSelector=Hawk.get(HawkConfig.DOH_URL, 0);
        JsonArray ips=null;
        try {
            dnsHttpsList.add("默认");
            String json=Hawk.get(HawkConfig.DOH_JSON,"");
            if(json.isEmpty())json=dnsConfigJson;
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
            if(dohSelector>jsonArray.size())Hawk.put(HawkConfig.DOH_URL, 0);       //xuameng修复最后一项DNS选不上
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
                String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
                dnsHttpsList.add(name);
                if(dohSelector==i)ips = dnsConfig.has("ips") ? dnsConfig.getAsJsonArray("ips") : null;   //xuameng修复最后一项DNS选不上
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
        builder.cache(new Cache(new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache"), 100 * 1024 * 1024));   //xuameng新增完
        OkHttpClient dohClient = builder.build();
        String dohUrl = getDohUrl(Hawk.get(HawkConfig.DOH_URL, 0));
        if (!dohUrl.isEmpty()) is_doh = true;   //xuameng新增
//        dnsOverHttps = new DnsOverHttps.Builder()
//                .client(dohClient)
//                .url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl))
//                .build();
        DnsOverHttps.Builder dnsBuilder = new DnsOverHttps.Builder();
        dnsBuilder.client(dohClient);
        dnsBuilder.url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl));
        if (is_doh && ips!=null){
            List<InetAddress> IPS=DohIps(ips);
            dnsOverHttps = dnsBuilder.bootstrapDnsHosts(IPS).build();
        }else {
            dnsOverHttps = dnsBuilder.build();
        }
    }

    // 自定义 DNS 解析器
    static class CustomDns implements Dns {
        private  ConcurrentHashMap<String, List<InetAddress>> map;
        private final String excludeIps = "2409:8087:6c02:14:100::14,2409:8087:6c02:14:100::18,39.134.108.253,39.134.108.245";
        @NonNull
@Override
public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
    if (myHosts == null) {
        myHosts = ApiConfig.get().getMyHost();
    }

    // 👇 关键：在声明时就确定最终值，只赋值一次
    final String targetHost;
    if (myHosts != null && !myHosts.isEmpty() && myHosts.containsKey(hostname)) {
        String mapped = myHosts.get(hostname);
        if (mapped != null && isValidIpAddress(mapped)) {
            return Collections.singletonList(InetAddress.getByName(mapped));
        }
        targetHost = mapped; // ← 唯一赋值点
    } else {
        targetHost = hostname; // ← 唯一赋值点
    }

    // 2. 如果是 IP，直接返回
    if (isValidIpAddress(targetHost)) {
        return Collections.singletonList(InetAddress.getByName(targetHost));
    }

    // 3. 尝试 DoH（带 5 秒超时）
    if (is_doh && dnsOverHttps != null) {
        final DnsOverHttps doh = dnsOverHttps;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<InetAddress>> future = executor.submit(() -> doh.lookup(targetHost)); // ✅ 现在 targetHost 是 final
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            // fallback
        } finally {
            executor.shutdownNow();
        }
    }

    // 4. fallback 到系统 DNS
    return Dns.SYSTEM.lookup(targetHost);
}

        public synchronized void mapHosts(Map<String,String> hosts) throws UnknownHostException {   //xuameng新增
            map=new ConcurrentHashMap<>();
            for (Map.Entry<String, String> entry : hosts.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if(isValidIpAddress(value)){
                    map.put(key,Collections.singletonList(InetAddress.getByName(value)));
                }else {
                    map.put(key,getAllByName(value));
                }
            }
        }

        private List<InetAddress> getAllByName(String host) {
            try {
                // 获取所有与主机名关联的 IP 地址
                InetAddress[] allAddresses = InetAddress.getAllByName(host);
                if(excludeIps.isEmpty())return Arrays.asList(allAddresses);
                // 创建一个列表用于存储有效的 IP 地址
                List<InetAddress> validAddresses = new ArrayList<>();
                Set<String> excludeIpsSet = new HashSet<>();
                for (String ip : excludeIps.split(",")) {
                    excludeIpsSet.add(ip.trim());  // 添加到集合，去除多余的空格
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
    }  //xuameng新增完

    static OkHttpClient defaultClient = null;
    static OkHttpClient noRedirectClient = null;

    public static OkHttpClient getDefaultClient() {
        return defaultClient;
    }

    public static OkHttpClient getNoRedirectClient() {
        return noRedirectClient;
    }

    public static void init() {
        initDnsOverHttps();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkGo");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }

        //builder.retryOnConnectionFailure(false);

        builder.addInterceptor(loggingInterceptor);

        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);

        builder.dns(dnsOverHttps);
        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }

        HttpHeaders.setUserAgent(Version.userAgent());

        OkHttpClient okHttpClient = builder.build();
        OkGo.getInstance().setOkHttpClient(okHttpClient);

        defaultClient = okHttpClient;

        builder.followRedirects(false);
        builder.followSslRedirects(false);
        noRedirectClient = builder.build();

        initExoOkHttpClient();
        initPicasso(okHttpClient);
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
