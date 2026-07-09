package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.crash.CrashLogUtil;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.util.ScreenUtils;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.io.File;
import java.io.FileWriter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;

/**
 * xuameng
 * 全局崩溃捕获 - 修复日志过长卡死问题
 * @version 2.0.0
 */
public class CrashActivity extends BaseActivity {

    private TextView tvLog;
    private TextView tvRestart;
    private TextView tvShare;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private String crashLog = "";
    private static final int MAX_DISPLAY_LINES = 50;  //最多50行
    private static final String LOG_FILE_NAME = "crash_log.txt";

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_crash;
    }

    @Override
    protected void init() {
        tvLog = findViewById(R.id.tvCrashLog);
        tvRestart = findViewById(R.id.tvRestart);
        tvShare = findViewById(R.id.tvShare);
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);

        // 显示加载中状态
        progressBar.setVisibility(View.VISIBLE);
        tvLog.setText("正在加载崩溃日志...");
        
        // 隐藏日志复制等按钮直到日志加载完成
        tvShare.setVisibility(View.GONE);
        tvRestart.setVisibility(View.GONE);

        // 异步加载日志
        loadCrashLogAsync();

        // 设置点击监听器
        tvRestart.setOnClickListener(v -> restartApp());
        tvShare.setOnClickListener(v -> copyCrashLogToClipboard());

        // 设置按键监听器
        tvRestart.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                CrashLogUtil.deleteCrashLog(this);  //删除日志
                restartApp();
                return true;
            }
            return false;
        });

        tvShare.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                copyCrashLogToClipboard();
                return true;
            }
            return false;
        });
    }

    /**
     * 设置焦点到重启按钮
     */
    // 
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (tvRestart != null && tvRestart.getVisibility() == View.VISIBLE) {
                tvRestart.requestFocus();
            }
        }
    }

    /**
     * 异步加载崩溃日志
     */
    private void loadCrashLogAsync() {
        new Thread(() -> {
            try {
                // 获取完整崩溃日志
                crashLog = CrashLogUtil.get(CrashActivity.this);
                
                // 在主线程更新UI
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    int lineCount = getLineCount(crashLog);
                    if (lineCount > MAX_DISPLAY_LINES) {
                        // 只显示错误摘要
                        String summary = extractErrorSummary(crashLog);
                        String displayText = String.format(
                            "检测到详细崩溃日志信息（共 %d 行）\n\n" +
                            "主要崩溃日志信息：\n%s\n\n" +
                            "完整崩溃日志可通过\"复制日志\"按钮保存到系统剪切版",
                            lineCount,
                            summary
                        );
                        tvLog.setText(displayText);
                    } else {
                        // 只显示错误摘要
                        String summary = extractErrorSummary(crashLog);
                        String displayText = String.format(
                            "检测到详细崩溃日志信息（共 %d 行）\n\n" +
                            "完整崩溃日志信息：\n%s\n" +
                            "崩溃日志可通过\"复制日志\"按钮保存到系统剪切版",
                            lineCount,
                            summary
                        );
                        tvLog.setText(displayText);
                    }
                    
                    // 显示操作按钮
                    tvShare.setVisibility(View.VISIBLE);
                    tvRestart.setVisibility(View.VISIBLE);
                    
                    // 设置焦点到重启按钮
                    tvRestart.requestFocus();
                    
                    // 滚动到底部查看最新日志
                   // scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvLog.setText("加载日志失败：" + e.getMessage());
                    tvRestart.setVisibility(View.VISIBLE);
                    tvRestart.requestFocus();
                });
            }
        }).start();
    }

    /**
     * 提取错误摘要（前MAX_DISPLAY_LINES行）
     */
    private String extractErrorSummary(String fullLog) {
        if (fullLog == null || fullLog.isEmpty()) {
            return "无错误信息";
        }

        String[] lines = fullLog.split("\n", -1);
        StringBuilder summary = new StringBuilder();
        int linesToShow = Math.min(MAX_DISPLAY_LINES, lines.length);

        for (int i = 0; i < linesToShow; i++) {
            summary.append(lines[i]).append("\n");
        }

        // 删除最后一个多余的换行
        if (summary.length() > 0) {
            summary.deleteCharAt(summary.length() - 1);
        }

        return summary.toString();
    }

    /**
     * 复制到剪切版并备到到 jvhuiys_backup目录中
     */
    private void copyCrashLogToClipboard() {
        if (crashLog == null || crashLog.isEmpty()) {
            App.showToastShort(CrashActivity.this, "没有可复制的日志");
            return;
        }

        if (XXPermissions.isGranted(CrashActivity.this, Permission.Group.STORAGE)) {
            doCopyAndSave();
        } else {
            XXPermissions.with(CrashActivity.this)
                    .permission(Permission.Group.STORAGE)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                doCopyAndSave();
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                App.showToastShort(CrashActivity.this, "获取存储权限失败,请在系统设置中开启！");
                                XXPermissions.startPermissionActivity(CrashActivity.this, permissions);
                                doCopyAndSave();
                            } else {
                                App.showToastShort(CrashActivity.this, "获取存储权限失败！");
                                doCopyAndSave();
                            }
                        }
                    });
                }
    }

    /**
     * 一定执行：复制崩溃日志到剪贴板
     * 仅在有权限时：异步保存到 jvhuiys_backup 目录
     */
    private void doCopyAndSave() {
        // ===== 制到剪贴板（主线程）=====
        boolean copySuccess = copyToClipboard();
        if (!copySuccess) {
            return; // 复制失败就不再继续
        }

        // =====  判断是否有权限再保存文件 =====
        if (XXPermissions.isGranted(CrashActivity.this, Permission.Group.STORAGE)) {
            saveCrashLogToFileAsync();
        }
    }

    /**
     * 复制崩溃日志到系统剪贴板
     * @return 是否成功
     */
    private boolean copyToClipboard() {
        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            android.content.ClipData clip =
                    android.content.ClipData.newPlainText("Crash Log", crashLog);

            clipboard.setPrimaryClip(clip);
            App.showToastShort(CrashActivity.this, "崩溃日志已复制到剪切板");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            App.showToastShort(CrashActivity.this, "崩溃日志复制失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 异步保存崩溃日志到 jvhuiys_backup 目录
     * 前提：已获得存储权限
     */
    private void saveCrashLogToFileAsync() {
        //异步保存到jvhuiys_backup目录
        new Thread(() -> {
            try {
                File root = Environment.getExternalStorageDirectory();
                File dir = new File(root, "jvhuiys_backup");

                if (!dir.exists()) {
                    dir.mkdirs();
                }

                // 按时间生成文件名
                String time = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd_HH-mm",
                        java.util.Locale.getDefault()
                ).format(new java.util.Date());

                File logFile = new File(dir, "jvhuiys_crash_" + time + ".txt");

                FileWriter writer = new FileWriter(logFile, false);
                writer.write(crashLog);
                writer.flush();
                writer.close();

                runOnUiThread(() ->
                        App.showToastShort(
                                CrashActivity.this,
                                "日志已复制到剪切版并保存到：\n" + logFile.getAbsolutePath()
                        )
                );

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        App.showToastShort(
                                CrashActivity.this,
                                "日志保存失败：请先到设置中获取存储权限" + e.getMessage()
                        )
                );
            }
        }).start();
    }

    /**
     * 判断行数
     */
    private int getLineCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\n").length;
    }

    /**
     * 重启应用
     */
    private void restartApp() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        
        // 强制停止当前进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    /**
     * 返回键
     */
    @Override
    public void onBackPressed() {
        if (ScreenUtils.isTv(this)){
            if (tvRestart != null
                    && tvRestart.getVisibility() == View.VISIBLE
                    && !tvRestart.isFocused()) {
                    tvRestart.requestFocus();
                return;
            }
        }
        CrashLogUtil.deleteCrashLog(this);  //删除日志
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

}
