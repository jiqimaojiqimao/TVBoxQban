package com.github.tvbox.osc.player.thirdparty;

import android.app.Activity;
import android.text.TextUtils;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.orhanobut.hawk.Hawk;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author xuameng
 * @date :2026/4/17
 * @description: 增加相应远程主机名
 */

public class RemoteTVBox {
    public static boolean run(Activity activity, String url, String title, String subtitle, HashMap<String, String> headers) {
        String actionUrl = getAvalibleActionUrl();
        if (TextUtils.isEmpty(actionUrl)) {
            return false;
        }
        try {
            if (headers != null && headers.size() > 0) {
                url = url + "|";
                int idx = 0;
                for (String hk : headers.keySet()) {
                    url += URLEncoder.encode(hk, "UTF-8") + "=" + URLEncoder.encode(headers.get(hk), "UTF-8");
                    if (idx < headers.keySet().size() -1) {
                        url += "&";
                    }
                    idx ++;
                }
            }
            Map<String ,String> params = new HashMap<>();
            params.put("do", "push");
            params.put("url", url);
            post(actionUrl, params, new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String pushResult = response.body().string();
                    if (pushResult.equals("ok")) {
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public static void searchAvalible(Callback callback) {
        final String localIp = RemoteServer.getLocalIPAddress(App.getInstance());
        int divisionIp = TextUtils.isEmpty(localIp) ? -1 : localIp.lastIndexOf(".");
        if (divisionIp <= 0) {
            callback.fail(true, true);
            return;
        }
        final String prefix = localIp.substring(0, divisionIp + 1);
        final int port = 9978;
        final AtomicInteger finishedNum = new AtomicInteger(0);
        final AtomicInteger foundNum = new AtomicInteger(0);
        final int total = 254;
        for (int i = 1; i <= 255; i++) {
            String ip = prefix + i;
            if (ip.equals(localIp)) {
                continue;
            }
            final String actionUrl = "http://" + ip + ":" + port + "/action";
            final String viewHost = ip + ":" + port;
            try {
                post(actionUrl, null, new okhttp3.Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        notifySearchFail(callback, foundNum, finishedNum, total);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
		                    // 1. xuameng获取远端返回的内容，例如 "ok|Xiaomi Box"  result包包含远端主机名
                            String result = response.body() == null ? "" : response.body().string();
                            boolean end = finishedNum.incrementAndGet() == total;

                            // 2. xuameng判断是否成功（只要以 ok 开头就算成功）
                            if (result.startsWith("ok")) {
                                String deviceName = "聚汇影视"; // 默认名字
                                // 3. xuameng解析设备名：如果包含 "|"，就分割取第二部分  因为result返回的是 "ok|" + App.deviceName);
                                if (result.contains("|")) {
                                    deviceName = result.split("\\|")[1];
                                }

                                // 4. xuameng找到设备，数量+1
                                foundNum.incrementAndGet();

                                // 5. xuameng传给回调
                                callback.found(viewHost, deviceName, end);
                            } else {
                                callback.fail(foundNum.get() == 0 && end, end);
                            }
                            // ===== xuameng结束 =====

                        } finally {
                            response.close();
                        }
                    }
                });
            } catch (Exception e) {
                notifySearchFail(callback, foundNum, finishedNum, total);
            }
        }
    }

    private static void notifySearchFail(Callback callback, AtomicInteger foundNum, AtomicInteger finishedNum, int total) {
        boolean end = finishedNum.incrementAndGet() == total;
        callback.fail(foundNum.get() == 0 && end, end);
    }


/*    public static String getAvalible() {
        return Hawk.get(HawkConfig.REMOTE_TVBOX, null);
    }
*/
    //xuameng value正常为 name(2.2.2.2:2222) 远程调用播放只需要保留 ip和端口 因此修改
    public static String getAvalible() {
        String value = Hawk.get(HawkConfig.REMOTE_TVBOX, null);
        
        if (value != null) {
            int start = value.indexOf('(');
            int end = value.indexOf(')');
            return value.substring(start + 1, end);
        }
        
        return null;
    }

    public static String getAvalibleActionUrl() {
        if (getAvalible() == null) {
            return "";
        }
        return "http://" + getAvalible() + "/action";
    }

    public static void setAvalible(String viewHost) {
        Hawk.put(HawkConfig.REMOTE_TVBOX, viewHost);
    }

    public static void post(String url, Map<String, String> params, okhttp3.Callback callback) {
        OkHttpClient base = OkGoHelper.getDefaultClient();
        OkHttpClient.Builder builder = base != null ? base.newBuilder() : new OkHttpClient.Builder().proxySelector(OkGoHelper.proxySelector()).proxyAuthenticator(OkGoHelper.proxyAuthenticator());
        builder.readTimeout(1000, TimeUnit.MILLISECONDS);
        builder.writeTimeout(1000, TimeUnit.MILLISECONDS);
        builder.connectTimeout(1000, TimeUnit.MILLISECONDS);
        OkHttpClient client = builder.build();
        FormBody.Builder formBodyBuilder = new FormBody.Builder();
        if (params != null && params.size() > 0) {
            for(Map.Entry<String, String> entry : params.entrySet()) {
                formBodyBuilder.add(entry.getKey(), entry.getValue());
            }
        }
        FormBody formBody = formBodyBuilder.build();
        client.newCall(new Request.Builder().url(url).post(formBody).build()).enqueue(callback);
    }

    // xuameng修改 Callback 接口，增加 deviceName 参数
    public abstract class Callback {
        //public abstract void found(String viewHost, boolean end); 原代码
        public abstract void found(String viewHost, String deviceName, boolean end);
        public abstract void fail(boolean all, boolean end);
    }
}
