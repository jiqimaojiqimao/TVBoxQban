package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.ClipboardManager;
import android.content.ClipData;

import android.view.animation.BounceInterpolator;   //xuameng动画
import android.graphics.PointF;
import android.util.DisplayMetrics;
import androidx.recyclerview.widget.LinearSmoothScroller;

import androidx.fragment.app.FragmentContainerView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter;
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog;
import com.github.tvbox.osc.ui.fragment.PlayFragment;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.ui.dialog.DescDialog;     //xuameng 内容简介
import com.github.tvbox.osc.ui.dialog.PushDialog;    //xuameng远程推送
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.squareup.picasso.Picasso;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jessyan.autosize.utils.AutoSizeUtils;

import android.graphics.Paint;
import android.text.TextPaint;
import androidx.annotation.NonNull;
import android.graphics.Typeface;
import androidx.recyclerview.widget.RecyclerView;
import com.github.tvbox.osc.util.ImgUtilXude;   //xuameng base64图片
import com.github.tvbox.osc.util.ImgUtil;   //xuameng base64图片
/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */

public class DetailActivity extends BaseActivity {
    private LinearLayout llLayout;
    private FragmentContainerView llPlayerFragmentContainer;
    private View llPlayerFragmentContainerBlock;
    private View llPlayerPlace;
    private PlayFragment playFragment = null;
    private ImageView ivThumb;
    private TextView tvName;
    private TextView tvYear;
    private TextView tvSite;
    private TextView tvArea;
    private TextView tvLang;
    private TextView tvType;
    private TextView tvActor;
    private TextView tvDirector;
    private TextView tvPlayUrl;
    private TextView tvDes;
    private TextView tvDesc;  //xuameng 内容简介
    private TextView tvPush;   //xuameng 远程推送
    private TextView tvPlay;
    private TextView tvSort;
    private TextView tvQuickSearch;
    private TextView tvCollect;
    private TvRecyclerView mGridViewFlag;    //选源
    private TvRecyclerView mGridView;            //选集
    private TvRecyclerView mSeriesGroupView;      //xuameng多集组
    private LinearLayout mEmptyPlayList;
    private SourceViewModel sourceViewModel;
    private Movie.Video mVideo;
    private VodInfo vodInfo;
    private SeriesFlagAdapter seriesFlagAdapter;
    private BaseQuickAdapter<String, BaseViewHolder> seriesGroupAdapter;
    private SeriesAdapter seriesAdapter;  //选集列表
    private LinearSmoothScroller smoothScroller;   //xuameng 滚动
    public String vodId;
    public String sourceKey;
    public String firstsourceKey;
    boolean seriesSelect = false;
    private View seriesFlagFocus = null;
    private String preFlag="";
    private V7GridLayoutManager mGridViewLayoutMgr = null;
    private HashMap<String, String> mCheckSources = null;
    private final ArrayList<String> seriesGroupOptions = new ArrayList<>();
    private View currentSeriesGroupView;
    private int GroupCount;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_detail;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        HawkConfig.intVod = true;  //xuameng判断进入播放
        HawkConfig.saveHistory = false;  //xuameng判断存储历史记录
        initView();
        initViewModel();
        initData();
    }

    private void initView() {
        llLayout = findViewById(R.id.llLayout);
        llPlayerPlace = findViewById(R.id.previewPlayerPlace);
        llPlayerFragmentContainer = findViewById(R.id.previewPlayer);
        llPlayerFragmentContainerBlock = findViewById(R.id.previewPlayerBlock);
        ivThumb = findViewById(R.id.ivThumb);
        llPlayerPlace.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        ivThumb.setVisibility(!showPreview ? View.VISIBLE : View.GONE);
        tvName = findViewById(R.id.tvName);
        tvYear = findViewById(R.id.tvYear);
        tvSite = findViewById(R.id.tvSite);
        tvArea = findViewById(R.id.tvArea);
        tvLang = findViewById(R.id.tvLang);
        tvType = findViewById(R.id.tvType);
        tvActor = findViewById(R.id.tvActor);
        tvDirector = findViewById(R.id.tvDirector);
        tvPlayUrl = findViewById(R.id.tvPlayUrl);
        tvDes = findViewById(R.id.tvDes);
        tvDesc = findViewById(R.id.tvDesc);  //xuameng 内容简介
        tvPush = findViewById(R.id.tvPush);   //xuameng 远程推送
        tvPlay = findViewById(R.id.tvPlay);
        tvSort = findViewById(R.id.tvSort);
        tvCollect = findViewById(R.id.tvCollect);
        tvQuickSearch = findViewById(R.id.tvQuickSearch);
        mEmptyPlayList = findViewById(R.id.mEmptyPlaylist);
        mGridView = findViewById(R.id.mGridView);
    //    mGridView.setHasFixedSize(true);  //xuameng固定大小用
        mGridView.setHasFixedSize(false);
        this.mGridViewLayoutMgr = new V7GridLayoutManager(this.mContext, 6);
        mGridView.setLayoutManager(this.mGridViewLayoutMgr);
//        mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));

        smoothScroller = new LinearSmoothScroller(mContext) {
             @Override
             protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                 return 100f / displayMetrics.densityDpi;
             }
             @Override
             public PointF computeScrollVectorForPosition(int targetPosition) {
                 return mGridViewLayoutMgr.computeScrollVectorForPosition(targetPosition);
             }
         };

        seriesAdapter = new SeriesAdapter(this.mGridViewLayoutMgr);
        mGridView.setAdapter(seriesAdapter);
        mGridViewFlag = findViewById(R.id.mGridViewFlag);
        mGridViewFlag.setHasFixedSize(true);
        mGridViewFlag.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        seriesFlagAdapter = new SeriesFlagAdapter();
        mGridViewFlag.setAdapter(seriesFlagAdapter);
        preFlag = "";
        if (showPreview) {
            playFragment = new PlayFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.previewPlayer, playFragment).commit();
            getSupportFragmentManager().beginTransaction().show(playFragment).commitAllowingStateLoss();
            tvPlay.setText("全屏");
        }

        mSeriesGroupView = findViewById(R.id.mSeriesGroupView);
        mSeriesGroupView.setHasFixedSize(true);
        mSeriesGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        seriesGroupAdapter = new BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_series_group, seriesGroupOptions) {
            @Override
            protected void convert(BaseViewHolder helper, String item) {
                TextView tvSeries = helper.getView(R.id.tvSeriesGroup);
                tvSeries.setText(item);
        //        if (helper.getLayoutPosition() == getData().size() - 1) {   //xuameng 选集分组
		//			helper.itemView.setNextFocusRightId(R.id.tvPlay);
        //        }
                if (helper.getLayoutPosition() == getData().size() - 1) {
                    helper.itemView.setId(View.generateViewId());
                    helper.itemView.setNextFocusRightId(helper.itemView.getId()); 
                }else {
                    helper.itemView.setNextFocusRightId(View.NO_ID);   //xuameng不超出item
                }
                if(mGridViewFlag != null && mGridViewFlag.getVisibility() == View.VISIBLE) {
                    helper.itemView.setNextFocusUpId(R.id.mGridViewFlag);
                }else{
                    helper.itemView.setNextFocusUpId(R.id.tvPlay);
                }
                if(mGridView != null && mGridView.getVisibility() == View.VISIBLE) {
                    helper.itemView.setNextFocusDownId(R.id.mGridView);
                }else{
                    helper.itemView.setNextFocusDownId(R.id.tvPlay);
                }

            }
        };
        mSeriesGroupView.setAdapter(seriesGroupAdapter);

        //禁用播放地址焦点
        tvPlayUrl.setFocusable(false);

        llPlayerFragmentContainerBlock.setOnClickListener(v -> {
            toggleFullPreview();
        });

        tvSort.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
                if (vodInfo != null && vodInfo.seriesMap.size() > 0) {
                    vodInfo.reverseSort = !vodInfo.reverseSort;
                    if (vodInfo.reverseSort){    //XUAMENG读取记录后显示BUG
                        tvSort.setText("正序");
                    }else{
                        tvSort.setText("倒序");
                    }

                    vodInfo.reverse();
                    vodInfo.playIndex=(vodInfo.seriesMap.get(vodInfo.playFlag).size()-1)-vodInfo.playIndex;
			   
                    setSeriesGroupOptions();
                    seriesAdapter.notifyDataSetChanged();
                    isReverseXu();
                }
            }
        });

        tvSort.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override         //xuameng许大师制作焦点变大
	        public void onFocusChange(View v, boolean hasFocus){
                if (hasFocus){
                    v.animate().scaleX(1.10f).scaleY(1.10f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }else{
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
        //xuameng : end
        ivThumb.setOnClickListener(new View.OnClickListener() {         //xuameng播放窗口点击图片播放视频
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpToPlay();
            }
        });
		
        tvPush.setOnClickListener(new View.OnClickListener() {  //xuameng播放窗口中的远程推送
            @Override
            public void onClick(View v) {
                PushDialog pushDialog = new PushDialog(mContext);
                pushDialog.show();
            }
        });

       tvPush.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override         //xuameng许大师制作焦点变大
            public void onFocusChange(View v, boolean hasFocus){
                if (hasFocus){
                    v.animate().scaleX(1.10f).scaleY(1.10f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }else{
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
        //xuameng : end

        tvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if (showPreview) {
                    toggleFullPreview();
                } else {
                    jumpToPlay();
                }
            }
        });

        //xuameng : 长按播放滚动
        tvPlay.setOnLongClickListener(new View.OnLongClickListener() {       //xuameng长按播放滚动
            @Override
            public boolean onLongClick(View v) {
                FastClickCheckUtil.check(v);
                // 调用提取的方法
                switchToPlayingSourceAndScroll();   //xuameng长按播放滚动
                return true;
            }
        });

        tvPlay.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override         //xuameng许大师制作焦点变大
            public void onFocusChange(View v, boolean hasFocus){
                if (hasFocus){
                    v.animate().scaleX(1.10f).scaleY(1.10f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }else{
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
        //xuameng : end

        tvQuickSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQuickSearch();
                QuickSearchDialog quickSearchDialog = new QuickSearchDialog(DetailActivity.this);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData));
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord));
                quickSearchDialog.show();
                if (pauseRunnable != null && pauseRunnable.size() > 0) {
                    searchExecutorService = Executors.newFixedThreadPool(5);
                    for (Runnable runnable : pauseRunnable) {
                        searchExecutorService.execute(runnable);
                    }
                    pauseRunnable.clear();
                    pauseRunnable = null;
                }
                quickSearchDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        try {
                            if (searchExecutorService != null) {
                                pauseRunnable = searchExecutorService.shutdownNow();
                                searchExecutorService = null;
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }
                });
            }
        });

        tvQuickSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override         //xuameng许大师制作焦点变大
            public void onFocusChange(View v, boolean hasFocus){
                if (hasFocus){
                    v.animate().scaleX(1.10f).scaleY(1.10f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }else{
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
        //xuameng : end

        tvCollect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = tvCollect.getText().toString();
                if ("☆收藏".equals(text)) {
                    RoomDataManger.insertVodCollect(sourceKey, vodInfo);
                    App.showToastShort(DetailActivity.this, "已加入收藏夹");
                    tvCollect.setText("★收藏");
                } else {
                    RoomDataManger.deleteVodCollect(sourceKey, vodInfo);
                    App.showToastShort(DetailActivity.this, "已移除收藏夹");
                    tvCollect.setText("☆收藏");
                }
            }
        });

        tvCollect.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override         //xuameng许大师制作焦点变大
            public void onFocusChange(View v, boolean hasFocus){
                if (hasFocus){
                    v.animate().scaleX(1.10f).scaleY(1.10f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }else{
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
        //xuameng : end

        tvPlayUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //获取剪切板管理器
                ClipboardManager cm = (ClipboardManager)getSystemService(mContext.CLIPBOARD_SERVICE);
                //设置内容到剪切板
                cm.setPrimaryClip(ClipData.newPlainText(null, tvPlayUrl.getText().toString().replace("播放地址：","")));
                App.showToastShort(DetailActivity.this, "播放地址已复制！");
            }
        });

        tvDesc.setOnClickListener(new View.OnClickListener() {      //xuameng内容简介
            @Override
            public void onClick(View v) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FastClickCheckUtil.check(v);
                        DescDialog dialog = new DescDialog(mContext);
                        //  dialog.setTip("内容简介");
                        dialog.setDescribe(removeHtmlTag(mVideo.des));
                        dialog.show();
                    }
                });
            }
        });

        tvDesc.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override         //xuameng许大师制作焦点变大
            public void onFocusChange(View v, boolean hasFocus){
                if (hasFocus){
                    v.animate().scaleX(1.10f).scaleY(1.10f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }else{
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
        //xuameng : end

        tvDesc.setOnLongClickListener(new View.OnLongClickListener() {  //xuameng内容简介长按复制
            @Override
            public boolean onLongClick(View v) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FastClickCheckUtil.check(v);
                        ClipboardManager clipprofile = (ClipboardManager)getSystemService(mContext.CLIPBOARD_SERVICE);
                        String cpContent = removeHtmlTag(mVideo.des);
                        ClipData clipData = ClipData.newPlainText(null, cpContent);
                        clipprofile.setPrimaryClip(clipData);
                        App.showToastShort(DetailActivity.this, "简介内容已复制：" + cpContent);
                    }
                });
                return true;
            }
        });

        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                seriesSelect = false;
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                seriesSelect = true;
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });

        mGridViewFlag.setOnItemListener(new TvRecyclerView.OnItemListener() {
            private void refresh(View itemView, int position) {
                String newFlag = seriesFlagAdapter.getData().get(position).name;
                if (vodInfo != null) {
                    // 保存旧的显示源
                    String oldFlag = vodInfo.playFlag;
        
                    // 重要：只更新显示源，绝对不更新 currentPlayFlag
                    // currentPlayFlag 应该只在用户点击播放时更新（在 jumpToPlay() 中）
                    vodInfo.playFlag = newFlag;
        
                    // 清除旧显示源的高亮状态
                    if (vodInfo.seriesMap.containsKey(oldFlag) && vodInfo.playIndex < vodInfo.seriesMap.get(oldFlag).size()) {
                        vodInfo.seriesMap.get(oldFlag).get(vodInfo.playIndex).selected = false;
                    }
        
                    // 更新选中状态
                    for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {
                        VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                        if (flag.name.equals(oldFlag)) {
                            flag.selected = false;
                            seriesFlagAdapter.notifyItemChanged(i);
                            break;
                        }
                    }
                    VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(position);
                    flag.selected = true;
                    seriesFlagAdapter.notifyItemChanged(position);
        
                    // 重要：不再检查是否切换播放源，因为用户只是查看，不是播放
                    // 播放源的切换应该在 jumpToPlay() 中处理
                    // 刷新列表，这会根据当前显示源和播放源的关系设置正确的高亮
                    refreshList();
                }
                seriesFlagFocus = itemView;
            }

            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
//                seriesSelect = false;
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                refresh(itemView, position);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                refresh(itemView, position);
            }
        });
        seriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
                    boolean reload = false;
                    for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                        seriesAdapter.getData().get(j).selected = false;
                        seriesAdapter.notifyItemChanged(j);
                    }
                    //解决倒叙不刷新
                    if (vodInfo.playIndex != position) {
                        seriesAdapter.getData().get(position).selected = true;
                        seriesAdapter.notifyItemChanged(position);
                        vodInfo.playIndex = position;

                        reload = true;
                    }
                    //解决当前集不刷新的BUG
                    if (!preFlag.isEmpty() && !vodInfo.playFlag.equals(preFlag)) {
                        reload = true;
                    }

                    seriesAdapter.getData().get(vodInfo.playIndex).selected = true;
                    seriesAdapter.notifyItemChanged(vodInfo.playIndex);
                    //选集全屏 想选集不全屏的注释下面一行
                    if (showPreview && !fullWindows){
                        toggleFullPreview();
					}
                    if (!showPreview || reload) {
                        jumpToPlay();
                    }
                }
            }
        });

        mSeriesGroupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                TextView txtView = itemView.findViewById(R.id.tvSeriesGroup);
                txtView.setTextColor(Color.WHITE);
