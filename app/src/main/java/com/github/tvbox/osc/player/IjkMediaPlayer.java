package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.server.ControlManager;   //xuameng新增
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;  //xuameng新增
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.net.URI;  //xuameng新增
import java.net.URLEncoder;  //xuameng新增
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;  //xuameng记忆选择音轨
import com.github.tvbox.osc.util.AudioTrackMemory;  //xuameng记忆选择音轨
import java.util.List;  //默认选中文音轨

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import xyz.doikki.videoplayer.ijk.IjkPlayer;

public class IjkMediaPlayer extends IjkPlayer {

    private IJKCode codec = null;
	private static AudioTrackMemory memory;

    public IjkMediaPlayer(Context context, IJKCode codec) {
        super(context);
        this.codec = codec;
		memory = AudioTrackMemory.getInstance(context);  //xuameng记忆选择音轨
    }

    @Override
    public void setOptions() {
        super.setOptions();
        IJKCode codecTmp = this.codec == null ? ApiConfig.get().getCurrentIJKCode() : this.codec;
        LinkedHashMap<String, String> options = codecTmp.getOption();
        if (options != null) {
            for (String key : options.keySet()) {
                String value = options.get(key);
                String[] opt = key.split("\\|");
                int category = Integer.parseInt(opt[0].trim());
                String name = opt[1].trim();
                try {
                    assert value != null;
                    long valLong = Long.parseLong(value);
                    mMediaPlayer.setOption(category, name, valLong);
                } catch (Exception e) {
                    mMediaPlayer.setOption(category, name, value);
                }
            }
        }

        //开启内置字幕
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT,"safe",0);  //xuameng新增

