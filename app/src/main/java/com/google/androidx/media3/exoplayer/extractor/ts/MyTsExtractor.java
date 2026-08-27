package com.google.androidx.media3.exoplayer.extractor.ts;

import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.extractor.Extractor;
import androidx.media3.exoplayer.extractor.ExtractorInput;
import androidx.media3.exoplayer.extractor.ExtractorOutput;
import androidx.media3.exoplayer.extractor.ExtractorsFactory;
import androidx.media3.exoplayer.extractor.PositionHolder;
import androidx.media3.exoplayer.extractor.SeekMap;
import androidx.media3.exoplayer.extractor.ts.DefaultTsPayloadReaderFactory.Flags;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Extracts data from the MPEG-TS container format. */
@UnstableApi
public final class MyTsExtractor implements Extractor {

  private static final String TAG = "MyTsExtractor";
  private static final boolean DEBUG = true; // 改 false 关闭日志

  public static final ExtractorsFactory FACTORY = () -> new Extractor[] { new MyTsExtractor() };

  // --- 常量 ---
  public static final int WORKAROUND_NONE = 0;
  public static final int WORKAROUND_ALLOW_NON_IDR_KEYFRAMES = 1;
  public static final int WORKAROUND_IGNORE_PTS = 2;
  public static final int WORKAROUND_IGNORE_AAC_STREAM = 4;
  public static final int WORKAROUND_IGNORE_H264_STREAM = 8;
  public static final int WORKAROUND_IGNORE_DTS_STREAM = 16;

  public static final int MODE_SINGLE_PMT = 0;
  public static final int MODE_MULTI_PMT = 1;
  public static final int MODE_HLS = 2;

  private static final int BUFFER_SIZE = 8192; // 改大以容纳更多 192 字节块
  private static final int TS_PACKET_SIZE = 188;
  private static final int TS_SYNC_BYTE = 0x47;

  private static final int TS_SCRAMBLING_CTRL_MASK = 0xC0;
  private static final int TS_SCRAMBLING_CTRL_UNSCRAMBLED = 0x00;
  private static final int TS_ADAPTATION_FIELD_EXISTS_MASK = 0x20;
  private static final int TS_PAYLOAD_EXISTS_MASK = 0x10;
  private static final int TS_CONTINUITY_COUNTER_MASK = 0x0F;
  private static final int TS_PID_MASK = 0x1FFF00;
  private static final int TS_PID_SHIFT = 8;

  private static final long MAX_PTS = 0x1FFFFFFFFL; // 33 bits

  private static final int MLP_PID = 0x4FFF; // Blu-ray Audio MLT PID

  // --- 状态 ---
  private final int defaultWorkaroundFlags;
  private final int mode;
  private final int selectedAudioTrack;
  private final int selectedSubtitleTrack;
  private final ParsableByteArray tsPacketBuffer;
  private final ParsableBitArray tsScratch;
  private final ArrayList<TsPayloadReader> queuedPayloadReaders;
  private final SparseArray<TsPayloadReader> tsPayloadReaders;
  private final SparseBooleanArray trackPids;
  private final SparseBooleanArray continuityCounters;
  private final DefaultTsPayloadReaderFactory payloadReaderFactory;
  private final long timestampSearchRangeUs;
  private final int packetSize;
  private final int skipBytes; // m2ts 4-byte timestamp header

  private @MonotonicNonNull ExtractorOutput output;
  private long lastKnownTimestampUs;
  private long lastPcrTimeUs;
  private long firstPcrTimeUs;
  private long firstSampleTimestampUs;
  private boolean pcrTimeUsInitialized;
  private boolean tracksEnded;
  private boolean fillBufferCalled;
  private int remainingPmts;

  public MyTsExtractor() {
    this(MODE_SINGLE_PMT, 0, 0, 0, 0, 192, 4);
  }

