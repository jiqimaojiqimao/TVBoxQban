package xyz.doikki.videoplayer.exo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodecMediaCodecUtil;
import android.content.Context;
import java.util.Collections; 
import java.util.List; 
import android.os.Build;

public class AdaptiveDecoderSelector implements MediaCodecSelector {
    private final Context context;
    private boolean useHardwareDecoder;

    public AdaptiveDecoderSelector(Context context) {
        this.context = context;
        // 设备性能检测逻辑调整为统一接口
        useHardwareDecoder = isHighEndDevice();
    }

    private boolean isHighEndDevice() {
        // 使用Build.VERSION.SDK_INT检测系统版本
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false; // 旧版系统默认使用软件解码
        }
        
        // 实际项目中应使用CpuInfo类获取CPU信息
        // 此处简化为示例逻辑
        return true; // 假设所有设备都支持硬件解码
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
            
            // 优先选择Amlogic解码器
            for (MediaCodecInfo info : candidates) {
                if (info.getName().equals("amlogic.avc.decoder")) {
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
