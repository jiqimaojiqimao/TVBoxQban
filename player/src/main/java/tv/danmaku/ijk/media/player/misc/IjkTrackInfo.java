/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.player.misc;

import tv.danmaku.ijk.media.player.IjkMediaMeta;

import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author xuameng
 * @date :2026/08/30
 * @description:  字符转换重构
 */

public class IjkTrackInfo implements ITrackInfo {
    private int mTrackType = MEDIA_TRACK_TYPE_UNKNOWN;
    public IjkMediaMeta.IjkStreamMeta mStreamMeta;

    public IjkTrackInfo(IjkMediaMeta.IjkStreamMeta streamMeta) {
        mStreamMeta = streamMeta;
    }

    public void setMediaMeta(IjkMediaMeta.IjkStreamMeta streamMeta) {
        mStreamMeta = streamMeta;
    }

    @Override
    public IMediaFormat getFormat() {
        return new IjkMediaFormat(mStreamMeta);
    }

    @Override
    public String getLanguage() {  //xuameng 语言
        String language = mStreamMeta.mLanguage;
        if (language == null || TextUtils.isEmpty(language)) {
            return "未知";
        }
        return cleanLanguage(language);
    }

    private static final LinkedHashMap<String, String> LANGUAGE_REPLACE_MAP = new LinkedHashMap<>();

    static {
        // 中文系
        LANGUAGE_REPLACE_MAP.put("chi", "中文");
        LANGUAGE_REPLACE_MAP.put("zhi", "中文");
        LANGUAGE_REPLACE_MAP.put("zho", "中文");
        LANGUAGE_REPLACE_MAP.put("wuu", "吴语");

        // 英语
        LANGUAGE_REPLACE_MAP.put("eng", "英语");

        // 日语 / 韩语
        LANGUAGE_REPLACE_MAP.put("jpn", "日语");
        LANGUAGE_REPLACE_MAP.put("kor", "韩语");

        // 欧洲语言 - 罗曼语族
        LANGUAGE_REPLACE_MAP.put("fra", "法语");
        LANGUAGE_REPLACE_MAP.put("fre", "法语");
        LANGUAGE_REPLACE_MAP.put("spa", "西班牙语");
        LANGUAGE_REPLACE_MAP.put("ita", "意大利语");
        LANGUAGE_REPLACE_MAP.put("por", "葡萄牙语");
        LANGUAGE_REPLACE_MAP.put("ron", "罗马尼亚语");
        LANGUAGE_REPLACE_MAP.put("rum", "罗马尼亚语");
        LANGUAGE_REPLACE_MAP.put("cat", "加泰罗尼亚语");
        LANGUAGE_REPLACE_MAP.put("glg", "加利西亚语");

        // 欧洲语言 - 日耳曼语族
        LANGUAGE_REPLACE_MAP.put("deu", "德语");
        LANGUAGE_REPLACE_MAP.put("ger", "德语");
        LANGUAGE_REPLACE_MAP.put("nld", "荷兰语");
        LANGUAGE_REPLACE_MAP.put("dut", "荷兰语");
        LANGUAGE_REPLACE_MAP.put("swe", "瑞典语");
        LANGUAGE_REPLACE_MAP.put("nor", "挪威语");
        LANGUAGE_REPLACE_MAP.put("nob", "书面挪威语");
        LANGUAGE_REPLACE_MAP.put("fin", "芬兰语");
        LANGUAGE_REPLACE_MAP.put("dan", "丹麦语");
        LANGUAGE_REPLACE_MAP.put("eng", "英语");

        // 欧洲语言 - 斯拉夫语族
        LANGUAGE_REPLACE_MAP.put("rus", "俄语");
        LANGUAGE_REPLACE_MAP.put("pol", "波兰语");
        LANGUAGE_REPLACE_MAP.put("ukr", "乌克兰语");
        LANGUAGE_REPLACE_MAP.put("ces", "捷克语");
        LANGUAGE_REPLACE_MAP.put("cze", "捷克语");
        LANGUAGE_REPLACE_MAP.put("slk", "斯洛伐克语");  
        LANGUAGE_REPLACE_MAP.put("slo", "斯洛伐克语");
        LANGUAGE_REPLACE_MAP.put("slv", "斯洛文尼亚语");
        LANGUAGE_REPLACE_MAP.put("hrv", "克罗地亚语");
        LANGUAGE_REPLACE_MAP.put("bul", "保加利亚语");

        // 欧洲语言 - 波罗的海
        LANGUAGE_REPLACE_MAP.put("lit", "立陶宛语");
        LANGUAGE_REPLACE_MAP.put("lav", "拉脱维亚语");
        LANGUAGE_REPLACE_MAP.put("est", "爱沙尼亚语");
        LANGUAGE_REPLACE_MAP.put("ell", "希腊语");  
        LANGUAGE_REPLACE_MAP.put("gre", "希腊语");

        // 其他
        LANGUAGE_REPLACE_MAP.put("hun", "匈牙利语");
        LANGUAGE_REPLACE_MAP.put("heb", "希伯来语");
        LANGUAGE_REPLACE_MAP.put("tur", "土耳其语");
        LANGUAGE_REPLACE_MAP.put("ara", "阿拉伯语");
        LANGUAGE_REPLACE_MAP.put("per", "波斯语");   
        LANGUAGE_REPLACE_MAP.put("fas", "波斯语");   
        LANGUAGE_REPLACE_MAP.put("hin", "印地语");
        LANGUAGE_REPLACE_MAP.put("vie", "越南语");
        LANGUAGE_REPLACE_MAP.put("tha", "泰语");
        LANGUAGE_REPLACE_MAP.put("ind", "印度尼西亚语");
        LANGUAGE_REPLACE_MAP.put("may", "马来语");
        LANGUAGE_REPLACE_MAP.put("msa", "马来语");   
        LANGUAGE_REPLACE_MAP.put("fil", "菲律宾语");
        LANGUAGE_REPLACE_MAP.put("tam", "泰米尔语");
        LANGUAGE_REPLACE_MAP.put("tel", "泰卢固语");
        LANGUAGE_REPLACE_MAP.put("kan", "卡纳达语");
        LANGUAGE_REPLACE_MAP.put("mal", "马拉雅拉姆语");
        LANGUAGE_REPLACE_MAP.put("baq", "巴斯克语");
        LANGUAGE_REPLACE_MAP.put("eus", "巴斯克语"); 
        LANGUAGE_REPLACE_MAP.put("aze", "阿塞拜疆语");
        LANGUAGE_REPLACE_MAP.put("mon", "蒙古语");
        LANGUAGE_REPLACE_MAP.put("aka", "阿坎语");
        LANGUAGE_REPLACE_MAP.put("aym", "艾马拉语");

        // 特殊
        LANGUAGE_REPLACE_MAP.put("mul", "多语言");
        LANGUAGE_REPLACE_MAP.put("und", "未知");
        LANGUAGE_REPLACE_MAP.put("Aud", "未知");
        LANGUAGE_REPLACE_MAP.put("aud", "未知");
    }

