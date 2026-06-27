package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;


import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;
import android.view.inputmethod.InputMethodManager;

/**
 * @author xuameng
 * @date :2026/06/27
 * @description:   弹幕地址设置
 */

public class DanmuApiDialog extends BaseDialog {
    private EditText input;
    private OnListener listener;

    public DanmuApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_api);
        setCanceledOnTouchOutside(false);  //xuameng 禁止点击外部关闭
        input = findViewById(R.id.input);
        input.setText(Hawk.get(HawkConfig.DANMU_API, ""));
        input.setHint(getDefaultApi());
        findViewById(R.id.inputDefault).setOnClickListener(v -> saveDefault());
        findViewById(R.id.inputSubmit).setOnClickListener(v -> {
            save(input.getText().toString().trim());
            dismiss();
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 文本改变前的回调，可以留空或添加需要的逻辑
            }
    
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 文本正在改变时的回调，可以留空或添加需要的逻辑
            }
    
            @Override
            public void afterTextChanged(Editable s) {
                if (TextUtils.isEmpty(s.toString())) {
                    Hawk.put(HawkConfig.DANMU_API, "");
                    input.setHint("请输入弹幕搜索地址"); 
                }
            }
        });

    }

    private String getDefaultApi() {
        String api = DanmakuApi.getDisplayApiUrl();
        return api.isEmpty() ? "请输入弹幕搜索地址" : api;
    }

    private void save(String api) {
        DanmakuApi.setCustomApi(api);
        if (listener != null) listener.onChange(api);
        //dismiss();
    }

    private void saveDefault() {
        DanmakuApi.setUseDefault(true);
        if (listener != null){
            listener.onChange("");
        }
        input.setText("");   
        input.setHint("请输入弹幕搜索地址"); 
        input.setHint(getDefaultApi());
        //dismiss();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public interface OnListener {
        void onChange(String api);
    }
}
