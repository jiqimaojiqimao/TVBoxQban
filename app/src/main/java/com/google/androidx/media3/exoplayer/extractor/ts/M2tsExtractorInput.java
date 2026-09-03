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
import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;

/**
 * 把底层 192 字节/包的 M2TS 输入，桥接为剥离头部后的纯 TS 输入。
 *
 * 每次 {@link #fillAndConsume()} 从底层读取一块数据，剥离 4 字节 header 后，
 * 包装成 {@link ByteArrayExtractorInput} 供 {@link MyTsExtractor} 消费。
 */
@UnstableApi
final class M2tsExtractorInput {

    private static final int BLOCK_PACKETS = 64;
    private static final int BLOCK_SIZE = M2tsUtil.M2TS_PACKET_SIZE * BLOCK_PACKETS; // 12288

    private final ExtractorInput delegate;
    private final byte[] blockBuffer = new byte[BLOCK_SIZE];
    private ByteArrayExtractorInput strippedInput;

    M2tsExtractorInput(ExtractorInput delegate) {
        this.delegate = delegate;
    }

    /** 返回剥离头部后的纯 TS 输入；可能为 null（需先调用 {@link #fillAndConsume()}）。 */
    ByteArrayExtractorInput getStrippedInput() {
        return strippedInput;
    }

    /**
     * 从底层读取一块 M2TS 数据，剥离头部后准备好纯 TS 输入。
     *
     * @return true 表示有新数据可读；false 表示已到输入末尾
     */
    boolean fillAndConsume() throws IOException {
        int bytesRead = delegate.read(blockBuffer, 0, blockBuffer.length);
        if (bytesRead <= 0) {
            strippedInput = null;
            return false;
        }
        // 对齐到完整包：只处理整数个 M2TS 包，避免跨块的部分包
        int fullLength = (bytesRead / M2tsUtil.M2TS_PACKET_SIZE) * M2tsUtil.M2TS_PACKET_SIZE;
        byte[] stripped = M2tsUtil.stripHeaders(blockBuffer, 0, fullLength);
        strippedInput = new ByteArrayExtractorInput(stripped);
        return true;
    }
}
