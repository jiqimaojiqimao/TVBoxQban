package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import android.view.animation.BounceInterpolator;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * @author xuameng
 * @date :2026/6/27
 * @description: 选择本地配置文件
   弹幕远程输入
 */

public class LocalFileAdapter extends BaseQuickAdapter<File, BaseViewHolder> {
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.#");
    private File parentDir;

    public LocalFileAdapter() {
        super(R.layout.item_local_file, new ArrayList<File>());
    }

    @Override
    protected void convert(BaseViewHolder helper, File item) {
        boolean isParent = parentDir != null && parentDir.equals(item);
    int iconRes;
    if (item.isDirectory()) {
        iconRes = R.drawable.ic_folder;      // 文件夹
    } else {
        iconRes = R.drawable.ic_file_xu;        // 文件
    }
    helper.setImageResource(R.id.tvType, iconRes);
        helper.setText(R.id.tvName, isParent ? ".." : item.getName());
        helper.setText(R.id.tvInfo, item.isDirectory() ? "进入" : formatSize(item.length()));

        // ====== 🔥 焦点放大动画 ======
        helper.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.BounceInterpolator())
                        .start();
            } else {
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.BounceInterpolator())
                        .start();
            }
        });
    }

    public void setParentDir(File parentDir) {
        this.parentDir = parentDir;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        double kb = size / 1024.0;
        if (kb < 1024) return SIZE_FORMAT.format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return SIZE_FORMAT.format(mb) + " MB";
        return SIZE_FORMAT.format(mb / 1024.0) + " GB";
    }
}
