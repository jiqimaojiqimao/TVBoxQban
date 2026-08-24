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
 * ✅ TVBox + 123 网盘 + BDAV m2ts 终极版
 * ✅ 强制 HEAD + Range 探测真实文件大小
 * ✅ 禁止任何越界 Range
 * ✅ 尾部 seek 永不 416
 */
public final class M2TsStrippingDataSource implements DataSource {

    private static final String TAG = "M2TS_xuameng";
    private static final int BDAV_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;

    private final DataSource upstream;
    private final byte[] scratch = new byte[BDAV_PACKET_SIZE];
    private byte[] pending = new byte[0];
    private int pendingOffset = 0;
    private int pendingLength = 0;

    /** ✅ 123 网盘真实文件大小 */
    private long realFileLength = C.LENGTH_UNSET;
    private boolean lengthDetected = false;

    public M2TsStrippingDataSource(DataSource upstream) {
        this.upstream = Assertions.checkNotNull(upstream);
    }

    @Override
    public void addTransferListener(@NonNull TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        long requestedPosition = dataSpec.position;

        Log.d(TAG, "open uri=" + dataSpec.uri + " position=" + requestedPosition);

        if (!lengthDetected) {
            detectRealFileLength(dataSpec);
            lengthDetected = true;
        }

        // ✅ 如果 ExoPlayer 请求的位置已经超出文件末尾，直接返回 EOF
        if (realFileLength > 0 && requestedPosition >= realFileLength) {
            Log.w(TAG, "⚠️ requestedPosition >= realFileLength, return EOF");
            return 0;
        }

        long upstreamPosition = requestedPosition;

        if (requestedPosition > 0 && realFileLength > 0) {
            long packets = requestedPosition / TS_PACKET_SIZE;
            long remainder = requestedPosition % TS_PACKET_SIZE;
            upstreamPosition = packets * BDAV_PACKET_SIZE + remainder;
            upstreamPosition =
                    (upstreamPosition / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;

            // ✅ 强制限制在合法范围内
            if (upstreamPosition >= realFileLength) {
                upstreamPosition =
                        (realFileLength / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;
                if (upstreamPosition >= BDAV_PACKET_SIZE) {
                    upstreamPosition -= BDAV_PACKET_SIZE;
                } else {
                    upstreamPosition = 0;
                }
            }
        }

        Log.d(TAG, "✅ tail seek: realFileLength=" + realFileLength
                + " upstreamPosition=" + upstreamPosition);

        DataSpec safeSpec = dataSpec.buildUpon()
                .setPosition(upstreamPosition)
                .build();

        long result = upstream.open(safeSpec);

        int read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
        if (read != BDAV_PACKET_SIZE || scratch[4] != 0x47) {
            upstream.close();
            throw new IOException("BDAV probe failed");
        }

        ensurePendingCapacity(TS_PACKET_SIZE);
        System.arraycopy(scratch, 4, pending, 0, TS_PACKET_SIZE);
        pendingOffset = 0;
        pendingLength = TS_PACKET_SIZE;

        Log.d(TAG, "✅ open success at " + upstreamPosition);
        return result;
    }

    /**
     * ✅ 123 网盘专用：HEAD + Range 强制探测
     */
    private void detectRealFileLength(DataSpec dataSpec) throws IOException {
        // 1️⃣ 先试 HEAD
        try {
            DataSpec headSpec = dataSpec.buildUpon()
                    .setUri(dataSpec.uri)
                    .setPosition(0)
                    .setLength(0)
                    .build();
            upstream.open(headSpec);
            Map<String, List<String>> headers = upstream.getResponseHeaders();
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if ("Content-Length".equalsIgnoreCase(e.getKey())) {
                    realFileLength = Long.parseLong(e.getValue().get(0));
                    Log.d(TAG, "✅ real file length (HEAD)=" + realFileLength);
                    return;
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { upstream.close(); } catch (Exception ignored) {}
        }

        // 2️⃣ 再试 Range: bytes=0-0
        try {
            DataSpec rangeSpec = dataSpec.buildUpon()
                    .setPosition(0)
                    .setLength(1)
                    .build();
            upstream.open(rangeSpec);
            Map<String, List<String>> headers = upstream.getResponseHeaders();
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if ("Content-Range".equalsIgnoreCase(e.getKey())) {
                    String v = e.getValue().get(0);
                    int slash = v.lastIndexOf('/');
                    if (slash > 0) {
                        realFileLength = Long.parseLong(v.substring(slash + 1));
                        Log.d(TAG, "✅ real file length (Range)=" + realFileLength);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { upstream.close(); } catch (Exception ignored) {}
        }

        // 3️⃣ 兜底：二分法逼出文件大小
        long low = 0, high = 1L << 40;
        while (low < high) {
            long mid = (low + high + 1) / 2;
            try {
                DataSpec probe = dataSpec.buildUpon()
                        .setPosition(mid)
                        .setLength(1)
                        .build();
                upstream.open(probe);
                upstream.close();
                low = mid;
            } catch (HttpDataSource.InvalidResponseCodeException e) {
                if (e.responseCode == 416) {
                    high = mid - 1;
                } else {
                    break;
                }
            } catch (Exception ignored) {
                high = mid - 1;
            } finally {
                try { upstream.close(); } catch (Exception ignored) {}
            }
        }
        realFileLength = low;
        Log.d(TAG, "✅ real file length (binary search)=" + realFileLength);
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length)
            throws IOException {

        if (length == 0) return 0;

        if (pendingLength > 0) {
            int copy = Math.min(pendingLength, length);
            System.arraycopy(pending, pendingOffset, buffer, offset, copy);
            pendingOffset += copy;
            pendingLength -= copy;
            return copy;
        }

        int read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
        if (read != BDAV_PACKET_SIZE) {
            return C.RESULT_END_OF_INPUT;
        }

        if (scratch[4] != 0x47) {
            throw new IOException("BDAV sync lost");
        }

        System.arraycopy(scratch, 4, buffer, offset, TS_PACKET_SIZE);
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
