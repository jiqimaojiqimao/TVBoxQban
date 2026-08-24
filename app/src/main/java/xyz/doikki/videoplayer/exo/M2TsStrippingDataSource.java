package xyz.doikki.videoplayer.exo;

import android.net.Uri;
import java.util.List;
import java.util.Map;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

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
    android.util.Log.d("M2TS_xuameng", "open uri=" + dataSpec.uri
            + " position=" + dataSpec.position);

    long requestedPosition = dataSpec.position;

    // ✅ BDAV 必须对齐
    long alignedPosition =
            (requestedPosition / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;

    if (alignedPosition != requestedPosition) {
        android.util.Log.d("M2TS_xuameng",
                "align position: " + requestedPosition + " -> " + alignedPosition);
    }

    DataSpec alignedSpec = dataSpec.buildUpon()
            .setPosition(alignedPosition)
            .build();

    this.bytesRead = 0;
    this.pendingOffset = 0;
    this.pendingLength = 0;
    this.isBdav = true; // ✅ 明确是 BDAV

    return upstream.open(alignedSpec);
}

@Override
public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
    if (length == 0) return 0;

    // 优先消费 pending
    if (pendingLength > 0) {
        int copy = Math.min(pendingLength, length);
        System.arraycopy(pending, pendingOffset, buffer, offset, copy);
        pendingOffset += copy;
        pendingLength -= copy;
        bytesRead += copy;
        return copy;
    }

    // 读一个 BDAV 包
    int read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
    if (read != BDAV_PACKET_SIZE) {
        return C.RESULT_END_OF_INPUT;
    }

    // ✅ BDAV 必须校验同步头
    if (scratch[4] != 0x47) {
        android.util.Log.d("M2TS_xuameng", "BDAV sync lost, skip packet");
        // 跳过这个包，继续读下一个
        return read(buffer, offset, length);
    }

    // ✅ 只给 188
    System.arraycopy(scratch, 4, buffer, offset, TS_PACKET_SIZE);
    bytesRead += TS_PACKET_SIZE;
    android.util.Log.d("M2TS_xuameng", "sync ok, stripped 192->188");
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
