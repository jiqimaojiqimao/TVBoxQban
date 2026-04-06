package com.github.tvbox.osc.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.DisplayMetrics;

import androidx.annotation.IntDef;

import com.squareup.picasso.Transformation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**xuameng
 * 最终版 RoundTransformation
 * 特点：
 * 1. 圆角场景强制 ARGB_8888（关键）
 * 2. 输入 / 输出 Bitmap 配置统一
 * 3. 使用 drawRoundRect，不依赖 clipPath
 * 4. 内存安全、兼容所有图片类型
 */
public class RoundTransformation implements Transformation {

    /** 防止极端大图 OOM，不影响清晰度 */
    private static final int MAX_BITMAP_SIZE = 1024;

    private int viewWidth, viewHeight;
    private int radius;
    private int bottomShapeHeight;
    private boolean centerCorp;
    private int roundType;
    private final String key;

    private final Matrix matrix = new Matrix();

    public RoundTransformation(String key) {
        this.key = key;
        this.viewWidth = 0;
        this.viewHeight = 0;
        this.radius = 0;
        this.bottomShapeHeight = 0;
        this.centerCorp = true;
        this.roundType = RoundType.NONE;
    }

    public RoundTransformation override(int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
        return this;
    }

    public RoundTransformation roundRadius(int radius, int roundType) {
        this.radius = radius;
        this.roundType = roundType;
        return this;
    }

    public RoundTransformation centerCorp(boolean centerCorp) {
        this.centerCorp = centerCorp;
        return this;
    }

    public RoundTransformation bottomShapeHeight(int height) {
        this.bottomShapeHeight = height;
        return this;
    }

    @Override
    public Bitmap transform(Bitmap source) {
        if (source == null) {
            return null;
        }

        try {
            int srcW = source.getWidth();
            int srcH = source.getHeight();

            // 1. 计算输出尺寸
            int outW = viewWidth <= 0 ? srcW : viewWidth;
            int outH = viewHeight <= 0 ? srcH : viewHeight;

            float scaleFactor = Math.min(
                    (float) MAX_BITMAP_SIZE / outW,
                    (float) MAX_BITMAP_SIZE / outH
            );

            if (scaleFactor < 1f) {
                outW = (int) (outW * scaleFactor);
                outH = (int) (outH * scaleFactor);
            }

            // 2. ✅ 圆角场景：强制 ARGB_8888（关键）
            Bitmap.Config outConfig = Bitmap.Config.ARGB_8888;

            Bitmap out = Bitmap.createBitmap(outW, outH, outConfig);
            Canvas canvas = new Canvas(out);
            canvas.setDrawFilter(new PaintFlagsDrawFilter(
                    0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

            // 3. ✅ 强制输入 Bitmap 为 ARGB_8888
            Bitmap workingBitmap = ensureArgb8888(source);

            // 4. Shader + Matrix
            BitmapShader shader = new BitmapShader(
                    workingBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

            matrix.reset();
            float scale;
            float dx = 0, dy = 0;

            if ((float) srcW / outW > (float) srcH / outH) {
                scale = (float) outH / srcH;
                dx = (srcW * scale - outW) / 2f;
            } else {
                scale = (float) outW / srcW;
                dy = (srcH * scale - outH) / 2f;
            }

            matrix.setScale(scale, scale);
            matrix.postTranslate(-dx, centerCorp ? -dy : 0);
            shader.setLocalMatrix(matrix);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setFilterBitmap(true);
            paint.setShader(shader);

            // 5. ✅ 稳定圆角绘制
            RectF rect = new RectF(0, 0, outW, outH);
            float radiusPx = radius;
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);

            // 6. 底部遮罩
            if (bottomShapeHeight > 0) {
                Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                maskPaint.setColor(0x99000000);
                canvas.drawRect(
                        0,
                        outH - bottomShapeHeight,
                        outW,
                        outH,
                        maskPaint
                );
            }

            // 7. 回收临时 Bitmap
            if (workingBitmap != source) {
                workingBitmap.recycle();
            }

            return out;
        } finally {
            if (source != null && !source.isRecycled()) {
                source.recycle();
            }
        }
    }

    /** ✅ 确保 Bitmap 一定是 ARGB_8888 */
    private Bitmap ensureArgb8888(Bitmap source) {
        if (source.getConfig() == Bitmap.Config.ARGB_8888) {
            return source;
        }
        Bitmap argb = source.copy(Bitmap.Config.ARGB_8888, false);
        return argb != null ? argb : source;
    }

    private float[] getCornerRadii() {
        float[] radii = new float[8];
        float r = radius;
        switch (roundType) {
            case RoundType.ALL:
                for (int i = 0; i < 8; i++) radii[i] = r;
                break;
            case RoundType.TOP:
                radii[0] = r; radii[1] = r;
                radii[2] = r; radii[3] = r;
                break;
            case RoundType.BOTTOM:
                radii[4] = r; radii[5] = r;
                radii[6] = r; radii[7] = r;
                break;
            case RoundType.LEFT:
                radii[0] = r; radii[1] = r;
                radii[6] = r; radii[7] = r;
                break;
            case RoundType.RIGHT:
                radii[2] = r; radii[3] = r;
                radii[4] = r; radii[5] = r;
                break;
            default:
        }
        return radii;
    }

    @Override
    public String key() {
        return key + "_" + viewWidth + "x" + viewHeight + "_r" + radius + "_t" + roundType;
    }

    @IntDef({RoundType.ALL, RoundType.TOP, RoundType.BOTTOM, RoundType.LEFT, RoundType.RIGHT, RoundType.NONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RoundType {
        int ALL = 0;
        int TOP = 1;
        int RIGHT = 2;
        int BOTTOM = 3;
        int LEFT = 4;
        int NONE = 5;
    }
}
