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
    
    private final MyTsExtractor tsExtractor;

    public M2tsExtractor(int mode) {
        // 直接告诉 MyTsExtractor 包大小是 192
        this.tsExtractor = new MyTsExtractor(
                mode,
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
                1024 * 1024,
                M2TS_PACKET_SIZE  // ← 关键！
        );
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        return tsExtractor.sniff(input); // 直接委托
    }

    @Override
    public void init(ExtractorOutput output) {
        tsExtractor.init(output);
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        // 直接传原始 input，不需要任何包装！
        return tsExtractor.read(input, seekPosition);
    }

    @Override
    public void seek(long position, long timeUs) {
        tsExtractor.seek(position, timeUs);
    }

    @Override
    public void release() {
        tsExtractor.release();
    }
}
