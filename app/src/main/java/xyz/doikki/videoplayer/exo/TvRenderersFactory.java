package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;

import java.util.ArrayList;

/**
 * TV 专用 RenderersFactory：
 * - 音频：FFmpeg 永远优先
 * - 视频：MediaCodec 优先，硬解失败才使用 FFmpeg 软解兜底
 */
@UnstableApi
public class TvRenderersFactory extends NextRenderersFactory {

    public TvRenderersFactory(Context context) {
        super(context);
        // 关闭 nextlib 的统一扩展模式
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF);
        // 关键：允许硬解失败后回退到软解
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
        // 1. 先加 MediaCodec 音频渲染器
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

        // 2. 音频 FFmpeg 插到最前（永远优先）
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
            // FFmpeg so 不存在时忽略
        }
    }

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
        // 1. MediaCodec 永远在前面
        super.buildVideoRenderers(
                context,
                EXTENSION_RENDERER_MODE_OFF,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                allowedVideoJoiningTimeMs,
                out
        );

        // 2. FFmpeg 视频渲染器放在最后（仅兜底）
        try {
            out.add(
                    new FfmpegVideoRenderer(
                            allowedVideoJoiningTimeMs,
                            eventHandler,
                            eventListener,
                            DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
                    )
            );
        } catch (Exception ignored) {
            // FFmpeg so 不存在时忽略
        }
    }
}