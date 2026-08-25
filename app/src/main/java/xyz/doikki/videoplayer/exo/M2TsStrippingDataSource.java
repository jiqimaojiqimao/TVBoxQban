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
 * M2TS BDAV 剥离数据源 (第10版)
 * 核心修复：移除全局 probeLoopDetected 标志，避免误杀正常播放位置；
 * 仅在探测循环时返回 EOF，close 后重置状态。
 */
public class M2TsStrippingDataSource implements DataSource {

    private static final String TAG = "M2TS_xuameng";
    private static final int BDAV_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;
    
    // 探测范围：文件末尾 200KB
    private static final long PROBE_RANGE = 200 * 1024;
    // 位置容差：两次 open 位置差小于 1000 字节视为探测
    private static final long PROBE_POSITION_TOLERANCE = 1000;
    // 最大探测次数
    private static final int MAX_PROBE_COUNT = 8;

    private final DataSource upstream;
    private DataSpec dataSpec;
    private long realFileLength;
    
    // 探测状态（仅在 open 时累加，close 时重置）
    private long lastOpenPosition = -1;
    private int probeCount = 0;
    private boolean eof = false;

    public M2TsStrippingDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.dataSpec = dataSpec;
        this.eof = false; // 每次 open 重置 EOF 状态
        long requestedPosition = dataSpec.position;

        // 1. 获取真实文件长度
        if (realFileLength == 0) {
            realFileLength = upstream.open(dataSpec);
            Log.d(TAG, "File opened, real length: " + realFileLength);
        }

        // 2. 探测循环检测（仅在文件末尾范围内触发）
        if (realFileLength > 0 && requestedPosition >= realFileLength - PROBE_RANGE) {
            if (lastOpenPosition >= 0 
                    && Math.abs(requestedPosition - lastOpenPosition) < PROBE_POSITION_TOLERANCE) {
                probeCount++;
                Log.d(TAG, "Probe attempt " + probeCount + " near end (position=" + requestedPosition + ")");
                
                if (probeCount > MAX_PROBE_COUNT) {
                    Log.w(TAG, "Probe loop detected near end of file (position=" + requestedPosition 
                            + ", count=" + probeCount + "), returning EOF to break the loop");
                    eof = true;
                    lastOpenPosition = requestedPosition;
                    return 0; // 返回 0 终止当前探测
                }
            } else {
                // 位置跨度大，重置计数
                probeCount = 1;
            }
            lastOpenPosition = requestedPosition;
        } else {
            // 不在探测区域，重置探测状态
            lastOpenPosition = -1;
            probeCount = 0;
        }

        // 3. 正常读取逻辑（限制长度防止越界）
        long remaining = realFileLength - requestedPosition;
        if (remaining <= 0) {
            eof = true;
            return 0;
        }

        DataSpec adjustedSpec = dataSpec.subrange(requestedPosition, remaining);
        return upstream.open(adjustedSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (eof) {
            return C.RESULT_END_OF_INPUT;
        }
        return upstream.read(buffer, offset, readLength);
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
            // 关键：close 时重置探测状态，允许后续正常播放
            eof = false;
            lastOpenPosition = -1;
            probeCount = 0;
        }
    }
}
