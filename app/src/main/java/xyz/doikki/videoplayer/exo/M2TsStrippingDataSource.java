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
/**
 * M2TS BDAV 剥离数据源 (第11版)
 * 核心修复：获取文件长度后立即 close upstream，避免两次 open 导致 IllegalStateException
 */
public class M2TsStrippingDataSource implements DataSource {

    private static final String TAG = "M2TS_xuameng";
    private static final long PROBE_RANGE = 200 * 1024;
    private static final long PROBE_POSITION_TOLERANCE = 1000;
    private static final int MAX_PROBE_COUNT = 8;

    private final DataSource upstream;
    private DataSpec dataSpec;

    private long realFileLength = C.LENGTH_UNSET;
    private long lastOpenPosition = -1;
    private int probeCount = 0;
    private boolean opened = false;

    public M2TsStrippingDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.dataSpec = dataSpec;

        long requestedPosition = dataSpec.position;

        // 1. 文件长度优先使用 dataSpec.length
        if (realFileLength == C.LENGTH_UNSET) {
            if (dataSpec.length != C.LENGTH_UNSET) {
                realFileLength = dataSpec.length;
            } else {
                // 仅在此处 open 一次拿长度
                realFileLength = upstream.open(dataSpec);
                opened = true;
                Log.d(TAG, "File length resolved: " + realFileLength);
                return realFileLength - requestedPosition;
            }
        }

        // 2. 探测循环检测
        if (realFileLength > 0
                && requestedPosition >= realFileLength - PROBE_RANGE) {

            if (lastOpenPosition >= 0
                    && Math.abs(requestedPosition - lastOpenPosition) < PROBE_POSITION_TOLERANCE) {
                probeCount++;
                Log.d(TAG, "Probe attempt " + probeCount);

                if (probeCount > MAX_PROBE_COUNT) {
                    Log.w(TAG, "Probe loop detected, return EOF");
                    return 0;
                }
            } else {
                probeCount = 1;
            }
            lastOpenPosition = requestedPosition;
        } else {
            lastOpenPosition = -1;
            probeCount = 0;
        }

        // 3. 正式 open（只做一次）
        if (!opened) {
            long remaining = realFileLength - requestedPosition;
            DataSpec adjusted = remaining > 0
                    ? dataSpec.subrange(requestedPosition, remaining)
                    : dataSpec.subrange(requestedPosition);
            long result = upstream.open(adjusted);
            opened = true;
            return result;
        }

        // 已经 open 过，直接返回剩余长度
        return realFileLength - requestedPosition;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return upstream.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
        if (opened) {
            upstream.close();
            opened = false;
        }
        lastOpenPosition = -1;
        probeCount = 0;
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
