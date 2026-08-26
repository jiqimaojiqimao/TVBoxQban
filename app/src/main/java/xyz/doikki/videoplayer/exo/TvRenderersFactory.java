package xyz.doikki.videoplayer.exo;

import android.os.Build;

import androidx.annotation.Nullable;


import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * xuameng TV 专用 RenderersFactory：
 * - 音频：FFmpeg 永远优先
 * - 视频：MediaCodec 优先，硬解失败才使用 FFmpeg 软解兜底
 */
@UnstableApi
public class TvRenderersFactory extends NextRenderersFactory {

    private static final int DEFAULT_MAX_DROPPED_FRAMES = 50;
    private final MediaCodecSelector selector;

    public TvRenderersFactory(Context context, MediaCodecSelector selector) {
        super(context);
        this.selector = selector;
        setMediaCodecSelector(selector);
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF);
        setEnableDecoderFallback(true);
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
        // ✅ 强制把 DV 当成 HEVC 处理，绕过 DV 专用解码器
        super.buildVideoRenderers(
                context,
                EXTENSION_RENDERER_MODE_OFF,
                new MediaCodecSelector() {
                    @NonNull
                    @Override
                    public List<MediaCodecInfo> getDecoderInfos(
                            @NonNull String mimeType,
                            boolean requiresSecureDecoder,
                            boolean requiresTunnelingDecoder
                    ) throws MediaCodecUtil.DecoderQueryException {

                        // ✅ 关键：DV 直接降级为 HEVC
                        if (mimeType.equals("video/dolby-vision")) {
                            mimeType = "video/hevc";
                        }

                        List<MediaCodecInfo> infos =
                                selector.getDecoderInfos(
                                        mimeType,
                                        requiresSecureDecoder,
                                        requiresTunnelingDecoder
                                );

                        if (!mimeType.startsWith("video/")) {
                            return infos;
                        }

                        // ✅ Amlogic 专用拦截
                        if (!"amlogic".equalsIgnoreCase(Build.MANUFACTURER)) {
                            return infos;
                        }

                        List<MediaCodecInfo> filtered = new ArrayList<>();
                        for (MediaCodecInfo info : infos) {
                            String name = info.name;
                            if (name.contains("OMX.amlogic.dolby-vision")
                                    || name.contains("dvhe.decoder.awesome2")) {
                                continue;
                            }
                            filtered.add(info);
                        }

                        return filtered.isEmpty() ? infos : filtered;
                    }
                },
                enableDecoderFallback,
                eventHandler,
                eventListener,
                allowedVideoJoiningTimeMs,
                out
        );

        // FFmpeg 软解兜底
        try {
            out.add(
                    new FfmpegVideoRenderer(
                            allowedVideoJoiningTimeMs,
                            eventHandler,
                            eventListener,
                            DEFAULT_MAX_DROPPED_FRAMES
                    )
            );
        } catch (Exception ignored) {}
    }
}
