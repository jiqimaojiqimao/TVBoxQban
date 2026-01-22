package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;   //xuameng搜索历史
import android.widget.TextView;
import com.github.tvbox.osc.base.App;

import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.dialog.RemoteDialog;
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.catvod.crawler.JsLoader;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.yang.flowlayoutlibrary.FlowLayout;  //xuameng搜索历史
import android.text.TextWatcher;  //xuameng搜索历史
import android.text.Editable;		//xuameng搜索历史
import com.github.tvbox.osc.data.SearchPresenter;  //xuameng搜索历史
import com.github.tvbox.osc.cache.SearchHistory;   //xuameng搜索历史
import java.util.Collections;   //xuameng搜索历史
import com.google.gson.Gson;  //热门搜索
import com.google.gson.JsonArray; //热门搜索
import com.google.gson.JsonElement; //热门搜索
import com.google.gson.JsonObject; //热门搜索
import com.google.gson.JsonParser; //热门搜索
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ArrayBlockingQueue;   //xuameng 线程池
import java.util.concurrent.ThreadPoolExecutor;  //xuameng 线程池
import java.util.concurrent.TimeUnit;   //xuameng 线程池
import java.util.concurrent.ThreadFactory;  //xuameng 线程池
import java.util.concurrent.LinkedBlockingQueue;   //xuameng 线程池
import java.util.Locale;   //xuameng 统计进度用

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;    //xuameng热搜
    private SourceViewModel sourceViewModel;   //xuameng
    private RemoteDialog remoteDialog;
    private EditText etSearch; //xuameng 请输入要搜索的内容
    private TextView tvSearch;   //xuameng 搜索
    private TextView tvClear;  //xuameng清空
    private SearchKeyboard keyboard;   //xuameng搜索键盘
    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private String searchTitle = "";
    private TextView tvSearchCheckboxBtn;  //xuameng指定搜索源
	private RelativeLayout searchTips;   //xuameng搜索历史
	private LinearLayout llWord;   //xuameng搜索历史
	private FlowLayout tv_history;    //xuameng搜索历史
	public String keyword;  //xuameng搜索历史
	private ImageView clearHistory;  //xuameng搜索历史
	private SearchPresenter searchPresenter;  //xuameng搜索历史
	private TextView tHotSearchText;  //xuameng热门搜索
	private static ArrayList<String> hots = new ArrayList<>();  //xuameng热门搜索

    private static HashMap<String, String> mCheckSources = null;
    private SearchCheckboxDialog mSearchCheckboxDialog = null;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }


    private static Boolean hasKeyBoard;
    private static Boolean isSearchBack;
    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
        hasKeyBoard = true;
        isSearchBack = false;
    }


    private List<Runnable> pauseRunnable = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (pauseRunnable != null && pauseRunnable.size() > 0) {
            searchExecutorService = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(), // 核心线程数=CPU核数
            Runtime.getRuntime().availableProcessors() * 2, // 最大线程数
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),  // 队列容量调整为1000
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        // 关键优化：设置256KB栈大小
                        Thread t = new Thread(null, r, "search-pool", 256 * 1024);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    }
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()  // 超限直接丢弃
            );
            allRunCount.set(pauseRunnable.size());
            for (Runnable runnable : pauseRunnable) {
                searchExecutorService.execute(runnable);
            }
            pauseRunnable.clear();
            pauseRunnable = null;
        }
        if (hasKeyBoard) {
            tvSearch.requestFocus();
       //     tvSearch.requestFocusFromTouch();     //xuameng 触碰时不获得焦点
        }else {
            if(!isSearchBack){
                etSearch.requestFocus();
         //       etSearch.requestFocusFromTouch();  //xuameng 触碰时不获得焦点
            }
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckboxBtn);
		searchTips = findViewById(R.id.search_tips);   //xuameng搜索历史
		llWord = findViewById(R.id.llWord);	//xuameng搜索历史
		tv_history = findViewById(R.id.tv_history);  //xuameng搜索历史
		clearHistory = findViewById(R.id.clear_history);  //xuameng搜索历史
		tHotSearchText = findViewById(R.id.mHotSearch_text);   //xuameng热门搜索
        tvClear = findViewById(R.id.tvClear);
        mGridView = findViewById(R.id.mGridView);
        keyboard = findViewById(R.id.keyBoardRoot);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        mGridViewWord.setHasFixedSize(true);
        mGridViewWord.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        wordAdapter = new PinyinAdapter();
        mGridViewWord.setAdapter(wordAdapter);
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
				keyword = wordAdapter.getItem(position);
				String[] split = keyword.split("\uFEFF");
				keyword = split[split.length - 1];
				etSearch.setText(keyword);
                if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                    Bundle bundle = new Bundle();
                    bundle.putString("title", keyword);
					refreshSearchHistory(keyword);  //xuameng搜索历史
                    jumpActivity(FastSearchActivity.class, bundle);
                }else {
                    search(keyword);
                }
            }
        });
        mGridView.setHasFixedSize(true);
        // lite
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0)
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
            // with preview
        else
            mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 3));
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    try {
                        if (searchExecutorService != null) {
                            pauseRunnable = searchExecutorService.shutdownNow();
                            searchExecutorService = null;
                            JsLoader.stopAll();
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }

try {
    // 【修改点1】：在停止线程池前，先保存未完成的任务
    List<Runnable> pendingTasks = null;
    if (searchExecutorService != null) {
        pendingTasks = searchExecutorService.shutdownNow(); // 保存返回的列表
        if (searchExecutorService instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) searchExecutorService).getQueue().clear();
        }
        searchExecutorService = null;
    }

    // 2. 彻底停止所有Js脚本加载器（保持不变）
    JsLoader.stopAll();
    // 3. 建议GC（保持不变）
    System.gc();

    // 策略A：赋值给成员变量 pauseRunnable，由 onResume() 统一恢复
    pauseRunnable = pendingTasks;


} catch (Throwable th) {
    th.printStackTrace();
}

                    hasKeyBoard = false;
                    isSearchBack = true;
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
					bundle.putString("picture", video.pic);   //xuameng某些网站图片部显示
                    bundle.putString("sourceKey", video.sourceKey);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });
        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                hasKeyBoard = true;
				if (!TextUtils.isEmpty(keyword)) {
                    if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                        Bundle bundle = new Bundle();
                        bundle.putString("title", keyword);
						refreshSearchHistory(keyword);  //xuameng搜索历史
                        jumpActivity(FastSearchActivity.class, bundle);
                    }else {
                        search(keyword);
                    }
                } else {
                    App.showToastShort(SearchActivity.this, "输入内容不能为空！");
                }
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {     
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                etSearch.setText("");
				wordAdapter.setNewData(hots);  //xuameng 热搜不用重复刷新     //xuameng修复清空后热门搜索为空
				tv_history.setVisibility(View.VISIBLE);   //xuameng修复BUG
                searchTips.setVisibility(View.VISIBLE);
                tHotSearchText.setText("热门搜索");          //xuameng修复删除内容后，热门搜索为空
				showSuccess();  //xuameng修复BUG
				mGridView.setVisibility(View.GONE);
				if (searchExecutorService != null) {
                   searchExecutorService.shutdownNow();
                   searchExecutorService = null;
                   JsLoader.stopAll();
				}
				cancel();
            }
        });

        this.etSearch.addTextChangedListener(new TextWatcher() {   //xuameng搜索历史
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {         //xuameng清空或删除关闭搜索内容显示搜索历史记录
                keyword = s.toString().trim();
                if (TextUtils.isEmpty(keyword)) {
                    cancel();
                    tv_history.setVisibility(View.VISIBLE);
                    searchTips.setVisibility(View.VISIBLE);
  //                  llWord.setVisibility(View.VISIBLE);
                    mGridView.setVisibility(View.GONE);
                }
            }
        });

