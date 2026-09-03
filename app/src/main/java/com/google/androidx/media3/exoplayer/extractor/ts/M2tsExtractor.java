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
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;

import java.io.IOException;

/**
 * M2TS (.m2ts / 192 字节每包) 容器 Extractor。
 *
 * 实现方式：把底层 192 字节/包的 M2TS 流，通过 {@link M2tsExtractorInput}
 * 在读取时剥掉每包前 4 字节 header，转换为 188 字节/包的纯 TS 流，
 * 再交给 {@link MyTsExtractor} 完成实际解析。
 */
@UnstableApi
public final class M2tsExtractor implements Extractor {

    private static final int DEFAULT_TIMESTAMP_SEARCH_BYTES = 1024 * 1024;

    private final MyTsExtractor tsExtractor;
    private M2tsExtractorInput m2tsInput;

    /** 无参构造：使用默认配置（单 PMT + DTS 音频支持）。 */
    public M2tsExtractor() {
        this(TsExtractor.MODE_SINGLE_PMT);
    }

    /**
     * @param mode {@link MyTsExtractor#MODE_SINGLE_PMT} 或 {@link MyTsExtractor#MODE_MULTI_PMT}
     */
    public M2tsExtractor(int mode) {
        // ✅ 对齐 MyTsExtractor 真实的 3 参构造器：(int mode, int flags, int timestampSearchBytes)
        //   MyTsExtractor 内部会自己 new DefaultTsPayloadReaderFactory(flags)
        this.tsExtractor = new MyTsExtractor(
                mode,
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
                DEFAULT_TIMESTAMP_SEARCH_BYTES);
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        // M2TS sniff：按 192 字节对齐，检查每包头部之后的 TS sync byte (0x47)
        final int peekLen = M2tsUtil.M2TS_PACKET_SIZE * 6;
        byte[] buf = new byte[peekLen];
        input.peekFully(buf, 0, peekLen);

        for (int offset = 0; offset < M2tsUtil.M2TS_HEADER_SIZE; offset++) {
            boolean valid = true;
            for (int p = 0; p < 5; p++) {
                int idx = offset + p * M2tsUtil.M2TS_PACKET_SIZE + M2tsUtil.M2TS_HEADER_SIZE;
                if (idx >= peekLen || buf[idx] != 0x47) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                input.skipFully(offset);
                return true;
            }
        }
        return false;
    }

    @Override
    public void init(ExtractorOutput output) {
        tsExtractor.init(output);
    }

    @Override
    public void seek(long position, long timeUs) {
        tsExtractor.seek(position, timeUs);
        // ✅ MyTsExtractor 没有 enableNextVideoKeyFrame，不调用
        m2tsInput = null; // 重置桥接状态
    }

    @Override
    public void release() {
        tsExtractor.release();
    }

    @Override
    @Extractor.ReadResult
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        if (m2tsInput == null || m2tsInput.getStrippedInput() == null) {
            m2tsInput = new M2tsExtractorInput(input);
        }
        while (true) {
            ByteArrayExtractorInput stripped = m2tsInput.getStrippedInput();
            if (stripped != null) {
                int result = tsExtractor.read(stripped, seekPosition);
                if (result != Extractor.RESULT_END_OF_INPUT) {
                    return result;
                }
            }
            boolean hasMore = m2tsInput.fillAndConsume();
            if (!hasMore) {
                return Extractor.RESULT_END_OF_INPUT;
            }
        }
    }

    /** 便捷 Factory。 */
    public static final ExtractorsFactory FACTORY =
            () -> new Extractor[]{new M2tsExtractor()};
}
