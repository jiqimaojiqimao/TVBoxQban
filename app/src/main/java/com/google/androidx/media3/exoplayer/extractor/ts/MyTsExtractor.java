/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static androidx.media3.extractor.ts.TsPayloadReader.EsInfo.AUDIO_TYPE_UNDEFINED;
import static androidx.media3.extractor.ts.TsPayloadReader.FLAG_PAYLOAD_UNIT_START_INDICATOR;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.SectionPayloadReader;
import androidx.media3.extractor.ts.SectionReader;
import androidx.media3.extractor.ts.TsPayloadReader;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("ALL")
@UnstableApi
/** Extracts data from the MPEG-2 TS container format. */
public final class MyTsExtractor implements Extractor {

    public static final ExtractorsFactory FACTORY = () -> new Extractor[]{new MyTsExtractor()};
    public static final int MODE_MULTI_PMT = 0;
    public static final int MODE_SINGLE_PMT = 1;
    public static final int MODE_HLS = 2;
    public static final int TS_PACKET_SIZE = 188;
    public static final int DEFAULT_TIMESTAMP_SEARCH_BYTES = 600 * TS_PACKET_SIZE;
    public static final int TS_STREAM_TYPE_MPA = 0x03;
    public static final int TS_STREAM_TYPE_MPA_LSF = 0x04;
    public static final int TS_STREAM_TYPE_AAC_ADTS = 0x0F;
    public static final int TS_STREAM_TYPE_AAC_LATM = 0x11;
    public static final int TS_STREAM_TYPE_AC3 = 0x81;
    public static final int TS_STREAM_TYPE_DTS = 0x8A;
    public static final int TS_STREAM_TYPE_HDMV_DTS = 0x82;
    public static final int TS_STREAM_TYPE_E_AC3 = 0x87;
    public static final int TS_STREAM_TYPE_AC4 = 0xAC;
    public static final int TS_STREAM_TYPE_H262 = 0x02;
    public static final int TS_STREAM_TYPE_H263 = 0x10;
    public static final int TS_STREAM_TYPE_H264 = 0x1B;
    public static final int TS_STREAM_TYPE_H265 = 0x24;
    public static final int TS_STREAM_TYPE_ID3 = 0x15;
    public static final int TS_STREAM_TYPE_SPLICE_INFO = 0x86;
    public static final int TS_STREAM_TYPE_DVBSUBS = 0x59;
    public static final int TS_STREAM_TYPE_DC2_H262 = 0x80;
    public static final int TS_STREAM_TYPE_AIT = 0x101;
    public static final int TS_SYNC_BYTE = 0x47;
    private static final int TS_PAT_PID = 0;
    private static final int MAX_PID_PLUS_ONE = 0x2000;
    private static final long AC3_FORMAT_IDENTIFIER = 0x41432d33;
    private static final long E_AC3_FORMAT_IDENTIFIER = 0x45414333;
    private static final long AC4_FORMAT_IDENTIFIER = 0x41432d34;
    private static final long HEVC_FORMAT_IDENTIFIER = 0x48455643;
    private static final int BUFFER_SIZE = TS_PACKET_SIZE * 50;
    private static final int SNIFF_TS_PACKET_COUNT = 5;

    private int packetSize = TS_PACKET_SIZE;
    @Mode
    private final int mode;
    private final int timestampSearchBytes;
    private final List<TimestampAdjuster> timestampAdjusters;
    private final ParsableByteArray tsPacketBuffer;
    private final SparseIntArray continuityCounters;
    private final TsPayloadReader.Factory payloadReaderFactory;
    private final SparseArray<TsPayloadReader> tsPayloadReaders;
    private final SparseBooleanArray trackIds;
    private final SparseBooleanArray trackPids;
    private final TsDurationReader durationReader;
    private TsBinarySearchSeeker tsBinarySearchSeeker;
    private ExtractorOutput output;
    private int remainingPmts;
    private boolean tracksEnded;
    private boolean hasOutputSeekMap;
    private boolean pendingSeekToStart;
    @Nullable
    private TsPayloadReader id3Reader;
    private int bytesSinceLastSync;
    private int pcrPid;

    // region Constructors
    public MyTsExtractor() { this(0); }

    public MyTsExtractor(@TsPayloadReader.Flags int defaultTsPayloadReaderFlags) {
        this(MODE_SINGLE_PMT, defaultTsPayloadReaderFlags, DEFAULT_TIMESTAMP_SEARCH_BYTES);
    }

