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
        // 参考官方：按 192 字节间隔找 0x47
        int peekLength = M2TS_PACKET_SIZE * (SNIFF_PACKET_COUNT + 3);
        byte[] buffer = new byte[peekLength];
        try {
            input.peekFully(buffer, 0, peekLength);
        } catch (IOException e) {
            return false;
        }

        // 寻找第一个 0x47，且后续每隔 192 字节也是 0x47
        for (int i = 0; i < peekLength - M2TS_PACKET_SIZE * SNIFF_PACKET_COUNT + 1; i++) {
            boolean sync = true;
            for (int j = 0; j < SNIFF_PACKET_COUNT; j++) {
                if (buffer[i + j * M2TS_PACKET_SIZE] != 0x47) {
                    sync = false;
                    break;
                }
            }
            if (sync) {
                // i 是第一个 0x47 的位置，M2TS 包起始 = i - 4
                int skipBytes = i - M2TS_HEADER_SIZE;
                if (skipBytes > 0) {
                    input.skipFully(skipBytes);
                }
                // 现在底层位置刚好对齐到 M2TS 包边界（192字节对齐）
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
