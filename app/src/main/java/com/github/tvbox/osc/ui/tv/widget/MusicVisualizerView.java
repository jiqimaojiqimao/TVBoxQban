package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * xuameng 20261120
 * 音乐可视化视图组件（带振幅颜色渐变）
 * <p>
 * 新增特性：
 * 1，颜色随振幅大小变化
 * 2，保持原有动画平滑性
 * 3，完全兼容原有接口
 * 4，振幅随音量大小变化
 * 5，三种颜色随机变化
 * 6，新增音柱分段效果
 * 7，强音扩散算法：强节奏时主柱+相邻柱协同弹起，距离越远越矮（xuameng 新增）
 */
public class MusicVisualizerView extends View {

    private static final int MAX_AMPLITUDE = 10222;  // xuameng判断音量大小
    private static final int BAR_COUNT = 64;          // xuameng 音柱数量
    private static final int ANIMATION_DURATION = 60; // xuameng 音柱动画时长

    // xuameng 是否应该绘制音柱
    private boolean mVisible = true;

    // ==================== xuameng 新增：强音扩散参数 ====================
    // 强音扩散半径（向左右各影响几个音柱）
    private static final int BEAT_SPREAD_RADIUS = 11;
    // 扩散衰减系数（越大衰减越慢，扩散越远，0~1之间）
    private static final float BEAT_DECAY = 0.80f;
    // =====================================================================

    /**
     * 最低可见高度比例（防止小音量时柱子消失）
     * 调大 → 最小柱子更高
     * 调小 → 差距更大但可能消失
     * 当前 0.005（0.5%）
     */
    private static final float MIN_VISIBLE_RATIO = 0.005f;

    // 改为非静态变量实现动态刷新
    private int[][] colorSchemes = new int[3][3];

    // 新增音柱分段效果相关变量
    private boolean mShowStripes = true; // 是否显示分段效果
    private int mSegmentCount = 20;      // 每个音柱的分段数
    private float mSegmentSpacingFixed = 2.5f; // 段间距固定值（像素）

    // 新增颜色方案刷新方法
    private void refreshColorSchemes() {
        for (int i = 0; i < 3; i++) {
            float baseHue = (float) (Math.random() * 360);
            colorSchemes[i] = new int[]{
                    Color.HSVToColor(new float[]{baseHue, 1f, 1f}),
                    Color.HSVToColor(new float[]{(baseHue + 120) % 360, 1f, 1f}),
                    Color.HSVToColor(new float[]{(baseHue + 240) % 360, 1f, 1f})
            };
        }
        postInvalidate(); // 新增：立即刷新视图
    }

