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

import com.github.tvbox.osc.util.AudioTrackMemory;  //xuameng记忆选择音轨
import com.github.tvbox.osc.util.StringUtils;

import android.util.Pair;  //xuameng记忆选择音轨
import java.util.Map;  //xuameng记忆选择音轨

import java.util.LinkedHashMap;

public class EXOmPlayer extends ExoMediaPlayer {

    // ==================== 常量 / 复用对象 ====================
    // 音轨 sampleMimeType 清洗规则（有序，保证替换顺序一致）
    private static final LinkedHashMap<String, String> AUDIO_SAMPLE_REPLACE = new LinkedHashMap<>();
    // 音轨 format.codecs 清洗规则
    private static final LinkedHashMap<String, String> AUDIO_CODECS_REPLACE = new LinkedHashMap<>();
    // 字幕类型清洗规则
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

    // 复用 StringBuilder，减少临时对象（大文件 / 多轨道场景有效降低 GC 压力）
    private final StringBuilder sharedBuilder = new StringBuilder(64);

    // ==================== 成员变量 ====================
    private String audioId = "";
    private String subtitleId = "";
    private String videoId = "";   //xuameng视轨
    private static AudioTrackMemory memory;    //xuameng记忆选择音轨

    // 切轨防抖：记录上一次实际应用的 (renderId, groupId, trackId)，避免重复 setParameters
    private int lastRenderId = C.INDEX_UNSET;
    private int lastGroupId = C.INDEX_UNSET;
    private int lastTrackId = C.INDEX_UNSET;

    public EXOmPlayer(Context context) {
        super(context);
        memory = AudioTrackMemory.getInstance(context);  //xuameng记忆选择音轨
    }

    // ==================== TrackInfo 主流程 ====================

    @SuppressLint("UnsafeOptInUsageError")
    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();

        // 【防闪退】播放器 / trackSelector 可能尚未就绪
        if (mMediaPlayer == null) {
            return data;
        }
        MappingTrackSelector.MappedTrackInfo mappedInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (mappedInfo == null) {
            return data;
        }

        // 刷新当前已选 id（内部已做判空）
        getExoSelectedTrack();

