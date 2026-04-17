package com.github.tvbox.osc.server;

import com.github.tvbox.osc.base.App; // xuameng引入 App 类

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author xuameng
 * @date :2026/4/17
 * @description: 响应按键和输入 增加响应主机名
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null && mDataReceiver != null) {
                    String action = params.get("do");

                    switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "api": {
                            mDataReceiver.onApiReceived(params.get("url").trim());
                            break;
                        }
                        case "push": {
                            // 暂未实现
                            mDataReceiver.onPushReceived(params.get("url").trim());
                            break;
                        }
                        case "mirror": {
                            //推送当前电影、电视剧……
                            mDataReceiver.onMirrorReceived(params.get("id").trim(), params.get("sourceKey").trim());
                            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "mirrored");
                        }
                    }
                }
                //return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
                //xuameng增加相应主机名
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok|" + App.deviceName);
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
