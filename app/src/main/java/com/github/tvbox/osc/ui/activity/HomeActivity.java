package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;							//xuameng
import android.widget.ImageView;						//xuameng
import android.graphics.Color;                          //xuameng获取颜色值
import android.util.TypedValue;              //xuameng TypedValue依赖
import android.view.LayoutInflater;			//xuameng LayoutInflater依赖
import androidx.recyclerview.widget.RecyclerView;  //xuameng主页默认焦点
import com.lzy.okgo.OkGo;   //xuameng 打断加载用
import java.util.Objects;   //xuameng主页默认焦点
import com.github.tvbox.osc.util.FastClickCheckUtil;   //xuameng cache
import com.github.tvbox.osc.util.MD5;  //xuameng cache
import android.util.Log; //xuameng音乐权限
import android.os.Build; //xuameng音乐权限
import android.content.pm.PackageManager; //xuameng音乐权限
import android.provider.Settings; //xuameng音乐权限
import android.net.Uri; //xuameng音乐权限
import androidx.appcompat.app.AlertDialog; //xuameng音乐权限
import android.Manifest;  //xuameng音乐权限
import androidx.core.app.ActivityCompat;  //xuameng音乐权限
import android.content.SharedPreferences;  //xuameng音乐权限
import android.content.Context; //xuameng音乐权限

