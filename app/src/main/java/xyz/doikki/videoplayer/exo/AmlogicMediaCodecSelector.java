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
    public void createDecoderSelector(String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) {
        // 获取所有可用的解码器信息
        List<MediaCodecInfo> decoderInfos = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
        );

        // 优先选择硬件解码器
        MediaCodecInfo selectedDecoder = null;
        for (MediaCodecInfo info : decoderInfos) {
            if (info.isHardwareAccelerated()) {
                selectedDecoder = info;
                break;
            }
        }

        // 如果没有找到硬件解码器，则抛出异常
        if (selectedDecoder == null) {
            throw new IllegalStateException("No hardware decoder available for " + mimeType);
        }

        // 调用基类方法创建解码器
        super.createDecoderSelector(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
        );
    }
}
