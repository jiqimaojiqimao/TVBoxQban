package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.FastListAdapter;
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter;
import com.github.tvbox.osc.ui.adapter.SearchWordAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.catvod.crawler.JsLoader;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.lzy.okgo.OkGo;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.github.tvbox.osc.base.App;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import com.orhanobut.hawk.Hawk;  //xuameng 搜索展示用
import com.github.tvbox.osc.util.HawkConfig; //xuameng 搜索展示用

import com.github.tvbox.osc.bean.MovieSort;   //xuameng 新增搜索结果有folder 就是下一级判断
import java.util.Stack;  //xuameng 新增搜索结果有folder 就是下一级判断 堆栈列表
import android.view.ViewTreeObserver;  //xuameng 新增搜索结果有folder 就是下一级判断 监听选中滚动

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ArrayBlockingQueue;   //xuameng 线程池
import java.util.concurrent.ThreadPoolExecutor;  //xuameng 线程池
import java.util.concurrent.TimeUnit;   //xuameng 线程池
import java.util.concurrent.ThreadFactory;   //xuameng 线程池
import java.util.concurrent.LinkedBlockingQueue;   //xuameng 线程池
import java.util.Locale;   //xuameng 统计进度用

/**
 * @author xuameng
 * @date :2026/06/01
 * @description:    //xuameng 新增搜索结果有folder 就是下一级判断
 */
public class FastSearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private TextView mSearchTitle;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewFilter;
    private TvRecyclerView mGridViewWord;
    private TvRecyclerView mGridViewWordFenci;
    SourceViewModel sourceViewModel;  //xuameng 数据源
    //    private EditText etSearch;
