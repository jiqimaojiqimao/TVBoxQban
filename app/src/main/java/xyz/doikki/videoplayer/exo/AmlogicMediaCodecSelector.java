package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AmlogicMediaCodecSelector implements MediaCodecSelector {
    // Amlogc解码器特征标识
    private static final String AML_SIGNATURE = "amlogic";
    // 已知有效的解码器白名单
    private static final String[] AML_DECODERS = {
        "amlogic.hevc.decoder.awesome2",
        "amlogic.avc.decoder.awesome"
    };

    @Override
    public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder) {
        List<MediaCodecInfo> infos = new ArrayList<>();
        
        // 获取系统所有硬件解码器（忽略mimeType参数）
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : codecList.getCodecInfos()) {
            if (isAmlogicDecoder(info)) {
                infos.add(0, info); // 优先返回Amlogic解码器
            } else if (info.isHardwareAccelerated()) {
                infos.add(info); // 其他硬件解码器
            }
        }
        return infos;
    }

    // 判断是否为Amlogic解码器
    private boolean isAmlogicDecoder(MediaCodecInfo info) {
        return info.getName().contains(AML_SIGNATURE) 
            || Arrays.stream(AML_DECODERS).anyMatch(info::getName);
    }
}
