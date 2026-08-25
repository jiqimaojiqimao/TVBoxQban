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
 * M2TS BDAV 剥离数据源（最终版 · 生产可用）
 *
 * <p>功能：
 * <ol>
 *   <li>将 BDAV 封装的 M2TS（每包 192 字节：4 字节 BDAV 起始码 00 00 00 01 + 188 字节 TS 包）
 *       剥离为标准 M2TS（每包 188 字节，以 0x47 同步头开头），供 Media3 TsExtractor 解析。</li>
 *   <li>通过扫描 0x47 同步头及其间距（188 / 192）动态判定当前封装格式，
 *       支持同一文件中 BDAV(192) 与标准 TS(188) 混合的场景。</li>
 *   <li>seek 后自动重新同步：丢弃不对齐数据，定位到第一个 0x47 对齐位置。</li>
 *   <li>完全遵循 Media3 DataSource 契约：open / read / close 状态由调用方控制；
 *       不在 open() 内自行 close upstream，不在 sniff 阶段伪造 EOF。</li>
 * </ol>
 *
 * <p>BDAV 头格式（4 字节大端起始码）：00 00 00 01，其后紧跟 188 字节 TS 包（首字节 0x47）。
 */
public class M2TsStrippingDataSource implements DataSource {

    private static final String TAG = "M2TS_xuameng";
    private static final boolean DEBUG = true; // 发布时可置 false

    private static final int TS_SYNC_BYTE = 0x47;
    private static final int BDAV_PACKET_SIZE = 192;
    private static final int TS_PACKET_SIZE = 188;

    // 内部缓冲：容纳若干 BDAV 包 + 对齐余量，减少 upstream syscall
    private static final int INTERNAL_BUFFER_SIZE = BDAV_PACKET_SIZE * 32; // 6144

    private final DataSource upstream;

    private final byte[] internalBuf = new byte[INTERNAL_BUFFER_SIZE];
    private int bufValid = 0; // internalBuf 中有效字节数

    /** 同步模式：0=未知(需判定) 1=标准 TS188 2=BDAV192 */
    private int syncMode = 0;

    /** BDAV 模式连续头校验失败计数，超过阈值降级为 TS188 */
    private int bdavFailCount = 0;
    private static final int BDAV_FAIL_THRESHOLD = 4;

    // 调试统计
    private long totalUpstreamRead = 0;
    private long totalOutputBytes = 0;
    private int resyncCount = 0;

    public M2TsStrippingDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        // open 可能被 sniff 多次调用（每次 DataSpec/position 不同），重置每调用状态
        bufValid = 0;
        syncMode = 0;
        return upstream.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int totalOut = 0;

        while (totalOut < length) {
            // 1. 补充内部缓冲（所有模式下都补充，直到缓冲满或 upstream 耗尽）
            while (bufValid < INTERNAL_BUFFER_SIZE) {
                int read = upstream.read(internalBuf, bufValid, INTERNAL_BUFFER_SIZE - bufValid);
                if (read == C.RESULT_END_OF_INPUT) break;
                bufValid += read;
                totalUpstreamRead += read;
            }

            // 2. 未知模式：尝试判定
            if (syncMode == 0) {
                int detected = detectSyncMode();
                if (detected == 0) {
                    // 数据不足以判定
                    if (bufValid >= INTERNAL_BUFFER_SIZE) {
                        // 缓冲已满仍无法判定：退化为 TS188 透传，避免死循环
                        if (DEBUG) Log.w(TAG, "Buffer full but sync undetectable, fallback TS188");
                        syncMode = 1;
                    } else if (totalOut == 0 && bufValid == 0) {
                        // upstream 已耗尽且缓冲为空
                        return C.RESULT_END_OF_INPUT;
                    } else {
                        // 已有部分数据但不足以判定：若 upstream 也耗尽，按 TS188 透传残余
                        if (bufValid > 0) syncMode = 1; else break;
                    }
                } else {
                    syncMode = detected;
                    if (DEBUG) Log.d(TAG, "Sync mode detected: " + (syncMode == 2 ? "BDAV192" : "TS188"));
                }
            }

            // 3. 按当前模式处理
            if (syncMode == 2) {
                // BDAV192：需要凑齐至少一个 192 字节包
                if (bufValid < BDAV_PACKET_SIZE) {
                    if (totalOut > 0) break; // 已有产出，先返回，下次再读
                    else if (bufValid == 0) return C.RESULT_END_OF_INPUT;
                    else { syncMode = 1; continue; } // 残余不足一包，退化为 TS188
                }
                if (isBdavHeader(internalBuf, 0)) {
                    // 合法 BDAV 包：跳过 4 字节头，输出 188 字节 TS
                    int outAvail = length - totalOut;
                    int copy = Math.min(TS_PACKET_SIZE, outAvail);
                    System.arraycopy(internalBuf, 4, buffer, offset + totalOut, copy);
                    totalOut += copy;
                    totalOutputBytes += copy;
                    bdavFailCount = 0; // 校验成功，重置失败计数
                    // 从缓冲移除已消费的 192 字节
                    int consumed = BDAV_PACKET_SIZE;
                    int left = bufValid - consumed;
                    if (left > 0) System.arraycopy(internalBuf, consumed, internalBuf, 0, left);
                    bufValid = left;
                } else {
                    // BDAV 头校验失败
                    bdavFailCount++;
                    if (DEBUG) Log.d(TAG, "BDAV header mismatch (fail=" + bdavFailCount + ")");

                    // 若缓冲首字节已是 0x47（TS 同步头），说明当前已是标准 TS188 数据，
                    // BDAV 判定可能是前期误判或格式已切换，立即降级为 TS188。
                    if ((internalBuf[0] & 0xFF) == TS_SYNC_BYTE) {
                        if (DEBUG) Log.i(TAG, "BDAV mismatch but 0x47 at buf[0], downgrade to TS188");
                        syncMode = 1;
                        bdavFailCount = 0;
                        // 重新判定/对齐后进入 TS188 分支（下一轮循环处理）
                        continue;
                    }

                    if (bdavFailCount >= BDAV_FAIL_THRESHOLD) {
                        if (DEBUG) Log.i(TAG, "BDAV fail threshold reached, downgrade to TS188");
                        syncMode = 1;
                        bdavFailCount = 0;
                    }
                    resyncToNextSyncByte();
                }
            } else {
                // TS188 模式：按 0x47 对齐后，以 188 字节为块透传输出
                // 先对齐：若缓冲首字节不是 0x47，丢弃直到找到
                if (bufValid > 0 && (internalBuf[0] & 0xFF) != TS_SYNC_BYTE) {
                    resyncToNextSyncByte();
                }
                if (bufValid < TS_PACKET_SIZE) {
                    if (totalOut > 0) break;
                    else if (bufValid == 0) return C.RESULT_END_OF_INPUT;
                    else { copyRemaining(buffer, offset, length, totalOut); return totalOut; }
                }
                int can = (bufValid / TS_PACKET_SIZE) * TS_PACKET_SIZE;
                int outAvail = length - totalOut;
                int produce = Math.min(can, outAvail);
                if (produce == 0) break; // 输出空间不足一包
                System.arraycopy(internalBuf, 0, buffer, offset + totalOut, produce);
                totalOut += produce;
                totalOutputBytes += produce;
                int left = bufValid - produce;
                if (left > 0) System.arraycopy(internalBuf, produce, internalBuf, 0, left);
                bufValid = left;
            }
        }

