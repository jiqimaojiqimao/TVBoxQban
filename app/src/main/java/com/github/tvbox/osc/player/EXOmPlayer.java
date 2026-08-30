package com.github.tvbox.osc.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.blankj.utilcode.util.LogUtils;
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

import com.github.tvbox.osc.util.AudioTrackMemory;
import com.github.tvbox.osc.util.StringUtils;

import android.util.Pair;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author xuameng
 * @date :2026/08/30
 * @description:  字符转换重构、代码BUG修复、优化、简化等
 */

public class EXOmPlayer extends ExoMediaPlayer {

    // ==================== 常量 / 复用对象 ====================
    private static final LinkedHashMap<String, String> AUDIO_SAMPLE_REPLACE = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> AUDIO_CODECS_REPLACE = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> SUBTITLE_TYPE_REPLACE = new LinkedHashMap<>();

    static {
        // ---- audio sampleMimeType ----
        AUDIO_SAMPLE_REPLACE.put("audio/mpeg-L2", "mp2");
        AUDIO_SAMPLE_REPLACE.put("audio/mpeg", "mp3");
        AUDIO_SAMPLE_REPLACE.put("true-hd", "TrueHD");
        AUDIO_SAMPLE_REPLACE.put("vnd.", "");
        AUDIO_SAMPLE_REPLACE.put(".hd", "");
        AUDIO_SAMPLE_REPLACE.put("audio/", "");

        // ---- audio format.codecs ----
        AUDIO_CODECS_REPLACE.put("mp4a.40.2", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a.40.02", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a.40.5", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a.40.05", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a.40.29", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a.66", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a.67", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a.68", "aac");
        AUDIO_CODECS_REPLACE.put("mp4a", "aac");

        // ---- subtitle type ----
        SUBTITLE_TYPE_REPLACE.put("application/", "");
        SUBTITLE_TYPE_REPLACE.put("text/x-", "");
        SUBTITLE_TYPE_REPLACE.put("text/vtt", "vtt");
        SUBTITLE_TYPE_REPLACE.put("quicktime-", "");
        SUBTITLE_TYPE_REPLACE.put("x-", "");
        SUBTITLE_TYPE_REPLACE.put("-608", "");
    }

    private final StringBuilder sharedBuilder = new StringBuilder(64);

    // ==================== 成员变量 ====================
    private String audioId = "";
    private String subtitleId = "";
    private String videoId = "";
    private static AudioTrackMemory memory;

    public EXOmPlayer(Context context) {
        super(context);
        memory = AudioTrackMemory.getInstance(context);
    }

    // ==================== TrackInfo 主流程 ====================

    @SuppressLint("UnsafeOptInUsageError")
    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();

        if (mMediaPlayer == null) {
            return data;
        }
        MappingTrackSelector.MappedTrackInfo mappedInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (mappedInfo == null) {
            return data;
        }

        getExoSelectedTrack();

        final int rendererCount = mappedInfo.getRendererCount();
        for (int groupArrayIndex = 0; groupArrayIndex < rendererCount; groupArrayIndex++) {
            TrackGroupArray groupArray = mappedInfo.getTrackGroups(groupArrayIndex);
            if (groupArray == null) continue;

            final int groupLen = groupArray.length;
            for (int groupIndex = 0; groupIndex < groupLen; groupIndex++) {
                TrackGroup group = groupArray.get(groupIndex);
                if (group == null) continue;

                final int formatLen = group.length;
                for (int formatIndex = 0; formatIndex < formatLen; formatIndex++) {
                    Format format = group.getFormat(formatIndex);
                    if (format == null) continue;

                    final String mime = format.sampleMimeType;
                    if (TextUtils.isEmpty(mime)) continue;

                    if (MimeTypes.isAudio(mime)) {
                        parseAudioTrack(format, formatIndex, groupIndex, groupArrayIndex, data);
                    } else if (MimeTypes.isText(mime)) {
                        parseTextTrack(format, formatIndex, groupIndex, groupArrayIndex, data);
                    } else if (MimeTypes.isVideo(mime)) {
                        parseVideoTrack(format, formatIndex, groupIndex, groupArrayIndex, data);
                    }
                }
            }
        }
        return data;
    }

    // ---- 音频轨道解析 ----
    private void parseAudioTrack(Format format, int formatIndex, int groupIndex, int renderId, TrackInfo data) {
        String audioCodecs = cleanWith(AUDIO_SAMPLE_REPLACE, format.sampleMimeType);
        if (TextUtils.isEmpty(audioCodecs)) {
            audioCodecs = "未知";
        }

        String formatCodecs = cleanWith(AUDIO_CODECS_REPLACE, format.codecs);
        if (TextUtils.isEmpty(formatCodecs)) {
            formatCodecs = "未知";
        }

        String displayCodec = TextUtils.isEmpty(format.codecs) ? audioCodecs : formatCodecs;

        sharedBuilder.setLength(0);
        sharedBuilder.append(data.getAudio().size() + 1).append("：")
                .append(trackNameProvider.getTrackName(format))
                .append("[").append(displayCodec).append("音轨]");

        TrackInfoBean t = new TrackInfoBean();
        t.name = sharedBuilder.toString();
        t.language = "";
        t.trackId = formatIndex;
        t.selected = !StringUtils.isEmpty(audioId) && audioId.equals(format.id);
        t.trackGroupId = groupIndex;
        t.renderId = renderId;
        data.addAudio(t);
    }