    public MyTsExtractor(@Mode int mode, @TsPayloadReader.Flags int defaultTsPayloadReaderFlags, int timestampSearchBytes) {
        this(mode, new TimestampAdjuster(0), new DefaultTsPayloadReaderFactory(defaultTsPayloadReaderFlags), timestampSearchBytes);
    }

    public MyTsExtractor(@Mode int mode, TimestampAdjuster timestampAdjuster, TsPayloadReader.Factory payloadReaderFactory) {
        this(mode, timestampAdjuster, payloadReaderFactory, DEFAULT_TIMESTAMP_SEARCH_BYTES);
    }

    public MyTsExtractor(@Mode int mode, TimestampAdjuster timestampAdjuster, TsPayloadReader.Factory payloadReaderFactory, int timestampSearchBytes) {
        this.payloadReaderFactory = Assertions.checkNotNull(payloadReaderFactory);
        this.timestampSearchBytes = timestampSearchBytes;
        this.mode = mode;
        if (mode == MODE_SINGLE_PMT || mode == MODE_HLS) {
            timestampAdjusters = Collections.singletonList(timestampAdjuster);
        } else {
            timestampAdjusters = new ArrayList<>();
            timestampAdjusters.add(timestampAdjuster);
        }
        tsPacketBuffer = new ParsableByteArray(new byte[BUFFER_SIZE], 0);
        trackIds = new SparseBooleanArray();
        trackPids = new SparseBooleanArray();
        tsPayloadReaders = new SparseArray<>();
        continuityCounters = new SparseIntArray();
        durationReader = new TsDurationReader(timestampSearchBytes);
        output = ExtractorOutput.PLACEHOLDER;
        pcrPid = -1;
        resetPayloadReaders();
    }
    // endregion

    // region Extractor
    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        Log.e("MyTsExtractor", "🔍 sniff() pos=" + input.getPosition() + " length=" + input.getLength());
        int searchSize = Math.min(timestampSearchBytes, 1024 * 1024);
        if (tsPacketBuffer.getData().length < searchSize) {
            tsPacketBuffer.reset(new byte[searchSize], 0);
        }
        byte[] buffer = tsPacketBuffer.getData();
        int bytesPeeked = input.peek(buffer, 0, searchSize);

