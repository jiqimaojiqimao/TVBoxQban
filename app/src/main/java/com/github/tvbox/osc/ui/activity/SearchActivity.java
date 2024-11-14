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
import android.widget.Toast;

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
import com.github.tvbox.osc.util.js.JSEngine;
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
import androidx.constraintlayout.widget.ConstraintLayout; //xuameng搜索历史
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;   //xuameng搜索历史

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

    /*
     * 禁止软键盘
     * @param activity Activity
     */
    public static void disableKeyboard(Activity activity) {
        hasKeyBoard = false;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    /*
     * 启用软键盘
     * @param activity Activity
     */
    public static void enableKeyboard(Activity activity) {
        hasKeyBoard = true;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    public void openSystemKeyBoard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(this.getCurrentFocus(), InputMethodManager.SHOW_FORCED);
    }

    private List<Runnable> pauseRunnable = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (pauseRunnable != null && pauseRunnable.size() > 0) {
            searchExecutorService = Executors.newFixedThreadPool(5);
            allRunCount.set(pauseRunnable.size());
            for (Runnable runnable : pauseRunnable) {
                searchExecutorService.execute(runnable);
            }
            pauseRunnable.clear();
            pauseRunnable = null;
        }
        if (hasKeyBoard) {
            tvSearch.requestFocus();
            tvSearch.requestFocusFromTouch();
        }else {
            if(!isSearchBack){
                etSearch.requestFocus();
                etSearch.requestFocusFromTouch();
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

				searchExecutorService = Executors.newFixedThreadPool(5);         //xuameng修复不选择搜索源还进行搜索，还显示搜索动画
				List<SourceBean> searchRequestList = new ArrayList<>();
				searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
				SourceBean home = ApiConfig.get().getHomeSourceBean();
				searchRequestList.remove(home);
				searchRequestList.add(0, home);
				ArrayList<String> siteKey = new ArrayList<>();
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
					Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
					return;
				}    //xuameng修复不选择搜索源还进行搜索，还显示搜索动画完 

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
                            JSEngine.getInstance().stopAll();
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    hasKeyBoard = false;
                    isSearchBack = true;
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
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

				searchExecutorService = Executors.newFixedThreadPool(5);         //xuameng修复不选择搜索源还进行搜索，还显示搜索动画
				List<SourceBean> searchRequestList = new ArrayList<>();
				searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
				SourceBean home = ApiConfig.get().getHomeSourceBean();
				searchRequestList.remove(home);
				searchRequestList.add(0, home);
				ArrayList<String> siteKey = new ArrayList<>();
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
					Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
					return;
				}    //xuameng修复不选择搜索源还进行搜索，还显示搜索动画完 

                        Bundle bundle = new Bundle();
                        bundle.putString("title", keyword);
						refreshSearchHistory(keyword);  //xuameng搜索历史
                        jumpActivity(FastSearchActivity.class, bundle);
                    }else {
                        search(keyword);
                    }
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {     
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                etSearch.setText("");
				showHotSearchtext();     //xuameng修复清空后热门搜索为空
				tv_history.setVisibility(View.VISIBLE);   //xuameng修复BUG
                searchTips.setVisibility(View.VISIBLE);
                tHotSearchText.setText("热门搜索");          //xuameng修复删除内容后，热门搜索为空
				showSuccess();  //xuameng修复BUG
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

        etSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Toast.makeText(mContext,"点击",Toast.LENGTH_SHORT).show();
                if (!hasKeyBoard) enableKeyboard(SearchActivity.this);
                openSystemKeyBoard();//再次尝试拉起键盘
                SearchActivity.this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
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
						showHotSearchtext();   //xuameng修复清空后热门搜索为空
                        tHotSearchText.setText("热门搜索");
						showSuccess();  //xuameng修复BUG
						tv_history.setVisibility(View.VISIBLE);   //xuameng修复BUG
						searchTips.setVisibility(View.VISIBLE);
                    }
                } else if (pos == 0) {
                    RemoteDialog remoteDialog = new RemoteDialog(mContext);
                    remoteDialog.show();
                }
            }
        });
        setLoadSir(llLayout);
        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchCheckboxDialog == null) {
                    List<SourceBean> allSourceBean = ApiConfig.get().getSourceBeanList();
                    List<SourceBean> searchAbleSource = new ArrayList<>();
                    for(SourceBean sourceBean : allSourceBean) {
                        if (sourceBean.isSearchable()) {
                            searchAbleSource.add(sourceBean);
                        }
                    }
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
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

    private void refreshSearchHistory(String keyword2) {         //xuameng 搜索历史
        if (!this.searchPresenter.keywordsExist(keyword2)) {
            this.searchPresenter.addKeyWordsTodb(keyword2);
            initSearchHistory();
        }
    }

    private void initSearchHistory() {
        ArrayList<SearchHistory> searchHistory = this.searchPresenter.getSearchHistory();
        List<String> historyList = new ArrayList<>();
        for (SearchHistory history : searchHistory) {
            historyList.add(history.searchKeyWords);
        }
        Collections.reverse(historyList);
        tv_history.setViews(historyList, new FlowLayout.OnItemClickListener() {
            public void onItemClick(String content) {
                etSearch.setText(content);
                if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)) {

				searchExecutorService = Executors.newFixedThreadPool(5);         //xuameng修复不选择搜索源还进行搜索，还显示搜索动画
				List<SourceBean> searchRequestList = new ArrayList<>();
				searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
				SourceBean home = ApiConfig.get().getHomeSourceBean();
				searchRequestList.remove(home);
				searchRequestList.add(0, home);
				ArrayList<String> siteKey = new ArrayList<>();
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
					Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
					return;
				}    //xuameng修复不选择搜索源还进行搜索，还显示搜索动画完 

                    Bundle bundle = new Bundle();
                    bundle.putString("title", content);
                    refreshSearchHistory(content);
                    jumpActivity(FastSearchActivity.class, bundle);
                } else {
                    search(content);
                    //etSearch.setSelection(etSearch.getText().length());
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
            showLoading();
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
            wordAdapter.setNewData(hots);
            return;
        }
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_search")
//        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
//                .params("cat", "1")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonObject().get("mapResult").getAsJsonObject().get("0").getAsJsonObject().get("listInfo").getAsJsonArray();
//                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
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
        if (remoteDialog != null) {
            remoteDialog.dismiss();
            remoteDialog = null;
        }
        cancel();
        searchExecutorService = Executors.newFixedThreadPool(5);         //xuameng修复不选择搜索源还进行搜索，还显示搜索动画
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);
        ArrayList<String> siteKey = new ArrayList<>();
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
			Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
            return;
        }           //xuameng修复不选择搜索源还进行搜索，还显示搜索动画完

        showLoading();        //xuameng 转圈动画
        etSearch.setText(title);
        this.searchTitle = title;
        mGridView.setVisibility(View.GONE); //xuameng 搜索历史
        searchAdapter.setNewData(new ArrayList<>());
		refreshSearchHistory(title);  //xuameng 搜索历史
        searchResult();
    }

    private ExecutorService searchExecutorService = null;
    private AtomicInteger allRunCount = new AtomicInteger(0);

    private void searchResult() {
        try {
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
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
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
   //         showEmpty();  //xuameng
            return;
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getSearch(key, searchTitle);
                }
            });
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
                showEmpty();		//xuameng修复BUG
                tv_history.setVisibility(View.VISIBLE);   //xuameng修复BUG
                searchTips.setVisibility(View.VISIBLE);
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
        cancel();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JSEngine.getInstance().stopAll();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }

    public void showHotSearchtext() {          //xuameng 热搜
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_search")
//        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
//                .params("cat", "1")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonObject().get("mapResult").getAsJsonObject().get("0").getAsJsonObject().get("listInfo").getAsJsonArray();
//                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
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
