/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.androidx.media3.exoplayer.extractor.ts;

import androidx.media3.common.util.UnstableApi;

/**
 * M2TS 相关常量与工具方法。
 *
 * M2TS 每包 192 字节 = 4 字节头部 + 188 字节标准 TS 包。
 * 本类负责把 192 字节/包的 M2TS 数据转换为 188 字节/包的纯 TS 数据。
 */
@UnstableApi
public final class M2tsUtil {

    /** M2TS 包大小（4 字节头部 + 188 字节 TS 包）。 */
    public static final int M2TS_PACKET_SIZE = 192;
    /** M2TS 每包头部大小。 */
    public static final int M2TS_HEADER_SIZE = 4;
    /** 标准 TS 包大小。 */
    public static final int TS_PACKET_SIZE = 188;

    private M2tsUtil() {}

    /**
     * 从 M2TS 数据中剥离每包的 4 字节头部，返回纯 TS 数据。
     *
     * @param src    源 M2TS 数据
     * @param offset 起始偏移
     * @param length 长度（应为 M2TS_PACKET_SIZE 的整数倍，否则末尾不完整包被丢弃）
     * @return 剥离头部后的纯 TS 数据
     */
    public static byte[] stripHeaders(byte[] src, int offset, int length) {
        int fullPackets = length / M2TS_PACKET_SIZE;
        if (fullPackets == 0) {
            return new byte[0];
        }
        byte[] out = new byte[fullPackets * TS_PACKET_SIZE];
        for (int i = 0; i < fullPackets; i++) {
            System.arraycopy(
                    src, offset + i * M2TS_PACKET_SIZE + M2TS_HEADER_SIZE,
                    out, i * TS_PACKET_SIZE,
                    TS_PACKET_SIZE);
        }
        return out;
    }

    /**
     * 在缓冲区中查找 M2TS 包的起始偏移。M2TS 包按 M2TS_PACKET_SIZE 对齐，
     * 每包第 M2TS_HEADER_SIZE 字节应为 TS sync byte (0x47)。
     */
    public static int findM2tsPacketOffset(byte[] data, int offset, int limit) {
        for (int i = offset; i <= limit - M2TS_PACKET_SIZE; i++) {
            boolean valid = true;
            for (int p = 0; p < 3; p++) {
                int idx = i + p * M2TS_PACKET_SIZE + M2TS_HEADER_SIZE;
                if (idx >= limit || data[idx] != 0x47) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return i;
            }
        }
        return -1;
    }
}
