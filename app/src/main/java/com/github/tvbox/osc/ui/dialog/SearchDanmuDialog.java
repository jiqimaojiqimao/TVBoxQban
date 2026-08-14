package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.DanmuSearchResult;
import com.github.tvbox.osc.ui.adapter.SearchDanmuAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.base.App;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xuameng
 * @date :2026/08/14
 * @description:   弹幕在线搜索
 */

public class SearchDanmuDialog extends BaseDialog {

    // ============ 缓存（static，不持有 Dialog 引用） ============
    public static class DanmuCache {
        public static final List<DanmuSearchResult> lastResults = new ArrayList<>();
        public static String lastSearchWord = "";
        public static String lastEpisode = "";
    }

    // ============ 成员变量 ============
    private TvRecyclerView gridView;
    private SearchDanmuAdapter searchAdapter;
    private EditText searchInput;
    private ProgressBar loadingBar;
    private DanmuLoader danmuLoader;
    private String episode = "";

    public SearchDanmuDialog(@NonNull Context context) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setContentView(R.layout.dialog_search_danmu);
        initView();
    }

    private void initView() {
        loadingBar = findViewById(R.id.loadingBar);
        gridView = findViewById(R.id.mGridView);
        searchInput = findViewById(R.id.input);
        TextView searchButton = findViewById(R.id.inputSubmit);

        searchAdapter = new SearchDanmuAdapter();
        gridView.setHasFixedSize(true);
        gridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        gridView.setAdapter(searchAdapter);

        // 点击条目加载弹幕
        searchAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            loadDanmu(searchAdapter.getData().get(position));
        });

        // 点击搜索按钮
        searchButton.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            search(searchInput.getText().toString().trim());
        });

        // 初始空数据（不触发清空动画）
        searchAdapter.setNewData(new ArrayList<>());
    }

    // ============ 对外方法 ============

    public void setEpisode(String episode) {
        this.episode = episode == null ? "" : episode;
        DanmuCache.lastEpisode = this.episode;
    }

    public void setSearchWord(String word) {
        String searchWord = word == null ? "" : word.trim();
    
        // ===== 核心判断：剧集名称变了没 =====
        boolean nameChanged = !searchWord.equals(DanmuCache.lastSearchWord);
    
        if (nameChanged) {
            // 名称变了 → 清空旧缓存，写入新名称
            DanmuCache.lastResults.clear();
            DanmuCache.lastSearchWord = searchWord;
        }
    
        // 输入框始终显示当前名称
        searchInput.setText(searchWord);
        searchInput.setSelection(searchWord.length());
        searchInput.requestFocus();
    
        if (nameChanged && !searchWord.isEmpty()) {
            // 名称变了 → 自动搜索新剧集
            // 注意：这里不自动搜索，让用户自己按回车/点击
            // 如果一定要自动搜，取消下面注释：
            //search(searchWord);
        }
        // 没变 → 不自动搜，等 show() 恢复缓存
    }

    public void setDanmuLoader(DanmuLoader danmuLoader) {
        this.danmuLoader = danmuLoader;
    }

    // ============ 搜索 ============

    private void search(String word) {
        if (TextUtils.isEmpty(word)) {
            App.showToastShort(getContext(), "输入内容不能为空！");
            return;
        }

        // 保存搜索词，方便下次恢复
        DanmuCache.lastSearchWord = word;

        showLoading();

        DanmakuApi.searchList(word, episode, new DanmakuApi.SearchListCallback() {
            @Override
            public void onSuccess(List<DanmuSearchResult> results) {
                // 缓存结果
                DanmuCache.lastResults.clear();
                if (results != null) {
                    DanmuCache.lastResults.addAll(results);
                }
                showResults(DanmuCache.lastResults);
            }

            @Override
            public void onError(String message) {
                DanmuCache.lastResults.clear();
                showResults(new ArrayList<>());
                App.showToastShort(getContext(), message);
            }
        });
    }

    // ============ 加载弹幕 ============

    private void loadDanmu(DanmuSearchResult result) {
        showLoading();
        DanmakuApi.loadSearchResult(result, new DanmakuApi.SearchResultCallback() {
            @Override
            public void onSuccess(String danmu) {
                if (danmuLoader != null) {
                    danmuLoader.loadDanmu(danmu);
                }
                App.showToastShort(getContext(), "弹幕加载成功！");
                dismiss();
            }

            @Override
            public void onError(String message) {
                loadingBar.setVisibility(View.GONE);
                gridView.setVisibility(View.VISIBLE);
                App.showToastShort(getContext(), message);
            }
        });
    }

    // ============ UI 状态 ============

    private void showLoading() {
        loadingBar.setVisibility(View.VISIBLE);
        gridView.setVisibility(View.GONE);
    }

    private void showResults(List<DanmuSearchResult> results) {
        if (results == null) results = new ArrayList<>();

        loadingBar.setVisibility(View.GONE);
        gridView.setVisibility(View.VISIBLE);
        searchAdapter.setNewData(results);

        if (results.isEmpty()) {
            App.showToastShort(getContext(), "未查询到匹配弹幕！");
            return;
        }
        gridView.requestFocus();
    }

    // ============ 关键：show() 时恢复缓存 ============

    @Override
    public void show() {
        super.show();

        // 恢复 episode
        if (!DanmuCache.lastEpisode.isEmpty()) {
            episode = DanmuCache.lastEpisode;
        }

        // 有缓存 → 恢复列表（名称没变的情况）
        if (!DanmuCache.lastResults.isEmpty()) {
            showResults(DanmuCache.lastResults);
        } else {
            // 没缓存 → 输入框获取焦点（名称变了已自动搜索，这里兜底）
            searchInput.requestFocus();
        }
    }

    // ============ 返回键 ============

    @Override
    public void onBackPressed() {
        DanmakuApi.cancel();
        dismiss();
    }

    // ============ 接口 ============

    public interface DanmuLoader {
        void loadDanmu(String danmu);
    }
}