        int[] packetSizes = {188, 192};
        for (int ps : packetSizes) {
            if (bytesPeeked < ps * 5) continue;
            for (int startPos = 0; startPos < ps; startPos++) {
                boolean sync = true;
                for (int i = 0; i < 5; i++) {
                    int offset = startPos + i * ps;
                    if (offset >= bytesPeeked || buffer[offset] != TS_SYNC_BYTE) {
                        sync = false;
                        break;
                    }
                }
                if (sync) {
                    Log.e("MyTsExtractor", "✅ TS sync found! packetSize=" + ps + " skipBytes=" + startPos);
                    this.packetSize = ps;
                    input.skipFully(startPos);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void init(ExtractorOutput output) {
        Log.e("MyTsExtractor", "🔍 init() CALLED");
        this.output = output;
    }

    @Override
    public void seek(long position, long timeUs) {
        Assertions.checkState(mode != MODE_HLS);
        for (int i = 0; i < timestampAdjusters.size(); i++) {
            TimestampAdjuster ta = timestampAdjusters.get(i);
            boolean reset = ta.getTimestampOffsetUs() == C.TIME_UNSET;
            if (!reset) {
                long firstTs = ta.getFirstSampleTimestampUs();
                reset = firstTs != C.TIME_UNSET && firstTs != 0 && firstTs != timeUs;
            }
            if (reset) ta.reset(timeUs);
        }
        if (timeUs != 0 && tsBinarySearchSeeker != null) {
            tsBinarySearchSeeker.setSeekTargetUs(timeUs);
        }
        tsPacketBuffer.reset(0);
        continuityCounters.clear();
        for (int i = 0; i < tsPayloadReaders.size(); i++) {
            tsPayloadReaders.valueAt(i).seek();
        }
        bytesSinceLastSync = 0;
    }

    @Override
    public void release() {}

    @Override
    @ReadResult
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        Log.e("MyTsExtractor_xuameng", "🔍 read() packetSize=" + packetSize + " inputPos=" + input.getPosition());

        long inputLength = input.getLength();

        if (!fillBufferWithAtLeastOnePacket(input)) {
            return RESULT_END_OF_INPUT;
        }

        int syncPos = findEndOfFirstTsPacketInBuffer();
        if (syncPos < 0) {
            long currentPos = input.getPosition();
            long remainder = currentPos % packetSize;
            if (remainder != 0) {
                int skipBytes = (int) (packetSize - remainder);
                Log.e("MyTsExtractor", "🔧 SEEK align: " + currentPos + " → " + (currentPos + skipBytes) + " skip=" + skipBytes);
                input.skipFully(skipBytes);
            }
            tsPacketBuffer.reset(0);
            return RESULT_CONTINUE;
        }

        int endOfPacket = syncPos + packetSize;
        if (endOfPacket > tsPacketBuffer.limit()) {
            tsPacketBuffer.setPosition(tsPacketBuffer.limit());
            return RESULT_CONTINUE;
        }

        tsPacketBuffer.setPosition(syncPos);

        @TsPayloadReader.Flags int packetHeaderFlags = 0;
        int tsPacketHeader = tsPacketBuffer.readInt();
        if ((tsPacketHeader & 0x800000) != 0) {
            tsPacketBuffer.setPosition(endOfPacket);
            return RESULT_CONTINUE;
        }
        packetHeaderFlags |= (tsPacketHeader & 0x400000) != 0 ? FLAG_PAYLOAD_UNIT_START_INDICATOR : 0;
        int pid = (tsPacketHeader & 0x1FFF00) >> 8;
        boolean adaptationFieldExists = (tsPacketHeader & 0x20) != 0;
        boolean payloadExists = (tsPacketHeader & 0x10) != 0;

        TsPayloadReader payloadReader = payloadExists ? tsPayloadReaders.get(pid) : null;
        if (payloadReader == null) {
            tsPacketBuffer.setPosition(endOfPacket);
            return RESULT_CONTINUE;
        }

        // Continuity check
        if (mode != MODE_HLS) {
            int continuityCounter = tsPacketHeader & 0xF;
            int previousCounter = continuityCounters.get(pid, continuityCounter - 1);
            continuityCounters.put(pid, continuityCounter);
        }

        // Skip adaptation field
        if (adaptationFieldExists) {
            int adaptationFieldLength = tsPacketBuffer.readUnsignedByte();
            int adaptationFieldFlags = tsPacketBuffer.readUnsignedByte();
            packetHeaderFlags |= (adaptationFieldFlags & 0x40) != 0 ? TsPayloadReader.FLAG_RANDOM_ACCESS_INDICATOR : 0;
            tsPacketBuffer.skipBytes(adaptationFieldLength - 1);
        }

        // Consume payload
        boolean wereTracksEnded = tracksEnded;
        if (shouldConsumePacketPayload(pid)) {
            if ((packetHeaderFlags & FLAG_PAYLOAD_UNIT_START_INDICATOR) != 0) {
                Log.e("MyTs6", "🎬 PUSI pid=" + pid + " cc=" + (tsPacketHeader & 0xF));
            }
            tsPacketBuffer.setLimit(endOfPacket);
            payloadReader.consume(tsPacketBuffer, packetHeaderFlags);
        }

        if (mode != MODE_HLS && !wereTracksEnded && tracksEnded && inputLength != C.LENGTH_UNSET) {
            pendingSeekToStart = true;
        }

        tsPacketBuffer.setPosition(endOfPacket);
        return RESULT_CONTINUE;
    }
    // endregion

    // region Internals
    private void maybeOutputSeekMap(long inputLength) {
        hasOutputSeekMap = true;
    }

private boolean fillBufferWithAtLeastOnePacket(ExtractorInput input) throws IOException {
        byte[] data = tsPacketBuffer.getData();

        // 如果剩余空间不够一个包，且已经读了很多数据，把未消费的数据移到前面
        if (tsPacketBuffer.bytesLeft() < packetSize) {
            if (tsPacketBuffer.getPosition() > 0) {
                // 需要压缩：把剩余数据移到开头
                int bytesLeft = tsPacketBuffer.bytesLeft();
                if (bytesLeft > 0) {
                    System.arraycopy(data, tsPacketBuffer.getPosition(), data, 0, bytesLeft);
                }
                tsPacketBuffer.reset(data, bytesLeft);
                // ★ 重置后 position=0，limit=bytesLeft，后续 findEndOfFirstTsPacketInBuffer 从 0 搜
            }
            // 现在 position=0，读新数据追加到 limit 后面
            int limit = tsPacketBuffer.limit();
            int maxRead = BUFFER_SIZE - limit;
            if (maxRead <= 0) {
                // buffer 满了但 bytesLeft >= packetSize 应该已经满足了，不应该到这里
                return true;
            }
            int read = input.read(data, limit, maxRead);
            if (read == C.RESULT_END_OF_INPUT) {
                return false;
            }
            tsPacketBuffer.setLimit(limit + read);
        }

        return true;
    }

    private int findEndOfFirstTsPacketInBuffer() {
        int searchStart = tsPacketBuffer.getPosition();
        int limit = tsPacketBuffer.limit();
        byte[] data = tsPacketBuffer.getData();

        for (int i = searchStart; i + packetSize * 2 <= limit; i++) {
            if (data[i] == (byte) TS_SYNC_BYTE && data[i + packetSize] == (byte) TS_SYNC_BYTE) {
                Log.e("MyTsExtractor", "📍 SYNC FOUND: syncPos=" + i + " endOfPacket=" + (i + packetSize));
                return i;
            }
        }

        Log.e("MyTsExtractor", "⚠️ No 0x47, searchStart=" + searchStart + " limit=" + limit);
        return -1;
    }

    private boolean shouldConsumePacketPayload(int packetPid) {
        return mode == MODE_HLS
                || tracksEnded
                || !trackPids.get(packetPid, false);
    }

    private void resetPayloadReaders() {
        trackIds.clear();
        tsPayloadReaders.clear();
        SparseArray<TsPayloadReader> initial = payloadReaderFactory.createInitialPayloadReaders();
        for (int i = 0; i < initial.size(); i++) {
            tsPayloadReaders.put(initial.keyAt(i), initial.valueAt(i));
        }
        tsPayloadReaders.put(TS_PAT_PID, new SectionReader(new PatReader()));
        id3Reader = null;
    }
    // endregion

    // region PAT / PMT readers
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MODE_MULTI_PMT, MODE_SINGLE_PMT, MODE_HLS})
    public @interface Mode {}