//                currentSeriesGroupView = null;
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                TextView txtView = itemView.findViewById(R.id.tvSeriesGroup);
                txtView.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
                if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
                    int targetPos = position * GroupCount+1;
                    customSeriesScrollPos(targetPos);
                }
                currentSeriesGroupView = itemView;
                currentSeriesGroupView.isSelected();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) { }
        });

        seriesGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                TextView newTxtView = view.findViewById(R.id.tvSeriesGroup);
                newTxtView.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
                if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
                    int targetPos =  position * GroupCount+1;
//                    mGridView.scrollToPosition(targetPos);
                    customSeriesScrollPos(targetPos);
                }
                if(currentSeriesGroupView != null) {
                    TextView txtView = currentSeriesGroupView.findViewById(R.id.tvSeriesGroup);
                    txtView.setTextColor(Color.WHITE);
                }
                currentSeriesGroupView = view;
                currentSeriesGroupView.isSelected();
            }
        });
        mGridView.setOnFocusChangeListener((view, b) -> onGridViewFocusChange(view, b));


        setLoadSir(llLayout);
    }

    //解决类似海贼王的超长动漫 焦点滚动失败的问题
    void customSeriesScrollPos(int targetPos) {   //xuameng 确保一定滚动
        // 如果 LayoutManager 为空，延迟重试
        if (mGridViewLayoutMgr == null || mGridView == null) {
            mGridView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    customSeriesScrollPos(targetPos);
                }
            }, 100); // 延迟100ms重试
            return;
        }
    
        // 正常执行滚动逻辑
        mGridViewLayoutMgr.scrollToPositionWithOffset(targetPos>10?targetPos - 10:0, 0);
        mGridView.postDelayed(() -> {
            if (mGridViewLayoutMgr != null && smoothScroller != null) {
                smoothScroller.setTargetPosition(targetPos);
                mGridViewLayoutMgr.startSmoothScroll(smoothScroller);
                mGridView.smoothScrollToPosition(targetPos);
            }
        }, 50);
    }

    private void onGridViewFocusChange(View view, boolean hasFocus) {
        if (llPlayerFragmentContainerBlock.getVisibility() != View.VISIBLE) return;
        llPlayerFragmentContainerBlock.setFocusable(!hasFocus);
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private List<Runnable> pauseRunnable = null;

    private void jumpToPlay() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            preFlag = vodInfo.playFlag;
            //更新播放地址
            setTextShow(tvPlayUrl, "播放地址：", vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).url);
            // 新增：记录当前播放的源和剧集索引
            vodInfo.currentPlayFlag = vodInfo.playFlag;
            vodInfo.currentPlayIndex = vodInfo.playIndex;
            Bundle bundle = new Bundle();
            //保存历史 - 关键修改：使用当前播放的源进行保存
            String saveSourceKey = vodInfo.currentPlayFlag != null ? vodInfo.currentPlayFlag : sourceKey;
            insertVod(saveSourceKey, vodInfo);
            // 同时保存一份到初始源，用于兼容性
            if (!saveSourceKey.equals(firstsourceKey)) {
                insertVod(firstsourceKey, vodInfo);
            }
        //   insertVod(sourceKey, vodInfo);
            bundle.putString("sourceKey", sourceKey);
