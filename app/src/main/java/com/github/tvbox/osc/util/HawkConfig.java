package com.github.tvbox.osc.util;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class HawkConfig {
    public static final String PUSH_TO_ADDR = "push_to_addr"; // xuameng推送到地址的IP
    public static final String PUSH_TO_PORT = "push_to_port"; // xuameng推送到地址的端口
    public static final String API_URL = "api_url";
    public static final String EPG_URL = "epg_url";
    public static final String SHOW_PREVIEW = "show_preview";   //xuameng窗口播放
    public static final String API_HISTORY = "api_history";
    public static final String EPG_HISTORY = "epg_history";
    public static final String HOME_API = "home_api";
    public static final String DEFAULT_PARSE = "parse_default";
    public static final String DEBUG_OPEN = "debug_open";
    public static final String PARSE_WEBVIEW = "parse_webview"; // true 系统 false xwalk
    public static final String IJK_CODEC = "ijk_codec";   //硬解软解
    public static final String PLAY_TYPE = "play_type";//0 系统 1 ijk 2 exo 10 MXPlayer
	public static final String LIVE_PLAY_TYPE = "live_play_type";//0 系统 1 ijk 2 exo 10 MXPlayer     xuameng升级直播JSON中可以指定播放器类型
    public static final String PLAY_RENDER = "play_render"; //0 texture 2
    public static final String PLAY_SCALE = "play_scale"; //0 texture 2
    public static final String PLAY_TIME_STEP = "play_time_step"; //0 texture 2
    public static final String DOH_URL = "doh_url";
    public static final String HOME_REC = "home_rec"; // 0 豆瓣热播 1 数据源推荐 2 历史
    public static final String HISTORY_NUM = "history_num";
    public static final String SEARCH_VIEW = "search_view"; // 0 列表 1 缩略图
    public static final String LIVE_CHANNEL = "last_live_channel_name";
    public static final String LIVE_CHANNEL_REVERSE = "live_channel_reverse";
    public static final String LIVE_CROSS_GROUP = "live_cross_group";
    public static final String LIVE_CONNECT_TIMEOUT = "live_connect_timeout";
    public static final String LIVE_SHOW_NET_SPEED = "live_show_net_speed";
    public static final String LIVE_SHOW_TIME = "live_show_time";
    public static final String FAST_SEARCH_MODE = "fast_search_mode";   //聚合模式
    public static final String SUBTITLE_TEXT_SIZE = "subtitle_text_size";
    public static final String SUBTITLE_TIME_DELAY = "subtitle_time_delay";
    public static final String SOURCES_FOR_SEARCH = "checked_sources_for_search";
    public static final String HOME_REC_STYLE = "home_rec_style";
    public static final String NOW_DATE = "now_date"; //当前日期
    public static final String REMOTE_TVBOX = "remote_tvbox_host";   
    public static final String IJK_CACHE_PLAY = "ijk_cache_play";       //IJK缓存
	public static final String HOME_DEFAULT_SHOW = "home_default_show";  //xuameng 启动时直接进直播的开关
	public static final String LIVE_CHANNEL_GROUP = "last_live_channel_group_name";  //xuameng记忆上次播放频道组
	public static boolean MSLIDEINFO = false;  //xuameng调节亮度声音
	public static boolean intVod = false;  //xuameng判断选择播放器防止surface焦点丢失
	public static boolean intLIVEPLAYTYPE = false;  //xuameng是否有直播默认播放器

    public static boolean hotVodDelete;
}
