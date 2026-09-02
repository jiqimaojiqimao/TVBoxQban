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

/** M2TS (Blu-ray / AVCHD) container constants and helpers. */
public final class M2tsUtil {

  private M2tsUtil() {}

  /** M2TS packet size = standard TS packet (188) + 4-byte M2TS header. */
  public static final int M2TS_PACKET_SIZE = 192;

  /** Size of the extra header prepended to each TS packet in M2TS. */
  public static final int M2TS_HEADER_SIZE = 4;

  /** Standard TS packet size. */
  public static final int TS_PACKET_SIZE = 188;

  /** TS sync byte, located at {@code M2TS_HEADER_SIZE} bytes into each M2TS packet. */
  public static final int TS_SYNC_BYTE = 0x47;

  /** Number of consecutive sync-aligned packets required to consider the stream valid. */
  public static final int SNIFF_PACKET_COUNT = 5;

  /**
   * Strips the 4-byte M2TS header from each packet in {@code data}, converting a buffer of M2TS
   * packets into a buffer of plain TS packets (192 → 188 per packet).
   *
   * <p>Packets are processed in-place. The valid TS data is packed at the front of the array
   * starting at {@code offset}. Any partial trailing packet (shorter than a full M2TS packet) is
   * left untouched at the end and ignored.
   *
   * @param data   buffer containing M2TS packets.
   * @param offset start of the M2TS data in {@code data}.
   * @param length number of M2TS bytes available from {@code offset}.
   * @return        number of bytes of valid TS data written (always a multiple of {@link
   *                #TS_PACKET_SIZE}).
   */
  public static int stripHeaders(byte[] data, int offset, int length) {
    if (length < M2TS_PACKET_SIZE) {
      return 0;
    }
    int fullPackets = length / M2TS_PACKET_SIZE;
    for (int i = 0; i < fullPackets; i++) {
      int src = offset + i * M2TS_PACKET_SIZE + M2TS_HEADER_SIZE;
      int dst = offset + i * TS_PACKET_SIZE;
      System.arraycopy(data, src, data, dst, TS_PACKET_SIZE);
    }
    return fullPackets * TS_PACKET_SIZE;
  }

  /**
   * Returns the offset of the first byte of the first M2TS packet in {@code data}, or {@code -1}
   * if no valid M2TS alignment could be found.
   *
   * <p>A candidate is valid when, after skipping {@code M2TS_HEADER_SIZE} bytes, {@link
   * #SNIFF_PACKET_COUNT} consecutive TS sync bytes are found at {@link #M2TS_PACKET_SIZE} intervals.
   */
  public static int findM2tsPacketOffset(byte[] data, int offset, int limit) {
    for (int candidate = offset; candidate < limit; candidate++) {
      boolean valid = true;
      for (int p = 0; p < SNIFF_PACKET_COUNT; p++) {
        int syncIndex = candidate + p * M2TS_PACKET_SIZE + M2TS_HEADER_SIZE;
        if (syncIndex >= limit || data[syncIndex] != TS_SYNC_BYTE) {
          valid = false;
          break;
        }
      }
      if (valid) {
        return candidate;
      }
    }
    return -1;
  }
}
