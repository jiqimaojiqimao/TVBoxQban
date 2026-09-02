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

import androidx.media3.common.util.Assertions;
import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;

/**
 * An {@link ExtractorInput} backed by a single in-memory byte array. Used to feed TS data (with
 * M2TS headers already stripped) into {@link MyTsExtractor} without modifying its internals.
 */
final class ByteArrayExtractorInput implements ExtractorInput {

  private final byte[] data;
  private int position;
  private final long absoluteStartPosition;

  ByteArrayExtractorInput(byte[] data, int limit, long absoluteStartPosition) {
    this.data = Assertions.checkNotNull(data);
    this.position = 0;
    this.absoluteStartPosition = absoluteStartPosition;
    Assertions.checkState(limit <= data.length);
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    int toCopy = Math.min(available(), length);
    if (toCopy == 0) {
      return RESULT_END_OF_INPUT;
    }
    System.arraycopy(data, position, buffer, offset, toCopy);
    position += toCopy;
    return toCopy;
  }

  @Override
  public boolean readFully(byte[] buffer, int offset, int length, boolean allowEndOfInput) {
    if (available() >= length) {
      System.arraycopy(data, position, buffer, offset, length);
      position += length;
      return true;
    }
    if (allowEndOfInput) {
      return false;
    }
    return false;
  }

  @Override
  public void readFully(byte[] buffer, int offset, int length) throws IOException {
    if (available() < length) {
      throw new IOException("End of input while reading " + length + " bytes.");
    }
    System.arraycopy(data, position, buffer, offset, length);
    position += length;
  }

  @Override
  public int skip(int length) {
    int skipped = Math.min(available(), length);
    position += skipped;
    return skipped == 0 && length > 0 ? RESULT_END_OF_INPUT : skipped;
  }

  @Override
  public boolean skipFully(long length, boolean allowEndOfInput) {
    if (available() < length) {
      return false;
    }
    position += length;
    return true;
  }

  @Override
  public void skipFully(long length) {
    Assertions.checkState(skipFully(length, /* allowEndOfInput= */ false));
  }

  @Override
  public int peek(byte[] buffer, int offset, int length) {
    int toCopy = Math.min(available(), length);
    if (toCopy == 0) {
      return RESULT_END_OF_INPUT;
    }
    System.arraycopy(data, position, buffer, offset, toCopy);
    return toCopy;
  }

  @Override
  public boolean peekFully(byte[] buffer, int offset, int length, boolean allowEndOfInput) {
    if (available() < length) {
      return false;
    }
    System.arraycopy(data, position, buffer, offset, length);
    return true;
  }

  @Override
  public void peekFully(byte[] buffer, int offset, int length) {
    Assertions.checkState(peekFully(buffer, offset, length, /* allowEndOfInput= */ false));
  }

  @Override
  public long getLength() {
    return data.length;
  }

  @Override
  public long getPosition() {
    return absoluteStartPosition + position;
  }

  @Override
  public void resetPeekPosition() {
    // No separate peek cursor; peek operations are copies.
  }

  @Override
  public boolean isPeekConsumed() {
    return true;
  }

  @Override
  public void advancePeekPosition(int amount) {
    position += amount;
  }

  int available() {
    return data.length - position;
  }
}