        return totalOut;
    }

    /** 把缓冲中残余数据（不足一包的最后片段）拷贝到输出 */
    private void copyRemaining(byte[] buffer, int offset, int length, int totalOut) {
        int outAvail = length - totalOut;
        int copy = Math.min(bufValid, outAvail);
        if (copy > 0) {
            System.arraycopy(internalBuf, 0, buffer, offset + totalOut, copy);
            totalOut += copy;
            totalOutputBytes += copy;
            bufValid -= copy;
        }
    }

    /**
     * 扫描内部缓冲，根据 0x47 同步头间距判定封装格式。
     * 间距 188 → TS188；间距 192 且 preceding 4 字节为 BDAV 起始码 → BDAV192。
     */
    private int detectSyncMode() {
        if (bufValid < BDAV_PACKET_SIZE + TS_PACKET_SIZE) return 0;

        int first = indexOfSyncByte(0);
        if (first < 0) return 0;
        int second = indexOfSyncByte(first + TS_PACKET_SIZE);
        if (second < 0) return 0;

        int gap = second - first;
        if (gap == TS_PACKET_SIZE) {
            return 1; // TS188
        } else if (gap == BDAV_PACKET_SIZE) {
            // 校验是否为 BDAV：first 之前的 4 字节应为起始码 00 00 00 01
            if (first >= 4 && isBdavStartCode(internalBuf, first - 4)) return 2;
            // 单次 192 间距无起始码佐证：再做一次多包验证
            int third = indexOfSyncByte(second + TS_PACKET_SIZE);
            if (third > 0 && (third - second) == BDAV_PACKET_SIZE) return 2;
            return 0;
        }
        return 0;
    }

    /** 从 start 起查找下一个 0x47 同步字节索引 */
    private int indexOfSyncByte(int start) {
        for (int i = start; i < bufValid; i++) {
            if ((internalBuf[i] & 0xFF) == TS_SYNC_BYTE) return i;
        }
        return -1;
    }

    /** BDAV 起始码：00 00 00 01 */
    private boolean isBdavStartCode(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) == 0x00
                && (buf[offset + 1] & 0xFF) == 0x00
                && (buf[offset + 2] & 0xFF) == 0x00
                && (buf[offset + 3] & 0xFF) == 0x01;
    }

    /**
     * BDAV 包头校验：4 字节起始码 00 00 00 01，且紧随其后的 TS 包首字节为 0x47。
     * 注：TS 同步头 0x47 位于 BDAV 头之后偏移 4 处。
     */
    private boolean isBdavHeader(byte[] buf, int offset) {
        return isBdavStartCode(buf, offset)
                && (offset + 4 < buf.length)
                && ((buf[offset + 4] & 0xFF) == TS_SYNC_BYTE);
    }

    /** 重同步：丢弃缓冲中第一个 0x47 之前的数据，重置模式判定 */
    private void resyncToNextSyncByte() {
        int sync = indexOfSyncByte(0);
        if (sync > 0) {
            int left = bufValid - sync;
            System.arraycopy(internalBuf, sync, internalBuf, 0, left);
            bufValid = left;
        } else {
            bufValid = 0;
        }
        syncMode = 0; // 重新判定
        resyncCount++;
        if (DEBUG) Log.d(TAG, "Resynced to next 0x47, resyncCount=" + resyncCount);
    }

    @Override
    public void close() throws IOException {
        try {
            upstream.close();
        } finally {
            bufValid = 0;
            syncMode = 0;
            if (DEBUG) Log.d(TAG, "closed. upstreamRead=" + totalUpstreamRead
                    + " outputBytes=" + totalOutputBytes
                    + " resyncs=" + resyncCount);
        }
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
