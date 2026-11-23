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
 * 20261122 新增：逐字卡拉OK格式歌词，无歌词时加长提示文字
 * 20261122 新增：逐字歌词“渲染时间轴”平滑推进
 * 说明：普通歌词走 mSmoothedProgress 滤波，逐字歌词原来是直接吃播放器原始进度，
 *      播放器（最新版 ExoPlayer 对 MP3 的改动后）上报的进度是离散跳变的，
 *      而单字时长只有几十~几百毫秒，一次刷新就会跨过半个字甚至整字。
 *      本次给逐字高亮单独引入一条逐帧推进的平滑时间轴 mRenderTime，
 *      并且播放中持续逐帧重绘，彻底消除跳字。
 * 20261123 新增：外部调用seekto时歌词可以实时滚动
 * 20261123 合并：普通歌词与逐字歌词合并为同一套方案（EXO 平滑方案统一按逐字歌词的做法实现）。
 *      普通歌词不再走 mSmoothedProgress 滤波，改为和逐字歌词共用同一条逐帧推进的
 *      平滑时间轴 mRenderTime；由此删除了 mSmoothedProgress 字段，以及围绕它的
 *      一批进度初始化判断和“普通歌词等铺满”判断，减少两套方案互相冲突。
 *      现在只有一套高亮逻辑：进度统一由 mRenderTime 决定，
 *      逐字行按字的起止时间填充，普通行按整行时长线性填充。
 * 20261123 修复：逐字歌词换行首帧整行闪满问题。
 *      原因是换行的那一帧 mRenderTime 已经被同步项拉到新行开始后几十毫秒，
 *      而逐字行的高亮是“每字 clip 后画整行文字”，只要某帧 lineElapsed 越过最后一个字的 end，
 *      循环中所有字都会以 wordProgress=1 被 clip 出来，拼起来就是整行闪黄。
 *      修复方式：
 *        1) 换行时把 mRenderTime 对齐到新行的 line.time，保证首帧 lineElapsed=0；
 *        2) 逐字行绘制时把 lineElapsed 钳到 [0, lastWordEnd]，不让它越过本行末尾；
 *      普通歌词逻辑保持不变。
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

    // ===== xuameng：统一的平滑时间轴（普通歌词与逐字歌词共用） =====
    // 说明：合并前普通歌词用 mSmoothedProgress 滤波、逐字歌词用 mRenderTime 时间轴；
    //      合并后两者只保留这一条 mRenderTime，普通歌词与逐字歌词的高亮都由它推进。
    private float mRenderTime = 0f;              // 平滑渲染时间轴（毫秒，浮点），高亮只认它
    private long mLastFrameNanos = 0L;           // 上一帧的系统时间，用于计算帧间隔
    private long mLastPositionChangeNanos = 0L;  // 播放器进度“数值发生变化”的最近时间
    private long mLastHostPosition = Long.MIN_VALUE; // 上一次的播放器进度，用于判断有没有前进
    private boolean mIsPlaying = false;          // 播放状态（宿主通过 setPlaying/updateTime 告知）
    private float mPlaybackSpeed = 1.0f;         // 播放倍速，变速播放时同步调用 setPlaybackSpeed

    /** 每帧向播放器真实进度收敛的系数：越小越平滑、越大越跟手，0.03~0.12 之间比较合适 */
    private static final float RENDER_SYNC_GAIN = 0.05f;
    /** 单帧最大推进量，防止掉帧/切后台回来后一次冲过头 */
    private static final float RENDER_MAX_FRAME_MS = 64f;
    /** 播放器进度长时间不前进则视为暂停，冻结时间轴（需明显大于宿主的轮询间隔） */
    private static final long RENDER_IDLE_TIMEOUT_MS = 1000L;
    /** 与播放器真实进度相差超过该值视为 seek/跳转，时间轴直接硬对齐 */
    private static final long RENDER_RESYNC_THRESHOLD_MS = 800L;
    /** 向后 seek 判定：正常播放进度只会前进，倒退超过该值即认定用户拖回了进度条 */
    private static final long RENDER_BACKWARD_SEEK_TOLERANCE_MS = 250L;
    /** xuameng：20261123 修复：换行时允许把 mRenderTime 对齐到新行起点的最大偏差。
     *  只有当 mRenderTime 落在 [newLineTime - 30ms, newLineTime + 150ms] 之间时才对齐，
     *  避免跨多行跳转/seek 时被误对齐。 */
    private static final long RENDER_LINE_ALIGN_EARLY_MS = 30L;
    private static final long RENDER_LINE_ALIGN_LATE_MS = 150L;
    /** xuameng：调试日志开关。逐帧重绘时务必关闭，否则日志本身会把渲染拖慢 */
    private static final boolean DEBUG_LOG = false;

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
     * xuameng 新增：设置播放状态
     * 强烈建议宿主在 Player.Listener#onIsPlayingChanged 里调用它，
     * 这样暂停时高亮会立刻冻结，不需要靠“进度不前进”去猜。
     *
     * @param playing 是否正在播放
     */
    public void setPlaying(boolean playing) {
        if (mIsPlaying == playing) {
            return;
        }
        mIsPlaying = playing;
        if (playing) {
            // 从暂停恢复：只有偏差明显（seek 或长时间漂移）才硬对齐。
            // 原来这里是无条件 mRenderTime = mCurrentPosition，这是“高亮往回走一点”的主因之一：
            // seek 期间宿主不会调用 updateTime，mCurrentPosition 仍是 seek 之前的旧值，
            // 恢复播放时会把已经平滑的时间轴拽回旧位置，之后才慢慢爬向新位置。
            if (Math.abs((float) mCurrentPosition - mRenderTime) > RENDER_RESYNC_THRESHOLD_MS) {
                mRenderTime = mCurrentPosition;
            }
            mLastPositionChangeNanos = System.nanoTime();
        }
        mLastFrameNanos = 0L; // 重置帧计时，避免把暂停时长算进下一帧
        if (playing) {
            postInvalidateOnAnimation();
        }
    }

    /**
     * xuameng 新增：设置播放倍速（变速播放时调用，保证时间轴推进速度一致）
     *
     * @param speed 倍速，默认 1.0f
     */
    public void setPlaybackSpeed(float speed) {
        if (speed > 0.1f && speed < 5.0f) {
            mPlaybackSpeed = speed;
        }
    }

    /**
     * xuameng 新增：宿主 seekTo 之后立即调用（推荐）
     * 时间轴瞬时对齐到 seek 的目标位置，绝不做平滑追赶，从根上避免“高亮往回走一点”。
     * 暂停状态下 seek 也能立刻反映到画面上（此时宿主不会走正常的进度轮询）。
     *
     * @param position seek 的目标位置（毫秒）
     */
    public void setSeekPosition(long position) {
        mRenderTime = position;                 // 时间轴先行硬对齐
        mCurrentPosition = position;
        mLastHostPosition = position;           // 同步基准，避免下一次被当成倒退再对齐一次
        mLastPositionChangeNanos = System.nanoTime();
        mLastFrameNanos = 0L;                   // 重置帧计时，seek 的耗时不算进下一帧
        updateTime(position, mIsPlaying);       // 复用定位/换行/滚动逻辑，立即刷新画面
    }

    /**
     * 解析LRC格式歌词
     *
     * @param lrcContent LRC格式歌词内容
     */
    public void setLrcText(String lrcContent) {
        // xuameng debug
        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "lrcContent length=" + (lrcContent == null ? "NULL" : lrcContent.length()) +
                  " first200=" + (lrcContent == null ? "null" : lrcContent.substring(0, Math.min(200, lrcContent.length()))));
        }
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
                if (DEBUG_LOG) {
                    Log.d("LrcView_xuameng", "逐字行原始: t1=" + t1 + " t2=" + t2 + " line=" + line.substring(0, Math.min(60, line.length())));
                }

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
                if (DEBUG_LOG) {
                    Log.d("LrcView_xuameng", "逐字行 lineTime=" + lineTime + "ms (" + lineTime/1000 + "s)");
                }

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
        if (DEBUG_LOG) {
            for (int i = 0; i < Math.min(5, mLrcLines.size()); i++) {
                Log.d("LrcView_xuameng", "排序后 line[" + i + "] time=" + mLrcLines.get(i).time + " text=" + mLrcLines.get(i).text.substring(0, Math.min(15, mLrcLines.get(i).text.length())));
            }
        }

        // 重置所有状态
        mShouldShowLyrics = false;
        mCurrentLine = 0; // 总是从第0行开始
        mScrollOffset = 0f;
        mCurrentPosition = 0;
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        // xuameng 新增：重置平滑时间轴
        mRenderTime = 0f;
        mLastFrameNanos = 0L;
        mLastHostPosition = Long.MIN_VALUE;
        mLastPositionChangeNanos = 0L;
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            mScrollAnimator.cancel();
        }
        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "setLrcText done, mLrcLines.size()=" + mLrcLines.size());
        }
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
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // 动画取消时也更新当前行
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
            }

        });

        mScrollAnimator.start();
    }

    /**
     * xuameng 新增：推进高亮的平滑时间轴（普通歌词与逐字歌词共用）
     * 规则：按帧自主推进（保证连续） + 缓慢向播放器真实进度收敛（保证不跑偏）
     *
     * @return 时间轴本次是否真的在推进（用于决定要不要请求下一帧）
     */
    private boolean advanceRenderTime() {
        long now = System.nanoTime();
        if (mLastFrameNanos == 0L) {
            mLastFrameNanos = now;
            return false;
        }
        float dtMs = (now - mLastFrameNanos) / 1_000_000f;
        mLastFrameNanos = now;
        if (dtMs <= 0f) {
            return false;
        }
        if (dtMs > RENDER_MAX_FRAME_MS) {
            dtMs = RENDER_MAX_FRAME_MS; // 掉帧保护
        }

        // 播放器进度是否还在前进（暂停、宿主停止轮询时都会停止前进）
        boolean progressAlive = (mLastPositionChangeNanos != 0L)
                && (now - mLastPositionChangeNanos) < RENDER_IDLE_TIMEOUT_MS * 1_000_000L;

        if (mIsPlaying && progressAlive) {
            // 1) 按帧自主推进：这才是高亮平滑的关键，不再依赖播放器给的时间精度
            mRenderTime += dtMs * mPlaybackSpeed;
            // 2) 缓慢收敛到真实进度：消除系统时钟与音频时钟的漂移，误差不会累积
            mRenderTime += (mCurrentPosition - mRenderTime) * RENDER_SYNC_GAIN;
            return true;
        }
        return false;
    }

    /**
     * 更新播放进度（旧入口，默认按“正在播放”处理）
     * 内部会结合“进度数值有没有变化”自动识别暂停，宿主不改代码也能基本可用。
     *
     * @param position 当前播放时间（毫秒）
     */
    public void updateTime(long position) {
        updateTime(position, true);
    }

    /**
     * xuameng 20261123 修复：切换到新行时，把平滑时间轴对齐到新行的 line.time，
     * 保证逐字行首帧 lineElapsed=0，避免"首帧整行闪黄"。
     * 只在相邻行正常切换时对齐；跨多行跳转/seek 由调用方自己硬对齐，不动它。
     */
    private void alignRenderTimeToLineIfNeeded(int newLine) {
        if (mLrcLines == null || newLine < 0 || newLine >= mLrcLines.size()) {
            return;
        }
        long newLineTime = mLrcLines.get(newLine).time;
        float diff = mRenderTime - newLineTime;
        // mRenderTime 比新行稍微早一点（前一行还没收敛完）或稍微晚一点（updateTime 已到、画面还没换）
        // 都对齐到新行起点；明显超前/明显落后属于 seek/大跳转，不做对齐交给既有的硬同步逻辑。
        if (diff >= -RENDER_LINE_ALIGN_EARLY_MS && diff <= RENDER_LINE_ALIGN_LATE_MS) {
            mRenderTime = newLineTime;
        }
    }

    /**
     * xuameng 新增：更新播放进度 + 播放状态（推荐宿主使用这个重载）
     *
     * @param position 当前播放时间（毫秒）
     * @param playing  是否正在播放
     */
    public void updateTime(long position, boolean playing) {
        long now = System.nanoTime();
        long previousHostPosition = mLastHostPosition; // 新增：先留住上一次进度，用于判断前进还是倒退

        if (position != mLastHostPosition) {
            mLastHostPosition = position;
            mLastPositionChangeNanos = now;
        }
        mIsPlaying = playing;

        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "updateTime pos=" + position + " playing=" + playing
                    + " render=" + mRenderTime + " shouldShow=" + mShouldShowLyrics
                    + " currentLine=" + mCurrentLine + " lines=" + (mLrcLines == null ? 0 : mLrcLines.size()));
        }

        // xuameng 新增：seek / 跳转 / 首次进入时，平滑时间轴直接硬对齐，不做平滑追赶
        if (Math.abs((float) position - mRenderTime) > RENDER_RESYNC_THRESHOLD_MS) {
            mRenderTime = position;
        }

        // xuameng 新增：向后 seek 检测（宿主没有调用 setSeekPosition 时的兜底）
        // 正常播放进度只会前进；本次进度比上次倒退超过阈值，就说明用户把进度条拖回去了，
        // 必须立刻硬对齐。否则差值落在 800ms 阈值以内时会走 RENDER_SYNC_GAIN 慢收敛，
        // 表现为高亮以极小步幅一点点往回爬，也就是“高亮往回走一点”。
        if (previousHostPosition != Long.MIN_VALUE
                && position < previousHostPosition - RENDER_BACKWARD_SEEK_TOLERANCE_MS) {
            mRenderTime = position;
        }

        // 检查是否达到最小显示位置
        if (position < MIN_POSITION_TO_SHOW) {
            // 进度小于0.1秒，不显示歌词，但保持在第一行
            mShouldShowLyrics = false;
            mCurrentLine = 0; // 确保强制重置到第一行
            mScrollOffset = 0f; // 重置滚动偏移
            mRenderTime = position; // 新增：时间轴同步重置
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
        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "updateTime targetLine=" + targetLine + " lineTime=" + mLrcLines.get(targetLine).time);
        }

        // 关键修改：处理初始定位
        if (mIsInitialPositioning) {
            // 初始定位阶段，直接跳转到目标行，不执行滚动动画
            mCurrentLine = targetLine;
            mScrollOffset = 0f;
            // 合并后高亮进度由平滑时间轴直接决定：初始定位时把时间轴硬对齐到当前进度，
            // 高亮自然落在正确位置，不再需要单独初始化行进度
            mRenderTime = position; // xuameng 新增：初始定位时时间轴硬对齐
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
                // 20261123 修复：跨多行跳转同样对齐，避免首帧越界
                alignRenderTimeToLineIfNeeded(targetLine);
                invalidate();
            } else {
                // 相邻行：只有向前滚动（到下一行）才执行平滑滚动
                if (isForward && targetLine > 3) {
                    // 20261123 修复：相邻向前切换前先对齐时间轴，再启动滚动动画
                    alignRenderTimeToLineIfNeeded(targetLine);
                    smoothScrollTo(targetLine);
                } else {
                    // 向后滚动（到上一行）或前三行：直接跳转
                    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                        mScrollAnimator.cancel();
                    }
                    // 合并后普通歌词与逐字歌词同为一套方案：高亮进度统一由平滑时间轴决定，
                    // 换行时不再需要按行类型分别初始化进度，也不再需要“普通歌词等铺满”的判断
                    mCurrentLine = targetLine;
                    mScrollOffset = 0f;
                    // 20261123 修复：向后切行/前三行直接切换也做一次对齐
                    alignRenderTimeToLineIfNeeded(targetLine);
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
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        // xuameng 新增：重置平滑时间轴
        mRenderTime = 0f;
        mLastFrameNanos = 0L;
        mLastHostPosition = Long.MIN_VALUE;
        mLastPositionChangeNanos = 0L;
        invalidate();
    }

    /**
     * xuameng 新增：卡拉OK高亮绘制（普通歌词与逐字歌词共用一套方案）
     * 关键改动：高亮进度统一改用平滑时间轴 mRenderTime，不再直接使用播放器原始进度，
     *          也不再区分两套平滑机制。
     * - 逐字行（line.words != null）：按每个字的起止时间逐字填充；
     * - 普通行（line.words == null）：按整行时长线性填充。
     *
     * @param index 当前行在 mLrcLines 中的下标（普通行需要用它取下一行时间算行时长）
     */
    private void drawKaraokeLine(Canvas canvas, LrcLine line, int index, float y) {
        float startX = getWidth() / 2 - line.width / 2;

        // 1. 先画整行普通底色（和老歌词一样）
        canvas.drawText(line.text, startX, y, mNormalPaint);

        Paint.FontMetrics fm = mHighlightPaint.getFontMetrics();
        float textTop = y + fm.top;
        float textBottom = y + fm.bottom;

        // xuameng 修改：使用平滑时间轴，且用浮点运算，避免整字跳变
        float lineElapsed = mRenderTime - line.time;

        if (line.words != null && !line.words.isEmpty()) {
            // ===== 逐字歌词：每个字按自己的起止时间推进 =====
            // 20261123 修复：逐字行的 lineElapsed 必须钳到 [0, 最后一个字的 end]。
            // 换行首帧或同步项过冲时，lineElapsed 可能越过最后一个字的 end，
            // 导致循环里所有字都被判定为 wordProgress=1，clip 拼起来就是整行闪黄。
            long lastWordEnd = line.words.get(line.words.size() - 1).end;
            if (lineElapsed < 0f) lineElapsed = 0f;
            if (lineElapsed > lastWordEnd) lineElapsed = lastWordEnd;

            float x = startX;
            for (WordInfo w : line.words) {
                // 20261123 修复：未到达的字完全不画，避免因 clip 区间错位闪出整行。
                // 原来写成 "if (lineElapsed >= w.start)"，在 lineElapsed 超前时会把后面的字
                // 一起画满；现在直接对 lineElapsed 做了上界钳制，未到的字 lineElapsed < w.start，
                // 自然不会进入分支，行为正确。
                if (lineElapsed >= w.start) {
                    float wordProgress = 1.0f;
                    long wordDur = w.end - w.start;
                    if (wordDur > 0 && lineElapsed < w.end) {
                        wordProgress = (lineElapsed - w.start) / (float) wordDur;
                        wordProgress = Math.max(0f, Math.min(1f, wordProgress));
                    }
                    //xuameng 盛满高亮
                    if (wordProgress > 0.99f) wordProgress = 1.0f;
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
        } else {
            // ===== 普通歌词：整行按行时长线性推进（同样走平滑时间轴） =====
            long nextTime = (index + 1 < mLrcLines.size())
                    ? mLrcLines.get(index + 1).time
                    : line.time + 5000;
            long duration = nextTime - line.time;
            float progress = 0f;
            if (duration > 0) {
                progress = (mRenderTime - line.time) / (float) duration;
                progress = Math.max(0f, Math.min(1f, progress));
            }
            //xuameng 盛满高亮
            if (progress > 0.99f) progress = 1.0f;
            float highlightWidth = line.width * progress;
            if (highlightWidth > 0.5f) {
                canvas.save();
                canvas.clipRect(startX, textTop, startX + highlightWidth, textBottom);
                canvas.drawText(line.text, startX, y, mHighlightPaint);
                canvas.restore();
            }
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

        // xuameng 新增：推进高亮平滑时间轴（必须放在绘制之前）
        boolean timeAdvancing = advanceRenderTime();

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
                // 当前行：卡拉OK高亮效果（普通行/逐字行统一走 drawKaraokeLine）
                drawKaraokeLine(canvas, line, actualIndex, y);
            } else {
                // 非当前行：普通显示
                canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mNormalPaint);
            }
        }

        // xuameng 新增：时间轴在推进时请求下一帧，让高亮按屏幕帧率平滑走字
        // 注意：这里不能再逐帧打 Log，否则日志本身会把渲染拖慢
        if (timeAdvancing) {
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
