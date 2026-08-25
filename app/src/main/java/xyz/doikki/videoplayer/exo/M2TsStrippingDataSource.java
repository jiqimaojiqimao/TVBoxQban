package xyz.doikki.videoplayer.exo;

import android.net.Uri;
import android.util.Log;

import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.Map;
import java.util.List;

public class M2TsStrippingDataSource implements DataSource {

    private static final String TAG = "M2TS_xuameng";
    private static final int BDAV_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;

    private final DataSource upstream;
    private byte[] packetBuffer = new byte[BDAV_PACKET_SIZE];
    private int packetOffset = 0;

    public M2TsStrippingDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        return upstream.open(dataSpec);
    }

@Override
public int read(byte[] buffer, int offset, int length) throws IOException {
    int total = 0;

    while (total < length) {
        // 1. 保证至少有 192 字节
        while (packetOffset < BDAV_PACKET_SIZE) {
            int read = upstream.read(
                    packetBuffer,
                    packetOffset,
                    BDAV_PACKET_SIZE - packetOffset
            );
            if (read == C.RESULT_END_OF_INPUT) {
                return total == 0 ? C.RESULT_END_OF_INPUT : total;
            }
            packetOffset += read;
        }

        // 2. 检查是否是 BDAV
        boolean isBdav =
                (packetBuffer[0] & 0xFF) == 0x00 &&
                (packetBuffer[1] & 0xFF) == 0x00 &&
                (packetBuffer[2] & 0xFF) == 0x00 &&
                (packetBuffer[3] & 0xFF) == 0x01 &&
                (packetBuffer[7] & 0xFF) == 0x47;

        if (!isBdav) {
            // 不是 BDAV，按普通 TS 处理
            int toCopy = Math.min(TS_PACKET_SIZE, length - total);
            System.arraycopy(packetBuffer, 0, buffer, offset + total, toCopy);
            total += toCopy;

            System.arraycopy(
                    packetBuffer,
                    toCopy,
                    packetBuffer,
                    0,
                    packetOffset - toCopy
            );
            packetOffset -= toCopy;
            continue;
        }

        // 3. BDAV：去掉前 4 字节
        int toCopy = Math.min(TS_PACKET_SIZE, length - total);
        System.arraycopy(packetBuffer, 4, buffer, offset + total, toCopy);
        total += toCopy;

        int consumed = 4 + toCopy;
        System.arraycopy(
                packetBuffer,
                consumed,
                packetBuffer,
                0,
                BDAV_PACKET_SIZE - consumed
        );
        packetOffset -= consumed;
    }

    return total;
}

    @Override
    public void close() throws IOException {
        upstream.close();
        packetOffset = 0;
    }

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public void addTransferListener(TransferListener listener) {
        upstream.addTransferListener(listener);
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }
}