  public MyTsExtractor(
      int mode,
      int defaultWorkaroundFlags,
      int selectedAudioTrack,
      int selectedSubtitleTrack,
      int selectedCameraId,
      int packetSize,
      int skipBytes) {
    this.mode = mode;
    this.defaultWorkaroundFlags = defaultWorkaroundFlags;
    this.selectedAudioTrack = selectedAudioTrack;
    this.selectedSubtitleTrack = selectedSubtitleTrack;
    this.packetSize = packetSize;
    this.skipBytes = skipBytes;
    tsPacketBuffer = new ParsableByteArray(BUFFER_SIZE);
    tsScratch = new ParsableBitArray(new byte[4]);
    queuedPayloadReaders = new ArrayList<>();
    tsPayloadReaders = new SparseArray<>();
    trackPids = new SparseBooleanArray();
    continuityCounters = new SparseBooleanArray();
    payloadReaderFactory =
        new DefaultTsPayloadReaderFactory(
            defaultWorkaroundFlags,
            selectedAudioTrack,
            selectedSubtitleTrack,
            selectedCameraId,
            Collections.emptyList(),
            false,
            false);
    timestampSearchRangeUs = 0;
    lastKnownTimestampUs = C.TIME_UNSET;
    lastPcrTimeUs = C.TIME_UNSET;
    firstPcrTimeUs = C.TIME_UNSET;
    firstSampleTimestampUs = C.TIME_UNSET;
    resetPayloadReaders();
    tsPacketBuffer.reset(new byte[BUFFER_SIZE], 0);
  }

  // --- Extractor 接口 ---
  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    if (DEBUG) Log.e(TAG, "🔍 sniff() pos=" + input.getPosition() + " length=" + input.getLength());

    byte[] buffer = new byte[packetSize];
    int startPos = -1;

