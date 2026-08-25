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
        // MediaCodec 永远优先
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

        // FFmpeg 视频软解兜底（排在最后）
        try {
            out.add(
                    new FfmpegVideoRenderer(
                            allowedVideoJoiningTimeMs,
                            eventHandler,
                            eventListener,
                            DEFAULT_MAX_DROPPED_FRAMES
                    )
            );
        } catch (Exception ignored) {
            // FFmpeg so 未加载时忽略
        }
    }
}
