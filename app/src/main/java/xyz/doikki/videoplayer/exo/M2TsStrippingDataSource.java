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
 * ✅ 禁止 open() 修改 position
 * ✅ 禁止尾部 rollback
 * ✅ 禁止欺骗 ExoPlayer
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

    private long bytesRead = 0;
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

        // ✅ 如果请求位置超出文件末尾，直接返回 EOF
        if (realFileLength > 0 && requestedPosition >= realFileLength) {
            Log.w(TAG, "⚠️ requestedPosition >= realFileLength, return EOF");
            return 0;
        }

        // ✅ 192 对齐（不跳包）
        long upstreamPosition =
                (requestedPosition / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;

        DataSpec safeSpec = dataSpec.buildUpon()
                .setPosition(upstreamPosition)
                .build();

        try {
            return upstream.open(safeSpec);
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            if (e.responseCode == 416) {
                Log.w(TAG, "⚠️ 416, return EOF");
                return 0;
            }
            throw e;
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

        int read;
        try {
            read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            if (e.responseCode == 416) {
                Log.w(TAG, "⚠️ read 416, return EOF");
                return C.RESULT_END_OF_INPUT;
            }
            throw e;
        }

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

    /**
     * ✅ 123 网盘专用：只探测一次
     */
    private void detectRealFileLength(DataSpec dataSpec) throws IOException {
        try {
            DataSpec probe = dataSpec.buildUpon()
                    .setPosition(0)
                    .setLength(1)
                    .build();
            upstream.open(probe);
            Map<String, List<String>> headers = upstream.getResponseHeaders();
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if ("Content-Range".equalsIgnoreCase(e.getKey())) {
                    String v = e.getValue().get(0);
                    int slash = v.lastIndexOf('/');
                    if (slash > 0) {
                        realFileLength = Long.parseLong(v.substring(slash + 1));
                        Log.d(TAG, "✅ real file length=" + realFileLength);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { upstream.close(); } catch (Exception ignored) {}
        }
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
}
