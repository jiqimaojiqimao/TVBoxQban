package xyz.doikki.videoplayer.exo;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AmlogicMediaCodecSelector implements MediaCodecSelector {

    private static final String AML_SIGNATURE = "amlogic";
    private static final String[] AML_DECODERS = {
        "amlogic.hevc.decoder.awesome2",
        "amlogic.avc.decoder.awesome"
    };

    @Override
    public List<MediaCodecInfo> getDecoderInfos(
            String mimeType,
            boolean requiresSecureDecoder,
            boolean requiresTunnelingDecoder) {

        List<MediaCodecInfo> infos = new ArrayList<>();
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);

        for (android.media.MediaCodecInfo androidInfo : codecList.getCodecInfos()) {
            MediaCodecInfo info = convertToExoMediaCodecInfo(androidInfo);
            if (isAmlogicDecoder(info)) {
                infos.add(0, info);
            } else if (info.isHardwareAccelerated()) {
                infos.add(info);
            }
        }
        return infos;
    }

    private boolean isAmlogicDecoder(MediaCodecInfo info) {
        String name = info.getName();
        return name.contains(AML_SIGNATURE) || 
               Arrays.asList(AML_DECODERS).contains(name);
    }

    private MediaCodecInfo convertToExoMediaCodecInfo(
            android.media.MediaCodecInfo androidInfo) {

        return new MediaCodecInfo(
            androidInfo.getName(),
            androidInfo.isEncoder(),
            androidInfo.getSecure() ? true : false, // 修正为getSecure()
            androidInfo.isHardwareAccelerated()
        );
    }
}