//            bundle.putSerializable("VodInfo", vodInfo);
            App.getInstance().setVodInfo(vodInfo);
            if (showPreview) {
                if (previewVodInfo == null) {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(vodInfo);
                        oos.flush();
                        oos.close();
                        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                        previewVodInfo = (VodInfo) ois.readObject();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (previewVodInfo != null) {
                    previewVodInfo.playerCfg = vodInfo.playerCfg;
                    previewVodInfo.playFlag = vodInfo.playFlag;
                    previewVodInfo.playIndex = vodInfo.playIndex;
                    previewVodInfo.seriesMap = vodInfo.seriesMap;
//                    bundle.putSerializable("VodInfo", previewVodInfo);
                    App.getInstance().setVodInfo(previewVodInfo);
                }
                playFragment.setData(bundle);
            } else {
                jumpActivity(PlayActivity.class, bundle);
            }
        }
    }

    private void isReverseXu() {       //xuameng 解决倒叙剧集播放错误问题
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            preFlag = vodInfo.playFlag;
            // 新增：记录当前播放的源和剧集索引
            vodInfo.currentPlayFlag = vodInfo.playFlag;
            vodInfo.currentPlayIndex = vodInfo.playIndex;
            Bundle bundle = new Bundle();
            //保存历史 - 关键修改：使用当前播放的源进行保存
            String saveSourceKey = vodInfo.currentPlayFlag != null ? vodInfo.currentPlayFlag : sourceKey;
            insertVod(saveSourceKey, vodInfo);
            // 同时保存一份到初始源，用于兼容性
            if (!saveSourceKey.equals(firstsourceKey)) {
                insertVod(firstsourceKey, vodInfo);
            }
            bundle.putString("sourceKey", sourceKey);
            App.getInstance().setVodInfo(vodInfo);
            if (showPreview) {
                if (previewVodInfo == null) {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(vodInfo);
                        oos.flush();
                        oos.close();
                        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                        previewVodInfo = (VodInfo) ois.readObject();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (previewVodInfo != null) {
                    previewVodInfo.playerCfg = vodInfo.playerCfg;
                    previewVodInfo.playFlag = vodInfo.playFlag;
                    previewVodInfo.playIndex = vodInfo.playIndex;
                    previewVodInfo.seriesMap = vodInfo.seriesMap;
                    App.getInstance().setVodInfo(previewVodInfo);
                }  
            }
            // xuameng刷新列表，这会根据当前显示源和播放源的关系设置正确的高亮
            refreshList();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshList() {     //xuameng 不同源选集不准确及 自动播放源不对等问题 切换回正在播放的源可以恢复到正确状态等BUG
        if (vodInfo.seriesMap.get(vodInfo.playFlag).size() <= vodInfo.playIndex) {
            vodInfo.playIndex = 0;
        }

        if (vodInfo.seriesMap.get(vodInfo.playFlag) != null) {
            // 清除当前显示源的所有高亮状态
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                vodInfo.seriesMap.get(vodInfo.playFlag).get(j).selected = false;
            }
    
            // 判断当前显示源是否是正在播放的源
            if (vodInfo.playFlag.equals(vodInfo.currentPlayFlag)) {
                // 如果是正在播放的源，高亮当前播放索引
                if (vodInfo.currentPlayIndex < vodInfo.seriesMap.get(vodInfo.playFlag).size()) {
                    vodInfo.playIndex = vodInfo.currentPlayIndex;
                    vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).selected = true;
                }
            } else {
                // 如果不是正在播放的源，检查是否有对应的剧集索引
                // 简单实现：如果当前显示源有足够多的剧集，使用相同的索引
                if (vodInfo.currentPlayIndex < vodInfo.seriesMap.get(vodInfo.playFlag).size()) {
                    vodInfo.playIndex = vodInfo.currentPlayIndex;
                    vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).selected = true;
                } else {
                    // 如果没有对应的索引，不清除高亮（保持现状）
                    vodInfo.playIndex = 0;
                    // 修复：确保至少有一个剧集被高亮 第一集被高亮
                    if (vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
                        vodInfo.seriesMap.get(vodInfo.playFlag).get(0).selected = true;
                    }
                }
            }
        }

        Paint pFont = new Paint();
