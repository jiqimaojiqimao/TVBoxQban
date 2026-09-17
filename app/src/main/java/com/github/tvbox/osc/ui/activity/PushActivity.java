package com.github.tvbox.osc.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.activity.TransferActivity;
import com.github.tvbox.osc.receiver.PushReceiver;
import com.github.tvbox.osc.util.ScreenUtils;
import com.github.tvbox.osc.util.FastClickCheckUtil;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * @author xuameng
 * @date :2026/9/16
 * @description: 加传输文件
 */

public class PushActivity extends BaseActivity {
    private ImageView ivQRCode;
    private TextView tvAddress;
    private TextView tvTransfer;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_push;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void initView() {
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        tvTransfer = findViewById(R.id.tvTransfer);
      //  tvTransfer.setVisibility(ScreenUtils.isTv(this) ? View.VISIBLE : View.GONE);
        refreshQRCode();
        findViewById(R.id.pushLocal).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                try {
                    ClipboardManager manager = (ClipboardManager) PushActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (manager != null) {
                        if (manager.hasPrimaryClip() && manager.getPrimaryClip() != null && manager.getPrimaryClip().getItemCount() > 0) {
                            ClipData.Item addedText = manager.getPrimaryClip().getItemAt(0);
                            CharSequence text = addedText.coerceToText(PushActivity.this);
                            if (text != null) PushReceiver.send(PushActivity.this, text.toString().trim());
                        }
                    }
                } catch (Throwable th) {

                }
            }
        });

        tvTransfer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(TransferActivity.class);
            }
        });

    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("手机/电脑扫描上方二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address+"push.html", AutoSizeUtils.mm2px(this, 300), AutoSizeUtils.mm2px(this, 300), 4));  //xuameng push.html直播跳转
    }

    private void initData() {

    }
}