    // ---- 字幕轨道解析 ----
    private void parseTextTrack(Format format, int formatIndex, int groupIndex, int renderId, TrackInfo data) {
        String originalString = format.sampleMimeType;
        if (TextUtils.isEmpty(originalString)) {
            originalString = "cea";
        }
        originalString = cleanWith(SUBTITLE_TYPE_REPLACE, originalString);

        sharedBuilder.setLength(0);
        sharedBuilder.append(data.getSubtitle().size() + 1).append("：")
                .append(trackNameProvider.getTrackName(format))
                .append("[").append(originalString).append("字幕]");

        TrackInfoBean t = new TrackInfoBean();
        t.name = sharedBuilder.toString();
        t.language = "";
        t.trackId = formatIndex;
        t.selected = !StringUtils.isEmpty(subtitleId) && subtitleId.equals(format.id);
        t.trackGroupId = groupIndex;
        t.renderId = renderId;
        data.addSubtitle(t);
    }

    // ---- 视频轨道解析 ----
    private void parseVideoTrack(Format format, int formatIndex, int groupIndex, int renderId, TrackInfo data) {
        String formatCodecs = simplifyCodec(format.codecs);

        sharedBuilder.setLength(0);
        sharedBuilder.append(data.getVideo().size() + 1).append("：")
                .append(trackNameProvider.getTrackName(format))
                .append("[").append(formatCodecs).append("视轨]");

        TrackInfoBean t = new TrackInfoBean();
        t.name = sharedBuilder.toString();
        t.language = "";
        t.trackId = formatIndex;
        t.selected = !StringUtils.isEmpty(videoId) && videoId.equals(format.id);
        t.trackGroupId = groupIndex;
        t.renderId = renderId;
        data.addVideo(t);
    }

    // ==================== 字符串清洗 ====================

    private static String cleanWith(LinkedHashMap<String, String> rules, String input) {
        if (TextUtils.isEmpty(input)) return input;
        String result = input;
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    // ==================== Codec 简化 ====================

    private String simplifyCodec(String codec) {
        if (TextUtils.isEmpty(codec)) return "未知";

        String[] parts = codec.split("\\.");
        String prefix = parts[0].toLowerCase().trim();

        switch (prefix) {
            case "avc1":
            case "avc2":
            case "avc3":
            case "avc4":
                return "h264";
            case "hev1":
            case "hvc1":
                return "hevc";
            case "vp09":
            case "vp9":
                return "vp9";
            case "av01":
                return "av1";
            case "mp4a":
                return "aac";
            default:
                return prefix;
        }
    }

    // ==================== 刷新当前已选轨道 id ====================

    @SuppressLint("UnsafeOptInUsageError")
    private void getExoSelectedTrack() {
        audioId = "";
        subtitleId = "";
        videoId = "";

        if (mMediaPlayer == null) {
            return;
        }
        Tracks tracks = mMediaPlayer.getCurrentTracks();
        if (tracks == null) return;

        for (Tracks.Group group : tracks.getGroups()) {
            if (group == null) continue;
            final int length = group.length;
            for (int i = 0; i < length; i++) {
                if (group.isTrackSelected(i)) {
                    Format format = group.getTrackFormat(i);
                    if (format == null) continue;
                    final String mime = format.sampleMimeType;
                    if (TextUtils.isEmpty(mime)) continue;

                    if (MimeTypes.isAudio(mime)) {
                        audioId = format.id;
                    } else if (MimeTypes.isText(mime)) {
                        subtitleId = format.id;
                    } else if (MimeTypes.isVideo(mime)) {
                        videoId = format.id;
                    }
                }
            }
        }
    }

    // ==================== 切轨（三个独立方法） ====================

    @SuppressLint("UnsafeOptInUsageError")
    public void selectExoTrack(@Nullable TrackInfoBean trackBean) {
        // 选择字幕
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo == null) return;

        int textRendererIndex = findRendererIndex(trackInfo, C.TRACK_TYPE_TEXT);
        if (textRendererIndex == C.INDEX_UNSET) return;

        DefaultTrackSelector.Parameters.Builder builder = getTrackSelector().buildUponParameters();

        if (trackBean == null) {
            // 关闭字幕
            builder.clearSelectionOverrides(textRendererIndex);
            builder.setRendererDisabled(textRendererIndex, true);
        } else {
            // 选择指定字幕
            TrackGroupArray trackGroupArray = trackInfo.getTrackGroups(trackBean.renderId);
            DefaultTrackSelector.SelectionOverride override =
                    new DefaultTrackSelector.SelectionOverride(trackBean.trackGroupId, trackBean.trackId);
            builder.clearSelectionOverrides(trackBean.renderId);
            builder.setRendererDisabled(trackBean.renderId, false);
            builder.setSelectionOverride(trackBean.renderId, trackGroupArray, override);
        }

        getTrackSelector().setParameters(builder.build());
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void selectExoTrackAudio(@Nullable TrackInfoBean trackBean, String playKey) {
        // 选择音轨
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo == null) return;

        int audioRendererIndex = findRendererIndex(trackInfo, C.TRACK_TYPE_AUDIO);
        if (audioRendererIndex == C.INDEX_UNSET) return;

        DefaultTrackSelector.Parameters.Builder builder = getTrackSelector().buildUponParameters();

        if (trackBean == null) {
            // 关闭音轨
            builder.clearSelectionOverrides(audioRendererIndex);
            builder.setRendererDisabled(audioRendererIndex, true);
        } else {
            // 选择指定音轨
            TrackGroupArray trackGroupArray = trackInfo.getTrackGroups(trackBean.renderId);
            DefaultTrackSelector.SelectionOverride override =
                    new DefaultTrackSelector.SelectionOverride(trackBean.trackGroupId, trackBean.trackId);
            builder.clearSelectionOverrides(trackBean.renderId);
            builder.setRendererDisabled(trackBean.renderId, false);
            builder.setSelectionOverride(trackBean.renderId, trackGroupArray, override);

            // 记忆
            if (!TextUtils.isEmpty(playKey)) {
                memory.save(playKey, trackBean.trackGroupId, trackBean.trackId);
            }
        }

        getTrackSelector().setParameters(builder.build());
    }

