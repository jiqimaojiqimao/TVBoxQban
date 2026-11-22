package com.github.tvbox.osc.subtitle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.Choreographer;
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
 *
 * 后续修复（普通歌词逻辑完全保持原始，未作任何修改）：
 *  - 逐字歌词独立渲染时间轴 mRenderTime，消除 ExoPlayer 粗粒度进度上报导致的"一次跳半个字"
 *  - 逐字宽度基于整行 getTextWidths 度量，解决英文行末字右侧漏缝
 *  - 裁剪右边界加 HIGHLIGHT_RIGHT_PADDING（2.0f）兜底伪粗体外扩
 *  - 新增 setPlaying / setSeekPosition / 两参 updateTime 接口
 *  - 播放中持续逐帧 invalidate，不再依赖宿主轮询频率
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


    // ===== 逐字歌词平滑渲染时间轴 =====
    /** 逐字高亮用的渲染时间（毫秒，按帧平滑推进） */
    private float mRenderTime = 0f;
    /** 宿主最近一次上报的进度（用于方向判定） */
    private long mLastHostPosition = Long.MIN_VALUE;
    /** 宿主最近一次"数值有变化"的纳秒时间戳，用于识别暂停 */
    private long mLastPositionChangeNanos = 0L;
    /** 上一帧的纳秒时间戳 */
    private long mLastFrameNanos = 0L;
    /** 当前是否正在播放（false 时时间轴冻结） */
    private boolean mIsPlaying = false;
    /** 播放倍速，默认 1.0 */
    private float mPlaybackSpeed = 1.0f;

    private static final float RENDER_SYNC_GAIN = 0.05f;              // 每帧向真实进度收敛的系数
    private static final long  RENDER_IDLE_TIMEOUT_MS = 1000L;        // 进度多久不动视为暂停
    private static final float RENDER_RESYNC_THRESHOLD_MS = 800f;     // 与真实进度差超过此值硬对齐（seek）
    private static final long  RENDER_BACKWARD_SEEK_TOLERANCE_MS = 250L; // 倒退超过此值视为向后 seek
    private static final long  RENDER_FORWARD_SEEK_TOLERANCE_MS = 250L;  // 向前大跳超过此值视为向前 seek
    private static final long  RENDER_MAX_FRAME_MS = 64L;             // 单帧最大推进，防止切后台回来冲过头
    /** 裁剪窗口右边界留白，兜住伪粗体（setFakeBoldText）向外扩展的像素 */
    private static final float HIGHLIGHT_RIGHT_PADDING = 2.0f;



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
     * 设置播放倍速（变速播放时同步调用，否则逐字时间轴会与声音慢/快）
     */
    public void setPlaybackSpeed(float speed) {
        if (speed <= 0f) speed = 1.0f;
        mPlaybackSpeed = speed;
    }

    /**
     * 重新计算所有歌词行的宽度
     * 逐字宽度基于整行 getTextWidths 度量，再做归一化，保证逐字累加严格等于整行宽度，
     * 解决英文行末字右侧漏缝（单字量宽累加 ≠ 整行排版宽度）的问题。
     */
    private void recalculateLineWidths() {
        float[] widthsBuf = new float[1];
        for (LrcLine line : mLrcLines) {
            line.width = mNormalPaint.measureText(line.text);
            if (line.words != null && !line.words.isEmpty()) {
                applyWordWidths(line);
            }
        }
    }

    /**
     * 为逐字歌词分配每个字的宽度：与整行 drawText 共用同一套推进量，
     * 避免单字量宽累加与整行度量不一致。
     */
    private void applyWordWidths(LrcLine line) {
        int len = line.text.length();
        float[] widths = new float[len];
        int got = mNormalPaint.getTextWidths(line.text, widths);
        line.width = mNormalPaint.measureText(line.text);

        // 逐字文本需要在整行 widths 里按字符位置对应
        int cursor = 0;
        float total = 0f;
        for (WordInfo w : line.words) {
            if (w.text == null || w.text.isEmpty()) {
                w.width = 0f;
                continue;
            }
            int wLen = w.text.length();
            float ww = 0f;
            for (int k = 0; k < wLen && cursor + k < got; k++) {
                ww += widths[cursor + k];
            }
            cursor += wLen;
            w.width = ww;
            total += ww;
        }
        // 归一化：逐字累加严格等于整行宽度，消除任何残差
        if (total > 0f && Math.abs(total - line.width) > 0.01f) {
            float k = line.width / total;
            for (WordInfo w : line.words) {
                w.width *= k;
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
                    // 先默认单字量宽，applyWordWidths 会重算
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

        // 重新按整行度量逐字宽度（英文字偶距修正）
        for (LrcLine l : mLrcLines) {
            if (l.words != null && !l.words.isEmpty()) {
                applyWordWidths(l);
            }
        }

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
        mRenderTime = 0f;
        mLastHostPosition = Long.MIN_VALUE;
        mLastPositionChangeNanos = 0L;
        mLastFrameNanos = 0L;
        mIsPlaying = false;
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            mScrollAnimator.cancel();
        }
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
     * 同步播放状态（暂停时逐字时间轴立即冻结）。
     * 在宿主 onPlayStateChanged 里调用。
     */
    public void setPlaying(boolean playing) {
        boolean wasPlaying = mIsPlaying;
        mIsPlaying = playing;
        long now = System.nanoTime();
        if (playing) {
            // 只有偏差明显（seek 或长时间漂移）才硬对齐，
            // 正常暂停/恢复不要用过期的 mCurrentPosition 把时间轴拽回去
            if (Math.abs((float) mCurrentPosition - mRenderTime) > RENDER_RESYNC_THRESHOLD_MS) {
                mRenderTime = mCurrentPosition;
            }
            mLastFrameNanos = now;
            mLastPositionChangeNanos = now;
            invalidate();
            // 请求下一帧持续推进
            if (getVisibility() == VISIBLE && getWidth() > 0) {
                postInvalidateOnAnimation();
            }
        } else {
            // 暂停时冻结时间轴
            mLastFrameNanos = 0L;
            invalidate();
        }
    }

    /**
     * 宿主 seekTo 之后立即调用：时间轴瞬时对齐，不做平滑追赶。
     */
    public void setSeekPosition(long position) {
        mRenderTime = position;
        mCurrentPosition = position;
        mLastHostPosition = position;
        mLastPositionChangeNanos = System.nanoTime();
        mLastFrameNanos = 0L;
        updateTime(position, mIsPlaying);
    }

    /**
     * 两参版本：携带播放状态。推荐使用。
     */
    public void updateTime(long position, boolean playing) {
        mIsPlaying = playing;
        long now = System.nanoTime();
        long previousHostPosition = mLastHostPosition;

        mCurrentPosition = position;
        if (position != mLastHostPosition) {
            mLastHostPosition = position;
            mLastPositionChangeNanos = now;
        }
        if (mLastFrameNanos == 0L) mLastFrameNanos = now;

        // 向后 seek：正常播放进度只会前进，倒退超过阈值立即硬对齐
        if (previousHostPosition != Long.MIN_VALUE
                && position < previousHostPosition - RENDER_BACKWARD_SEEK_TOLERANCE_MS) {
            mRenderTime = position;
        }
        // 向前大跳：一次前进超过阈值（seek/跳曲）硬对齐
        if (previousHostPosition != Long.MIN_VALUE
                && position > previousHostPosition + RENDER_FORWARD_SEEK_TOLERANCE_MS) {
            mRenderTime = position;
        }

        updateTime(position);
    }

    /**
     * 更新播放进度（原始单参入口，保持兼容；默认按"正在播放"处理）
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

        mCurrentPosition = position;

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
            mRenderTime = position;
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

        // 正常播放中的滚动逻辑（与原始代码完全一致，未修改）
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
                mRenderTime = position;
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
                            // 逐字歌词：直接跳转，但初始化新行进度避免闪烁
                            mCurrentLine = targetLine;
                            mScrollOffset = 0f;
                            mRenderTime = position;
                            // xuameng 修复：初始化新行的高亮进度，避免切换瞬间lineElapsed为负导致全白闪烁
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
                            return;
                        }
                        // 普通歌词：等铺满
                        invalidate();
                        return;
                    }
                    mCurrentLine = targetLine;
                    mScrollOffset = 0f;
                    mRenderTime = position;
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
     * 逐字歌词的渲染时间轴按帧推进：按帧自主推进 + 慢收敛同步，不直接用宿主原始进度。
     * 这样无论播放器多少毫秒才上报一次进度，高亮都按屏幕帧率连续推进，消除"跳半个字"。
     */
    private void advanceRenderTime() {
        long now = System.nanoTime();
        // 没开始过或没歌词，不动
        if (mLrcLines == null || mLrcLines.isEmpty() || mLastFrameNanos == 0L) {
            mLastFrameNanos = now;
            return;
        }
        long dtNanos = now - mLastFrameNanos;
        mLastFrameNanos = now;
        if (dtNanos <= 0) return;
        float dtMs = dtNanos / 1_000_000f;
        if (dtMs > RENDER_MAX_FRAME_MS) dtMs = RENDER_MAX_FRAME_MS; // 掉帧/切后台回来不冲

        // 暂停/缓冲判定：进度长时间不动 → 冻结时间轴
        boolean idle = (now - mLastPositionChangeNanos) / 1_000_000L > RENDER_IDLE_TIMEOUT_MS;
        if (!mIsPlaying || idle) {
            return;
        }

        // 1) 按倍速自主推进
        mRenderTime += dtMs * mPlaybackSpeed;
        // 2) 慢收敛到真实进度，避免漂移累积
        mRenderTime += ((float) mCurrentPosition - mRenderTime) * RENDER_SYNC_GAIN;
        // 3) 大偏差硬对齐（seek/跳曲）
        if (Math.abs((float) mCurrentPosition - mRenderTime) > RENDER_RESYNC_THRESHOLD_MS) {
            mRenderTime = mCurrentPosition;
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
        mRenderTime = 0f;
        mLastHostPosition = Long.MIN_VALUE;
        mLastPositionChangeNanos = 0L;
        mLastFrameNanos = 0L;
        mIsPlaying = false;
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            mScrollAnimator.cancel();
        }
        invalidate();
    }

    /**
     * xuameng 新增：逐字卡拉OK绘制
     * 高亮使用 mRenderTime（平滑时间轴），宽度基于归一化后的字宽，右边界加 2px 留白。
     */
    private void drawKaraokeLine(Canvas canvas, LrcLine line, float y) {
        float startX = getWidth() / 2 - line.width / 2;

        // 1. 先画整行普通底色（和老歌词一样）
        canvas.drawText(line.text, startX, y, mNormalPaint);

        Paint.FontMetrics fm = mHighlightPaint.getFontMetrics();
        float textTop = y + fm.top;
        float textBottom = y + fm.bottom;

        // 用平滑时间轴算逐字进度（浮点），不直接用 mCurrentPosition
        float lineElapsed = mRenderTime - line.time;
        if (lineElapsed < 0f) lineElapsed = 0f;

        float x = startX;
        for (WordInfo w : line.words) {
            float wordProgress = 0f;
            if (lineElapsed >= w.end) {
                wordProgress = 1.0f;
            } else if (lineElapsed > w.start) {
                float wordDur = (float) (w.end - w.start);
                if (wordDur > 0f) {
                    wordProgress = (lineElapsed - w.start) / wordDur;
                } else {
                    wordProgress = 1.0f;
                }
            }
            wordProgress = Math.max(0f, Math.min(1f, wordProgress));

            float highlightWidth = w.width * wordProgress;
            if (highlightWidth > 0.5f) {
                // 右边界加 2px 留白，兜住伪粗体外扩
                canvas.save();
                canvas.clipRect(x, textTop, x + highlightWidth + HIGHLIGHT_RIGHT_PADDING, textBottom);
                canvas.drawText(line.text, startX, y, mHighlightPaint);
                canvas.restore();
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

        // 逐帧推进逐字时间轴（普通歌词行不需要，但推进本身代价极小且不会影响普通分支）
        advanceRenderTime();

        // 检查是否应该显示歌词 播放进度0.1秒以上才允许显示
        String hint = null;   //xuameng 可以加 歌词载入中...
        if (!mShouldShowLyrics) {
            // 0.1秒以内不显示歌词，显示提示信息
            hint = null;   //xuameng 可以加 歌词载入中... 现是是什么都不显示，如改成文字就取消下面的return;
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
        boolean needsNextFrame = false;
        for (int i = 0; i < visibleLines; i++) {
            int actualIndex = startLineIndex + i;
            if (actualIndex < 0 || actualIndex >= mLrcLines.size()) {
                continue;
            }

            LrcLine line = mLrcLines.get(actualIndex);
            float y = startY + i * lineHeight;

            if (actualIndex == mCurrentLine) {
                if (line.words != null && !line.words.isEmpty()) {
                    drawKaraokeLine(canvas, line, y);
                    needsNextFrame = true;
                } else {
                    // 当前行：卡拉OK高亮效果（完全保持原始逻辑）
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
                        needsNextFrame = true;
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

        // 播放中（或平滑滤波尚未收敛）时持续请求下一帧，不再依赖宿主轮询频率
        if (needsNextFrame && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
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
