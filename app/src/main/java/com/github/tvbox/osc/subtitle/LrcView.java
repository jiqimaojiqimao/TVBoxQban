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
 */
public class LrcView extends View {

    /**
     * LRC歌词行数据结构
     */
    private static class LrcLine {
        long time; // 时间戳（毫秒）
        String text; // 歌词文本
        float width; // 文本宽度（用于绘制）
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
        mLrcLines.clear();
        String[] lines = lrcContent.split("\n");
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{1,3})\\]");

        for (String line : lines) {
            // 跳过空行
            if (line.trim().isEmpty()) {
                continue;
            }

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
                    mLrcLines.add(lrcLine);
                }
            }
            // 注意：这里我们跳过了纯时间标签行（如[00:13.760]），因为它们没有歌词内容
        }

        // 按时间排序
        Collections.sort(mLrcLines, (a, b) -> Long.compare(a.time, b.time));

        // 移除重复的歌词行
        removeDuplicateLines();

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


private enum LrcState {
    IDLE,               // 未开始 / 进度不足
    INIT_POSITIONING,   // 初始定位（无动画）
    SCROLLING,          // 正在平滑滚动
    STABLE              // 正常播放、稳定状态
}

private boolean canChangeToLine(int targetLine, long position) {
    // 1. 非前 3 行：时间到了就换
    if (targetLine > 3) {
        return true;
    }

    // 2. 前 3 行：必须高亮铺满
    if (mCurrentLine >= targetLine) {
        return true; // 回退或不变，允许
    }

    LrcLine current = mLrcLines.get(mCurrentLine);
    long nextTime = (mCurrentLine + 1 < mLrcLines.size())
            ? mLrcLines.get(mCurrentLine + 1).time
            : current.time + 5000;

    long duration = nextTime - current.time;
    if (duration <= 0) {
        return true;
    }

    float progress = (float) (position - current.time) / duration;
    return progress >= 0.99f;
}

private int findTargetLine(long position) {
    if (mLrcLines.isEmpty() || position < mLrcLines.get(0).time) {
        return 0;
    }

    for (int i = 0; i < mLrcLines.size() - 1; i++) {
        if (position >= mLrcLines.get(i).time &&
            position < mLrcLines.get(i + 1).time) {
            return i;
        }
    }
    return mLrcLines.size() - 1;
}

private void initLineProgress(int lineIndex, long position) {
    LrcLine line = mLrcLines.get(lineIndex);
    long nextTime = (lineIndex + 1 < mLrcLines.size())
            ? mLrcLines.get(lineIndex + 1).time
            : line.time + 5000;

    long duration = nextTime - line.time;
    if (duration > 0 && position >= line.time) {
        float progress = (float) (position - line.time) / duration;
        mSmoothedProgress = Math.max(0f, Math.min(1f, progress));
    } else {
        mSmoothedProgress = 0f;
    }
}

private void enterIdleState() {
    mShouldShowLyrics = false;
    mCurrentLine = 0;
    mScrollOffset = 0f;
    mSmoothedProgress = 0f;
    mIsInitialPositioning = true;
    if (mScrollAnimator != null) {
        mScrollAnimator.cancel();
    }
}

private void handleInitPositioning(int targetLine) {
    mCurrentLine = targetLine;
    mScrollOffset = 0f;
    initLineProgress(targetLine, mCurrentPosition);
    mIsInitialPositioning = false;
    invalidate();
}

private void handleStableState(int targetLine) {
    if (targetLine == mCurrentLine) {
        invalidate();
        return;
    }

    // ===== 换行策略 =====
    if (!canChangeToLine(targetLine, mCurrentPosition)) {
        invalidate();
        return;
    }

    int lineDiff = targetLine - mCurrentLine;

    // 非相邻行：直接跳转
    if (Math.abs(lineDiff) > MAX_SCROLL_DISTANCE) {
        applyJumpToLine(targetLine);
        return;
    }

    // 向后跳转（上一行）：直接跳转
    if (lineDiff < 0) {
        applyJumpToLine(targetLine);
        return;
    }

    // 向前相邻行：平滑滚动
    applySmoothScrollTo(targetLine);
}

、private void applySmoothScrollTo(int targetLine) {
    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
        return;
    }

    int lineDiff = targetLine - mCurrentLine;
    mScrollAnimator = ValueAnimator.ofFloat(0f, lineDiff);
    mScrollAnimator.setDuration(mScrollDuration);
    mScrollAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

    mScrollAnimator.addUpdateListener(animation -> {
        mScrollOffset = (float) animation.getAnimatedValue();
        invalidate();
    });

    mScrollAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mCurrentLine = targetLine;
            mScrollOffset = 0f;
            initLineProgress(targetLine, mCurrentPosition);
        }
    });

    mScrollAnimator.start();
}

private void applyJumpToLine(int targetLine) {
    if (mScrollAnimator != null) {
        mScrollAnimator.cancel();
    }
    mCurrentLine = targetLine;
    mScrollOffset = 0f;
    initLineProgress(targetLine, mCurrentPosition);
    invalidate();
}

private void applyJumpToLine(int targetLine) {
    if (mScrollAnimator != null) {
        mScrollAnimator.cancel();
    }
    mCurrentLine = targetLine;
    mScrollOffset = 0f;
    initLineProgress(targetLine, mCurrentPosition);
    invalidate();
}

private LrcState getCurrentState() {
    if (!mShouldShowLyrics) {
        return LrcState.IDLE;
    }
    if (mIsInitialPositioning) {
        return LrcState.INIT_POSITIONING;
    }
    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
        return LrcState.SCROLLING;
    }
    return LrcState.STABLE;
}

public void updateTime(long position) {
    if (mLrcLines.isEmpty()) {
        return;
    }

    // ===== 1. 是否允许显示歌词 =====
    if (position < MIN_POSITION_TO_SHOW) {
        enterIdleState();
        invalidate();
        return;
    }

    mShouldShowLyrics = true;

    // ===== 2. 计算目标行 =====
    int targetLine = findTargetLine(position);
    mCurrentPosition = position;

    // ===== 3. 状态机 =====
    LrcState currentState = getCurrentState();

    switch (currentState) {

        case IDLE:
        case INIT_POSITIONING:
            handleInitPositioning(targetLine);
            break;

        case SCROLLING:
            // 滚动期间不响应换行，等动画结束
            invalidate();
            break;

        case STABLE:
            handleStableState(targetLine);
            break;
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
        invalidate();
    }

    /**
     * 绘制卡拉OK效果
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 检查是否应该显示歌词
        if (!mShouldShowLyrics) {
            // 不显示歌词，显示提示信息
            String hint = "";   //xuameng 可以加 歌词载入中...
            float textWidth = mNormalPaint.measureText(hint);
            float centerX = getWidth() / 2 - textWidth / 2;
            float centerY = getHeight() / 2;
            canvas.drawText(hint, centerX, centerY, mNormalPaint);
            return;
        }

        if (mLrcLines.isEmpty()) {
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
            }else {
                // 非当前行：普通显示
                canvas.drawText(line.text, getWidth() / 2 - line.width / 2, y, mNormalPaint);
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
