package com.github.tvbox.osc.subtitle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.content.res.Resources;
import android.util.Log; //xuameng记录字幕播放日志
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.lang.Math;

/**
 * xuameng
 * LRC歌词显示控件
 * 支持卡拉OK效果的歌词同步显示
 * 新增平滑滚动功能
 * 新增：未获取到进度或进度小于0.1秒时不显示歌词
 * 新增：初始位不显示滚动动画
 * 新增：不是相邻行不不显示滚动动画
 * 新增：播放进度到当前行的上行或多行不显示滚动动画
 * 20260817 新增：高亮进度平滑滤波 解决最版exoplayer对mp3的升级后字幕卡顿问题
 * 202601122 新增：逐字卡拉OK格式歌词，无歌词时加长提示文字
 * 2026xxxx 新增：ExoPlayer 逐字歌词平滑时间源，防止逐字卡顿
 */
public class LrcView extends View {

    /**
     * xuameng 新增：逐字信息（用于逐字卡拉OK格式）
     */
    private static class WordInfo {
        String text;       // 字文本，如 "演"
        long start;        // 在本行内的起始偏移（ms），如 0
        long end;          // 在本行内的结束偏移（ms），如 95
        float width;       // 字的宽度（绘制时计算）
    }

    /**
     * LRC歌词行数据结构
     */
    private static class LrcLine {
        long time; // 时间戳（毫秒）
        String text; // 歌词文本
        float width; // 文本宽度（用于绘制）
        List<WordInfo> words; // xuameng 新增：逐字信息，null表示普通行（非逐字格式）
    }

    private List<LrcLine> mLrcLines = new ArrayList<>();
    private Paint mNormalPaint, mHighlightPaint;
    private int mCurrentLine = 0;
    private long mCurrentPosition = 0;

    // 平滑滚动相关变量
    private float mScrollOffset = 0f; // 当前滚动偏移量（行数）
    private ValueAnimator mScrollAnimator; // 滚动动画
    private int mScrollDuration = 300; // 滚动动画时长（毫秒）

    // 新增：控制是否显示歌词的标志
    private boolean mShouldShowLyrics = false;
    private static final long MIN_POSITION_TO_SHOW = 100; // 0.1秒，单位：毫秒
    // 标记是否正在初始定位
    private boolean mIsInitialPositioning = true;
    // 最大滚动距离（行数）
    private static final int MAX_SCROLL_DISTANCE = 1;
    // 新增：高亮进度平滑滤波 =====
    private float mSmoothedProgress = 0f;

    // xuameng ExoPlayer 逐字歌词平滑时间源（老方案）
    private long mSmoothedPosition = 0L;
    private long mLastUpdatePositionTime = 0L;
    private boolean mExoMode = true; // 外部设置：是否是 ExoPlayer


