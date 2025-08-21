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
   4. 新增：三组颜色方案
   5. 新增：按时间切换颜色方案
 */
public class MusicVisualizerView extends View {
    private static final int MAX_AMPLITUDE = 10000;
    private static final int BAR_COUNT = 22;
    private static final int ANIMATION_DURATION = 200;
    
    private static final int[][] COLOR_SCHEMES = {
        {Color.parseColor("#DBDB70"), Color.parseColor("#FF8A00"), Color.parseColor("#FF0000")}, // 黄-橙-红
        {Color.parseColor("#FF5733"), Color.parseColor("#33FF57"), Color.parseColor("#3357FF")}, // 橙红-亮绿-亮蓝
        {Color.parseColor("#FFD700"), Color.parseColor("#FF00FF"), Color.parseColor("#00CED1")},  // 金色-品红-深绿松石
        {Color.parseColor("#FF4500"), Color.parseColor("#32CD32"), Color.parseColor("#FFD700")}, // 橙红-酸橙绿-金色
        {Color.parseColor("#FF0000"), Color.parseColor("#00FF00"), Color.parseColor("#0000FF")}, // 纯红-纯绿-纯蓝
        {Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"), Color.parseColor("#FFFF00")},  // 品红-青柠-亮黄
        {Color.parseColor("#9400D3"), Color.parseColor("#00FA9A"), Color.parseColor("#FF6347")}  // 紫罗兰-春绿-番茄红
    };
  //  private static final long COLOR_CYCLE_DURATION = 2 * 60 * 1000; // 2分钟
  private static final long COLOR_CYCLE_DURATION = (long)(0.1 * 60 * 1000); // 正确写法
    private int currentSchemeIndex = 0;
    private long lastSwitchTime = 0;

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

        for (int i = 0; i < BAR_COUNT; i++) {
            int barIndex;
            if (i < BAR_COUNT / 3) {
                barIndex = 2 + i * 2;
            } else {
                barIndex = 2 + (BAR_COUNT / 3) * 2 + (i - BAR_COUNT / 3) * 4;
            }
    
            if (barIndex < fft.length - 1) {
                byte rfk = fft[barIndex];
                byte ifk = fft[barIndex + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
        
                float weight;
                if (i < BAR_COUNT / 4) {
                    weight = 0.6f;
                } else if (i < BAR_COUNT / 2) {
                    weight = 1.8f;
                } else {
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

        // 检查是否需要切换颜色方案
        checkColorCycleSwitch();

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
            
            // 根据当前颜色方案和振幅强度计算颜色
            int color = getDynamicColor(mAmplitudeLevels[i], COLOR_SCHEMES[currentSchemeIndex]);
            mBarPaint.setColor(color);
            
            canvas.drawRect(left, top, right, height, mBarPaint);
        }
    }

    // 新增：检查是否需要切换颜色方案
    private void checkColorCycleSwitch() {
        long now = System.currentTimeMillis();
        if (now - lastSwitchTime > COLOR_CYCLE_DURATION) {
            currentSchemeIndex = (currentSchemeIndex + 1) % COLOR_SCHEMES.length;
            lastSwitchTime = now;
        }
    }

    private int getDynamicColor(float amplitude, int[] currentScheme) {
        if (amplitude < 0.3f) {
            return interpolateColor(amplitude / 0.3f, currentScheme[0], currentScheme[1]);
        } else {
            return interpolateColor((amplitude - 0.3f) / 0.7f, currentScheme[1], currentScheme[2]);
        }
    }

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
