package xyz.doikki.videoplayer.exo;

import androidx.annotation.NonNull;
import androidx.media3.mediacodec.MediaCodecSelector;
import androidx.media3.mediacodec.MediaCodecUtil;
import androidx.media3.mediacodec.MediaCodecInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 针对 Amlogic 电视：
 * - 屏蔽 Dolby Vision 专用 OMX
 * - 强制使用普通 HEVC 解码器
 */
public class ExoMediaCodecSelector implements MediaCodecSelector {

    @NonNull
    @Override
    public List<MediaCodecInfo> getDecoderInfos(
            @NonNull String mimeType,
            boolean requiresSecureDecoder,
            boolean requiresTunnelingDecoder
    ) throws MediaCodecUtil.DecoderQueryException {

        List<MediaCodecInfo> infos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
        );

        // 只对视频做处理
        if (!mimeType.startsWith("video/")) {
            return infos;
        }

        List<MediaCodecInfo> filtered = new ArrayList<>();
        for (MediaCodecInfo info : infos) {
            String name = info.name;   // ✅ Media3 用字段，不是方法

            // ❌ 屏蔽 Amlogic 的 DV 专用解码器
            if (name.contains("dolby-vision")
                    || name.contains("dvhe")
                    || name.contains("awesome")) {
                continue;
            }

            filtered.add(info);
        }

        // 如果全被过滤了，回退到原始列表（保底）
        return filtered.isEmpty() ? infos : filtered;
    }
}
