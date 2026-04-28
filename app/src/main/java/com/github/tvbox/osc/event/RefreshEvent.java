package com.github.tvbox.osc.event;

/**
 * @author pj567
 * @date :2021/1/6
 * @description:
 */
public class RefreshEvent {
	public static final int TYPE_PUSH_VOD = 99;  //xuameng 推送
    public static final int TYPE_REFRESH = 0;
    public static final int TYPE_HISTORY_REFRESH = 1;
    public static final int TYPE_QUICK_SEARCH = 2;
    public static final int TYPE_QUICK_SEARCH_SELECT = 3;
    public static final int TYPE_QUICK_SEARCH_WORD = 4;
    public static final int TYPE_QUICK_SEARCH_WORD_CHANGE = 5;
    public static final int TYPE_SEARCH_RESULT = 6;
    public static final int TYPE_QUICK_SEARCH_RESULT = 7;
    public static final int TYPE_API_URL_CHANGE = 8;
    public static final int TYPE_PUSH_URL = 9;
    public static final int TYPE_EPG_URL_CHANGE = 10;
    public static final int TYPE_CLOSE_PLAY_ACTIVITY = 11;  //xuaemng远程关闭playactivity 用于push推送解析刷新
    public static final int TYPE_SUBTITLE_SIZE_CHANGE = 12;
    public static final int TYPE_FILTER_CHANGE = 13;
    public static final int TYPE_PAUSE_VOD = 14;   //xuameng 全屏时如果是暂停状态就显示暂停图标
    public int type;
    public Object obj;

    public RefreshEvent(int type) {
        this.type = type;
    }

    public RefreshEvent(int type, Object obj) {
        this.type = type;
        this.obj = obj;
    }
}