    public LrcView(Context context) {
        super(context);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);  // 修复这里
        init();
    }

    /**
     * xuameng
     * 设置是否为 ExoPlayer 模式
     * true = ExoPlayer：开启逐字平滑自驱
     * false = IJK / VLC / 本地：按原来逻辑
     */
    public void setExoPlayerMode(boolean exo) {
        mExoMode = exo;
    }

    /**
     * 初始化画笔
     */
    private void init() {
        mNormalPaint = new Paint();
        mNormalPaint.setAntiAlias(true);
        mNormalPaint.setTextSize(36);
        mNormalPaint.setColor(Color.WHITE);
        mNormalPaint.setShadowLayer(3, 1, 1, Color.BLACK);

        mHighlightPaint = new Paint();
        mHighlightPaint.setAntiAlias(true);
        mHighlightPaint.setTextSize(36);
        mHighlightPaint.setColor(Color.YELLOW);
        mHighlightPaint.setShadowLayer(3, 1, 1, Color.BLACK);
        mHighlightPaint.setFakeBoldText(true);
    }

    /**
     * 设置滚动动画时长
     *
     * @param duration 动画时长（毫秒）
     */
    public void setScrollDuration(int duration) {
        mScrollDuration = duration;
    }

    /**
     * 设置普通文本大小
     *
     * @param textSize 文本大小
     */
    public void setNormalTextSize(float textSize) {
        // 将 sp 转换为 px 以便与字幕的字体大小一致
        float pxSize = spToPx(getContext(), textSize);
        mNormalPaint.setTextSize(pxSize);
        // 重新计算所有歌词行的宽度
        recalculateLineWidths();
        invalidate();
    }

    /**
     * 设置高亮文本大小
     *
     * @param textSize 文本大小
     */
    public void setHighlightTextSize(float textSize) {
        // 将 sp 转换为 px 以便与字幕的字体大小一致
        float pxSize = spToPx(getContext(), textSize);
        mHighlightPaint.setTextSize(pxSize);
        // 重新计算所有歌词行的宽度
        recalculateLineWidths();
        invalidate();
    }

    /**
     * 重新计算所有歌词行的宽度
     */
    private void recalculateLineWidths() {
        for (LrcLine line : mLrcLines) {
            line.width = mNormalPaint.measureText(line.text);
            // xuameng 新增：同步计算逐字宽度
            if (line.words != null) {
                for (WordInfo w : line.words) {
                    w.width = mNormalPaint.measureText(w.text);
                }
            }
        }
    }

    /**
     * 设置普通文本颜色
     *
     * @param color 颜色值
     */
    public void setNormalColor(int color) {
        mNormalPaint.setColor(color);
        invalidate();
    }

    /**
     * 设置高亮文本颜色
     *
     * @param color 颜色值
     */
    public void setHighlightColor(int color) {
        mHighlightPaint.setColor(color);
        invalidate();
    }

    /**
     * 解析LRC格式歌词
     *
     * @param lrcContent LRC格式歌词内容
     */
    public void setLrcText(String lrcContent) {
        // xuameng debug
        Log.d("LrcView_xuameng", "lrcContent length=" + (lrcContent == null ? "NULL" : lrcContent.length()) + 
              " first200=" + (lrcContent == null ? "null" : lrcContent.substring(0, Math.min(200, lrcContent.length()))));
        mLrcLines.clear();

        // xuameng 新增：逐字格式正则
        // 正则1：逐字格式行首 [数字,数字] 或 [数字.数字]，如 [0,1064] 或 [0.570]
        // xuameng 修改：去掉非贪婪 ?，确保完整匹配毫秒部分
        Pattern patternSec = Pattern.compile("\\[(\\d+)[,.](\\d+)\\]");
        // 正则2：逐字标签 <偏移,持续,标志> 如 <0,95,0>
        Pattern patternWord = Pattern.compile("<(\\d+),(\\d+),\\d*>([^<]*)");

        // 原有：普通行级LRC正则
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{1,3})\\]");

        String[] lines = lrcContent.split("\\r?\\n");

        for (String line : lines) {
            // 跳过空行
            if (line.trim().isEmpty()) {
                continue;
            }

            // ===== xuameng 新增：尝试逐字格式解析 =====
            Matcher secMatcher = patternSec.matcher(line);
            if (secMatcher.find()) {
                // xuameng 修改：酷狗逐字格式 [开始毫秒, 持续毫秒]
                long t1 = Long.parseLong(secMatcher.group(1));
                long t2 = Long.parseLong(secMatcher.group(2));
                
                // xuameng debug
                Log.d("LrcView_xuameng", "逐字行原始: t1=" + t1 + " t2=" + t2 + " line=" + line.substring(0, Math.min(60, line.length())));
                
                long lineTime;
                if (t1 > 1000 || t2 < 1000) {
                    // t1 很大说明是绝对毫秒；t2 很小说明 t1 是秒
                    // 实际上酷狗格式是 [开始ms, 持续ms]，直接取 t1
                    lineTime = t1;
                } else {
                    // 兼容 [秒.毫秒] 格式：0,570 → 0 * 1000+570
                    lineTime = t1 * 1000 + t2;
                }

                // xuameng debug
                Log.d("LrcView_xuameng", "逐字行 lineTime=" + lineTime + "ms (" + lineTime/1000 + "s)");

                String afterBracket = line.substring(secMatcher.end());
                Matcher wordMatcher = patternWord.matcher(afterBracket);

                List<WordInfo> words = new ArrayList<>();
                StringBuilder fullText = new StringBuilder();

                while (wordMatcher.find()) {
                    long wordStart = Long.parseLong(wordMatcher.group(1));
                    long wordDur = Long.parseLong(wordMatcher.group(2));
                    String wordText = wordMatcher.group(3);

                    if (wordText.isEmpty()) continue;

                    WordInfo wi = new WordInfo();
                    wi.text = wordText;
                    wi.start = wordStart;
                    wi.end = wordStart + wordDur;
                    words.add(wi);
                    fullText.append(wordText);
                }

                if (!words.isEmpty()) {
                    LrcLine lrcLine = new LrcLine();
                    lrcLine.time = lineTime;
                    lrcLine.text = fullText.toString();
                    lrcLine.width = mNormalPaint.measureText(fullText.toString());
                    lrcLine.words = words;
                    for (WordInfo w : words) {
                        w.width = mNormalPaint.measureText(w.text);
                    }
                    mLrcLines.add(lrcLine);
                    continue;
                }
            }

            // ===== 原有：普通行级 LRC 解析（完全不动） =====
            Matcher matcher = pattern.matcher(line);
            List<Long> times = new ArrayList<>();
            String text = "";
            int lastEnd = 0;

            // 查找所有时间标签
            while (matcher.find()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                // 处理毫秒部分
                String msStr = matcher.group(3);
                long ms;
                if (msStr.length() == 2) {
                    // 2位数字，按百分秒处理
                    ms = Integer.parseInt(msStr) * 10L; // 百分秒转毫秒
                } else if (msStr.length() == 1) {
                    ms = Integer.parseInt(msStr) * 100L; // 如.1 -> 100毫秒
                } else {
                    ms = Integer.parseInt(msStr); // 3位数字，直接作为毫秒
                }
                times.add((min * 60 + sec) * 1000L + ms);
                lastEnd = matcher.end();
            }

            // 提取歌词文本（去除时间标签后的内容）
            text = line.substring(lastEnd).trim();

            // 只有当文本非空时才添加到歌词列表
            if (!text.isEmpty()) {
                for (Long time : times) {
                    LrcLine lrcLine = new LrcLine();
                    lrcLine.time = time;
                    lrcLine.text = text;
                    lrcLine.width = mNormalPaint.measureText(text);
                    lrcLine.words = null; // 普通行，无逐字信息
                    mLrcLines.add(lrcLine);
                }
            }
            // 注意：这里我们跳过了纯时间标签行（如[00:13.760]），因为它们没有歌词内容
        }

        // 按时间排序
        Collections.sort(mLrcLines, (a, b) -> Long.compare(a.time, b.time));

        // 移除重复的歌词行
        removeDuplicateLines();

        // xuameng debug：打印前5行时间
        for (int i = 0; i < Math.min(5, mLrcLines.size()); i++) {
            Log.d("LrcView_xuameng", "排序后 line[" + i + "] time=" + mLrcLines.get(i).time + " text=" + mLrcLines.get(i).text.substring(0, Math.min(15, mLrcLines.get(i).text.length())));
        }

        // 重置所有状态
        mShouldShowLyrics = false;
        mCurrentLine = 0; // 总是从第0行开始
        mScrollOffset = 0f;
        mSmoothedProgress = 0f;
        mCurrentPosition = 0;
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            mScrollAnimator.cancel();
        }

        // xuameng ExoPlayer：重置平滑时间源
        mSmoothedPosition = 0L;
        mLastUpdatePositionTime = 0L;

        Log.d("LrcView_xuameng", "setLrcText done, mLrcLines.size()=" + mLrcLines.size());
        invalidate(); // 立即重绘
    }

    /**
     * 移除重复的歌词行
     */
    private void removeDuplicateLines() {
        if (mLrcLines.size() <= 1) return;

        List<LrcLine> uniqueLines = new ArrayList<>();
        for (LrcLine line : mLrcLines) {
            boolean isDuplicate = false;
            for (LrcLine existingLine : uniqueLines) {
                if (existingLine.time == line.time && existingLine.text.equals(line.text)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                uniqueLines.add(line);
            }
        }
        mLrcLines = uniqueLines;
    }

    /**
     * 平滑滚动到指定行
     *
     * @param targetLine 目标行索引
     */
    private void smoothScrollTo(int targetLine) {
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            return; // 无需滚动
        }

        // 计算滚动距离（行数差）
        int lineDiff = targetLine - mCurrentLine;
        if (lineDiff == 0) {
            return; // 无需滚动
        }
        mSmoothedProgress = 0f;
        // 设置动画
        mScrollAnimator = ValueAnimator.ofFloat(0f, (float) lineDiff);
        mScrollAnimator.setDuration(mScrollDuration);
        mScrollAnimator.setInterpolator(new AccelerateDecelerateInterpolator());  //加速减速插值器
    //    mScrollAnimator.setInterpolator(new LinearInterpolator()); // 改为线性插值器
    //    mScrollAnimator.setInterpolator(new DecelerateInterpolator(1.5f));  //减速插值器

        mScrollAnimator.addUpdateListener(animation -> {
            mScrollOffset = (float) animation.getAnimatedValue();
            invalidate();
        });

        mScrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束后更新当前行
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
                mSmoothedProgress = 0f; 
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // 动画取消时也更新当前行
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
                mSmoothedProgress = 0f; 
            }

        });

        mScrollAnimator.start();
    }

    /**
     * 更新播放进度
     *
     * @param position 当前播放时间（毫秒）
     */
    public void updateTime(long position) {
        // xuameng debug
        Log.d("LrcView_xuameng", "updateTime pos=" + position + " shouldShow=" + mShouldShowLyrics + " currentLine=" + mCurrentLine + " lines=" + (mLrcLines == null ? 0 : mLrcLines.size()));

        // 检查是否达到最小显示位置
        if (position < MIN_POSITION_TO_SHOW) {
            // 进度小于1秒，不显示歌词，但保持在第一行
            mShouldShowLyrics = false;
            mCurrentLine = 0; // 确保强制重置到第一行
            mScrollOffset = 0f; // 重置滚动偏移
            mSmoothedProgress = 0f;
            invalidate();
            return;
        }

        // 达到最小显示位置，开始显示歌词
        if (!mShouldShowLyrics) {
            mShouldShowLyrics = true;
        }

        // xuameng ExoPlayer 特殊处理：位置低通跟随，避免逐字卡顿
        if (mExoMode) {
            long now = System.currentTimeMillis();
            if (mLastUpdatePositionTime == 0) {
                mSmoothedPosition = position;
            } else {
                long diff = Math.abs(position - mSmoothedPosition);
                if (diff > 250) {
                    // seek / 缓冲跳变，直接跟
                    mSmoothedPosition = position;
                } else {
                    // 80% 旧值 + 20% 新值，专门治 ExoPlayer 抖动
                    mSmoothedPosition = (long) (mSmoothedPosition * 0.8f + position * 0.2f);
                }
            }
            mLastUpdatePositionTime = now;
            mCurrentPosition = mSmoothedPosition;
        } else {
            mCurrentPosition = position;
        }

        // 关键：没歌词时只更新状态，不访问 mLrcLines
        if (mLrcLines == null || mLrcLines.isEmpty()) {
            invalidate(); // 触发 onDraw 去画"暂无歌词"
            return;
        }

        // xuameng 修改：反向查找最后一个 time <= position 的行
        int targetLine = 0;

        // 如果当前时间比第一行还早，保持在第一行
        if (position < mLrcLines.get(0).time) {
            targetLine = 0;
        } else {
            // 否则找到合适的时间点
            for (int i = mLrcLines.size() - 1; i >= 0; i--) {
                if (position >= mLrcLines.get(i).time) {
                    targetLine = i;
                    break;
                }
            }
        }

        // xuameng debug
        Log.d("LrcView_xuameng", "updateTime targetLine=" + targetLine + " lineTime=" + mLrcLines.get(targetLine).time);

        // 关键修改：处理初始定位
        if (mIsInitialPositioning) {
            // 初始定位阶段，直接跳转到目标行，不执行滚动动画
            mCurrentLine = targetLine;
            mScrollOffset = 0f;
            // 初始化新行的进度，避免高亮从头开始
            long newLineTime = mLrcLines.get(targetLine).time;
            long nextTime = (targetLine + 1 < mLrcLines.size())
                    ? mLrcLines.get(targetLine + 1).time
                    : newLineTime + 5000;
            long duration = nextTime - newLineTime;
            if (duration > 0 && mCurrentPosition >= newLineTime) {
                float initProgress = (float) (mCurrentPosition - newLineTime) / duration;
                mSmoothedProgress = Math.max(0f, Math.min(1f, initProgress));
            } else {
                mSmoothedProgress = 0f;
            }
            mIsInitialPositioning = false; // 定位完成，退出初始状态
            invalidate();
            return;
        }

        // 正常播放中的滚动逻辑
        if (targetLine != mCurrentLine) {
            // 计算目标行与当前行的距离和方向
            int lineDiff = targetLine - mCurrentLine;
            int lineDistance = Math.abs(lineDiff);
    
            // 判断滚动方向：正数表示向前（下一行），负数表示向后（上一行）
            boolean isForward = lineDiff > 0;
    
            // 如果距离超过阈值（不是相邻行），直接跳转而不滚动
            if (lineDistance > MAX_SCROLL_DISTANCE) {
                if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                    mScrollAnimator.cancel();
                }
                // 直接跳转逻辑
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
                // 初始化新行的进度，避免高亮从头开始
                long newLineTime = mLrcLines.get(targetLine).time;
                long nextTime = (targetLine + 1 < mLrcLines.size())
                        ? mLrcLines.get(targetLine + 1).time
                        : newLineTime + 5000;
                long duration = nextTime - newLineTime;
                if (duration > 0 && mCurrentPosition >= newLineTime) {
                    float initProgress = (float) (mCurrentPosition - newLineTime) / duration;
                    mSmoothedProgress = Math.max(0f, Math.min(1f, initProgress));
                } else {
                    mSmoothedProgress = 0f;
                }
                invalidate();
            } else {
                // 相邻行：只有向前滚动（到下一行）才执行平滑滚动
                if (isForward && targetLine > 3) {
                    smoothScrollTo(targetLine);
                } else {
                    // 向后滚动（到上一行）或前三行：直接跳转
                    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                        mScrollAnimator.cancel();
                    }
                    // 只有前三行向前才等铺满（逐字歌词直接切换，不等铺满）
                    if (isForward && targetLine <= 3 && mSmoothedProgress < 1f) {
                        LrcLine line = mLrcLines.get(mCurrentLine);
                        if (line.words != null && !line.words.isEmpty()) {
                            // 逐字歌词：直接跳转，不走等铺满
                            mCurrentLine = targetLine;
                            mScrollOffset = 0f;
                            mSmoothedProgress = 0f;
                            invalidate();
                            return;
                        }
                        // 普通歌词：等铺满
                        invalidate();
                        return;
                    }
                    mCurrentLine = targetLine;
                    mScrollOffset = 0f;
                    // 初始化新行的进度，避免高亮从头开始
                    long newLineTime = mLrcLines.get(targetLine).time;
                    long nextTime = (targetLine + 1 < mLrcLines.size())
                            ? mLrcLines.get(targetLine + 1).time
                            : newLineTime + 5000;
                    long duration = nextTime - newLineTime;
                    if (duration > 0 && mCurrentPosition >= newLineTime) {
                        float initProgress = (float) (mCurrentPosition - newLineTime) / duration;
                        mSmoothedProgress = Math.max(0f, Math.min(1f, initProgress));
                    } else {
                        mSmoothedProgress = 0f;
                    }
                    invalidate();
                }
            }
        } else {
            // 行数不变，只更新进度
            invalidate();
        }
    }

    /**
     * 新增：手动设置是否显示歌词
     *
     * @param show 是否显示歌词
     */
    public void setShowLyrics(boolean show) {
        if (mShouldShowLyrics != show) {
            mShouldShowLyrics = show;
            invalidate();
        }
    }

    /**
     * 新增：获取当前是否显示歌词
     *
     * @return 是否显示歌词
     */
    public boolean isShowingLyrics() {
        return mShouldShowLyrics;
    }

    /**
     * 新增：重置显示状态
     */
    public void reset() {
        mShouldShowLyrics = false;
        mCurrentPosition = 0;
        mCurrentLine = 0;
        mScrollOffset = 0f;
        mSmoothedProgress = 0f;  
        mIsInitialPositioning = true; // 新增：重置初始定位状态

        // xuameng ExoPlayer：重置平滑时间源
        mSmoothedPosition = 0L;
        mLastUpdatePositionTime = 0L;

        invalidate();
    }

    /**
     * xuameng 新增：获取逐字歌词的平滑 elapsed
     */
    private long getKaraokeElapsed(LrcLine line) {
        long base = mCurrentPosition - line.time;

        // xuameng ExoPlayer：逐字帧级低通，防抖
        if (mExoMode) {
            base = (long) (base * 0.85f + (mCurrentPosition - line.time) * 0.15f);
        }
        return base;
    }

    /**
     * xuameng 新增：逐字卡拉OK绘制
     */
    private void drawKaraokeLine(Canvas canvas, LrcLine line, float y) {
        float startX = getWidth() / 2 - line.width / 2;

        // 1. 先画整行普通底色（和老歌词一样）
        canvas.drawText(line.text, startX, y, mNormalPaint);

        Paint.FontMetrics fm = mHighlightPaint.getFontMetrics();
        float textTop = y + fm.top;
        float textBottom = y + fm.bottom;

        // xuameng ExoPlayer：使用平滑 elapsed
        long lineElapsed = getKaraokeElapsed(line);

        float x = startX;
        for (WordInfo w : line.words) {
            if (lineElapsed >= w.start) {
                float wordProgress = 1.0f;
                if (lineElapsed < w.end) {
                    wordProgress = (float)(lineElapsed - w.start) / (w.end - w.start);
                    wordProgress = Math.max(0f, Math.min(1f, wordProgress));
                }

                if (wordProgress >= 0.99f) wordProgress = 1.0f;

                float highlightWidth = w.width * wordProgress;
                if (highlightWidth > 0.5f) {
                    // 关键：用整行 clipRect，但只裁剪当前字的区域
                    canvas.save();
                    canvas.clipRect(x, textTop, x + highlightWidth, textBottom);
                    // 画整行文本，不是逐字！
                    canvas.drawText(line.text, startX, y, mHighlightPaint);
                    canvas.restore();
                }
            }
            x += w.width;
        }
    }

    /**
     * 绘制卡拉OK效果
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 检查是否应该显示歌词 播放进度0.1秒以上才允许显示
        String hint = null;   //xuameng 可以加 歌词载入中...
        if (!mShouldShowLyrics) {
            // 0.1秒以内不显示歌词，显示提示信息
            hint = null;   //xuameng 可以加 歌词载入中... 现是是什么都不显示，如改成文字就取消下面的return;
            return;
        } else {
            if (mLrcLines == null || mLrcLines.isEmpty()) {  //0.1秒以上才没有歌词显示
                hint = "聚汇影视提示您：暂无歌词!";   //xuameng 可以加 歌词载入中...
            }
        }

        if (hint != null) {
            float textWidth = mNormalPaint.measureText(hint);
            float centerX = getWidth() / 2 - textWidth / 2;
            float centerY = getHeight() / 2;
            canvas.drawText(hint, centerX, centerY, mHighlightPaint);
            return;
        }

        // 计算总高度和起始Y位置，实现垂直居中
        float lineHeight = mNormalPaint.getTextSize() * 1.5f;
        int visibleLines = Math.min(mLrcLines.size(), 7); // 显示最多7行歌词
        float totalHeight = lineHeight * visibleLines;
        // 将浮点数偏移转换为整数像素，避免亚像素渲染问题
        float scrollOffsetPixels = Math.round(mScrollOffset * lineHeight);  //滚动偏移像素
        float startY = (getHeight() - totalHeight) / 2 + mNormalPaint.getTextSize() - scrollOffsetPixels;  // 计算起始Y位置，使当前行居中显示  并滚动
        // 计算实际可见的行范围，确保不会超出歌词列表边界
        int startLineIndex = Math.max(0, mCurrentLine - 3);
        int endLineIndex = Math.min(mLrcLines.size() - 1, mCurrentLine + 3);

        // 绘制当前行及前后行
        for (int i = 0; i < visibleLines; i++) {
            int actualIndex = startLineIndex + i;
            if (actualIndex < 0 || actualIndex >= mLrcLines.size()) {
                continue;
            }

            LrcLine line = mLrcLines.get(actualIndex);
            float y = startY + i * lineHeight;

            if (actualIndex == mCurrentLine) {
                // xuameng debug
                Log.d("LrcView_xuameng", "onDraw currentLine=" + actualIndex + " time=" + line.time + " pos=" + mCurrentPosition + " words=" + (line.words != null ? line.words.size() : "null"));

                if (line.words != null && !line.words.isEmpty()) {
                    drawKaraokeLine(canvas, line, y);
                } else {
                    // 当前行：卡拉OK高亮效果
                    float targetProgress = 0f;
                    if (mCurrentPosition >= line.time) {
                        long nextTime = (actualIndex + 1 < mLrcLines.size())
                            ? mLrcLines.get(actualIndex + 1).time
                            : line.time + 5000;
                        long duration = nextTime - line.time;

                       if (duration > 0) {
                           targetProgress = (float) (mCurrentPosition - line.time) / duration;
                           targetProgress = Math.max(0f, Math.min(1f, targetProgress));
                       }
                    }

                    if (targetProgress > 0.99f) {
                        targetProgress = 1.0f;
                    }

                    // 平滑滤波
                    if (targetProgress >= 1.0f) {
                        mSmoothedProgress = 1.0f;
                    } else {
                        mSmoothedProgress += (targetProgress - mSmoothedProgress) * 0.1f;
                    }
                    float progress = mSmoothedProgress;

                    // 获取字体度量信息
                    Paint.FontMetrics fm = mHighlightPaint.getFontMetrics();
                    float textTop = y + fm.top;
                    float textBottom = y + fm.bottom;

                    // 绘制背景文本（完整）
                    canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mNormalPaint);

                    // 绘制高亮部分（渐变填充）
                    float highlightWidth = line.width * progress;
                    // 防止裁剪区域无效
                    if (highlightWidth < 1f) {
                        highlightWidth = 1f;
                    }
                    canvas.save();
                    // 使用精确的裁剪区域
                    canvas.clipRect(getWidth() / 2 - line.width / 2, 
                                    textTop,
                                    getWidth() / 2 - line.width / 2 + highlightWidth, 
                                    textBottom);
                    canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mHighlightPaint);
                    canvas.restore();
                }
            } else {
                // 非当前行：普通显示
                canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mNormalPaint);
            }
        }

        // xuameng ExoPlayer 逐字歌词自驱渲染，避免等外部进度回调才动
        if (mExoMode && mShouldShowLyrics && mLrcLines != null && !mLrcLines.isEmpty()) {
            LrcLine cur = mLrcLines.get(mCurrentLine);
            if (cur.words != null && !cur.words.isEmpty()) {
                long lineElapsed = mCurrentPosition - cur.time;
                long nextTime = (mCurrentLine + 1 < mLrcLines.size())
                        ? mLrcLines.get(mCurrentLine + 1).time
                        : cur.time + 5000;

                if (lineElapsed < nextTime - cur.time) {
                    invalidate();
                }
            }
        }
    }

    /**
     * 将 sp 值转换为 px 值  以便与字幕的字体大小一致
     */
    private float spToPx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }

    /**
     * 清理资源
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mScrollAnimator != null) {
            mScrollAnimator.cancel();
            mScrollAnimator = null;
        }
    }
}
