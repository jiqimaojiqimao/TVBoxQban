package xyz.doikki.videoplayer.exo;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Blu-ray BDAV m2ts（192B） → 标准 MPEG-TS（188B）
 *
 * ✅ 适配 123 网盘严格 Range 行为
 * ✅ 自动探测真实文件大小
 * ✅ 尾部 seek 永不越界
 * ✅ Media3 / ExoPlayer 通用
 */
public final class M2TsStrippingDataSource implements DataSource {

    private static final String TAG = "M2TS_xuameng";

    private static final int BDAV_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;
    private static final int SYNC_FAIL_LIMIT = 8;

    private final DataSource upstream;

    @Nullable
    private TransferListener transferListener;

    private final byte[] scratch = new byte[BDAV_PACKET_SIZE];
    private byte[] pending = new byte[0];
    private int pendingOffset = 0;
    private int pendingLength = 0;

    private long bytesRead = 0;
    private boolean stripping = true;
    private int syncFailCount = 0;
    private boolean modeDecided = false;

    /** 123 网盘真实文件大小（通过 HEAD / Range 探测） */
    private long realFileLength = C.LENGTH_UNSET;

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

        Log.d(TAG, "open uri=" + dataSpec.uri + " position=" + requestedPosition);

        // ✅ 第一次 open 时，必须探测真实文件大小
        if (realFileLength == C.LENGTH_UNSET) {
            detectRealFileLength(dataSpec);
        }

        long upstreamPosition;

        if (requestedPosition == 0) {
            upstreamPosition = 0;
        } else if (realFileLength > 0) {
            // ✅ 尾部 seek：只 seek 到最后一个合法 BDAV 包
            long lastPacketStart =
                    (realFileLength / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;

            if (lastPacketStart >= BDAV_PACKET_SIZE) {
                upstreamPosition = lastPacketStart - BDAV_PACKET_SIZE;
            } else {
                upstreamPosition = 0;
            }

            Log.d(TAG, "tail seek: realFileLength=" + realFileLength
                    + " upstreamPosition=" + upstreamPosition);
        } else {
            // fallback（理论上不会走到）
            long packets = requestedPosition / TS_PACKET_SIZE;
            long remainder = requestedPosition % TS_PACKET_SIZE;
            upstreamPosition = packets * BDAV_PACKET_SIZE + remainder;
            upstreamPosition =
                    (upstreamPosition / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;
        }

        // 192 对齐
        upstreamPosition =
                (upstreamPosition / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;

        long finalPosition = upstreamPosition;

        // ✅ 最多回退 100 次，防止死循环
        for (int retry = 0; retry < 100; retry++) {
            try {
                DataSpec trySpec = dataSpec.buildUpon()
                        .setPosition(finalPosition)
                        .build();

                long result = upstream.open(trySpec);

                // probe 一个 BDAV 包
                int read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
                if (read != BDAV_PACKET_SIZE) {
                    upstream.close();
                    throw new IOException("probe read failed");
                }

                if (scratch[4] != 0x47) {
                    upstream.close();
                    throw new IOException("BDAV sync failed");
                }

                Log.d(TAG, "✅ open + probe success at " + finalPosition);

                // 缓存剥头后的 188 字节
                ensurePendingCapacity(TS_PACKET_SIZE);
                System.arraycopy(scratch, 4, pending, 0, TS_PACKET_SIZE);
                pendingOffset = 0;
                pendingLength = TS_PACKET_SIZE;

                this.bytesRead = 0;
                this.stripping = true;
                this.modeDecided = true;

                return result;

            } catch (HttpDataSource.InvalidResponseCodeException e) {
                if (e.responseCode == 416) {
                    Log.w(TAG, "❌ 416 at " + finalPosition + ", rollback 192");
                    try { upstream.close(); } catch (Exception ignored) {}
                } else {
                    throw e;
                }
            } catch (IOException e) {
                Log.w(TAG, "❌ probe failed at " + finalPosition);
                try { upstream.close(); } catch (Exception ignored) {}
            }

            finalPosition -= BDAV_PACKET_SIZE;
            if (finalPosition < 0) {
                finalPosition = 0;
            }
        }

        throw new IOException("BDAV open failed after retries");
    }

    /**
     * ✅ 强制探测真实文件大小
     * 123 网盘支持 Range: bytes=0-0，并返回 Content-Range
     */
    private void detectRealFileLength(DataSpec dataSpec) throws IOException {
        try {
            DataSpec probe = dataSpec.buildUpon()
                    .setPosition(0)
                    .setLength(1)
                    .build();

            upstream.open(probe);

            Map<String, List<String>> headers = upstream.getResponseHeaders();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String key = entry.getKey();
                if (key == null) continue;

                if ("Content-Range".equalsIgnoreCase(key)) {
                    for (String value : entry.getValue()) {
                        if (value != null && value.startsWith("bytes")) {
                            int slash = value.lastIndexOf('/');
                            if (slash > 0) {
                                realFileLength =
                                        Long.parseLong(value.substring(slash + 1));
                                Log.d(TAG, "✅ real file length=" + realFileLength);
                                return;
                            }
                        }
                    }
                }

                if ("Content-Length".equalsIgnoreCase(key)) {
                    for (String value : entry.getValue()) {
                        realFileLength = Long.parseLong(value);
                        Log.d(TAG, "✅ real file length from Content-Length="
                                + realFileLength);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "detectRealFileLength failed", e);
        } finally {
            try { upstream.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length)
            throws IOException {

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

        int read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
        if (read != BDAV_PACKET_SIZE) {
            Log.d(TAG, "END_OF_INPUT");
            return C.RESULT_END_OF_INPUT;
        }

        if (scratch[4] != 0x47) {
            Log.w(TAG, "❌ BDAV sync lost at " + bytesRead);
            throw new IOException("BDAV sync lost");
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
