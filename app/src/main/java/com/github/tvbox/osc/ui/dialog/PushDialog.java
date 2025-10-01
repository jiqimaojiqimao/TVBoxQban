package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.text.TextWatcher;  //xuameng输入监听依赖
import android.text.Editable;		//xuameng输入监听依赖
import com.github.tvbox.osc.base.App;  //xuameng toast

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PushDialog extends BaseDialog {

    private EditText etAddr;
    private EditText etPort;
    private TextView etCurrent;

    public PushDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_push);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Current IP
        etCurrent = findViewById(R.id.etCurrent);

        // Push IP / Port
        etAddr = findViewById(R.id.etAddr);

		etAddr.addTextChangedListener(new TextWatcher() {         //xuameng输入监听
		    private boolean isPointAdded = false;
 
		    @Override
		    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // 在文本改变之前不需要做任何操作
		    }
 
		    @Override
		    public void onTextChanged(CharSequence s, int start, int before, int count) {
	        // 在文本改变时也不需要做任何操作
		    }
 
		    @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
    
                // 移除连续点（保留原逻辑）
                if (text.contains("..")) {
                    int index = text.lastIndexOf(".");
                    etAddr.getEditableText().delete(index, index + 1);
                    isPointAdded = false;
                    App.showToastShort(PushDialog.this.getContext(), "聚汇影视提示：IP地址格式错误，请按222.222.222.222格式输入");
                    return;
                }
    
                // 新增正则表达式校验
                if (text.matches("^((25[0-5]|2[0-4]\\d|[0-1]?\\d?\\.){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d?))$")) {
                    // 合法IP逻辑（可扩展为自动补全等操作）
                    isPointAdded = true;
                } else {
                    // 非法IP处理（可选：自动清除或提示）
                    if (!text.isEmpty()) {
                        etAddr.getEditableText().clear();
                        App.showToastShort(PushDialog.this.getContext(), "聚汇影视提示：IP地址格式错误，请按222.222.222.222格式输入");
                    }
                }
            }
        });            //xuameng输入监听完

        etPort = findViewById(R.id.etPort);
        String cfgAddr = Hawk.get(HawkConfig.PUSH_TO_ADDR, "");
        String cfgPort = Hawk.get(HawkConfig.PUSH_TO_PORT, "");

        if (cfgAddr.isEmpty()) {
            String ipAddress = RemoteServer.getLocalIPAddress(PushDialog.this.getContext());
            int lp = ipAddress.lastIndexOf('.');
            if (lp > 0)
                etAddr.setText(ipAddress.substring(0, lp + 1));
        } else {
            etAddr.setText(cfgAddr);
        }
        if (cfgPort.isEmpty()) {
            etPort.setText("" + RemoteServer.serverPort);
        } else {
            etPort.setText(cfgPort);
        }

        // Get Current IP
        String currIP = getCurrentIP();
        etCurrent.setText(currIP);

        findViewById(R.id.btnConfirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String addr = etAddr.getText().toString();
                String port = etPort.getText().toString();
                if (addr == null || addr.length() == 0) {
                    App.showToastShort(PushDialog.this.getContext(), "请输入远端聚汇影视地址");
                    return;
                }
                if (port == null || port.length() == 0) {
                    App.showToastShort(PushDialog.this.getContext(), "请输入远端聚汇影视端口");
                    return;
                }
                Hawk.put(HawkConfig.PUSH_TO_ADDR, addr);
                Hawk.put(HawkConfig.PUSH_TO_PORT, port);
                List<String> list = new ArrayList<>();
                list.add(addr);
                list.add(port);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_VOD, list));
                PushDialog.this.dismiss();
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                App.showToastShort(PushDialog.this.getContext(), "功能还没实现！");
            }
        });
    }

    private String getCurrentIP() {
        String ipAddress = RemoteServer.getLocalIPAddress(PushDialog.this.getContext());
        return ipAddress;
    }

}
