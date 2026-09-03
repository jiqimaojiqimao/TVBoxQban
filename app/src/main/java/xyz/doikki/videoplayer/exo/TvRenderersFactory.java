package xyz.doikki.videoplayer.exo;

import android.os.Build;
import android.os.Handler;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * xuameng TV 专用 RenderersFactory：
 * - 音频：FFmpeg 永远优先
 * - 视频：MediaCodec 优先，硬解失败才使用 FFmpeg 软解兜底
 * - 强制把 DV 当成 HEVC 处理，绕过 DV 专用解码器
 */
@UnstableApi
public class TvRenderersFactory extends NextRenderersFactory {

    /** 与旧版 ExoPlayer 默认值保持一致 */
    private static final int DEFAULT_MAX_DROPPED_FRAMES = 50;

    public TvRenderersFactory(Context context) {
        super(context);
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF);
        setEnableDecoderFallback(true);
    }

    @Override
    protected void buildAudioRenderers(
            @NonNull Context context,
            int extensionRendererMode,
            @NonNull MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            @NonNull AudioSink audioSink,
            @NonNull Handler eventHandler,
            @NonNull AudioRendererEventListener eventListener,
            @NonNull ArrayList<Renderer> out
    ) {
        super.buildAudioRenderers(
                context,
                EXTENSION_RENDERER_MODE_OFF,
                mediaCodecSelector,
                enableDecoderFallback,
                audioSink,
                eventHandler,
                eventListener,
                out
        );

        // 音频 FFmpeg 永远优先
        try {
            out.add(
                    0,
                    new FfmpegAudioRenderer(
                            eventHandler,
                            eventListener,
                            audioSink
                    )
            );
        } catch (Exception ignored) {
            // FFmpeg so 未加载时忽略
        }
    }

@UnstableApi
@Override
protected void buildVideoRenderers(
        @NonNull Context context,
        int extensionRendererMode,
        @NonNull MediaCodecSelector mediaCodecSelector,
        boolean enableDecoderFallback,
        @NonNull Handler eventHandler,
        @NonNull VideoRendererEventListener eventListener,
        long allowedVideoJoiningTimeMs,
        @NonNull ArrayList<Renderer> out
) {
    // =========================================================
    // 1. 第一优先级：原生 DV 硬解
    // =========================================================
    // 使用系统默认的 MediaCodecSelector，遇到 DV 视频就查 DV 解码器
    out.add(new MediaCodecVideoRenderer(
            context,
            MediaCodecSelector.DEFAULT,  // 不修改 mimeType，老老实实查 DV
            allowedVideoJoiningTimeMs,
            true,                       // 开启 Fallback，失败后才尝试下一个 Renderer
            eventHandler,
            eventListener,
            DEFAULT_MAX_DROPPED_FRAMES
    ));

    // =========================================================
    // 2. 第二优先级：HEVC 硬解兜底（把 DV 当 HEVC 处理）
    // =========================================================
    // 只有在上一步 DV 硬解失败时，ExoPlayer 才会用到这个 Renderer
    out.add(new MediaCodecVideoRenderer(
            context,
            new MediaCodecSelector() {
                @NonNull
                @Override
                public List<MediaCodecInfo> getDecoderInfos(
                        @NonNull String mimeType,
                        boolean requiresSecureDecoder,
                        boolean requiresTunnelingDecoder
                ) throws MediaCodecUtil.DecoderQueryException {
                    
                    // 走到这里说明 DV 硬解不行，强制降级为 HEVC 查硬解
                    if ("video/dolby-vision".equals(mimeType)) {
                        mimeType = "video/hevc";
                    }
                    return MediaCodecSelector.DEFAULT.getDecoderInfos(
                            mimeType,
                            requiresSecureDecoder,
                            requiresTunnelingDecoder
                    );
                }
            },
            allowedVideoJoiningTimeMs,
            true,
            eventHandler,
            eventListener,
            DEFAULT_MAX_DROPPED_FRAMES
    ));

    // =========================================================
    // 3. 第三优先级：FFmpeg 软解兜底
    // =========================================================
    // 如果连 HEVC 硬解都失败了（或者设备根本不支持 HEVC），就用软解
    try {
        out.add(new FfmpegVideoRenderer(
                allowedVideoJoiningTimeMs,
                eventHandler,
                eventListener,
                DEFAULT_MAX_DROPPED_FRAMES
        ));
    } catch (Exception ignored) {
        // FFmpeg so 未加载时忽略
    }
}
}