//    private TextView tvSearch;
//    private TextView tvClear;
//    private SearchKeyboard keyboard;
//    private TextView tvAddress;
//    private ImageView ivQRCode;
    private SearchWordAdapter searchWordAdapter;
    private FastSearchAdapter searchAdapter;
    private FastSearchAdapter searchAdapterFilter;
    private FastListAdapter spListAdapter;
    private String searchTitle = "";
    private HashMap<String, String> spNames;
    private boolean isFilterMode = false;
    private String searchFilterKey = "";    // 过滤的key
    private HashMap<String, ArrayList<Movie.Video>> resultVods; // 搜索结果
    private List<String> quickSearchWord = new ArrayList<>();
    private HashMap<String, String> mCheckSources = null;
    private final HashMap<String, List<Movie.Video>> folderCache = new HashMap<>();  //xuameng 搜索中间层缓存

    // xuameng新增：返回栈（核心）
    private int page = 1;
    private MovieSort.SortData currentSortData = new MovieSort.SortData("", "搜索结果");
    static class BackNode {
        String sourceKey;    // 记录来源站点
        String sortId;        // 当前节点（下一级）的 ID
        String parentSortId; // 父节点（上一层）的 ID
        int lastSelectedPosition;  //  选中项
        boolean isFilterMode;     // 是否来自筛选列表
        String filterKey;         // 当前筛选 key

        private BackNode(String sourceKey, String sortId, String parentSortId, int lastSelectedPosition, boolean isFilterMode, String filterKey) {
            this.sourceKey = sourceKey;
            this.sortId = sortId;
            this.parentSortId = parentSortId;
            this.lastSelectedPosition = lastSelectedPosition;
            this.isFilterMode = isFilterMode;
            this.filterKey = filterKey;
        }
    }
    private final Stack<BackNode> backStack = new Stack<>();
    // 缓存首次全站搜索结果
    private final List<Movie.Video> topSearchCache = new ArrayList<>();
    // 判断搜索是否完成
    private boolean topSearchCompleted = false;
    // 判断是否正在加载下级列表
    private boolean getListIng = false; 
    // 是否处于「全局搜索结果阶段」
    private boolean isTopSearchStage = true;
    // 是否进入过下一级全部
    private boolean isNextLevel = false;
    // 是否进入过下一级分类
    private boolean isNextLevelFilter = false;
    // 首页选中项全部
    private int firstSelectedPos = 0;
    // xuameng新增：返回栈（核心完成）

    private View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {  //xuameng 左侧菜单焦点监听
        @Override
        public void onFocusChange(View itemView, boolean hasFocus) {
            try {
                if (!hasFocus) {
                    spListAdapter.onLostFocus(itemView);
                } else {
                    int ret = spListAdapter.onSetFocus(itemView);
                    if (ret < 0) return;
                    TextView v = (TextView) itemView;
                    String sb = v.getText().toString();
                    //   filterResult(sb);           xuameng这是TV获取到焦点默认执行筛选菜单数据，因为加了下一级所以需要点击后刷新数据
                    if (backStack.isEmpty()) {  //xuameng改成如果当前没有下一级，TV获取到焦点默认执行筛选菜单数据
                        filterResult(sb); 
                    }else{
                        App.showToastShort(FastSearchActivity.this, "请按OK键显示筛选数据！");
                    }
                }
            } catch (Exception e) {
                App.showToastShort(FastSearchActivity.this, e.toString());
            }
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_fast_search;
    }

    @Override
    protected void init() {
        spNames = new HashMap<String, String>();
        resultVods = new HashMap<String, ArrayList<Movie.Video>>();
        initView();
        initViewModel();
        initData();
    }

    private List<Runnable> pauseRunnable = null;

    @Override
    protected void onResume() {
        super.onResume();
        ContinueSearchExecutor(); //继续搜索
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        mSearchTitle = findViewById(R.id.mSearchTitle);
        mGridView = findViewById(R.id.mGridView);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        mGridViewFilter = findViewById(R.id.mGridViewFilter);

        mGridViewWord.setHasFixedSize(true);
        mGridViewWord.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        spListAdapter = new FastListAdapter();
        mGridViewWord.setAdapter(spListAdapter);


//        mGridViewWord.setFocusable(true);
//        mGridViewWord.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            @Override
//            public void onFocusChange(View itemView, boolean hasFocus) {}
//        });

        mGridViewWord.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {  //xuameng 左侧菜单焦点监听
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                child.setFocusable(true);
                child.setOnFocusChangeListener(focusChangeListener);
                TextView t = (TextView) child;
                if (t.getText() == "全部") {  //默认焦点 全部
                    t.requestFocus();
                }
//                if (child.isFocusable() && null == child.getOnFocusChangeListener()) {
//                    child.setOnFocusChangeListener(focusChangeListener);
//                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                view.setOnFocusChangeListener(null);
            }
        });

        spListAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {  //xuameng 左侧菜单点击监听
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                String spName = spListAdapter.getItem(position);
                filterResult(spName);
            }
        });

        mGridView.setHasFixedSize(true);

        // xuameng 搜索展示 0文字列表 
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0){
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        }else{  // xuameng 搜索展示 1缩略图
            mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 5));
        }
        
        searchAdapter = new FastSearchAdapter();
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

                    //xuameng 如有下一级直接getListFromSearch 加载列表
                    if (video.tag != null && (video.tag.equals("folder") || video.tag.equals("cover"))) {  
                        isTopSearchStage = false;   // 关闭全局搜索结果写入
                        isNextLevel = true;  //进入过下一级
                        // 【关键】在修改 currentSortData.id 之前，先把当前的 ID 保存下来，它就是父级 ID
                        String currentParentId = currentSortData.id; 
                        currentSortData.id = video.id;
                        int selectedPos = position;
                        if (selectedPos > 0 && firstSelectedPos == 0){  //首页选中项全部只赋值一次
	                        firstSelectedPos = selectedPos;
                        }
                        // 【关键】把父级 ID 传入 BackNode
                        BackNode node = new BackNode(
                            video.sourceKey,
                            currentSortData.id, // 当前层级（下一级）的 ID
                            currentParentId,    // 【传入】上一层的 ID
                            selectedPos, 
                            false,  // 不是筛选
                            ""     // 无筛选 key
                        );
		                isFilterMode = false; //来自筛选列表
                        backStack.push(node); //xuameng保存堆栈
                        searchAdapter.setNewData(new ArrayList<>());
                        showLoading();
                        mGridView.setVisibility(View.VISIBLE);
                        mGridViewFilter.setVisibility(View.GONE);
                        sourceViewModel.getListFromSearch(currentSortData, page, video.sourceKey);  //xuameng返回列表
                        getListIng = true;   // 判断是否正在加载下级列表
                        return;
                    }  
                    //xuameng 如有下一级直接getListFromSearch 加载列表完成

                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    bundle.putString("picture", video.pic);   //xuameng某些网站图片部显示
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });

        // xuameng 搜索展示 0文字列表 
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0){
            mGridViewFilter.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));           
        }else{  // xuameng 搜索展示 1缩略图
            mGridViewFilter.setLayoutManager(new V7GridLayoutManager(this.mContext, 5));
        }
        searchAdapterFilter = new FastSearchAdapter();
        mGridViewFilter.setAdapter(searchAdapterFilter);
        searchAdapterFilter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapterFilter.getData().get(position);
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
                    //xuameng 如有下一级直接getListFromSearch 加载列表
                    if (video.tag != null && (video.tag.equals("folder") || video.tag.equals("cover"))) {  
                        isTopSearchStage = false;   // 关闭全局搜索结果写入
                        isNextLevelFilter = true;  //进入过下一级分类
                        // 【关键】在修改 currentSortData.id 之前，先把当前的 ID 保存下来，它就是父级 ID
                        String currentParentId = currentSortData.id; 
                        currentSortData.id = video.id;
                        int selectedPosFilter = position;

                        // 【关键】把父级 ID 传入 BackNode
                        BackNode node = new BackNode(
                            video.sourceKey,
                            currentSortData.id, // 当前层级（下一级）的 ID
                            currentParentId,    // 【传入】上一层的 ID
                            selectedPosFilter, 
                            true,           // 来自筛选
                            searchFilterKey// 当前筛选 key
                        );
		                isFilterMode = true; //来自筛选列表
                        backStack.push(node); //xuameng保存堆栈
                        searchAdapterFilter.setNewData(new ArrayList<>());
                        showLoading();
                        mGridView.setVisibility(View.GONE);
                        mGridViewFilter.setVisibility(View.VISIBLE);
                        sourceViewModel.getListFromSearch(currentSortData, page, video.sourceKey);
                        getListIng = true;   // 判断是否正在加载下级列表
                        return;
                    }  
                    //xuameng 如有下一级直接getListFromSearch 加载列表完成
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    bundle.putString("picture", video.pic);   //xuameng某些网站图片部显示
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });

        setLoadSir(llLayout);

        // 分词
        searchWordAdapter = new SearchWordAdapter();
        mGridViewWordFenci = findViewById(R.id.mGridViewWordFenci);
        mGridViewWordFenci.setAdapter(searchWordAdapter);
        mGridViewWordFenci.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        searchWordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                String str = searchWordAdapter.getData().get(position);
                search(str);
            }
        });
        searchWordAdapter.setNewData(new ArrayList<>());
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);

        ///xuameng：folder / cover 下级 监听返回结果
        sourceViewModel.listResult.observe(this, absXml -> {
            if (!getListIng){ // 判断是否正在加载下级列表
                return;
            }
            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                // ✅ 写缓存（核心）
                // ✅ 从返回栈中取当前 node
                 if (!backStack.isEmpty()) {
                    BackNode node = backStack.peek();
                    // ✅ 正确 cacheKey
                    String cacheKey = node.sourceKey + "_" + currentSortData.id;
                    folderCache.put(cacheKey, absXml.movie.videoList);
                }
                showSuccess();
                if (isFilterMode) {
                    searchAdapterFilter.setNewData(absXml.movie.videoList);
                    scrollAndSelectAfterLayout(mGridViewFilter, 0);
                } else {
                    searchAdapter.setNewData(absXml.movie.videoList);
                    scrollAndSelectAfterLayout(mGridView, 0);
                }
            } else {
                showEmpty();
            }
        });
        ///xuameng：folder / cover 下级 监听返回结果完

    }

    private void filterResult(String spName) {   //xuameng 左侧列表执行逻辑
        if (spName == "全部") {
            mGridView.setVisibility(View.VISIBLE);
            mGridViewFilter.setVisibility(View.GONE);
            getListIng = false;
            searchFilterKey = "";
            if (isNextLevelFilter){
                backStack.clear();
                folderCache.clear(); // xuameng 清空中间层缓存数据
                showSuccess();
                isNextLevelFilter = false;
            }
            if (isNextLevel){  //xuameng 进入过下一级
                backStack.clear();
                folderCache.clear(); // xuameng 清空中间层缓存数据
                showSuccess();
                isNextLevel = false;  //进入过下一级重置
                if (!topSearchCache.isEmpty()) {
                    searchAdapter.setNewData(topSearchCache);
                }
                mGridViewWord.requestFocus();    //xuameng 指定搜索列表为焦点 防止系统乱分配焦点 防止下面滚动位置不对的BUG
                mGridView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            mGridView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            TvRecyclerView.LayoutManager lm = mGridView.getLayoutManager();
                            if (lm == null) return;
                            // xuameng在这里滚
                            lm.scrollToPosition(firstSelectedPos);
                            // xuameng在这里只滚动不选中焦点
                            mGridView.post(() -> {
                                mGridView.setSelectedPosition(firstSelectedPos);
                            });
                        }
                    }
                );				
                // 如果搜索还没结束，继续展示
                if (!topSearchCompleted) {
                    isTopSearchStage = true;   // 打开全局搜索结果写入
                    ContinueSearchExecutor();
                } 
            }
            return;
        }
        mGridView.setVisibility(View.GONE);
        mGridViewFilter.setVisibility(View.VISIBLE);
        String key = spNames.get(spName);
        if (key.isEmpty()) return;
        if (searchFilterKey == key) return;
        showSuccess();
        searchFilterKey = key;
        getListIng = false;
        folderCache.clear(); // xuameng 清空中间层缓存数据
        backStack.clear();
        List<Movie.Video> list = resultVods.get(key);
        searchAdapterFilter.setNewData(list); 
        // 搜索还没结束，继续展示
        if (!topSearchCompleted) {
            isTopSearchStage = true;   // 打开全局搜索结果写入
            ContinueSearchExecutor();
        }
    }

    private void fenci() {
        if (!quickSearchWord.isEmpty()) return; // 如果经有分词了，不再进行二次分词
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
        // 分词
//        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
//                .tag("fenci")
//                .execute(new AbsCallback<String>() {
//                    @Override
//                    public String convertResponse(okhttp3.Response response) throws Throwable {
//                        if (response.body() != null) {
//                            return response.body().string();
//                        } else {
//                            throw new IllegalStateException("网络请求错误");
//                        }
//                    }
//
//                    @Override
//                    public void onSuccess(Response<String> response) {
//                        String json = response.body();
//                        quickSearchWord.clear();
//                        try {
//                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
//                                quickSearchWord.add(je.getAsJsonObject().get("t").getAsString());
//                            }
//                        } catch (Throwable th) {
//                            th.printStackTrace();
//                        }
//                        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
//                        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
//                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
//                    }
//
//                    @Override
//                    public void onError(Response<String> response) {
//                        super.onError(response);
//                    }
//                });
    }

    private void initData() {
        initCheckedSourcesForSearch();
        Intent intent = getIntent();
		showSuccess();  //xuameng 
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            search(title);
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
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD) {
            if (event.obj != null) {
                List<String> data = (List<String>) event.obj;
                searchWordAdapter.setNewData(data);
            }
        }
        if (mSearchTitle != null) {
            mSearchTitle.setText(String.format("搜索(%d/%d)", resultVods.size(), spNames.size()));
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private void search(String title) {
		if (TextUtils.isEmpty(title)){
            App.showToastShort(FastSearchActivity.this, "输入内容不能为空！");
			return;
		}
        folderCache.clear(); // xuameng 清空中间层缓存数据
        isTopSearchStage = true;   // 开启全局搜索阶段
        backStack.clear();  //xuameng清空节点数据确保数据初始化状态
        topSearchCompleted = false;  // xuameng搜索完成重置
        topSearchCache.clear();  // xuameng搜索缓存重置
        cancel();
        this.searchTitle = title;
        fenci();
        mGridView.setVisibility(View.INVISIBLE);
        mGridViewFilter.setVisibility(View.GONE);
        searchAdapter.setNewData(new ArrayList<>());
        searchAdapterFilter.setNewData(new ArrayList<>());
        spListAdapter.reset();
        resultVods.clear();
        searchFilterKey = "";
        isFilterMode = false;
        getListIng = false;
        spNames.clear();
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
            searchAdapterFilter.setNewData(new ArrayList<>());
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
        ArrayList<String> hots = new ArrayList<>();
        spListAdapter.setNewData(hots);
        spListAdapter.addData("全部");
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
            this.spNames.put(bean.getName(), bean.getKey());
            allRunCount.incrementAndGet();
        }
        if (siteKey.size() <= 0) {
            App.showToastShort(FastSearchActivity.this, "聚汇影视提示：请指定搜索源！");
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
            App.showToastLong(FastSearchActivity.this, message);
        } else {
            App.showToastShort(FastSearchActivity.this, message);
        }
    }


    // 向过滤栏添加有结果的spname
    private String addWordAdapterIfNeed(String key) {
        try {
            String name = "";
            for (String n : spNames.keySet()) {
                if (spNames.get(n) == key) {
                    name = n;
                }
            }
            if (name == "") return key;

            List<String> names = spListAdapter.getData();
            for (int i = 0; i < names.size(); ++i) {
                if (name == names.get(i)) {
                    return key;
                }
            }

            spListAdapter.addData(name);
            return key;
        } catch (Exception e) {
            return key;
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

    private void searchData(AbsXml absXml) {
        // 已经进入子级，直接丢弃全局搜索结果
        if (!isTopSearchStage) {
            return;
        }
        String lastSourceKey = "";

        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (!matchSearchResult(video.name, searchTitle)) continue;
                data.add(video);
                if (!resultVods.containsKey(video.sourceKey)) {
                    resultVods.put(video.sourceKey, new ArrayList<Movie.Video>());
                }
                resultVods.get(video.sourceKey).add(video);
                if (video.sourceKey != lastSourceKey) {
                    lastSourceKey = this.addWordAdapterIfNeed(video.sourceKey);
                }
            }

            if (searchAdapter.getData().isEmpty()) {
                if (data != null && !data.isEmpty()){
	                showSuccess();   //xuameng 修复loading隐藏BUG只有真正获取到数据才隐藏
                }
                if (!isFilterMode)
                    mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data);
                // xuameng 搜索缓存 有下一级时有缓存不用重搜
                topSearchCache.addAll(data); //xuameng 增加搜索缓存
                topSearchCompleted = false;  // xuameng搜索完成
               // xuameng 搜索缓存 有下一级时有缓存不用重搜完
            } else {
                searchAdapter.addData(data);
                topSearchCache.addAll(data);  // xuameng 搜索缓存 有下一级时有缓存不用重搜
            }

        }

        int count = allRunCount.decrementAndGet();
        if (count <= 0) {
            topSearchCompleted = true;  //xuameng 搜索完成
            if (searchAdapter.getData().size() <= 0) {
                showEmpty();
            }
            stopSearchExecutor();
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
        stopSearchExecutor();
        cancel();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (!backStack.isEmpty()) {
            getListIng = false;
            App.HideToast();
            BackNode node = backStack.pop();
            showLoading();

            // 情况 1：从「筛选列表的下一级」返回到筛选页
            if (node.isFilterMode  && backStack.isEmpty()) {
                mGridView.setVisibility(View.GONE);
                mGridViewFilter.setVisibility(View.VISIBLE);
                isNextLevelFilter = false;
                // 直接从缓存恢复
                List<Movie.Video> list = resultVods.get(node.filterKey);
                if (list != null && !list.isEmpty()) {
                    searchAdapterFilter.setNewData(list);
                    showSuccess();
                    scrollAndSelectAfterLayout(mGridViewFilter, node.lastSelectedPosition);
                }

                if (!topSearchCompleted) {
                    isTopSearchStage = true;   // 打开全局搜索结果写入
                    ContinueSearchExecutor();
                }
                return;
            }

            // 情况 2：全部列表
            if (backStack.isEmpty()) {
                mGridViewFilter.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
                isNextLevel = false;  //进入过下一级
                if (!topSearchCache.isEmpty()) {
                    searchAdapter.setNewData(topSearchCache);
                    showSuccess();
                    if (node.lastSelectedPosition >= 0 && node.lastSelectedPosition < topSearchCache.size()) {
                        scrollAndSelectAfterLayout(mGridView, node.lastSelectedPosition);
                    }
                }

                if (!topSearchCompleted) {
                    isTopSearchStage = true;   // 打开全局搜索结果写入
                    ContinueSearchExecutor();
                }
                return;
            }

            //  情况 3：中间层级（folder 嵌套）
            currentSortData.id = node.parentSortId;   //xuameng 变成上级ID

			// 缓存 key
            String cacheKey = node.sourceKey + "_" + node.parentSortId;

            // 关键：恢复 UI 状态
            if (node.isFilterMode) {
                searchAdapterFilter.setNewData(new ArrayList<>());
                showLoading();
                mGridViewFilter.setVisibility(View.VISIBLE);
                mGridView.setVisibility(View.GONE);   
            } else {
                searchFilterKey = "";
                searchAdapter.setNewData(new ArrayList<>());
                showLoading();
                mGridViewFilter.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
            }

            // ✅ 优先读缓存
            List<Movie.Video> cachedList = folderCache.get(cacheKey);
            if (cachedList != null && !cachedList.isEmpty()) {
                // ✅ 有缓存：直接用，不请求网络
                getListIng = false;
                showSuccess();

               if (node.isFilterMode) {
                   searchAdapterFilter.setNewData(cachedList);
                   scrollAndSelectAfterLayout(mGridViewFilter, node.lastSelectedPosition);
               } else {
                   searchAdapter.setNewData(cachedList);
                   scrollAndSelectAfterLayout(mGridView, node.lastSelectedPosition);
               }
               return;
           }

           // ❌ 无缓存：再走网络
           getListIng = true;
           showLoading();
           sourceViewModel.getListFromSearch(currentSortData, page, node.sourceKey);
           return;
        }

        //  真正退出 Activity
        isActivityDestroyed = true;
        stopSearchExecutor();
        cancel();
        App.HideToast();
        super.onBackPressed();
    }

    /**
     * xuameng等待布局完成，然后滚动并选中指定位置
     *
     * @param recyclerView 目标 TvRecyclerView
     * @param position     要选中的位置
     */
    private void scrollAndSelectAfterLayout(TvRecyclerView recyclerView, int position) {
        if (recyclerView == null) return;

        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // 防止重复回调
                    recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    TvRecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                    if (lm == null) return;

                    // 滚动
                    lm.scrollToPosition(position);

                    // 选中焦点
                    recyclerView.post(() ->
                        recyclerView.setSelection(position)
                    );
                }
            }
        );
    }

    public void stopSearchExecutor() {  //停止搜索
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void ContinueSearchExecutor() {  //继续搜索
        if (searchExecutorService != null) {
            // 已经在搜索中，不允许重复启动
            return;
        }

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
    }
}
