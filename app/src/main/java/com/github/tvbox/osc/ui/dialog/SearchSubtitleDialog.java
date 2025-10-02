package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.KeyEvent;       //xuameng Enter  隐藏软键盘问题
import android.view.inputmethod.InputMethodManager;  //xuameng Enter  隐藏软键盘问题
import android.view.inputmethod.EditorInfo;
import com.github.tvbox.osc.base.App;  //xuameng toast

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Subtitle;
import com.github.tvbox.osc.bean.SubtitleData;
import com.github.tvbox.osc.ui.adapter.SearchSubtitleAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.viewmodel.SubtitleViewModel;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SearchSubtitleDialog extends BaseDialog {

    private Context mContext;
    private TvRecyclerView mGridView;
    private SearchSubtitleAdapter searchAdapter;

    private TextView subtitleSearchBtn;
    private EditText subtitleSearchEt;
    private SubtitleLoader mSubtitleLoader;
    private ProgressBar loadingBar;
    private SubtitleViewModel subtitleViewModel;
    private int page = 1;
    private int maxPage = 5;
    private String searchWord = "";

    private List<Subtitle> zipSubtitles = new ArrayList<>();
    private boolean isSearchPag = true;


    public SearchSubtitleDialog(@NonNull @NotNull Context context) {
        super(context);
        mContext = context;
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setContentView(R.layout.dialog_search_subtitle);
        initView(context);
        initViewModel();
    }

    protected void initView(Context context) {
        loadingBar = findViewById(R.id.loadingBar);
        mGridView = findViewById(R.id.mGridView);
        subtitleSearchEt = findViewById(R.id.input);
        subtitleSearchBtn = findViewById(R.id.inputSubmit);
        searchAdapter = new SearchSubtitleAdapter();
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Subtitle subtitle = searchAdapter.getData().get(position);
                //加载字幕
                if (mSubtitleLoader != null) {
                    if (subtitle.getIsZip()) {
                        isSearchPag = false;
                        loadingBar.setVisibility(View.VISIBLE);
                        mGridView.setVisibility(View.GONE);
                        subtitleViewModel.getSearchResultSubtitleUrls(subtitle);
                    } else {        //xuameng搜索在线字幕重复点击相同字幕文件空指针问题
                        if (TextUtils.isEmpty(subtitle.getUrl())){
                            App.showToastShort(getContext(), "网络链接已失效,请重新搜索！");
                            return;
                        }
                        loadSubtitle(subtitle);
                        dismiss();
                    }
                }
            }
        });

        searchAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                if (searchAdapter.getData().get(0).getIsZip()) {
                    subtitleViewModel.searchResult(searchWord, page);
                }
            }
        }, mGridView);

        // xuameng : Fix on Key Enter  隐藏软键盘
        subtitleSearchEt.setOnKeyListener(onSoftKeyPress);

        subtitleSearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                String wd = subtitleSearchEt.getText().toString().trim();
                search(wd);
            }
        });
        searchAdapter.setNewData(new ArrayList<>());
    }

    // xuameng : Fix on Key Enter  隐藏软键盘
    private final View.OnKeyListener onSoftKeyPress = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            InputMethodManager imm = (InputMethodManager) v.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);        
            if (imm != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && 
                    keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.requestFocus();
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP && 
                    keyCode == KeyEvent.KEYCODE_ENTER) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    subtitleSearchEt.clearFocus();
                    subtitleSearchBtn.requestFocus();
                    return true;
                }
            }
            return false;
        }
    };

subtitleSearchEt.setOnEditorActionListener((v, actionId, event) -> {
    if (actionId == EditorInfo.IME_ACTION_DONE) {
                    subtitleSearchEt.clearFocus();
                    subtitleSearchBtn.requestFocus();
        return true; // 消费事件
    }
    return false;
});


    public void setSearchWord(String wd) {
        wd = wd.replaceAll("(?:（|\\(|\\[|【|\\.mp4|\\.mkv|\\.avi|\\.MP4|\\.MKV|\\.AVI)", "");
        wd = wd.replaceAll("(?:：|\\:|）|\\)|\\]|】|\\.)", " ");
        int len = wd.length();
        int finalLen = len >= 36 ? 36 : len;
        wd = wd.substring(0, finalLen).trim();
        subtitleSearchEt.setText(wd);
        subtitleSearchEt.setSelection(wd.length());
        subtitleSearchEt.requestFocus();
    }

    public void search(String wd) {
        isSearchPag = true;
        searchAdapter.setNewData(new ArrayList<>());
        if (!TextUtils.isEmpty(wd)) {
            loadingBar.setVisibility(View.VISIBLE);
            mGridView.setVisibility(View.GONE);
            searchWord = wd;
            subtitleViewModel.searchResult(wd, page = 1);
        } else {
            App.showToastShort(getContext(), "输入内容不能为空！");
        }
    }

    private void initViewModel() {
        subtitleViewModel = new ViewModelProvider((ViewModelStoreOwner) mContext).get(SubtitleViewModel.class);
        subtitleViewModel.searchResult.observe((LifecycleOwner) mContext, new Observer<SubtitleData>() {
            @Override
            public void onChanged(SubtitleData subtitleData) {
                List<Subtitle> data = subtitleData.getSubtitleList();
                loadingBar.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
                if (data == null) {
                    mGridView.post(new Runnable() {
                        @Override
                        public void run() {
                            App.showToastShort(getContext(), "输入内容不能为空！");
                        }
                    });
                    return;
                }

                if (data.size() > 0) {
                    mGridView.requestFocus();
                    if (subtitleData.getIsZip()) {
                        if (subtitleData.getIsNew()) {
                            searchAdapter.setNewData(data);
                            zipSubtitles = data;
                        } else {
                            searchAdapter.addData(data);
                            zipSubtitles.addAll(data);
                        }
                        page++;
                        if (page > maxPage) {
                            searchAdapter.loadMoreEnd();
                            searchAdapter.setEnableLoadMore(false);
                        } else {
                            searchAdapter.loadMoreComplete();
                            searchAdapter.setEnableLoadMore(true);
                        }
                    } else {
                        searchAdapter.loadMoreComplete();
                        searchAdapter.setNewData(data);
                        searchAdapter.setEnableLoadMore(false);
                    }
                } else {
                    if (page > maxPage) {
                        searchAdapter.loadMoreEnd();
                    } else {
                        searchAdapter.loadMoreComplete();
                    }
                    searchAdapter.setEnableLoadMore(false);
                }

            }
        });
    }

    private void loadSubtitle(Subtitle subtitle) {
        subtitleViewModel.getSubtitleUrl(subtitle, mSubtitleLoader);
    }

    public void setSubtitleLoader(SubtitleLoader subtitleLoader) {
        mSubtitleLoader = subtitleLoader;
    }

    public interface SubtitleLoader {
        void loadSubtitle(Subtitle subtitle);
    }

    @Override
    public void onBackPressed() {
        if (!isSearchPag) {
            isSearchPag = true;
            loadingBar.setVisibility(View.GONE);
            mGridView.setVisibility(View.VISIBLE);
            searchAdapter.setNewData(zipSubtitles);
            searchAdapter.setEnableLoadMore(page < maxPage);
            return;
        }
        dismiss();
    }
}
