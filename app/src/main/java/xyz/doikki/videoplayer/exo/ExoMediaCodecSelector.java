package xyz.doikki.videoplayer.exo;

import androidx.annotation.NonNull;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import java.util.ArrayList;
import java.util.List;

public class ExoMediaCodecSelector implements MediaCodecSelector {
    @NonNull
    @Override
    public List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> getDecoderInfos(
            @NonNull String mimeType,
            boolean requiresSecureDecoder,
            boolean requiresTunnelingDecoder
    ) throws MediaCodecUtil.DecoderQueryException {

        List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> infos =
                MediaCodecSelector.DEFAULT.getDecoderInfos(
                        mimeType, requiresSecureDecoder, requiresTunnelingDecoder);

        if (!mimeType.startsWith("video/")) return infos;

        List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> out = new ArrayList<>();
        for (androidx.media3.exoplayer.mediacodec.MediaCodecInfo i : infos) {
            String n = i.getName();
            if (n.contains("dolby-vision") || n.contains("dvhe") || n.contains("awesome"))
                continue;
            out.add(i);
        }
        return out.isEmpty() ? infos : out;
    }
}