    private class PatReader implements SectionPayloadReader {
        private final ParsableBitArray patScratch = new ParsableBitArray(new byte[4]);

        @Override
        public void init(TimestampAdjuster ta, ExtractorOutput eo, TsPayloadReader.TrackIdGenerator idGen) {}

        @Override
        public void consume(ParsableByteArray sectionData) {
            if (sectionData.readUnsignedByte() != 0x00) return;
            if ((sectionData.readUnsignedByte() & 0x80) == 0) return;
            sectionData.skipBytes(6);
            int programCount = sectionData.bytesLeft() / 4;
            for (int i = 0; i < programCount; i++) {
                sectionData.readBytes(patScratch, 4);
                int programNumber = patScratch.readBits(16);
                patScratch.skipBits(3);
                if (programNumber == 0) {
                    patScratch.skipBits(13);
                } else {
                    int pid = patScratch.readBits(13);
                    if (tsPayloadReaders.get(pid) == null) {
                        tsPayloadReaders.put(pid, new SectionReader(new PmtReader(pid)));
                        remainingPmts++;
                    }
                }
            }
            if (mode != MODE_HLS) {
                tsPayloadReaders.remove(TS_PAT_PID);
            }
        }
    }

    private class PmtReader implements SectionPayloadReader {
        private static final int TS_PMT_DESC_REGISTRATION = 0x05;
        private static final int TS_PMT_DESC_ISO639_LANG = 0x0A;
        private static final int TS_PMT_DESC_AC3 = 0x6A;
        private static final int TS_PMT_DESC_AIT = 0x6F;
        private static final int TS_PMT_DESC_EAC3 = 0x7A;
        private static final int TS_PMT_DESC_DTS = 0x7B;
        private static final int TS_PMT_DESC_DVB_EXT = 0x7F;
        private static final int TS_PMT_DESC_DVBSUBS = 0x59;
        private static final int TS_PMT_DESC_DVB_EXT_AC4 = 0x15;

        private final ParsableBitArray pmtScratch = new ParsableBitArray(new byte[5]);
        private final SparseArray<TsPayloadReader> trackIdToReaderScratch = new SparseArray<>();
        private final SparseIntArray trackIdToPidScratch = new SparseIntArray();
        private final int pid;

        public PmtReader(int pid) { this.pid = pid; }

        @Override
        public void init(TimestampAdjuster ta, ExtractorOutput eo, TsPayloadReader.TrackIdGenerator idGen) {}

