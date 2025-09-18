package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import android.content.Context;
import java.util.Collections;
import java.util.List;
import android.os.Build;

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
                    if (info.name.equals("amlogic.avc.decoder")) {
                        return Collections.singletonList(info);
                    }
                }
                return candidates;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            throw new MediaCodecUtil.DecoderQueryException(e);
        }
    }
}
