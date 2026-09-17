package com.github.tvbox.osc.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.util.TypedValue;

import androidx.core.content.FileProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.base.App;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONObject;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

/**
 * @author xuameng
 * @date :2026/9/16
 * @description: 传输文件
 */

public class TransferActivity extends BaseActivity {
    private LinearLayout fileList;
    private String address;
    private ProgressBar progress;
    private TextView status;
    private String progressName;
    private String fileSignature;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() { @Override public void run() { loadFiles(); handler.postDelayed(this, 2000); } };
    private final Runnable progressRefresh = new Runnable() { @Override public void run() { new ProgressTask().execute(); handler.postDelayed(this, 800); } };

    @Override protected int getLayoutResID() { return R.layout.activity_transfer; }

    @Override protected void init() {
        // xuameng进入页面先判断存储权限
        checkStoragePermission();
        ImageView qr = findViewById(R.id.ivTransferQr);
        TextView addressView = findViewById(R.id.tvTransferAddress);
        fileList = findViewById(R.id.transferFileList);
        progress = findViewById(R.id.transferProgress);
        status = findViewById(R.id.tvTransferStatus);
        address = ControlManager.get().getAddress(false);
        String page = address + "transfer.html";
        addressView.setText("手机或电脑扫描二维码，或访问：\n" + page);
        qr.setImageBitmap(QRCodeGen.generateBitmap(page, 420, 420));
        loadFiles();
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); handler.post(progressRefresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); handler.removeCallbacks(progressRefresh); super.onPause(); }

    private class ProgressTask extends AsyncTask<Void, Void, String> {
        @Override protected String doInBackground(Void... ignored) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address + "transfer/progress").openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line = reader.readLine(); reader.close(); return line;
            } catch (Throwable ignoredError) { return null; }
            finally { if (connection != null) connection.disconnect(); }
        }
        @Override protected void onPostExecute(String value) {
            if (value == null) return;
            try {
                JSONObject object = new JSONObject(value);
                int percent = object.optInt("progress", 0);
                String name = object.optString("name", "");
                progressName = name;
                boolean fileReady = percent >= 100 && hasTransferFile(name);
                int displayPercent = percent >= 100 && !fileReady ? 99 : percent;
                progress.setProgress(displayPercent);
                if (displayPercent > 0 && displayPercent < 100 && name.length() > 0) status.setText("正在上传 " + name + "（" + displayPercent + "%）");
                else if (fileReady) status.setText("最近一次上传完成");
                else if (percent >= 100) status.setText("正在确认 " + (name.length() > 0 ? name : "文件"));
                else status.setText("等待网页上传");
            } catch (Throwable ignored) { }
        }
    }

    private boolean hasTransferFile(String name) {
        if (name == null || name.length() == 0) return false;
        File[] fs = RemoteServer.getTransferDirectory().listFiles();
        if (fs == null) return false;
        for (File f : fs) if (f.isFile() && name.equals(f.getName())) return true;
        return false;
    }

    private void loadFiles() {
        if (!XXPermissions.isGranted(this, Permission.Group.STORAGE)) {
            fileList.removeAllViews();
            TextView tip = new TextView(this);
            tip.setText("请先授予存储权限");
            tip.setTextColor(0xffef5350);
            fileList.addView(tip);
            return;
        }

        File dir = RemoteServer.getTransferDirectory();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            fileList.removeAllViews();
            TextView empty = new TextView(this);
            empty.setText("暂无文件");
            empty.setTextColor(0xffb0bec5);
            setTextSizeMM(empty, R.dimen.ts_22);
            empty.setPadding(15, 15, 15, 15);
            fileList.addView(empty);
            return;
        }

        File[] fs = dir.listFiles();

        // ====== 上传完成检测（提前）======
        if (progress.getProgress() > 0 && progressName != null && !progressName.isEmpty() && fs != null) {
            for (File f : fs) {
                if (f.isFile() && progressName.equals(f.getName())) {
                    progressName = "";
                    progress.setProgress(0);
                    status.setText("等待网页上传");
                    break;
                }
            }
        }

        // ====== 只统计根目录文件，忽略目录 ======
        List<File> rootFiles = new ArrayList<>();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile()) {
                    rootFiles.add(f);
                }
            }
        }

        // 文件签名（只关心根目录文件）
        StringBuilder signature = new StringBuilder();
        for (File f : rootFiles) {
            signature.append(f.getName()).append(':').append(f.length()).append(';');
        }

        if (fileSignature != null && signature.toString().equals(fileSignature)) {
            return;
        }
        fileSignature = signature.toString();

        fileList.removeAllViews();

        if (rootFiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无文件");
            empty.setTextColor(0xffb0bec5);
            setTextSizeMM(empty, R.dimen.ts_22);
            empty.setPadding(15, 15, 15, 15);
            fileList.addView(empty);
            return;
        }

        for (final File f : rootFiles) {
            addFileRow(f);
        }
    }

    //xuameng 统一用mm单位
    public static void setTextSizeMM(TextView tv, int dimenResId) {  
        TypedValue out = new TypedValue();
        tv.getResources().getValue(dimenResId, out, true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_MM, TypedValue.complexToFloat(out.data));
    }

    private void addFileRow(final File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(10, 10, 10, 10);
        TextView name = new TextView(this);
        name.setId(View.generateViewId());
        name.setText(file.getName() + "  " + readable(file.length())); name.setTextColor(0xffffffff); setTextSizeMM(name, R.dimen.ts_22); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); name.setGravity(Gravity.CENTER_VERTICAL); name.setFocusable(true); name.setClickable(true); name.setBackgroundResource(R.drawable.shape_setting_model_focus); name.setPadding(20, 5, 20, 5);
        row.addView(name, new LinearLayout.LayoutParams(0, getResources().getDimensionPixelSize(R.dimen.vs_50), 1));
        //xuameng 用TextView解决Button文字不显示
        TextView delete = new TextView(this); delete.setId(View.generateViewId()); delete.setText("删除"); setTextSizeMM(delete, R.dimen.ts_22); delete.setTextColor(0xffffffff); delete.setBackgroundResource(R.drawable.button_dialog_main); delete.setGravity(Gravity.CENTER); delete.setFocusable(true); delete.setClickable(true); delete.setPadding(10, 5, 10, 5); delete.setContentDescription("删除 " + file.getName());
	    //	Button delete = new Button(this); delete.setId(View.generateViewId()); setTextSizeMM(delete, R.dimen.ts_22); delete.setTextColor(0xffffffff); delete.setMinHeight(0); delete.setBackgroundResource(R.drawable.button_dialog_main); delete.setFocusable(true); delete.setContentDescription("删除 " + file.getName());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(getResources().getDimensionPixelSize(R.dimen.vs_80), getResources().getDimensionPixelSize(R.dimen.vs_50)); deleteParams.leftMargin = getResources().getDimensionPixelSize(R.dimen.vs_8); row.addView(delete, deleteParams);
        name.setNextFocusRightId(delete.getId()); delete.setNextFocusLeftId(name.getId());
        name.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openFile(file); } });
        delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (file.delete()) loadFiles(); } });
        fileList.addView(row);
        if (fileList.getChildCount() == 1) name.requestFocus();
    }

    private void openFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW); Uri uri;
        if (android.os.Build.VERSION.SDK_INT >= 24) { uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); } else uri = Uri.fromFile(file);
        intent.setDataAndType(uri, mimeType(file.getName()));
        try { startActivity(intent); } catch (ActivityNotFoundException e) { App.showToastShort(this, "没有找到可以打开此文件的应用！"); }
    }

    private String mimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")) return "video/*";
        if (lower.endsWith(".mp3") || lower.endsWith(".flac")) return "audio/*";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) return "image/*";
        return "*/*";
    }

    private String readable(long n) { return n < 1024 ? n + " B" : n < 1048576 ? (n / 1024) + " KB" : (n / 1048576) + " MB"; }

    /**
     * xuameng 进入页面先检查存储权限
     */
    private void checkStoragePermission() {
        if (XXPermissions.isGranted(this, Permission.Group.STORAGE)) {
            // 已有权限，什么都不做
            return;
        }

        XXPermissions.with(this)
                .permission(Permission.Group.STORAGE)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        if (all) {
                            App.showToastShort(TransferActivity.this, "已获得存储权限！");
                            // 权限拿到后，重新加载文件列表
                            loadFiles();
                        }
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        if (never) {
                            App.showToastShort(
                                    TransferActivity.this,
                                    "获取存储权限失败，请在系统设置中开启！"
                            );
                            XXPermissions.startPermissionActivity(
                                    TransferActivity.this,
                                    permissions
                            );
                        } else {
                            App.showToastShort(
                                    TransferActivity.this,
                                    "获取存储权限失败！"
                            );
                        }
                    }
                });
    }

}
