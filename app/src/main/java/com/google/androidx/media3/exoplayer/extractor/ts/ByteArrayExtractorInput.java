package com.google.androidx.media3.exoplayer.extractor.ts;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;
import java.io.InterruptedIOException;

@UnstableApi
final class ByteArrayExtractorInput implements ExtractorInput {

    private final byte[] data;
    private int position;
    private int peekPosition;

    ByteArrayExtractorInput(byte[] data) {
        this.data = data;
        this.position = 0;
        this.peekPosition = 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        int available = data.length - position;
        if (available == 0) return Extractor.RESULT_END_OF_INPUT;
        int toRead = Math.min(length, available);
        System.arraycopy(data, position, buffer, offset, toRead);
        position += toRead;
        peekPosition = position;
        return toRead;
    }

    @Override
    public void readFully(byte[] buffer, int offset, int length) throws IOException {
        readFully(buffer, offset, length, false);
    }

    @Override
    public int readFully(byte[] buffer, int offset, int length, boolean allowEndOfInput) throws IOException {
        int read = 0;
        while (read < length) {
            int result = read(buffer, offset + read, length - read);
            if (result == Extractor.RESULT_END_OF_INPUT) {
                if (allowEndOfInput && read == 0) return Extractor.RESULT_END_OF_INPUT;
                throw new IOException("End of input");
            }
            if (result == 0) throw new InterruptedIOException();
            read += result;
        }
        return read;
    }

    @Override
    public int skip(int length) throws IOException {
        int available = data.length - position;
        int toSkip = Math.min(length, available);
        position += toSkip;
        peekPosition = position;
        return toSkip;
    }

    @Override
    public void skipFully(int length) throws IOException {
        skipFully(length, false);
    }

    @Override
    public void skipFully(int length, boolean allowEndOfInput) throws IOException {
        long skipped = skip(length);
        if (skipped < length && !allowEndOfInput) throw new IOException("End of input");
    }

    @Override
    public void peekFully(byte[] buffer, int offset, int length) throws IOException {
        peekFully(buffer, offset, length, false);
    }

    @Override
    public void peekFully(byte[] buffer, int offset, int length, boolean allowEndOfInput) throws IOException {
        int available = data.length - peekPosition;
        if (available < length && !allowEndOfInput) throw new IOException("End of input");
        int toRead = Math.min(length, available);
        System.arraycopy(data, peekPosition, buffer, offset, toRead);
        peekPosition += toRead;
    }

    @Override
    public boolean advancePeekPosition(int length) throws IOException {
        return advancePeekPosition(length, false);
    }

    @Override
    public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException {
        if (peekPosition + length > data.length) {
            if (!allowEndOfInput) throw new IOException("End of input");
            return false;
        }
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
        return data.length;
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
        this.position = (int) position;
        this.peekPosition = (int) position;
        throw e;
    }
}