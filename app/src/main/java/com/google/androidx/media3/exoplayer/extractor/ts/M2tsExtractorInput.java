package com.google.androidx.media3.exoplayer.extractor.ts;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;

/**
 * 装饰器：将 192 字节/包的 M2TS 流实时转换为 188 字节/包的纯 TS 流。
 * 每次 read() 最多提供一个 TS 包（188 字节）。
 */
@UnstableApi
final class M2tsExtractorInput implements ExtractorInput {

    private final ExtractorInput input;
    private final byte[] m2tsPacket = new byte[192];
    private final byte[] tsPacket = new byte[188];

    // 内部缓冲状态
    private int bufferedBytes = 0;  // tsPacket 里有多少字节还没被读走
    private int bufferedPos = 0;    // tsPacket 里已读位置

    private long position = 0;      // 逻辑位置（纯 TS 视角）
    private long peekPosition = 0;
    private long peekBasePosition = 0; // 进入 peek 模式时的 position

    M2tsExtractorInput(ExtractorInput input) {
        this.input = input;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;

        // 如果内部缓冲没有数据了，从底层读一个新的 M2TS 包
        if (bufferedBytes == 0) {
            int read = input.read(m2tsPacket, 0, 192);
            if (read == -1) return -1;  // 底层流真正结束
            if (read < 192) {
                // 不完整包，忽略（流末尾可能不足 192 字节）
                return -1;
            }
            // 剥离 4 字节 header：跳过前 4 字节，拷贝 188 字节
            System.arraycopy(m2tsPacket, 4, tsPacket, 0, 188);
            bufferedBytes = 188;
            bufferedPos = 0;
        }

        // 从内部缓冲拷贝数据给调用者
        int toCopy = Math.min(length, bufferedBytes);
        System.arraycopy(tsPacket, bufferedPos, buffer, offset, toCopy);
        bufferedPos += toCopy;
        bufferedBytes -= toCopy;
        position += toCopy;

        return toCopy;
    }

    @Override
    public int peek(byte[] buffer, int offset, int length) throws IOException {
        long savedPos = position;
        long savedPeekPos = peekPosition;
        int savedBufBytes = bufferedBytes;
        int savedBufPos = bufferedPos;

        // 重置到 peek 起点
        if (peekPosition == 0 || peekPosition < peekBasePosition) {
            // 简化：peek 不支持跨越包边界的随机访问
            // 对于 TS 解析，peek 主要用于 sniff，实际播放时主要用 read
            peekBasePosition = position;
        }

        // 简化 peek：直接从当前位置读
        int result = read(buffer, offset, length);

        // 恢复状态（peek 不改变读取位置）
        position = savedPos;
        peekPosition = savedPeekPos;
        bufferedBytes = savedBufBytes;
        bufferedPos = savedBufPos;

        return result;
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
        // 简化：逐字节跳过（TS 提取器主要按包读取，skip 量不大）
        int skipped = 0;
        byte[] temp = new byte[Math.min(length, 188)];
        while (skipped < length) {
            int toRead = Math.min(length - skipped, temp.length);
            int result = read(temp, 0, toRead);
            if (result == -1) break;
            skipped += result;
        }
        position += skipped;
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
        long underlyingLength = input.getLength();
        if (underlyingLength == -1) return -1;
        // M2TS 192 -> TS 188，长度按比例换算
        return underlyingLength * 188 / 192;
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
        int read = 0;
        while (read < length) {
            int result = peek(buffer, offset + read, length - read);
            if (result == -1) {
                if (allowEndOfInput && read == 0) return false;
                throw new IOException("End of input");
            }
            read += result;
        }
        return true;
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
        this.position = position;
        this.bufferedBytes = 0; // 重置缓冲
        throw e;
    }
}
