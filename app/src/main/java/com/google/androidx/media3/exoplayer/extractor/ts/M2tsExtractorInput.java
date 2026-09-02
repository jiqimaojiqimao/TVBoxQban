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
 * Bridge that turns an underlying M2TS (192-byte packets) {@link ExtractorInput} into a source of
 * plain TS (188-byte packets) consumed by {@link MyTsExtractor}.
 *
 * <p>Usage: each call to {@link #fillAndConsume(ExtractorInput)} reads a block of M2TS data from
 * the delegate, strips the 4-byte header from each packet, and returns a {@link ExtractorInput}
 * over the resulting TS bytes. The caller ( {@link M2tsExtractor}) drives {@link MyTsExtractor#read}
 * against that returned input until it is exhausted, then requests the next block.
 *
 * <p>This avoids modifying any of the TS parsing logic in {@link MyTsExtractor}.
 */
final class M2tsExtractorInput {

  private static final int BLOCK_M2TS_PACKETS = 256;
  /** Capacity of the M2TS-side read buffer (a multiple of the M2TS packet size). */
  static final int M2TS_BLOCK_SIZE = BLOCK_M2TS_PACKETS * M2tsUtil.M2TS_PACKET_SIZE; // 49152
  /** Capacity of the TS-side buffer after header stripping. */
  static final int TS_BLOCK_SIZE = BLOCK_M2TS_PACKETS * M2tsUtil.TS_PACKET_SIZE; // 48128

  private final byte[] m2tsBuffer = new byte[M2TS_BLOCK_SIZE];
  private final byte[] tsBuffer = new byte[TS_BLOCK_SIZE];

  /** Number of valid bytes sitting at the start of {@link #m2tsBuffer} (a leftover partial packet). */
  private int prefixLength;
  private long m2tsBytesConsumed;

  /** Resets all buffered state. Call after a seek. */
  void reset() {
    prefixLength = 0;
    m2tsBytesConsumed = 0;
  }

  /**
   * Reads up to one block of M2TS data from {@code input}, strips headers, and returns an
   * {@link ExtractorInput} over the converted TS bytes. Returns {@code null} when the underlying
   * input is exhausted (and no buffered data remains).
   */
  ExtractorInput fillAndConsume(ExtractorInput input) throws IOException {
    // Move any leftover partial packet from a previous block to the front.
    int totalM2tsRead = prefixLength;
    prefixLength = 0;

    while (totalM2tsRead < M2TS_BLOCK_SIZE) {
      int read = input.read(m2tsBuffer, totalM2tsRead, M2TS_BLOCK_SIZE - totalM2tsRead);
      if (read == ExtractorInput.RESULT_END_OF_INPUT) {
        break;
      }
      totalM2tsRead += read;
    }
    if (totalM2tsRead == 0) {
      return null;
    }

    // Only convert complete M2TS packets; carry any partial trailing packet forward as the next
    // block's prefix.
    int fullPackets = totalM2tsRead / M2TS_PACKET_SIZE();
    int tsLength = fullPackets * TS_PACKET_SIZE();
    for (int i = 0; i < fullPackets; i++) {
      int src = i * M2TS_PACKET_SIZE() + M2TS_HEADER_SIZE();
      System.arraycopy(m2tsBuffer, src, tsBuffer, i * TS_PACKET_SIZE(), TS_PACKET_SIZE());
    }

    int consumedM2ts = fullPackets * M2TS_PACKET_SIZE();
    int leftover = totalM2tsRead - consumedM2ts;
    if (leftover > 0) {
      System.arraycopy(m2tsBuffer, consumedM2ts, m2tsBuffer, 0, leftover);
      prefixLength = leftover;
    }
    long absoluteTsStart = (m2tsBytesConsumed / M2TS_PACKET_SIZE()) * TS_PACKET_SIZE();
    m2tsBytesConsumed += consumedM2ts;

    return new ByteArrayExtractorInput(tsBuffer, tsLength, absoluteTsStart);
  }

  /** @return the TS packet size (188). Exists for clarity alongside the M2TS size. */
  private static int TS_PACKET_SIZE() {
    return M2tsUtil.TS_PACKET_SIZE;
  }

  private static int M2TS_PACKET_SIZE() {
    return M2tsUtil.M2TS_PACKET_SIZE;
  }

  private static int M2TS_HEADER_SIZE() {
    return M2tsUtil.M2TS_HEADER_SIZE;
  }
}
