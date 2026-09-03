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

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * 包装 byte[] 的 ExtractorInput 实现，供 M2tsExtractor 使用。
 */
@UnstableApi
final class ByteArrayExtractorInput implements ExtractorInput {

    private static final int RESULT_END_OF_INPUT = Extractor.RESULT_END_OF_INPUT;

    private final byte[] data;
    private int position;
    private int peekPosition;

    ByteArrayExtractorInput(byte[] data) {
        this.data = data;
        this.position = 0;
        this.peekPosition = 0;
    }

    // ---------- peek ----------

    @Override
    public int peek(byte[] buffer, int offset, int length) throws IOException {
        int available = data.length - peekPosition;
        if (available == 0) return RESULT_END_OF_INPUT;
        int toRead = Math.min(length, available);
        System.arraycopy(data, peekPosition, buffer, offset, toRead);
        peekPosition += toRead;
        return toRead;
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
            if (result == RESULT_END_OF_INPUT) {
                if (allowEndOfInput && read == 0) return false;
                throw new IOException("End of input");
            }
            if (result == 0) throw new InterruptedIOException();
            read += result;
        }
        return true;
    }

    // ---------- read ----------

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        int available = data.length - position;
        if (available == 0) return RESULT_END_OF_INPUT;
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
    public boolean readFully(byte[] buffer, int offset, int length, boolean allowEndOfInput) throws IOException {
        int read = 0;
        while (read < length) {
            int result = read(buffer, offset + read, length - read);
            if (result == RESULT_END_OF_INPUT) {
                if (allowEndOfInput && read == 0) return false;
                throw new IOException("End of input");
            }
            if (result == 0) throw new InterruptedIOException();
            read += result;
        }
        return true;
    }

    // ---------- skip ----------

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
    public boolean skipFully(int length, boolean allowEndOfInput) throws IOException {
        long skipped = skip(length);
        if (skipped < length) {
            if (allowEndOfInput) return false;
            throw new IOException("End of input");
        }
        return true;
    }

    // ---------- advancePeekPosition ----------

    @Override
    public void advancePeekPosition(int length) throws IOException {
        if (peekPosition + length > data.length) throw new IOException("End of input");
        peekPosition += length;
    }

    @Override
    public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException {
        if (peekPosition + length > data.length) {
            if (allowEndOfInput) return false;
            throw new IOException("End of input");
        }
        peekPosition += length;
        return true;
    }

    // ---------- 其余 ----------

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