        if(Hawk.get(HawkConfig.PLAYER_IS_LIVE)){   //xuameng新增
            LOG.i("type-直播");
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 300);
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1);
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 1);
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", "1");
            // 限制视频缓冲队列大小
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 512);
        }else{
            LOG.i("type-点播");
			// 降低延迟
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 3000);
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", 0);
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", "2");
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "keepalive", 1); // xuameng开启TCP保活
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1); // xuameng开启自动重连
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 60000000); // xuameng60秒超时
        }
    }  //xuameng新增完

    private static final String ITV_TARGET_DOMAIN = "gslbserv.itv.cmvideo.cn";
    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        try {
            switch (getStreamType(path)) {  //xuameng新增
                case RTSP_UDP_RTP:
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", 1);
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp");
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512 * 1000);
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 2 * 1000 * 1000);
                    break;

                case CACHE_VIDEO:
                    if (Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)) {
                        String cachePath = FileUtils.getCachePath() + "/ijkcaches/";
                        File cacheFile = new File(cachePath);
                        if (!cacheFile.exists()) cacheFile.mkdirs();
                        String tmpMd5 = MD5.string2MD5(path);
                        String cacheFilePath = cachePath + tmpMd5 + ".file";
                        String cacheMapPath = cachePath + tmpMd5 + ".map";

                        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_file_path", cacheFilePath);
                        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_map_path", cacheMapPath);
                        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "parse_cache_map", 1);
                        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "auto_save_map", 1);
                        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_max_capacity", 60 * 1024 * 1024);
                        path = "ijkio:cache:ffio:" + path;
                    }
                    break;

                case M3U8:
                    // 直播且是ijk的时候自动自动走代理解决DNS
                    if (Hawk.get(HawkConfig.PLAYER_IS_LIVE, false) ) {
                        URI uri = new URI(path);
                        String host = uri.getHost();
                        if(ITV_TARGET_DOMAIN.equalsIgnoreCase(host))path = ControlManager.get().getAddress(true) + "proxy?go=live&type=m3u8&url="+ URLEncoder.encode(path,"UTF-8");
                    }
                    break;

                default:
                    break;  //xuameng新增完
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        setDataSourceHeader(headers);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "ijkio,ffio,async,cache,crypto,file,dash,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data");
		super.setDataSource(path, null);
    }

    /**
     * 解析 URL  //xuameng新增
     */
    private static final int RTSP_UDP_RTP = 1;
    private static final int CACHE_VIDEO = 2;
    private static final int M3U8 = 3;
    private static final int OTHER = 0;

    private int getStreamType(String path) {
        if (TextUtils.isEmpty(path)) {
            return OTHER;
        }
        // 低成本检查 RTSP/UDP/RTP 类型
        String lowerPath = path.toLowerCase();
        if (lowerPath.startsWith("rtsp://") || lowerPath.startsWith("udp://") || lowerPath.startsWith("rtp://")) {
            return RTSP_UDP_RTP;
        }
        String cleanUrl = path.split("\\?")[0];
        if (cleanUrl.endsWith(".m3u8")) {
            return M3U8;
        }
        if (cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".mkv") || cleanUrl.endsWith(".avi")) {
            return CACHE_VIDEO;
        }
        return OTHER;
    }         //xuameng新增完

    private void setDataSourceHeader(Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            String userAgent = headers.get("User-Agent");
            if (!TextUtils.isEmpty(userAgent)) {
                mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent);
                // 移除header中的User-Agent，防止重复
                headers.remove("User-Agent");
            }
            if (headers.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String value = entry.getValue();
                    if (!TextUtils.isEmpty(value)) {
                        sb.append(entry.getKey());
                        sb.append(": ");
                        sb.append(value);
                        sb.append("\r\n");
                    }
                }
                mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", sb.toString());
            }
        }
    }

    public TrackInfo getTrackInfo() {
        IjkTrackInfo[] trackInfo = mMediaPlayer.getTrackInfo();
        if (trackInfo == null) return null;
        TrackInfo data = new TrackInfo();
        int subtitleSelected = mMediaPlayer.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);
        int audioSelected = mMediaPlayer.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
        int index = 0;
        for (IjkTrackInfo info : trackInfo) {
            if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) {//音轨信息
				String trackName = (data.getAudio().size() + 1) + "：" + info.getInfoInline();       //xuameng 音轨信息
                TrackInfoBean a = new TrackInfoBean();
                a.name = trackName;
                a.language = "";
                a.trackId = index;
                a.selected = index == audioSelected;
                // 如果需要，还可以检查轨道的描述或标题以获取更多信息
                data.addAudio(a);
            }
            if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {//内置字幕
				String trackName = "";
                TrackInfoBean t = new TrackInfoBean();
                t.name = trackName;
                t.language = (data.getSubtitle().size() + 1) + "：" + info.getInfoInline();   //xuameng 字幕信息
                t.trackId = index;
                t.selected = index == subtitleSelected;
                data.addSubtitle(t);
            }
            index++;
        }
        return data;
    }

    public void setTrack(int trackIndex) {
        int audioSelected = mMediaPlayer.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
        int subtitleSelected = mMediaPlayer.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);
        if (trackIndex!=audioSelected && trackIndex!=subtitleSelected){
            mMediaPlayer.selectTrack(trackIndex);
        }
    }
    public void setTrack(int trackIndex,String playKey) {     //xuameng记忆选择音轨
        int audioSelected = mMediaPlayer.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
        if (trackIndex!=audioSelected){
            if (!playKey.isEmpty()) {
                memory.save(playKey, trackIndex);
            }
            mMediaPlayer.selectTrack(trackIndex);
        }
    }

    public void setOnTimedTextListener(IMediaPlayer.OnTimedTextListener listener) {
        mMediaPlayer.setOnTimedTextListener(listener);
    }

    //xuameng有上次选中的加载上次选中的，没有选中默认选中文音轨，没有中文音轨，默认第一条音轨
    public void loadDefaultTrack(TrackInfo trackInfo, String playKey) {
    // 基础参数校验
        if (trackInfo == null || trackInfo.getAudio().isEmpty()) {
            return;
        }
        // 1. 优先加载上次选中的
        Integer trackIndex = memory.ijkLoad(playKey);
        if (trackIndex != -1) {
            setTrack(trackIndex);
            return;
        }
        // 2. 智能中文音轨检测
        List<TrackInfoBean> audioTracks = trackInfo.getAudio();
        for (TrackInfoBean track : audioTracks) {
            if (isChineseTrack(track, trackInfo)) {
                setTrack(track.trackId);
                memory.save(playKey, track.trackId);   //xuameng保存选择
                return;
            }
        }
        // 3. 默认选择第一条
        setTrack(audioTracks.get(0).trackId);
    }

    private boolean isChineseTrack(TrackInfoBean track, TrackInfo trackInfo) {   //xuameng 音轨语言名称匹配
        // 基础语言匹配
        String language = track.language != null ? track.language.toLowerCase() : "";
        String name = track.name != null ? track.name.toLowerCase() : "";
        // 名称关键词匹配（中文常见标识）
        if (name.contains("中文") || name.contains("国语") || name.contains("简体")) {
        return true;
        }
        // 语言代码扩展（多语言标准支持）
        return language.matches("zh|chi|zho|cn|chinese|中文|国语|简体|国配");
    }
}
