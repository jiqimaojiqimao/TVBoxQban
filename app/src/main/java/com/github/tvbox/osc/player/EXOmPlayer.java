package com.github.tvbox.osc.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.util.StringUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.util.MimeTypes;

import xyz.doikki.videoplayer.exo.ExoMediaPlayer;

public class EXOmPlayer extends ExoMediaPlayer {
    private String audioId = "";
    private String subtitleId = "";

    public EXOmPlayer(Context context) {
        super(context);
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
								audioCodecs = "";
							}
							String text = "audio/";  //xuameng过滤字幕类型里application/字符串
							String textString = "";
							if(audioCodecs.contains(text)) {  //xuameng过滤字幕类型里application/字符串
								audioCodecs = audioCodecs.replace(text, textString);  //xuameng过滤字幕类型里application/字符串
							}
							String tex3 = "vnd.";  //xuameng过滤字幕类型里application/字符串
							String textString3 = "";
							if(audioCodecs.contains(tex3)) {  //xuameng过滤字幕类型里application/字符串
								audioCodecs = audioCodecs.replace(tex3, textString3);  //xuameng过滤字幕类型里application/字符串
							}
							String tex4 = "true-hd";  //xuameng过滤字幕类型里application/字符串
							String textString4 = "TrueHD";
							if(audioCodecs.contains(tex4)) {  //xuameng过滤字幕类型里application/字符串
								audioCodecs = audioCodecs.replace(tex4, textString4);  //xuameng过滤字幕类型里application/字符串
							}

							if (TextUtils.isEmpty(formatCodecs)){
								formatCodecs = "";
							}
							String text1 = ".40.2";  //xuameng过滤字幕类型里application/字符串
							String textString1 = "";
							if(formatCodecs.contains(text1)) {  //xuameng过滤字幕类型里application/字符串
								formatCodecs = formatCodecs.replace(text1, textString1);  //xuameng过滤字幕类型里application/字符串
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
							String originalString = format.sampleMimeType;   //xuameng显示字幕类型
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

    public void setOnTimedTextListener(Player.Listener listener) {
        mMediaPlayer.addListener(listener);
    }

}
