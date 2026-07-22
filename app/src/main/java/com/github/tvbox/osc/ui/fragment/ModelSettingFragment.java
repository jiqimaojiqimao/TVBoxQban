package com.github.tvbox.osc.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.activity.LocalFileActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.SearchRemoteTvDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.XWalkInitDialog;      //xuamengXWalk
import com.github.tvbox.osc.ui.dialog.ResetDialog;  //xuameng重置
import com.owen.tvrecyclerview.widget.TvRecyclerView;  //xuameng优化首页数据源列表
import com.owen.tvrecyclerview.widget.V7GridLayoutManager; //xuameng优化首页数据源列表
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager; //xuameng优化首页数据源列表
import androidx.constraintlayout.widget.ConstraintLayout;  //xuameng优化首页数据源列表
import android.view.ViewGroup;   //xuameng优化首页数据源列表
import me.jessyan.autosize.utils.AutoSizeUtils;  //xuameng优化首页数据源列表
import com.github.tvbox.osc.util.DefaultConfig;  //xuameng长按许大师制作重启APP
import com.github.tvbox.osc.base.App;  //xuameng showtoast
import android.util.Pair;  //xuameng exo解码用
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import com.github.tvbox.osc.ui.dialog.DanmuApiDialog;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.api.DanmakuApi;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private static final int REQUEST_LOCAL_CONFIG = 1001;
    private TextView tvDebugOpen;
    private TextView tvMediaCodec;
    private TextView tvParseWebView;
    private TextView tvPlay;
    private TextView tvRender;
    private TextView tvScale;
    private TextView tvApi;
    private TextView tvHomeApi;
    private TextView tvHomeDefaultShow;       //xuameng直接进入直播
    private TextView tvm3u8AdText;  //xuameng去广告
    private TextView tvShowMusicZb;  //xuameng 直播动画
    private TextView tvShowMusicDb;  //xuameng 点播动画
    private TextView tvExodecode;  //xuameng Exo解码方式
    private TextView tvSwitchDecode;  //xuameng解码切换
    private TextView tvSwitchPlayer;  //xuameng播放器切换
    private TextView tvDns;
    private TextView tvHomeRec;
    private TextView tvHistoryNum;
    private TextView tvSearchView;
    private TextView tvShowPreviewText;
    private TextView tvFastSearchText;
    private TextView tvRecStyleText;
    private TextView tvIjkCachePlay;
    private SelectDialog<SourceBean> mSiteSwitchDialog;  //xuameng点播源切换
    private TextView tvApiLine;   //xuameng 多仓
    private View llApi;  //xuameng 多仓
    private View llApiLine; //xuameng 多仓
    private ApiDialog apiDialog;
    private boolean selectLocalLive;  //xuameng 本地配置
    private TextView tvDanmuOpenText; //xuameng 弹幕
    private TextView tvDanmuApiText;  //xuameng 弹幕


    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment().setArguments();
    }

    public ModelSettingFragment setArguments() {
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_model;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        tvFastSearchText = findViewById(R.id.showFastSearchText);
        tvm3u8AdText = findViewById(R.id.m3u8AdText);    //xuameng去广告
        tvSwitchDecode = findViewById(R.id.tvSwitchDecode);    //解码切换
        tvSwitchPlayer = findViewById(R.id.tvSwitchPlayer);    //播放器切换
        tvShowMusicZb = findViewById(R.id.zbmusictext);    //xuameng直播动画
        tvShowMusicDb = findViewById(R.id.dbmusictext);    //xuameng点播动画
        tvExodecode = findViewById(R.id.tvexodecode);   //xuameng Exo解码方式
        tvShowMusicZb.setText(Hawk.get(HawkConfig.LIVE_MUSIC_ANIMATION, false) ? "已开启" : "已关闭"); //xuameng去广告
        tvShowMusicDb.setText(Hawk.get(HawkConfig.VOD_MUSIC_ANIMATION, false) ? "已开启" : "已关闭");
        tvExodecode.setText(Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false) ? "软解码" : "硬解码");
        tvm3u8AdText.setText(Hawk.get(HawkConfig.M3U8_PURIFY, false) ? "已开启" : "已关闭"); //xuameng去广告
        tvDanmuOpenText = findViewById(R.id.danmuOpenText);   //xuameng 弹幕
        tvDanmuOpenText.setText(DanmuHelper.isOpen() ? "已开启" : "已关闭");
        tvDanmuApiText = findViewById(R.id.danmuApiText);
        refreshDanmuApiText();  //xuameng 弹幕
        tvSwitchDecode.setText(Hawk.get(HawkConfig.VOD_SWITCHDECODE, false) ? "已开启" : "已关闭"); //xuameng解码切换
        tvSwitchPlayer.setText(Hawk.get(HawkConfig.VOD_SWITCHPLAYER, true) ? "已开启" : "已关闭"); //xuameng播放器切换
        tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
        tvRecStyleText = findViewById(R.id.showRecStyleText);
        tvRecStyleText.setText(Hawk.get(HawkConfig.HOME_REC_STYLE, false) ? "已开启" : "已关闭");
        tvShowPreviewText = findViewById(R.id.showPreviewText);
        tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "已开启" : "已关闭");
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvParseWebView = findViewById(R.id.tvParseWebView);
        tvMediaCodec = findViewById(R.id.tvMediaCodec);
        tvPlay = findViewById(R.id.tvPlay);
        tvRender = findViewById(R.id.tvRenderType);
        tvScale = findViewById(R.id.tvScaleType);
        tvApi = findViewById(R.id.tvApi);
        tvHomeApi = findViewById(R.id.tvHomeApi);
        tvDns = findViewById(R.id.tvDns);
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvHistoryNum = findViewById(R.id.tvHistoryNum);
        tvSearchView = findViewById(R.id.tvSearchView);
        tvIjkCachePlay = findViewById(R.id.tvIjkCachePlay);
        llApi = findViewById(R.id.llApi);
        llApiLine = findViewById(R.id.llApiLine);
        tvApiLine = findViewById(R.id.tvApiLine);
        tvMediaCodec.setText(Hawk.get(HawkConfig.IJK_CODEC, ""));
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        tvApi.setText(Hawk.get(HawkConfig.API_URL, ""));
        refreshApiLineText();  //xuameng 多仓
        tvDns.setText(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)));
        tvHistoryNum.setText(HistoryHelper.getHistoryNumName(Hawk.get(HawkConfig.HISTORY_NUM, 0)));
        tvSearchView.setText(getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 0)));
        tvHomeDefaultShow = findViewById(R.id.tvHomeDefaultShow);          //xuameng 直进直播
        tvHomeDefaultShow.setText(Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false) ? "已开启" : "已关闭");  //xuameng 直进直播
        tvHomeApi.setText(ApiConfig.get().getHomeSourceBean().getName());
        tvScale.setText(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0)));
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0)));
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "已开启" : "已关闭");
        findViewById(R.id.llDebug).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
                tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
            }
        });
        findViewById(R.id.llParseWebVew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
                Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem);
                tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
                if (!useSystem) {
                    App.showToastLong(getContext(), "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带");
                    XWalkInitDialog dialog = new XWalkInitDialog(mContext);
                    dialog.setOnListener(new XWalkInitDialog.OnListener() {
                        @Override
                        public void onchange() {
                        }
                    });
                    dialog.show();
                }
            }
        });
        findViewById(R.id.llBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                BackupDialog dialog = new BackupDialog(mActivity);
                dialog.show();
            }
        });
       findViewById(R.id.llAbout).setOnClickListener(new View.OnClickListener() {   //xuameng存储权限
            @Override
            public void onClick(View v) {
                if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                    App.showToastShort(getContext(), "已获得存储权限！");
                } else {
                    XXPermissions.with(getContext())
                            .permission(Permission.Group.STORAGE)
                            .request(new OnPermissionCallback() {
                                @Override
                                public void onGranted(List<String> permissions, boolean all) {
                                    if (all) {
                                        App.showToastShort(getContext(), "已获得存储权限！");
                                    }
                                }

                                @Override
                                public void onDenied(List<String> permissions, boolean never) {
                                    if (never) {
                                        App.showToastShort(getContext(), "获取存储权限失败,请在系统设置中开启！");
                                        XXPermissions.startPermissionActivity(getContext(), permissions);      //xuameng Activity去掉
                                    } else {
                                        App.showToastShort(getContext(), "获取存储权限失败！");
                                    }
                                }
                            });
                }
            }
        });
        findViewById(R.id.llWp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);  //xuameng 2秒
                if (!ApiConfig.get().wallpaper.isEmpty()){
                    HawkConfig.isGetWp = true;  //xuameng下载壁纸
                    App.showToastShort(getContext(), "壁纸更换中！");
                    OkGo.<File>get(ApiConfig.get().wallpaper).tag("wallpaperDown").execute(new FileCallback(requireActivity().getFilesDir().getAbsolutePath(), "wp") {  //xuameng增加tag以便打断下载
                        @Override
                        public void onSuccess(Response<File> response) {
                            if (HawkConfig.isGetWp){
                                String mimeType = response.headers().get("Content-Type");
                                if (mimeType != null && mimeType.startsWith("image/")) {   // 确认是图片文件
                                   ((BaseActivity) requireActivity()).changeWallpaper(true);      
                                   HawkConfig.isGetWp = false;  //xuameng下载壁纸 
                                   App.showToastShort(getContext(), "壁纸更换成功！");
                                }else{
                                   File wp = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wp");
                                   if (wp.exists()) wp.delete();
                                   ((BaseActivity) requireActivity()).changeWallpaper(true);
                                   HawkConfig.isGetWp = false;  //xuameng下载壁纸
                                   App.showToastShort(getContext(), "壁纸文件类型错误！已重置壁纸！");
                                }
                            }
                        }

                        @Override
                        public void onError(Response<File> response) {
                            HawkConfig.isGetWp = false;  //xuameng下载壁纸
                            App.showToastShort(getContext(), "壁纸更换失败！");
                            super.onError(response);
                        }

                        @Override
                        public void downloadProgress(Progress progress) {
                            super.downloadProgress(progress);
                        }
                    });
                }else{
                    App.showToastShort(getContext(), "壁纸站点未配置！");
                }
            }
        });
        findViewById(R.id.llWpRecovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                File wp = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wp");
                if (wp.exists()) wp.delete();
                ((BaseActivity) requireActivity()).changeWallpaper(true);
                App.showToastShort(getContext(), "壁纸已重置！");
            }
        });

        findViewById(R.id.llHomeApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
                if (!sites.isEmpty()){
                    int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
                    if (select < 0 || select >= sites.size()) select = 0;
                    if (mSiteSwitchDialog == null) {
                        mSiteSwitchDialog = new SelectDialog<>(mActivity);
                        TvRecyclerView tvRecyclerView = mSiteSwitchDialog.findViewById(R.id.list);
                        // 根据 sites 数量动态计算列数
                        int spanCount = (int) Math.floor(sites.size() / 20.0);
                        spanCount = Math.min(spanCount, 2);
                        tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mSiteSwitchDialog.getContext(), spanCount + 1));
                        // 设置对话框宽度
                        ConstraintLayout cl_root = mSiteSwitchDialog.findViewById(R.id.cl_root);
                        ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
                        clp.width = AutoSizeUtils.mm2px(mSiteSwitchDialog.getContext(), 380 + 200 * spanCount);
                        mSiteSwitchDialog.setTip("请选择首页数据源");
                    }
                    mSiteSwitchDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                        @Override
                        public void click(SourceBean value, int pos) {
                            mSiteSwitchDialog.dismiss();
                            SourceBean targetSource = ApiConfig.get().getHomeSourceBean(); //xuameng 上次的主页源
                            ApiConfig.get().setSourceBean(value);
                            tvHomeApi.setText(ApiConfig.get().getHomeSourceBean().getName());
                            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_PREVIOUS_HOME_SOURCE, targetSource)); //xuameng告诉HOME上次的主页源以便恢复
                            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HOME_SOURCE_CHANGE));
                        }
                        @Override
                        public String getDisplay(SourceBean val) {
                            return val.getName();
                        }
                    }, new DiffUtil.ItemCallback<SourceBean>() {
                        @Override
                        public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                            return oldItem == newItem;
                        }
                        @Override
                        public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                            return oldItem.getKey().equals(newItem.getKey());
                        }
                    }, sites, select);
                    mSiteSwitchDialog.show();
                }else {
                    App.showToastLong(getContext(), "主页暂无数据！联系许大师吧！");
                }
            }
        });
        findViewById(R.id.llDns).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);

                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择安全DNS");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        tvDns.setText(OkGoHelper.dnsHttpsList.get(pos));
                        Hawk.put(HawkConfig.DOH_URL, pos);
                        String url = OkGoHelper.getDohUrl(pos);
                        OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                        IjkMediaPlayer.toggleDotPort(pos > 0);
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                }, new DiffUtil.ItemCallback<String>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }
                }, OkGoHelper.dnsHttpsList, dohUrl);
                dialog.show();
            }
        });
        findViewById(R.id.llApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                apiDialog = new ApiDialog(mActivity);
                ApiDialog dialog = apiDialog;
                EventBus.getDefault().register(dialog);
                dialog.setOnListener(new ApiDialog.OnListener() {
                    @Override
                    public void onchange(String api) {
                        Hawk.put(HawkConfig.API_URL, api);
                        if (!HistoryHelper.isApiLineHistory(api)) {
                            HistoryHelper.clearApiLineList(); //xuameng 多仓
                        }
                        tvApi.setText(api);
                        refreshApiLineText(); //xuameng 多仓
                    }
                    @Override
                    public void onLocalConfig(boolean live) {
                        openLocalConfig(live);
                    }
                });
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ((BaseActivity) mActivity).hideSysBar();
                        EventBus.getDefault().unregister(dialog);
                        apiDialog = null;
                    }
                });
                dialog.show();
            }
        });

        findViewById(R.id.llApiLine).setOnClickListener(new View.OnClickListener() {   //xuameng 多仓
            @Override
            public void onClick(View v) {
                ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
                if (apiLines.isEmpty()) {
                    App.showToastShort(getContext(), "线路列表为空！");
                    return;
                }
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                for (int i = 0; i < apiLines.size(); i++) {
                    if (current.equals(HistoryHelper.getApiLineUrl(apiLines.get(i)))) {
                        idx = i;
                        break;
                    }
                }
                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("线路选择");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        String newApi = HistoryHelper.getApiLineUrl(value);
                        String oldApi = Hawk.get(HawkConfig.API_URL, "");
                        if (newApi.isEmpty()) {
                            return;
                        }
                        Hawk.put(HawkConfig.API_URL, newApi);
                        Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                        HistoryHelper.setApiHistory(newApi);
                        HistoryHelper.setLiveApiHistory(newApi);
                        tvApi.setText(newApi);
                        refreshApiLineText(); //xuameng 多仓
                        dialog.dismiss();
                    }

                    @Override
                    public String getDisplay(String val) {
                        return HistoryHelper.getApiLineName(val);
                    }
                }, SelectDialogAdapter.stringDiff, apiLines, idx);
                dialog.show();
            }
        });

        findViewById(R.id.llMediaCodec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<IJKCode> ijkCodes = ApiConfig.get().getIjkCodes();
                if (ijkCodes == null || ijkCodes.size() == 0)
                    return;
                FastClickCheckUtil.check(v);

                int defaultPos = 0;
                String ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "");
                for (int j = 0; j < ijkCodes.size(); j++) {
                    if (ijkSel.equals(ijkCodes.get(j).getName())) {
                        defaultPos = j;
                        break;
                    }
                }

                SelectDialog<IJKCode> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择IJK解码");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<IJKCode>() {
                    @Override
                    public void click(IJKCode value, int pos) {
                        value.selected(true);
                        tvMediaCodec.setText(value.getName());
                    }

                    @Override
                    public String getDisplay(IJKCode val) {
                        return val.getName();
                    }
                }, new DiffUtil.ItemCallback<IJKCode>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem == newItem;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem.getName().equals(newItem.getName());
                    }
                }, ijkCodes, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llScale).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, 0);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                players.add(4);
                players.add(5);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认画面缩放");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_SCALE, value);
                        tvScale.setText(PlayerHelper.getScaleName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getScaleName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, players, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
                int defaultPos = 0;
                ArrayList<Integer> players = PlayerHelper.getExistPlayerTypes();
                ArrayList<Integer> renders = new ArrayList<>();
                for(int p = 0; p<players.size(); p++) {
                    renders.add(p);
                    if (players.get(p) == playerType) {
                        defaultPos = p;
                    }
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认播放器");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Integer thisPlayerType = players.get(pos);
                        Hawk.put(HawkConfig.PLAY_TYPE, thisPlayerType);
                        tvPlay.setText(PlayerHelper.getPlayerName(thisPlayerType));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        Integer playerType = players.get(val);
                        return PlayerHelper.getPlayerName(playerType);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llRender).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0);
                ArrayList<Integer> renders = new ArrayList<>();
                renders.add(0);
                renders.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认渲染方式");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_RENDER, value);
                        tvRender.setText(PlayerHelper.getRenderName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getRenderName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llHomeRec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择首页列表数据");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_REC, value);
                        tvHomeRec.setText(getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getHomeRecName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llSearchView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.SEARCH_VIEW, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择搜索视图");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.SEARCH_VIEW, value);
                        tvSearchView.setText(getSearchView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getSearchView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });

        findViewById(R.id.llHomeLive).setOnClickListener(new View.OnClickListener() {       //xuameng
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.HOME_DEFAULT_SHOW, !Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false));
                tvHomeDefaultShow.setText(Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, true) ? "已开启" : "已关闭");
            }
        });

        // xuameng重置
        findViewById(R.id.llReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                ResetDialog dialog = new ResetDialog(mActivity);
                dialog.show();
            }
        });

        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };

        findViewById(R.id.showPreview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.SHOW_PREVIEW, !Hawk.get(HawkConfig.SHOW_PREVIEW, true));
                tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.llHistoryNum).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HISTORY_NUM, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                types.add(3);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("保留历史记录数量");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HISTORY_NUM, value);
                        tvHistoryNum.setText(HistoryHelper.getHistoryNumName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return HistoryHelper.getHistoryNumName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.showFastSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.FAST_SEARCH_MODE, !Hawk.get(HawkConfig.FAST_SEARCH_MODE, false));
                tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.m3u8Ad).setOnClickListener(new View.OnClickListener() {   //xuameng广告过滤
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean is_purify=Hawk.get(HawkConfig.M3U8_PURIFY, false);
                Hawk.put(HawkConfig.M3U8_PURIFY, !is_purify);
                tvm3u8AdText.setText(!is_purify ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.danmuOpen).setOnClickListener(new View.OnClickListener() {  //xuameng 弹幕
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean open = !DanmuHelper.isOpen();
                DanmuHelper.setOpen(open);
                tvDanmuOpenText.setText(open ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.danmuApi).setOnClickListener(new View.OnClickListener() {  //xuameng 弹幕
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                DanmuApiDialog dialog = new DanmuApiDialog(mActivity);
                dialog.setOnListener(new DanmuApiDialog.OnListener() {
                    @Override
                    public void onChange(String api) {
                        refreshDanmuApiText();
                    }
                });
                dialog.show();
            }
        });
        findViewById(R.id.llMusicdb).setOnClickListener(new View.OnClickListener() {   //xuameng点播动画
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean musicdb=Hawk.get(HawkConfig.VOD_MUSIC_ANIMATION, false);
                Hawk.put(HawkConfig.VOD_MUSIC_ANIMATION, !musicdb);
                tvShowMusicDb.setText(!musicdb ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.llMusiczb).setOnClickListener(new View.OnClickListener() {   //xuameng点播动画
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean is_musiczb=Hawk.get(HawkConfig.LIVE_MUSIC_ANIMATION, false);
                Hawk.put(HawkConfig.LIVE_MUSIC_ANIMATION, !is_musiczb);
                tvShowMusicZb.setText(!is_musiczb ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.llExodecode).setOnClickListener(new View.OnClickListener() {  //xuamengEXO解码
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                // xuameng获取解码模式选项（模拟数据源）
                List<Pair<String, Boolean>> decodeModes = new ArrayList<>();
                decodeModes.add(new Pair<>("软解码", true));
                decodeModes.add(new Pair<>("硬解码", false));
                // xuameng当前选中项
                int defaultPos = 0;
                for (int i = 0; i < decodeModes.size(); i++) {
                    if (Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false) == decodeModes.get(i).second) {
                        defaultPos = i;
                        break;
                    }
                }
                // xuameng创建选择对话框
                SelectDialog<Pair<String, Boolean>> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择EXO解码");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Pair<String, Boolean>>() {
                    @Override
                    public void click(Pair<String, Boolean> value, int pos) {
                        tvExodecode.setText(value.first);
                        Hawk.put(HawkConfig.EXO_PLAYER_DECODE, value.second);
                    }

                    @Override
                    public String getDisplay(Pair<String, Boolean> val) {
                        return val.first;
                    }
                }, new DiffUtil.ItemCallback<Pair<String, Boolean>>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull Pair<String, Boolean> oldItem, @NonNull Pair<String, Boolean> newItem) {
                        return oldItem == newItem;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull Pair<String, Boolean> oldItem, @NonNull Pair<String, Boolean> newItem) {
                        return oldItem.first.equals(newItem.first);
                    }
                }, decodeModes, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llSwitchDecode).setOnClickListener(new View.OnClickListener() {   //xuameng解码切换
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean is_switchdecode=Hawk.get(HawkConfig.VOD_SWITCHDECODE, false);
                Hawk.put(HawkConfig.VOD_SWITCHDECODE, !is_switchdecode);
                tvSwitchDecode.setText(!is_switchdecode ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.llSwitchPlayer).setOnClickListener(new View.OnClickListener() {   //xuameng播放器切换
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean is_switchplayer=Hawk.get(HawkConfig.VOD_SWITCHPLAYER, true);
                Hawk.put(HawkConfig.VOD_SWITCHPLAYER, !is_switchplayer);
                tvSwitchPlayer.setText(!is_switchplayer ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.llHomeRecStyle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.HOME_REC_STYLE, !Hawk.get(HawkConfig.HOME_REC_STYLE, false));
                tvRecStyleText.setText(Hawk.get(HawkConfig.HOME_REC_STYLE, false) ? "已开启" : "已关闭");
            }
        });

        findViewById(R.id.llSearchTv).setOnClickListener(new View.OnClickListener() {   //xuameng搜索远程聚汇影视
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                SearchRemoteTvDialog dialog = new SearchRemoteTvDialog(mActivity);  //xuameng 方法全部移到SearchRemoteTvDialog中
                dialog.show();
            }
        });

        findViewById(R.id.llIjkCachePlay).setOnClickListener((view -> onClickIjkCachePlay(view)));
        findViewById(R.id.llClearCache).setOnClickListener((view -> onClickClearCache(view)));
    }

    private void onClickIjkCachePlay(View v) {
        FastClickCheckUtil.check(v);
        Hawk.put(HawkConfig.IJK_CACHE_PLAY, !Hawk.get(HawkConfig.IJK_CACHE_PLAY, false));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "已开启" : "已关闭");
    }

    private void onClickClearCache(View v) {
        FastClickCheckUtil.check(v);
        FileUtils.clearSpiderCacheFiles();
        App.showToastShort(getContext(), "缓存已清空！");
        return;
    }

    @Override
    public void onDestroyView() {
        OkGo.getInstance().cancelTag("wallpaperDown");   //xuameng打断下载
        EventBus.getDefault().unregister(this);
        SettingActivity.callback = null;
        super.onDestroyView();
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "聚汇推荐";
        } else if (type == 2) {
            return "观看历史";
        } else {
            return "聚汇热播";
        }
    }

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "缩略图";
        }
    }

    private void refreshApiLineText() {  //xuameng 多仓
        if (tvApiLine == null) return;
        ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        String current = Hawk.get(HawkConfig.API_URL, "");
        boolean showLine = HistoryHelper.isApiLineUrl(current);
        if (llApiLine != null) {
            llApiLine.setVisibility(showLine ? View.VISIBLE : View.GONE);
            int maxEms = showLine ? 11 : 22;
            tvApi.setMaxEms(maxEms);
            tvApiLine.setMaxEms(maxEms);
        }
        String lineName = "";
        if (showLine) {
            for (String apiLine : apiLines) {
                if (current.equals(HistoryHelper.getApiLineUrl(apiLine))) {
                    lineName = HistoryHelper.getApiLineName(apiLine);
                    break;
                }
            }
        }
        tvApiLine.setText(lineName);
    }

    private void openLocalConfig(boolean live) {  //xuameng 本地配置
        selectLocalLive = live;
        if (!XXPermissions.isGranted(mContext, Permission.Group.STORAGE)) {
            App.showToastShort(getContext(), "请选择文件前需要先授予存储权限！");
            XXPermissions.with(mActivity)
                    .permission(Permission.Group.STORAGE)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                App.showToastShort(getContext(), "已获得存储权限！");
                                openLocalFileActivity(selectLocalLive);
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                App.showToastShort(getContext(), "获取存储权限失败,请在系统设置中开启！");
                                XXPermissions.startPermissionActivity(mActivity, permissions);
                            } else {
                                App.showToastShort(getContext(), "获取存储权限失败！");
                            }
                        }
                    });
            return;
        }
        openLocalFileActivity(live);
    }

    private void openLocalFileActivity(boolean live) {
        Intent intent = new Intent(mContext, LocalFileActivity.class);
        intent.putExtra(LocalFileActivity.EXTRA_LIVE, live);
        startActivityForResult(intent, REQUEST_LOCAL_CONFIG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_LOCAL_CONFIG || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        String api = localConfigToApi(data.getData());
        if (api == null || api.isEmpty()) {
            App.showToastShort(getContext(), "读取本地配置失败！");
            return;
        }
        if (apiDialog != null) {
            apiDialog.setLocalApi(api, selectLocalLive);
        }
    }

    private String localConfigToApi(Uri uri) {
        String path = getPathFromUri(uri);
        if (path == null || path.isEmpty()) {
            path = copyUriToLocalConfig(uri);
        }
        if (path == null || path.isEmpty()) {
            return "";
        }
        String storageRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length()).replaceFirst("^/+", "");
        }
        path = copyUriToLocalConfig(uri);
        if (path != null && path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length()).replaceFirst("^/+", "");
        }
        return "";
    }

    private String getPathFromUri(Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
            if (DocumentsContract.isDocumentUri(mContext, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String[] split = docId.split(":");
                    if (split.length > 1 && "primary".equalsIgnoreCase(split[0])) {
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + split[1];
                    }
                }
                if ("com.android.providers.downloads.documents".equals(uri.getAuthority()) && docId.startsWith("raw:")) {
                    return docId.substring(4);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String copyUriToLocalConfig(Uri uri) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = mContext.getContentResolver().openInputStream(uri);
            if (input == null) return "";
            File dir = new File(FileUtils.getExternalCachePath(), "config");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, getDisplayName(uri));
            output = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return file.getAbsolutePath();
        } catch (Throwable th) {
            th.printStackTrace();
            return "";
        } finally {
            try {
                if (output != null) output.close();
            } catch (Throwable ignored) {
            }
            try {
                if (input != null) input.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private String getDisplayName(Uri uri) {
        String name = "local_config.json";
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String displayName = cursor.getString(index);
                    if (displayName != null && !displayName.isEmpty()) {
                        name = displayName;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name;
    }

    private void refreshDanmuApiText() {  //xuameng 弹幕
        if (tvDanmuApiText == null) return;
        if (DanmakuApi.isUseDefault()) {
            tvDanmuApiText.setText("默认");
            return;
        }
        String custom = Hawk.get(HawkConfig.DANMU_API, "");
        if (!custom.isEmpty()) {
            tvDanmuApiText.setText("自定义");
            return;
        }
        String config = ApiConfig.get().getDanmaku();
        tvDanmuApiText.setText(config.isEmpty() ? "默认" : "接口");
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {     //xuameng 远程聚汇影视变更后更新UI
        if (event.type == RefreshEvent.TYPE_REMOTE_TVBOX_CHANGE) {
            refreshPlayName();
        }
    }

    private void refreshPlayName() {   //xuameng 远程聚汇影视变更后更新UI播放器
        if (tvPlay != null) {
            int playType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
            tvPlay.setText(PlayerHelper.getPlayerName(playType));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (llApi != null) {
            llApi.post(() -> {   
                llApi.requestFocus();   //xuameng 默认焦点
            }); 
        }
    }

}