//        pFont.setTypeface(Typeface.DEFAULT );
        Rect rect = new Rect();

        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        int listSize = list.size();
        int w = 1;
        for(int i =0; i < listSize; ++i){
            String name = list.get(i).name;
            pFont.getTextBounds(name, 0, name.length(), rect);
            if(w < rect.width()){
                w = rect.width();
            }
        }
        w += 32;
        int screenWidth = getWindowManager().getDefaultDisplay().getWidth()/3;
        int offset = screenWidth/w;
        if(offset <=2) offset =2;
        if(offset > 6) offset =6;
        mGridViewLayoutMgr.setSpanCount(offset);
        seriesAdapter.setNewData(vodInfo.seriesMap.get(vodInfo.playFlag));

        setSeriesGroupOptions();

        customSeriesScrollPos(vodInfo.playIndex);  //xuameng 直接滚动
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setSeriesGroupOptions(){
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        int listSize = list.size();
        int offset = mGridViewLayoutMgr.getSpanCount();
        seriesGroupOptions.clear();
        GroupCount=(offset==3 || offset==6)?30:20;
        if(listSize>100 && listSize<=400)GroupCount=60;
        if(listSize>400)GroupCount=120;
        if(listSize > GroupCount) {
            mSeriesGroupView.setVisibility(View.VISIBLE);
            int remainedOptionSize = listSize % GroupCount;
            int optionSize = listSize / GroupCount;

            for(int i = 0; i < optionSize; i++) {
                if(vodInfo.reverseSort)
//                    seriesGroupOptions.add(String.format("%d - %d", i * GroupCount + GroupCount, i * GroupCount + 1));
                    seriesGroupOptions.add(String.format("%d - %d", listSize - (i * GroupCount + 1)+1, listSize - (i * GroupCount + GroupCount)+1));
                else
                    seriesGroupOptions.add(String.format("%d - %d", i * GroupCount + 1, i * GroupCount + GroupCount));
            }
            if(remainedOptionSize > 0) {
                if(vodInfo.reverseSort)
//                    seriesGroupOptions.add(String.format("%d - %d", optionSize * GroupCount + remainedOptionSize, optionSize * GroupCount + 1));
                    seriesGroupOptions.add(String.format("%d - %d", listSize - (optionSize * GroupCount + 1)+1, listSize - (optionSize * GroupCount + remainedOptionSize)+1));
                else
                    seriesGroupOptions.add(String.format("%d - %d", optionSize * GroupCount + 1, optionSize * GroupCount + remainedOptionSize));
            }
//            if(vodInfo.reverseSort) Collections.reverse(seriesGroupOptions);

            seriesGroupAdapter.notifyDataSetChanged();
        }else {
            mSeriesGroupView.setVisibility(View.GONE);
        }
    }

    private void setTextShow(TextView view, String tag, String info) {
        if (info == null || info.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(Html.fromHtml(getHtml(tag, info)));
    }

    private String removeHtmlTag(String info) {
        if (info == null)
            return "";
        return info.replaceAll("\\<.*?\\>", "").replaceAll("\\s", "");
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    showSuccess();
                    if(!TextUtils.isEmpty(absXml.msg) && !absXml.msg.equals("数据列表")){
                        App.showToastShort(DetailActivity.this, absXml.msg);
                        showEmpty();
                        return;
                    }
                    mVideo = absXml.movie.videoList.get(0);
                    mVideo.id = vodId;
                    if (TextUtils.isEmpty(mVideo.name))mVideo.name = "🥇聚汇影视";
                    vodInfo = new VodInfo();
                    if((mVideo.pic==null || mVideo.pic.isEmpty()) && !vod_picture.isEmpty()){    //xuameng某些网站图片部显示
                        mVideo.pic=vod_picture;
                    }
                    vodInfo.setVideo(mVideo);
                    vodInfo.sourceKey = mVideo.sourceKey;
                    sourceKey = mVideo.sourceKey;

                    tvName.setText(mVideo.name);
                    setTextShow(tvSite, "来源：", ApiConfig.get().getSource(firstsourceKey).getName());
                    setTextShow(tvYear, "年份：", mVideo.year == 0 ? "" : String.valueOf(mVideo.year));
                    setTextShow(tvArea, "地区：", mVideo.area);
                    setTextShow(tvLang, "语言：", mVideo.lang);
                    if (!firstsourceKey.equals(sourceKey)) {
                    	setTextShow(tvType, "类型：", "[" + ApiConfig.get().getSource(sourceKey).getName() + "] 解析");
                    } else {
                    	setTextShow(tvType, "类型：", mVideo.type);
                    }
                    setTextShow(tvActor, "演员：", mVideo.actor);
                    setTextShow(tvDirector, "导演：", mVideo.director);
                    setTextShow(tvDes, "内容简介：", removeHtmlTag(mVideo.des));
                    if (!TextUtils.isEmpty(mVideo.pic)) {
                        Picasso.get()
                                .load(DefaultConfig.checkReplaceProxy(mVideo.pic))
                                .transform(new RoundTransformation(MD5.string2MD5(mVideo.pic))
                                        .centerCorp(true)
                                        .override(AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth), AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight))
                                        .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                                .placeholder(R.drawable.img_loading_placeholder)
                                .noFade()
                            //    .error(R.drawable.img_loading_placeholder)
	                        .error(ImgUtilXude.createTextDrawable(mVideo.name))
                                .into(ivThumb);
                    } else {
                      //  ivThumb.setImageResource(R.drawable.img_loading_placeholder);
                          ivThumb.setImageDrawable(ImgUtilXude.createTextDrawable(mVideo.name));
                    }

                    if (vodInfo.seriesMap != null && vodInfo.seriesMap.size() > 0) {
                        mGridViewFlag.setVisibility(View.VISIBLE);
                        mGridView.setVisibility(View.VISIBLE);
                        tvPlay.setVisibility(View.VISIBLE);
                        tvSort.setVisibility(View.VISIBLE);  //xuameng修复无播放数据倒序空指针
                        mEmptyPlayList.setVisibility(View.GONE);

                        VodInfo vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId);
                        // xuameng读取历史记录
                        if (vodInfoRecord != null) {
                            // 优先使用历史记录中保存的当前播放源和索引
                            if (vodInfoRecord.currentPlayFlag != null && vodInfoRecord.currentPlayIndex >= 0) {
                                vodInfo.playIndex = vodInfoRecord.currentPlayIndex;
                                vodInfo.playFlag = vodInfoRecord.currentPlayFlag;
                                vodInfo.currentPlayFlag = vodInfoRecord.currentPlayFlag;
                                vodInfo.currentPlayIndex = vodInfoRecord.currentPlayIndex;
                            } else {
                                // 兼容旧版记录
                                vodInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0);
                                vodInfo.playFlag = vodInfoRecord.playFlag;
                                vodInfo.currentPlayFlag = vodInfoRecord.playFlag;
                                vodInfo.currentPlayIndex = vodInfoRecord.playIndex;
                            }
                        vodInfo.playerCfg = vodInfoRecord.playerCfg;
                        vodInfo.reverseSort = vodInfoRecord.reverseSort;
                        } else {
                            vodInfo.playIndex = 0;
                            vodInfo.playFlag = null;
                            vodInfo.currentPlayFlag = null;
                            vodInfo.currentPlayIndex = 0;
                            vodInfo.playerCfg = "";
                            vodInfo.reverseSort = false;
                        }

                        if (vodInfo.reverseSort) {      //XUAMENG读取记录后显示BUG
                            vodInfo.reverse();
                            tvSort.setText("正序");
                        }else{
                            tvSort.setText("倒序");
                        }

                        if (vodInfo.playFlag == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag))
                            vodInfo.playFlag = (String) vodInfo.seriesMap.keySet().toArray()[0];

                        int flagScrollTo = 0;
                        for (int j = 0; j < vodInfo.seriesFlags.size(); j++) {
                            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(j);
                            if (flag.name.equals(vodInfo.playFlag)) {
                                flagScrollTo = j;
                                flag.selected = true;
                            } else
                                flag.selected = false;
                        }
                        //设置播放地址
                        setTextShow(tvPlayUrl, "播放地址：", vodInfo.seriesMap.get(vodInfo.playFlag).get(0).url);
                        seriesFlagAdapter.setNewData(vodInfo.seriesFlags);
                        mGridViewFlag.scrollToPosition(flagScrollTo);

                        refreshList();   //xuameng返回键、长按播放刷新滚动到剧集
                        mGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                            @Override
                                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                                    super.onScrollStateChanged(recyclerView, newState);
                                    if (newState == mGridView.SCROLL_STATE_IDLE) {   //xuameng剧集滚动完成后焦点选择为剧集
                                        // 滚动已经停止，执行你需要的操作
                                        //	mGridView.requestFocus();
                                        mGridView.setSelection(vodInfo.playIndex);
                                        mGridView.removeOnScrollListener(this);    //xuameng删除滚动监听
                                    }
                                }
                       });
                       if(mGridView.isScrolling() || mGridView.isComputingLayout()) {
                       }else{
                           //mGridView.requestFocus();  //xuameng如果不满足滚动条件直接获得焦点
                           mGridView.setSelection(vodInfo.playIndex);
                       }

                       tvPlay.setNextFocusUpId(R.id.mGridView);   //xuameng上面焦点是选剧集
                       tvQuickSearch.setNextFocusUpId(R.id.mGridView); 
                       tvSort.setNextFocusUpId(R.id.mGridView); 
                       tvCollect.setNextFocusUpId(R.id.mGridView); 
                       tvDesc.setNextFocusUpId(R.id.mGridView); 
                       tvPush.setNextFocusUpId(R.id.mGridView); 
                       //llPlayerFragmentContainerBlock.setNextFocusUpId(R.id.mGridView); 

                        if (showPreview) {
                            jumpToPlay();
                            llPlayerFragmentContainer.setVisibility(View.VISIBLE);
                            llPlayerFragmentContainerBlock.setVisibility(View.VISIBLE);
                            toggleSubtitleTextSize();
                        }
                        // startQuickSearch();
                    } else {
                        mGridViewFlag.setVisibility(View.GONE);
                        mGridView.setVisibility(View.GONE);
                        mSeriesGroupView.setVisibility(View.GONE);
                        tvPlay.setVisibility(View.GONE);
                        tvSort.setVisibility(View.GONE);  //xuameng修复无播放数据倒序空指针
                        mEmptyPlayList.setVisibility(View.VISIBLE);
                    }
                } else {
                    showEmpty();
                    llPlayerFragmentContainer.setVisibility(View.GONE);
                    llPlayerFragmentContainerBlock.setVisibility(View.GONE);
                }
            }
        });
    }

    private String getHtml(String label, String content) {
        if (content == null) {
            content = "";
        }
        return label + "<font color=\"#FFFFFF\">" + content + "</font>";
    }

    private String  vod_picture="";
    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            vod_picture=bundle.getString("picture", "");
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid != null) {
            vodId = vid;
            sourceKey = key;
            firstsourceKey = key;
            showLoading();
            sourceViewModel.getDetail(sourceKey, vodId);
            boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
            if (isVodCollect) {
                tvCollect.setText("★收藏");
            } else {
                tvCollect.setText("☆收藏");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
        public void refresh(RefreshEvent event) {
            if (event.type == RefreshEvent.TYPE_REFRESH) {
                if (event.obj != null) {
                    if (event.obj instanceof Integer) {
                        int newIndex = (int) event.obj;
                        if (vodInfo != null) {
                            // 1. 保存当前显示源，用于后续UI更新
                            String originalDisplayFlag = vodInfo.playFlag;
                    
                            // 2. 重要：绝对不改变 currentPlayFlag，保持原来的播放源
                            if (vodInfo.currentPlayFlag == null) {
                                vodInfo.currentPlayFlag = vodInfo.playFlag;
                            }
                    
                            // 3. 更新当前播放源的索引
                            vodInfo.currentPlayIndex = newIndex;
                    
                            // 4. 清除播放源中所有剧集的高亮状态
                            if (vodInfo.seriesMap.containsKey(vodInfo.currentPlayFlag)) {
                                List<VodInfo.VodSeries> currentSeriesList = vodInfo.seriesMap.get(vodInfo.currentPlayFlag);
                                if (currentSeriesList != null) {
                                    for (VodInfo.VodSeries series : currentSeriesList) {
                                        series.selected = false;
                                    }
                                }
                            }
                    
                            // 5. 为播放源设置新的高亮
                            if (vodInfo.seriesMap.containsKey(vodInfo.currentPlayFlag)) {
                                List<VodInfo.VodSeries> currentSeriesList = vodInfo.seriesMap.get(vodInfo.currentPlayFlag);
                                int safeIndex = newIndex;
                                if (safeIndex >= currentSeriesList.size()) {
                                    safeIndex = currentSeriesList.size() - 1;
                                }
                                if (safeIndex >= 0 && safeIndex < currentSeriesList.size()) {
                                    currentSeriesList.get(safeIndex).selected = true;
                                }
                            }
                    
                            // 6. 处理显示源的高亮 - 修复关键问题
                            // 如果显示源和播放源相同，更新playIndex
                            if (vodInfo.currentPlayFlag.equals(vodInfo.playFlag)) {
                                vodInfo.playIndex = newIndex;
                            } else {
                                // 显示源和播放源不同，需要为显示源设置合理的高亮
                                if (vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
                                    List<VodInfo.VodSeries> displaySeriesList = vodInfo.seriesMap.get(vodInfo.playFlag);
                                    if (displaySeriesList != null) {
                                        // 清除显示源的高亮
                                        for (VodInfo.VodSeries series : displaySeriesList) {
                                            series.selected = false;
                                        }
                                
                                        // 设置显示源的高亮（映射到相同索引或第一集）
                                        int displaySafeIndex = newIndex;
                                        if (displaySafeIndex >= displaySeriesList.size() || displaySafeIndex < 0) {
                                            displaySafeIndex = 0;
                                        }
                                        if (!displaySeriesList.isEmpty()) {
                                            displaySeriesList.get(displaySafeIndex).selected = true;
                                            vodInfo.playIndex = displaySafeIndex;
                                        }
                                    }
                                }
                            }
                    
                            // 7. 关键修复：确保UI刷新 - 无论显示源和播放源是否相同，都要刷新UI
                            updateSeriesAdapterData();
                    
                            // 8. 关键修复：保存历史记录时使用临时变量确保正确性
                            // 创建临时VodInfo副本，确保保存时使用正确的播放源信息
                            VodInfo saveVodInfo = new VodInfo();
                            try {
                                // 深拷贝vodInfo的基本属性
                                saveVodInfo.setVideo(vodInfo.getVideo());
                                saveVodInfo.sourceKey = vodInfo.sourceKey;
                                saveVodInfo.seriesMap = vodInfo.seriesMap;
                                saveVodInfo.seriesFlags = vodInfo.seriesFlags;
                                saveVodInfo.playerCfg = vodInfo.playerCfg;
                                saveVodInfo.reverseSort = vodInfo.reverseSort;
                        
                                // 关键：保存时使用播放源的信息，而不是显示源
                                saveVodInfo.playFlag = vodInfo.currentPlayFlag;  // 使用播放源
                                saveVodInfo.playIndex = vodInfo.currentPlayIndex; // 使用播放索引
                                saveVodInfo.currentPlayFlag = vodInfo.currentPlayFlag;
                                saveVodInfo.currentPlayIndex = vodInfo.currentPlayIndex;
                        
                                // 恢复显示源状态，不影响UI
                                vodInfo.playFlag = originalDisplayFlag;
                        
                            } catch (Exception e) {
                                e.printStackTrace();
                                saveVodInfo = vodInfo;
                            }
                    
                            // 9. 保存历史记录 - 使用当前播放源进行保存
                            String saveSourceKey = vodInfo.currentPlayFlag != null ? vodInfo.currentPlayFlag : sourceKey;
                            insertVod(saveSourceKey, saveVodInfo);
                    
                            // 10. 同时保存一份到初始源，用于兼容性
                            if (!saveSourceKey.equals(firstsourceKey)) {
                                insertVod(firstsourceKey, saveVodInfo);
                            }
                        }
			                //xuameng解决焦点丢失		if (!fullWindows){
            //              mGridView.setSelection(index);
			//
            //             insertVod(sourceKey, vodInfo);
                
                        } else if (event.obj instanceof JSONObject) {    //xuameng保存播放器配置
                            vodInfo.playerCfg = ((JSONObject) event.obj).toString();
                            //保存历史
                            insertVod(firstsourceKey, vodInfo);
                            //        insertVod(sourceKey, vodInfo);
                        } else if (event.obj instanceof String) {
                            String url = event.obj.toString();
                            //设置更新播放地址
                            setTvPlayUrl(url);
                        }
                    }
            } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
                if (event.obj != null) {
                    Movie.Video video = (Movie.Video) event.obj;
                    loadDetail(video.id, video.sourceKey);
                }
            } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
                if (event.obj != null) {
                    String word = (String) event.obj;
                    switchSearchWord(word);
                }
            } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
                try {
                    searchData(event.obj == null ? null : (AbsXml) event.obj);
                } catch (Exception e) {
                    searchData(null);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)              //xuameng远程推送
    public void pushVod(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_VOD) {
            if (event.obj != null) {
                List<String> data = (List<String>) event.obj;
                OkGo.getInstance().cancelTag("pushVod");
                OkGo.<String>post("http://" + data.get(0) + ":" + data.get(1) + "/action")
                        .tag("pushVod")
                        .params("id", vodId)
                        .params("sourceKey", sourceKey)
                        .params("do", "mirror")
                        .execute(new AbsCallback<String>() {
                            @Override
                            public String convertResponse(okhttp3.Response response) throws Throwable {
                                if (response.body() != null) {
                                    return response.body().string();
                                } else {
                                    App.showToastShort(DetailActivity.this, "推送失败，地址可能填写错误！");
                                    throw new IllegalStateException("网络请求错误");
                                }
                            }

                            @Override
                            public void onSuccess(Response<String> response) {
                                String r = response.body();
                                if ("mirrored".equals(r))
                                    App.showToastShort(DetailActivity.this, "推送成功！");
                                else
                                    App.showToastShort(DetailActivity.this, "推送失败，远端聚汇影视版本不支持！");
                            }

                            @Override
                            public void onError(Response<String> response) {
                                super.onError(response);
                                App.showToastShort(DetailActivity.this, "推送失败，地址可能填写错误！");
                            }
                        });
            }
        }
    }                //xuameng远程推送END

    private String searchTitle = "";
    private boolean hadQuickStart = false;
    private final List<Movie.Video> quickSearchData = new ArrayList<>();
    private final List<String> quickSearchWord = new ArrayList<>();
    private ExecutorService searchExecutorService = null;

    private void switchSearchWord(String word) {
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchData.clear();
        searchTitle = word;
        searchResult();
    }

    private void startQuickSearch() {
        initCheckedSourcesForSearch();
        if (hadQuickStart)
            return;
        hadQuickStart = true;
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchWord.clear();
        searchTitle = mVideo.name;
        quickSearchData.clear();
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
        /* xuameng // 分词   
        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
                .tag("fenci")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        try {
                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
                                quickSearchWord.add(je.getAsJsonObject().get("t").getAsString());
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }
                });   xuameng */

        searchResult();
    }

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable() || !bean.isQuickSearch()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getQuickSearch(key, searchTitle);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                // 去除当前相同的影片
                if (video.sourceKey.equals(sourceKey) && video.id.equals(vodId))
                    continue;
                data.add(video);
            }
            quickSearchData.addAll(data);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }


    private void insertVod(String sourceKey, VodInfo vodInfo) {   //xuameng 更新保存逻辑修复 历史记录不正确
        try {
            // 优先使用当前播放源的信息来获取剧集名称
            String playFlagForNote;
            int playIndexForNote;
        
            if (vodInfo.currentPlayFlag != null && vodInfo.currentPlayIndex >= 0) {
                // 使用当前播放源的信息
                playFlagForNote = vodInfo.currentPlayFlag;
                playIndexForNote = vodInfo.currentPlayIndex;
            } else {
                // 兼容旧版：使用显示源的信息
                playFlagForNote = vodInfo.playFlag;
                playIndexForNote = vodInfo.playIndex;
            }
        
            vodInfo.playNote = vodInfo.seriesMap.get(playFlagForNote).get(playIndexForNote).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
        HawkConfig.saveHistory = true;   //xuameng判断存储历史记录
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH));
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        OkGo.getInstance().cancelTag("fenci");
        OkGo.getInstance().cancelTag("detail");
        OkGo.getInstance().cancelTag("quick_search");
		OkGo.getInstance().cancelTag("pushVod");      //XUAMENG远程推送
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
		boolean showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true);  //xuameng true是显示小窗口,false是不显示小窗口
        if (fullWindows) {
            if (playFragment.onBackPressed())  //xuameng上一级交给VODController控制
                return;
            toggleFullPreview();
            switchToPlayingSourceAndScroll();   //xuameng滚动到当前剧集
//            mGridView.requestFocus(); 没用了
            List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
            mSeriesGroupView.setVisibility(list.size()>GroupCount ? View.VISIBLE : View.GONE);
            return;
        }
        else if (seriesSelect) {
            if (seriesFlagFocus != null && !seriesFlagFocus.isFocused()) {
               // seriesFlagFocus.requestFocus();
               switchTomGridViewFlag();  //xuameng 自动滚动到当前播放源
                return;
            }else {
                tvPlay.requestFocus();        //xuameng修复播放退出到小窗口后再按返回键直接退出的问题，跳转到播放
                return;
            }
        }
        else if (showPreview && playFragment!=null) {    //xuameng如果显示小窗口播放就释放视频，修复退出还显示暂停图标等图标的BUG
            playFragment.setPlayTitle(false);
            playFragment.mVideoView.release();
        }
        HawkConfig.intVod = false;  //xuameng判断进入播放
        App.HideToast();
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && playFragment != null && fullWindows) {
            if (playFragment.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event != null && playFragment != null && fullWindows) {
            if (playFragment.onKeyDown(keyCode,event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event != null && playFragment != null && fullWindows) {
            if (playFragment.onKeyUp(keyCode,event)) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    // preview
    VodInfo previewVodInfo = null;
    boolean showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true); // true 开启 false 关闭
    boolean fullWindows = false;
    ViewGroup.LayoutParams windowsPreview = null;
    ViewGroup.LayoutParams windowsFull = null;

    void toggleFullPreview() {
        if (windowsPreview == null) {
            windowsPreview = llPlayerFragmentContainer.getLayoutParams();
        }
        if (windowsFull == null) {
            windowsFull = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        fullWindows = !fullWindows;
        llPlayerFragmentContainer.setLayoutParams(fullWindows ? windowsFull : windowsPreview);
        llPlayerFragmentContainerBlock.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
        mGridView.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
        mGridViewFlag.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
        mSeriesGroupView.setVisibility(fullWindows ? View.GONE : View.VISIBLE);

        //全屏下禁用详情页几个按键的焦点 防止上键跑过来
        tvPlay.setFocusable(!fullWindows);
        tvSort.setFocusable(!fullWindows);
        tvCollect.setFocusable(!fullWindows);
        tvQuickSearch.setFocusable(!fullWindows);
        tvDesc.setFocusable(!fullWindows);      //xuameng 内容简介
        tvPush.setFocusable(!fullWindows);    //xuameng 远程推送
        llPlayerFragmentContainerBlock.setFocusable(!fullWindows);
        toggleSubtitleTextSize();
    }

    void toggleSubtitleTextSize() {
        int subtitleTextSize  = SubtitleHelper.getTextSize(this);
        if (!fullWindows) {
            subtitleTextSize *= 0.6;
        }
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE, subtitleTextSize));
    }

    private void setTvPlayUrl(String url){
      if (url == null || url.isEmpty()) {
          url = "聚汇影视提示您：播放地址为空！";
      }	
      setTextShow(tvPlayUrl, "播放地址：", url);
    }

    //xuameng 优化后的UI刷新方法
    public void updateSeriesAdapterData() {
        if (seriesAdapter != null && vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
            // 使用递归重试机制确保安全执行
            postDelayedWithRetry();
        }
    }

    private void postDelayedWithRetry() {
        if (mGridViewLayoutMgr == null || mGridView == null) {
            // 如果LayoutManager或GridView为空，延迟重试
            mGridView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    postDelayedWithRetry(); // 递归调用重试
                }
            }, 100); // 延迟100ms重试
            return;
        }
    
        // 确保在主线程中执行
        if (vodInfo != null && vodInfo.playFlag != null) {
            // 执行数据更新
            if (seriesAdapter != null && vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
                seriesAdapter.setNewData(vodInfo.seriesMap.get(vodInfo.playFlag));
            }
        }
    }

    private void switchToPlayingSourceAndScroll() {   //xuameng 支持跨源滚动到当前剧集
        // 1. 检查当前显示源是否是正在播放的源
        if (vodInfo != null && vodInfo.currentPlayFlag != null && !vodInfo.playFlag.equals(vodInfo.currentPlayFlag)) {
            // 当前显示源不是播放源，需要切换回播放源
        
            // 1.1 保存旧的显示源
            String oldFlag = vodInfo.playFlag;
        
            // 1.2 清除旧显示源的高亮状态
            if (vodInfo.seriesMap.containsKey(oldFlag) && vodInfo.playIndex < vodInfo.seriesMap.get(oldFlag).size()) {
                vodInfo.seriesMap.get(oldFlag).get(vodInfo.playIndex).selected = false;
            }
        
            // 1.3 清除旧显示源在源列表中的选中状态
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(oldFlag)) {
                    flag.selected = false;
                    seriesFlagAdapter.notifyItemChanged(i);
                    break;
                }
            }
        
            // 1.4 切换到播放源
            vodInfo.playFlag = vodInfo.currentPlayFlag;
        
            // 1.5 设置播放源在源列表中的选中状态
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(vodInfo.playFlag)) {
                    flag.selected = true;
                    seriesFlagAdapter.notifyItemChanged(i);
                    // 滚动源列表到播放源位置
                    mGridViewFlag.scrollToPosition(i);
                    break;
                }
            }
        }
    
        // 2. 刷新列表显示
        refreshList();
    
        // 3. 确保即使不滚动也能执行选择操作
        // 添加滚动监听器确保在任何情况下都能执行选择
        mGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == mGridView.SCROLL_STATE_IDLE) {
                    mGridView.setSelection(vodInfo.playIndex);
                    mGridView.removeOnScrollListener(this);
                }
            }
        });
    
        // 4. 立即检查是否需要直接执行选择（避免滚动不触发）
        if (!mGridView.isScrolling() && !mGridView.isComputingLayout()) {
            // 如果当前没有滚动且没有计算布局，则直接执行选择
            mGridView.setSelection(vodInfo.playIndex);
        } else {
            // 如果正在滚动或计算布局，则等待滚动完成后再执行
            // 上面的监听器会处理这种情况
        }    
        App.showToastShort(DetailActivity.this, "已滚动到当前播放剧集！");
    }

    private void switchTomGridViewFlag() {  //xuameng 自动滚动到当前播放源
        // 1. 检查当前显示源是否是正在播放的源
        if (vodInfo != null && vodInfo.currentPlayFlag != null && !vodInfo.playFlag.equals(vodInfo.currentPlayFlag)) {
            // 当前显示源不是播放源，需要切换回播放源
    
            // 1.1 保存旧的显示源
            String oldFlag = vodInfo.playFlag;
    
            // 1.2 清除旧显示源的高亮状态
            if (vodInfo.seriesMap.containsKey(oldFlag) && vodInfo.playIndex < vodInfo.seriesMap.get(oldFlag).size()) {
                vodInfo.seriesMap.get(oldFlag).get(vodInfo.playIndex).selected = false;
            }
    
            // 1.3 清除旧显示源在源列表中的选中状态
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(oldFlag)) {
                    flag.selected = false;
                    seriesFlagAdapter.notifyItemChanged(i);
                    break;
                }
            }
    
            // 1.4 切换到播放源
            vodInfo.playFlag = vodInfo.currentPlayFlag;
    
            // 1.5 设置播放源在源列表中的选中状态
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(vodInfo.playFlag)) {
                    flag.selected = true;
                    seriesFlagAdapter.notifyItemChanged(i);
                    // 滚动源列表到播放源位置
                    mGridViewFlag.scrollToPosition(i);
                    // 选中源列表到播放源位置
                    final int finalScrollPosition = i; // 创建一个 final 局部变量副本
                    mGridViewFlag.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                            super.onScrollStateChanged(recyclerView, newState);
                            if (newState == mGridViewFlag.SCROLL_STATE_IDLE) {
                                mGridViewFlag.setSelection(finalScrollPosition);
                                mGridViewFlag.removeOnScrollListener(this);
                            }
                        }
                    });
    
                    if (!mGridViewFlag.isScrolling() && !mGridViewFlag.isComputingLayout()) {
                        // 如果当前没有滚动且没有计算布局，则直接执行选择
                        mGridViewFlag.setSelection(i);
                    } 
                    break;
                }
            }
        
        } else if (vodInfo != null && vodInfo.currentPlayFlag != null) {
            // 如果已经在播放源，只需要确保选中状态正确
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(vodInfo.currentPlayFlag)) {
                    mGridViewFlag.scrollToPosition(i);
                    // 选中源列表到播放源位置
                    final int finalScrollPosition = i; // 创建一个 final 局部变量副本
                    mGridViewFlag.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                            super.onScrollStateChanged(recyclerView, newState);
                            if (newState == mGridViewFlag.SCROLL_STATE_IDLE) {
                                mGridViewFlag.setSelection(finalScrollPosition);
                            mGridViewFlag.removeOnScrollListener(this);
                            }
                        }
                    });
    
                    if (!mGridViewFlag.isScrolling() && !mGridViewFlag.isComputingLayout()) {
                        // 如果当前没有滚动且没有计算布局，则直接执行选择
                        mGridViewFlag.setSelection(i);
                    }
                    break;
                }
            }
        }
    }

}
