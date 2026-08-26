package xyz.doikki.videoplayer.exo;

import androidx.annotation.NonNull;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;

import java.util.ArrayList;
import java.util.List;

public class ExoMediaCodecSelector implements MediaCodecSelector {

    @NonNull
    @Override
    public List<MediaCodecInfo> getDecoderInfos(
            @NonNull String mimeType,
            boolean requiresSecureDecoder,
            boolean requiresTunnelingDecoder
    ) throws MediaCodecUtil.DecoderQueryException {

        List<MediaCodecInfo> infos =
                MediaCodecSelector.DEFAULT.getDecoderInfos(
                        mimeType, requiresSecureDecoder, requiresTunnelingDecoder);

        if (!mimeType.startsWith("video/")) {
            return infos;
        }

        // ✅ 只针对 Amlogic
        boolean isAmlogic = Util.MANUFACTURER.equals("amlogic");
        if (!isAmlogic) {
            return infos;
        }

        List<MediaCodecInfo> filtered = new ArrayList<>();
        for (MediaCodecInfo info : infos) {
            String name = info.name;

            // ✅ 精准屏蔽 Amlogic DV 专用解码器
            if (name.contains("OMX.amlogic.dolby-vision")
                    || name.contains("dvhe.decoder.awesome2")) {
                continue;
            }

            filtered.add(info);
        }

        return filtered.isEmpty() ? infos : filtered;
    }
}