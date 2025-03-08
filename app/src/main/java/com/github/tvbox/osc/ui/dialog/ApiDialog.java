package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
public class ApiDialog extends BaseDialog {
    private ImageView ivQRCode;
    private TextView tvAddress;
    private EditText inputApi;
    private EditText inputApiLive;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            inputApi.setText((String) event.obj);
            inputApiLive.setText((String) event.obj);
        }
    }

    public ApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_api);
        setCanceledOnTouchOutside(false);
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        inputApi = findViewById(R.id.input);
        inputApiLive = findViewById(R.id.inputLive);
        //内置网络接口在此处添加
        inputApi.setText(Hawk.get(HawkConfig.API_URL, ""));
        inputApiLive.setText(Hawk.get(HawkConfig.LIVE_API_URL, Hawk.get(HawkConfig.API_URL)));
        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newApi = inputApi.getText().toString().trim();
                if (!newApi.isEmpty()) {
                    ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                    if (!history.contains(newApi))
                        history.add(0, newApi);
                    if (history.size() > 30)
                        history.remove(30);
                    Hawk.put(HawkConfig.API_HISTORY, history);
//                    String newLiveApi = inputApi.getText().toString().trim();
                    if(!newApi.equals(Hawk.get(HawkConfig.API_URL, newApi))){
                        inputApiLive.setText(newApi);
                        Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                    }
                }else{          //xuameng密码不能为空
                Toast.makeText(getContext(), "请输入点播地址！", Toast.LENGTH_SHORT).show();
				return;
               }
                listener.onchange(newApi);
                dismiss();
            }
        });
        findViewById(R.id.inputSubmitLive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newApi = inputApiLive.getText().toString().trim();
                if (!newApi.isEmpty()) {
                    ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
                    if (!history.contains(newApi)) {
                        history.add(0, newApi);
                    }
                    if (history.size() > 30) {
                        history.remove(30);
                    }
                    Hawk.put(HawkConfig.LIVE_API_HISTORY, history);
                }else{          //xuameng密码不能为空
                Toast.makeText(getContext(), "请输入直播地址！", Toast.LENGTH_SHORT).show();
				return;
               }
                Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                dismiss();
            }
        });
        findViewById(R.id.apiHistory_live).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
                if (history.isEmpty()){
                    return;
                }
                String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
                int idx = 0;
                if (history.contains(current))
                    idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip("历史直播源");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        inputApiLive.setText(value);
                        Hawk.put(HawkConfig.LIVE_API_URL, value);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.LIVE_API_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });

        findViewById(R.id.apiHistory_vod).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                if (history.isEmpty())
                    return;
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                if (history.contains(current))
                    idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip("历史点播源");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        Hawk.put(HawkConfig.API_URL, value);
                        Hawk.put(HawkConfig.LIVE_API_URL, value);
						inputApi.setText(value);
                        listener.onchange(value);
                        ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
                        if (!history.contains(value)) {
                            history.add(0, value);
                        }
                        if (history.size() > 30) {
                            history.remove(30);
                        }
                        Hawk.put(HawkConfig.LIVE_API_HISTORY, history);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.API_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });
		refreshQRCode();
	}

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("手机/电脑扫描上方二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address+"api.html", AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300)));
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    OnListener listener = null;

    public interface OnListener {
        void onchange(String api);
    }
}
