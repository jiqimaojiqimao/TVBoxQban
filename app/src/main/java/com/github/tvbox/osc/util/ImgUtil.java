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
 *base64图片
 * @version 1.0.0 <br/>
 */
public class ImgUtil {
    private static final Map<String, Drawable> drawableCache = new HashMap<>();
    public static boolean isBase64Image(String picUrl) {
        return picUrl.startsWith("data:image");
    }
    public static int defaultWidth = 240;
    public static int defaultHeight = 320;

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

    public static int spanCountByStyle(Style style,int defaultCount){
        int spanCount=defaultCount;
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

    public static int getStyleDefaultWidth(Style style){
        int styleDefaultWidth = 280;
        if(style.ratio<1)styleDefaultWidth=220;
        if(style.ratio>1.7)styleDefaultWidth=380;
        return styleDefaultWidth;
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

}
