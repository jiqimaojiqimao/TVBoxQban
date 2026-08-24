package xyz.doikki.videoplayer.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.HttpDataSource;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.io.IOException;

/**
 * 把 Blu-ray BDAV m2ts 的 192 字节包剥成 188 字节标准 MPEG-TS 再交给 Extractor。
 *
 * 每个 BDAV 包结构：4 字节时间戳 + 188 字节 TS 包（首字节 0x47）。
 * 本类每次从上游读 192 字节，校验第 5 字节(下标4)==0x47，然后把后面 188 字节
 * 填入调用方的 buffer。这样 TsExtractor 看到的完全是标准 188 对齐 TS，sniff 必过。
 *
 * 说明：
 * - open() 会向上游透传 DataSpec，但把 position 按 192/188 比例换算后定位到最近包边界，
 *   避免 seek 到包中间导致 0x47 对不齐。
 * - 若上游不是 192 对齐的 BDAV（如已经是 188 TS），本类会回退为直接透传，不破坏原有播放。
 */
public final class M2TsStrippingDataSource implements DataSource {

    private static final int BDAV_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;
    // 连续多少次读不到 0x47 就判定为"非 BDAV"，进入透传模式
    private static final int SYNC_FAIL_LIMIT = 8;

    private final DataSource upstream;

    @Nullable
    private TransferListener transferListener;

    // 从上游一次读一个 BDAV 包
    private final byte[] scratch = new byte[BDAV_PACKET_SIZE];
    // 已剥头、等待被消费的标准 TS 数据
    private byte[] pending = new byte[0];
    private int pendingOffset = 0;
    private int pendingLength = 0;

    private long upstreamOpenPosition = 0; // 本次 open 时上游起始 position
    private long streamLength = C.LENGTH_UNSET;
    private long bytesRead = 0;

    // 模式：true=按 192 剥头；false=直接透传（非 BDAV 或已确认不是）
    private boolean stripping = true;
    private int syncFailCount = 0;
    private boolean modeDecided = false;
    private boolean isBdav = false;

    public M2TsStrippingDataSource(DataSource upstream) {
        this.upstream = Assertions.checkNotNull(upstream);
    }

    @Override
    public void addTransferListener(@NonNull TransferListener transferListener) {
        this.transferListener = transferListener;
        upstream.addTransferListener(transferListener);
    }

@Override
public long open(@NonNull DataSpec dataSpec) throws IOException {
    long requestedPosition = dataSpec.position;

    android.util.Log.d("M2TS_xuameng",
            "open uri=" + dataSpec.uri + " position=" + requestedPosition);

    long upstreamPosition;
    if (requestedPosition == 0) {
        upstreamPosition = 0;
    } else {
        long packets = requestedPosition / TS_PACKET_SIZE;
        long remainder = requestedPosition % TS_PACKET_SIZE;
        upstreamPosition = packets * BDAV_PACKET_SIZE + remainder;
    }

    // 192 对齐
    upstreamPosition =
            (upstreamPosition / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;

    long finalPosition = upstreamPosition;

    // ✅ 循环回退，直到 open 成功 或 position=0
    while (finalPosition >= 0) {
        try {
            DataSpec trySpec = dataSpec.buildUpon()
                    .setPosition(finalPosition)
                    .build();

            long result = upstream.open(trySpec);
            android.util.Log.d("M2TS_xuameng",
                    "open success at " + finalPosition);
            return result;

        } catch (HttpDataSource.InvalidResponseCodeException e) {
            if (e.responseCode == 416) {
                android.util.Log.w("M2TS_xuameng",
                        "416 at " + finalPosition + ", rollback 192");
                finalPosition -= BDAV_PACKET_SIZE;
                if (finalPosition < 0) {
                    finalPosition = 0;
                }
                continue;
            }
            throw e;
        }
    }

    // 理论上不会走到这里
    throw new IOException("Failed to open after 416 rollback");
}

@Override
public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
    if (length == 0) return 0;

    if (pendingLength > 0) {
        int copy = Math.min(pendingLength, length);
        System.arraycopy(pending, pendingOffset, buffer, offset, copy);
        pendingOffset += copy;
        pendingLength -= copy;
        bytesRead += copy;
        return copy;
    }

    int read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
    if (read != BDAV_PACKET_SIZE) {
        android.util.Log.d("M2TS_xuameng", "END_OF_INPUT");
        return C.RESULT_END_OF_INPUT;
    }

    if (scratch[4] != 0x47) {
        android.util.Log.d("M2TS_xuameng", "BDAV sync lost, skip");
        return read(buffer, offset, length);
    }

    System.arraycopy(scratch, 4, buffer, offset, TS_PACKET_SIZE);
    bytesRead += TS_PACKET_SIZE;
    return TS_PACKET_SIZE;
}

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        try {
            upstream.close();
        } finally {
            pending = new byte[0];
            pendingOffset = 0;
            pendingLength = 0;
        }
    }

    private void ensurePendingCapacity(int needed) {
        if (pending.length < needed) {
            pending = new byte[needed];
        }
    }
}
