package xyz.doikki.videoplayer.exo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import android.content.Context;
import java.util.Collections; 
import java.util.List; 

// 设备性能检测与解码策略选择
public class AdaptiveDecoderSelector implements MediaCodecSelector {
    private final Context context;
    private boolean useHardwareDecoder;
    
    public AdaptiveDecoderSelector(Context context) {
        this.context = context;
        // 根据设备CPU核心数和频率决定解码方式
        useHardwareDecoder = isHighEndDevice();
    }
    
    private boolean isHighEndDevice() {
        // 检测设备是否为高端机型
        return Build.VERSION.SDK_INT >= 31 && 
               (getCpuCores() >= 8 || getMaxCpuFrequency() > 2800000);
    }
    
    @Override
    public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecure) {
        if (useHardwareDecoder) {
            return MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure);
        } else {
            // 返回空列表，触发软件解码
            return Collections.emptyList();
        }
    }
}
