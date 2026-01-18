package com.github.tvbox.osc.util;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class HawkConfig {
    public static final String PUSH_TO_ADDR = "push_to_addr"; // xuameng推送到地址的IP
    public static final String PUSH_TO_PORT = "push_to_port"; // xuameng推送到地址的端口
    public static final String API_URL = "api_url";   //xuameng配置地址
    public static final String EPG_URL = "epg_url";  //xuameng EPG地址
    public static final String SHOW_PREVIEW = "show_preview";   //xuameng窗口播放
    public static final String API_HISTORY = "api_history";  //xuameng配置地址历史
    public static final String EPG_HISTORY = "epg_history";
    public static final String HOME_API = "home_api";     //xuameng首页数据源
    public static final String DEFAULT_PARSE = "parse_default";     //xuameng 默认解析
    public static final String DEBUG_OPEN = "debug_open"; //xuameng调试
    public static final String PARSE_WEBVIEW = "parse_webview"; // true 系统 false xwalk
    public static final String IJK_CODEC = "ijk_codec";   //硬解软解
    public static final String PLAY_TYPE = "play_type";//0 系统 1 ijk 2 exo 10 MXPlayer
	public static final String LIVE_PLAY_TYPE = "live_play_type";//0 系统 1 ijk 2 exo 10 MXPlayer     xuameng升级直播JSON中可以指定播放器类型
    public static final String PLAY_RENDER = "play_render"; //0 texture surface渲染
    public static final String PLAY_SCALE = "play_scale"; //xuameng 画面缩放
    public static final String PLAY_TIME_STEP = "play_time_step"; //0 texture 2
    public static final String DOH_URL = "doh_url";      //xuameng DNS   
    public static final String HOME_REC = "home_rec"; // 0 豆瓣热播 1 数据源推荐 2 历史
    public static final String HISTORY_NUM = "history_num";
    public static final String SEARCH_VIEW = "search_view"; // 0 列表 1 缩略图
    public static final String LIVE_CHANNEL = "last_live_channel_name";    
    public static final String LIVE_CHANNEL_REVERSE = "live_channel_reverse";  //xuameng换台反转
    public static final String LIVE_CROSS_GROUP = "live_cross_group";   //xuameng跨选分类
    public static final String LIVE_CONNECT_TIMEOUT = "live_connect_timeout";   //xuameng直播超时
    public static final String LIVE_SHOW_NET_SPEED = "live_show_net_speed";  //xuameng显示直播网速
    public static final String LIVE_SHOW_TIME = "live_show_time";   //xuameng显示直播时间
    public static final String FAST_SEARCH_MODE = "fast_search_mode";   //聚合模式
    public static final String SUBTITLE_TEXT_SIZE = "subtitle_text_size";   //xuameng字幕大小
    public static final String SUBTITLE_TIME_DELAY = "subtitle_time_delay";   //xuameng 字幕显示延迟时间
    public static final String SOURCES_FOR_SEARCH = "checked_sources_for_search";   //xuameng搜索源
    public static final String HOME_REC_STYLE = "home_rec_style";   //xuameng首页单行
    public static final String NOW_DATE = "now_date"; //当前日期
    public static final String REMOTE_TVBOX = "remote_tvbox_host";    //xuameng远源TVBOX 
    public static final String IJK_CACHE_PLAY = "ijk_cache_play";       //IJK缓存
	public static final String HOME_DEFAULT_SHOW = "home_default_show";  //xuameng 启动时直接进直播的开关
	public static final String LIVE_CHANNEL_GROUP = "last_live_channel_group_name";  //xuameng记忆上次播放频道组
	public static final String PLAYER_IS_LIVE = "player_is_live";   //xuameng判断是否进入直播
	public static final String DOH_JSON = "doh_json";    //xuameng DNS JSON
    public static final String LIVE_GROUP_INDEX = "live_group_index";    //XUAMENG直播源index
    public static final String LIVE_GROUP_LIST = "live_group_list";  //XUAMENG直播源list
    public static final String LIVE_API_URL = "live_api_url";     //xuameng直播接口地址
	public static final String M3U8_PURIFY = "m3u8_purify";  //xuameng广告过滤
	public static final String LIVE_API_HISTORY = "live_api_history";   //xuameng直播历史列表
	public static final String LIVE_WEB_HEADER = "live_web_header";    //xuameng直播定义UA
    public static final String LIVE_MUSIC_ANIMATION = "live_music_animation";  //xuameng直播音乐动画
    public static final String VOD_MUSIC_ANIMATION = "vod_music_animation";  //xuameng点播音乐动画
    public static final String EXO_PLAYER_DECODE = "exo_player_decode";  //xuameng exo解码方式
    public static final String EXO_PLAY_SELECTCODE = "exo_play_selectcode"; //xuameng exo解码动态选择  
    public static final String EXO_PROGRESS_KEY = "exo_progress_key"; //xuameng 进程KEY
    public static final String IJK_PROGRESS_KEY = "ijk_progress_key"; //xuameng 进程KEY
	public static final String VOD_SWITCHDECODE = "vod_switchdecode";  //xuameng解码切换
	public static final String VOD_SWITCHPLAYER = "vod_switchplayer";  //xuameng播放器切换

	public static boolean MSLIDEINFO = false;  //xuameng调节亮度声音
	public static boolean intLIVEPLAYTYPE = false;  //xuameng是否有直播默认播放器
	public static boolean intSYSplayer = false;  //xuameng是否进入系统播放器
	public static boolean intVod = false;  //xuameng判断是否进入VOD界面
	public static boolean ISrestore = false;  //xuameng判断是否进行恢复操作
	public static boolean isGetWp = false;  //xuameng下载壁纸
    public static boolean saveHistory = false;  //xuameng 存储历史记录
    public static boolean exoSubtitle = false;  //xuameng 判断当前是否播放EXO内置字幕

    public static boolean hotVodDelete;
}
