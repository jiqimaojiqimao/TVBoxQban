/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.google.androidx.media3.exoplayer.extractor.ts;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.PositionHolder;
import java.io.EOFException;
import java.io.IOException;

/**
 * 把底层"192 字节/包 M2TS 流"桥接成"188 字节/包纯 TS 流"的 {@link ExtractorInput}。
 *
 * 每次 fillAndConsume() 从 delegate 读一块数据，剥掉每包的 4 字节 header，
 * 再包装成 {@link ByteArrayExtractorInput} 交给 {@link MyTsExtractor}。
 *
 * 对齐新版 media3 (1.4+/1.5+) ExtractorInput 接口。
 */
@UnstableApi
final class M2tsExtractorInput implements ExtractorInput {

  private final ExtractorInput delegate;
  private ByteArrayExtractorInput strippedInput;
  private byte[] bridgeBuffer;
  private int bridgeBufferSize;

  /** 跨块残留的、不构成完整 M2TS 包的部分（最多 M2TS_PACKET_SIZE-1 字节）。 */
  private byte[] prefix;
  private int prefixLength;

  M2tsExtractorInput(ExtractorInput delegate) {
    this.delegate = delegate;
    this.bridgeBuffer = new byte[M2tsUtil.M2TS_PACKET_SIZE * 8];
    this.prefix = new byte[M2tsUtil.M2TS_PACKET_SIZE];
    this.prefixLength = 0;
  }

  /**
   * 从底层填充一块数据，剥离 M2TS header，输出可供 TS 解析的纯字节流。
   * 返回 false 表示已经到达输入末尾。
   */
  boolean fillAndConsume() throws IOException {
    // 1) 先把上次残留的前缀搬进 buffer 开头
    int offset = prefixLength;
    if (offset > 0) {
      System.arraycopy(prefix, 0, bridgeBuffer, 0, prefixLength);
    }

    // 2) 从 delegate 读更多数据
    int read = delegate.read(bridgeBuffer, offset, bridgeBuffer.length - offset);
    if (read == Extractor.RESULT_END_OF_INPUT) {
      if (offset == 0) {
        strippedInput = null;
        return false;
      }
      // 还有残留前缀数据，当作最后一块处理
      read = 0;
    }
    int total = offset + read;

    // 3) 剥离 header：每 M2TS_PACKET_SIZE 跳过前 M2TS_HEADER_SIZE 字节
    //    stripHeaders(data, offset, length) 处理 [offset, offset+length) 内的完整包
    int packets = total / M2tsUtil.M2TS_PACKET_SIZE;
    int consumed = packets * M2tsUtil.M2TS_PACKET_SIZE;

    int strippedLen = 0;
    if (packets > 0) {
      strippedLen = M2tsUtil.stripHeaders(bridgeBuffer, 0, consumed);
    }

    // 4) 处理剩余不足一个包的部分，暂存为前缀留给下一次
    int remainder = total - consumed;
    if (remainder > 0) {
      System.arraycopy(bridgeBuffer, consumed, prefix, 0, remainder);
    }
    prefixLength = remainder;

    if (strippedLen == 0 && remainder == 0 && read == 0) {
      strippedInput = null;
      return false;
    }

    // 5) 包装成纯 TS 的 ExtractorInput
    strippedInput = new ByteArrayExtractorInput(bridgeBuffer, 0, strippedLen);
    return true;
  }

  /** 供 read() 循环消费当前已剥离的纯 TS 数据。 */
  ByteArrayExtractorInput getStrippedInput() {
    return strippedInput;
  }

  // ---------- ExtractorInput 委托 / 透传 ----------

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (strippedInput != null) {
      int result = strippedInput.read(buffer, offset, length);
      if (result != Extractor.RESULT_END_OF_INPUT) {
        return result;
      }
    }
    return Extractor.RESULT_END_OF_INPUT;
  }

  @Override
  public boolean readFully(byte[] buffer, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    if (strippedInput == null) {
      if (allowEndOfInput) {
        return false;
      }
      throw new EOFException();
    }
    return strippedInput.readFully(buffer, offset, length, allowEndOfInput);
  }

  @Override
  public void readFully(byte[] buffer, int offset, int length) throws IOException {
    readFully(buffer, offset, length, false);
  }

  @Override
  public int skip(int length) throws IOException {
    if (strippedInput == null) {
      return Extractor.RESULT_END_OF_INPUT;
    }
    return strippedInput.skip(length);
  }

  @Override
  public boolean skipFully(int length, boolean allowEndOfInput) throws IOException {
    if (strippedInput == null) {
      if (allowEndOfInput) {
        return false;
      }
      throw new EOFException();
    }
    return strippedInput.skipFully(length, allowEndOfInput);
  }

  @Override
  public void skipFully(int length) throws IOException {
    skipFully(length, false);
  }

  @Override
  public int peek(byte[] buffer, int offset, int length) throws IOException {
    if (strippedInput == null) {
      return Extractor.RESULT_END_OF_INPUT;
    }
    return strippedInput.peek(buffer, offset, length);
  }

  @Override
  public boolean peekFully(byte[] buffer, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    if (strippedInput == null) {
      if (allowEndOfInput) {
        return false;
      }
      throw new EOFException();
    }
    return strippedInput.peekFully(buffer, offset, length, allowEndOfInput);
  }

  @Override
  public void peekFully(byte[] buffer, int offset, int length) throws IOException {
    peekFully(buffer, offset, length, false);
  }

  @Override
  public long getLength() {
    return delegate.getLength();
  }

  @Override
  public long getPosition() {
    return delegate.getPosition() - prefixLength;
  }

  @Override
  public long getPeekPosition() {
    return strippedInput != null ? strippedInput.getPeekPosition() : getPosition();
  }

  @Override
  public void resetPeekPosition() {
    if (strippedInput != null) {
      strippedInput.resetPeekPosition();
    }
  }

  @Override
  public boolean isPeekConsumed() {
    return strippedInput == null || strippedInput.isPeekConsumed();
  }

  @Override
  public void advancePeekPosition(int amount) throws IOException {
    advancePeekPosition(amount, false);
  }

  @Override
  public boolean advancePeekPosition(int amount, boolean allowEndOfInput) throws IOException {
    if (strippedInput == null) {
      if (allowEndOfInput) {
        return false;
      }
      throw new EOFException();
    }
    return strippedInput.advancePeekPosition(amount, allowEndOfInput);
  }

  @Override
  public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
    delegate.setRetryPosition(position, e);
  }
}
