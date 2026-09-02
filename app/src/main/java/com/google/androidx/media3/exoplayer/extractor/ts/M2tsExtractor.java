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

import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.ts.TsExtractor;
import java.io.IOException;

public final class M2tsExtractor implements Extractor {

  private final TsExtractor tsExtractor;

  public M2tsExtractor(SubtitleParser.Factory subtitleParserFactory) {
    tsExtractor =
        new TsExtractor(
            TsExtractor.MODE_SINGLE_PMT,
            TsExtractor.FLAG_IGNORE_SECTION_CRC | TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            subtitleParserFactory,
            new TimestampAdjuster(0),
            new DefaultTsPayloadReaderFactory(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM),
            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
            TsExtractor.M2TS_PACKET_SIZE);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    int peekLength = TsExtractor.M2TS_PACKET_SIZE * (TsUtil.SNIFF_TS_PACKET_COUNT + 3);
    byte[] buffer = new byte[peekLength];
    input.peekFully(buffer, 0, peekLength);
    int syncOffset = TsUtil.tryToFindSyncBytePosition(buffer, 0, peekLength, TsExtractor.M2TS_PACKET_SIZE);
    if (syncOffset < peekLength && syncOffset >= TsExtractor.M2TS_PACKET_HEADER_SIZE) {
      input.skipFully(syncOffset - TsExtractor.M2TS_PACKET_HEADER_SIZE);
      return true;
    }
    return false;
  }

  @Override
  public void init(ExtractorOutput output) {
    tsExtractor.init(output);
  }

  @Override
  public void seek(long position, long timeUs) {
    tsExtractor.seek(position, timeUs);
    tsExtractor.enableNextVideoKeyFrame(timeUs);
  }

  @Override
  public void release() {
    tsExtractor.release();
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    return tsExtractor.read(input, seekPosition);
  }
}