        final int rendererCount = mappedInfo.getRendererCount();
        for (int groupArrayIndex = 0; groupArrayIndex < rendererCount; groupArrayIndex++) {
            TrackGroupArray groupArray = mappedInfo.getTrackGroups(groupArrayIndex);
            if (groupArray == null) continue; // 【防闪退】防御性判空

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
        // sampleMimeType 清洗（原 audioCodecs）
        String audioCodecs = cleanWith(audioSampleReplaceMap(), format.sampleMimeType);
        if (TextUtils.isEmpty(audioCodecs)) {
            audioCodecs = "未知";
        }

        // format.codecs 清洗（原 formatCodecs）
        String formatCodecs = cleanWith(AUDIO_CODECS_REPLACE, format.codecs);
        if (TextUtils.isEmpty(formatCodecs)) {
            formatCodecs = "未知";
        }

        // 展示优先级：format.codecs 非空用 formatCodecs，否则 audioCodecs
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

    // ==================== 字符串清洗（核心：替代原来的 replace 风暴） ====================

    /**
     * 用规则表一次性完成替换，全程只产生少量中间对象。
     * 规则按插入顺序匹配；若某规则的 key 已在前面的替换结果中被破坏，则跳过（与原代码 contain+replace 行为一致）。
     */
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

    // 历史兼容：原代码里 audio sampleMimeType 使用独立的替换表（静态返回，避免重复 new）
    private static LinkedHashMap<String, String> audioSampleReplaceMap() {
        return AUDIO_SAMPLE_REPLACE;
    }

    // ==================== Codec 简化 ====================

    /**
     * xuameng从完整 codec 字符串中提取简短编码名
     * avc1.640032 → h264
     * hev1.1.6.L93.B0 → hevc
     * mp4a.40.2 → aac
     * 其他原样返回
     */
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
        videoId = "";  //xuameng视轨

        // 【防闪退】播放器可能已 release
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
                    } else if (MimeTypes.isVideo(mime)) {  //xuameng视轨
                        videoId = format.id;
                    }
                }
            }
        }
    }

    // ==================== 切轨（统一入口 + 防抖） ====================

    /**
     * 通用切轨：audio / video / text 均可通过此方法切换。
     * 内部统一做：判空、索引校验、相同轨道防抖、一次性应用参数。
     */
    private void applyTrackSelection(@Nullable TrackInfoBean bean, boolean disableTextOnNull, String playKey) {
        MappingTrackSelector.MappedTrackInfo mappedInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (mappedInfo == null) return;

        // null：禁用某个轨道类型（原逻辑只针对 TEXT，这里保持通用）
        if (bean == null) {
            if (!disableTextOnNull) return;
            for (int renderIndex = 0; renderIndex < mappedInfo.getRendererCount(); renderIndex++) {
                if (mappedInfo.getRendererType(renderIndex) == C.TRACK_TYPE_TEXT) {
                    DefaultTrackSelector.Parameters.Builder builder =
                            getTrackSelector().getParameters().buildUpon();
                    builder.setRendererDisabled(renderIndex, true);
                    getTrackSelector().setParameters(builder);
                    resetLastSelection();
                    break;
                }
            }
            return;
        }

        final int renderId = bean.renderId;
        if (renderId < 0 || renderId >= mappedInfo.getRendererCount()) return;

        TrackGroupArray groups = mappedInfo.getTrackGroups(renderId);
        final int groupId = bean.trackGroupId;
        final int trackId = bean.trackId;
        if (!isTrackIndexValid(groups, groupId, trackId)) return;

        // 【防闪退/防抖】与上次完全相同则跳过，避免频繁 setParameters 触发 Renderer 重建
        if (renderId == lastRenderId && groupId == lastGroupId && trackId == lastTrackId) {
            return;
        }

        DefaultTrackSelector.SelectionOverride override =
                new DefaultTrackSelector.SelectionOverride(groupId, trackId);

        DefaultTrackSelector.Parameters.Builder builder = getTrackSelector().buildUponParameters();
        builder.setRendererDisabled(renderId, false);
        builder.setSelectionOverride(renderId, groups, override);
        getTrackSelector().setParameters(builder);

        lastRenderId = renderId;
        lastGroupId = groupId;
        lastTrackId = trackId;

        // xuameng记忆选择音轨（仅音轨需要保存）
        if (disableTextOnNull && !TextUtils.isEmpty(playKey)) {
            memory.save(playKey, groupId, trackId);
        }
    }

    private void resetLastSelection() {
        lastRenderId = C.INDEX_UNSET;
        lastGroupId = C.INDEX_UNSET;
        lastTrackId = C.INDEX_UNSET;
    }

    // ---- 对外接口（保持原方法签名，调用方无需改动） ----

    public void selectExoTrack(@Nullable TrackInfoBean subtitleTrackBean) {
        // 原逻辑：null 时禁用字幕渲染器
        applyTrackSelection(subtitleTrackBean, true, "");
    }

    public void selectExoTrackAudio(@Nullable TrackInfoBean audioTrackBean, String playKey) {     //xuameng记忆选择音轨
        if (audioTrackBean == null) {
            // 原逻辑里 audio 的 null 分支也是禁用 TEXT 渲染器（保持原样）
            MappingTrackSelector.MappedTrackInfo mappedInfo = getTrackSelector().getCurrentMappedTrackInfo();
            if (mappedInfo == null) return;
            for (int renderIndex = 0; renderIndex < mappedInfo.getRendererCount(); renderIndex++) {
                if (mappedInfo.getRendererType(renderIndex) == C.TRACK_TYPE_TEXT) {
                    DefaultTrackSelector.Parameters.Builder parametersBuilder =
                            getTrackSelector().getParameters().buildUpon();
                    parametersBuilder.setRendererDisabled(renderIndex, true);
                    getTrackSelector().setParameters(parametersBuilder);
                    resetLastSelection();
                    break;
                }
            }
            return;
        }
        applyTrackSelection(audioTrackBean, true, playKey);
    }

    public void selectExoTrackVideo(@Nullable TrackInfoBean videoTrackBean) {    //xuameng选择视轨
        MappingTrackSelector.MappedTrackInfo mappedInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (mappedInfo == null) return;

        if (videoTrackBean == null) {
            // 禁用视频轨道（保留逻辑完整性）
            for (int renderIndex = 0; renderIndex < mappedInfo.getRendererCount(); renderIndex++) {
                if (mappedInfo.getRendererType(renderIndex) == C.TRACK_TYPE_VIDEO) {
                    DefaultTrackSelector.Parameters.Builder parametersBuilder =
                            getTrackSelector().getParameters().buildUpon();
                    parametersBuilder.setRendererDisabled(renderIndex, true);
                    getTrackSelector().setParameters(parametersBuilder);
                    resetLastSelection();
                    break;
                }
            }
            return;
        }

        // 校验 renderId 确实指向视频
        if (mappedInfo.getRendererType(videoTrackBean.renderId) != C.TRACK_TYPE_VIDEO) {
            LogUtils.e("selectExoTrackVideo: renderId does not point to a video track!");
            return;
        }

        applyTrackSelection(videoTrackBean, false, "");
    }

    // ==================== 记忆音轨 ====================

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

        // 防抖：与上次一致则跳过
        if (audioRendererIndex == lastRenderId && groupIndex == lastGroupId && trackIndex == lastTrackId) {
            return;
        }

        DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);

        DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().buildUponParameters();
        parametersBuilder.clearSelectionOverrides(audioRendererIndex);
        parametersBuilder.setSelectionOverride(audioRendererIndex, audioGroups, override);
        getTrackSelector().setParameters(parametersBuilder.build());

        lastRenderId = audioRendererIndex;
        lastGroupId = groupIndex;
        lastTrackId = trackIndex;
    }

    /**
     * 查找音频渲染器索引   //xuameng记忆选择音轨
     */
    private int findAudioRendererIndex(MappingTrackSelector.MappedTrackInfo mappedInfo) {
        if (mappedInfo == null) return C.INDEX_UNSET;
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
        if (groups == null) return false;
        if (groupIndex < 0 || groupIndex >= groups.length) {
            return false;
        }
        TrackGroup group = groups.get(groupIndex);
        return group != null && trackIndex >= 0 && trackIndex < group.length;
    }

    public void setOnTimedTextListener(Player.Listener listener) {
        // 【防闪退】判空
        if (mMediaPlayer != null && listener != null) {
            mMediaPlayer.addListener(listener);
        }
    }
}