//        etSearch.setOnFocusChangeListener(tvSearchFocusChangeListener);

        clearHistory.setOnClickListener(v -> {
            searchPresenter.clearSearchHistory();
            initSearchHistory();
        });


        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(int pos, String key) {
                if (pos > 1) {
                    String text = etSearch.getText().toString().trim();
                    text += key;
                    etSearch.setText(text);
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                } else if (pos == 1) {
                    String text = etSearch.getText().toString().trim();
                    if (text.length() > 0) {
                        text = text.substring(0, text.length() - 1);
                        etSearch.setText(text);
                    }
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                    if (text.length() == 0) {
						wordAdapter.setNewData(hots);  //xuameng 热搜不用重复刷新   //xuameng修复清空后热门搜索为空
                        tHotSearchText.setText("热门搜索");
						showSuccess();  //xuameng修复BUG
						tv_history.setVisibility(View.VISIBLE);   //xuameng修复BUG
						searchTips.setVisibility(View.VISIBLE);
						mGridView.setVisibility(View.GONE);
						if (searchExecutorService != null) {
							searchExecutorService.shutdownNow();
							searchExecutorService = null;
							JsLoader.stopAll();
							}
                    }
                } else if (pos == 0) {
                    if (remoteDialog == null) {
                        remoteDialog = new RemoteDialog(mContext); // XUAMENG成员变量    解决远程搜索remoteDialog不关闭的问题
                    }
                    remoteDialog.show();
                }
            }
        });
        setLoadSir(llLayout);
        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<SourceBean> searchAbleSource = ApiConfig.get().getSearchSourceBeanList();
                if (mSearchCheckboxDialog == null) {
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
                }else {
                    if(searchAbleSource.size()!=mSearchCheckboxDialog.mSourceList.size()){
                        mSearchCheckboxDialog.setMSourceList(searchAbleSource);
                    }
                }
                mSearchCheckboxDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
                mSearchCheckboxDialog.show();
            }
        });
    }

    private void refreshSearchHistory(String keyword2) {         //xuameng 更新搜索历史记录
        if (!this.searchPresenter.keywordsExist(keyword2)) {
            this.searchPresenter.addKeyWordsTodb(keyword2);
            initSearchHistory();
        }
    }

    private void initSearchHistory() {
        ArrayList<SearchHistory> searchHistory = this.searchPresenter.getSearchHistory();
        List<String> historyList = new ArrayList<>();
        // xuameng保留原始数据列表的引用，以便通过索引获取对应的 SearchHistory 对象（如果删除需要ID）
        final List<SearchHistory> originalHistoryList = new ArrayList<>(searchHistory);   //xuameng 用于长按删除
        for (SearchHistory history : searchHistory) {
            historyList.add(history.searchKeyWords);
        }
        Collections.reverse(historyList);
        tv_history.setViews(historyList, new FlowLayout.OnItemClickListener() {
            public void onItemClick(String content) {
                etSearch.setText(content);
                if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)) {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", content);
                    refreshSearchHistory(content);
                    jumpActivity(FastSearchActivity.class, bundle);
                } else {
                    search(content);
                    tvSearch.requestFocus();    //xuameng 点击搜索历史文字后的默认焦点
                }
            }
        });
        // 仅在 Android 5.0 以下版本应用焦点修复       xuameng修复安卓4搜索历史获取不到焦点问题
 /*       if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < tv_history.getChildCount(); i++) {
                        View child = tv_history.getChildAt(i);
                        // 关键修复：设置焦点属性，解决 Android 4.x 点击问题
                        child.setFocusable(true);
                       // child.setFocusableInTouchMode(true);
                    }
                }
            });
        }    
*/

		// ========== xuameng新增：为每个历史标签添加长按直接删除功能 ==========
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 遍历 FlowLayout 中的所有子视图（即每个历史标签）
                for (int i = 0; i < tv_history.getChildCount(); i++) {
                    final View child = tv_history.getChildAt(i);
                    // 根据视图的索引，从已反转的列表中获取对应的关键词和数据对象
                    final String keywordToDelete = historyList.get(i);
                    final SearchHistory historyItemToDelete = originalHistoryList.get(i);

                    // 设置长按监听器
                    child.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            // 直接执行删除操作，不弹出对话框
                            // 1. 从数据库删除数据
                            boolean success = searchPresenter.deleteKeyWordsFromDb(keywordToDelete);

                            if (success) {
                                // 2. 从界面移除该标签
                                tv_history.removeView(child);
                                // 3. 显示操作成功的 Toast 提示
                                App.showToastShort(SearchActivity.this, "已删除: " + keywordToDelete);
                            } else {
                                App.showToastShort(SearchActivity.this, "删除失败");
                            }
                            // 返回 true 表示已消费该长按事件，防止触发其他可能的后续事件
                            return true;
                        }
                    });
                }
            }
        });

    }               //xuameng 搜索历史

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
		searchPresenter = new SearchPresenter();   //xuameng 搜索历史
    }

    /**
     * 拼音联想
     */
    private void loadRec(String key) {
        OkGo.get("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box")
                .params("format", "json")
                .params("page_num", 0)
                .params("page_size", 50) //随便改
                .params("key", key)
                .execute(new AbsCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        try {
                            ArrayList hots = new ArrayList<>();
                            String result = (String) response.body();
                            Gson gson = new Gson();
                            JsonElement json = gson.fromJson(result, JsonElement.class);
                            JsonArray groupDataArr = json.getAsJsonObject()
                                    .get("data").getAsJsonObject()
                                    .get("search_data").getAsJsonObject()
                                    .get("vecGroupData").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("group_data").getAsJsonArray();
                            for (JsonElement groupDataElement : groupDataArr) {
                                JsonObject groupData = groupDataElement.getAsJsonObject();
                                String keywordTxt = groupData.getAsJsonObject("dtReportInfo")
                                        .getAsJsonObject("reportData")
                                        .get("keyword_txt").getAsString();
                                hots.add(keywordTxt.trim());
                            }
                            tHotSearchText.setText("猜你想搜");
                            wordAdapter.setNewData(hots);
                            mGridViewWord.smoothScrollToPosition(0);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private void initData() {
        initCheckedSourcesForSearch();
        Intent intent = getIntent();
		initSearchHistory();  //xuameng 搜索历史
		showSuccess();  //xuameng 搜索历史
		mGridView.setVisibility(View.GONE);
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
				refreshSearchHistory(title);  //xuameng 搜索历史
                jumpActivity(FastSearchActivity.class, bundle);
            }else {
                search(title);
            }
        }
        // 加载热词
        if (hots.size() != 0) {
            wordAdapter.setNewData(hots);  //xuameng 热搜不用重复刷新
            return;
        }
        showHotSearchtext();  //xuameng 热搜
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
				refreshSearchHistory(title);   //xuameng 搜索历史
                jumpActivity(FastSearchActivity.class, bundle);
            }else{
                search(title);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    public static void setCheckedSourcesForSearch(HashMap<String,String> checkedSources) {
        mCheckSources = checkedSources;
    }

    private void search(String title) {
		if (TextUtils.isEmpty(title)){
            App.showToastShort(SearchActivity.this, "输入内容不能为空！");
			return;
		}
        cancel();   
        if (remoteDialog != null) {
            remoteDialog.dismiss();
            remoteDialog = null;
        }
        etSearch.setText(title);
        this.searchTitle = title;
        mGridView.setVisibility(View.GONE); //xuameng 搜索历史
        searchAdapter.setNewData(new ArrayList<>());
		refreshSearchHistory(title);  //xuameng 搜索历史
        searchResult();
    }

    private ExecutorService searchExecutorService = null;   //xuameng全局声明
    private AtomicInteger allRunCount = new AtomicInteger(0);
    private volatile boolean isActivityDestroyed = false; //xuameng 退出就不统计搜索成功了

    private void searchResult() {
        // 原有清理逻辑保持不变
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
        }
        // 优化线程池配置（核心修改点）
        searchExecutorService = new ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(), // 核心线程数=CPU核数
        Runtime.getRuntime().availableProcessors() * 2, // 最大线程数
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),  // 队列容量调整为1000
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    // 关键优化：设置256KB栈大小
                    Thread t = new Thread(null, r, "search-pool", 256 * 1024);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()  // 超限直接丢弃
        );
        // 原有数据准备逻辑（完全保留）
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);
        ArrayList<String> siteKey = new ArrayList<>();
        // 创建计数器（新增）
        final AtomicInteger completedCount = new AtomicInteger(0);
        final int totalTasks = (int) searchRequestList.stream()
            .filter(bean -> bean.isSearchable() && 
                   (mCheckSources == null || mCheckSources.containsKey(bean.getKey())))
            .count();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
            allRunCount.incrementAndGet();
        }
        if (siteKey.size() <= 0) {
            App.showToastShort(SearchActivity.this, "聚汇影视提示：请指定搜索源！");
            return;
        }
        showLoading();
        // 执行搜索任务（添加完成回调）
        for (String key : siteKey) {
            searchExecutorService.execute(() -> {
                try {
                    if (!isActivityDestroyed) { //xuameng 退出就不搜索了
                        sourceViewModel.getSearch(key, searchTitle);
                    }
                } finally {
                    // 实时进度更新（每完成10%或最后一项）
                    int current = completedCount.incrementAndGet();
                    if (!isActivityDestroyed && 
                       (current % Math.max(1, totalTasks/10) == 0 || current == totalTasks)) {
                        runOnUiThread(() -> updateProgress(current, totalTasks));
                    }
                }
            });
        }
    }

    private void updateProgress(int current, int total) {   // xuameng任务完成计数（新增）
        String message = (current == total) 
            ? String.format(Locale.getDefault(), 
                "所有任务已完成！共处理%d个搜索源 (100%%)", total)
            : String.format(Locale.getDefault(), 
                "搜索进度: %d/%d (%.1f%%)", current, total, current * 100f / total);
        // 根据完成状态选择Toast时长
        if (current == total) {
            App.showToastLong(SearchActivity.this, message);
        } else {
            App.showToastShort(SearchActivity.this, message);
        }
    }

    private boolean matchSearchResult(String name, String searchTitle) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false;
        searchTitle = searchTitle.trim();
        String[] arr = searchTitle.split("\\s+");
        int matchNum = 0;
        for(String one : arr) {
            if (name.contains(one)) matchNum++;
        }
        return matchNum == arr.length ? true : false;
    }

    private void searchData(AbsXml absXml) {  //xuameng重要BUG如果快速模式直接返回
		if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
			return;
		}
        if (searchExecutorService == null) {  //xuameng点击清除或删除所有文字后还继续显示搜索结果
            return;
        }
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (matchSearchResult(video.name, searchTitle)) data.add(video);
            }
            if (searchAdapter.getData().size() > 0) {
                searchAdapter.addData(data);
            } else {
                showSuccess();   //xuameng搜索历史
                mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data);
                tv_history.setVisibility(View.GONE);    //xuameng搜索历史
                searchTips.setVisibility(View.GONE);  //xuameng搜索历史