    private static final long COLOR_CYCLE_DURATION = (long) (0.1 * 60 * 1000); // 6秒切换
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];

    // 动画控制器
    private ValueAnimator mAnimator;

    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBarPaint.setStyle(Paint.Style.FILL);
        refreshColorSchemes(); // 新增：初始化时预加载颜色方案
    }

    // 新增音柱分段效果设置方法
    public void setShowStripes(boolean show) {
        mShowStripes = show;
        postInvalidate();
    }

    public void setSegmentCount(int count) {
        mSegmentCount = Math.max(1, count); // 至少1段
        postInvalidate();
    }

    public void setSegmentSpacingFixed(float spacing) {
        mSegmentSpacingFixed = Math.max(0f, spacing); // 确保非负值
        postInvalidate();
    }

    public void updateVisualizer(byte[] fft, float volumeLevel) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;
        // xuameng处理静音状态重置音柱
        if (volumeLevel <= 0f) {
            reset();  
            return;
        }
        mVisible = true; // xuameng 有数据就显示
        // 修改采样策略：前1/3柱子采样低频，后2/3均匀采样中高频
        for (int i = 0; i < BAR_COUNT; i++) {
            // xuameng 1点取1（每根音柱对应一个复数对）
            int barIndex = 2 + i * 2;
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);

                // xuameng改进的频率加权策略（三段式加权）
                float weight;
                int third = BAR_COUNT / 3; // 21
                if (i < third) {
                    // 第一段：xuameng 超低频段(0-200Hz)增益
                    weight = 1.0f;
                } else if (i < third * 2) {
                    // 第二段：xuameng 中低频段(200-800Hz)基准值增益
                    weight = 2.8f;
                } else {
                    // 第三段：xuameng 高频段(800Hz+)指数增强
                    float freqFactor = (float) Math.pow(1.5, (i - third * 2) / 2.0);
                    weight = 2.8f * freqFactor;
                    weight = Math.min(weight, 15.0f); // 限制最大增益
                }
                mTargetHeights[i] = Math.min(
                        (magnitude * getHeight() * weight * volumeLevel) / MAX_AMPLITUDE, // xuameng判断音量大小
                        getHeight() * 0.95f
                );
            }
        }

        // ==================== xuameng 新增：强音扩散算法 ====================
        // 找到当前能量最强的主音柱
        int dominantIndex = 0;
        float maxTarget = 0f;
        for (int i = 0; i < BAR_COUNT; i++) {
            if (mTargetHeights[i] > maxTarget) {
                maxTarget = mTargetHeights[i];
                dominantIndex = i;
            }
        }

        // 只对明显强音做扩散（避免微弱信号也扩散导致乱跳）
        // 阈值：主柱高度超过视图高度的10%才触发扩散
        float spreadThreshold = getHeight() * 0.10f;
        if (maxTarget > spreadThreshold) {
            // 创建临时数组存储扩散后的高度，不破坏原始计算结果
            float[] spreadHeights = new float[BAR_COUNT];
            for (int i = 0; i < BAR_COUNT; i++) {
                spreadHeights[i] = mTargetHeights[i];
            }

            // 以主音柱为中心，向左右扩散
            for (int i = 0; i < BAR_COUNT; i++) {
                int distance = Math.abs(i - dominantIndex);
                float spreadFactor;

                if (distance == 0) {
                    // 主音柱本身不受影响，保持最高
                    spreadFactor = 1.0f;
                } else if (distance <= BEAT_SPREAD_RADIUS) {
                    // 相邻柱：距离越近衰减越小，越近越高
                    spreadFactor = (float) Math.pow(BEAT_DECAY, distance);
                } else {
                    // 超出扩散半径不参与
                    spreadFactor = 0f;
                }

                if (distance > 0 && spreadFactor > 0f) {
                    // 相邻柱在自身原始高度基础上叠加主柱能量的衰减部分
                    // 0.85f 控制扩散强度，避免叠加后过高
                    spreadHeights[i] = mTargetHeights[i]
                            + maxTarget * spreadFactor * 0.85f;
                }

                // 限制最大高度不超过视图的95%
                spreadHeights[i] = Math.min(spreadHeights[i], getHeight() * 0.95f);
            }

            // 将扩散结果写回目标高度数组
            System.arraycopy(spreadHeights, 0, mTargetHeights, 0, BAR_COUNT);
        }
        // ==================== 强音扩散算法结束 ====================

        // ==================== xuameng 新增：最低可见高度托底 ====================
        float maxH = getHeight();
        if (maxH <= 0) maxH = 100f;

        float minVisibleHeight = maxH * MIN_VISIBLE_RATIO;

        for (int i = 0; i < BAR_COUNT; i++) {
            // 1. 先限制最大高度
            mTargetHeights[i] = Math.min(mTargetHeights[i], maxH * 0.95f);
   
            // 2. 小音量托底（防止柱子消失）
            if (mTargetHeights[i] < minVisibleHeight) {
                mTargetHeights[i] = minVisibleHeight;
            }
        }
        // ==================== 托底逻辑结束 ====================
        startAnimation();
    }

    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            // xuameng 找到当前主音柱（动画过程中最高的）
            int dominantIdx = 0;
            float maxH = 0f;
            for (int i = 0; i < BAR_COUNT; i++) {
                if (mBarHeights[i] > maxH) {
                    maxH = mBarHeights[i];
                    dominantIdx = i;
                }
            }

            for (int i = 0; i < BAR_COUNT; i++) {
                if (mTargetHeights[i] > mBarHeights[i]) {
                    // 起跳快（缓出感）：主音柱起跳更快，相邻柱稍慢
                    float boost = (i == dominantIdx) ? 0.65f : 0.60f;
                    mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * boost;
                } else {
                    // 下落慢（拖尾感）
                    mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * 0.15f;
                }
            }
            postInvalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBarHeights == null) return;
        // xuameng 静音/暂停时不画
        if (!mVisible) {
            return;
        }
        // 检查是否需要切换颜色方案
        checkColorCycleSwitch();

        final int width = getWidth();
        final int height = getHeight();
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.15f;  //xuameng 音柱间距
        final float barUnitWidth = barWidth - gap;

        // 新增：定义分段效果的最小高度阈值（例如4像素）
        final float MIN_HEIGHT_FOR_STRIPES = 20f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;

            // xuameng：改为按实际音柱高度计算颜色
            float normalizedHeight = barHeight / (height > 0 ? height : 1f);
            normalizedHeight = Math.max(0f, Math.min(1f, normalizedHeight));
            int color = getDynamicColor(normalizedHeight, colorSchemes[currentSchemeIndex]);
            mBarPaint.setColor(color);

            // 分段绘制音柱（实现"一段一段"效果）
            // 修改点：分段绘制音柱（仅当音柱高度达到最小阈值时才显示分段效果）
            if (mShowStripes && barHeight >= MIN_HEIGHT_FOR_STRIPES) {
                // 动态计算实际能显示的分段数量
                float segmentHeightWithSpacing = mSegmentSpacingFixed * 2; // 每段高度+间距
                int actualSegmentCount = (int) (barHeight / segmentHeightWithSpacing);
                actualSegmentCount = Math.min(actualSegmentCount, mSegmentCount);
                actualSegmentCount = Math.max(1, actualSegmentCount); // 至少显示1段

                // 计算实际段高度（考虑固定间距）
                float actualSegmentHeight = (barHeight - (actualSegmentCount - 1) * mSegmentSpacingFixed) / actualSegmentCount;

                // 从下往上绘制各个段
                for (int segment = 0; segment < actualSegmentCount; segment++) {
                    float segmentTop = height - (segment + 1) * (actualSegmentHeight + mSegmentSpacingFixed);
                    float segmentBottom = segmentTop + actualSegmentHeight;
                    // 确保段在音柱范围内
                    if (segmentTop >= top) {
                        canvas.drawRect(left, segmentTop, right, segmentBottom, mBarPaint);
                    }
                }
            } else {
                // 如果不显示分段效果，绘制完整音柱
                canvas.drawRect(left, top, right, height, mBarPaint);
            }
        }
    }

    // 修改调用方式
    private void checkColorCycleSwitch() {
        long now = System.currentTimeMillis();
        if (now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % colorSchemes.length;
            lastSwitchTime = now;
            // 优化刷新逻辑（不再需要currentSchemeIndex检查）
            refreshColorSchemes();
        }
    }

    /**
     * 根据振幅强度计算渐变颜色
     */
    private int getDynamicColor(float amplitude, int[] currentScheme) {
        amplitude = Math.max(0.0f, Math.min(1.0f, amplitude)); // 确保振幅在有效范围内
        if (amplitude < 0.3f) {
            return interpolateColor(amplitude / 0.3f, currentScheme[0], currentScheme[1]);
        } else {
            return interpolateColor((amplitude - 0.3f) / 0.7f, currentScheme[1], currentScheme[2]);
        }
    }

    /**
     * 颜色插值计算
     */
    private int interpolateColor(float ratio, int startColor, int endColor) {
        int alpha = (int) (Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int) (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int) (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int) (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
        }
        mVisible = false;
        postInvalidate();
    }

    public void release() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }
}
