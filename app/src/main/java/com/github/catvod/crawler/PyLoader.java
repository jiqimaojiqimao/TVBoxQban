package com.github.catvod.crawler;

import com.github.tvbox.osc.base.App;
import com.undcover.freedom.pyramid.PythonLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class PyLoader {
    private final PythonLoader pythonLoader = PythonLoader.getInstance().setApplication(App.getInstance());
    private static ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();

    private String lastConfig = null; // 记录上次的配置

    public void setConfig(String jsonStr) {
        if (jsonStr != null && !jsonStr.equals(lastConfig)) {
            pythonLoader.setConfig(jsonStr);
            lastConfig = jsonStr;
        }
    }
    public Spider getSpider(String key, String cls, String ext) {
        if (spiders.containsKey(key))
//            Log.i("PyLoader", "echo-getSpider spider缓存: " + key);
//            LOG.i("");
            return spiders.get(key);
        try {
            Spider sp = pythonLoader.getSpider(key, getPyUrl(cls,ext));
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    public Object[] proxyInvoke(String key,String url,Map<String,String> params) {
        try {
            String doStr = params.get("do");
            assert doStr != null;
            if (doStr.equals("ck") || doStr.equals("live"))return pythonLoader.proxyLocal("", "", params);
            return (Object[]) pythonLoader.proxyLocal(key, url, params);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    private String getPyUrl(String api,String ext)
    {
        StringBuilder urlBuilder = new StringBuilder(api);
        if (!ext.isEmpty()) {
            urlBuilder.append(api.contains("?") ? "&" : "?").append("extend=").append(ext);
        }
        return urlBuilder.toString();
    }
}