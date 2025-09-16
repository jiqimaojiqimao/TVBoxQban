package xyz.doikki.videoplayer.exo;

import android.content.Context;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import java.util.List;

public class AmlogicMediaCodecSelector extends DefaultRenderersFactory {
    public AmlogicMediaCodecSelector(Context context) {
        super(context);
    }

    @Override
    public MediaCodecSelector createDecoderSelector(String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) {
        // 获取所有可用的解码器信息
        List<MediaCodecInfo> decoderInfos = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
        );

        // 优先选择硬件解码器
        MediaCodecInfo selectedDecoder = null;
        for (MediaCodecInfo info : decoderInfos) {
            // 通过名称判断是否为Amlogic硬件解码器
            if (isAmlogicHardwareDecoder(info.getName())) {
                selectedDecoder = info;
                break;
            }
        }

        // 如果没有找到硬件解码器，则抛出异常
        if (selectedDecoder == null) {
            throw new IllegalStateException("No Amlogic hardware decoder available for " + mimeType);
        }

        // 返回自定义的MediaCodecSelector
        return new MediaCodecSelector() {
            @Override
            public MediaCodecInfo getDecoderInfo(String mimeType) {
                return selectedDecoder;
            }
        };
    }

    // 判断是否为Amlogic硬件解码器
    private boolean isAmlogicHardwareDecoder(String name) {
        return name != null && (name.contains("OMX.Amlogic.") || name.contains("OMX.amlogic."));
    }
}