        @Override
        public void consume(ParsableByteArray sectionData) {
            if (sectionData.readUnsignedByte() != 0x02) return;
            TimestampAdjuster timestampAdjuster;
            if (mode == MODE_SINGLE_PMT || mode == MODE_HLS || remainingPmts == 1) {
                timestampAdjuster = timestampAdjusters.get(0);
            } else {
                timestampAdjuster = new TimestampAdjuster(timestampAdjusters.get(0).getFirstSampleTimestampUs());
                timestampAdjusters.add(timestampAdjuster);
            }
            int secondHeaderByte = sectionData.readUnsignedByte();
            if ((secondHeaderByte & 0x80) == 0) return;
            sectionData.skipBytes(1);
            int programNumber = sectionData.readUnsignedShort();
            sectionData.skipBytes(1);  // ← 只跳 last_section_number
            sectionData.readBytes(pmtScratch, 2);
            pmtScratch.skipBits(3);
            pcrPid = pmtScratch.readBits(13);
            sectionData.readBytes(pmtScratch, 2);
            pmtScratch.skipBits(4);
            int programInfoLength = pmtScratch.readBits(12);
            sectionData.skipBytes(programInfoLength);

            if (mode == MODE_HLS && id3Reader == null) {
                TsPayloadReader.EsInfo id3EsInfo = new TsPayloadReader.EsInfo(TS_STREAM_TYPE_ID3, null, AUDIO_TYPE_UNDEFINED, null, Util.EMPTY_BYTE_ARRAY);
                id3Reader = payloadReaderFactory.createPayloadReader(TS_STREAM_TYPE_ID3, id3EsInfo);
                if (id3Reader != null) {
                    id3Reader.init(timestampAdjuster, output, new TsPayloadReader.TrackIdGenerator(programNumber, TS_STREAM_TYPE_ID3, MAX_PID_PLUS_ONE));
                }
            }

            trackIdToReaderScratch.clear();
            trackIdToPidScratch.clear();
            int remainingEntriesLength = sectionData.bytesLeft();
            while (remainingEntriesLength > 0) {
                sectionData.readBytes(pmtScratch, 5);
                int streamType = pmtScratch.readBits(8);
                pmtScratch.skipBits(3);
                int elementaryPid = pmtScratch.readBits(13);
                pmtScratch.skipBits(4);
                int esInfoLength = pmtScratch.readBits(12);
                // ★ 原始 streamType + PID，在 descriptor 覆盖之前打
                Log.e("MyTsExtractor", "🔍 ES raw: streamType=0x" + Integer.toHexString(streamType)
                        + " elementaryPid=" + elementaryPid + " esInfoLength=" + esInfoLength);
                TsPayloadReader.EsInfo esInfo = readEsInfo(sectionData, esInfoLength);
                if (streamType == 0x06 || streamType == 0x05) streamType = esInfo.streamType;
                remainingEntriesLength -= esInfoLength + 5;
                int trackId = mode == MODE_HLS ? streamType : elementaryPid;
                if (trackIds.get(trackId)) continue;
                // PGS 字幕 → 跳过（ExoPlayer 不支持）
                if (streamType == 0x90) {
                    continue;
                }
                // DTS-HD MA (0x86) → 映射成 DTS (0x8A) 让工厂建 DtsReader
                int mappedStreamType = (streamType == 0x86) ? TS_STREAM_TYPE_DTS : streamType;
                TsPayloadReader reader = mode == MODE_HLS && mappedStreamType == TS_STREAM_TYPE_ID3
                        ? id3Reader
                        : payloadReaderFactory.createPayloadReader(mappedStreamType, esInfo);
                if (mode != MODE_HLS || elementaryPid < trackIdToPidScratch.get(trackId, MAX_PID_PLUS_ONE)) {
                    trackIdToPidScratch.put(trackId, elementaryPid);
                    trackIdToReaderScratch.put(trackId, reader);
                }
            }

            for (int i = 0; i < trackIdToPidScratch.size(); i++) {
                int trackId = trackIdToPidScratch.keyAt(i);
                int trackPid = trackIdToPidScratch.valueAt(i);
                trackIds.put(trackId, true);
                trackPids.put(trackPid, true);
                TsPayloadReader reader = trackIdToReaderScratch.valueAt(i);
                if (reader != null) {
                    Log.e("MyTsExtractor", "🎯 TRACK: pid=" + trackPid
                            + " reader=" + reader.getClass().getSimpleName());
                    if (reader != id3Reader) {
                        reader.init(timestampAdjuster, output, new TsPayloadReader.TrackIdGenerator(programNumber, trackId, MAX_PID_PLUS_ONE));
                    }
                    tsPayloadReaders.put(trackPid, reader);
                } else {
                    Log.e("MyTsExtractor", "🎯 TRACK: pid=" + trackPid + " reader=NULL");
                }
            }

            if (mode == MODE_HLS) {
                if (!tracksEnded) {
                    output.endTracks();
                    remainingPmts = 0;
                    tracksEnded = true;
                }
            } else {
                tsPayloadReaders.remove(pid);
                remainingPmts = mode == MODE_SINGLE_PMT ? 0 : remainingPmts - 1;
                if (remainingPmts == 0) {
                    output.endTracks();
                    tracksEnded = true;
                }
            }
        }

