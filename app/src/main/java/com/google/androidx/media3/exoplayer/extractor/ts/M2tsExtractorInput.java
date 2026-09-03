/*
 * M2TS 流式剥头装饰器
 * 每次从底层读取 192 字节，剥离 4 字节 header，向上提供 188 字节纯 TS
 */
package com.google.androidx.media3.exoplayer.extractor.ts;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;

@UnstableApi
final class M2tsExtractorInput implements ExtractorInput {

    private final ExtractorInput input;
    private final byte[] m2tsBuffer = new byte[192];
    private final byte[] tsBuffer = new byte[188];
    private int tsBufferPos = 0;
    private int tsBufferLimit = 0;
    private long position = 0;
    private long peekPosition = 0;

    M2tsExtractorInput(ExtractorInput input) {
        this.input = input;
    }

    void reset() {
        tsBufferPos = 0;
        tsBufferLimit = 0;
    }

    private void ensurePacket() throws IOException {
        if (tsBufferPos < tsBufferLimit) return;

        // 循环读取，确保凑满 192 字节（处理网络流的部分读取）
        int totalRead = 0;
        while (totalRead < 192) {
            int read = input.read(m2tsBuffer, totalRead, 192 - totalRead);
            if (read == -1) {
                // EOF
                tsBufferLimit = 0;
                return;
            }
            totalRead += read;
        }

        // 剥离 4 字节 header，提取 188 字节 TS 包
        System.arraycopy(m2tsBuffer, 4, tsBuffer, 0, 188);
        tsBufferPos = 0;
        tsBufferLimit = 188;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        ensurePacket();
        if (tsBufferLimit == 0) return -1;

        int toCopy = Math.min(length, tsBufferLimit - tsBufferPos);
        System.arraycopy(tsBuffer, tsBufferPos, buffer, offset, toCopy);
        tsBufferPos += toCopy;
        position += toCopy;
        return toCopy;
    }

    @Override
    public int peek(byte[] buffer, int offset, int length) throws IOException {
        // MyTsExtractor.read() 内部不调用 peek，这里简化实现
        return -1;
    }

    @Override
    public void readFully(byte[] buffer, int offset, int length) throws IOException {
        readFully(buffer, offset, length, false);
    }

    @Override
    public boolean readFully(byte[] buffer, int offset, int length, boolean allowEndOfInput) throws IOException {
        int read = 0;
        while (read < length) {
            int result = read(buffer, offset + read, length - read);
            if (result == -1) {
                if (allowEndOfInput && read == 0) return false;
                throw new IOException("End of input");
            }
            read += result;
        }
        return true;
    }

    @Override
    public void peekFully(byte[] buffer, int offset, int length) throws IOException {
        peekFully(buffer, offset, length, false);
    }

    @Override
    public boolean peekFully(byte[] buffer, int offset, int length, boolean allowEndOfInput) throws IOException {
        throw new UnsupportedOperationException("peekFully not supported");
    }

    @Override
    public void skipFully(int length) throws IOException {
        skipFully(length, false);
    }

    @Override
    public boolean skipFully(int length, boolean allowEndOfInput) throws IOException {
        long skipped = skip(length);
        if (skipped < length) {
            if (allowEndOfInput) return false;
            throw new IOException("End of input");
        }
        return true;
    }

    @Override
    public int skip(int length) throws IOException {
        int skipped = 0;
        byte[] temp = new byte[Math.min(length, 188)];
        while (skipped < length) {
            int toRead = Math.min(length - skipped, temp.length);
            int result = read(temp, 0, toRead);
            if (result == -1) break;
            skipped += result;
        }
        return skipped;
    }

    @Override
    public void advancePeekPosition(int length) throws IOException {
        peekPosition += length;
    }

    @Override
    public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException {
        peekPosition += length;
        return true;
    }

    @Override
    public void resetPeekPosition() {
        peekPosition = position;
    }

    @Override
    public long getPeekPosition() {
        return peekPosition;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getLength() {
        return input.getLength();
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
        this.position = position;
        throw e;
    }
}