import com.github.tvbox.osc.base.App;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.github.tvbox.osc.util.FileUtils;  //xuameng 清缓存
import java.io.File;   //xuameng 清缓存

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvDate;
    private TextView tvName;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private boolean refreshEmpty = false;	//xuameng打断加载判断
    private int currentSelected = 0;
    private int sortFocused = 0;
	private int PositionXu = 0;  //xuameng 分类筛选BUG修复变色问题
    public View sortFocusView = null;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private static final int REQUEST_CODE_RECORD_AUDIO = 1001; //xuameng获取音频权限
    private static final String TAG = "PermissionHelper";//xuameng获取音频权限
    private static final int MARSHMALLOW = Build.VERSION_CODES.M;  //xuameng获取音频权限
    private static final String PREF_PERMISSION_DIALOG = "permission_prefs";   //xuameng获取音频权限
    private static final String KEY_DIALOG_SHOWN = "dialog_shown";  //xuameng获取音频权限
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            Date date = new Date();
            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        initData();
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvDate = findViewById(R.id.tvDate);
        this.tvName = findViewById(R.id.tvName);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {     //xuameng主页默认焦点
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = view.findViewById(R.id.tvTitle);
                            textView.getPaint().setFakeBoldText(false);
                            if (sortFocused == p) {
                                view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                            } else {
                                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_BBFFFFFF));
                                view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                                view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
                            }
                            textView.invalidate();
                        }
                        public final int p = position;
                    }, 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
					PositionXu = position;
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (!sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    } else if (baseLazyFragment instanceof UserFragment) {
                        showSiteSwitch();
                    }
                }
            }
        });

        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (direction == View.FOCUS_UP) {   //XUAMENG上键刷新完
                    BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                    if ((baseLazyFragment instanceof GridFragment)) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();      //XUAMENG上键刷新完
            }
        });
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
				FastClickCheckUtil.check(v);
                if(dataInitOk && jarInitOk){
				String cachePath = FileUtils.getCachePath();          //xuameng点击清空缓存
                String cspCachePath = FileUtils.getFilePath()+"/csp/";
				File cspCacheDir = new File(cspCachePath);
				File cacheDir = new File(cachePath);
				if (!cacheDir.exists() && !cspCacheDir.exists()) return;
				new Thread(() -> {
					try {
						if(cacheDir.exists())FileUtils.cleanDirectory(cacheDir);
						if(cspCacheDir.exists()){
					//		FileUtils.deleteFile(cspCacheDir);
					       FileUtils.cleanDirectory(cspCacheDir);
						}
                       // ApiConfig.get().clearJarLoader();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}).start();
                    App.showToastShort(HomeActivity.this, "缓存已清空！");
                }else {
                    jumpActivity(SettingActivity.class);		//xuameng加载慢跳转设置
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {      //xuameng长按重新加载
            @Override
            public boolean onLongClick(View v) {
                if(dataInitOk && jarInitOk){
                    Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("useCache", true);
                    intent.putExtras(bundle);
                    HomeActivity.this.startActivity(intent);
                    App.showToastShort(HomeActivity.this, "重新加载主页数据！");
                }else {
                    jumpActivity(SettingActivity.class);   //xuameng加载慢跳转设置
                }
                return true;
            }
        });
        tvDate.setOnClickListener(new View.OnClickListener() {    //xuameng点击系统时间跳转设置
            @Override
            public void onClick(View v) {
                if(dataInitOk && jarInitOk){           //xuameng MENU键显示主页源
					showSiteSwitch(); 
                }else{
					jumpActivity(SettingActivity.class);		//xuameng加载慢跳转设置 
				}
            }
        });

        tvDate.setOnLongClickListener(new View.OnLongClickListener() {      //xuameng长按重新加载
            @Override
            public boolean onLongClick(View v) {
				jumpActivity(SettingActivity.class);		//xuameng加载慢跳转设置   
                return true;
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 500);
    }

	private boolean skipNextUpdate = false;

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                showSuccess();
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true));
                } else {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
                }
                initViewPager(absXml);
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;

    private void initData() {
        refreshEmpty = false;	//xuameng打断加载判断
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home != null && home.getName() != null && !home.getName().isEmpty())
            tvName.setText(home.getName());
        if (dataInitOk && jarInitOk) {
//            showLoading();
            sourceViewModel.getSort(ApiConfig.get().getHomeSourceBean().getKey());
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            return;
        }
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!useCacheConfig) {
                                    if (Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false)) {         //xuameng直接进入直播
										jumpActivity(LivePlayActivity.class);
                                   }
									if (!ApiConfig.get().JvhuiWarning.isEmpty()){
										String JvhuiWarning = ApiConfig.get().JvhuiWarning;
                                        App.showToastShort(HomeActivity.this, (JvhuiWarning));
									}else{
                                        App.showToastShort(HomeActivity.this, "聚汇影视提示：jar加载成功！");
									}
                                }

                                initData();
                                checkMicrophonePermission();  //xuameng音频权限
                            }
                        }, 50);
                    }

                    @Override
                    public void notice(String msg) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                App.showToastShort(HomeActivity.this, msg);
                            }
                        });
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
						dataInitOk = true;
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                App.showToastShort(HomeActivity.this, "聚汇影视提示：jar加载失败！");
                                initData();
                                checkMicrophonePermission();  //xuameng音频权限
                            }
                        });
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            TipDialog dialog = null;

            @Override
            public void notice(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        App.showToastShort(HomeActivity.this, msg);
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog == null)
                            dialog = new TipDialog(HomeActivity.this, msg, "重试", "取消", new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            //dialog.hide();
											dialog.dismiss();   //xuameng显示BUG
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            //dialog.hide();
											dialog.dismiss();  //xuameng显示BUG
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            //dialog.hide();
											dialog.dismiss();  //xuameng显示BUG
                                        }
                                    });
                                }
                            });
                        if (!dialog.isShowing() && !refreshEmpty){ 
							showSuccess();  //xuameng显示BUG
                            dialog.show();
						}
                    }
                });
            }
        }, this);
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

	@SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBackPressed() {
        if(isLoading()){
            refreshEmpty();     //xuameng打断加载优化
            return;
        }

         // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
			if(!Hawk.get(HawkConfig.HOME_REC_STYLE, false)){   //xuameng首页单行
				UserFragment.homeHotVodAdapterxu.notifyDataSetChanged();
			}else{
				UserFragment.homeHotVodAdapter.notifyDataSetChanged();
			}
			return;
        } 
		
        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            exit();
            return;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
            } else {
                exit();
            }
        } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotList1.canScrollVertically(-1)) {
            // 如果 UserFragment 列表可以向上滚动，则滚动到顶部
            UserFragment.tvHotList1.scrollToPosition(0);
            this.mGridView.setSelection(0);
        } else {
            exit();
        }
    }

	 public void showExitXu(){
        App.HideToast();
        LayoutInflater inflater = getLayoutInflater();
        View customToastView = inflater.inflate(R.layout.exit_toast, null);
        ImageView imageView = customToastView.findViewById(R.id.toastImage);
        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(customToastView);
        toast.setGravity(Gravity.CENTER, 0, 0);      //xuameng 20为左右，0是上下
        toast.show();
   }

    private void exit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            //这一段借鉴来自 q群老哥 IDCardWeb
            App.HideToast();
            // 1. 清除所有Activity（增强版）
            AppManager.getInstance().finishAllActivity();
            // 2. 注销事件总线
            EventBus.getDefault().unregister(this);
            // 3. 停止服务（需确保stopServer()内部释放了所有资源）
            ControlManager.get().stopServer();
            // 4. 强制终止进程（组合方案）
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);  // 非0状态码
        } else {
            mExitTime = System.currentTimeMillis();
            showExitXu();        
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mRunnable);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null && PositionXu !=0) {   //xuameng 分类筛选BUG修复变色问题
                MovieSort.SortData sortData = sortAdapter.getItem(PositionXu);
                if (!sortData.filters.isEmpty()) {
                    showFilterIcon(sortData.filterSelectCount());
                }
          //      showFilterIcon((int) event.obj);
            }
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    if (sortFocused == 0) {
                        changeTop(false);
                    } else {
                        changeTop(true);
                    }
                }
            }
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                if(dataInitOk && jarInitOk){           //xuameng MENU键显示主页源
					showSiteSwitch(); 
                }else {
                    jumpActivity(SettingActivity.class);   //xuameng主页加载缓慢时跳转到设置页面
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {

        }
        return super.dispatchKeyEvent(event);
    }

    byte topHide = 0;

    private void changeTop(boolean hide) {
        ViewObj viewObj = new ViewObj(topLayout, (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams());
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                topHide = (byte) (hide ? 1 : 0);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        if (hide && topHide == 0) {
            animatorSet.playTogether(new Animator[]{
                    ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 10.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 0.0f))
                            }),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 50.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 1.0f))
                            }),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", new float[]{1.0f, 0.0f})});
            animatorSet.setDuration(200);
            animatorSet.start();
            return;
        }
        if (!hide && topHide == 1) {
            animatorSet.playTogether(new Animator[]{
                    ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 0.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 10.0f))
                            }),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 1.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 50.0f))
                            }),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", new float[]{0.0f, 1.0f})});
            animatorSet.setDuration(200);
            animatorSet.start();
            return;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        AppManager.getInstance().appExit(0);
        ControlManager.get().stopServer();
    }

	private SelectDialog<SourceBean> mSiteSwitchDialog;

    void showSiteSwitch() {
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (!sites.isEmpty()){
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) select = 0;
        if (mSiteSwitchDialog == null) {
            mSiteSwitchDialog = new SelectDialog<>(HomeActivity.this);
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
                ApiConfig.get().setSourceBean(value);
                Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                Bundle bundle = new Bundle();
                bundle.putBoolean("useCache", true);
                intent.putExtras(bundle);
                HomeActivity.this.startActivity(intent);
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
            App.showToastLong(HomeActivity.this, "主页暂无数据！联系许大师吧！");
		}
    }
    private void refreshEmpty(){   //xuameng打断加载优化
        refreshEmpty = true;	//xuameng打断加载判断
        OkGo.getInstance().cancelTag("loadjar");    //xuameng打断加载
        OkGo.getInstance().cancelTag("loadUrl");    //xuameng打断加载
        jarInitOk = true;
        dataInitOk = true;
        skipNextUpdate=true;
        showSuccess();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
        App.showToastShort(HomeActivity.this, "聚汇影视提示：已打断当前源加载！");
    }

    // 触发权限检查的入口方法
    public void checkMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= MARSHMALLOW) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    // 用户已拒绝过权限，显示解释弹窗
                    showPermissionDeniedDialog();
                } else {
                    // 首次请求或永久拒绝时发起标准权限请求
                    requestRecordAudioPermission();
                }
            } else {
                Log.d(TAG, "麦克风权限已授予");
            }
        } else {
            // 6.0以下版本默认视为已授权
        }
    }

    /**
     * 标准权限请求方法
     */
    private void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            new String[]{Manifest.permission.RECORD_AUDIO},
            REQUEST_CODE_RECORD_AUDIO
        );
    }

    /**
     * 权限请求结果回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, 
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            if (grantResults.length > 0 && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    // 用户勾选"不再询问"后的处理
                    showPermanentDenialDialog();
                }
            }
        }
    }

    /**
     * 权限被永久拒绝时的提示
     */
    private void showPermanentDenialDialog() {
        SharedPreferences prefs = getSharedPreferences(PREF_PERMISSION_DIALOG, MODE_PRIVATE);
    
        // 检查是否已经显示过
        if (prefs.getBoolean(KEY_DIALOG_SHOWN, false)) {
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("权限被永久禁用")
            .setMessage("聚汇影视提示您：您已永久拒绝麦克风权限，请前往设置手动开启！\n\n如点击取消后将不再提示，音频柱状图功能也将无法使用！")
            .setPositiveButton("去设置", (dialog, which) -> launchSystemSettings())
            .setNegativeButton("取消", (dialog, which) -> {
                // 用户点击取消时记录状态
                prefs.edit().putBoolean(KEY_DIALOG_SHOWN, true).apply();
            })
            .setCancelable(false)
            .show();
    }

    /**
     * 权限拒绝后的解释弹窗
     */
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("功能需要权限")
            .setMessage("聚汇影视提示您：音频柱状图功能需要访问麦克风！请授权！")
            .setPositiveButton("再次请求", (dialog, which) -> requestRecordAudioPermission())
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 跳转应用设置页面
     */
    private void launchSystemSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", getPackageName(), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "跳转设置失败: " + e.getMessage());
            // 备用方案：跳转到应用列表
            startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        }
    }
}