        private TsPayloadReader.EsInfo readEsInfo(ParsableByteArray data, int length) {
            int descriptorsStartPosition = data.getPosition();
            int descriptorsEndPosition = descriptorsStartPosition + length;
            int streamType = -1;
            @TsPayloadReader.EsInfo.AudioType int audioType = AUDIO_TYPE_UNDEFINED;
            String language = null;
            List<TsPayloadReader.DvbSubtitleInfo> dvbSubtitleInfos = null;
            while (data.getPosition() < descriptorsEndPosition) {
                int descriptorTag = data.readUnsignedByte();
                int descriptorLength = data.readUnsignedByte();
                int positionOfNextDescriptor = data.getPosition() + descriptorLength;
                if (positionOfNextDescriptor > descriptorsEndPosition) break;
                if (descriptorTag == TS_PMT_DESC_REGISTRATION) {
                    long formatId = data.readUnsignedInt();
                    if (formatId == AC3_FORMAT_IDENTIFIER) streamType = TS_STREAM_TYPE_AC3;
                    else if (formatId == E_AC3_FORMAT_IDENTIFIER) streamType = TS_STREAM_TYPE_E_AC3;
                    else if (formatId == AC4_FORMAT_IDENTIFIER) streamType = TS_STREAM_TYPE_AC4;
                    else if (formatId == HEVC_FORMAT_IDENTIFIER) streamType = TS_STREAM_TYPE_H265;
                } else if (descriptorTag == TS_PMT_DESC_AC3) {
                    streamType = TS_STREAM_TYPE_AC3;
                } else if (descriptorTag == TS_PMT_DESC_EAC3) {
                    streamType = TS_STREAM_TYPE_E_AC3;
                } else if (descriptorTag == TS_PMT_DESC_DVB_EXT) {
                    if (data.readUnsignedByte() == TS_PMT_DESC_DVB_EXT_AC4) streamType = TS_STREAM_TYPE_AC4;
                } else if (descriptorTag == TS_PMT_DESC_DTS) {
                    streamType = TS_STREAM_TYPE_DTS;
                } else if (descriptorTag == TS_PMT_DESC_ISO639_LANG) {
                    language = data.readString(3).trim();
                    audioType = data.readUnsignedByte();
                } else if (descriptorTag == TS_PMT_DESC_DVBSUBS) {
                    streamType = TS_STREAM_TYPE_DVBSUBS;
                    dvbSubtitleInfos = new ArrayList<>();
                    while (data.getPosition() < positionOfNextDescriptor) {
                        String dvbLanguage = data.readString(3).trim();
                        int dvbSubtitlingType = data.readUnsignedByte();
                        byte[] initData = new byte[4];
                        data.readBytes(initData, 0, 4);
                        dvbSubtitleInfos.add(new TsPayloadReader.DvbSubtitleInfo(dvbLanguage, dvbSubtitlingType, initData));
                    }
                } else if (descriptorTag == TS_PMT_DESC_AIT) {
                    streamType = TS_STREAM_TYPE_AIT;
                }
int skip = positionOfNextDescriptor - data.getPosition();
if (skip > 0 && skip <= data.bytesLeft()) {
    data.skipBytes(skip);
} else if (skip > data.bytesLeft()) {
    // descriptor 声明的长度超出 buffer，直接跳到末尾
    data.skipBytes(data.bytesLeft());
}
            }
            data.setPosition(descriptorsEndPosition);
            return new TsPayloadReader.EsInfo(streamType, language, audioType, dvbSubtitleInfos,
                    Arrays.copyOfRange(data.getData(), descriptorsStartPosition, descriptorsEndPosition));
        }
    }
    // endregion
}
