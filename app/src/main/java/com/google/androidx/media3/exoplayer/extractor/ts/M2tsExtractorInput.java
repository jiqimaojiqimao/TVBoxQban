package com.google.androidx.media3.exoplayer.extractor.ts;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;

@UnstableApi
final class M2tsExtractorInput {

    private final ExtractorInput delegate;
    private byte[] blockBuffer = new byte[M2tsUtil.M2TS_PACKET_SIZE * 64];
    private ByteArrayExtractorInput strippedInput;

    M2tsExtractorInput(ExtractorInput delegate) {
        this.delegate = delegate;
    }

    ByteArrayExtractorInput getStrippedInput() {
        return strippedInput;
    }

    boolean fillAndConsume() throws IOException {
        int bytesRead = delegate.read(blockBuffer, 0, blockBuffer.length);
        if (bytesRead <= 0) {
            strippedInput = null;
            return false;
        }
        byte[] stripped = M2tsUtil.stripHeaders(blockBuffer, 0, bytesRead);
        strippedInput = new ByteArrayExtractorInput(stripped);
        return true;
    }
}