    for (int searchPos = 0; searchPos < 512; searchPos++) {
      input.peekFully(buffer, 0, packetSize);
      for (int i = 0; i < packetSize; i++) {
        if (buffer[i] == TS_SYNC_BYTE) {
          if (startPos == -1) startPos = i;
          if (i == startPos) {
            if (searchPos > 0 && startPos == 4) {
              // m2ts: skip 4-byte timestamp header
              input.skipFully(startPos);
              if (DEBUG) Log.e(TAG, "✅ TS sync found! packetSize=" + packetSize + " skipBytes=" + skipBytes);
              return true;
            }
            if (startPos == 0) {
              if (DEBUG) Log.e(TAG, "✅ TS sync found! packetSize=" + packetSize + " skipBytes=0");
              return true;
            }
          }
        }
      }
      input.skipFully(1);
    }
    return false;
  }

  @Override
  public void init(ExtractorOutput output) {
    if (DEBUG) Log.e(TAG, "🔍 init() CALLED");
    this.output = output;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    if (DEBUG) Log.e(TAG, "🔍 read() packetSize=" + packetSize + " inputPos=" + input.getPosition());

    if (!fillBufferWithAtLeastOnePacket(input)) {
      return RESULT_END_OF_INPUT;
    }

    int syncPos = findEndOfFirstTsPacketInBuffer();
    if (syncPos < 0) {
      tsPacketBuffer.setPosition(tsPacketBuffer.limit());
      return RESULT_CONTINUE;
    }

    int tsStart = syncPos + skipBytes;
    int tsEnd = tsStart + TS_PACKET_SIZE;

    if (DEBUG) Log.e(TAG, "📍 SYNC FOUND: syncPos=" + syncPos + " tsStart=" + tsStart + " tsEnd=" + tsEnd);

    if (tsEnd > tsPacketBuffer.limit()) {
      // Not enough data for full TS packet, need more
      tsPacketBuffer.setPosition(syncPos);
      return RESULT_CONTINUE;
    }

    // Parse TS header
    tsPacketBuffer.setPosition(tsStart);
    int tsPacketHeader = tsPacketBuffer.readInt(); // 4 bytes
    int pid = (tsPacketHeader & TS_PID_MASK) >> TS_PID_SHIFT;
    boolean adaptationFieldExists = (tsPacketHeader & TS_ADAPTATION_FIELD_EXISTS_MASK) != 0;
    boolean payloadExists = (tsPacketHeader & TS_PAYLOAD_EXISTS_MASK) != 0;
    int packetHeaderFlags = 0;

    // Skip adaptation field
    if (adaptationFieldExists) {
      int adaptationFieldLength = tsPacketBuffer.readUnsignedByte();
      if (adaptationFieldLength > 0) {
        int adaptationFieldFlags = tsPacketBuffer.readUnsignedByte();
        packetHeaderFlags |= (adaptationFieldFlags & 0x40) != 0
            ? TsPayloadReader.FLAG_RANDOM_ACCESS_INDICATOR : 0;
        int skip = adaptationFieldLength - 1;
        // Safety: don't skip beyond tsEnd
        int maxSkip = tsEnd - tsPacketBuffer.getPosition();
        tsPacketBuffer.skipBytes(Math.min(skip, Math.max(0, maxSkip)));
      }
    }

    // Get payload reader
    TsPayloadReader payloadReader = payloadExists ? tsPayloadReaders.get(pid) : null;

    // Consume payload
    boolean wereTracksEnded = tracksEnded;
    if (shouldConsumePacketPayload(pid)) {
      if ((packetHeaderFlags & TsPayloadReader.FLAG_PAYLOAD_UNIT_START_INDICATOR) != 0
          || (tsPacketHeader & 0x400000) != 0) {
        packetHeaderFlags |= TsPayloadReader.FLAG_PAYLOAD_UNIT_START_INDICATOR;
        if (DEBUG && pid == 0) Log.e(TAG, "🎬 PUSI pid=" + pid + " cc=" + (tsPacketHeader & 0xF));
      }

      boolean isSectionReader = payloadReader instanceof SectionReader;
      int originalLimit = tsPacketBuffer.limit();

      if (!isSectionReader) {
        // Only restrict limit for PES readers (H.264/5, audio, etc.)
        tsPacketBuffer.setLimit(tsEnd);
      }

      try {
        payloadReader.consume(tsPacketBuffer, packetHeaderFlags);
      } finally {
        if (!isSectionReader) {
          tsPacketBuffer.setLimit(originalLimit);
        }
      }
    }

    if (!wereTracksEnded && tracksEnded) {
      maybeOutputSeekMap();
    }

    // Advance position past this packet
    tsPacketBuffer.setPosition(tsEnd);
    return RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {
    lastKnownTimestampUs = C.TIME_UNSET;
    lastPcrTimeUs = C.TIME_UNSET;
    firstPcrTimeUs = C.TIME_UNSET;
    firstSampleTimestampUs = C.TIME_UNSET;
    pcrTimeUsInitialized = false;
    tsPacketBuffer.reset(new byte[BUFFER_SIZE], 0);
    continuityCounters.clear();
    resetPayloadReaders();
  }

  @Override
  public void release() {}

  // --- 内部方法 ---
  private boolean fillBufferWithAtLeastOnePacket(ExtractorInput input) throws IOException {
    fillBufferCalled = true;
    int bytesLeft = tsPacketBuffer.bytesLeft();
    if (bytesLeft < packetSize) {
      // Compact: move unread data to front
      byte[] data = tsPacketBuffer.getData();
      int position = tsPacketBuffer.getPosition();
      int limit = tsPacketBuffer.limit();
      int unreadBytes = limit - position;
      if (unreadBytes > 0) {
        System.arraycopy(data, position, data, 0, unreadBytes);
      }
      tsPacketBuffer.reset(data, unreadBytes);

      // Read more data
      int maxRead = BUFFER_SIZE - unreadBytes;
      int readBytes = input.read(data, unreadBytes, maxRead);
      if (readBytes == C.RESULT_END_OF_INPUT) {
        return false;
      }
      tsPacketBuffer.setLimit(unreadBytes + readBytes);
    }
    return tsPacketBuffer.bytesLeft() >= packetSize;
  }

  private int findEndOfFirstTsPacketInBuffer() {
    int searchStart = tsPacketBuffer.getPosition();
    int limit = tsPacketBuffer.limit();
    for (int i = searchStart; i + packetSize < limit; i++) {
      if (tsPacketBuffer.getData()[i] == TS_SYNC_BYTE) {
        return i;
      }
    }
    return -1;
  }

  private boolean shouldConsumePacketPayload(int packetPid) {
    return mode == MODE_HLS
        || tracksEnded
        || !trackPids.get(packetPid, false);
  }

  @RequiresNonNull("output")
  private void maybeOutputSeekMap() {
    if (output == null) return;
    if (firstSampleTimestampUs != C.TIME_UNSET) {
      output.seekMap(
          new SeekMap.Unseekable(firstSampleTimestampUs, /* seekPoints= */ null));
    } else if (firstPcrTimeUs != C.TIME_UNSET) {
      output.seekMap(
          new SeekMap.Unseekable(firstPcrTimeUs, /* seekPoints= */ null));
    }
  }

  @EnsuresNonNull({"output"})
  private void resetPayloadReaders() {
    trackPids.clear();
    tsPayloadReaders.clear();
    remainingPmts = 1;
    tsPayloadReaders.put(TS_PAT_PID, new SectionReader(new PatReader()));
    trackPids.put(TS_PAT_PID, false);
  }

  private int streamTypeFromReader(TsPayloadReader reader) {
    if (reader instanceof PesReader) {
      return ((PesReader) reader).getStreamType();
    }
    return -1;
  }

  // --- PAT Reader ---
  private final class PatReader implements SectionPayloadReader {
    private final ParsableBitArray patScratch = new ParsableBitArray(new byte[4]);

    @Override
    public void init(
        TimestampAdjuster timestampAdjuster,
        ExtractorOutput extractorOutput,
        TsPayloadReader.TrackIdGenerator idGenerator) {}

    @Override
    public void consume(ParsableByteArray sectionData) {
      int tableId = sectionData.readUnsignedByte();
      if (tableId != 0x00) return; // Not PAT

      int sectionLengthAndFlags = sectionData.readUnsignedShort();
      int sectionLength = sectionLengthAndFlags & 0x0FFF;
      if (DEBUG) Log.e(TAG, "📊 PAT: sectionLength=" + sectionLength);

      int programsStart = sectionData.getPosition() + sectionLength - 4;
      while (sectionData.getPosition() < programsStart) {
        int programNumber = sectionData.readUnsignedShort();
        int pid = sectionData.readUnsignedShort() & 0x1FFF;

        if (DEBUG) Log.e(TAG, "📊 PAT: programNumber=" + programNumber + " pmtPid=" + pid);

        if (programNumber == 0) {
          // NIT
          continue;
        }

        if (mode == MODE_SINGLE_PMT && remainingPmts <= 0) {
          continue;
        }

        if (tsPayloadReaders.get(pid) == null) {
          TsPayloadReader pmtReader =
              new SectionReader(new PmtReader(pid, defaultWorkaroundFlags));
          tsPayloadReaders.put(pid, pmtReader);
          trackPids.put(pid, false);
          remainingPmts--;
        }
      }
    }
  }

  // --- PMT Reader ---
  private final class PmtReader implements SectionPayloadReader {
    private final int pmtPid;
    private final int workaroundFlags;
    private final ParsableBitArray pmtScratch = new ParsableBitArray(new byte[5]);

    public PmtReader(int pmtPid, int workaroundFlags) {
      this.pmtPid = pmtPid;
      this.workaroundFlags = workaroundFlags;
    }

    @Override
    public void init(
        TimestampAdjuster timestampAdjuster,
        ExtractorOutput extractorOutput,
        TsPayloadReader.TrackIdGenerator idGenerator) {}

    @Override
    public void consume(ParsableByteArray sectionData) {
      int tableId = sectionData.readUnsignedByte();
      if (tableId != 0x02) return; // Not PMT

      int sectionLengthAndFlags = sectionData.readUnsignedShort();
      int sectionLength = sectionLengthAndFlags & 0x0FFF;

      int pcrPid = sectionData.readUnsignedShort() & 0x1FFF;
      if (DEBUG) Log.e(TAG, "📊 PMT: pcrPid=" + pcrPid + " sectionLength=" + sectionLength);

      int infoEndPos = sectionData.getPosition() + (sectionData.readUnsignedShort() & 0x0FFF);
      if (infoEndPos > sectionData.limit()) return;
      sectionData.setPosition(infoEndPos);

      int remaining = (sectionData.getPosition() + sectionLength - 4) - sectionData.getPosition();
      if (DEBUG) Log.e(TAG, "📊 PMT: programInfoLength=" + (infoEndPos - (sectionData.getPosition() - 2)) + " remainingEsInfo=" + remaining);

      int trackCount = 0;
      while (remaining >= 5) {
        int streamType = sectionData.readUnsignedByte();
        int elementaryPid = sectionData.readUnsignedShort() & 0x1FFF;
        int esInfoLength = sectionData.readUnsignedShort() & 0x0FFF;
        int esInfoEnd = sectionData.getPosition() + esInfoLength;

        if (esInfoEnd > sectionData.limit()) {
          if (DEBUG) Log.e(TAG, "⚠️ PMT: esInfoLength OVERRUN, skipping");
          break;
        }

        // Skip ES info descriptors
        sectionData.setPosition(esInfoEnd);

        // Skip PGS subtitles
        if (streamType == 0x90) {
          if (DEBUG) Log.e(TAG, "⏭️ PMT: SKIP PGS subtitle pid=" + elementaryPid);
          remaining -= 5 + esInfoLength;
          continue;
        }

        // Map DTS-HD MA
        int effectiveStreamType = streamType;
        if (streamType == 0x82 || streamType == 0x83 || streamType == 0x84
            || streamType == 0x85 || streamType == 0x86) {
          effectiveStreamType = 0x82; // DTS-HD MA
        }

        TsPayloadReader reader = payloadReaderFactory.createPayloadReader(
            effectiveStreamType, new byte[0]);

        if (reader != null) {
          trackPids.put(elementaryPid, true);
          tsPayloadReaders.put(elementaryPid, reader);
          trackCount++;
          if (DEBUG) Log.e(TAG, "🎯 TRACK: streamType=0x" + Integer.toHexString(streamType)
              + " effective=0x" + Integer.toHexString(effectiveStreamType)
              + " pid=" + elementaryPid + " reader=" + reader.getClass().getSimpleName());
        } else {
          if (DEBUG) Log.e(TAG, "⚠️ PMT: No reader for streamType=0x"
              + Integer.toHexString(streamType) + " pid=" + elementaryPid);
        }

        remaining -= 5 + esInfoLength;
      }

      if (DEBUG) Log.e(TAG, "📊 PMT: registered " + trackCount + " tracks");

      // Remove PMT and PAT readers
      tsPayloadReaders.remove(pmtPid);
      tsPayloadReaders.remove(TS_PAT_PID);

      if (mode == MODE_SINGLE_PMT) {
        tracksEnded = true;
      }
    }
  }

  // --- 辅助 ---
  public static final int TS_PAT_PID = 0;

  public static long readPts(ParsableBitArray scratch, int offset) {
    byte[] data = scratch.getData();
    long pts = (data[offset] & 0x0E) << 29; // top 3 bits
    pts |= (data[offset + 1] & 0xFF) << 22; // next 8 bits
    pts |= (data[offset + 2] & 0xFE) << 14; // next 7 bits
    pts |= (data[offset + 3] & 0xFF) << 7; // next 8 bits
    pts |= (data[offset + 4] & 0xFE) >> 1; // bottom 7 bits
    return pts & MAX_PTS;
  }
}
