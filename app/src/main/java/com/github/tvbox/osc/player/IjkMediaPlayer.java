package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import xyz.doikki.videoplayer.ijk.IjkPlayer;

public class IjkMediaPlayer extends IjkPlayer {

    private IJKCode codec = null;

    public IjkMediaPlayer(Context context, IJKCode codec) {
        super(context);
        this.codec = codec;
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

        // 在每个数据包之后启用 I/O 上下文的刷新
 //       mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1);        xuameng不上下文的刷新
        // 当 CPU 处理不过来的时候的丢帧帧数，默认为 0，参数范围是 [-1, 120]
//        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5);          xuameng不丢帧
        // 设置视频流格式
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", tv.danmaku.ijk.media.player.IjkMediaPlayer.SDL_FCC_RV32);

        //开启内置字幕
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1);

        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1);
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        try {
            if (path.contains("rtsp") || path.contains("udp") || path.contains("rtp")) {
                mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", 1);
                mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
                mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp");
                mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512 * 1000);
                mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 2 * 1000 * 1000);
            } else if (!TextUtils.isEmpty(path)
                    && !path.contains(".m3u8")
                    && (path.contains(".mp4") || path.contains(".mkv") || path.contains(".avi"))) {
                if (Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)) {
                    String cachePath = FileUtils.getCachePath() + "/ijkcaches/";
                    String cacheMapPath = cachePath;
                    File cacheFile = new File(cachePath);
                    if (!cacheFile.exists()) cacheFile.mkdirs();
                    String tmpMd5 = MD5.string2MD5(path);
                    cachePath += tmpMd5 + ".file";
                    cacheMapPath += tmpMd5 + ".map";
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_file_path", cachePath);
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_map_path", cacheMapPath);
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "parse_cache_map", 1);
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "auto_save_map", 1);
                    mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_max_capacity", 60 * 1024 * 1024);
                    path = "ijkio:cache:ffio:" + path;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        setDataSourceHeader(headers);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "ijkio,ffio,async,cache,crypto,file,dash,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data");
        super.setDataSource(path, null);
    }

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
				String audioLanguage = info.getLanguage();   //xuameng显示字幕类型
				String ch = "chi";  //xuameng过滤字幕类型里application/字符串
				String change = "中文";
				if(audioLanguage.contains(ch)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(ch, change);  //xuameng过滤字幕类型里application/字符串
				}
				String zhi = "zhi";  //xuameng过滤字幕类型里application/字符串
				String changezhi = "中文";
				if(audioLanguage.contains(zhi)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(zhi, changezhi);  //xuameng过滤字幕类型里application/字符串
				}
				String eng = "eng";  //xuameng过滤字幕类型里application/字符串
				String changeeng = "英语";
				if(audioLanguage.contains(eng)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(eng, changeeng);  //xuameng过滤字幕类型里application/字符串
				}
				String ara = "ara";  //xuameng过滤字幕类型里application/字符串
				String changeara = "阿拉伯语";
				if(audioLanguage.contains(ara)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(ara, changeara);  //xuameng过滤字幕类型里application/字符串
				}
				String bul = "bul";  //xuameng过滤字幕类型里application/字符串
				String changebul = "保加利亚语";
				if(audioLanguage.contains(bul)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(bul, changebul);  //xuameng过滤字幕类型里application/字符串
				}
				String cze = "cze";  //xuameng过滤字幕类型里application/字符串
				String changecze = "捷克语";
				if(audioLanguage.contains(cze)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(cze, changecze);  //xuameng过滤字幕类型里application/字符串
				}
				String dan = "dan";  //xuameng过滤字幕类型里application/字符串
				String changedan = "丹麦语";
				if(audioLanguage.contains(dan)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(dan, changedan);  //xuameng过滤字幕类型里application/字符串
				}
				String ger = "ger";  //xuameng过滤字幕类型里application/字符串
				String changeger = "德语";
				if(audioLanguage.contains(ger)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(ger, changeger);  //xuameng过滤字幕类型里application/字符串
				}
				String gre = "gre";  //xuameng过滤字幕类型里application/字符串
				String changegre = "希腊语";
				if(audioLanguage.contains(gre)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(gre, changegre);  //xuameng过滤字幕类型里application/字符串
				}
				String spa = "spa";  //xuameng过滤字幕类型里application/字符串
				String changespa = "西班牙语";
				if(audioLanguage.contains(spa)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(spa, changespa);  //xuameng过滤字幕类型里application/字符串
				}
				String est = "est";  //xuameng过滤字幕类型里application/字符串
				String changeest = "爱沙尼亚语";
				if(audioLanguage.contains(est)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(est, changeest);  //xuameng过滤字幕类型里application/字符串
				}
				String fin = "fin";  //xuameng过滤字幕类型里application/字符串
				String changefin = "芬兰语";
				if(audioLanguage.contains(fin)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(fin, changefin);  //xuameng过滤字幕类型里application/字符串
				}
				String fre = "fre";  //xuameng过滤字幕类型里application/字符串
				String changefre = "法语";
				if(audioLanguage.contains(fre)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(fre, changefre);  //xuameng过滤字幕类型里application/字符串
				}
				String heb = "heb";  //xuameng过滤字幕类型里application/字符串
				String changeheb = "希伯来语";
				if(audioLanguage.contains(heb)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(heb, changeheb);  //xuameng过滤字幕类型里application/字符串
				}
				String hin = "hin";  //xuameng过滤字幕类型里application/字符串
				String changehin = "印地语";
				if(audioLanguage.contains(hin)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(hin, changehin);  //xuameng过滤字幕类型里application/字符串
				}
				String hun = "hun";  //xuameng过滤字幕类型里application/字符串
				String changehun = "匈牙利语";
				if(audioLanguage.contains(hun)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(hun, changehun);  //xuameng过滤字幕类型里application/字符串
				}
				String ind = "ind";  //xuameng过滤字幕类型里application/字符串
				String changeind = "印度尼西亚语";
				if(audioLanguage.contains(ind)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(ind, changeind);  //xuameng过滤字幕类型里application/字符串
				}
				String ita = "ita";  //xuameng过滤字幕类型里application/字符串
				String changeita = "意大利语";
				if(audioLanguage.contains(ita)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(ita, changeita);  //xuameng过滤字幕类型里application/字符串
				}
				String jpn = "jpn";  //xuameng过滤字幕类型里application/字符串
				String changejpn = "日语";
				if(audioLanguage.contains(jpn)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(jpn, changejpn);  //xuameng过滤字幕类型里application/字符串
				}
				String kor = "kor";  //xuameng过滤字幕类型里application/字符串
				String changekor = "韩语";
				if(audioLanguage.contains(kor)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(kor, changekor);  //xuameng过滤字幕类型里application/字符串
				}
				String lit = "lit";  //xuameng过滤字幕类型里application/字符串
				String changelit = "立陶宛语";
				if(audioLanguage.contains(lit)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(lit, changelit);  //xuameng过滤字幕类型里application/字符串
				}
				String lav = "lav";  //xuameng过滤字幕类型里application/字符串
				String changelav = "拉脱维亚语";
				if(audioLanguage.contains(lav)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(lav, changelav);  //xuameng过滤字幕类型里application/字符串
				}
				String may = "may";  //xuameng过滤字幕类型里application/字符串
				String changemay = "马来语";
				if(audioLanguage.contains(may)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(may, changemay);  //xuameng过滤字幕类型里application/字符串
				}
				String dut = "dut";  //xuameng过滤字幕类型里application/字符串
				String changedut = "荷兰语";
				if(audioLanguage.contains(dut)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(dut, changedut);  //xuameng过滤字幕类型里application/字符串
				}
				String nor = "nor";  //xuameng过滤字幕类型里application/字符串
				String changenor = "挪威语";
				if(audioLanguage.contains(nor)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(nor, changenor);  //xuameng过滤字幕类型里application/字符串
				}
				String pol = "pol";  //xuameng过滤字幕类型里application/字符串
				String changepol = "波兰语";
				if(audioLanguage.contains(pol)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(pol, changepol);  //xuameng过滤字幕类型里application/字符串
				}
				String por = "por";  //xuameng过滤字幕类型里application/字符串
				String changepor = "葡萄牙语";
				if(audioLanguage.contains(por)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(por, changepor);  //xuameng过滤字幕类型里application/字符串
				}
				String rus = "rus";  //xuameng过滤字幕类型里application/字符串
				String changerus = "俄语";
				if(audioLanguage.contains(rus)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(rus, changerus);  //xuameng过滤字幕类型里application/字符串
				}
				String slo = "slo";  //xuameng过滤字幕类型里application/字符串
				String changeslo = "斯洛伐克语";
				if(audioLanguage.contains(slo)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(slo, changeslo);  //xuameng过滤字幕类型里application/字符串
				}
				String slv = "slv";  //xuameng过滤字幕类型里application/字符串
				String changeslv = "斯洛文尼亚语";
				if(audioLanguage.contains(slv)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(slv, changeslv);  //xuameng过滤字幕类型里application/字符串
				}
				String swe = "swe";  //xuameng过滤字幕类型里application/字符串
				String changeswe = "瑞典语";
				if(audioLanguage.contains(swe)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(swe, changeswe);  //xuameng过滤字幕类型里application/字符串
				}
				String tam = "tam";  //xuameng过滤字幕类型里application/字符串
				String changetam = "泰米尔语";
				if(audioLanguage.contains(tam)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(tam, changetam);  //xuameng过滤字幕类型里application/字符串
				}
				String tel = "tel";  //xuameng过滤字幕类型里application/字符串
				String changetel = "泰卢固语";
				if(audioLanguage.contains(tel)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(tel, changetel);  //xuameng过滤字幕类型里application/字符串
				}
				String tha = "tha";  //xuameng过滤字幕类型里application/字符串
				String changetha = "泰语";
				if(audioLanguage.contains(tha)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(tha, changetha);  //xuameng过滤字幕类型里application/字符串
				}
				String ukr = "ukr";  //xuameng过滤字幕类型里application/字符串
				String changeukr = "乌克兰语";
				if(audioLanguage.contains(ukr)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(ukr, changeukr);  //xuameng过滤字幕类型里application/字符串
				}
				String vie = "vie";  //xuameng过滤字幕类型里application/字符串
				String changevie = "越南语";
				if(audioLanguage.contains(vie)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(vie, changevie);  //xuameng过滤字幕类型里application/字符串
				}
				String tur = "tur";  //xuameng过滤字幕类型里application/字符串
				String changetur = "土耳其语";
				if(audioLanguage.contains(tur)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(tur, changetur);  //xuameng过滤字幕类型里application/字符串
				}
				String und = "und";  //xuameng过滤字幕类型里application/字符串
				String changeund = "未知";
				if(audioLanguage.contains(und)) {  //xuameng过滤字幕类型里application/字符串
					audioLanguage = audioLanguage.replace(und, changeund);  //xuameng过滤字幕类型里application/字符串
				}
				String trackName = (data.getAudio().size() + 1) + "：" + audioLanguage + ", " + info.getInfoInline();
                TrackInfoBean a = new TrackInfoBean();
                a.name = trackName;
                a.language = "";
                a.trackId = index;
                a.selected = index == audioSelected;
                // 如果需要，还可以检查轨道的描述或标题以获取更多信息
                data.addAudio(a);
            }
            if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {//内置字幕
				String zimuLanguage = info.getLanguage();   //xuameng显示字幕类型
				String ch = "chi";  //xuameng过滤字幕类型里application/字符串
				String change = "中文";
				if(zimuLanguage.contains(ch)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(ch, change);  //xuameng过滤字幕类型里application/字符串
				}
				String zhi = "zhi";  //xuameng过滤字幕类型里application/字符串
				String changezhi = "中文";
				if(zimuLanguage.contains(zhi)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(zhi, changezhi);  //xuameng过滤字幕类型里application/字符串
				}
				String eng = "eng";  //xuameng过滤字幕类型里application/字符串
				String changeeng = "英语";
				if(zimuLanguage.contains(eng)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(eng, changeeng);  //xuameng过滤字幕类型里application/字符串
				}
				String ara = "ara";  //xuameng过滤字幕类型里application/字符串
				String changeara = "阿拉伯语";
				if(zimuLanguage.contains(ara)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(ara, changeara);  //xuameng过滤字幕类型里application/字符串
				}
				String bul = "bul";  //xuameng过滤字幕类型里application/字符串
				String changebul = "保加利亚语";
				if(zimuLanguage.contains(bul)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(bul, changebul);  //xuameng过滤字幕类型里application/字符串
				}
				String cze = "cze";  //xuameng过滤字幕类型里application/字符串
				String changecze = "捷克语";
				if(zimuLanguage.contains(cze)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(cze, changecze);  //xuameng过滤字幕类型里application/字符串
				}
				String dan = "dan";  //xuameng过滤字幕类型里application/字符串
				String changedan = "丹麦语";
				if(zimuLanguage.contains(dan)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(dan, changedan);  //xuameng过滤字幕类型里application/字符串
				}
				String ger = "ger";  //xuameng过滤字幕类型里application/字符串
				String changeger = "德语";
				if(zimuLanguage.contains(ger)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(ger, changeger);  //xuameng过滤字幕类型里application/字符串
				}
				String gre = "gre";  //xuameng过滤字幕类型里application/字符串
				String changegre = "希腊语";
				if(zimuLanguage.contains(gre)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(gre, changegre);  //xuameng过滤字幕类型里application/字符串
				}
				String spa = "spa";  //xuameng过滤字幕类型里application/字符串
				String changespa = "西班牙语";
				if(zimuLanguage.contains(spa)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(spa, changespa);  //xuameng过滤字幕类型里application/字符串
				}
				String est = "est";  //xuameng过滤字幕类型里application/字符串
				String changeest = "爱沙尼亚语";
				if(zimuLanguage.contains(est)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(est, changeest);  //xuameng过滤字幕类型里application/字符串
				}
				String fin = "fin";  //xuameng过滤字幕类型里application/字符串
				String changefin = "芬兰语";
				if(zimuLanguage.contains(fin)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(fin, changefin);  //xuameng过滤字幕类型里application/字符串
				}
				String fre = "fre";  //xuameng过滤字幕类型里application/字符串
				String changefre = "法语";
				if(zimuLanguage.contains(fre)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(fre, changefre);  //xuameng过滤字幕类型里application/字符串
				}
				String heb = "heb";  //xuameng过滤字幕类型里application/字符串
				String changeheb = "希伯来语";
				if(zimuLanguage.contains(heb)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(heb, changeheb);  //xuameng过滤字幕类型里application/字符串
				}
				String hin = "hin";  //xuameng过滤字幕类型里application/字符串
				String changehin = "印地语";
				if(zimuLanguage.contains(hin)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(hin, changehin);  //xuameng过滤字幕类型里application/字符串
				}
				String hun = "hun";  //xuameng过滤字幕类型里application/字符串
				String changehun = "匈牙利语";
				if(zimuLanguage.contains(hun)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(hun, changehun);  //xuameng过滤字幕类型里application/字符串
				}
				String ind = "ind";  //xuameng过滤字幕类型里application/字符串
				String changeind = "印度尼西亚语";
				if(zimuLanguage.contains(ind)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(ind, changeind);  //xuameng过滤字幕类型里application/字符串
				}
				String ita = "ita";  //xuameng过滤字幕类型里application/字符串
				String changeita = "意大利语";
				if(zimuLanguage.contains(ita)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(ita, changeita);  //xuameng过滤字幕类型里application/字符串
				}
				String jpn = "jpn";  //xuameng过滤字幕类型里application/字符串
				String changejpn = "日语";
				if(zimuLanguage.contains(jpn)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(jpn, changejpn);  //xuameng过滤字幕类型里application/字符串
				}
				String kor = "kor";  //xuameng过滤字幕类型里application/字符串
				String changekor = "韩语";
				if(zimuLanguage.contains(kor)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(kor, changekor);  //xuameng过滤字幕类型里application/字符串
				}
				String lit = "lit";  //xuameng过滤字幕类型里application/字符串
				String changelit = "立陶宛语";
				if(zimuLanguage.contains(lit)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(lit, changelit);  //xuameng过滤字幕类型里application/字符串
				}
				String lav = "lav";  //xuameng过滤字幕类型里application/字符串
				String changelav = "拉脱维亚语";
				if(zimuLanguage.contains(lav)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(lav, changelav);  //xuameng过滤字幕类型里application/字符串
				}
				String may = "may";  //xuameng过滤字幕类型里application/字符串
				String changemay = "马来语";
				if(zimuLanguage.contains(may)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(may, changemay);  //xuameng过滤字幕类型里application/字符串
				}
				String dut = "dut";  //xuameng过滤字幕类型里application/字符串
				String changedut = "荷兰语";
				if(zimuLanguage.contains(dut)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(dut, changedut);  //xuameng过滤字幕类型里application/字符串
				}
				String nor = "nor";  //xuameng过滤字幕类型里application/字符串
				String changenor = "挪威语";
				if(zimuLanguage.contains(nor)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(nor, changenor);  //xuameng过滤字幕类型里application/字符串
				}
				String pol = "pol";  //xuameng过滤字幕类型里application/字符串
				String changepol = "波兰语";
				if(zimuLanguage.contains(pol)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(pol, changepol);  //xuameng过滤字幕类型里application/字符串
				}
				String por = "por";  //xuameng过滤字幕类型里application/字符串
				String changepor = "葡萄牙语";
				if(zimuLanguage.contains(por)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(por, changepor);  //xuameng过滤字幕类型里application/字符串
				}
				String rus = "rus";  //xuameng过滤字幕类型里application/字符串
				String changerus = "俄语";
				if(zimuLanguage.contains(rus)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(rus, changerus);  //xuameng过滤字幕类型里application/字符串
				}
				String slo = "slo";  //xuameng过滤字幕类型里application/字符串
				String changeslo = "斯洛伐克语";
				if(zimuLanguage.contains(slo)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(slo, changeslo);  //xuameng过滤字幕类型里application/字符串
				}
				String slv = "slv";  //xuameng过滤字幕类型里application/字符串
				String changeslv = "斯洛文尼亚语";
				if(zimuLanguage.contains(slv)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(slv, changeslv);  //xuameng过滤字幕类型里application/字符串
				}
				String swe = "swe";  //xuameng过滤字幕类型里application/字符串
				String changeswe = "瑞典语";
				if(zimuLanguage.contains(swe)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(swe, changeswe);  //xuameng过滤字幕类型里application/字符串
				}
				String tam = "tam";  //xuameng过滤字幕类型里application/字符串
				String changetam = "泰米尔语";
				if(zimuLanguage.contains(tam)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(tam, changetam);  //xuameng过滤字幕类型里application/字符串
				}
				String tel = "tel";  //xuameng过滤字幕类型里application/字符串
				String changetel = "泰卢固语";
				if(zimuLanguage.contains(tel)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(tel, changetel);  //xuameng过滤字幕类型里application/字符串
				}
				String tha = "tha";  //xuameng过滤字幕类型里application/字符串
				String changetha = "泰语";
				if(zimuLanguage.contains(tha)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(tha, changetha);  //xuameng过滤字幕类型里application/字符串
				}
				String ukr = "ukr";  //xuameng过滤字幕类型里application/字符串
				String changeukr = "乌克兰语";
				if(zimuLanguage.contains(ukr)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(ukr, changeukr);  //xuameng过滤字幕类型里application/字符串
				}
				String vie = "vie";  //xuameng过滤字幕类型里application/字符串
				String changevie = "越南语";
				if(zimuLanguage.contains(vie)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(vie, changevie);  //xuameng过滤字幕类型里application/字符串
				}
				String tur = "tur";  //xuameng过滤字幕类型里application/字符串
				String changetur = "土耳其语";
				if(zimuLanguage.contains(tur)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(tur, changetur);  //xuameng过滤字幕类型里application/字符串
				}
				String und = "und";  //xuameng过滤字幕类型里application/字符串
				String changeund = "未知";
				if(zimuLanguage.contains(und)) {  //xuameng过滤字幕类型里application/字符串
					zimuLanguage = zimuLanguage.replace(und, changeund);  //xuameng过滤字幕类型里application/字符串
				}
				String trackName = (data.getSubtitle().size() + 1) + "：" + zimuLanguage + ", " + info.getInfoInline();
                TrackInfoBean t = new TrackInfoBean();
                t.name = trackName;
                t.language = "";
                t.trackId = index;
                t.selected = index == subtitleSelected;
                data.addSubtitle(t);
            }
            index++;
        }
        return data;
    }

    public void setTrack(int trackIndex) {
        mMediaPlayer.selectTrack(trackIndex);
    }

    public void setOnTimedTextListener(IMediaPlayer.OnTimedTextListener listener) {
        mMediaPlayer.setOnTimedTextListener(listener);
    }

}
