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

    private final MyTsExtractor tsExtractor;

    public M2tsExtractor(int mode) {
        this.tsExtractor = new MyTsExtractor(
                mode,
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
                1024 * 1024
        );
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        // 简单嗅探：检查前 192 字节是否是 M2TS（前 4 字节通常是 0x47 之前的 header）
        // TS 同步字节是 0x47，在 M2TS 里位于第 4 字节（offset 4）
        byte[] header = new byte[192];
        int read = input.read(header, 0, 192);
        if (read < 192) return false;
        // 检查第一个 TS 包的同步字节
        boolean isM2ts = (header[4] == 0x47);
        input.resetPeekPosition();
        return isM2ts;
    }

    @Override
    public void init(ExtractorOutput output) {
        tsExtractor.init(output);
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        // 关键：用装饰器包装，实时剥头，流式传给 MyTsExtractor
        M2tsExtractorInput adaptedInput = new M2tsExtractorInput(input);
        return tsExtractor.read(adaptedInput, seekPosition);
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