    public void selectExoTrackVideo(@Nullable TrackInfoBean trackBean) {
        // 选择视轨
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo == null) return;

        int videoRendererIndex = findRendererIndex(trackInfo, C.TRACK_TYPE_VIDEO);
        if (videoRendererIndex == C.INDEX_UNSET) return;

        DefaultTrackSelector.Parameters.Builder builder = getTrackSelector().buildUponParameters();

        if (trackBean == null) {
            // 关闭视频
            builder.clearSelectionOverrides(videoRendererIndex);
            builder.setRendererDisabled(videoRendererIndex, true);
        } else {
            TrackGroupArray trackGroupArray = trackInfo.getTrackGroups(trackBean.renderId);
            DefaultTrackSelector.SelectionOverride override =
                    new DefaultTrackSelector.SelectionOverride(trackBean.trackGroupId, trackBean.trackId);
            builder.clearSelectionOverrides(trackBean.renderId);
            builder.setRendererDisabled(trackBean.renderId, false);
            builder.setSelectionOverride(trackBean.renderId, trackGroupArray, override);
        }

        getTrackSelector().setParameters(builder.build());
    }

    private int findRendererIndex(MappingTrackSelector.MappedTrackInfo trackInfo, int trackType) {
        if (trackInfo == null) return C.INDEX_UNSET;
        for (int i = 0; i < trackInfo.getRendererCount(); i++) {
            if (trackInfo.getRendererType(i) == trackType) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    // ==================== 记忆音轨 ====================

    public void loadDefaultTrack(String playKey) {
        Pair<Integer, Integer> pair = memory.exoLoad(playKey);
        if (pair == null) return;

        MappingTrackSelector.MappedTrackInfo mappedInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (mappedInfo == null) return;

        int audioRendererIndex = findRendererIndex(mappedInfo, C.TRACK_TYPE_AUDIO);
        if (audioRendererIndex == C.INDEX_UNSET) return;

        TrackGroupArray audioGroups = mappedInfo.getTrackGroups(audioRendererIndex);
        int groupIndex = pair.first;
        int trackIndex = pair.second;
        if (!isTrackIndexValid(audioGroups, groupIndex, trackIndex)) return;

        DefaultTrackSelector.SelectionOverride override =
                new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);

        DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().buildUponParameters();
        parametersBuilder.clearSelectionOverrides(audioRendererIndex);
        parametersBuilder.setSelectionOverride(audioRendererIndex, audioGroups, override);
        getTrackSelector().setParameters(parametersBuilder.build());
    }

    private boolean isTrackIndexValid(TrackGroupArray groups, int groupIndex, int trackIndex) {
        if (groups == null) return false;
        if (groupIndex < 0 || groupIndex >= groups.length) {
            return false;
        }
        TrackGroup group = groups.get(groupIndex);
        return group != null && trackIndex >= 0 && trackIndex < group.length;
    }

    // ==================== TimedText ====================

    public void setOnTimedTextListener(Player.Listener listener) {
        if (mMediaPlayer != null && listener != null) {
            mMediaPlayer.addListener(listener);
        }
    }
}
