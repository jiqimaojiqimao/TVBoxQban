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

import android.text.TextUtils;
import java.util.Locale;     //xuameng getSampleRateInline依赖

import tv.danmaku.ijk.media.player.IjkMediaMeta;

public class IjkTrackInfo implements ITrackInfo {
    private int mTrackType = MEDIA_TRACK_TYPE_UNKNOWN;
    private IjkMediaMeta.IjkStreamMeta mStreamMeta;

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
    public String getLanguage() {
		String Language = mStreamMeta.mLanguage;   //xuameng显示字幕类型
        if (Language == null || TextUtils.isEmpty(Language))
            return "未知";
				String ch = "chi";  //xuameng过滤字幕类型里application/字符串
				String change = "中文";
				if(Language.contains(ch)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(ch, change);  //xuameng过滤字幕类型里application/字符串
				}
				String zhi = "zhi";  //xuameng过滤字幕类型里application/字符串
				String changezhi = "中文";
				if(Language.contains(zhi)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(zhi, changezhi);  //xuameng过滤字幕类型里application/字符串
				}
				String eng = "eng";  //xuameng过滤字幕类型里application/字符串
				String changeeng = "英语";
				if(Language.contains(eng)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(eng, changeeng);  //xuameng过滤字幕类型里application/字符串
				}
				String ara = "ara";  //xuameng过滤字幕类型里application/字符串
				String changeara = "阿拉伯语";
				if(Language.contains(ara)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(ara, changeara);  //xuameng过滤字幕类型里application/字符串
				}
				String bul = "bul";  //xuameng过滤字幕类型里application/字符串
				String changebul = "保加利亚语";
				if(Language.contains(bul)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(bul, changebul);  //xuameng过滤字幕类型里application/字符串
				}
				String cze = "cze";  //xuameng过滤字幕类型里application/字符串
				String changecze = "捷克语";
				if(Language.contains(cze)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(cze, changecze);  //xuameng过滤字幕类型里application/字符串
				}
				String dan = "dan";  //xuameng过滤字幕类型里application/字符串
				String changedan = "丹麦语";
				if(Language.contains(dan)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(dan, changedan);  //xuameng过滤字幕类型里application/字符串
				}
				String ger = "ger";  //xuameng过滤字幕类型里application/字符串
				String changeger = "德语";
				if(Language.contains(ger)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(ger, changeger);  //xuameng过滤字幕类型里application/字符串
				}
				String gre = "gre";  //xuameng过滤字幕类型里application/字符串
				String changegre = "希腊语";
				if(Language.contains(gre)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(gre, changegre);  //xuameng过滤字幕类型里application/字符串
				}
				String spa = "spa";  //xuameng过滤字幕类型里application/字符串
				String changespa = "西班牙语";
				if(Language.contains(spa)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(spa, changespa);  //xuameng过滤字幕类型里application/字符串
				}
				String est = "est";  //xuameng过滤字幕类型里application/字符串
				String changeest = "爱沙尼亚语";
				if(Language.contains(est)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(est, changeest);  //xuameng过滤字幕类型里application/字符串
				}
				String fin = "fin";  //xuameng过滤字幕类型里application/字符串
				String changefin = "芬兰语";
				if(Language.contains(fin)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(fin, changefin);  //xuameng过滤字幕类型里application/字符串
				}
				String fre = "fre";  //xuameng过滤字幕类型里application/字符串
				String changefre = "法语";
				if(Language.contains(fre)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(fre, changefre);  //xuameng过滤字幕类型里application/字符串
				}
				String heb = "heb";  //xuameng过滤字幕类型里application/字符串
				String changeheb = "希伯来语";
				if(Language.contains(heb)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(heb, changeheb);  //xuameng过滤字幕类型里application/字符串
				}
				String hin = "hin";  //xuameng过滤字幕类型里application/字符串
				String changehin = "印地语";
				if(Language.contains(hin)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(hin, changehin);  //xuameng过滤字幕类型里application/字符串
				}
				String hun = "hun";  //xuameng过滤字幕类型里application/字符串
				String changehun = "匈牙利语";
				if(Language.contains(hun)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(hun, changehun);  //xuameng过滤字幕类型里application/字符串
				}
				String ind = "ind";  //xuameng过滤字幕类型里application/字符串
				String changeind = "印度尼西亚语";
				if(Language.contains(ind)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(ind, changeind);  //xuameng过滤字幕类型里application/字符串
				}
				String ita = "ita";  //xuameng过滤字幕类型里application/字符串
				String changeita = "意大利语";
				if(Language.contains(ita)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(ita, changeita);  //xuameng过滤字幕类型里application/字符串
				}
				String jpn = "jpn";  //xuameng过滤字幕类型里application/字符串
				String changejpn = "日语";
				if(Language.contains(jpn)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(jpn, changejpn);  //xuameng过滤字幕类型里application/字符串
				}
				String kor = "kor";  //xuameng过滤字幕类型里application/字符串
				String changekor = "韩语";
				if(Language.contains(kor)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(kor, changekor);  //xuameng过滤字幕类型里application/字符串
				}
				String lit = "lit";  //xuameng过滤字幕类型里application/字符串
				String changelit = "立陶宛语";
				if(Language.contains(lit)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(lit, changelit);  //xuameng过滤字幕类型里application/字符串
				}
				String lav = "lav";  //xuameng过滤字幕类型里application/字符串
				String changelav = "拉脱维亚语";
				if(Language.contains(lav)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(lav, changelav);  //xuameng过滤字幕类型里application/字符串
				}
				String may = "may";  //xuameng过滤字幕类型里application/字符串
				String changemay = "马来语";
				if(Language.contains(may)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(may, changemay);  //xuameng过滤字幕类型里application/字符串
				}
				String dut = "dut";  //xuameng过滤字幕类型里application/字符串
				String changedut = "荷兰语";
				if(Language.contains(dut)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(dut, changedut);  //xuameng过滤字幕类型里application/字符串
				}
				String nor = "nor";  //xuameng过滤字幕类型里application/字符串
				String changenor = "挪威语";
				if(Language.contains(nor)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(nor, changenor);  //xuameng过滤字幕类型里application/字符串
				}
				String pol = "pol";  //xuameng过滤字幕类型里application/字符串
				String changepol = "波兰语";
				if(Language.contains(pol)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(pol, changepol);  //xuameng过滤字幕类型里application/字符串
				}
				String por = "por";  //xuameng过滤字幕类型里application/字符串
				String changepor = "葡萄牙语";
				if(Language.contains(por)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(por, changepor);  //xuameng过滤字幕类型里application/字符串
				}
				String rus = "rus";  //xuameng过滤字幕类型里application/字符串
				String changerus = "俄语";
				if(Language.contains(rus)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(rus, changerus);  //xuameng过滤字幕类型里application/字符串
				}
				String slo = "slo";  //xuameng过滤字幕类型里application/字符串
				String changeslo = "斯洛伐克语";
				if(Language.contains(slo)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(slo, changeslo);  //xuameng过滤字幕类型里application/字符串
				}
				String slv = "slv";  //xuameng过滤字幕类型里application/字符串
				String changeslv = "斯洛文尼亚语";
				if(Language.contains(slv)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(slv, changeslv);  //xuameng过滤字幕类型里application/字符串
				}
				String swe = "swe";  //xuameng过滤字幕类型里application/字符串
				String changeswe = "瑞典语";
				if(Language.contains(swe)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(swe, changeswe);  //xuameng过滤字幕类型里application/字符串
				}
				String tam = "tam";  //xuameng过滤字幕类型里application/字符串
				String changetam = "泰米尔语";
				if(Language.contains(tam)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(tam, changetam);  //xuameng过滤字幕类型里application/字符串
				}
				String tel = "tel";  //xuameng过滤字幕类型里application/字符串
				String changetel = "泰卢固语";
				if(Language.contains(tel)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(tel, changetel);  //xuameng过滤字幕类型里application/字符串
				}
				String tha = "tha";  //xuameng过滤字幕类型里application/字符串
				String changetha = "泰语";
				if(Language.contains(tha)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(tha, changetha);  //xuameng过滤字幕类型里application/字符串
				}
				String ukr = "ukr";  //xuameng过滤字幕类型里application/字符串
				String changeukr = "乌克兰语";
				if(Language.contains(ukr)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(ukr, changeukr);  //xuameng过滤字幕类型里application/字符串
				}
				String vie = "vie";  //xuameng过滤字幕类型里application/字符串
				String changevie = "越南语";
				if(Language.contains(vie)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(vie, changevie);  //xuameng过滤字幕类型里application/字符串
				}
				String tur = "tur";  //xuameng过滤字幕类型里application/字符串
				String changetur = "土耳其语";
				if(Language.contains(tur)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(tur, changetur);  //xuameng过滤字幕类型里application/字符串
				}
				String cat = "cat";  //xuameng过滤字幕类型里application/字符串
				String changecat = "泰加罗尼亚语";
				if(Language.contains(cat)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(cat, changecat);  //xuameng过滤字幕类型里application/字符串
				}
				String baq = "baq";  //xuameng过滤字幕类型里application/字符串
				String changebaq = "巴基斯坦语";
				if(Language.contains(baq)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(baq, changebaq);  //xuameng过滤字幕类型里application/字符串
				}
				String fil = "fil";  //xuameng过滤字幕类型里application/字符串
				String changefil = "菲律宾语";
				if(Language.contains(fil)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(fil, changefil);  //xuameng过滤字幕类型里application/字符串
				}
				String glg = "glg";  //xuameng过滤字幕类型里application/字符串
				String changeglg = "加利西亚语";
				if(Language.contains(glg)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(glg, changeglg);  //xuameng过滤字幕类型里application/字符串
				}
				String kan = "kan";  //xuameng过滤字幕类型里application/字符串
				String changekan = "卡纳达语";
				if(Language.contains(kan)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(kan, changekan);  //xuameng过滤字幕类型里application/字符串
				}
				String mal = "mal";  //xuameng过滤字幕类型里application/字符串
				String changemal = "马拉雅拉姆语";
				if(Language.contains(mal)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(mal, changemal);  //xuameng过滤字幕类型里application/字符串
				}
				String nob = "nob";  //xuameng过滤字幕类型里application/字符串
				String changenob = "书面挪威语";
				if(Language.contains(nob)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(nob, changenob);  //xuameng过滤字幕类型里application/字符串
				}
				String rum = "rum";  //xuameng过滤字幕类型里application/字符串
				String changerum = "罗马尼亚语";
				if(Language.contains(rum)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(rum, changerum);  //xuameng过滤字幕类型里application/字符串
				}
				String und = "und";  //xuameng过滤字幕类型里application/字符串
				String changeund = "未知";
				if(Language.contains(und)) {  //xuameng过滤字幕类型里application/字符串
					Language = Language.replace(und, changeund);  //xuameng过滤字幕类型里application/字符串
				}
        return Language;
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

    public String getSampleRateInline() {       //XUAMENG显示K赫兹
        if (mStreamMeta.mSampleRate <= 0) {
            return "N/A";
        } else if (mStreamMeta.mSampleRate < 1000) {
				return String.format(Locale.US, "%d Hz", mStreamMeta.mSampleRate);
	    } else {
                return String.format(Locale.US, "%d KHz", mStreamMeta.mSampleRate / 1000);
        }
    }

    public String getMCodecName() {
        if (getCodecName() == null || TextUtils.isEmpty(getCodecName()))
            return "未知";
		String zimuCodecs = mStreamMeta.mCodecName;   //xuameng显示字幕类型
		String text = "hdmv_pgs_subtitle";  //xuameng过滤字幕类型里application/字符串
		String textString = "pgs";
		if(zimuCodecs.contains(text)) {  //xuameng过滤字幕类型里application/字符串
			zimuCodecs = zimuCodecs.replace(text, textString);  //xuameng过滤字幕类型里application/字符串
		}
		String text1 = "mov_text";  //xuameng过滤字幕类型里application/字符串
		String textString1 = "tx3g";
		if(zimuCodecs.contains(text1)) {  //xuameng过滤字幕类型里application/字符串
			zimuCodecs = zimuCodecs.replace(text1, textString1);  //xuameng过滤字幕类型里application/字符串
		}
		String text2 = "dvd_subtitle";  //xuameng过滤字幕类型里application/字符串
		String textString2 = "vobsub";
		if(zimuCodecs.contains(text2)) {  //xuameng过滤字幕类型里application/字符串
			zimuCodecs = zimuCodecs.replace(text2, textString2);  //xuameng过滤字幕类型里application/字符串
		}
		String text3 = "truehd";  //xuameng过滤字幕类型里application/字符串
		String textString3 = "TrueHD";
		if(zimuCodecs.contains(text3)) {  //xuameng过滤字幕类型里application/字符串
			zimuCodecs = zimuCodecs.replace(text3, textString3);  //xuameng过滤字幕类型里application/字符串
		}
        return zimuCodecs;
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
                out.append(getMCodecName()); //xuameng编码
                out.append(", ");
                out.append(mStreamMeta.getBitrateInline());
                out.append(", ");
                out.append(getSampleRateInline());  //XUAMENG显示K赫兹
                break;
            case MEDIA_TRACK_TYPE_TIMEDTEXT:
				out.append(getLanguage());
  //              out.append(mStreamMeta.mLanguage);  //xuameng显示语言
                out.append(", ");
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
