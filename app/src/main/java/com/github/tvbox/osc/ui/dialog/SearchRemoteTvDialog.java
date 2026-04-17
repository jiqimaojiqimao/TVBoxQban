package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.content.DialogInterface;  // 关闭监听器
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.List;

/**
 * xuameng
 * 远端聚汇影视全面修改
 * 直接显示上次列表，可清空列表，重新搜索等
 * 
 * @version 2.0.0
 */
public class SearchRemoteTvDialog extends BaseDialog {

    private SelectDialogAdapter<String> mSelectAdapter;
    private static final List<String> remoteTvHostList = new ArrayList<>();
    private boolean foundRemoteTv = false;
    private LoadService mLoadService;
    private boolean isSearching = false;
    private volatile boolean isCancelled = false;

    public SearchRemoteTvDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_search_remotetv);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTip("搜索附近聚汇影视");

        // 添加对话框关闭监听器
        setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (isSearching){
                    // 在对话框关闭时停止搜索
                    isCancelled = true;
                    isSearching = false;
                    App.showToastShort(getContext(), "搜索已终止");
                }
            }
        });

        // 重新搜索
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            if (isSearching) {
                App.showToastShort(getContext(), "搜索中，请稍候");
                return;
            }
            setTip("搜索附近聚汇影视");
            startSearch();
        });

        // 清空列表
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            String lastTvBox = Hawk.get(HawkConfig.REMOTE_TVBOX, null);
            List<String> cache = Hawk.get(HawkConfig.REMOTE_TV_LIST, null);
            boolean isLastTvBoxEmpty = (lastTvBox == null || lastTvBox.isEmpty());
            boolean isCacheEmpty = (cache == null || cache.isEmpty());
            if (isLastTvBoxEmpty && isCacheEmpty) {
                App.showToastShort(getContext(), "列表为空无需清理");
                return;
            }
            isCancelled = true;
            isSearching = false;
            foundRemoteTv = false;
            remoteTvHostList.clear();
            Hawk.delete(HawkConfig.REMOTE_TV_LIST);
            Hawk.delete(HawkConfig.REMOTE_TVBOX);
            // ✅ 添加：清除 PlayerHelper 缓存
            PlayerHelper.clearRemoteTvBoxCache(); 
            setTip("搜索附近聚汇影视");
            // 关键修改：清空适配器数据并刷新UI
            if (mSelectAdapter != null) {
                mSelectAdapter.setData(new ArrayList<>(), 0);
            }
            showSuccess();
            App.showToastShort(getContext(), "列表已清空");
        });
    }

    @Override
    public void show() {
        super.show();
        List<String> cache = Hawk.get(HawkConfig.REMOTE_TV_LIST, null);
        if (cache != null && !cache.isEmpty()) {
            remoteTvHostList.clear();
            remoteTvHostList.addAll(cache);
            setTip("选择附近聚汇影视");
            showRemoteTvList();
        } else {
            setTip("搜索附近聚汇影视");
        }
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
        setLoadSir(findViewById(R.id.list));
    }

    private void startSearch() {
        if (isSearching) {
            return;
        }
        showLoading();
        isSearching = true;
        isCancelled = false;
        foundRemoteTv = false;
        remoteTvHostList.clear();
        Hawk.delete(HawkConfig.REMOTE_TV_LIST);
        Hawk.delete(HawkConfig.REMOTE_TVBOX);
        // ✅ 添加：清除 PlayerHelper 缓存
        PlayerHelper.clearRemoteTvBoxCache();
        if (mSelectAdapter != null) {
            mSelectAdapter.setData(new ArrayList<>(), 0);
        }
        RemoteTVBox tv = new RemoteTVBox();

        new Thread(() -> {
            RemoteTVBox.searchAvalible(tv.new Callback() {
                @Override
                public void found(String viewHost, String deviceName, boolean end) {  //增加deviceName设备
                    if (isCancelled) {
                        return;
                    }
                    // 把 "设备名 (IP:端口)" 组合成一个字符串存入列表
                    String displayItem = deviceName + "(" + viewHost + ")";
                    remoteTvHostList.add(displayItem);
                    if (end) {
                        finishSearch(true);
                    }
                }

                @Override
                public void fail(boolean all, boolean end) {
                    if (isCancelled) {  // 在fail方法中也添加检查
                        return;
                    }
                    if (end) {
                        finishSearch(!all);
                    }
                }
            });
        }).start();
    }

    private void finishSearch(boolean found) {
        if (isCancelled) {  // 在finishSearch方法中也添加检查
            return;
        }
        isSearching = false;
        foundRemoteTv = found;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (found && !remoteTvHostList.isEmpty()) {
                // ✅ 保存到 Hawk
                Hawk.put(HawkConfig.REMOTE_TV_LIST, new ArrayList<>(remoteTvHostList));
                // ✅ 关键：默认选中第一个
                if (mSelectAdapter != null) {
                    PlayerHelper.clearRemoteTvBoxCache(); //xuameng 首次获取必须同步
                    RemoteTVBox.setAvalible(remoteTvHostList.get(0));
                    PlayerHelper.clearRemoteTvBoxCache();
                }
                setTip("选择附近聚汇影视");
                showRemoteTvList();
            } else {
                setTip("搜索附近聚汇影视");
                showEmpty();
                App.showToastShort(getContext(), "未找到附近聚汇影视！");
            }
        });
    }

    private void showRemoteTvList() {
        showSuccess();
        TvRecyclerView list = findViewById(R.id.list);
        if (mSelectAdapter == null) {
            mSelectAdapter = new SelectDialogAdapter<>(new SelectDialogAdapter.SelectDialogInterface<String>() {
                @Override
                public void click(String value, int pos) {
                    RemoteTVBox.setAvalible(value);
                    App.showToastShort(getContext(), "已选择：" + value);
                }

                @Override
                public String getDisplay(String val) {
                    return val;
                }
            }, new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                    return oldItem.equals(newItem);
                }
            });
            list.setAdapter(mSelectAdapter);
        }
        mSelectAdapter.setData(remoteTvHostList, getLastSelectedIndex());
        list.setSelectedPosition(getLastSelectedIndex());
        if (getLastSelectedIndex() < 10) {
            list.setSelection(getLastSelectedIndex());
        } else {
            list.post(new Runnable() {
                @Override
                public void run() {
                    list.smoothScrollToPosition(getLastSelectedIndex());
                    list.setSelectionWithSmooth(getLastSelectedIndex());
                }
            });
        }
    }

    private int getLastSelectedIndex() {
        String last = Hawk.get(HawkConfig.REMOTE_TVBOX, null);
        if (last == null || remoteTvHostList == null) {
            return 0;
        }
        return remoteTvHostList.indexOf(last);
    }

    protected void setLoadSir(View view) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view, new Callback.OnReloadListener() {
                @Override
                public void onReload(View v) {
                }
            });
        }
    }

    public void showLoading() {
        if (mLoadService != null) {
            mLoadService.showCallback(LoadingCallback.class);
        }
    }

    public void showEmpty() {
        if (mLoadService != null) {
            mLoadService.showCallback(EmptyCallback.class);
        }
    }

    public void showSuccess() {
        if (mLoadService != null) {
            mLoadService.showSuccess();
        }
    }
}
