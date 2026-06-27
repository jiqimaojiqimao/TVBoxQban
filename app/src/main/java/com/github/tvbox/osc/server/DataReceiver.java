package com.github.tvbox.osc.server;

/**
 * @author xuameng
 * @date :2026/6/24
 * @description: 直播远程输入
 */
public interface DataReceiver {

    /**
     * @param text
     */
    void onTextReceived(String text);


    void onApiReceived(String url);

    void onLiveApiReceived(String url); //xuameng直播远程输入

    void onPushReceived(String url);

    void onDanmuApiReceived(String url);  //xuameng 弹幕远程输入

	void onMirrorReceived(String id, String sourceKey);      //xuameng推送
}
