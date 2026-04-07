package com.github.tvbox.osc.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.api.ApiConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;   //xuameng 新增 base64图片圆角处理
import android.graphics.Rect; //xuameng 新增 base64图片圆角处理
import android.graphics.PorterDuff; //xuameng 新增 base64图片圆角处理
import android.graphics.PorterDuffXfermode; //xuameng 新增 base64图片圆角处理

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * xuameng
 * base64图片
 * 解决base64图片圆角变形等问题
 * @version 2.0.0 <br/>
 */
public class ImgUtilXu {
    private static final Map<String, Drawable> drawableCache = new HashMap<>();
    
    public static boolean isBase64Image(String picUrl) {
        return picUrl.startsWith("data:image");
    }
    
    public static int defaultWidth = 220;
    public static int defaultHeight = 280;

    /**
     * style 数据结构：ratio 指定宽高比（宽 / 高），type 表示风格（例如 rect、list）
     */
    public static class Style {
        public float ratio;
        public String type;

        public Style(float ratio, String type) {
            this.ratio = ratio;
            this.type = type;
        }
    }

    public static Style initStyle() {     //xuameng 改成list 不需要ratio
        String bStyle = ApiConfig.get().getHomeSourceBean().getStyle();
        if (TextUtils.isEmpty(bStyle)) {
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(bStyle);

            String type = jsonObject.getString("type");

            // list 类型不需要 ratio
            if ("list".equals(type)) {
                return new Style(0f, type);
            }

            // 非 list 才解析 ratio
            float ratio = (float) jsonObject.getDouble("ratio");
            return new Style(ratio, type);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static int spanCountByStyle(Style style, int defaultCount) {
        int spanCount = defaultCount;
        if ("rect".equals(style.type)) {
            if (style.ratio >= 1.7) {
                spanCount = 3; // 横图
            } else if (style.ratio >= 1.3) {
                spanCount = 4; // 4:3
            }
        } else if ("list".equals(style.type)) {   //xuameng list 时 首页推荐 1列
            spanCount = 1;
        }
        return spanCount;
    }

    public static int getStyleDefaultWidth(Style style) {
        // 1. style 为空，回退默认
        if (style == null) {
            return defaultWidth;
        }

        // 2. xuameng list 类型，直接给固定宽度
        if ("list".equals(style.type)) {
            return 100;
        }

        // 3. rect / 其他类型，根据 ratio 计算
        if (style.ratio < 1) {
            return 220;
        }
        if (style.ratio > 1.7) {
            return 380;
        }
        return 280;
    }

    public static Bitmap decodeBase64ToBitmap(String base64Str) {
        // 去掉 Base64 数据的头部前缀，例如 "data:image/png;base64,"
        String base64Data = base64Str.substring(base64Str.indexOf(",") + 1);
        byte[] decodedBytes = Base64.decode(base64Data, Base64.NO_WRAP);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    public static Drawable createTextDrawable(String text) {
        if (TextUtils.isEmpty(text)) {
            text = "聚";
        }
        text = text.substring(0, 1);

        if (drawableCache.containsKey(text)) {
            return drawableCache.get(text);
        }

        Style style = initStyle();

        int width;
        int height;

        if (style != null) {
            width = getStyleDefaultWidth(style);

            if ("list".equals(style.type)) {
                //  list 类型：高度等于宽度（或你希望的任何固定比例）
                height = width;
            } else {
                //  非 list，才使用 ratio   //xuameng 用normalizeRatio 强行指定ratio值防止用户乱写
                float safeRatio = normalizeRatio(style.ratio);
                height = (int) (width / safeRatio);
            }
        } else {
            //  style == null，回退默认
            width = defaultWidth;
            height = defaultHeight;
        }

        if (height <= 0) {
            height = defaultHeight;
        }

        int randomColor = getRandomColor();

        // xuameng 圆角按 bitmap 尺寸动态计算
        float cornerRadius = Math.min(width, height) * 0.05f;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(randomColor);
        paint.setStyle(Paint.Style.FILL);

        RectF rectF = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        paint.setColor(Color.WHITE);
        // xuameng基于最小边，保证文字不变形
        float baseSize = Math.min(width, height);
        paint.setTextSize(baseSize * 0.25f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float x = width / 2f;
        float y = (height - fm.bottom - fm.top) / 2f;

        canvas.drawText(text, x, y, paint);

        Drawable drawable = new BitmapDrawable(bitmap);
        drawableCache.put(text, drawable);
        return drawable;
    }

    public static int getRandomColor() {
        Random random = new Random();
        return Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    public static void clearCache() {
        drawableCache.clear();
    }

    public static Bitmap decodeBase64ToRoundBitmap(String base64Str, int radiusPx) {    //xuameng base64图片圆角处理
        if (TextUtils.isEmpty(base64Str)) {
            return null;
        }

        try {
            // 1. 去掉 data:image/xxx;base64,
            String base64 = base64Str;
            if (base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(",") + 1);
            }

            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return null;

            // 2. 创建圆角 Bitmap
            Bitmap output = Bitmap.createBitmap(
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setFilterBitmap(true);

            Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            RectF rectF = new RectF(rect);

            canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint);

            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, rect, rect, paint);

            bitmap.recycle();

            return output;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static float normalizeRatio(float ratio) {   //xuameng 强行指定ratio值防止用户乱写
        if (ratio <= 0) {
            return 1f;
        }
        if (ratio < 1f) {
            return 1f;
        }
        if (ratio > 2.0f) {
            return 1.755f;
        }
        return ratio;
    }
}
