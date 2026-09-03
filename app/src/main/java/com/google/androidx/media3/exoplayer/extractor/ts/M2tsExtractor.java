/*
 * M2TS Extractor - 参考官方 TsExtractor 实现
 * 通过装饰器模式将 192 字节 M2TS 实时剥头为 188 字节纯 TS
 */
package com.google.androidx.media3.exoplayer.extractor.ts;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.io.IOException;

@UnstableApi
public final class M2tsExtractor implements Extractor {

    private static final int M2TS_PACKET_SIZE = 192;
    private static final int M2TS_HEADER_SIZE = 4;
    private static final int TS_PACKET_SIZE = 188;
    private static final int SNIFF_PACKET_COUNT = 5;

    private final MyTsExtractor tsExtractor;
    private M2tsExtractorInput adaptedInput;

    public M2tsExtractor(int mode) {
        this.tsExtractor = new MyTsExtractor(
                mode,
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
                1024 * 1024
        );
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        // 参考官方：在原始 M2TS 流上按 192 字节对齐寻找 sync byte (0x47)
        int peekLength = M2TS_PACKET_SIZE * (SNIFF_PACKET_COUNT + 3);
        byte[] buffer = new byte[peekLength];

        try {
            input.peekFully(buffer, 0, peekLength);
        } catch (IOException e) {
            return false;
        }

        // 寻找同步模式：每 192 字节的偏移 4 处应该是 0x47
        for (int startPos = 0; startPos < M2TS_PACKET_SIZE; startPos++) {
            boolean sync = true;
            for (int i = 0; i < SNIFF_PACKET_COUNT; i++) {
                int offset = startPos + i * M2TS_PACKET_SIZE + M2TS_HEADER_SIZE;
                if (offset >= peekLength || buffer[offset] != 0x47) {
                    sync = false;
                    break;
                }
            }
            if (sync) {
                // 对齐到第一个 TS 包的起始位置（跳过 M2TS header）
                input.skipFully(startPos + M2TS_HEADER_SIZE);
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
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        // 创建装饰器，实时剥头，流式传给 MyTsExtractor
        if (adaptedInput == null) {
            adaptedInput = new M2tsExtractorInput(input);
        }
        return tsExtractor.read(adaptedInput, seekPosition);
    }

    @Override
    public void seek(long position, long timeUs) {
        tsExtractor.seek(position, timeUs);
        if (adaptedInput != null) {
            adaptedInput.reset();
        }
    }

    @Override
    public void release() {
        tsExtractor.release();
    }
}