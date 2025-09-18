package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.os.Build;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import java.util.Collections;
import java.util.List;

public class AdaptiveDecoderSelector implements MediaCodecSelector {
    private final Context context;
    private boolean useHardwareDecoder;

    public AdaptiveDecoderSelector(Context context) {
        this.context = context;
        useHardwareDecoder = isHighEndDevice();
    }

    private boolean isHighEndDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        return true;
    }

    @Override
    public List<MediaCodecInfo> getDecoderInfos(
            String mimeType,
            boolean requiresSecure,
            boolean requiresTunneling) throws MediaCodecUtil.DecoderQueryException {

        try {
            if (useHardwareDecoder) {
                List<MediaCodecInfo> candidates = MediaCodecSelector.DEFAULT
                    .getDecoderInfos(mimeType, requiresSecure, requiresTunneling);

                for (MediaCodecInfo info : candidates) {
                    if (info.name.equals("amlogic")) {
                        // 创建新实例并强制标记硬件加速
MediaCodecInfo amlogicInfo = new MediaCodecInfo(
    info.name,
    info.mimeType,
    null, // codecAlias
    info.getCapabilitiesForType(info.mimeType), // capabilities
    true,  // hardwareAccelerated
    false, // softwareOnly
    false, // vendor
    false, // alias
    false, // encoder
    false  // forceDisableAdaptive
);
                        return Collections.singletonList(amlogicInfo);
                    }
                }
                return candidates;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            throw new RuntimeException("Decoder query failed", e);
        }
    }
}
