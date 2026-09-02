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
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;
import java.io.IOException;

/**
 * M2TS (.m2ts / 192 字节每包) 容器 Extractor。
 *
 * 实现方式：把底层 192 字节/包的 M2TS 流，通过 {@link M2tsExtractorInput}
 * 在读取时剥掉每包前 4 字节 header，转换为 188 字节/包的纯 TS 流，
 * 再交给 {@link MyTsExtractor} 完成实际解析。
 *
 * 用法（在 ExtractorsFactory 中返回即可）：
 * <pre>
 *   return () -> new Extractor[]{ new M2tsExtractor(...) };
 * </pre>
 */
@UnstableApi
public final class M2tsExtractor implements Extractor {

  private final MyTsExtractor tsExtractor;
  private M2tsExtractorInput m2tsInput;

  // ---------- 构造器 ----------

  /** 无参构造：使用默认配置。 */
  public M2tsExtractor() {
    this(MyTsExtractor.MODE_SINGLE_PMT,
        new DefaultTsPayloadReaderFactory(
            DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS),
        1024 * 1024);
  }

  /**
   * @param mode                    {@link MyTsExtractor#MODE_SINGLE_PMT} 或 {@code MODE_MULTI_PMT}
   * @param payloadReaderFactory     TS payload reader 工厂
   * @param timestampSearchBytes     时间戳搜索窗口大小
   */
  public M2tsExtractor(int mode,
                       DefaultTsPayloadReaderFactory payloadReaderFactory,
                       int timestampSearchBytes) {
    this.tsExtractor =
        new MyTsExtractor(mode, payloadReaderFactory, timestampSearchBytes);
  }

  /**
   * 兼容官方签名（含 SubtitleParser.Factory）的构造器。
   * 若你的 media3 版本较新、MyTsExtractor 需要 subtitleParserFactory，用这个。
   */
  public M2tsExtractor(int mode,
                       DefaultTsPayloadReaderFactory payloadReaderFactory,
                       SubtitleParser.Factory subtitleParserFactory,
                       int timestampSearchBytes) {
    // 若 MyTsExtractor 有对应 4 参构造器，请在此替换；默认回退到 3 参
    this(mode, payloadReaderFactory, timestampSearchBytes);
  }

  // ---------- Extractor 实现 ----------

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    M2tsExtractorInput m2ts = new M2tsExtractorInput(input);
    boolean filled = m2ts.fillAndConsume();
    if (!filled || m2ts.getStrippedInput() == null) {
      return false;
    }
    // 把"去头后的纯 TS 流"交给 MyTsExtractor 去嗅探
    return tsExtractor.sniff(m2ts.getStrippedInput());
  }

  @Override
  public void init(ExtractorOutput output) {
    tsExtractor.init(output);
  }

  @Override
  public void seek(long position, long timeUs) {
    tsExtractor.seek(position, timeUs);
    tsExtractor.enableNextVideoKeyFrame(timeUs);
    m2tsInput = null; // 重置桥接状态
  }

  @Override
  public void release() {
    tsExtractor.release();
  }

  @Override
  @Extractor.ReadResult
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    if (m2tsInput == null || m2tsInput.getStrippedInput() == null) {
      m2tsInput = new M2tsExtractorInput(input);
    }
    // 循环：当前块消费完后，填下一块继续喂给 TS 解析器
    while (true) {
      ByteArrayExtractorInput stripped = m2tsInput.getStrippedInput();
      if (stripped != null) {
        int result = tsExtractor.read(stripped, seekPosition);
        if (result != Extractor.RESULT_END_OF_INPUT) {
          return result;
        }
      }
      boolean hasMore = m2tsInput.fillAndConsume();
      if (!hasMore) {
        return Extractor.RESULT_END_OF_INPUT;
      }
    }
  }

  // ---------- 便捷 Factory ----------

  public static final ExtractorsFactory FACTORY =
      () -> new Extractor[]{new M2tsExtractor()};
}
