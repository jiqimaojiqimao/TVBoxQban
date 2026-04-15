package com.github.tvbox.osc.crash;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * xuameng
 * 全局崩溃捕获
 * @version 2.0.0 <br/>
 */
public class CrashLogUtil {

    private static final String TAG = "CrashLogUtil";
    private static final String FILE_NAME = "xuameng_crash.txt";

    public static void save(Context context, String log) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) return;

            File file = new File(dir, FILE_NAME);
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(log.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "保存崩溃日志失败", e);
        }
    }

    public static String get(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) return "无崩溃日志";

            File file = new File(dir, FILE_NAME);
            if (!file.exists()) return "无崩溃日志";

            return new String(
                    java.nio.file.Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            Log.e(TAG, "读取崩溃日志失败", e);
            return "读取崩溃日志失败";
        }
    }

    public static void deleteCrashLog(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) return;

            File file = new File(dir, FILE_NAME);
            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d(TAG, deleted ? "崩溃日志已删除" : "崩溃日志删除失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "删除崩溃日志失败", e);
        }
    }
}
