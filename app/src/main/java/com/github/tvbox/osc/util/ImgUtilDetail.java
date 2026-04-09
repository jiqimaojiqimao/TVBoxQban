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
public class ImgUtilDetail {
    private static final Map<String, Drawable> drawableCache = new HashMap<>();
    
    public static boolean isBase64Image(String picUrl) {
        return picUrl.startsWith("data:image");
    }
    
    public static int defaultWidth = 220;
    public static int defaultHeight = 280;

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

        int width = defaultWidth;
        int height = defaultHeight;
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
