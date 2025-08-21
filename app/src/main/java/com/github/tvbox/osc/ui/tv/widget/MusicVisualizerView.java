package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;

/** xuameng
 * 音乐可视化视图组件（带振幅颜色渐变）
 * 新增特性：
 * 1. 振幅越大颜色越红（黄色->橙黄->红渐变）
 * 2. 保持原有动画平滑性
 * 3. 完全兼容原有接口
 */
public class MusicVisualizerView extends View {
    // 常量定义
    private static final int MAX_AMPLITUDE = 10000;
    private static final int BAR_COUNT = 22;
    private static final int ANIMATION_DURATION = 200;
    
    private static final int[] COLOR_SPECTRUM = {
        Color.parseColor("#DBDB70"), // 黄色
        Color.parseColor("#FF8A00"), // 橙黄
        Color.parseColor("#FF0000")  // 橙红
    };

    // 绘图工具
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBarHeights = new float[BAR_COUNT];
    private final float[] mTargetHeights = new float[BAR_COUNT];
    private final float[] mAmplitudeLevels = new float[BAR_COUNT]; // 存储各柱振幅强度

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
    }

    public void updateVisualizer(byte[] fft) {
        if (fft == null || fft.length < BAR_COUNT * 2 + 2) return;

        // 修改采样策略：前1/3柱子重点采样低频，后2/3均匀采样中高频
        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                // 低频段密集采样（每2点取1）
                barIndex = 2 + i * 2;
            } else {
                // 中高频段间隔采样（每4点取1）
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }
    
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
        
                // xuameng改进的频率加权策略（三段式加权）
                float weight;
                if (i < BAR_COUNT / 4) {
                    // 超低频段(0-200Hz)衰减40%
                    weight = 0.6f;
                } else if (i < BAR_COUNT / 2) {
                    // 中低频段(200-800Hz)基准值
                    weight = 1.8f;
                } else {
                    // 高频段(800Hz+)指数增强
                    float freqFactor = (float) Math.pow(1.5, (i - BAR_COUNT / 2) / 2.0);
                    weight = 3.0f * freqFactor;
                }
            
                mTargetHeights[i] = Math.min(
                    (magnitude * getHeight() * weight) / MAX_AMPLITUDE,
                    getHeight() * 0.95f
                );
                mAmplitudeLevels[i] = Math.min(magnitude / MAX_AMPLITUDE, 1.0f);
            }
        }
        startAnimation();
    }


    private void startAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            for (int i = 0; i < BAR_COUNT; i++) {
                mBarHeights[i] += (mTargetHeights[i] - mBarHeights[i]) * fraction;
            }
            postInvalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBarHeights == null) return;

        final int width = getWidth();
        final int height = getHeight();
        final float barWidth = width / (float) BAR_COUNT;
        final float gap = barWidth * 0.2f;
        final float barUnitWidth = barWidth - gap;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * barWidth + gap / 2;
            float right = left + barUnitWidth;
            float barHeight = Math.min(mBarHeights[i], height * 0.9f);
            float top = height - barHeight;
            
            // 根据振幅强度计算颜色
            int color = getDynamicColor(mAmplitudeLevels[i]);
            mBarPaint.setColor(color);
            
            canvas.drawRect(left, top, right, height, mBarPaint);
        }
    }

    /**
     * 根据振幅强度计算渐变颜色
     */
    private int getDynamicColor(float amplitude) {
        if (amplitude < 0.3f) {
            return interpolateColor(amplitude / 0.3f, COLOR_SPECTRUM[0], COLOR_SPECTRUM[1]);
        } else {
            return interpolateColor((amplitude - 0.3f) / 0.7f, COLOR_SPECTRUM[1], COLOR_SPECTRUM[2]);
        }
    }

    /**
     * 颜色插值计算
     */
    private int interpolateColor(float ratio, int startColor, int endColor) {
        int alpha = (int)(Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int)(Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int)(Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int)(Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mBarHeights[i] = 0;
            mTargetHeights[i] = 0;
            mAmplitudeLevels[i] = 0;
        }
        postInvalidate();
    }

    public void release() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }
        reset();
    }

    public void onRawDataReceived(byte[] rawData) {
        if (rawData == null) return;
        
        byte[] simulatedFFT = new byte[66];
        System.arraycopy(rawData, 0, simulatedFFT, 2, 
            Math.min(rawData.length, simulatedFFT.length - 2));
        updateVisualizer(simulatedFFT);
    }
}
