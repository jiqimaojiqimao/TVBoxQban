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

import androidx.annotation.IntDef;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsPayloadReader;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An {@link Extractor} for M2TS (Blu-ray / AVCHD) container files, i.e. MPEG-TS with a 4-byte
 * header prepended to each 188-byte TS packet (192 bytes per packet).
 *
 * <p>This extractor acts as a thin adapter around {@link MyTsExtractor}: it locates the M2TS
 * alignment during {@link #sniff}, and on every {@link #read} it reads a block of M2TS data,
 * strips the per-packet headers, and forwards the resulting plain TS stream to a wrapped
 * {@link MyTsExtractor}. No TS parsing logic is duplicated or modified.
 */
@UnstableApi
public final class M2tsExtractor implements Extractor {

  /**
   * Factory for {@link M2tsExtractor} instances.
   */
  public static final ExtractorsFactory FACTORY = () new Extractor[] {new M2tsExtractor()};

  /**
   * Behave as defined in ISO/IEC 13818-1 (one PAT/PMT pass, then media).
   */
  public static final int MODE_MULTI_PMT = 0;
  /**
   * Assume only one PMT will be contained in the stream, even if more are declared by the PAT.
   */
  public static final int MODE_SINGLE_PMT = 1;
  /**
   * HLS-style mode: map {@link androidx.media3.extractor.TrackOutput}s by type instead of PID and
   * ignore continuity counters.
   */
  public static final int MODE_HLS = 2;

  public static final int DEFAULT_TIMESTAMP_SEARCH_BYTES =
      600 * M2tsUtil.TS_PACKET_SIZE;

  private final int mode;
  private final int timestampSearchBytes;
  private final DefaultTsPayloadReaderFactory payloadReaderFactory;

  private final MyTsExtractor tsExtractor;
  private final M2tsExtractorInput bridge = new M2tsExtractorInput();

  /** The current converted TS block being consumed by {@link #tsExtractor}. Null when exhausted. */
  private ExtractorInput currentTsBlock;

  public M2tsExtractor() {
    this(MODE_SINGLE_PMT);
  }

  public M2tsExtractor(@Mode int mode) {
    this(mode, new DefaultTsPayloadReaderFactory(), DEFAULT_TIMESTAMP_SEARCH_BYTES);
  }

  /**
   * @param mode                        One of {@link #MODE_MULTI_PMT}, {@link #MODE_SINGLE_PMT}
   *                                    and {@link #MODE_HLS}.
   * @param payloadReaderFactory        Factory for the set of payload readers used to parse
   *                                    elementary streams.
   * @param timestampSearchBytes        Number of bytes scanned to find a PCR timestamp when
   *                                    determining duration / seeking.
   */
  public M2tsExtractor(
      @Mode int mode,
      DefaultTsPayloadReaderFactory payloadReaderFactory,
      int timestampSearchBytes) {
    this.mode = mode;
    this.payloadReaderFactory = payloadReaderFactory;
    this.timestampSearchBytes = timestampSearchBytes;
    // MyTsExtractor operates on plain TS; we feed it header-stripped data via `bridge`.
    this.tsExtractor =
        new MyTsExtractor(
            /* mode= */ mode,
            /* timestampAdjuster= */ new TimestampAdjuster(0),
            /* payloadReaderFactory= */ (TsPayloadReader.Factory) payloadReaderFactory,
            /* timestampSearchBytes= */ timestampSearchBytes);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // Sniffing must not permanently consume bytes: use a bounded peek. Allow a few leading garbage
    // bytes before the first aligned M2TS packet (common for file headers / clip info).
    int peekLength = M2tsUtil.M2TS_PACKET_SIZE * (M2tsUtil.SNIFF_PACKET_COUNT + 4);
    byte[] buffer = new byte[peekLength];
    int peeked = 0;
    while (peeked < peekLength) {
      int read = input.peek(buffer, peeked, peekLength - peeked);
      if (read == ExtractorInput.RESULT_END_OF_INPUT) {
        break;
      }
      peeked += read;
    }
    int packetOffset = M2tsUtil.findM2tsPacketOffset(buffer, 0, peeked);
    if (packetOffset < 0) {
      return false;
    }
    // Align the underlying input to the start of the first M2TS packet so the first converted TS
    // block begins on a packet boundary.
    input.advancePeekPosition(packetOffset);
    return true;
  }

  @Override
  public void init(ExtractorOutput output) {
    tsExtractor.init(output);
  }

  @Override
  public void seek(long position, long timeUs) {
    tsExtractor.seek(position, timeUs);
    // Discard any buffered/converted block so the next read starts fresh from the new position.
    currentTsBlock = null;
    bridge.reset();
  }

  @Override
  public void release() {
    tsExtractor.release();
  }

  @Override
  public @Extractor.ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    // Drain any already-converted TS block first.
    if (currentTsBlock != null) {
      @Extractor.ReadResult
      int result = tsExtractor.read(currentTsBlock, seekPosition);
      if (result == Extractor.RESULT_CONTINUE || result == Extractor.RESULT_SEEK) {
        return result;
      }
      // The wrapped extractor finished with this block; fall through to convert the next one.
      currentTsBlock = null;
    }
    // Convert TS blocks until the wrapped extractor asks for more data (RESULT_CONTINUE/SEEK) or
    // the underlying input is exhausted.
    while (true) {
      currentTsBlock = bridge.fillAndConsume(input);
      if (currentTsBlock == null) {
        // No more M2TS data (and no buffered partial packet). Let the wrapped extractor finalize
        // duration / seek-map bookkeeping; propagate its result unchanged.
        return tsExtractor.read(emptyInput(), seekPosition);
      }
      @Extractor.ReadResult
      int result = tsExtractor.read(currentTsBlock, seekPosition);
      if (result == Extractor.RESULT_CONTINUE || result == Extractor.RESULT_SEEK) {
        return result;
      }
      // Block fully consumed; convert the next one.
      currentTsBlock = null;
    }
  }

  // Internal helpers.

  /**
   * Returns an empty TS-backed input. Used only after the real input is exhausted, so that
   * {@link MyTsExtractor} can finish any terminal duration / seek-map work. Backed by a zero-length
   * buffer; all reads return end-of-input.
   */
  private static ExtractorInput emptyInput() {
    return new ByteArrayExtractorInput(new byte[0], 0, /* absoluteStartPosition= */ 0);
  }

  /**
   * Modes for the extractor. One of {@link #MODE_MULTI_PMT}, {@link #MODE_SINGLE_PMT} or
   * {@link #MODE_HLS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_MULTI_PMT, MODE_SINGLE_PMT, MODE_HLS})
  public @interface Mode {}
}
