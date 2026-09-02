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
 * 一个基于内存 byte[] 的 {@link ExtractorInput}。
 *
 * 仅供 M2TS 桥接层使用：把"剥完 header 的纯 TS 字节流"包装成
 * {@link ExtractorInput}，供 {@link MyTsExtractor} 消费。
 *
 * 对齐新版 media3 (1.4+/1.5+) 的 ExtractorInput 接口：
 * - RESULT_END_OF_INPUT 已移至 Extractor 接口
 * - 新增 setRetryPosition(long, E)
 * - skipFully / advancePeekPosition 的 boolean allowEndOfInput 重载
 */
@UnstableApi
class ByteArrayExtractorInput implements ExtractorInput {

  private byte[] data;
  private int position;
  private int peekPosition;

  ByteArrayExtractorInput(byte[] data, int offset, int length) {
    this.data = new byte[length];
    System.arraycopy(data, offset, this.data, 0, length);
    this.position = 0;
    this.peekPosition = 0;
  }

  /** 从当前读取位置追加更多数据（用于跨块拼接）。 */
  void append(byte[] newData, int offset, int length) {
    byte[] merged = new byte[data.length + length];
    System.arraycopy(data, 0, merged, 0, data.length);
    System.arraycopy(newData, offset, merged, data.length, length);
    // 重新指向新数组；position/peekPosition 以"相对字节偏移"维持
    int oldLen = data.length;
    this.data = merged;
    // peekPosition 不应因 append 回退
    if (peekPosition < position) {
      peekPosition = position;
    }
    // 防止越界
    if (position > this.data.length) {
      position = this.data.length;
    }
    if (peekPosition > this.data.length) {
      peekPosition = this.data.length;
    }
    // 触发未使用警告消除（oldLen 仅供调试）
    assert oldLen >= 0;
  }

  /** 返回尚可读的字节数。 */
  int bytesAvailable() {
    return data.length - position;
  }

  // ---------- ExtractorInput 实现 ----------

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    int available = bytesAvailable();
    if (available == 0) {
      return Extractor.RESULT_END_OF_INPUT; // -1
    }
    int toRead = Math.min(length, available);
    System.arraycopy(data, position, buffer, offset, toRead);
    position += toRead;
    peekPosition = Math.max(peekPosition, position);
    return toRead;
  }

  @Override
  public boolean readFully(byte[] buffer, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    int read = 0;
    while (read < length) {
      int result = read(buffer, offset + read, length - read);
      if (result == Extractor.RESULT_END_OF_INPUT) {
        if (read == 0 && allowEndOfInput) {
          return false;
        }
        throw new EOFException();
      }
      read += result;
    }
    return true;
  }

  @Override
  public void readFully(byte[] buffer, int offset, int length) throws IOException {
    readFully(buffer, offset, length, false);
  }

  @Override
  public int skip(int length) throws IOException {
    int available = bytesAvailable();
    if (available == 0) {
      return Extractor.RESULT_END_OF_INPUT;
    }
    int toSkip = Math.min(length, available);
    position += toSkip;
    peekPosition = Math.max(peekPosition, position);
    return toSkip;
  }

  @Override
  public boolean skipFully(int length, boolean allowEndOfInput) throws IOException {
    long skipped = skip(length);
    if (skipped == Extractor.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return false;
      }
      throw new EOFException();
    }
    return true;
  }

  @Override
  public void skipFully(int length) throws IOException {
    skipFully(length, false);
  }

  @Override
  public int peek(byte[] buffer, int offset, int length) throws IOException {
    int available = data.length - peekPosition;
    if (available == 0) {
      return Extractor.RESULT_END_OF_INPUT;
    }
    int toPeek = Math.min(length, available);
    System.arraycopy(data, peekPosition, buffer, offset, toPeek);
    peekPosition += toPeek;
    return toPeek;
  }

  @Override
  public boolean peekFully(byte[] buffer, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    int peeked = 0;
    while (peeked < length) {
      int result = peek(buffer, offset + peeked, length - peeked);
      if (result == Extractor.RESULT_END_OF_INPUT) {
        if (peeked == 0 && allowEndOfInput) {
          return false;
        }
        throw new EOFException();
      }
      peeked += result;
    }
    return true;
  }

  @Override
  public void peekFully(byte[] buffer, int offset, int length) throws IOException {
    peekFully(buffer, offset, length, false);
  }

  @Override
  public long getLength() {
    return data.length;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getPeekPosition() {
    return peekPosition;
  }

  @Override
  public void resetPeekPosition() {
    peekPosition = position;
  }

  @Override
  public boolean isPeekConsumed() {
    return peekPosition == position;
  }

  @Override
  public void advancePeekPosition(int amount) throws IOException {
    advancePeekPosition(amount, false);
  }

  @Override
  public boolean advancePeekPosition(int amount, boolean allowEndOfInput) throws IOException {
    int newPeek = peekPosition + amount;
    if (newPeek > data.length) {
      if (allowEndOfInput) {
        peekPosition = data.length;
        return false;
      }
      throw new EOFException();
    }
    peekPosition = newPeek;
    return true;
  }

  /**
   * 读取失败时由框架调用，用于把重试位置重置为给定值，随后抛出传入的 Throwable。
   * 这里仅更新位置后直接抛出。
   */
  @Override
  public <E extends Throwable> void setRetryPosition(long newPosition, E e) throws E {
    if (newPosition >= 0 && newPosition <= data.length) {
      this.position = (int) newPosition;
      this.peekPosition = Math.max(this.peekPosition, this.position);
    }
    throw e;
  }
}