    private static String cleanLanguage(String input) {
        if (TextUtils.isEmpty(input)) {
            return "未知";
        }
        String result = input;
        for (Map.Entry<String, String> entry : LANGUAGE_REPLACE_MAP.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public String getCodecName() {
        if (!TextUtils.isEmpty(mStreamMeta.mCodecLongName)) {
            return mStreamMeta.mCodecLongName;
        } else if (!TextUtils.isEmpty(mStreamMeta.mCodecName)) {
            return mStreamMeta.mCodecName;
        } else {
            return "null";
        }
    }

    public String getMCodecName() {  //xuameng 编码
        String codecName = getCodecName();
        if (codecName == null || TextUtils.isEmpty(codecName)) {
            return "未知";
        }
        return cleanCodec(codecName);
    }

    private static final LinkedHashMap<String, String> CODEC_REPLACE_MAP = new LinkedHashMap<>();

    static {
        // 字幕编码
        CODEC_REPLACE_MAP.put("hdmv_pgs_subtitle", "pgs");
        CODEC_REPLACE_MAP.put("mov_text", "tx3g");
        CODEC_REPLACE_MAP.put("dvd_subtitle", "vobsub");

        // 音频编码
        CODEC_REPLACE_MAP.put("truehd", "TrueHD");
        CODEC_REPLACE_MAP.put("wmav2", "wma");

        // 清理用（直接置空）
        CODEC_REPLACE_MAP.put("-608", "");
        CODEC_REPLACE_MAP.put("_s24le", "");
    }

    private static String cleanCodec(String input) {
        if (TextUtils.isEmpty(input)) {
           return "未知";
        }
        String result = input;
        for (Map.Entry<String, String> entry : CODEC_REPLACE_MAP.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        // 如果替换完变成空字符串，返回"未知"
        return TextUtils.isEmpty(result) ? "未知" : result;
    }

    @Override
    public int getTrackType() {
        return mTrackType;
    }

    public void setTrackType(int trackType) {
        mTrackType = trackType;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' + getInfoInline() + "}";
    }

    @Override
    public String getInfoInline() {
        StringBuilder out = new StringBuilder(128);
        switch (mTrackType) {
            case MEDIA_TRACK_TYPE_VIDEO:
                out.append("VIDEO");
                out.append(", ");
                out.append(mStreamMeta.getCodecShortNameInline());
                out.append(", ");
                out.append(mStreamMeta.getBitrateInline());
                out.append(", ");
                out.append(mStreamMeta.getResolutionInline());
                break;
            case MEDIA_TRACK_TYPE_AUDIO:
                out.append(getLanguage());  //xuameng显示语言
                out.append(", ");
                out.append(mStreamMeta.getBitrateInline());   //xuameng音频比特率
                out.append(", ");
                out.append(mStreamMeta.getSampleRateInline());  //XUAMENG显示K赫兹
                out.append("[");
                out.append(getMCodecName()); //xuameng编码
                out.append("音轨]");
                break;
            case MEDIA_TRACK_TYPE_TIMEDTEXT:
                out.append(getLanguage());
                //              out.append(mStreamMeta.mLanguage);  //xuameng显示语言
                //              out.append(", ");        //xuameng 多了个逗号
                out.append("[");
                out.append(getMCodecName()); //xuameng编码
                out.append("字幕]");
                break;
            case MEDIA_TRACK_TYPE_SUBTITLE:
                out.append("SUBTITLE");
                break;
            default:
                out.append("UNKNOWN");
                break;
        }
        return out.toString();
    }
}