//                llWord.setVisibility(View.GONE);   //xuameng搜索历史
            }
        }

        int count = allRunCount.decrementAndGet();
        if (count <= 0) {
            if (searchAdapter.getData().size() <= 0) {
				if (searchExecutorService != null) {
                    showEmpty();		//xuameng修复BUG
				}else{
				tv_history.setVisibility(View.VISIBLE);   //xuameng修复BUG
				searchTips.setVisibility(View.VISIBLE);
				mGridView.setVisibility(View.GONE); 
				}
            }
            cancel();
        }
    }


    private void cancel() {
        OkGo.getInstance().cancelTag("search");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true; //xuameng 退出就不统计搜索成功了
        cancel();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        isActivityDestroyed = true;   //xuameng 退出就不统计搜索成功了
        App.HideToast();  //xuameng HideToast
        cancel();
        try {
            if (searchExecutorService != null) {
                if (searchExecutorService instanceof ThreadPoolExecutor) {
                    ((ThreadPoolExecutor) searchExecutorService).getQueue().clear();
                }
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
                System.gc();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        super.onBackPressed();
    }

    public void showHotSearchtext() {          //xuameng 热搜
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_search")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
//xuameng已声明类成员变量                            ArrayList<String> hots = new ArrayList<>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonObject().get("mapResult").getAsJsonObject().get("0").getAsJsonObject().get("listInfo").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                hots.add(obj.get("title").getAsString().trim().replaceAll("<|>|《|》|-", "").split(" ")[0]);
                            }
							wordAdapter.setNewData(hots);   
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
		} 
}
