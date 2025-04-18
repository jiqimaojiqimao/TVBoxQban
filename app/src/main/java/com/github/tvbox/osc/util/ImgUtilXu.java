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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 *base64图片
 * @version 1.0.0 <br/>
 */
public class ImgUtilXu {
    private static final Map<String, Drawable> drawableCache = new HashMap<>();
    public static boolean isBase64Image(String picUrl) {
        return picUrl.startsWith("data:image");
    }

    public static Bitmap decodeBase64ToBitmap(String base64Str) {
        // 去掉 Base64 数据的头部前缀，例如 "data:image/png;base64,"
        String base64Data = base64Str.substring(base64Str.indexOf(",") + 1);
        byte[] decodedBytes = Base64.decode(base64Data, Base64.NO_WRAP);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    public static Drawable createTextDrawable(String text) {
        if(text.isEmpty())text="聚";
        text=text.substring(0, 1);
        // 如果缓存中已存在，直接返回
        if (drawableCache.containsKey(text)) {
            return drawableCache.get(text);
        }
        int width = 240, height = 320; // 设定图片大小
        int randomColor = getRandomColor();
        float cornerRadius = AutoSizeUtils.mm2px(App.getInstance(), 8); // 圆角半径

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // 画圆角背景
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(randomColor);
        paint.setStyle(Paint.Style.FILL);
        RectF rectF = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
        paint.setColor(Color.WHITE); // 文字颜色
        paint.setTextSize(60); // 文字大小
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float x = width / 2f;
        float y = (height - fontMetrics.bottom - fontMetrics.top) / 2f;

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
}
