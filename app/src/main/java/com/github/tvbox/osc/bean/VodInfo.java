package com.github.tvbox.osc.bean;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import android.util.Log;  //xuameng 错误日志

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class VodInfo implements Serializable {
    public String last;//时间
    //内容id
    public String id;
    //父级id
    public int tid;
    //影片名称 <![CDATA[老爸当家]]>
    public String name;
    //类型名称
    public String type;
    //视频分类zuidam3u8,zuidall
    public String dt;
    //图片
    public String pic;
    //语言
    public String lang;
    //地区
    public String area;
    //年份
    public int year;
    public String state;
    //描述集数或者影片信息<![CDATA[共40集]]>
    public String note;
    //演员<![CDATA[张国立,蒋欣,高鑫,曹艳艳,王维维,韩丹彤,孟秀,王新]]>
    public String actor;
    //导演<![CDATA[陈国星]]>
    public String director;
    public ArrayList<VodSeriesFlag> seriesFlags;
    public LinkedHashMap<String, List<VodSeries>> seriesMap;
    public String des;// <![CDATA[权来]
    public String playFlag = null;
    public int playIndex = 0;
    public String playNote = "";
    public String sourceKey;
    public String playerCfg = "";
    public boolean reverseSort = false;
    // 新增：记录当前播放的源和剧集索引（用于切换源时恢复位置）
    public String currentPlayFlag = null; // 当前播放的源（flag）
    public int currentPlayIndex = 0;      // 当前播放的剧集索引
	public Movie.Video video; // xuameng添加video字段存储

    public void setVideo(Movie.Video video) {
        this.video = video; // xuameng存储video对象
        last = video.last;
        id = video.id;
        tid = video.tid;
        name = video.name;
        type = video.type;
        // dt = video.dt;
        pic = video.pic;
        lang = video.lang;
        area = video.area;
        year = video.year;
        state = video.state;
        note = video.note;
        actor = video.actor;
        director = video.director;
        des = video.des;
        if (video.urlBean != null && video.urlBean.infoList != null && video.urlBean.infoList.size() > 0) {
            LinkedHashMap<String, List<VodSeries>> tempSeriesMap = new LinkedHashMap<>();
            seriesFlags = new ArrayList<>();
            for (Movie.Video.UrlBean.UrlInfo urlInfo : video.urlBean.infoList) {
                if (urlInfo.beanList != null && urlInfo.beanList.size() > 0) {
                    List<VodSeries> seriesList = new ArrayList<>();
                    for (Movie.Video.UrlBean.UrlInfo.InfoBean infoBean : urlInfo.beanList) {
                        seriesList.add(new VodSeries(infoBean.name, infoBean.url));
                    }
                    tempSeriesMap.put(urlInfo.flag, seriesList);
                    seriesFlags.add(new VodSeriesFlag(urlInfo.flag));
                }
            }

            seriesMap = new LinkedHashMap<>();
            for (VodSeriesFlag flag : seriesFlags) {
                List<VodSeries> list = tempSeriesMap.get(flag.name);
                assert list != null;
                if(seriesFlags.size()<=5){    //节目源小于等于5排序
                    if(isReverse(list))Collections.reverse(list);
                }
                seriesMap.put(flag.name, list);
            }
        }
    }

    // xuameng存储video对象在VodInfo类中添加getVideo()方法
    public Movie.Video getVideo() {
        return null; 
    }

    private int extractNumber(String name) {
        // xuameng将 Unicode 数字转换为标准 ASCII 数字  解决全角字符闪退
        String normalized = name.replaceAll("[\\p{Ll}\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}]", "");
        java.util.regex.Matcher matcher = getPattern("\\d+").matcher(normalized);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                Log.e("ExtractNumber", "Invalid number format: " + normalized, e);
                return 0;
            }
        }
        return 0;
    }

    private boolean isReverse(List<VodInfo.VodSeries> list) {
        if (list.size() > 300) {      //xuameng集数大于300返回
            return false;
        }
        int ascCount = 0, descCount = 0;
        // 比较最多前 6 个相邻元素对
        int limit = Math.min(list.size() - 1, 6);
        for (int i = 0; i < limit; i++) {
            int current = extractNumber(list.get(i).name);
            int next = extractNumber(list.get(i + 1).name);
            if (current < next) {
                ascCount++;
                if (ascCount == 2) return false;
            } else if (current > next) {
                descCount++;
                if (descCount == 2) return true;
            }
        }
        return false;
    }

    public void reverse() {
        Set<String> flags = seriesMap.keySet();
        for (String flag : flags) {
            Collections.reverse(seriesMap.get(flag));
        }
    }

    public static class VodSeriesFlag implements Serializable {

        public String name;
        public boolean selected;

        public VodSeriesFlag() {

        }

        public VodSeriesFlag(String name) {
            this.name = name;
        }
    }

    public static class VodSeries implements Serializable {

        public String name;
        public String url;
        public boolean selected;

        public VodSeries() {
        }

        public VodSeries(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
