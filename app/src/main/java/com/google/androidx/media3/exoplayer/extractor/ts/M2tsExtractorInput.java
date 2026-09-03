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

    M2tsExtractorInput(ExtractorInput input) {
        this.input = input;
    }

    void reset() {
        tsBufferPos = 0;
        tsBufferLimit = 0;
    }

    /** 确保内部有至少一个剥头后的 TS 包（188字节） */
    private void ensurePacket() throws IOException {
        if (tsBufferPos < tsBufferLimit) return;

        // 循环读取，确保凑满 192 字节（处理网络流的部分读取）
        int totalRead = 0;
        while (totalRead < 192) {
            int read = input.read(m2tsBuffer, totalRead, 192 - totalRead);
            if (read == -1) {
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
        // 简化：不支持单字节 peek，TsDurationReader 用的是 peekFully
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
        // 从底层 peek 192 字节块，剥头后填入 buffer
        int filled = 0;
        while (filled < length) {
            try {
                input.peekFully(m2tsBuffer, 0, 192);
            } catch (IOException e) {
                if (allowEndOfInput && filled == 0) return false;
                throw e;
            }
            int toCopy = Math.min(length - filled, 188);
            System.arraycopy(m2tsBuffer, 4, buffer, offset + filled, toCopy);
            filled += toCopy;
            input.advancePeekPosition(192);
        }
        return true;
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
        // 按 192/188 比例推进底层 peek 位置
        int m2tsBytes = (int) Math.ceil(length * 192.0 / 188.0);
        input.advancePeekPosition(m2tsBytes);
    }

    @Override
    public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException {
        advancePeekPosition(length);
        return true;
    }

    @Override
    public void resetPeekPosition() {
        input.resetPeekPosition();
    }

    @Override
    public long getPeekPosition() {
        return input.getPeekPosition() * 188 / 192; // 近似
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getLength() {
        long len = input.getLength();
        return len == -1 ? -1 : len * 188 / 192;
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
        this.position = position;
        throw e;
    }
}
