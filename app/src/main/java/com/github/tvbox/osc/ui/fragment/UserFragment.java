package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import com.github.tvbox.osc.base.App;  //xuameng toast
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.PushActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapterXu;      //xuameng首页单行
import com.github.tvbox.osc.ui.dialog.xuamengAboutDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.DefaultConfig;  //xuameng长按许大师制作重启APP
import com.github.tvbox.osc.ui.activity.HomeActivity;  //xuameng长按历史键重新载入主页数据
import com.github.tvbox.osc.util.ImgUtil;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.UA;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import com.github.tvbox.osc.util.FileUtils;  //xuameng 清缓存
import java.io.File;   //xuameng 清缓存

/**
 * @author pj567
 * @date :2021/3/9
 * @description:
 */
public class UserFragment extends BaseLazyFragment implements View.OnClickListener {
    private LinearLayout tvLive;
    private LinearLayout tvSearch;
    private LinearLayout tvSetting;
    private LinearLayout tvHistory;
    private LinearLayout tvCollect;
    private LinearLayout tvPush;
    public static HomeHotVodAdapter homeHotVodAdapter;
    public static HomeHotVodAdapterXu homeHotVodAdapterxu;  //xuameng首页单行
    private List<Movie.Video> homeSourceRec;
    public static TvRecyclerView tvHotList1;   
    public static TvRecyclerView tvHotList2;   //xuameng首页单行
    private ImgUtil.Style style;   //xuameng 图片样式

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    public static UserFragment newInstance(List<Movie.Video> recVod) {
        return new UserFragment().setArguments(recVod);
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    @Override
    protected void onFragmentResume() {
        if(Hawk.get(HawkConfig.HOME_REC_STYLE, false)){
            tvHotList1.setVisibility(View.VISIBLE);
            tvHotList2.setVisibility(View.GONE);
            tvHotList1.setHasFixedSize(true);
            int spanCount = 5;
            if(style!=null && Hawk.get(HawkConfig.HOME_REC, 0) == 1)spanCount=ImgUtil.spanCountByStyle(style,spanCount);
            tvHotList1.setLayoutManager(new V7GridLayoutManager(this.mContext, spanCount));
        }else {
            tvHotList1.setVisibility(View.GONE);
            tvHotList2.setVisibility(View.VISIBLE);
            //	tvHotList2.setHasFixedSize(true);      //xuameng不想显示单行
            //    tvHotList2.setLayoutManager(new V7GridLayoutManager(this.mContext, 5));
        }
        super.onFragmentResume();
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);    //xuameng首页历史条数
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                    vod.note = "上次看到" + vodInfo.playNote;
                vodList.add(vod);
            }
            if(!Hawk.get(HawkConfig.HOME_REC_STYLE, false)){
                homeHotVodAdapterxu.setNewData(vodList);   //xuameng首页多行
            }else{
                homeHotVodAdapter.setNewData(vodList);  //xuameng首页单行
            }
        }
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvCollect = findViewById(R.id.tvFavorite);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.tvPush);
        tvLive.setOnClickListener(this);
        tvSearch.setOnClickListener(this);
        tvSetting.setOnClickListener(this);
        tvHistory.setOnClickListener(this);
        tvPush.setOnClickListener(this);
        tvCollect.setOnClickListener(this);
        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);
        tvSetting.setOnFocusChangeListener(focusChangeListener);
        tvHistory.setOnFocusChangeListener(focusChangeListener);
        tvPush.setOnFocusChangeListener(focusChangeListener);
        tvCollect.setOnFocusChangeListener(focusChangeListener);
        tvHotList1 = findViewById(R.id.tvHotList1);
        tvHotList2 = findViewById(R.id.tvHotList2);
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && homeSourceRec!=null) {
            style=ImgUtil.initStyle();
        }
        homeHotVodAdapter = new HomeHotVodAdapter(style);
        homeHotVodAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty())
                    return;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                
                // takagen99: CHeck if in Delete Mode
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2) && HawkConfig.hotVodDelete) {
                    homeHotVodAdapter.remove(position);
                    VodInfo vodInfo = RoomDataManger.getVodInfo(vod.sourceKey, vod.id);
                    RoomDataManger.deleteVodRecord(vod.sourceKey, vodInfo);
                    App.showToastShort(mContext, "已删除当前记录！");
               }  else if (vod.id != null && !vod.id.isEmpty()) {         //xuameng 修复首页聚汇推荐单击不能搜索的问题
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    if (vod.id.startsWith("msearch:")) {
                        bundle.putString("title", vod.name);
                        if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                            jumpActivity(FastSearchActivity.class, bundle);
                        }else {
                            jumpActivity(SearchActivity.class, bundle);
                        }
		    }else {
                        bundle.putString("picture", vod.pic);
                        jumpActivity(DetailActivity.class, bundle);
                    }                           //xuameng 修复首页聚汇推荐单击不能搜索的问题结束
                } else {
                    Intent newIntent;
                    if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                        newIntent = new Intent(mContext, FastSearchActivity.class);
                    }else {
                        newIntent = new Intent(mContext, SearchActivity.class);
                    }
                    newIntent.putExtra("title", vod.name);
                    newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mActivity.startActivity(newIntent);
                }
            }
        });

        homeHotVodAdapterxu = new HomeHotVodAdapterXu(style); 
        homeHotVodAdapterxu.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {   //xuameng首页单行
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty())
                    return;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                
                // takagen99: CHeck if in Delete Mode
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2) && HawkConfig.hotVodDelete) {
                    homeHotVodAdapterxu.remove(position);
                    VodInfo vodInfo = RoomDataManger.getVodInfo(vod.sourceKey, vod.id);
                    RoomDataManger.deleteVodRecord(vod.sourceKey, vodInfo);
                    App.showToastShort(mContext, "已删除当前记录！");
                } else if (vod.id != null && !vod.id.isEmpty()) {         //xuameng 修复首页聚汇推荐单击不能搜索的问题
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    if (vod.id.startsWith("msearch:")) {
                        bundle.putString("title", vod.name);
                        if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                            jumpActivity(FastSearchActivity.class, bundle);
                        }else {
                            jumpActivity(SearchActivity.class, bundle);
                        }
		    }else {
                        bundle.putString("picture", vod.pic);   //xuameng某些网站图片部显示
                        jumpActivity(DetailActivity.class, bundle);
                    }                           //xuameng 修复首页聚汇推荐单击不能搜索的问题结束
                } else {
                    Intent newIntent;
                    if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                        newIntent = new Intent(mContext, FastSearchActivity.class);
                    }else {
                        newIntent = new Intent(mContext, SearchActivity.class);
                    }
                    newIntent.putExtra("title", vod.name);
                    newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mActivity.startActivity(newIntent);
                }
            }
        });
           //xuameng : start
        findViewById(R.id.tvHistory).setOnLongClickListener(new View.OnLongClickListener() {       //xuameng长按历史键重载主页数据
            @Override
            public boolean onLongClick(View v) {
                FastClickCheckUtil.check(v);
                Intent intent =new Intent(mContext, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                Bundle bundle = new Bundle();
                bundle.putBoolean("useCache", true);
                intent.putExtras(bundle);
                startActivity(intent);
                App.showToastShort(mContext, "重新加载主页数据！");
                return true;
            }
        });
		
        findViewById(R.id.tvxuameng).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                xuamengAboutDialog dialog = new xuamengAboutDialog(mActivity);
                dialog.show();
            }
        });

        findViewById(R.id.tvxuameng).setOnLongClickListener(new View.OnLongClickListener() {       //xuameng长按许大师制作重启APP
            @Override
            public boolean onLongClick(View v) {
                FastClickCheckUtil.check(v);
                DefaultConfig.restartApp();
                return true;
            }
        });

        findViewById(R.id.tvSetting).setOnLongClickListener(new View.OnLongClickListener() {       //xuameng长按设置清空缓存
            @Override
            public boolean onLongClick(View v) {
                FastClickCheckUtil.check(v);
                String cachePath = FileUtils.getCachePath();
                File cacheDir = new File(cachePath);
                String cspCachePath = FileUtils.getFilePath()+"/csp/";
                File cspCacheDir = new File(cspCachePath);
                if (!cacheDir.exists() && !cspCacheDir.exists()) return true;
                new Thread(() -> {
                    try {
                        if(cacheDir.exists())FileUtils.cleanDirectory(cacheDir);
                        if(cspCacheDir.exists()){
                            FileUtils.cleanDirectory(cspCacheDir);
                            //ApiConfig.get().clearJarLoader();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                App.showToastShort(mContext, "缓存已清空！");
                return true;
            }
        });

        findViewById(R.id.tvxuameng).setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override         //xuameng许大师制作焦点变大
	        public void onFocusChange(View v, boolean hasFocus){
                if (hasFocus){
                    v.animate().scaleX(1.03f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }else{
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
	    }
        });
        //xuameng : end
        // takagen99 : Long press to trigger Delete Mode for VOD History on Home Page       
        homeHotVodAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty()) return false;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                // Additional Check if : Home Rec 0=豆瓣, 1=推荐, 2=历史
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2)) {
                    HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
                    homeHotVodAdapter.notifyDataSetChanged();
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vod.name);
                    jumpActivity(FastSearchActivity.class, bundle);                    
                }
                return true;
            }    
        });

        homeHotVodAdapterxu.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty()) return false;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                // Additional Check if : Home Rec 0=豆瓣, 1=推荐, 2=历史
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2)) {
                    HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
                    homeHotVodAdapterxu.notifyDataSetChanged();
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vod.name);
                    jumpActivity(FastSearchActivity.class, bundle);                    
                }
                return true;
            }    
        });

        tvHotList1.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        tvHotList1.setAdapter(homeHotVodAdapter);
        tvHotList2.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        tvHotList2.setAdapter(homeHotVodAdapterxu);   //xuameng首页单行

        initHomeHotVodXu(homeHotVodAdapterxu);   //xuameng首页单行

        initHomeHotVod(homeHotVodAdapter);
		
    }

    private void initHomeHotVod(HomeHotVodAdapter adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (homeSourceRec != null) {
                adapter.setNewData(homeSourceRec);
                return;
            }
        } else if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            return;
        }
        setDouBanData(adapter);
    }

    private void initHomeHotVodXu(HomeHotVodAdapterXu adapter) {  //xuameng首页单行
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (homeSourceRec != null) {
                adapter.setNewData(homeSourceRec);
                return;
            }
        } else if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            return;
        }
        setDouBanDataXu(adapter);
    }

    private void setDouBanData(HomeHotVodAdapter adapter) {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    ArrayList<Movie.Video> hotMovies = loadHots(json);
                    if (hotMovies != null && hotMovies.size() > 0) {
                        adapter.setNewData(hotMovies);
                        return;
                    }
                }
            }
            String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            Hawk.put("home_hot_day", today);
                            Hawk.put("home_hot", netJson);
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.setNewData(loadHots(netJson));
                                }
                            });
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void setDouBanDataXu(HomeHotVodAdapterXu adapter) {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    ArrayList<Movie.Video> hotMovies = loadHots(json);
                    if (hotMovies != null && hotMovies.size() > 0) {
                        adapter.setNewData(hotMovies);
                        return;
                    }
                }
            }
            String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            Hawk.put("home_hot_day", today);
                            Hawk.put("home_hot", netJson);
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.setNewData(loadHots(netJson));
                                }
                            });
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                if(!vod.note.isEmpty())vod.note+=" 分";
                vod.pic = obj.get("cover").getAsString()+"@User-Agent="+ UA.randomOne()+"@Referer=https://www.douban.com/";
                result.add(vod);
            }
        } catch (Throwable th) {

        }
        return result;
    }

    private View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus)
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            else
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
        }
    };

    @Override
    public void onClick(View v) {
    	
    	// takagen99: Remove Delete Mode
        HawkConfig.hotVodDelete = false;
    
        FastClickCheckUtil.check(v);
        if (v.getId() == R.id.tvLive) {
            jumpActivity(LivePlayActivity.class);
        } else if (v.getId() == R.id.tvSearch) {
            jumpActivity(SearchActivity.class);
        } else if (v.getId() == R.id.tvSetting) {
            jumpActivity(SettingActivity.class);
        } else if (v.getId() == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (v.getId() == R.id.tvPush) {
            jumpActivity(PushActivity.class);
        } else if (v.getId() == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_CONNECTION) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
