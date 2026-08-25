package xyz.doikki.videoplayer.exo;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.Map;
import java.util.List;

/**
 * M2TS BDAV 剥离数据源 - 修复版
 *
 * 修复内容：
 * 1. 移除了"尾部假 EOF"机制（原代码在文件最后 196KB 内直接返回 0，导致 ExoPlayer 无限 seek 循环）
 * 2. 改为正常限制 DataSpec 长度到剩余文件数据，让 ExoPlayer 自然检测 EOF
 * 3. read() 方法支持读取多个 BDAV 包以填充输出缓冲区，提高效率
 * 4. 处理小缓冲区场景：当输出缓冲区小于 188 字节时，使用 pending 缓冲区暂存
 * 5. getResponseHeaders() 直接委托上游，不再返回假 Content-Length
 */
public final class M2TsStrippingDataSource implements DataSource {

    private static final String TAG = "M2TS_xuameng";
    private static final int BDAV_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;

    private final DataSource upstream;
    private final byte[] scratch = new byte[BDAV_PACKET_SIZE];
    // 用于处理输出缓冲区小于一个 TS 包(188字节)的情况
    private final byte[] pending = new byte[TS_PACKET_SIZE];
    private int pendingOffset = 0;
    private int pendingLength = 0;

    private long realFileLength = C.LENGTH_UNSET;
    private boolean lengthDetected = false;
    private boolean eof = false;

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

        // 192 字节 BDAV 对齐
        long upstreamPosition = (requestedPosition / BDAV_PACKET_SIZE) * BDAV_PACKET_SIZE;

        // 如果位置超出文件长度，自然返回 EOF（不是假 EOF）
        if (realFileLength > 0 && upstreamPosition >= realFileLength) {
            Log.w(TAG, "Position " + upstreamPosition + " beyond file length " + realFileLength + ", EOF");
            eof = true;
            return 0;
        }

        // 限制读取长度为剩余文件数据，防止越界读取
        long remainingBytes = realFileLength > 0 ? realFileLength - upstreamPosition : C.LENGTH_UNSET;
        long actualLength;
        if (dataSpec.length != C.LENGTH_UNSET) {
            actualLength = remainingBytes == C.LENGTH_UNSET ? dataSpec.length : Math.min(dataSpec.length, remainingBytes);
        } else {
            actualLength = remainingBytes;
        }

        DataSpec safeSpec = dataSpec.buildUpon()
                .setPosition(upstreamPosition)
                .setLength(actualLength)
                .build();

        try {
            long result = upstream.open(safeSpec);
            eof = false;
            return result;
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            if (e.responseCode == 416) {
                Log.w(TAG, "416 on open, EOF");
                eof = true;
                return 0;
            }
            throw e;
        }
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (eof) return C.RESULT_END_OF_INPUT;

        // 先排出之前缓存的 TS 字节（处理小缓冲区场景）
        if (pendingLength > 0) {
            int copy = Math.min(pendingLength, length);
            System.arraycopy(pending, pendingOffset, buffer, offset, copy);
            pendingOffset += copy;
            pendingLength -= copy;
            return copy;
        }

        int totalRead = 0;
        while (totalRead < length) {
            int read;
            try {
                read = upstream.read(scratch, 0, BDAV_PACKET_SIZE);
            } catch (HttpDataSource.InvalidResponseCodeException e) {
                if (e.responseCode == 416) {
                    Log.w(TAG, "416 on read, EOF");
                    eof = true;
                    return totalRead > 0 ? totalRead : C.RESULT_END_OF_INPUT;
                }
                throw e;
            }

            if (read != BDAV_PACKET_SIZE) {
                eof = true;
                return totalRead > 0 ? totalRead : C.RESULT_END_OF_INPUT;
            }

            if (scratch[4] != 0x47) {
                Log.w(TAG, "BDAV sync lost, EOF");
                eof = true;
                return totalRead > 0 ? totalRead : C.RESULT_END_OF_INPUT;
            }

            // 将 188 字节 TS 包复制到输出缓冲区
            int available = length - totalRead;
            if (available >= TS_PACKET_SIZE) {
                System.arraycopy(scratch, 4, buffer, offset + totalRead, TS_PACKET_SIZE);
                totalRead += TS_PACKET_SIZE;
            } else {
                // 缓冲区不够放完整 TS 包，暂存到 pending
                System.arraycopy(scratch, 4, pending, 0, TS_PACKET_SIZE);
                pendingLength = TS_PACKET_SIZE;
                pendingOffset = 0;
                int copy = Math.min(TS_PACKET_SIZE, available);
                System.arraycopy(pending, 0, buffer, offset + totalRead, copy);
                totalRead += copy;
                pendingLength -= copy;
                pendingOffset = copy;
                break;
            }
        }

        return totalRead;
    }

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
                        Log.d(TAG, "real file length=" + realFileLength);
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
        // 修复：直接委托上游，不再返回假的 Content-Length: 0
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        try {
            upstream.close();
        } finally {
            pendingLength = 0;
            pendingOffset = 0;
            eof = false;
        }
    }
}
