
import android.content.Context;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import java.util.List;

public class AmlogicMediaCodecSelector  extends DefaultVideoDecoderFactory {
    public AmlogicMediaCodecSelector(Context context) {
        super(context, MediaCodecSelector.DEFAULT, false);
    }

    @Override
    public MediaCodecInfo getDecoderInfo(
            String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) {
        List<MediaCodecInfo> decoderInfos = MediaCodecUtil.getDecoderInfos(
                mimeType, 
                requiresSecureDecoder,
                requiresTunnelingDecoder);
        
        // 优先选择硬件解码器
        for (MediaCodecInfo info : decoderInfos) {
            if (info.isHardwareAccelerated()) {
                return info;
            }
        }
        
        // 如果没有硬件解码器则返回null（不自动降级到软件解码）
        return null;
    }
}
