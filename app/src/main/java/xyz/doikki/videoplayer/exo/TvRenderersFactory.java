package xyz.doikki.videoplayer.exo;

import android.os.Build;
import android.os.Handler;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.Format;
import androidx.media3.common.C;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * xuameng TV 专用 RenderersFactory：
 * - 音频：FFmpeg 永远优先
 * - 视频：MediaCodec 优先，硬解失败才使用 FFmpeg 软解兜底
 * - 只将 dvhe 编码 + PQ (ST.2084) 色域的 DV 流降级为 HEVC 处理
 */
@UnstableApi
public class TvRenderersFactory extends NextRenderersFactory {

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
        // MediaCodec 优先
        // 注意：MediaCodecSelector 不再做 mimeType 转换，DV→HEVC 的降级逻辑移到 createMediaCodecVideoRenderer 中
        super.buildVideoRenderers(
                context,
                EXTENSION_RENDERER_MODE_OFF,
                MediaCodecSelector.DEFAULT,
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
        }
    }

    /**
     * 重写以实现对 DV 流的精确过滤：
     * 只有 codec 以 "dvhe" 开头 且 colorInfo.transferFunction == PQ(ST.2084) 的 DV 流
     * 才会被降级为 HEVC 解码。
     *
     * 注意：如果 NextRenderersFactory 内部重写了 createMediaCodecVideoRenderer 且不调用 super，
     * 则此方法可能不会被调用。此时需要改用 buildVideoRenderers 中手动创建自定义 MediaCodecVideoRenderer 的方式。
     */
    @SuppressWarnings("unchecked")
    protected MediaCodecVideoRenderer createMediaCodecVideoRenderer(
            VideoRendererEventListener eventListener,
            long allowedJoiningTimeMs,
            @NonNull MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            @NonNull Object decoderSelector,  // 实际类型为 MediaCodecVideoDecoderSelector
            @NonNull ArrayList<Renderer> eventLoggers
    ) {
        return new MediaCodecVideoRenderer(
                eventListener,
                allowedJoiningTimeMs,
                mediaCodecSelector,
                enableDecoderFallback,
                decoderSelector,
                eventLoggers
        ) {
            /**
             * 在格式确定后、创建解码器之前拦截，检查完整 Format 信息（codec + color），
             * 满足条件时才将 mimeType 改为 video/hevc。
             */
            @Override
            protected void onInputFormatChanged(
                    @NonNull FormatHolder formatHolder,
                    @Nullable Object decoderReuseEvaluation
            ) {
                Format format = formatHolder.format;
                // 精确过滤：mimeType=dolby-vision + codec=dvhe* + color=PQ(ST.2084)
                if ("video/dolby-vision".equals(format.sampleMimeType)
                        && format.codecs != null
                        && format.codecs.startsWith("dvhe")
                        && format.colorInfo != null
                        && format.colorInfo.transferFunction == C.TRANSFER_SMPTE2084) {
                    // 降级为 HEVC
                    Format hevcFormat = format.buildUpon()
                            .setSampleMimeType("video/hevc")
                            .build();
                    formatHolder.format = hevcFormat;
                    super.onInputFormatChanged(formatHolder, decoderReuseEvaluation);
                    return;
                }
                super.onInputFormatChanged(formatHolder, decoderReuseEvaluation);
            }
        };
    }
}
