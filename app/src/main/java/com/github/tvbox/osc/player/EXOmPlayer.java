package com.github.tvbox.osc.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.Nullable;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;

import xyz.doikki.videoplayer.exo.ExoMediaPlayer;

import com.github.tvbox.osc.util.AudioTrackMemory;  //xuameng记忆选择音轨
import com.github.tvbox.osc.util.StringUtils;

import android.util.Pair;  //xuameng记忆选择音轨
import java.util.Map;  //xuameng记忆选择音轨

public class EXOmPlayer extends ExoMediaPlayer {
    private String audioId = "";
    private String subtitleId = "";
	private static AudioTrackMemory memory;    //xuameng记忆选择音轨

    public EXOmPlayer(Context context) {
        super(context);
		memory = AudioTrackMemory.getInstance(context);  //xuameng记忆选择音轨
    }

    @SuppressLint("UnsafeOptInUsageError")
    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo != null) {
            getExoSelectedTrack();
            for (int groupArrayIndex = 0; groupArrayIndex < trackInfo.getRendererCount(); groupArrayIndex++) {
                TrackGroupArray groupArray = trackInfo.getTrackGroups(groupArrayIndex);
                for (int groupIndex = 0; groupIndex < groupArray.length; groupIndex++) {
                    TrackGroup group = groupArray.get(groupIndex);
                    for (int formatIndex = 0; formatIndex < group.length; formatIndex++) {
                        Format format = group.getFormat(formatIndex);
                        if (MimeTypes.isAudio(format.sampleMimeType)) {
							String audioCodecs = format.sampleMimeType;
							String formatCodecs = format.codecs;
							if (TextUtils.isEmpty(audioCodecs)){
								audioCodecs = "未知";
							}
							String mp3 = "audio/mpeg";  //xuameng过滤音轨类型里application/字符串
							String mp3String = "mp3";
							if(audioCodecs.contains(mp3)) {  //xuameng过滤音轨类型里application/字符串
								audioCodecs = audioCodecs.replace(mp3, mp3String);  //xuameng过滤音轨类型里application/字符串
							}
							String tex5 = "audio/mpeg-L2";  //xuameng过滤音轨类型里application/字符串
							String textString5 = "mp2";
							if(audioCodecs.contains(tex5)) {  //xuameng过滤音轨类型里application/字符串
								audioCodecs = audioCodecs.replace(tex5, textString5);  //xuameng过滤音轨类型里application/字符串
							}
							String text = "audio/";  //xuameng过滤音轨类型里application/字符串
							String textString = "";
							if(audioCodecs.contains(text)) {  //xuameng过滤音轨类型里application/字符串
								audioCodecs = audioCodecs.replace(text, textString);  //xuameng过滤音轨类型里application/字符串
							}
							String tex3 = "vnd.";  //xuameng过滤音轨类型里application/字符串
							String textString3 = "";
							if(audioCodecs.contains(tex3)) {  //xuameng过滤音轨类型里application/字符串
								audioCodecs = audioCodecs.replace(tex3, textString3);  //xuameng过滤音轨类型里application/字符串
							}
							String tex4 = "true-hd";  //xuameng过滤音轨类型里application/字符串
							String textString4 = "TrueHD";
							if(audioCodecs.contains(tex4)) {  //xuameng过滤音轨类型里application/字符串
								audioCodecs = audioCodecs.replace(tex4, textString4);  //xuameng过滤音轨类型里application/字符串
							}
							String dtshd = ".hd";  //xuameng过滤音轨类型里application/字符串
							String dtshdString = "";
							if(audioCodecs.contains(dtshd)) {  //xuameng过滤音轨类型里application/字符串
								audioCodecs = audioCodecs.replace(dtshd, dtshdString);  //xuameng过滤音轨类型里application/字符串
							}
							if (TextUtils.isEmpty(formatCodecs)){    //xuameng formatCodecs这是文件类型当audioCodecs返回空是用formatCodecs代替
								formatCodecs = "未知";
							}
							String text1 = ".40.2";  //xuameng过滤formatCodecs类型里application/字符串
							String textString1 = "";
							if(formatCodecs.contains(text1)) {  //xuameng过滤formatCodecs类型里application/字符串
								formatCodecs = formatCodecs.replace(text1, textString1);  //xuameng过滤音轨类型里application/字符串
							}
							String mp4a = ".40.5";  //xuameng过滤formatCodecs类型里application/字符串
							String mp4aString = "";
							if(formatCodecs.contains(mp4a)) {  //xuameng过滤formatCodecs类型里application/字符串
								formatCodecs = formatCodecs.replace(mp4a, mp4aString);  //xuameng过滤音轨类型里application/字符串
							}

                            String trackName = (data.getAudio().size() + 1) + "：" + trackNameProvider.getTrackName(format) + "[" + (TextUtils.isEmpty(format.codecs)?audioCodecs:formatCodecs) + "]";
							TrackInfoBean t = new TrackInfoBean();
                            t.name = trackName;
                            t.language = "";
                            t.trackId = formatIndex;
                            t.selected = !StringUtils.isEmpty(audioId) && audioId.equals(format.id);
                            t.trackGroupId = groupIndex;
                            t.renderId = groupArrayIndex;
                            data.addAudio(t);
                        } else if (MimeTypes.isText(format.sampleMimeType)) {
							String originalString = format.codecs;   //xuameng显示字幕类型  sampleMimeType改成codecs  media3升级后真正类型在codecs里sampleMimeType显示的是media3-cues
							String stringToReplace = "application/";  //xuameng过滤字幕类型里application/字符串
							String replacementString = "";
							if(originalString.contains(stringToReplace)) {  //xuameng过滤字幕类型里application/字符串
							originalString = originalString.replace(stringToReplace, replacementString);  //xuameng过滤字幕类型里application/字符串
							}
							String text = "text/x-";  //xuameng过滤字幕类型里application/字符串
							String textString = "";
							if(originalString.contains(text)) {  //xuameng过滤字幕类型里application/字符串
							originalString = originalString.replace(text, textString);  //xuameng过滤字幕类型里application/字符串
							}
							String textvtt = "text/vtt";  //xuameng过滤字幕类型里application/字符串
							String textvttString = "vtt";
							if(originalString.contains(textvtt)) {  //xuameng过滤字幕类型里application/字符串
							originalString = originalString.replace(textvtt, textvttString);  //xuameng过滤字幕类型里application/字符串
							}
							String text1 = "x-";  //xuameng过滤字幕类型里application/字符串
							String textString1 = "";
							if(originalString.contains(text1)) {  //xuameng过滤字幕类型里application/字符串
							originalString = originalString.replace(text1, textString1);  //xuameng过滤字幕类型里application/字符串
							}
							String text2 = "quicktime-";  //xuameng过滤字幕类型里application/字符串
							String textString2 = "";
							if(originalString.contains(text2)) {  //xuameng过滤字幕类型里application/字符串
							originalString = originalString.replace(text2, textString2);  //xuameng过滤字幕类型里application/字符串
							}
							String text3 = "-608";  //xuameng过滤字幕类型里application/字符串
							String textString3 = "";
							if(originalString.contains(text3)) {  //xuameng过滤字幕类型里application/字符串
							originalString = originalString.replace(text3, textString3);  //xuameng过滤字幕类型里application/字符串
							}
                            String trackName = "";  //xuameng显示字幕类型
                            TrackInfoBean t = new TrackInfoBean();
                            t.name = trackName;
                            t.language = (data.getSubtitle().size() + 1) + "：" + trackNameProvider.getTrackName(format) + "，"  + "[" + originalString  + "字幕]";  //xuameng显示字幕类型
                            t.trackId = formatIndex;
                            t.selected = !StringUtils.isEmpty(subtitleId) && subtitleId.equals(format.id);
                            t.trackGroupId = groupIndex;
                            t.renderId = groupArrayIndex;
                            data.addSubtitle(t);
                        }
                    }
                }
            }
        }
        return data;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void getExoSelectedTrack() {
        audioId = "";
        subtitleId = "";
        for (Tracks.Group group : mMediaPlayer.getCurrentTracks().getGroups()) {
            if (!group.isSelected()) continue;
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                if (!group.isTrackSelected(trackIndex)) continue;
                Format format = group.getTrackFormat(trackIndex);
                if (MimeTypes.isAudio(format.sampleMimeType)) {
                    audioId = format.id;
                }
                if (MimeTypes.isText(format.sampleMimeType)) {
                    subtitleId = format.id;
                }
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void selectExoTrack(@Nullable TrackInfoBean videoTrackBean) {
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo != null) {
            if (videoTrackBean == null) {
                for (int renderIndex = 0; renderIndex < trackInfo.getRendererCount(); renderIndex++) {
                    if (trackInfo.getRendererType(renderIndex) == C.TRACK_TYPE_TEXT) {
                        DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().getParameters().buildUpon();
                        parametersBuilder.setRendererDisabled(renderIndex, true);
                        getTrackSelector().setParameters(parametersBuilder);
                        break;
                    }
                }
            } else {
                TrackGroupArray trackGroupArray = trackInfo.getTrackGroups(videoTrackBean.renderId);
                @SuppressLint("UnsafeOptInUsageError") DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(videoTrackBean.trackGroupId, videoTrackBean.trackId);
                DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().buildUponParameters();
                parametersBuilder.setRendererDisabled(videoTrackBean.renderId, false);
                parametersBuilder.setSelectionOverride(videoTrackBean.renderId, trackGroupArray, override);
                getTrackSelector().setParameters(parametersBuilder);
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void selectExoTrackAudio(@Nullable TrackInfoBean videoTrackBean,String playKey) {
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo != null) {
            if (videoTrackBean == null) {
                for (int renderIndex = 0; renderIndex < trackInfo.getRendererCount(); renderIndex++) {
                    if (trackInfo.getRendererType(renderIndex) == C.TRACK_TYPE_TEXT) {
                        DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().getParameters().buildUpon();
                        parametersBuilder.setRendererDisabled(renderIndex, true);
                        getTrackSelector().setParameters(parametersBuilder);
                        break;
                    }
                }
            } else {
                TrackGroupArray trackGroupArray = trackInfo.getTrackGroups(videoTrackBean.renderId);
                @SuppressLint("UnsafeOptInUsageError") DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(videoTrackBean.trackGroupId, videoTrackBean.trackId);
                DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().buildUponParameters();
                parametersBuilder.setRendererDisabled(videoTrackBean.renderId, false);
                parametersBuilder.setSelectionOverride(videoTrackBean.renderId, trackGroupArray, override);
                getTrackSelector().setParameters(parametersBuilder);
                //xuameng记忆选择音轨
                if (!playKey.isEmpty()) {
                    memory.save(playKey,videoTrackBean.trackGroupId, videoTrackBean.trackId);
                }
            }
        }
    }

    //xuameng记忆选择音轨
    public void loadDefaultTrack(String playKey) {
        Pair<Integer, Integer> pair = memory.exoLoad(playKey);
        if (pair == null) return;

        MappingTrackSelector.MappedTrackInfo mappedInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (mappedInfo == null) return;

        int audioRendererIndex = findAudioRendererIndex(mappedInfo);
        if (audioRendererIndex == C.INDEX_UNSET) return;

        TrackGroupArray audioGroups = mappedInfo.getTrackGroups(audioRendererIndex);
        int groupIndex = pair.first;
        int trackIndex = pair.second;
        if (!isTrackIndexValid(audioGroups, groupIndex, trackIndex)) return;

        DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
		DefaultTrackSelector.Parameters.Builder builder = getTrackSelector().buildUponParameters();
        builder.clearSelectionOverrides(audioRendererIndex);
        builder.setSelectionOverride(audioRendererIndex, audioGroups, override);
        getTrackSelector().setParameters(builder.build());
    }
    /**
     * 查找音频渲染器索引    //xuameng记忆选择音轨
     */
    private int findAudioRendererIndex(MappingTrackSelector.MappedTrackInfo mappedInfo) {
        for (int i = 0; i < mappedInfo.getRendererCount(); i++) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    /**
     * 验证音轨索引是否有效   //xuameng记忆选择音轨
     */
    private boolean isTrackIndexValid(TrackGroupArray groups, int groupIndex, int trackIndex) {
        if (groupIndex < 0 || groupIndex >= groups.length) {
            return false;
        }

        TrackGroup group = groups.get(groupIndex);
        return trackIndex >= 0 && trackIndex < group.length;
    }

    public void setOnTimedTextListener(Player.Listener listener) {
        mMediaPlayer.addListener(listener);
    }

}

