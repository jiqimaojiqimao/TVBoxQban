package com.github.tvbox.osc.ui.activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Color; //xuameng获取颜色值
import android.util.TypedValue; //xuameng TypedValue依赖
import android.view.LayoutInflater; //xuameng LayoutInflater依赖
import android.text.InputFilter; //xuameng导入依赖的package包/类
import android.content.Intent; //xuameng记忆播放频道组用
import com.github.tvbox.osc.util.HawkUtils; //xuameng记忆播放频道组用
import com.github.tvbox.osc.util.JavaUtil; //xuameng记忆播放频道组用
import android.os.Bundle; //xuameng记忆播放频道组用
import kotlin.Pair; //xuameng记忆播放频道组用
import android.widget.ProgressBar; //xuameng 播放音频时的缓冲动画
import com.squareup.picasso.Picasso; //xuameng播放音频切换图片
import com.squareup.picasso.MemoryPolicy; //xuameng播放音频切换图片
import com.squareup.picasso.NetworkPolicy; //xuameng播放音频切换图片
import android.graphics.Bitmap; //xuameng播放音频切换图片
import com.github.tvbox.osc.api.ApiConfig; //xuameng播放音频切换图片
import android.annotation.SuppressLint; //xuamengEPG显示错误
import java.util.HashMap; //XUAMENG自定义UA
import com.github.tvbox.osc.ui.tv.widget.MusicVisualizerView;  //xuameng音乐播放动画
import android.media.audiofx.Visualizer;  //xuameng音乐播放动画
import android.util.Log; //xuameng音乐播放动画
import android.os.Looper; //xuameng音乐播放动画
import android.media.AudioManager;  //xuameng音乐播放动画
import java.util.Objects;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveDayListGroup;
import com.github.tvbox.osc.bean.LiveEpgDate;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.player.controller.LiveController;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgDateAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.ui.adapter.MyEpgAdapter;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.github.tvbox.osc.util.urlhttp.CallBackUtil;
import com.github.tvbox.osc.util.urlhttp.UrlHttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject; //xuameng新增
import com.google.gson.JsonElement; //xuameng新增
import org.apache.commons.lang3.StringUtils;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import xyz.doikki.videoplayer.player.VideoView;
public class LivePlayActivity extends BaseActivity {
    public static Context context;
    private VideoView mVideoView;
    private TextView tvChannelInfo;
    private TextView tvTime;
    private TextView tvTime_xu; //xuameng的系统时间
    private TextView tvNetSpeed;
    private LinearLayout tvLeftChannelListLayout;
    private TvRecyclerView mChannelGroupView;
    private TvRecyclerView mLiveChannelView;
    private LiveChannelGroupAdapter liveChannelGroupAdapter;
    private LiveChannelItemAdapter liveChannelItemAdapter;
    private long mExitTime = 0; //xuameng返回键退出时间
	private long mExitTimeBack = 0; //xuameng回看返回键退出时间
    private long mExitTimeUp = 0; //xuameng上键间隔时间
    private long mExitTimeDown = 0; //xuameng下键间隔时间
    private long mSpeedTimeUp = 0; //xuameng上键间隔时间
    private static Toast xToast;   //xuameng  Toast
    private String logoUrl = null; 
    private LinearLayout tvRightSettingLayout;
    private TvRecyclerView mSettingGroupView;
    private TvRecyclerView mSettingItemView;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private List < LiveSettingGroup > liveSettingGroupList = new ArrayList < > ();
    public static int currentChannelGroupIndex = 0;
    public static int currentChannelGroupIndexXu = 0;   //xuameng item显示EPG用
    private Handler mHandler = new Handler();
    private List < LiveChannelGroup > liveChannelGroupList = new ArrayList < > ();
    private int currentLiveChannelIndex = -1;
    private int currentLiveChannelIndexXu = -1;  //xuameng item显示EPG用
    private int currentLiveChangeSourceTimes = 0;
    private LiveChannelItem currentLiveChannelItem = null;
    private LiveChannelItem currentLiveChannelItemXu = null;  //xuameng item显示EPG用
    private LivePlayerManager livePlayerManager = new LivePlayerManager();   //xuameng切换播放器渲染等
    private ArrayList < Integer > channelGroupPasswordConfirmed = new ArrayList < > ();
    private static LiveChannelItem channel_Name = null;
    private static LiveChannelItem channel_NameXu = null;   //xuameng item显示EPG用
    private static Hashtable<String, ArrayList<Epginfo>> hsEpg = new Hashtable<>();   //xuameng EPG缓存
    private CountDownTimer countDownTimer;
    private View ll_right_top_loading; //xuameng左上图标
    private View view_line_XU;  //xuameng 动画横线
    private View divLoadEpg;
    private View divLoadEpgleft;
    private LinearLayout divEpg;
    RelativeLayout ll_epg;
    TextView tv_channelnum;
    TextView tip_chname;
    TextView tip_epg1;
    TextView tip_epg2;
    TextView tv_srcinfo;
    TextView tv_curepg_left;
    TextView tv_nextepg_left;
    LinearLayout Mtv_left_top_xu; //xuameng回看中左上图标
    LinearLayout iv_Play_Xu; //xuameng回看暂停图标
    private TextView tv_size; //xuameng分辨率
    private MyEpgAdapter myAdapter;
    private TextView tv_right_top_tipnetspeed;
    private TextView tv_right_top_channel_name;
    private TextView tv_right_top_epg_name;
    private TextView tv_right_top_type;
    private TextView iv_play_pause;
    private ImageView iv_circle_bg;
    private TextView tv_shownum;
    private TextView txtNoEpg;
    private ImageView iv_back_bg;
    // private ObjectAnimator objectAnimator;
    public String epgStringAddress = "";
    private TvRecyclerView mEpgDateGridView;
    private TvRecyclerView mRightEpgList;
    private LiveEpgDateAdapter liveEpgDateAdapter;
    private LiveEpgAdapter epgListAdapter;
    private List < LiveDayListGroup > liveDayList = new ArrayList < > ();
    //laodao 7day replay
    public static SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd");
    public static SimpleDateFormat formatDate1 = new SimpleDateFormat("MM-dd");
    public static String day = formatDate.format(new Date());
    public static Date nowday = new Date();
    private boolean isBack = false;
    private boolean isTouch = false; //xuameng手机选择频道判断
    private boolean isVOD = false; //xuameng点播
    private boolean isKUAIJIN = false; //xuameng快进
    private boolean isSEEKBAR = false; //xuameng进入SEEKBAR
    private boolean isTVNUM = false; //xuameng获取频道编号
    private boolean isBuffer = false; //xuameng缓冲
    private boolean isShowlist = false; //xuameng判断菜单显示
    private boolean isVideoplaying = false; //xuameng判断视频开始播放
    private boolean XuSource = false; //xuameng退出回看
    private boolean TimeoutChangeSource = false; //xuameng是否自动换源
    private int selectedChannelNumber = 0; // xuameng遥控器数字键输入的要切换的频道号码
    private TextView tvSelectedChannel; //xuameng频道编号
    private ImageView iv_circle_bg_xu; //xuameng音乐播放时图标
    private ImageView MxuamengMusic; //xuameng播放音乐背景
    private static String shiyi_time; //时移时间
    private static int shiyi_time_c; //时移时间差值
    public static String playUrl;
    private ImageView imgLiveIcon;
    private ImageView imgLiveIconXu;   //XUAMENG 显示密码频道时用
    private FrameLayout liveIconNullBg;
    private TextView liveIconNullText;
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
    private View backcontroller;
    private CountDownTimer countDownTimer6;  //xuameng显示设置菜单用
    private CountDownTimer countDownTimer5;   //xuameng显示频道菜单用
    private CountDownTimer countDownTimer7;  //xuameng显示频道菜单用
    private CountDownTimer countDownTimer8;  //xuameng设置频道菜单用
    private CountDownTimer countDownTimer10;  //xuameng显示频道菜单用
    private CountDownTimer countDownTimer20; //xuameng显示频道菜单用
    private CountDownTimer countDownTimer21;  //xuameng显示设置菜单用
    private CountDownTimer countDownTimer22;  //xuameng显示设置菜单用
    private Visualizer mVisualizer;  //xuameng音乐播放动画
    private MusicVisualizerView customVisualizer; //xuameng播放音乐柱状图
    private boolean musicAnimation = false;     ////xuameng 音柱动画 加载设置
    private int audioSessionId = -1; // 使用-1表示未初始化状态 //xuameng音乐播放动画
	private static final String TAG = "LivePlayActivity";  //xuameng音乐播放动画
    private final int videoWidth = 1920;
    private final int videoHeight = 1080;
    private TextView tv_currentpos;
    private TextView tv_duration;
    private SeekBar sBar;
    private TextView iv_playpause;
    private View iv_play;
    private boolean show = false;
    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_play;
    }
    @Override
    protected void init() {
        context = this;
        epgStringAddress = Hawk.get(HawkConfig.EPG_URL, "");
     //   if(epgStringAddress == null || epgStringAddress.length() < 5) epgStringAddress = "http://epg.51zmt.top:8000/api/diyp/";
        if(epgStringAddress == null || epgStringAddress.length() < 5) epgStringAddress = "http://baidu.com";
        setLoadSir(findViewById(R.id.live_root));
        mVideoView = findViewById(R.id.mVideoView);
        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout); //xuameng左边频道菜单
        mChannelGroupView = findViewById(R.id.mGroupGridView);
        mLiveChannelView = findViewById(R.id.mChannelGridView);
        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        mSettingGroupView = findViewById(R.id.mSettingGroupView);
        mSettingItemView = findViewById(R.id.mSettingItemView);
        tvChannelInfo = findViewById(R.id.tvChannel);
        tvTime = findViewById(R.id.tvTime);
        tvTime_xu = findViewById(R.id.tvtime_xu); //xuameng的系统时间
        tvSelectedChannel = findViewById(R.id.tv_selected_channel); //xuameng选中频道编号
        tvNetSpeed = findViewById(R.id.tvNetSpeed);
        Mtv_left_top_xu = findViewById(R.id.tv_left_top_xu); //xuameng回看左上图标
        iv_Play_Xu = findViewById(R.id.iv_play_xu); //xuameng回看暂停图标
        tv_size = findViewById(R.id.tv_size); //XUAMENG分辨率
        tip_chname = (TextView) findViewById(R.id.tv_channel_bar_name); //底部名称
        tv_channelnum = (TextView) findViewById(R.id.tv_channel_bottom_number); //底部数字
        tip_epg1 = (TextView) findViewById(R.id.tv_current_program_time); //底部EPG当前节目信息
        tip_epg2 = (TextView) findViewById(R.id.tv_next_program_time); //底部EPG当下个节目信息
        tv_srcinfo = (TextView) findViewById(R.id.tv_source); //线路状态
        tv_curepg_left = (TextView) findViewById(R.id.tv_current_program); //当前节目
        tv_nextepg_left = (TextView) findViewById(R.id.tv_current_program); //下一节目
        ll_epg = (RelativeLayout) findViewById(R.id.ll_epg);
        tv_right_top_tipnetspeed = (TextView) findViewById(R.id.tv_right_top_tipnetspeed);
        tv_right_top_channel_name = (TextView) findViewById(R.id.tv_right_top_channel_name);
        tv_right_top_epg_name = (TextView) findViewById(R.id.tv_right_top_epg_name);
        tv_right_top_type = (TextView) findViewById(R.id.tv_right_top_type);
        iv_play_pause = (TextView) findViewById(R.id.iv_play_pause);
        iv_circle_bg = (ImageView) findViewById(R.id.iv_circle_bg);
        iv_back_bg = (ImageView) findViewById(R.id.iv_back_bg);
        tv_shownum = (TextView) findViewById(R.id.tv_shownum);
        txtNoEpg = (TextView) findViewById(R.id.txtNoEpg);
        ll_right_top_loading = findViewById(R.id.ll_right_top_loading);
        divLoadEpg = (View) findViewById(R.id.divLoadEpg);
        divLoadEpgleft = (View) findViewById(R.id.divLoadEpgleft);
        view_line_XU = (View) findViewById(R.id.view_line); //xuameng横线
        iv_circle_bg_xu = (ImageView) findViewById(R.id.iv_circle_bg_xu); //xuameng音乐播放时图标
        MxuamengMusic = (ImageView) findViewById(R.id.xuamengMusic); //xuameng播放音乐背景
        customVisualizer = findViewById(R.id.visualizer_view);  //xuameng播放音乐柱状图
        divEpg = (LinearLayout) findViewById(R.id.divEPG);
        //右上角图片旋转
        ObjectAnimator animator1 = ObjectAnimator.ofFloat(iv_circle_bg, "rotation", 360.0f);
        animator1.setDuration(10000);
        animator1.setRepeatCount(-1);
        animator1.start();
        ObjectAnimator animator2 = ObjectAnimator.ofFloat(iv_circle_bg_xu, "rotation", 360.0f);   //xuameng听歌时显示用
        animator2.setDuration(10000);
        animator2.setRepeatCount(-1);
        animator2.start();
        //laodao 7day replay
        mEpgDateGridView = findViewById(R.id.mEpgDateGridView);
        Hawk.put(HawkConfig.NOW_DATE, formatDate.format(new Date()));
        day = formatDate.format(new Date());
        nowday = new Date();
        mRightEpgList = (TvRecyclerView) findViewById(R.id.lv_epg);
        //EPG频道名称
        imgLiveIcon = findViewById(R.id.img_live_icon);
        imgLiveIconXu = findViewById(R.id.img_live_icon_xu);  //XUAMENG 显示密码频道时用
        liveIconNullBg = findViewById(R.id.live_icon_null_bg);
        liveIconNullText = findViewById(R.id.live_icon_null_text);
        imgLiveIcon.setVisibility(View.GONE);
        imgLiveIconXu.setVisibility(View.VISIBLE); //XUAMENG无频道信息显示
        liveIconNullText.setVisibility(View.VISIBLE);
        liveIconNullBg.setVisibility(View.INVISIBLE);
        sBar = (SeekBar) findViewById(R.id.pb_progressbar);
        tv_currentpos = (TextView) findViewById(R.id.tv_currentpos);
        backcontroller = (View) findViewById(R.id.backcontroller);
        tv_duration = (TextView) findViewById(R.id.tv_duration);
        iv_playpause = (TextView) findViewById(R.id.iv_playpause);
        iv_play = findViewById(R.id.iv_play);
        if(show) {
            ll_right_top_loading.setVisibility(View.GONE); //xuameng右上菜单隐藏
            view_line_XU.setVisibility(View.INVISIBLE); //xuamengEPG中的横线
            mHideChannelListRun(); //xuameng显示EPG就隐藏左右菜单
            mHideSettingLayoutRun(); //xuameng显示EPG就隐藏左右菜单
            iv_playpause.requestFocus(); //xuameng回看菜单默认焦点为播放	
            backcontroller.setVisibility(View.VISIBLE);   //xuameng底部进度条
            showTimeXu(); //xuameng系统显示时间
            showNetSpeedXu(); //XUAMENG显示右下网速
            Mtv_left_top_xu.setVisibility(View.VISIBLE); //xuameng显示左上回看图标
            ll_epg.setVisibility(View.VISIBLE); //xuameng下面EPG菜单显示	
        } else {
            backcontroller.setVisibility(View.GONE);  //xuameng底部进度条
            Mtv_left_top_xu.setVisibility(View.GONE); //xuameng隐藏左上回看图标
            iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
            tvLeftChannelListLayout.setVisibility(View.INVISIBLE); //xuameng显示EPG就隐藏左右菜单
            tvRightSettingLayout.setVisibility(View.INVISIBLE); //xuameng显示EPG就隐藏左右菜单
            ll_epg.setVisibility(View.VISIBLE); //xuameng下面EPG菜单显示
            ll_right_top_loading.setVisibility(View.VISIBLE); //xuameng右上菜单显示
            showTimeXu(); //xuameng显示系统时间
            showNetSpeedXu(); //XUAMENG显示右下网速
            view_line_XU.setVisibility(View.VISIBLE); //xuamengEPG中的横线
        }
        iv_playpause.setOnClickListener(new View.OnClickListener() { //xuameng回看暂停键
            @Override
            public void onClick(View arg0) {
                if(mVideoView == null) return;
                HideBottomEpgTimer();  //隐藏底部菜单到计时
                if(mVideoView.isPlaying()) {
                    mVideoView.pause();
                    iv_Play_Xu.setVisibility(View.VISIBLE); //回看暂停图标
                    iv_playpause.setText("播放"); 
                } else {
                    HideBottomEpg();  //隐藏底部菜单
                    mVideoView.start();
                    iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    iv_playpause.setText("暂停"); 
                }
            }
        });
        sBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { //xuameng升级手机进程条
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(mVideoView == null) return;
                long duration = mVideoView.getDuration();
                if(duration <= 0) return;
                long newPosition = (duration * seekBar.getProgress()) / sBar.getMax(); //xuameng停止触碰获取进度条进度
                mVideoView.seekTo((int) newPosition); //xuameng当前进度播放
                isKUAIJIN = false;  //xuameng快进判断
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(mVideoView == null) return;
                long duration = mVideoView.getDuration();
                if(duration <= 0) return;
                isKUAIJIN = true;   //xuameng快进判断
            }
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromuser) {
                if(mVideoView == null) return;
                if(!fromuser) {
                    return;
                }
                if(fromuser) {
                    long duration = mVideoView.getDuration();
                    if(duration <= 0) return;
                    long newPosition = (duration * progress) / sBar.getMax(); //xuameng触碰进度变化获取
                    if(tv_currentpos != null) {
                        tv_currentpos.setText(durationToString((int) newPosition)); //xuameng文字显示进度
                    }
                    HideBottomEpgTimer();  //隐藏底部菜单到计时
                }
            }
        });
        sBar.setOnKeyListener(new View.OnKeyListener() { //xuameng回看进度条监听
            @Override
            public boolean onKey(View arg0, int keycode, KeyEvent event) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) {
                    int keyCode = event.getKeyCode();
                    int action = event.getAction();
                    if(keycode == KeyEvent.KEYCODE_DPAD_CENTER || keycode == KeyEvent.KEYCODE_ENTER) {
                        if(mVideoView == null) return true;
                        HideBottomEpgTimer();  //隐藏底部菜单到计时
                        if(mVideoView.isPlaying()) {
                            mVideoView.pause();
                            iv_Play_Xu.setVisibility(View.VISIBLE); //回看暂停图标
                            iv_playpause.setText("播放"); 
                        } else {
                            HideBottomEpg();  //隐藏底部菜单
                            mVideoView.start();
                            iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                            iv_playpause.setText("暂停"); 
                        }
                    }
                    if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if(mVideoView == null) return true;
                        HideBottomEpgTimer();  //隐藏底部菜单到计时
                        tvSlideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                        return true;
                    }
                }
                if(event.getAction() == KeyEvent.ACTION_UP) {
                    int keyCode = event.getKeyCode();
                    int action = event.getAction();
                    if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        tvSlideStop(); //xuameng修复SEEKBAR快进重新播放问题
                        return true;
                    }
                }
                return false;
            }
        });
        initEpgDateView();
        initEpgListView();
        initDayList();
        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initLiveChannelList();
        initLiveSettingGroupList();
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, true); //xuameng新增 
        mHandler.post(mUpdateVodProgressXu); //xuamengVOD BACK播放进度检测
        mHandler.post(myRunnableMusic); //xuamengVOD BACK播放进度检测
        mHandler.post(mUpdateVodImageXu); //xuamengVOD BACK播放进度检测
        iv_playpause.setNextFocusLeftId(R.id.pb_progressbar);  //xuameng左右焦点
    }
    //获取EPG并存储 // 百川epg  DIYP epg   51zmt epg ------- 自建EPG格式输出格式请参考 51zmt
    private List < Epginfo > epgdata = new ArrayList < > ();
    private void showEpg(Date date, ArrayList < Epginfo > arrayList) {
        if(arrayList != null && arrayList.size() > 0){
            epgdata = arrayList;
            epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
            epgListAdapter.setNewData(epgdata);
            int i = -1;
            int size = epgdata.size() - 1;
            while(size >= 0) {
                if(new Date().compareTo(((Epginfo) epgdata.get(size)).startdateTime) >= 0) {
                    break;
                }
                size--;
            }
            i = size;
            if(i >= 0 && new Date().compareTo(epgdata.get(i).enddateTime) <= 0) {
                epgListAdapter.notifyDataSetChanged();
                final int targetPos = i; // 使用final保证线程安全
                mRightEpgList.removeCallbacks(null);
           //些方法有滚动效果会产生焦点乱跳         mRightEpgList.setSelectedPosition(targetPos);  
                epgListAdapter.setSelectedEpgIndex(targetPos);
                if(targetPos >= 0 && targetPos < epgListAdapter.getItemCount()) {
                   mRightEpgList.post(() -> {
                   mRightEpgList.scrollToPositionWithOffset(targetPos, 0);
                        //xuameng防止跳焦点                 mRightEpgList.setSelection(finalI);
                   });
                }
            }
        } 
    }
    private void showEpgxu(Date date, ArrayList < Epginfo > arrayList) {
        if(arrayList != null && arrayList.size() > 0){
            epgdata = arrayList;
            epgListAdapter.CanBack(currentLiveChannelItemXu.getinclude_back());
            epgListAdapter.setNewData(epgdata);
            int i = -1;
            int size = epgdata.size() - 1;
            while(size >= 0) {
                if(new Date().compareTo(((Epginfo) epgdata.get(size)).startdateTime) >= 0) {
                    break;
                }
                size--;
            }
            i = size;
            if(i >= 0 && new Date().compareTo(epgdata.get(i).enddateTime) <= 0) {
                epgListAdapter.notifyDataSetChanged();
                final int targetPos = i; // 使用final保证线程安全
                mRightEpgList.removeCallbacks(null);
                 //些方法有滚动效果会产生焦点乱跳   mRightEpgList.setSelectedPosition(targetPos);
                epgListAdapter.setSelectedEpgIndex(targetPos);
                if(targetPos >= 0 && targetPos < epgListAdapter.getItemCount()) {
                   mRightEpgList.post(() -> {
                   mRightEpgList.scrollToPositionWithOffset(targetPos, 0);
                        //xuameng防止跳焦点                 mRightEpgList.setSelection(finalI);
                   });
                }
            }
        } 
    }

    public void getEpg(Date date) {
        String channelName = channel_Name.getChannelName();
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        String[] epgInfo = EpgUtil.getEpgInfo(channelName);
        String epgTagName = channelName;
        if(logoUrl == null || logoUrl.isEmpty()) {
            updateChannelIcon(channelName, epgInfo == null ? null : epgInfo[0]); //xuameng自带logo
        } else if(logoUrl.equals("false")) {
            updateChannelIcon(channelName, null);
        } else {
            String logo = logoUrl.replace("{name}", channelName); //xuameng支持logourl
            updateChannelIcon(channelName, logo);
        }
        if(epgInfo != null && !epgInfo[1].isEmpty()) {   //xuameng自定义EPG对应表
            epgTagName = epgInfo[1];
        }
        epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
        //epgListAdapter.updateData(date, new ArrayList<>());
		String savedEpgKey = channelName + "_" + Objects.requireNonNull(liveEpgDateAdapter.getItem(liveEpgDateAdapter.getSelectedIndex())).getDatePresented();
        if(hsEpg.containsKey(savedEpgKey)) {   //xuameng如果有缓存EPG
           ArrayList<Epginfo> arrayListJudge = (ArrayList<Epginfo>) hsEpg.get(savedEpgKey);
           String title = arrayListJudge.get(0).title;      //0中EPG第一行的名称
           if (!title.contains("聚汇直播")) {   //xuameng再次判断如果缓存EPG中有聚汇直播字样说明是在线获取EPG失败则继续重试
              showEpg(date, hsEpg.get(savedEpgKey));   //xuameng如果成功就直接显示缓存EPG   
              showBottomEpgXU(); //xuameng测试EPG刷新 
              return;
           }
        }
        String url;
        if(epgStringAddress.contains("{name}") && epgStringAddress.contains("{date}")) {
            url = epgStringAddress.replace("{name}", URLEncoder.encode(epgTagName)).replace("{date}", timeFormat.format(date));
        } else {
            url = epgStringAddress + "?ch=" + URLEncoder.encode(epgTagName) + "&date=" + timeFormat.format(date);
        }
        UrlHttpUtil.get(url, new CallBackUtil.CallBackString() {
            public void onFailure(int i, String str) {    //xuameng如果EPG获取失败启动默认列表
                ArrayList arrayList = new ArrayList();
                Epginfo epgbcinfo = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "00:00", "01:59", 0);   //xuameng最后一项为pos id
                Epginfo epgbcinfo1 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "02:00", "03:59", 1);
                Epginfo epgbcinfo2 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "04:00", "05:59", 2);
                Epginfo epgbcinfo3 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "06:00", "07:59", 3);
                Epginfo epgbcinfo4 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "08:00", "09:59", 4);
                Epginfo epgbcinfo5 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "10:00", "11:59", 5);
                Epginfo epgbcinfo6 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "12:00", "13:59", 6);
                Epginfo epgbcinfo7 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "14:00", "15:59", 7);
                Epginfo epgbcinfo8 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "16:00", "17:59", 8);
                Epginfo epgbcinfo9 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "18:00", "19:59", 9);
                Epginfo epgbcinfo10 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "20:00", "21:59", 10);
                Epginfo epgbcinfo11 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "22:00", "23:59", 11);
                arrayList.add(epgbcinfo);
                arrayList.add(epgbcinfo1);
                arrayList.add(epgbcinfo2);
                arrayList.add(epgbcinfo3);
                arrayList.add(epgbcinfo4);
                arrayList.add(epgbcinfo5);
                arrayList.add(epgbcinfo6);
                arrayList.add(epgbcinfo7);
                arrayList.add(epgbcinfo8);
                arrayList.add(epgbcinfo9);
                arrayList.add(epgbcinfo10);
                arrayList.add(epgbcinfo11);
                epgdata = arrayList;
                epgListAdapter.setNewData(epgdata);
                hsEpg.put(savedEpgKey, arrayList);   //xuameng默认列表存入缓存
                showEpg(date, arrayList);
                showBottomEpgXU(); //xuameng测试EPG刷新        
            }
            public void onResponse(String paramString) {
                ArrayList arrayList = new ArrayList();
             ////xuameng 空指针   Log.d("返回的EPG信息", paramString != null ? paramString : "暂无当前节目单，聚汇直播欢迎您的观看！");
                try {
                    if(paramString != null && paramString.contains("epg_data")) {  //xuameng 空指针
                        final JSONArray jSONArray = new JSONObject(paramString).optJSONArray("epg_data");
                        if(jSONArray != null)
                            for(int b = 0; b < jSONArray.length(); b++) {
                                JSONObject jSONObject = jSONArray.getJSONObject(b);
                                Epginfo epgbcinfo = new Epginfo(date, jSONObject.optString("title"), date, jSONObject.optString("start"), jSONObject.optString("end"), b);
                                arrayList.add(epgbcinfo);
                             ////xuameng 空指针   Log.d("EPG信息:", day + "  " + jSONObject.optString("start") + " - " + jSONObject.optString("end") + "  " + jSONObject.optString("title"));
                            }
                    }
                } catch (JSONException jSONException) {
                    jSONException.printStackTrace();
                }
                if(arrayList != null && arrayList.size() > 0){
                   hsEpg.put(savedEpgKey, arrayList);  //xuameng默认列表存入缓存
                   showEpg(date, arrayList);
                   showBottomEpgXU(); //xuameng测试EPG刷新
                }else{
                   Epginfo epgbcinfo = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "00:00", "01:59", 0);   //xuameng最后一项为pos id
                   Epginfo epgbcinfo1 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "02:00", "03:59", 1);
                   Epginfo epgbcinfo2 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "04:00", "05:59", 2);
                   Epginfo epgbcinfo3 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "06:00", "07:59", 3);
                   Epginfo epgbcinfo4 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "08:00", "09:59", 4);
                   Epginfo epgbcinfo5 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "10:00", "11:59", 5);
                   Epginfo epgbcinfo6 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "12:00", "13:59", 6);
                   Epginfo epgbcinfo7 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "14:00", "15:59", 7);
                   Epginfo epgbcinfo8 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "16:00", "17:59", 8);
                   Epginfo epgbcinfo9 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "18:00", "19:59", 9);
                   Epginfo epgbcinfo10 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "20:00", "21:59", 10);
                   Epginfo epgbcinfo11 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "22:00", "23:59", 11);
                   arrayList.add(epgbcinfo);
                   arrayList.add(epgbcinfo1);
                   arrayList.add(epgbcinfo2);
                   arrayList.add(epgbcinfo3);
                   arrayList.add(epgbcinfo4);
                   arrayList.add(epgbcinfo5);
                   arrayList.add(epgbcinfo6);
                   arrayList.add(epgbcinfo7);
                   arrayList.add(epgbcinfo8);
                   arrayList.add(epgbcinfo9);
                   arrayList.add(epgbcinfo10);
                   arrayList.add(epgbcinfo11);
                   epgdata = arrayList;
                   epgListAdapter.setNewData(epgdata);
                   hsEpg.put(savedEpgKey, arrayList);   //xuameng默认列表存入缓存
                   showEpg(date, arrayList);
                   showBottomEpgXU(); //xuameng测试EPG刷新
                }
            }
        });
    }
    public void getEpgxu(Date date) {
        String channelName = channel_NameXu.getChannelName();    //xuameng频道名称在移动item中选中
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        String[] epgInfo = EpgUtil.getEpgInfo(channelName);    //xuameng自定义EPG对应表
        String epgTagName = channelName;
        if(epgInfo != null && !epgInfo[1].isEmpty()) {  
            epgTagName = epgInfo[1];
        }
        epgListAdapter.CanBack(currentLiveChannelItemXu.getinclude_back()); //xuameng重要EPG滚动菜单检测可不可以回看
        //epgListAdapter.updateData(date, new ArrayList<>());
		String savedEpgKey = channelName + "_" + Objects.requireNonNull(liveEpgDateAdapter.getItem(liveEpgDateAdapter.getSelectedIndex())).getDatePresented();
        if(hsEpg.containsKey(savedEpgKey)) {   //xuameng如果有缓存EPG
           ArrayList<Epginfo> arrayListJudge = (ArrayList<Epginfo>) hsEpg.get(savedEpgKey);
           String title = arrayListJudge.get(0).title;   //0中EPG第一行的名称
           if (!title.contains("聚汇直播")) {   //xuameng再次判断如果缓存EPG中有聚汇直播字样说明是在线获取EPG失败则继续重试
              showEpgxu(date, hsEpg.get(savedEpgKey));     //xuameng如果成功就直接显示缓存EPG   
              return;
           }
        }
        String url;
        if(epgStringAddress.contains("{name}") && epgStringAddress.contains("{date}")) {
            url = epgStringAddress.replace("{name}", URLEncoder.encode(epgTagName)).replace("{date}", timeFormat.format(date));
        } else {
            url = epgStringAddress + "?ch=" + URLEncoder.encode(epgTagName) + "&date=" + timeFormat.format(date);
        }
        UrlHttpUtil.get(url, new CallBackUtil.CallBackString() {
            public void onFailure(int i, String str) {
                ArrayList arrayList = new ArrayList();
                Epginfo epgbcinfo = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "00:00", "01:59", 0);   //xuameng最后一项为pos id
                Epginfo epgbcinfo1 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "02:00", "03:59", 1);
                Epginfo epgbcinfo2 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "04:00", "05:59", 2);
                Epginfo epgbcinfo3 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "06:00", "07:59", 3);
                Epginfo epgbcinfo4 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "08:00", "09:59", 4);
                Epginfo epgbcinfo5 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "10:00", "11:59", 5);
                Epginfo epgbcinfo6 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "12:00", "13:59", 6);
                Epginfo epgbcinfo7 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "14:00", "15:59", 7);
                Epginfo epgbcinfo8 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "16:00", "17:59", 8);
                Epginfo epgbcinfo9 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "18:00", "19:59", 9);
                Epginfo epgbcinfo10 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "20:00", "21:59", 10);
                Epginfo epgbcinfo11 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "22:00", "23:59", 11);
                arrayList.add(epgbcinfo);
                arrayList.add(epgbcinfo1);
                arrayList.add(epgbcinfo2);
                arrayList.add(epgbcinfo3);
                arrayList.add(epgbcinfo4);
                arrayList.add(epgbcinfo5);
                arrayList.add(epgbcinfo6);
                arrayList.add(epgbcinfo7);
                arrayList.add(epgbcinfo8);
                arrayList.add(epgbcinfo9);
                arrayList.add(epgbcinfo10);
                arrayList.add(epgbcinfo11);
                epgdata = arrayList;
                epgListAdapter.setNewData(epgdata);
                hsEpg.put(savedEpgKey, arrayList);  //xuameng默认列表存入缓存
                showEpgxu(date, arrayList);   //xuameng先保存EPG再显示EPG
            }
            public void onResponse(String paramString) {
                ArrayList arrayList = new ArrayList();
			////xuameng 空指针 	Log.d("返回的EPG信息", paramString != null ? paramString : "暂无当前节目单，聚汇直播欢迎您的观看！");
                try {
                    if(paramString != null && paramString.contains("epg_data")) {   //xuameng 空指针
                        final JSONArray jSONArray = new JSONObject(paramString).optJSONArray("epg_data");
                        if(jSONArray != null)
                            for(int b = 0; b < jSONArray.length(); b++) {
                                JSONObject jSONObject = jSONArray.getJSONObject(b);
                                Epginfo epgbcinfo = new Epginfo(date, jSONObject.optString("title"), date, jSONObject.optString("start"), jSONObject.optString("end"), b);
                                arrayList.add(epgbcinfo);
                             ////xuameng 空指针   Log.d("EPG信息:", day + "  " + jSONObject.optString("start") + " - " + jSONObject.optString("end") + "  " + jSONObject.optString("title"));
                            }
                    }
                } catch (JSONException jSONException) {
                    jSONException.printStackTrace();
                }
                if(arrayList != null && arrayList.size() > 0){
                   hsEpg.put(savedEpgKey, arrayList);  //xuameng默认列表存入缓存
                   showEpgxu(date, arrayList);
                }else{
                   Epginfo epgbcinfo = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "00:00", "01:59", 0);   //xuameng最后一项为pos id
                   Epginfo epgbcinfo1 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "02:00", "03:59", 1);
                   Epginfo epgbcinfo2 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "04:00", "05:59", 2);
                   Epginfo epgbcinfo3 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "06:00", "07:59", 3);
                   Epginfo epgbcinfo4 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "08:00", "09:59", 4);
                   Epginfo epgbcinfo5 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "10:00", "11:59", 5);
                   Epginfo epgbcinfo6 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "12:00", "13:59", 6);
                   Epginfo epgbcinfo7 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "14:00", "15:59", 7);
                   Epginfo epgbcinfo8 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "16:00", "17:59", 8);
                   Epginfo epgbcinfo9 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "18:00", "19:59", 9);
                   Epginfo epgbcinfo10 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "20:00", "21:59", 10);
                   Epginfo epgbcinfo11 = new Epginfo(date, "聚汇直播提示您：暂无节目信息！", date, "22:00", "23:59", 11);
                   arrayList.add(epgbcinfo);
                   arrayList.add(epgbcinfo1);
                   arrayList.add(epgbcinfo2);
                   arrayList.add(epgbcinfo3);
                   arrayList.add(epgbcinfo4);
                   arrayList.add(epgbcinfo5);
                   arrayList.add(epgbcinfo6);
                   arrayList.add(epgbcinfo7);
                   arrayList.add(epgbcinfo8);
                   arrayList.add(epgbcinfo9);
                   arrayList.add(epgbcinfo10);
                   arrayList.add(epgbcinfo11);
                   epgdata = arrayList;
                   epgListAdapter.setNewData(epgdata);
                   hsEpg.put(savedEpgKey, arrayList);   //xuameng默认列表存入缓存
                   showEpgxu(date, arrayList);
                }
            }
        });
    }
    //显示底部EPG
    @SuppressLint("SetTextI18n") //xuameng乱码
    private void showBottomEpg() {
        if(!isCurrentLiveChannelValid()) { //xuameng 未选择频道空指针问题
            return;
        }
        if(isBack) return;
        liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
        if(channel_Name.getChannelName() != null) {
            ((TextView) findViewById(R.id.tv_channel_bar_name)).setText(channel_Name.getChannelName());
            ((TextView) findViewById(R.id.tv_channel_bottom_number)).setText("" + channel_Name.getChannelNum());
            tip_epg1.setText("暂无当前节目单，聚汇直播欢迎您的观看！");
            ((TextView) findViewById(R.id.tv_current_program_name)).setText("");
            tip_epg2.setText("许大师开发制作，请勿商用以及播放违法内容！");
            ((TextView) findViewById(R.id.tv_next_program_name)).setText("");
            String savedEpgKey = channel_Name.getChannelName() + "_" + Objects.requireNonNull(liveEpgDateAdapter.getItem(liveEpgDateAdapter.getSelectedIndex())).getDatePresented();
            if(hsEpg.containsKey(savedEpgKey)) {
                String[] epgInfo = EpgUtil.getEpgInfo(channel_Name.getChannelName());
                if(logoUrl == null || logoUrl.isEmpty()) {
                    updateChannelIcon(channel_Name.getChannelName(), epgInfo == null ? null : epgInfo[0]); //xuameng自带url
                } else if(logoUrl.equals("false")) {
                    updateChannelIcon(channel_Name.getChannelName(), null);
                } else {
                    String logo = logoUrl.replace("{name}", channel_Name.getChannelName());
                    updateChannelIcon(channel_Name.getChannelName(), logo); //xuameng支持logourl
                }
                ArrayList arrayList = (ArrayList) hsEpg.get(savedEpgKey);
                if(arrayList != null && arrayList.size() > 0) {
                    int size = arrayList.size() - 1;
                    while(size >= 0) {
                        if(new Date().compareTo(((Epginfo) arrayList.get(size)).startdateTime) >= 0) {
                            tip_epg1.setText(((Epginfo) arrayList.get(size)).start + "--" + ((Epginfo) arrayList.get(size)).end);
                            ((TextView) findViewById(R.id.tv_current_program_name)).setText(((Epginfo) arrayList.get(size)).title);
                            if(size != arrayList.size() - 1) {
                                tip_epg2.setText(((Epginfo) arrayList.get(size + 1)).start + "--" + ((Epginfo) arrayList.get(size + 1)).end); //xuameng修复EPG低菜单下一个节目结束的时间
                                ((TextView) findViewById(R.id.tv_next_program_name)).setText(((Epginfo) arrayList.get(size + 1)).title);
                            }
                            break;
                        } else {
                            size--;
                        }
                    }
                }
                epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
                epgListAdapter.setNewData(arrayList);
            } else {
                int selectedIndex = liveEpgDateAdapter.getSelectedIndex();
                if(selectedIndex < 0) getEpg(new Date());
                else getEpg(liveEpgDateAdapter.getData().get(selectedIndex).getDateParamVal());
            }
            HideBottomEpgTimer();  //隐藏底部菜单到计时
            backcontroller.setVisibility(View.GONE);    //xuameng 隐藏进度条
            Mtv_left_top_xu.setVisibility(View.GONE); //xuameng直播时隐藏回看左上图标
            mHideChannelListRun(); //xuameng显示EPG就隐藏左右菜单
            mHideSettingLayoutRun(); //xuameng显示EPG就隐藏左右菜单
            ll_epg.setVisibility(View.VISIBLE); //xuameng下面EPG菜单显示
            ll_right_top_loading.setVisibility(View.VISIBLE); //xuameng右上菜单显示
            view_line_XU.setVisibility(View.VISIBLE); //xuamengEPG中的横线
            showTimeXu(); //xuameng显示系统时间
            showNetSpeedXu(); //XUAMENG显示左上网速 
            if(channel_Name == null || channel_Name.getSourceNum() <= 1) {
                ((TextView) findViewById(R.id.tv_source)).setText("[线路源1/1]");
            } else {
                ((TextView) findViewById(R.id.tv_source)).setText("[线路源" + (channel_Name.getSourceIndex() + 1) + "/" + channel_Name.getSourceNum() + "]");
            }
            tv_right_top_channel_name.setText(channel_Name.getChannelName());
            tv_right_top_epg_name.setText(channel_Name.getChannelName());
        }
    }
    @SuppressLint("SetTextI18n") //xuameng乱码
    private void showBottomEpgXU() { //XUAMENG刷新EPG，要不不能自动刷新
        if(!isCurrentLiveChannelValidXu()) { //xuameng 未选择频道空指针问题
            return;
        }
        if(isBack) return;
        if(channel_Name.getChannelName() != null) {
            String savedEpgKey = channel_Name.getChannelName() + "_" + Objects.requireNonNull(liveEpgDateAdapter.getItem(liveEpgDateAdapter.getSelectedIndex())).getDatePresented();
            if(hsEpg.containsKey(savedEpgKey)) {
                String[] epgInfo = EpgUtil.getEpgInfo(channel_Name.getChannelName());
                if(logoUrl == null || logoUrl.isEmpty()) {
                    updateChannelIcon(channel_Name.getChannelName(), epgInfo == null ? null : epgInfo[0]); //xuameng自带logo
                } else if(logoUrl.equals("false")) {
                    updateChannelIcon(channel_Name.getChannelName(), null);
                } else {
                    String logo = logoUrl.replace("{name}", channel_Name.getChannelName());
                    updateChannelIcon(channel_Name.getChannelName(), logo); //xuameng支持logourl
                }
                ArrayList arrayList = (ArrayList) hsEpg.get(savedEpgKey);
                if(arrayList != null && arrayList.size() > 0) {
                    int size = arrayList.size() - 1;
                    while(size >= 0) {
                        if(new Date().compareTo(((Epginfo) arrayList.get(size)).startdateTime) >= 0) {
                            tip_epg1.setText(((Epginfo) arrayList.get(size)).start + "--" + ((Epginfo) arrayList.get(size)).end);
                            ((TextView) findViewById(R.id.tv_current_program_name)).setText(((Epginfo) arrayList.get(size)).title);
                            if(size != arrayList.size() - 1) {
                                tip_epg2.setText(((Epginfo) arrayList.get(size + 1)).start + "--" + ((Epginfo) arrayList.get(size + 1)).end); //xuameng修复EPG低菜单下一个节目结束的时间
                                ((TextView) findViewById(R.id.tv_next_program_name)).setText(((Epginfo) arrayList.get(size + 1)).title);
                            }
                            break;
                        } else {
                            size--;
                        }
                    }
                }
                epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
                epgListAdapter.setNewData(arrayList);
            } else {
                int selectedIndex = liveEpgDateAdapter.getSelectedIndex();
                if(selectedIndex < 0) getEpg(new Date());
                else getEpg(liveEpgDateAdapter.getData().get(selectedIndex).getDateParamVal());
            }
        }
    }
    private void updateChannelIcon(String channelName, String logoUrl) {
        if(StringUtils.isEmpty(logoUrl)) {
            imgLiveIconXu.setVisibility(View.GONE);
            liveIconNullBg.setVisibility(View.VISIBLE);
            liveIconNullText.setVisibility(View.VISIBLE);
            imgLiveIcon.setVisibility(View.VISIBLE);
            Picasso.get().load(logoUrl).placeholder(R.drawable.banner_xu).into(imgLiveIcon); // xuameng内容空显示banner
            liveIconNullText.setVisibility(View.VISIBLE);
            liveIconNullText.setText("[频道编号" + channel_Name.getChannelNum() + "]"); // xuameng显示频道编号
        } else {
            imgLiveIconXu.setVisibility(View.GONE);
            imgLiveIcon.setVisibility(View.VISIBLE);
            Picasso.get().load(logoUrl).placeholder(R.drawable.banner_xu).into(imgLiveIcon); // xuameng内不空显示banner
            liveIconNullBg.setVisibility(View.VISIBLE);
            liveIconNullText.setVisibility(View.VISIBLE);
            liveIconNullText.setVisibility(View.VISIBLE);
            liveIconNullText.setText("[频道编号" + channel_Name.getChannelNum() + "]"); // xuameng显示频道编号
        }
    }
    //频道列表
    @SuppressLint("NotifyDataSetChanged")
    public void divLoadEpgRight(View view) {
        mHideChannelListRunXu(); //xuameng BUG
        if(!isCurrentLiveChannelValid()) return; //xuameng 未选择频道空指针问题
        if(isTouch) {
            showChannelListTouch();
        }
        mChannelGroupView.setVisibility(View.GONE);
        divLoadEpg.setVisibility(View.GONE);
        divEpg.setVisibility(View.VISIBLE);
        divLoadEpgleft.setVisibility(View.VISIBLE);
        int SelectedIndexEpg = epgListAdapter.getSelectedIndex(); //xuameng当前选中的EPG
        if (SelectedIndexEpg >= 0  && SelectedIndexEpg < epgListAdapter.getItemCount()){  //xuameng不等于-1代表已有选中的EPG，防空指针
            mRightEpgList.removeCallbacks(null);
	        mRightEpgList.post(() -> {
            mRightEpgList.scrollToPositionWithOffset(SelectedIndexEpg, 0);
            epgListAdapter.getSelectedIndex(); //xuamengEPG打开菜单自动变颜色
            }); 
        }
    }
    //频道列表
    public void divLoadEpgLeft(View view) {
        divEpg.setVisibility(View.GONE);
        divLoadEpgleft.setVisibility(View.GONE);
        mChannelGroupView.setVisibility(View.VISIBLE);
        divLoadEpg.setVisibility(View.VISIBLE);
        mHideChannelListRunXu();
    }
    private void xuexit() { //xuameng双击退出
        if(System.currentTimeMillis() - mExitTime < 2000) {
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBack);
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBuffer);
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            mHandler.removeCallbacks(mUpdateNetSpeedRunXu);
            mHandler.removeCallbacks(mUpdateVodProgressXu);
            mHandler.removeCallbacks(myRunnableMusic);
            mHandler.removeCallbacks(mUpdateVodImageXu);
            mHandler.removeCallbacks(mUpdateTimeRun);
            mHandler.removeCallbacks(mUpdateTimeRunXu);
            iv_circle_bg_xu.setVisibility(View.GONE); //xuameng音乐播放时图标
            MxuamengMusic.setVisibility(View.GONE); //xuameng播放音乐背景
            customVisualizer.setVisibility(View.GONE);  //xuameng播放音乐柱状图
            if(mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
            OkGo.getInstance().cancelTag("xuameng");
            if(countDownTimer != null) {
               countDownTimer.cancel();
               countDownTimer = null;
            }
           if(mVideoView != null) {
               mVideoView.release();
               mVideoView = null;
           }
            releaseVisualizer();  //xuameng音乐播放动画
            App.HideToast();  //xuameng HideToast
            cancelxToast();
            super.onBackPressed();
        } else {
            mExitTime = System.currentTimeMillis();
            showLiveXu();
        }
    }
    private void xubackexit() { //xuameng双击退出回看
        if(System.currentTimeMillis() - mExitTimeBack < 2000) {
            isBack = false;
            Mtv_left_top_xu.setVisibility(View.GONE); //xuameng返回键隐藏左上回看菜单
            iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
            hideTimeXu(); //xuameng隐藏系统时间
            hideNetSpeedXu(); //XUAMENG隐藏左上网速
            liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBuffer);
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBack);
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
            cancelxToast();
            App.HideToast();  //xuameng HideToast
            playXuSource();
        } else {
            mExitTimeBack = System.currentTimeMillis();
            showToastBack();   //xuameng 退出回看
        }
    }
    @Override
    public void onBackPressed() { //xuameng返回键
        if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHideChannelListRun();
        } else if(tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHideSettingLayoutRun();
        } else if(backcontroller.getVisibility() == View.VISIBLE) {
            HideBottomEpg();  //隐藏底部菜单
        } else if(isLl_epgVisible()) {
            HideBottomEpg(); //隐藏底部菜单
        } else if(isBack) {
            xubackexit(); //xuameng回放双击退出
        } else {
            xuexit(); //xuameng双击退出
        }
    }
    private void ExitLiveOnSetting() { //xuameng设置中推出直播
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBack);
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBuffer);
        mHandler.removeCallbacks(mUpdateNetSpeedRun);
        mHandler.removeCallbacks(mUpdateNetSpeedRunXu);
        mHandler.removeCallbacks(mUpdateVodProgressXu);
        mHandler.removeCallbacks(myRunnableMusic);
        mHandler.removeCallbacks(mUpdateVodImageXu);
        mHandler.removeCallbacks(mUpdateTimeRun);
        mHandler.removeCallbacks(mUpdateTimeRunXu);
        iv_circle_bg_xu.setVisibility(View.GONE); //xuameng音乐播放时图标
        MxuamengMusic.setVisibility(View.GONE); //xuameng播放音乐背景
        if(mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        OkGo.getInstance().cancelTag("xuameng");
        if(countDownTimer != null) {
           countDownTimer.cancel();
           countDownTimer = null;
        }
        if(mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        releaseVisualizer();  //xuameng音乐播放动画
        App.HideToast();  //xuameng HideToast
        cancelxToast();
        super.onBackPressed();
    }
    private final Runnable mPlaySelectedChannel = new Runnable() {
        @Override
        public void run() {
            tvSelectedChannel.setVisibility(View.GONE);
            tvSelectedChannel.setText("");
            int grpIndx = 0;
            int chaIndx = 0;
            int getMin = 1;
            int getMax;
            for(int j = 0; j < liveChannelGroupList.size(); j++) { //xuameng循环频道组
                getMax = getMin + getLiveChannelsXu(j).size() - 1;     //xuameng数字选台时用跳过密码频道验证获取全部频道编号
                if(selectedChannelNumber >= getMin && selectedChannelNumber <= getMax) {
                    grpIndx = j;
                    chaIndx = selectedChannelNumber - getMin + 1;
                    isTVNUM = true; //xuameng以获取到频道编号
                    break;
                } else {
                    getMin = getMax + 1;
                }
            }
            if(selectedChannelNumber > 0) {
                if(!isTVNUM) { //xuameng没获取到频道编号
                    selectedChannelNumber = 0;
                    App.showToastShort(mContext, "聚汇直播提示您：无此频道编号！");
                    return;
                }
                if(isNeedInputPassword(grpIndx)) {
                    selectedChannelNumber = 0;
                    App.showToastShort(mContext, "聚汇直播提示您：此为加密频道！");
                    return;
                }
                playChannel(grpIndx, chaIndx - 1, false); //xuameng获取到编号播放
            } else {
                App.showToastShort(mContext, "聚汇直播提示您：无此频道编号！");    //xuameng编号为0
            }
            selectedChannelNumber = 0;
        }
    };
    private void numericKeyDown(int digit) {
        selectedChannelNumber = selectedChannelNumber * 10 + digit;
        if(selectedChannelNumber > 99999) {
            selectedChannelNumber = 0;
        }
        if(backcontroller.getVisibility() == View.VISIBLE) {
            HideBottomEpg();  //隐藏底部菜单
        }
        if(isLl_epgVisible()) {
            HideBottomEpg();  //隐藏底部菜单
        }
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBack);
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBuffer);
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(5); // XUAMENG限制输入长度为5位
        tvSelectedChannel.setFilters(filters);
        tvSelectedChannel.setText(Integer.toString(selectedChannelNumber));
        tvSelectedChannel.setVisibility(View.VISIBLE);
        isTVNUM = false;
        mHandler.removeCallbacks(mPlaySelectedChannel);
        mHandler.postDelayed(mPlaySelectedChannel, 2500);
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if(keyCode == KeyEvent.KEYCODE_MENU) { //xuameng回看时控制
                if(isBack) {
                    if(backcontroller.getVisibility() == View.VISIBLE) {
                        HideBottomEpg(); //隐藏底部菜单
                    } else {
                        showProgressBars(true);
                    }
                    return true;
                } else {
                    showSettingGroup();
                    return false;
                }
            } else if(!isListOrSettingLayoutVisible()) {
                switch(keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if(isBack) { //xuameng回看时控制
                            if(backcontroller.getVisibility() == View.VISIBLE) {
                                HideBottomEpg(); //隐藏底部菜单
                            } else if(backcontroller.getVisibility() == View.GONE) {
                                showProgressBars(true);
                            }
                        } else if(System.currentTimeMillis() - mExitTimeUp < 1200) { //xuameng小于1.2秒换台
                            if(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) {
                                playNext();
                            } else {
                                playPrevious();
                            }
                        } else if(isVOD) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {
                                mExitTimeUp = System.currentTimeMillis();
                                HideBottomEpg(); //隐藏底部菜单
                            } else if(backcontroller.getVisibility() == View.GONE) {
                                mExitTimeUp = System.currentTimeMillis();
                                showProgressBars(true);
                            }
                        } else if(isLl_epgVisible()) {
                            mExitTimeUp = System.currentTimeMillis();
                            HideBottomEpg(); //隐藏底部菜单
                        } else if(!isLl_epgVisible()) {
                            mExitTimeUp = System.currentTimeMillis();
                            showBottomEpg(); //xuameng显示EPG和上面菜单
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN: //xuameng回看时控制
                        if(isBack) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {
                                HideBottomEpg(); //隐藏底部菜单
                            } else if(backcontroller.getVisibility() == View.GONE) {
                                showProgressBars(true);
                            }
                        } else if(System.currentTimeMillis() - mExitTimeDown < 1200) {
                            if(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) {
                                playPrevious();
                            } else {
                                playNext();
                            }
                        } else if(isVOD) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {
                                mExitTimeDown = System.currentTimeMillis();
                                HideBottomEpg(); //隐藏底部菜单
                            } else if(backcontroller.getVisibility() == View.GONE) {
                                mExitTimeDown = System.currentTimeMillis();
                                showProgressBars(true);
                            }
                        } else if(isLl_epgVisible()) {
                            mExitTimeDown = System.currentTimeMillis();
                            HideBottomEpg(); //隐藏底部菜单
                        } else if(!isLl_epgVisible()) {
                            mExitTimeDown = System.currentTimeMillis();
                            showBottomEpg(); //xuameng显示EPG和上面菜单
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if(isBack || isVOD) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {   //xuameng左右键与seekbar调节进度冲突
                            }else{
                               showProgressBars(true);
						    }
                        } else {
                            playPreSource(); //xuameng 直播时按左键把弹出菜单改为换源
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if(isBack || isVOD) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {    //xuameng左右键与seekbar调节进度冲突
                            }else{
                               showProgressBars(true);
						    }
                        } else {
                            playNextSource(); //xuameng 直播时按右键把弹出菜单改为换源
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER: //xuameng 修复回看时不能暂停，弹出菜单问题
                        if(mVideoView == null) return true;
                        if(isBack) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {   //xuameng确认键与暂停键冲突
                            }else if(mVideoView.isPlaying()) {
                                showProgressBars(true);
                            }else{
                                mVideoView.start();
                                iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                            }
                        } else if(isVOD) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {
                            }else {
                                showChannelList();
                            }
                        } else {
                            showChannelList();
                        }
                        break;
                    case KeyEvent.KEYCODE_ENTER: //xuameng 修复回看时不能暂停，弹出菜单问题
                        if(mVideoView == null) return true;
                        if(isBack) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {   //xuameng确认键与暂停键冲突
                            }else if(mVideoView.isPlaying()) {
                                showProgressBars(true);
                            }else{
                                mVideoView.start();
                                iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                            }
                        } else if(isVOD) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {
                            }else {
                                showChannelList();
                            }
                        } else {
                            showChannelList();
                        }
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: //xuameng 修复回看时不能暂停，弹出菜单问题
                        if(mVideoView == null) return true;
                        if(isBack) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {   //xuameng确认键与暂停键冲突
                            }else if(mVideoView.isPlaying()) {
                                showProgressBars(true);
                            }else{
                                mVideoView.start();
                                iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                            }
                        } else if(isVOD) {
                            if(backcontroller.getVisibility() == View.VISIBLE) {
                            }else {
                                showChannelList();
                            }
                        } else {
                            showChannelList();
                        }
                        break;
                    default:
                        if(keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) { //xuameng遥控数字键切换频道
                            keyCode -= KeyEvent.KEYCODE_0;
                        } else if(keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                            keyCode -= KeyEvent.KEYCODE_NUMPAD_0;
                        } else {
                            break;
                        }
                        numericKeyDown(keyCode);
                }
            }
        } else if(event.getAction() == KeyEvent.ACTION_UP) {}
        return super.dispatchKeyEvent(event);
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(mVideoView != null) {
            mVideoView.resume();
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if(mVideoView != null) {
            mVideoView.pause();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        if(countDownTimer != null) {
           countDownTimer.cancel();
           countDownTimer = null;
        }
        OkGo.getInstance().cancelTag("xuameng");
    }
    private void showChannelList() {
        if(liveChannelGroupList.isEmpty()) return; //xuameng新增
        if(tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHideSettingLayoutRun();
            return;
        }
        if(isVOD) {
            Mtv_left_top_xu.setVisibility(View.GONE);
        }
        if(tvLeftChannelListLayout.getVisibility() == View.INVISIBLE) {
            //重新载入上一次状态
            liveChannelItemAdapter.setNewData(getLiveChannels(currentChannelGroupIndex));
            if(currentLiveChannelIndex > -1) mLiveChannelView.scrollToPosition(currentLiveChannelIndex); //xuameng先滚动再选择防止空指针
            mChannelGroupView.scrollToPosition(currentChannelGroupIndex); //xuameng先滚动再选择防止空指针
            //      mChannelGroupView.setSelection(currentChannelGroupIndex); //xuameng先滚动再选择防止空指针
            //       mLiveChannelView.setSelection(currentLiveChannelIndex); //xuameng先滚动再选择防止空指针
            if(countDownTimer10 != null) {
                countDownTimer10.cancel();
            }
            countDownTimer10 = new CountDownTimer(100, 50) { //底部epg隐藏时间设定
                public void onTick(long j) {}
                public void onFinish() {
                    mFocusCurrentChannelAndShowChannelList();
                }
            };
            countDownTimer10.start();
        } else {
            mHideChannelListRun();
        }
    }
    private void showChannelListTouch() {
        //重新载入上一次状态
        liveChannelItemAdapter.setNewData(getLiveChannels(currentChannelGroupIndex));
        if(currentLiveChannelIndex > -1) mLiveChannelView.scrollToPosition(currentLiveChannelIndex); //xuameng先滚动再选择防止空指针
        mChannelGroupView.scrollToPosition(currentChannelGroupIndex); //xuameng先滚动再选择防止空指针
        //        mChannelGroupView.setSelection(currentChannelGroupIndex); //xuameng先滚动再选择防止空指针
        //        mLiveChannelView.setSelection(currentLiveChannelIndex); //xuameng先滚动再选择防止空指针
        if(countDownTimer10 != null) {
            countDownTimer10.cancel();
        }
        countDownTimer10 = new CountDownTimer(100, 50) { //底部epg隐藏时间设定
            public void onTick(long j) {}
            public void onFinish() {
                mFocusCurrentChannelAndShowChannelList();
            }
        };
        countDownTimer10.start();
    }
    private void mFocusCurrentChannelAndShowChannelList() { //xuameng左侧菜单显示
        if(mChannelGroupView.isScrolling() || mLiveChannelView.isScrolling() || mChannelGroupView.isComputingLayout() || mLiveChannelView.isComputingLayout()) {
            if(countDownTimer20 != null) {
                countDownTimer20.cancel();
            }
            countDownTimer20 = new CountDownTimer(100, 50) { //底部epg隐藏时间设定
                public void onTick(long j) {}
                public void onFinish() {
                    mFocusCurrentChannelAndShowChannelList();
                }
            };
            countDownTimer20.start();
        } else {
            mFocusCurrentChannelAndShowChannelListXu();
        }
    }
    private void mFocusCurrentChannelAndShowChannelListXu() { //xuameng左侧菜单显示
        epgListAdapter.getSelectedIndex(); //xuamengEPG打开菜单自动变颜色 
        liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
        liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        mChannelGroupView.setSelection(currentChannelGroupIndex); //xuameng先滚动再选择防止空指针
        mLiveChannelView.setSelection(currentLiveChannelIndex); //xuameng先滚动再选择防止空指针
        RecyclerView.ViewHolder holder = mLiveChannelView.findViewHolderForAdapterPosition(currentLiveChannelIndex);
        if(holder != null) holder.itemView.requestFocus();
        tvLeftChannelListLayout.setVisibility(View.VISIBLE);
        tvLeftChannelListLayout.requestLayout(); //xuameng surface按键不好使
        HideBottomEpg(); //隐藏底部菜单
        isShowlist = true;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvLeftChannelListLayout.getLayoutParams();
        if(countDownTimer5 != null) {
            countDownTimer5.cancel();
        }
        if(countDownTimer7 != null) {
            countDownTimer7.cancel();
        }
        countDownTimer5 = new CountDownTimer(5000, 1000) { //底部epg隐藏时间设定
            public void onTick(long j) {}
            public void onFinish() {
                mHideChannelListRun();
            }
        };
        countDownTimer5.start();
    }
    private void mHideChannelListRun() { //xuameng左侧菜单隐藏
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvLeftChannelListLayout.getLayoutParams();
        isShowlist = false;
        if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
        }
        if(isVOD) {
            Mtv_left_top_xu.setVisibility(View.VISIBLE);
        }
        if(!isCurrentLiveChannelValidXu()) return;    //xuameng 空指针修复
        liveEpgDateAdapter.setSelectedIndex(1);   //xuameng频道EPG日期自动选今天
        getEpg(new Date());
    }
    private void mHideChannelListRunXu() { //xuameng左侧菜单延时5秒隐藏
        if(countDownTimer7 != null) {
            countDownTimer7.cancel();
        }
        if(countDownTimer5 != null) {
            countDownTimer5.cancel();
        }
        countDownTimer7 = new CountDownTimer(5000, 1000) { //xuameng左侧菜单延时5秒时间设定
            public void onTick(long j) {}
            public void onFinish() {
                mHideChannelListRun();
            }
        };
        countDownTimer7.start();
    }
    private HashMap < String, String > liveWebHeader() { //xuameng自定义UA
        return Hawk.get(HawkConfig.LIVE_WEB_HEADER);
    }
    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) { //xuameng播放
        if((channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource) || (changeSource && currentLiveChannelItem.getSourceNum() == 1) && !XuSource) {
            // xuamengEPG日期自动选今天
            liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
            getEpg(new Date());
            if(isVOD) {
                if(backcontroller.getVisibility() == View.GONE) {
                   showProgressBars(true);
                }
            }else{
                showBottomEpg();
            }
            return true;
        }
        if(mVideoView == null) return true; //XUAMENG可能会引起空指针问题的修复
        mVideoView.release(); //XUAMENG可能会引起空指针问题的修复
        if(!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
            HawkUtils.setLastLiveChannelGroup(liveChannelGroupList.get(currentChannelGroupIndex).getGroupName()); //xuameng记忆频道组
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        }
        channel_Name = currentLiveChannelItem;
        isBack = false;
        XuSource = false;
        isVOD = false;
        HideBottomEpg(); //隐藏底部菜单
        if(currentLiveChannelItem.getUrl().contains("PLTV/") || currentLiveChannelItem.getUrl().contains("TVOD/")) { //xuameng判断直播源URL中有没有PLTV字符，有才可以时移
            currentLiveChannelItem.setinclude_back(true);
        } else {
            currentLiveChannelItem.setinclude_back(false);
        }

        if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE  && TimeoutChangeSource || tvRightSettingLayout.getVisibility() == View.VISIBLE && TimeoutChangeSource) {
            App.showToastShort(mContext, "聚汇直播提示您：播放失败！自动切换中！");
            TimeoutChangeSource = false;  //xuameng是否自动换源
        }else{
		    getEpg(new Date());
            showBottomEpg(); //XUAMENG重要点击频道播放，上面的不重新播放。只显示EPG
            TimeoutChangeSource = false;  //xuameng是否自动换源
        }

        liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
        simSeekPosition = 0; //XUAMENG重要,换视频时重新记录进度
        simSlideOffset = 0; //XUAMENG重要,换视频时重新记录进度
        if(liveWebHeader() != null) LOG.i("echo-" + liveWebHeader().toString());
        mVideoView.setUrl(currentLiveChannelItem.getUrl(), liveWebHeader());
        mVideoView.start();
        if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
            iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
        }
        return true;
    }
    private boolean playChannelxu(int channelGroupIndex, int liveChannelIndex, boolean changeSource) { //xuameng播放
        if(mVideoView == null) return true; //XUAMENG可能会引起空指针问题的修复
        if(!changeSource) {
            currentChannelGroupIndexXu = channelGroupIndex; //xuameng重要频道组
            currentLiveChannelIndexXu = liveChannelIndex; //xuameng重要频道名称
            currentLiveChannelItemXu = getLiveChannels(currentChannelGroupIndexXu).get(currentLiveChannelIndexXu);
            liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
        }
        channel_NameXu = currentLiveChannelItemXu; //xuameng重要EPG名称
        if(currentLiveChannelItemXu.getUrl().contains("PLTV/") || currentLiveChannelItemXu.getUrl().contains("TVOD/")) { //xuameng判断直播源URL中有没有PLTV字符，有才可以时移
            currentLiveChannelItemXu.setinclude_back(true);
        } else {
            currentLiveChannelItemXu.setinclude_back(false);
        }
        getEpgxu(new Date()); //xuameng重要EPG名称
        return true;
    }
    private void playNext() { //xuameng 下一个频道
        if(mVideoView == null) {
            return;
        }
        if(!isCurrentLiveChannelValid()) return;   //xuameng 空指针修复
        int channelGroupIndexXu = liveChannelGroupAdapter.getSelectedGroupIndex(); //xuameng当前选定的频道组
        if(liveChannelGroupList.size() - 1 < 1 && getLiveChannels(channelGroupIndexXu).size() - 1 < 1) { //如果只有一个频道组就播放当前频道，不胯下胯下跨选频道组
            App.showToastShort(mContext, "聚汇影视提示您：只有一个频道！");
            return;
        }
        if(!Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false) && getLiveChannels(channelGroupIndexXu).size() - 1 < 1) {
            App.showToastShort(mContext, "聚汇影视提示您：未选择跨选分类且本组只有一个频道或没有选择频道！");
            return;
        }
        Integer[] groupChannelIndex = getNextChannel(1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }
    private void playPrevious() {
        if(mVideoView == null) {
            return;
        }
        if(!isCurrentLiveChannelValid()) return;    //xuameng 空指针修复
        int channelGroupIndexXu = liveChannelGroupAdapter.getSelectedGroupIndex(); //xuameng当前选定的频道组
        if(liveChannelGroupList.size() - 1 < 1 && getLiveChannels(channelGroupIndexXu).size() - 1 < 1) { //如果只有一个频道组就播放当前频道，不胯下胯下跨选频道组
            App.showToastShort(mContext, "聚汇影视提示您：只有一个频道！");
            return;
        }
        if(!Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false) && getLiveChannels(channelGroupIndexXu).size() - 1 < 1) {
            App.showToastShort(mContext, "聚汇影视提示您：未选择跨选分类且本组只有一个频道或没有选择频道！");
            return;
        }
        Integer[] groupChannelIndex = getNextChannel(-1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }
    public void playPreSource() {
        if(mVideoView == null) {
            return;
        }
        if(!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }
    public void playNextSource() {
        if(mVideoView == null) {
            return;
        }
        if(!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }
    public void playXuSource() {
        if(mVideoView == null) {
            return;
        }
        if(!isCurrentLiveChannelValid()) return;
        XuSource = true;
        currentLiveChannelItem.getUrl();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }
    //显示设置列表
    private void showSettingGroup() {
        if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHideChannelListRun();
        }
        if(tvRightSettingLayout.getVisibility() == View.INVISIBLE) {
            if(!isCurrentLiveChannelValid()) return;
            //重新载入默认状态
            if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
            }
            loadCurrentSourceList();
            liveSettingGroupAdapter.setNewData(liveSettingGroupList);
            selectSettingGroup(0, false);
            mSettingGroupView.scrollToPosition(0);
            mSettingItemView.scrollToPosition(currentLiveChannelItem.getSourceIndex());
            if(countDownTimer22 != null) {
                countDownTimer22.cancel();
            }
            countDownTimer22 = new CountDownTimer(100, 50) { //XUAMENG显示右侧菜单时间设定
                public void onTick(long j) {}
                public void onFinish() {
                    mFocusAndShowSettingGroup();
                }
            };
            countDownTimer22.start();
        } else {
            mHideSettingLayoutRun();
        }
    }
    private void mFocusAndShowSettingGroup() { //XUAMENG显示右侧菜单
        if(mSettingGroupView.isScrolling() || mSettingItemView.isScrolling() || mSettingGroupView.isComputingLayout() || mSettingItemView.isComputingLayout()) {
            if(countDownTimer21 != null) {
                countDownTimer21.cancel();
            }
            countDownTimer21 = new CountDownTimer(100, 50) { //底部epg隐藏时间设定
                public void onTick(long j) {}
                public void onFinish() {
                    mFocusAndShowSettingGroup();
                }
            };
            countDownTimer21.start();
        } else {
            mFocusAndShowSettingGroupXu();
        }
    }
    private void mFocusAndShowSettingGroupXu() { //XUAMENG显示右侧菜单
        RecyclerView.ViewHolder holder = mSettingGroupView.findViewHolderForAdapterPosition(0);
        if(holder != null) holder.itemView.requestFocus();
        tvRightSettingLayout.setVisibility(View.VISIBLE);
        tvRightSettingLayout.requestLayout(); //xuameng surface按键不好使
        HideBottomEpg(); //隐藏底部菜单
        hideNetSpeed(); //xuameng隐藏右下网速
        hideTime(); //xuameng隐藏右下系统时间
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
        if(countDownTimer6 != null) {
            countDownTimer6.cancel();
        }
        if(countDownTimer8 != null) {
            countDownTimer8.cancel();
        }
        countDownTimer6 = new CountDownTimer(5000, 1000) { //XUAMENG时间设定
            public void onTick(long j) {}
            public void onFinish() {
                mHideSettingLayoutRun();
            }
        };
        countDownTimer6.start();
    }
    private void mHideSettingLayoutRun() { //XUAMENG隐藏右侧菜单
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
        if(tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            tvRightSettingLayout.setVisibility(View.INVISIBLE);
            liveSettingGroupAdapter.setSelectedGroupIndex(-1);
            showTime(); //XUAMENG显示右下时间
            showNetSpeed(); //XUAMENG显示右下网速
            if(mVideoView == null) return;
            if(isVOD) {
                if(!mVideoView.isPlaying()) {
                    iv_Play_Xu.setVisibility(View.VISIBLE); //回看暂停图标
                }
            }
        }
    }
    private void mHideSettingLayoutRunXu() { //XUAMENG隐藏右侧延时5秒菜单
        if(countDownTimer8 != null) {
            countDownTimer8.cancel();
        }
        if(countDownTimer6 != null) {
            countDownTimer6.cancel();
        }
        countDownTimer8 = new CountDownTimer(5000, 1000) { //底部epg隐藏时间设定
            public void onTick(long j) {}
            public void onFinish() {
                mHideSettingLayoutRun();
            }
        };
        countDownTimer8.start();
    }
    //laodao 7天Epg数据绑定和展示
    private void initEpgListView() {
        mRightEpgList.setHasFixedSize(true);
        mRightEpgList.setItemAnimator(null);   //xuameng禁用TVRecyclerView动画
        mRightEpgList.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        epgListAdapter = new LiveEpgAdapter();
        mRightEpgList.setAdapter(epgListAdapter);
        mRightEpgList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHideChannelListRunXu();
                if(newState == mRightEpgList.SCROLL_STATE_IDLE) {
                }
            }
        });
        //电视
        mRightEpgList.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if(!mRightEpgList.isScrolling() && !mRightEpgList.isComputingLayout()) { //xuameng如果EPG正在滚动返回，解决BUG
                    epgListAdapter.setFocusedEpgIndex(-1);
                }
            }
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                mHideChannelListRunXu();
                epgListAdapter.setFocusedEpgIndex(position);
            }
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                currentChannelGroupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
                currentLiveChannelIndex = liveChannelItemAdapter.getSelectedChannelIndex();
                currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
                Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
                channel_Name = currentLiveChannelItem; //xuameng重要EPG名称
                String channelName = channel_Name.getChannelName();
                Date date = liveEpgDateAdapter.getSelectedIndex() < 0 ? new Date() : liveEpgDateAdapter.getData().get(liveEpgDateAdapter.getSelectedIndex()).getDateParamVal();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                Epginfo selectedData = epgListAdapter.getItem(position);
                String targetDate = dateFormat.format(date);
                String shiyiStartdate = targetDate + selectedData.originStart.replace(":", "") + "00";
                String shiyiEnddate = targetDate + selectedData.originEnd.replace(":", "") + "00";
                Date now = new Date();
                if(new Date().compareTo(selectedData.startdateTime) < 0) {
                    return;
                }
                //                epgListAdapter.setSelectedEpgIndex(position);   //xuameng取消电视手机点击无法回看的EPG节目源变色
                if(now.compareTo(selectedData.startdateTime) >= 0 && now.compareTo(selectedData.enddateTime) <= 0) {
                    if(mVideoView == null) return;
                    mVideoView.release();
                    mVideoView.setUrl(currentLiveChannelItem.getUrl(), liveWebHeader());
                    mVideoView.start();
                    if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    }
                    showBottomEpg(); //xuameng显示EPG和上面菜单 
                    return;
                }
                String shiyiUrl = currentLiveChannelItem.getUrl();
                if(now.compareTo(selectedData.startdateTime) < 0) {} else if(shiyiUrl.contains("PLTV/") || shiyiUrl.contains("TVOD/")) {
                    if(mVideoView == null) return;
                    isBack = true;
                    isVOD = false;
                    mVideoView.release();
                    shiyi_time = shiyiStartdate + "-" + shiyiEnddate;
                    if(shiyiUrl.indexOf("?") <= 0) {
                        shiyiUrl += "?playseek=" + shiyi_time;
                    } else if(shiyiUrl.indexOf("playseek") > 0) {
                        shiyiUrl = shiyiUrl.replaceAll("playseek=(.*)", "playseek=" + shiyi_time);
                    } else {
                        shiyiUrl += "&playseek=" + shiyi_time;
                    }
                    LOG.i("echo-回看地址playUrl :" + shiyiUrl);
                    playUrl = shiyiUrl;
                    if(liveWebHeader() != null) LOG.i("echo-liveWebHeader :" + liveWebHeader().toString());
                    mVideoView.setUrl(playUrl, liveWebHeader());
                    mVideoView.start();
                    if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    }
                    liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
                    shiyi_time_c = (int) getTime(formatDate.format(nowday) + " " + selectedData.start + ":" + "00", formatDate.format(nowday) + " " + selectedData.end + ":" + "00");
                    ViewGroup.LayoutParams lp = iv_play.getLayoutParams();
                    lp.width = videoHeight / 7;
                    lp.height = videoHeight / 7;
                    showProgressBars(true);
                    tv_right_top_type.setText("回看中");
                    iv_play_pause.setText("回看暂停中！聚汇直播欢迎您的收看！");
                }
            }
        });
        //手机/模拟器
        epgListAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                currentChannelGroupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
                currentLiveChannelIndex = liveChannelItemAdapter.getSelectedChannelIndex();
                currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
                Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
                channel_Name = currentLiveChannelItem; //xuameng重要EPG名称
                String channelName = channel_Name.getChannelName();
                Date date = liveEpgDateAdapter.getSelectedIndex() < 0 ? new Date() : liveEpgDateAdapter.getData().get(liveEpgDateAdapter.getSelectedIndex()).getDateParamVal();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                Epginfo selectedData = epgListAdapter.getItem(position);
                String targetDate = dateFormat.format(date);
                String shiyiStartdate = targetDate + selectedData.originStart.replace(":", "") + "00";
                String shiyiEnddate = targetDate + selectedData.originEnd.replace(":", "") + "00";
                Date now = new Date();
                if(new Date().compareTo(selectedData.startdateTime) < 0) {
                    return;
                }
                //                epgListAdapter.setSelectedEpgIndex(position);   //xuameng取消电视手机点击无法回看的EPG节目源变色
                if(now.compareTo(selectedData.startdateTime) >= 0 && now.compareTo(selectedData.enddateTime) <= 0) {
                    if(mVideoView == null) return;
                    mVideoView.release();
                    mVideoView.setUrl(currentLiveChannelItem.getUrl(), liveWebHeader());
                    mVideoView.start();
                    if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    }
                    //   epgListAdapter.setShiyiSelection(-1, false, timeFormat.format(date));
                    showBottomEpg(); //xuameng显示EPG和上面菜单 				
                    return;
                }
                String shiyiUrl = currentLiveChannelItem.getUrl();
                if(now.compareTo(selectedData.startdateTime) < 0) {} else if(shiyiUrl.contains("PLTV/") || shiyiUrl.contains("TVOD/")) {
                    if(mVideoView == null) return;
                    isBack = true;
                    isVOD = false;
                    mVideoView.release();
                    shiyi_time = shiyiStartdate + "-" + shiyiEnddate;
                    if(shiyiUrl.indexOf("?") <= 0) {
                        shiyiUrl += "?playseek=" + shiyi_time;
                    } else if(shiyiUrl.indexOf("playseek") > 0) {
                        shiyiUrl = shiyiUrl.replaceAll("playseek=(.*)", "playseek=" + shiyi_time);
                    } else {
                        shiyiUrl += "&playseek=" + shiyi_time;
                    }
                    LOG.i("echo-回看地址playUrl :" + shiyiUrl);
                    playUrl = shiyiUrl;
                    if(liveWebHeader() != null) LOG.i("echo-liveWebHeader :" + liveWebHeader().toString());
                    mVideoView.setUrl(playUrl, liveWebHeader());
                    mVideoView.start();
                    if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    }
                    liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
                    shiyi_time_c = (int) getTime(formatDate.format(nowday) + " " + selectedData.start + ":" + "00", formatDate.format(nowday) + " " + selectedData.end + ":" + "00");
                    ViewGroup.LayoutParams lp = iv_play.getLayoutParams();
                    lp.width = videoHeight / 7;
                    lp.height = videoHeight / 7;
                    showProgressBars(true);
                    tv_right_top_type.setText("回看中");
                    iv_play_pause.setText("回看暂停中！聚汇直播欢迎您的收看！");
                }
            }
        });
    }
    //laoda 生成7天回放日期列表数据
    private void initDayList() {
        liveDayList.clear();
        Date firstday = new Date(nowday.getTime() - 6 * 24 * 60 * 60 * 1000);
        for(int i = 0; i < 8; i++) {
            LiveDayListGroup daylist = new LiveDayListGroup();
            Date newday = new Date(firstday.getTime() + i * 24 * 60 * 60 * 1000);
            String day = formatDate1.format(newday);
            daylist.setGroupIndex(i);
            daylist.setGroupName(day);
            liveDayList.add(daylist);
        }
    }
    //kens 7天回放数据绑定和展示
    private void initEpgDateView() {
        mEpgDateGridView.setHasFixedSize(true);
        mEpgDateGridView.setItemAnimator(null);   //xuameng禁用TVRecyclerView动画
        mEpgDateGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveEpgDateAdapter = new LiveEpgDateAdapter();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        SimpleDateFormat datePresentFormat = new SimpleDateFormat("MM月dd日"); //xuameng加中文
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        for(int i = 0; i < 9; i++) { //XUAMENG8天回看
            Date dateIns = calendar.getTime();
            LiveEpgDate epgDate = new LiveEpgDate();
            epgDate.setIndex(i);
            epgDate.setDatePresented(datePresentFormat.format(dateIns));
            epgDate.setDateParamVal(dateIns);
            liveEpgDateAdapter.addData(epgDate);
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }
        mEpgDateGridView.setAdapter(liveEpgDateAdapter);
        mEpgDateGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHideChannelListRunXu(); //xuameng隐藏频道菜单
            }
        });
        //电视
        mEpgDateGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                liveEpgDateAdapter.setFocusedIndex(-1);
            }
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                mHideChannelListRunXu(); //xuameng隐藏频道菜单
                liveEpgDateAdapter.setFocusedIndex(position);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                mHideChannelListRunXu(); //xuameng隐藏频道菜单
                liveEpgDateAdapter.setSelectedIndex(position);
                currentChannelGroupIndexXu = liveChannelGroupAdapter.getSelectedGroupIndex(); //XUAMENG 7天EPG
                currentLiveChannelIndexXu = liveChannelItemAdapter.getSelectedChannelIndex();
                currentLiveChannelItemXu = getLiveChannels(currentChannelGroupIndexXu).get(currentLiveChannelIndexXu);
                channel_NameXu = currentLiveChannelItemXu;
                getEpgxu(liveEpgDateAdapter.getData().get(position).getDateParamVal()); //XUAMENG 7天EPG
            }
        });
        //手机/模拟器
        liveEpgDateAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                mHideChannelListRunXu(); //xuameng隐藏频道菜单
                liveEpgDateAdapter.setSelectedIndex(position);
                currentChannelGroupIndexXu = liveChannelGroupAdapter.getSelectedGroupIndex(); //XUAMENG 7天EPG
                currentLiveChannelIndexXu = liveChannelItemAdapter.getSelectedChannelIndex();
                currentLiveChannelItemXu = getLiveChannels(currentChannelGroupIndexXu).get(currentLiveChannelIndexXu);
                channel_NameXu = currentLiveChannelItemXu;
                getEpgxu(liveEpgDateAdapter.getData().get(position).getDateParamVal()); //XUAMENG 7天EPG
            }
        });
        liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
    }
    private void initVideoView() {
        LiveController controller = new LiveController(this);
        controller.setListener(new LiveController.LiveControlListener() {
            @Override
            public boolean singleTap() { //xuameng点击屏幕显示频道菜单
                if(isBack) { //xuameng显示EPG和显示时移控制栏
                    if(backcontroller.getVisibility() == View.VISIBLE) {
                        HideBottomEpg(); //隐藏底部菜单
                    } else if(backcontroller.getVisibility() == View.GONE) {
                        showProgressBars(true);
                    }
                    return true;
                }
                if(isVOD) {
                    if(backcontroller.getVisibility() == View.VISIBLE) {
                        showChannelList();
                    } else {
                        showChannelList();
                    }
                    return true;
                } else {
                    showChannelList();
                }
                return false; //XUAMENG如果true 就会默认执行
            }
            @Override
            public void longPress() { //xuameng长按显示左边设置菜单
                if(isBack) {
                    if(backcontroller.getVisibility() == View.VISIBLE) {
                        HideBottomEpg(); //隐藏底部菜单
                    } else {
                        showProgressBars(true);
                    }
                } else {
                    showSettingGroup();
                }
            }
            @Override
            public boolean DoublePress() { //xuameng双击显示回看菜单并暂停
                if(mVideoView == null) return true;
                if(isBack) {
                    if(mVideoView.isPlaying()) {
                        showProgressBars(true);
                        mVideoView.pause();
                        iv_Play_Xu.setVisibility(View.VISIBLE); //回看暂停图标
                        iv_playpause.setText("播放"); 
                    } else {
                        HideBottomEpg(); //隐藏底部菜单
                        mVideoView.start();
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                        iv_playpause.setText("暂停"); 
                    }
                    return true;
                }
                if(isVOD) {
                    if(mVideoView.isPlaying()) {
                        showProgressBars(true);
                        mVideoView.pause();
                        iv_Play_Xu.setVisibility(View.VISIBLE); //回看暂停图标
                        iv_playpause.setText("播放"); 
                    } else {
                        HideBottomEpg(); //隐藏底部菜单
                        mHideChannelListRun(); //xuameng显示EPG就隐藏左右菜单
                        mHideSettingLayoutRun(); //xuameng显示EPG就隐藏左右菜单
                        mVideoView.start();
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                        iv_playpause.setText("暂停"); 
                    }
                    return true;
                } else if(isLl_epgVisible()) {
                    HideBottomEpg();   //隐藏底部菜单
                } else if(!isLl_epgVisible()) {
                    showBottomEpg(); //xuameng显示EPG和上面菜单
                }
                return false;
            }
            @Override
            public void playStateChanged(int playState) {
                switch(playState) {
                    case VideoView.STATE_IDLE:
                        isVideoplaying = false;
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                        tv_size.setText("[0 X 0]"); //XUAMENG分辨率
                        if(MxuamengMusic.getVisibility() == View.VISIBLE) { //xuameng播放音乐背景
                            MxuamengMusic.setVisibility(View.GONE);
                        }
                        if(iv_circle_bg_xu.getVisibility() == View.VISIBLE) { //xuameng音乐播放时图标
                            iv_circle_bg_xu.setVisibility(View.GONE);
                        }
                        if(customVisualizer.getVisibility() == View.VISIBLE) { //xuameng播放音乐柱状图
                           customVisualizer.setVisibility(View.GONE);
                        }
				        releaseVisualizer();  //xuameng播放音乐背景
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                        sBar = (SeekBar) findViewById(R.id.pb_progressbar); //xuameng重置进度条
                        sBar.setMax(0);
                        sBar.setProgress(0);
                        tv_currentpos.setText("00:00");
                        tv_duration.setText("00:00");
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                        String width = Integer.toString(mVideoView.getVideoSize()[0]);
                        String height = Integer.toString(mVideoView.getVideoSize()[1]);
                        tv_size.setText("[" + width + " X " + height + "]");

                        musicAnimation = livePlayerManager.getLivePlaymusic();
                        if (musicAnimation){
                            int newSessionId = mVideoView.getAudioSessionId();   //xuameng音乐播放动画
                            if(newSessionId != audioSessionId) { // 避免重复初始化
                               initVisualizer();  //xuameng音乐播放动画
                            }
                        }

                        int duration1 = (int) mVideoView.getDuration();
                        if(isBack) {
                            sBar = (SeekBar) findViewById(R.id.pb_progressbar); //xuameng回看进度条
                            sBar.setMax(duration1);
                            sBar.setProgress((int) mVideoView.getCurrentPosition());
                            tv_currentpos.setText(durationToString((int) mVideoView.getCurrentPosition()));
                            tv_duration.setText(durationToString(duration1));
                            return;
                        }
                        if(duration1 > 130000 && duration1 < 180000000) { //xuameng处理某些播放器时长获取不准确问题
                            isVOD = true;
                            if(isLl_epgVisible()) { //xuameng修复
                                showProgressBars(true);
                            }
                            sBar = (SeekBar) findViewById(R.id.pb_progressbar);
                            sBar.setMax(duration1);
                            sBar.setProgress((int) mVideoView.getCurrentPosition());
                            tv_currentpos.setText(durationToString((int) mVideoView.getCurrentPosition()));
                            tv_duration.setText(durationToString(duration1));
                            tv_right_top_type.setText("点播中");
                            iv_play_pause.setText("点播暂停中！聚汇直播欢迎您的收看！");
                        } else {
                            isVOD = false;
                            Mtv_left_top_xu.setVisibility(View.GONE); //xuameng返回键隐藏左上回看菜单
                        }
                        break;
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        currentLiveChangeSourceTimes = 0;
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBack);
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBuffer);
                        isVideoplaying = true;
                        isBuffer = false;
                        if(isBack) { //xuameng 回看不成功返回直播
                            int durationXu = (int) mVideoView.getDuration();
                            if(durationXu < 60000) {
                                if(mVideoView != null) {
                                    mVideoView.release();
                                }
                                Mtv_left_top_xu.setVisibility(View.GONE); //xuameng返回键隐藏左上回看菜单
                                iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                                hideTimeXu(); //xuameng隐藏系统时间
                                hideNetSpeedXu(); //XUAMENG隐藏左上网速
                                liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
                                playXuSource();
                                showToastXu();
                            }
                        }
                        break;
                    case VideoView.STATE_ERROR:
                        isVideoplaying = false;
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        if(isBack) {
                            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);     //xuameng清除回调修复BUG
                            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBack);
                            mHandler.postDelayed(mConnectTimeoutChangeSourceRunBack, 5000); //xuameng回看完毕5秒退出
                            return;
                        }
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, 10000); //xuameng播放超时10秒换源
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        if(iv_circle_bg_xu.getVisibility() == View.VISIBLE) { //xuameng音乐播放时图标
                            iv_circle_bg_xu.setVisibility(View.GONE);
                        }
                        isBuffer = true;
                        if(isBack) {
                            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);   //xuameng清除回调修复BUG
                            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBuffer);
                            mHandler.postDelayed(mConnectTimeoutChangeSourceRunBuffer, (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000); //xuameng回看超时
                            return;
                        }
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000);
                        break;
                }
            }
            @Override
            public void changeSource(int direction) {
                if(direction > 0)
                    if(isBack) { //xuameng手机换源和显示时移控制栏
                        if(backcontroller.getVisibility() == View.VISIBLE) {
                            HideBottomEpg();  //隐藏底部菜单
                        } else if(backcontroller.getVisibility() == View.GONE) {
                            showProgressBars(true);
                        }
                    } else if(isVOD) {
                    if(backcontroller.getVisibility() == View.VISIBLE) {
                        HideBottomEpg();  //隐藏底部菜单
                    } else if(backcontroller.getVisibility() == View.GONE) {
                        showProgressBars(true);
                    }
                } else {
                    playNextSource();
                    liveSettingGroupAdapter.setSelectedGroupIndex(-1); //xuameng右菜单BUG修复
                } else if(direction < 0)
                    if(isBack) { //xuameng手机换源和隐藏时移控制栏
                        if(backcontroller.getVisibility() == View.VISIBLE) {
                            HideBottomEpg();  //隐藏底部菜单
                        } else if(backcontroller.getVisibility() == View.GONE) {
                            showProgressBars(true);
                        }
                    } else if(isVOD) {
                    if(backcontroller.getVisibility() == View.VISIBLE) {
                        HideBottomEpg();  //隐藏底部菜单
                    } else if(backcontroller.getVisibility() == View.GONE) {
                        showProgressBars(true);
                    }
                } else {
                    playPreSource();
                    liveSettingGroupAdapter.setSelectedGroupIndex(-1); //xuameng右菜单BUG修复
                }
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }
    private Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            TimeoutChangeSource = true;  //xuameng是否自动换源
            currentLiveChangeSourceTimes++;
            int channelGroupIndexXu = liveChannelGroupAdapter.getSelectedGroupIndex(); //xuameng当前选定的频道组
            if(currentLiveChannelItem.getSourceNum() == currentLiveChangeSourceTimes) { //xuameng如果只有一个源就换频道
                currentLiveChangeSourceTimes = 0;
                if(liveChannelGroupList.size() - 1 < 1 && getLiveChannels(channelGroupIndexXu).size() - 1 < 1) { //如果只有一个频道组就播放当前频道，不胯下跨选频道组
                    playXuSource();
                    App.showToastShort(mContext, "聚汇影视提示您：只有一个频道！正在重播！");
                    return;
                }
                if(!Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false) && getLiveChannels(channelGroupIndexXu).size() - 1 < 1) {
                    playXuSource();
                    App.showToastShort(mContext, "聚汇影视提示您：未选择跨选分类且本组只有一个频道！正在重播！");
                    return;
                }
                Integer[] groupChannelIndex = getNextChannel(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false) ? -1 : 1); //xuameng换台反转与跨选分类
                playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
            } else {
                playNextSource();     //xuameng否则换源
            }
        }
    };
    public void showToastXu() {   //xuameng回看完成
        App.HideToast();  //xuameng HideToast
        cancelxToast();
        LayoutInflater inflater = getLayoutInflater();
        View customToastView = inflater.inflate(R.layout.review_toast, null);
        ImageView imageView = customToastView.findViewById(R.id.toastImage);
        xToast = new Toast(getApplicationContext());
        xToast.setDuration(Toast.LENGTH_SHORT);
        xToast.setView(customToastView);
        xToast.setGravity(Gravity.CENTER, 0, 0); //xuameng 20为左右，0是上下
        xToast.show();
    }
    public void showToastError() {  //xuameng回看错误
        App.HideToast();   //xuameng HideToast
        cancelxToast();
        LayoutInflater inflater = getLayoutInflater();
        View customToastView = inflater.inflate(R.layout.review_toast_error, null);
        ImageView imageView = customToastView.findViewById(R.id.toastImage);
        xToast = new Toast(getApplicationContext());
        xToast.setDuration(Toast.LENGTH_LONG);
        xToast.setView(customToastView);
        xToast.setGravity(Gravity.CENTER, 0, 0); //xuameng 20为左右，0是上下
        xToast.show();
    }
    public void showLiveXu() {  //xuameng退出直播
        App.HideToast();   //xuameng HideToast
        cancelxToast();
        LayoutInflater inflater = getLayoutInflater();
        View customToastView = inflater.inflate(R.layout.live_toast, null);
        ImageView imageView = customToastView.findViewById(R.id.toastImage);
        xToast = new Toast(getApplicationContext());
        xToast.setDuration(Toast.LENGTH_SHORT);
        xToast.setView(customToastView);
        xToast.setGravity(Gravity.CENTER, 0, 0); //xuameng 20为左右，0是上下
        xToast.show();
    }
    public void showToastBack() {    //xuameng退出回看
        App.HideToast();  //xuameng HideToast
        cancelxToast();
        LayoutInflater inflater = getLayoutInflater();
        View customToastView = inflater.inflate(R.layout.review_toast_back, null);
        ImageView imageView = customToastView.findViewById(R.id.toastImage);
        xToast = new Toast(getApplicationContext());
        xToast.setDuration(Toast.LENGTH_SHORT);
        xToast.setView(customToastView);
        xToast.setGravity(Gravity.CENTER, 0, 0); //xuameng 20为左右，0是上下
        xToast.show();
    }
    private Runnable mConnectTimeoutChangeSourceRunBack = new Runnable() { //xuameng为回看失败准备
        @Override
        public void run() {
            playXuSource();
            showToastXu();
        }
    };
    private Runnable mConnectTimeoutChangeSourceRunBuffer = new Runnable() { //xuameng为回看失败准备
        @Override
        public void run() {
            playXuSource();
            showToastError();
        }
    };
    private void initChannelGroupView() {
        mChannelGroupView.setHasFixedSize(true);
        mChannelGroupView.setItemAnimator(null);   //xuameng禁用TVRecyclerView动画
        mChannelGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveChannelGroupAdapter = new LiveChannelGroupAdapter();
        mChannelGroupView.setAdapter(liveChannelGroupAdapter);
        mChannelGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHideChannelListRunXu(); //xuameng隐藏频道菜单
            }
        });
        //电视
        mChannelGroupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectChannelGroup(position, true, -1); //xuameng频道组
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if(isNeedInputPassword(position)) {
                    showPasswordDialog(position, -1);
                }
            }
        });
        //手机/模拟器
        liveChannelGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectChannelGroup(position, false, -1);
            }
        });
    }
    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if(focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(groupIndex);
            liveChannelItemAdapter.setFocusedChannelIndex(-1); //xuameng修复频道名称移走焦点变色问题
        }
        if((groupIndex > -1 && groupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            isTouch = true;
            liveChannelGroupAdapter.setSelectedGroupIndex(groupIndex);
            if(isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
        }
        if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHideChannelListRunXu(); //xuameng隐藏频道菜单
        }
    }
    private void initLiveChannelView() {
        mLiveChannelView.setHasFixedSize(true);
        mLiveChannelView.setItemAnimator(null);   //xuameng禁用TVRecyclerView动画
        mLiveChannelView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveChannelItemAdapter = new LiveChannelItemAdapter();
        mLiveChannelView.setAdapter(liveChannelItemAdapter);
        mLiveChannelView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHideChannelListRunXu(); //xuameng隐藏频道菜单
            }
        });
        //电视
        mLiveChannelView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                liveChannelItemAdapter.setFocusedChannelIndex(-1); //xuameng修复频道名称移走焦点变色问题
            }
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                isTouch = false;
                if(position < 0) return;
                int channelGroupIndexXu = liveChannelGroupAdapter.getSelectedGroupIndex(); //xuameng当前选定的频道组
                if(position == getLiveChannels(channelGroupIndexXu).size() - 1) { //xuameng判断是否是最后一个item
                    itemView.setId(View.generateViewId());
                    itemView.setNextFocusDownId(itemView.getId()); //xuameng不超出item
                } else {
                    itemView.setNextFocusDownId(View.NO_ID);
                }
                liveChannelGroupAdapter.setFocusedGroupIndex(-1);
                liveChannelItemAdapter.setFocusedChannelIndex(position);
                liveChannelItemAdapter.setSelectedChannelIndex(position);
                playChannelxu(liveChannelGroupAdapter.getSelectedGroupIndex(), liveChannelItemAdapter.getSelectedChannelIndex(), false); //xuameng换频道显示EPG
                liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
                mHideChannelListRunXu(); //xuameng隐藏频道菜单
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) { //选中播放就隐藏左侧频道菜单
                isTouch = false;
                clickLiveChannel(position);
                mHideChannelListRun(); //xuameng隐藏左侧频道菜单
            }
        });
        //手机/模拟器
        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickLiveChannel(position);
                isTouch = false;
                mHideChannelListRun(); //xuameng隐藏左侧频道菜单
            }
        });
    }
    private void clickLiveChannel(int position) {
        liveChannelItemAdapter.setSelectedChannelIndex(position);
        liveEpgDateAdapter.setSelectedIndex(1); //xuameng频道EPG日期自动选今天
        playChannel(liveChannelGroupAdapter.getSelectedGroupIndex(), position, false);
        if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHideChannelListRunXu(); //xuameng隐藏频道菜单
        }
    }
    private void initSettingGroupView() {
        mSettingGroupView.setHasFixedSize(true);
        mSettingGroupView.setItemAnimator(null);   //xuameng禁用TVRecyclerView动画
        mSettingGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingGroupView.setAdapter(liveSettingGroupAdapter);
        mSettingGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHideSettingLayoutRunXu();
            }
        });
        //电视
        mSettingGroupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectSettingGroup(position, true);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {}
        });
        //手机/模拟器
        liveSettingGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectSettingGroup(position, false);
            }
        });
    }
    private void selectSettingGroup(int position, boolean focus) {
        if(!isCurrentLiveChannelValid()) return;
        if(focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(position);
            liveSettingItemAdapter.setFocusedItemIndex(-1);
        }
        if(position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < -1) return;
        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(liveSettingGroupList.get(position).getLiveSettingItems());
        switch(position) {
            case 0:
                liveSettingItemAdapter.selectItem(currentLiveChannelItem.getSourceIndex(), true, false);
                break;
            case 1:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true);
                break;
            case 2:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true);
                break;
            case 6:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayrender(), true, true); //xuameng 获取渲染方式
                break;
            case 7:
                musicAnimation = livePlayerManager.getLivePlaymusic();
                if (musicAnimation){
                    liveSettingItemAdapter.selectItem(0, true, true);  //xuameng 音柱动画开
                }else{
                    liveSettingItemAdapter.selectItem(1, true, true);  //xuameng 音柱动画关
                }
                break;
        }
        int scrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if(scrollToPosition < 0) scrollToPosition = 0;
        mSettingItemView.scrollToPosition(scrollToPosition);
        mHideSettingLayoutRunXu();
    }
    private void initSettingItemView() {
        mSettingItemView.setHasFixedSize(true);
        mSettingItemView.setItemAnimator(null);   //xuameng禁用TVRecyclerView动画
        mSettingItemView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemView.setAdapter(liveSettingItemAdapter);
        mSettingItemView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHideSettingLayoutRunXu();
            }
        });
        //电视
        mSettingItemView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if(position < 0) return;
                liveSettingGroupAdapter.setFocusedGroupIndex(-1);
                liveSettingItemAdapter.setFocusedItemIndex(position);
                mHideSettingLayoutRunXu();
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickSettingItem(position);
                //			    mHideSettingLayoutRun();          //xuameng选中源就隐藏右侧菜单
            }
        });
        //手机/模拟器
        liveSettingItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickSettingItem(position);
                //				mHideSettingLayoutRun();         //xuameng选中源就隐藏右侧菜单
            }
        });
    }
    private void clickSettingItem(int position) {
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if(settingGroupIndex < 4) {
            if(position == liveSettingItemAdapter.getSelectedItemIndex()) return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch(settingGroupIndex) {
            case 0: //线路切换
                currentLiveChannelItem.setSourceIndex(position);
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
                break;
            case 1: //画面比例
                if(mVideoView == null) return;
                livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
                break;
            case 2: //播放解码
                if(mVideoView == null) return;
                mVideoView.release();
                livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
                mVideoView.setUrl(currentLiveChannelItem.getUrl(), liveWebHeader());
                mVideoView.start();
                if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                    iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                }
                break;
            case 3: //超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4: //偏好设置
                boolean select = false;
                switch(position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        showTime();
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        showNetSpeed();
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 3:
                        if(liveChannelGroupList.size() - 1 < 1) { //xuameng 只有一个频道组跨选分类BUG
                            App.showToastShort(mContext, "聚汇影视提示您：只有一个频道组！不能跨选分类！");
                            return;
                        }
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
            case 5: //多源切换   //xuameng新增
                if(position == liveSettingItemAdapter.getSelectedItemIndex()) return;
                //TODO
                if(mVideoView != null) {
                    mVideoView.release();
                    mVideoView = null;
                }
                if(position == Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0)) break;
                JsonArray live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                JsonObject livesOBJ = live_groups.get(position).getAsJsonObject();
                liveSettingItemAdapter.selectItem(position, true, true);
                Hawk.put(HawkConfig.LIVE_GROUP_INDEX, position);
                ApiConfig.get().loadLiveApi(livesOBJ);
                mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);  //xuameng BUG
                mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBack);  //xuameng BUG
                mHandler.removeCallbacks(mConnectTimeoutChangeSourceRunBuffer);  //xuameng BUG
                recreate();
                return;
            case 6: //xuameng渲染方式
                if(position == liveSettingItemAdapter.getSelectedItemIndex()) return;
                if(mVideoView == null) return;
                mVideoView.release();
                livePlayerManager.changeLivePlayerRender(mVideoView, position, currentLiveChannelItem.getChannelName()); //xuameng 设置渲染方式
                mVideoView.setUrl(currentLiveChannelItem.getUrl(), liveWebHeader());
                mVideoView.start();
                liveSettingItemAdapter.selectItem(position, true, true);
                if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                    iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                }
                break;
            case 7: //xuameng音柱动画 点击
                if(position == liveSettingItemAdapter.getSelectedItemIndex()) return;
                if(mVideoView == null) return;
                livePlayerManager.changeLivePlayerMusic(mVideoView, position, currentLiveChannelItem.getChannelName()); 
                if (position == 0){
                    initVisualizer();  //xuameng音乐播放动画
                }else{
                    releaseVisualizer();  //xuameng音乐播放动画
                }
                liveSettingItemAdapter.selectItem(position, true, true);
                break;
            case 8: //xuameng退出直播
                mHideSettingLayoutRun();
                ExitLiveOnSetting();
                break;
        }
        mHideSettingLayoutRunXu();
    }
    private void initLiveChannelList() {
        List < LiveChannelGroup > list = ApiConfig.get().getChannelGroupList();
        if(list.isEmpty()) {
            JsonArray live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
            if(live_groups.size() > 1) {
                setDefaultLiveChannelList();
                App.showToastShort(mContext, "聚汇影视提示您：直播列表为空！请切换线路！");
                return;
            }
            App.showToastShort(mContext, "聚汇影视提示您：频道列表为空！");
            finish();
            return;
        }
        initLiveObj(); //xuameng 直播配置里有没有logo配置
        if(list.size() == 1 && list.get(0).getGroupName().startsWith("http://127.0.0.1")) {
            loadProxyLives(list.get(0).getGroupName());
        } else {
            liveChannelGroupList.clear();
            liveChannelGroupList.addAll(list);
            showSuccess();
            initLiveState();
        }
    }
    public void loadProxyLives(String url) {
        try {
            Uri parsedUrl = Uri.parse(url);
            url = new String(Base64.decode(parsedUrl.getQueryParameter("ext"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
        } catch (Throwable th) {
            if(!url.startsWith("http://127.0.0.1")) {
                JsonArray live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                if(live_groups.size() > 1) {
                    setDefaultLiveChannelList();
                    App.showToastShort(mContext, "聚汇影视提示您：直播文件错误！请切换线路！");
                } else {
                    setDefaultLiveChannelList();
                    App.showToastShort(mContext, "聚汇影视提示您：直播文件错误！");
                }
                return;
            }
        }
        showLoading();
        LOG.i("echo-live-url:" + url);
        OkGo. < String > get(url).tag("xuameng").execute(new AbsCallback < String > () {
            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                assert response.body() != null;
                return response.body().string();
            }
            @Override
            public void onSuccess(Response < String > response) {
                LinkedHashMap < String, LinkedHashMap < String, ArrayList < String >>> linkedHashMap = new LinkedHashMap < > ();
                TxtSubscribe.parse(linkedHashMap, response.body());
                JsonArray livesArray = TxtSubscribe.live2JsonArray(linkedHashMap);
                JsonArray live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                ApiConfig.get().loadLives(livesArray);
                List < LiveChannelGroup > list = ApiConfig.get().getChannelGroupList();
                if(list.isEmpty()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(live_groups.size() > 1) {
                                setDefaultLiveChannelList();
                                App.showToastShort(mContext, "聚汇影视提示您：直播列表为空！请切换线路！");
                            } else {
                                setDefaultLiveChannelList();
                                App.showToastShort(mContext, "聚汇影视提示您：直播列表为空！");
                            }
                        }
                    });
                    return;
                }
                liveChannelGroupList.clear();
                liveChannelGroupList.addAll(list);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        LivePlayActivity.this.showSuccess();
                        initLiveState();
                    }
                });
            }
            @Override
            public void onError(Response < String > response) {
                JsonArray live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(live_groups.size() > 1) {
                            setDefaultLiveChannelList();
                            App.showToastShort(mContext, "聚汇影视提示您：直播列表获取错误！请切换线路！");
                        } else {
                            setDefaultLiveChannelList();
                            App.showToastShort(mContext, "聚汇影视提示您：直播列表获取错误！");
                        }
                    }
                });
            }
        });
    }
    private void initLiveState() {
        int lastChannelGroupIndex = -1; //xuameng记忆上次播放频道组开始
        int lastLiveChannelIndex = -1;
        Intent intent = getIntent();
        if(intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            lastChannelGroupIndex = bundle.getInt("groupIndex", 0);
            lastLiveChannelIndex = bundle.getInt("channelIndex", 0);
        } else {
            Pair < Integer, Integer > lastChannel = JavaUtil.findLiveLastChannel(liveChannelGroupList);
            lastChannelGroupIndex = lastChannel.getFirst();
            lastLiveChannelIndex = lastChannel.getSecond();
        } //xuameng记忆上次播放频道组结束
        livePlayerManager.init(mVideoView);
        showTime();
        showNetSpeed();
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE); //xuameng显示EPG就隐藏左右菜单
        tvRightSettingLayout.setVisibility(View.INVISIBLE); //xuameng显示EPG就隐藏左右菜单
        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex);
    }
    private boolean isListOrSettingLayoutVisible() {
        return tvLeftChannelListLayout.getVisibility() == View.VISIBLE || tvRightSettingLayout.getVisibility() == View.VISIBLE;
    }
    private boolean isLl_epgVisible() { //XUAMENG判断底部EPG是否显示
        return ll_epg.getVisibility() == View.VISIBLE;
    }
    private boolean backcontrollerVisible() { //XUAMENG判断底部回看菜单是否显示
        return backcontroller.getVisibility() == View.VISIBLE;
    }
    boolean isiv_Play_XuVisible() { //xuameng判断暂停动画是否显示
        return iv_Play_Xu.getVisibility() == View.VISIBLE;
    }
    private void initLiveSettingGroupList() { //xuameng
        List < LiveChannelGroup > listxu = ApiConfig.get().getChannelGroupList();
        JsonArray live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        liveSettingGroupList = ApiConfig.get().getLiveSettingGroupList();
        if(!listxu.isEmpty()) {
            if(liveChannelGroupList.size() - 1 < 1) { //xuameng 只有一个频道组跨选分类BUG
                Hawk.put(HawkConfig.LIVE_CROSS_GROUP, false);
            }
            liveSettingGroupList.get(3).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)).setItemSelected(true);
            liveSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
            liveSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
            liveSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
            liveSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
            if(live_groups != null) {
                for(JsonElement element: live_groups) {
                    if(element.isJsonNull()) {
                        App.showToastShort(mContext, "聚汇直播提示您：直播列表读取有错误！请检查JSON中lives的配置！");
                        return;
                    }
                }
            }
            liveSettingGroupList.get(5).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0)).setItemSelected(true); //xuameng新增 换源
        }
    }
    private void loadCurrentSourceList() {
        ArrayList < String > currentSourceNames = currentLiveChannelItem.getChannelSourceNames();
        ArrayList < LiveSettingItem > liveSettingItemList = new ArrayList < > ();
        for(int j = 0; j < currentSourceNames.size(); j++) {
            LiveSettingItem liveSettingItem = new LiveSettingItem();
            liveSettingItem.setItemIndex(j);
            liveSettingItem.setItemName(currentSourceNames.get(j));
            liveSettingItemList.add(liveSettingItem);
        }
        liveSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }
    void showTime() {
        if(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)) {
            mHandler.removeCallbacks(mUpdateTimeRun);
            mHandler.post(mUpdateTimeRun);
            tvTime.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun);
            tvTime.setVisibility(View.GONE);
        }
    }
    void hideTime() { //xuameng右下角系统时间
        if(tvTime.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mUpdateTimeRun);
            tvTime.setVisibility(View.GONE);
        }
    }
    private Runnable mUpdateTimeRun = new Runnable() {
        @Override
        public void run() {
            Date day = new Date();
            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
            tvTime.setText(df.format(day));
            mHandler.postDelayed(this, 1000);
        }
    };
    private Runnable mUpdateTimeRunXu = new Runnable() { //xuameng的系统时间
        @Override
        public void run() {
            Date day = new Date();
            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
            tvTime_xu.setText(df.format(day));
            mHandler.postDelayed(this, 1000);
        }
    };
    void showTimeXu() { //xuameng的系统时间
        mHandler.removeCallbacks(mUpdateTimeRunXu);
        mHandler.post(mUpdateTimeRunXu);
        tvTime.setVisibility(View.GONE);
        tvTime_xu.setVisibility(View.VISIBLE);
    }
    void hideTimeXu() { //xuameng的系统时间
        if(tvTime_xu.getVisibility() == View.VISIBLE) {
           mHandler.removeCallbacks(mUpdateTimeRunXu);
           tvTime_xu.setVisibility(View.GONE);
        }
        if(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)) {
            mHandler.removeCallbacks(mUpdateTimeRun);
            mHandler.post(mUpdateTimeRun);
            tvTime.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun);
            tvTime.setVisibility(View.GONE);
        }
    }
    private void showNetSpeed() {
        if(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)) {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            mHandler.post(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.GONE);
        }
    }
    private void hideNetSpeed() { //xuameng右下角网速
        if(tvNetSpeed.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.GONE);
        }
    }
    private Runnable mUpdateNetSpeedRun = new Runnable() {
        @Override
        public void run() {
            if(mVideoView == null) return;
            String speed = PlayerHelper.getDisplaySpeed(mVideoView.getTcpSpeed());
            tvNetSpeed.setText(speed);
            mHandler.postDelayed(this, 1000);
        }
    };
    private void showNetSpeedXu() {
        mHandler.removeCallbacks(mUpdateNetSpeedRunXu);
        mHandler.post(mUpdateNetSpeedRunXu); //XUAMENG左上网速检测1秒钟一次
        tvNetSpeed.setVisibility(View.GONE);
        tv_right_top_tipnetspeed.setVisibility(View.VISIBLE); //xuameng右上网络速度
    }
    private void hideNetSpeedXu() {
        if(tv_right_top_tipnetspeed.getVisibility() == View.VISIBLE) {
           mHandler.removeCallbacks(mUpdateNetSpeedRunXu);
           tv_right_top_tipnetspeed.setVisibility(View.GONE); //xuameng右上网络速度
        }
        if(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)) {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            mHandler.post(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.GONE);
        }
    }
    private Runnable mUpdateNetSpeedRunXu = new Runnable() {
        @Override
        public void run() {
            if(mVideoView == null) return;
            String speed = PlayerHelper.getDisplaySpeed(mVideoView.getTcpSpeed());
            tv_right_top_tipnetspeed.setText("[" + speed + "]");
            mHandler.postDelayed(this, 1000);
        }
    };
    private Runnable mUpdateVodProgressXu = new Runnable() {
        @Override
        public void run() {
            if(mVideoView == null) return;
            int duration2 = (int) mVideoView.getDuration();
            if(duration2 > 0) {
                if(mVideoView.isPlaying()) { //xuameng音乐播放时图标判断
                    if(iv_Play_Xu.getVisibility() == View.VISIBLE) {
                        iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    }
                    iv_playpause.setText("暂停"); 
                    if(!isKUAIJIN) {   //xuameng快进判断
                        sBar.setProgress((int) mVideoView.getCurrentPosition());
                        int percent = mVideoView.getBufferedPercentage();
                        int totalBuffer = percent * duration2;
                        int SecondaryProgress = totalBuffer / 100;
                        tv_currentpos.setText(durationToString((int) mVideoView.getCurrentPosition()));
                        if(percent >= 98) {
                            sBar.setSecondaryProgress(duration2);
                        } else {
                            sBar.setSecondaryProgress(SecondaryProgress); //xuameng缓冲进度
                        }
                    }
                }
            }
            mHandler.postDelayed(this, 1000);
        }
    };
    private Runnable mUpdateVodImageXu = new Runnable() {
        @Override
        public void run() {
            if(backcontroller.getVisibility() == View.GONE) {
                isSEEKBAR = false;  //xuameng 焦点进入SEEKBAR判断
            }
            if(mVideoView == null) return;
            if(mVideoView.isPlaying()) { //xuameng音乐播放时图标判断
                String width = Integer.toString(mVideoView.getVideoSize()[0]);
                String height = Integer.toString(mVideoView.getVideoSize()[1]);
                //	tv_size.setText("[" + width + " X " + height +"]");
                musicAnimation = livePlayerManager.getLivePlaymusic();
                if (musicAnimation){
                    if(customVisualizer.getVisibility() == View.GONE) { //xuameng播放音乐柱状图
                        customVisualizer.setVisibility(View.VISIBLE);
                    }
                }else{
                    if(customVisualizer.getVisibility() == View.VISIBLE) { //xuameng播放音乐柱状图
                        customVisualizer.setVisibility(View.GONE);
                    }
                }
                if(width.length() > 1 && height.length() > 1) { //XUAMENG分辨率
                    if(iv_circle_bg_xu.getVisibility() == View.VISIBLE) { //xuameng音乐播放时图标
                        iv_circle_bg_xu.setVisibility(View.GONE);
                    }
                    if(MxuamengMusic.getVisibility() == View.VISIBLE) { //xuameng播放音乐背景
                        MxuamengMusic.setVisibility(View.GONE);
                    }
                } else {
                    if(MxuamengMusic.getVisibility() == View.GONE) { //xuameng播放音乐背景
                        MxuamengMusic.setVisibility(View.VISIBLE);
                    }
                    if(isBuffer || isShowlist || HawkConfig.MSLIDEINFO) { //xuameng缓冲时，显示左菜单时，显示亮度音量时
                        if(iv_circle_bg_xu.getVisibility() == View.VISIBLE) { //xuameng音乐播放时图标
                            iv_circle_bg_xu.setVisibility(View.GONE);
                        }
                    } else {
                        if(isVideoplaying) {
                            iv_circle_bg_xu.setVisibility(View.VISIBLE);
                        }
                    }
                }
            } else {
                iv_circle_bg_xu.setVisibility(View.GONE);
            } //xuameng音乐播放时图标判断完  
            mHandler.postDelayed(this, 100);
        }
    };
    private Runnable myRunnableMusic = new Runnable() { //xuameng播放音频切换图片
        @Override
        public void run() {
            if(MxuamengMusic.getVisibility() == View.VISIBLE) {
                if(!ApiConfig.get().musicwallpaper.isEmpty()) {
                    String Url = ApiConfig.get().musicwallpaper;
                    Picasso.get().load(Url)
                        //.placeholder(R.drawable.xumusic)  //xuameng默认的站位图
                        .noPlaceholder() //不使用站位图，效果不好
                        .resize(1920, 1080)
                        //.centerCrop()
                        //.error(R.drawable.xumusic)
                        .config(Bitmap.Config.RGB_565)
                        .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                        .networkPolicy(NetworkPolicy.NO_CACHE).
                        into(MxuamengMusic); // xuameng内容空显示banner
                    mHandler.postDelayed(this, 15000);
                    return;
                } else if(!ApiConfig.get().wallpaper.isEmpty()) {
                    String Url = ApiConfig.get().wallpaper;
                    Picasso.get().load(Url)
                        //.placeholder(R.drawable.xumusic)   //xuameng默认的站位图
                        .noPlaceholder() //不使用站位图，效果不好
                        .resize(1920, 1080)
                        //.centerCrop()
                        //.error(R.drawable.xumusic)
                        .config(Bitmap.Config.RGB_565)
                        .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                        .networkPolicy(NetworkPolicy.NO_CACHE)
                        .into(MxuamengMusic); // xuameng内容空显示banner
                    mHandler.postDelayed(this, 15000);
                    return;
                }
                String Url = "https://api.miaomc.cn/image/get";
                Picasso.get().load(Url)
                    //.placeholder(R.drawable.xumusic)   //xuameng默认的站位图
                    .noPlaceholder() //不使用站位图，效果不好
                    .resize(1920, 1080)
                    //.centerCrop()
                    //.error(R.drawable.xumusic)
                    .config(Bitmap.Config.RGB_565)
                    .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                    .networkPolicy(NetworkPolicy.NO_CACHE)
                    .into(MxuamengMusic); // xuameng内容空显示banner
            }
            mHandler.postDelayed(this, 15000);
        }
    };
    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {
        if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHideChannelListRunXu(); //xuameng隐藏频道菜单
        }
        int groupIndexXu = liveChannelGroupAdapter.getSelectedGroupIndex(); //xuameng频道有密码先清空频道列表防止点击空指针
        liveChannelItemAdapter.setNewData(getLiveChannels(groupIndexXu)); //xuameng频道有密码先清空频道列表防止点击空指针
        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if(password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                    App.showToastShort(mContext, "密码验证通过！已显示密码频道列表！");
                } else {
                    showPasswordDialog(groupIndex, liveChannelIndex); //xuameng 密码错误重新弹出密码输入窗口
                    App.showToastShort(mContext, "密码错误！请重新输入！");
                }
            }
            @Override
            public void onCancel() {
                if(tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    int groupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
                    liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
                }
            }
        });
        dialog.show();
    }
    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
        if(groupIndex == currentChannelGroupIndex) {
            if(currentLiveChannelIndex > -1) mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        } else {
            mLiveChannelView.scrollToPosition(0);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
        }
        if(liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
            mChannelGroupView.scrollToPosition(groupIndex);
            mLiveChannelView.scrollToPosition(liveChannelIndex);
            playChannel(groupIndex, liveChannelIndex, false);
        }
    }
    private boolean isNeedInputPassword(int groupIndex) {
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty() && !isPasswordConfirmed(groupIndex);
    }
    private boolean isPasswordConfirmed(int groupIndex) {
        for(Integer confirmedNum: channelGroupPasswordConfirmed) {
            if(confirmedNum == groupIndex) return true;
        }
        return false;
    }
    private ArrayList < LiveChannelItem > getLiveChannels(int groupIndex) {
        if(!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        } else {
            return new ArrayList < > ();
        }
    }

    private ArrayList < LiveChannelItem > getLiveChannelsXu(int groupIndex) {   //xuameng数字选台时用跳过密码频道验证
        return liveChannelGroupList.get(groupIndex).getLiveChannels();
    }
    private Integer[] getNextChannel(int direction) {
        int channelGroupIndex = currentChannelGroupIndex;
        int liveChannelIndex = currentLiveChannelIndex;
        //跨选分组模式下跳过加密频道分组（遥控器上下键换台/超时换源）
        if(direction > 0) {
            liveChannelIndex++;
            if(liveChannelIndex >= getLiveChannels(channelGroupIndex).size()) {
                liveChannelIndex = 0;
                if(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex++;
                        if(channelGroupIndex >= liveChannelGroupList.size()) channelGroupIndex = 0;
                    } while(!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() && isNeedInputPassword(channelGroupIndex) || channelGroupIndex == currentChannelGroupIndex);   //xuameng isNeedInputPassword(channelGroupIndex)  目的是跨选分类，如果密码频道组密码验证以通过了即使有密码也可以跨选了是的BUG
                }
            }
        } else {
            liveChannelIndex--;
            if(liveChannelIndex < 0) {
                if(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex--;
                        if(channelGroupIndex < 0) channelGroupIndex = liveChannelGroupList.size() - 1;
                    } while(!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() && isNeedInputPassword(channelGroupIndex) || channelGroupIndex == currentChannelGroupIndex);   //xuameng isNeedInputPassword(channelGroupIndex)  目的是跨选分类，如果密码频道组密码验证以通过了即使有密码也可以跨选了是的BUG
                }
                liveChannelIndex = getLiveChannels(channelGroupIndex).size() - 1;
            }
        }
        Integer[] groupChannelIndex = new Integer[2];
        groupChannelIndex[0] = channelGroupIndex;
        groupChannelIndex[1] = liveChannelIndex;
        return groupChannelIndex;
    }
    private int getFirstNoPasswordChannelGroup() {
        for(LiveChannelGroup liveChannelGroup: liveChannelGroupList) {
            if(liveChannelGroup.getGroupPassword().isEmpty()) return liveChannelGroup.getGroupIndex();
        }
        return -1;
    }
    private boolean isCurrentLiveChannelValid() {
        if(currentLiveChannelItem == null) {
           App.showToastShort(mContext, "聚汇影视提示您：请先选择频道！");
           return false;
        }
        return true;
    }
    private boolean isCurrentLiveChannelValidXu() {
        if(currentLiveChannelItem == null) {
            return false;
        }
        return true;
    }
    //计算两个时间相差的秒数
    public static long getTime(String startTime, String endTime) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long eTime = 0;
        try {
            eTime = df.parse(endTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long sTime = 0;
        try {
            sTime = df.parse(startTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long diff = (eTime - sTime) / 1000;
        return diff;
    }
    private String durationToString(int duration) {   //xuameng时间转换
        String result = "";
        int dur = duration / 1000;
        int hour = dur / 3600;
        int min = (dur / 60) % 60;
        int sec = dur % 60;
        if(hour > 0) {
            if(min > 9) {
                if(sec > 9) {
                    result = hour + ":" + min + ":" + sec;
                } else {
                    result = hour + ":" + min + ":0" + sec;
                }
            } else {
                if(sec > 9) {
                    result = hour + ":" + "0" + min + ":" + sec;
                } else {
                    result = hour + ":" + "0" + min + ":0" + sec;
                }
            }
        } else {
            if(min > 9) {
                if(sec > 9) {
                    result = min + ":" + sec;
                } else {
                    result = min + ":0" + sec;
                }
            } else {
                if(sec > 9) {
                    result = "0" + min + ":" + sec;
                } else {
                    result = "0" + min + ":0" + sec;
                }
            }
        }
        return result;
    }
    public void showProgressBars(boolean show) { //显示回看菜单
        //        sBar.requestFocus();                            //xuameng回看菜单默认焦点为播放
        if(show) {
            ll_right_top_loading.setVisibility(View.GONE); //xuameng右上菜单隐藏
            view_line_XU.setVisibility(View.INVISIBLE); //xuamengEPG中的横线
            mHideChannelListRun(); //xuameng显示EPG就隐藏左右菜单
            mHideSettingLayoutRun(); //xuameng显示EPG就隐藏左右菜单
            backcontroller.setVisibility(View.VISIBLE); //xuameng显示回看下方菜单
            showTimeXu(); //xuameng系统显示时间
            showNetSpeedXu(); //XUAMENG显示右下网速
        //    iv_playpause.requestFocus();     //xuameng默认焦点
            Mtv_left_top_xu.setVisibility(View.VISIBLE); //xuameng显示回看上图标
            ll_epg.setVisibility(View.VISIBLE); //xuameng下面EPG菜单显示
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                   if(!iv_playpause.hasFocus()){
                      iv_playpause.requestFocus();
                   }
                }
            }, 200);
        } else {
            backcontroller.setVisibility(View.GONE);   //xuameng显示回看下方菜单
            Mtv_left_top_xu.setVisibility(View.GONE);  //xuameng显示回看上图标
            iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
            mHideChannelListRun(); //xuameng显示EPG就隐藏左右菜单
            mHideSettingLayoutRun(); //xuameng显示EPG就隐藏左右菜单
            if(!tip_epg1.getText().equals("暂无当前节目单，聚汇直播欢迎您的观看！")) {
                ll_epg.setVisibility(View.VISIBLE); //xuameng下面EPG菜单显示
                ll_right_top_loading.setVisibility(View.VISIBLE); //xuameng右上菜单显示
                view_line_XU.setVisibility(View.VISIBLE); //xuamengEPG中的横线
                showTimeXu(); //xuameng系统显示时间
                showNetSpeedXu(); //XUAMENG显示右下网速
            }
        }
        iv_playpause.setOnClickListener(new View.OnClickListener() { //xuameng回看播放按钮监听
            @Override
            public void onClick(View arg0) {
                if(mVideoView == null) return;
                HideBottomEpgTimer();  //隐藏底部菜单到计时
                if(mVideoView.isPlaying()) {
                    mVideoView.pause();
                    iv_Play_Xu.setVisibility(View.VISIBLE); //回看暂停图标
                    iv_playpause.setText("播放"); 
                } else {
                    HideBottomEpg();  //隐藏底部菜单
                    mVideoView.start();
                    iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                    iv_playpause.setText("暂停");
                }
            }
        });
        sBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { //xuameng升级手机进程条
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(mVideoView == null) return;
                long duration = mVideoView.getDuration();
                if(duration <= 0) return;
                long newPosition = (duration * seekBar.getProgress()) / sBar.getMax(); //xuameng停止触碰获取进度条进度
                mVideoView.seekTo((int) newPosition); //xuameng当前进度播放
                isKUAIJIN = false;  //xuameng快进判断
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(mVideoView == null) return;
                long duration = mVideoView.getDuration();
                if(duration <= 0) return;
                isKUAIJIN = true;  //xuameng快进判断
            }
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromuser) {
                if(mVideoView == null) return;
                if(!fromuser) {
                    return;
                }
                if(fromuser) {
                    long duration = mVideoView.getDuration();
                    if(duration <= 0) return;
                    long newPosition = (duration * progress) / sBar.getMax(); //xuameng触碰进度变化获取
                    if(tv_currentpos != null) {
                        tv_currentpos.setText(durationToString((int) newPosition)); //xuameng文字显示进度
                    }
                    HideBottomEpgTimer();  //隐藏底部菜单到计时
                }
            }
        });
        sBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View arg0, int keycode, KeyEvent event) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) {
                    int keyCode = event.getKeyCode();
                    int action = event.getAction();
                    if(keycode == KeyEvent.KEYCODE_DPAD_CENTER || keycode == KeyEvent.KEYCODE_ENTER) {
                        if(mVideoView == null) return true;
                        HideBottomEpgTimer();  //隐藏底部菜单到计时
                        if(mVideoView.isPlaying()) {
                            mVideoView.pause();
                            iv_Play_Xu.setVisibility(View.VISIBLE); //回看暂停图标
                            iv_playpause.setText("播放"); 
                        } else {
                            HideBottomEpg();  //隐藏底部菜单
                            mVideoView.start();
                            iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
                            iv_playpause.setText("暂停"); 
                        }
                    }
                    if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if(mVideoView == null) return true;
                        HideBottomEpgTimer();  //隐藏底部菜单到计时
                        tvSlideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                        return true;
                    }
                }
                if(event.getAction() == KeyEvent.ACTION_UP) {
                    int keyCode = event.getKeyCode();
                    int action = event.getAction();
                    if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        tvSlideStop(); //xuameng修复SEEKBAR快进重新播放问题
                        return true;
                    }
                }
                return false;
            }
        });
        HideBottomEpgTimer();  //隐藏底部菜单到计时
        if(mVideoView == null) return;
        if(mVideoView.isPlaying()) {
            iv_Play_Xu.setVisibility(View.GONE); //回看暂停图标
        }
    }
    private boolean simSlideStart = false;
    private int simSeekPosition = 0;   //XUAMENG调整播放进度
    private long simSlideOffset = 0;   //xuameng快进步长
    public void tvSlideStop() {
        if(!simSlideStart || mVideoView == null) return;
        if(isSEEKBAR) {  //xuameng 焦点进入SEEKBAR判断
            mVideoView.seekTo(simSeekPosition);
        }
        //        if(!mVideoView.isPlaying())
        //xuameng快进暂停就暂停测试    mVideoView.start();  如果想暂停时快进自动播放取消注销
        //        simSeekPosition = 0;    //XUAMENG重要重置进度
        isKUAIJIN = false;  //xuameng快进判断
        simSlideStart = false;
        simSlideOffset = 0; //快进步数
        mSpeedTimeUp = 0; //xuameng按住按键开始计时
    }

    public void tvSlideStart(int dir) {
        int duration = (int) mVideoView.getDuration();
        if(duration <= 0) return;
        isSEEKBAR = true;   //xuameng 焦点进入SEEKBAR判断
        isKUAIJIN = true;
        if(!simSlideStart) {
            simSlideStart = true;
        }
        // 每次10秒
        if(mSpeedTimeUp == 0) {
            mSpeedTimeUp = System.currentTimeMillis();
        }
        if(System.currentTimeMillis() - mSpeedTimeUp < 3000) { //xuameng快进越来越快
            simSlideOffset += (10000.0f * dir);
        }
        if(System.currentTimeMillis() - mSpeedTimeUp > 3000 && System.currentTimeMillis() - mSpeedTimeUp < 6000) {
            simSlideOffset += (30000.0f * dir);
        }
        if(System.currentTimeMillis() - mSpeedTimeUp > 6000 && System.currentTimeMillis() - mSpeedTimeUp < 9000) {
            simSlideOffset += (60000.0f * dir);
        }
        if(System.currentTimeMillis() - mSpeedTimeUp > 9000) {
            simSlideOffset += (120000.0f * dir);
        }
        int currentPosition = (int) mVideoView.getCurrentPosition();
        int position = (int)(simSlideOffset + currentPosition);
        if(position > duration) position = duration;
        if(position < 0) position = 0;
        simSeekPosition = position;
        sBar.setProgress(simSeekPosition);
        tv_currentpos.setText(durationToString(simSeekPosition));
    }

    private void setDefaultLiveChannelList() {   //xuameng 加载失败默认频道列表
        liveChannelGroupList.clear();
        // 创建默认直播分组
        LiveChannelGroup defaultGroup = new LiveChannelGroup();
        defaultGroup.setGroupIndex(0);
        defaultGroup.setGroupName("聚汇直播");
        defaultGroup.setGroupPassword("");
        LiveChannelItem defaultChannel = new LiveChannelItem();
        defaultChannel.setChannelName("暂无频道");
        defaultChannel.setChannelIndex(0);
        defaultChannel.setChannelNum(1);
        ArrayList<String> defaultSourceNames = new ArrayList<>();
        ArrayList<String> defaultSourceUrls = new ArrayList<>();
        defaultSourceNames.add("默认源1");
        defaultSourceUrls.add("http://default.play.url/stream");
        defaultChannel.setChannelSourceNames(defaultSourceNames);
        defaultChannel.setChannelUrls(defaultSourceUrls);
        // 将默认频道添加到分组内
        ArrayList<LiveChannelItem> channels = new ArrayList<>();
        channels.add(defaultChannel);
        defaultGroup.setLiveChannels(channels);
        // 添加分组到全局列表
        liveChannelGroupList.add(defaultGroup);
        showSuccess();
        initLiveState();
    }
    private void initLiveObj(){
        int position=Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
        JsonArray live_groups=Hawk.get(HawkConfig.LIVE_GROUP_LIST,new JsonArray());
        JsonObject livesOBJ = live_groups.get(position).getAsJsonObject();
        if(livesOBJ.has("logo")){
            logoUrl = livesOBJ.get("logo").getAsString();    //xuameng 直播配置里有没有logo配置
        }
    }

    public void HideBottomEpg() {  //隐藏底部菜单
       ll_epg.setVisibility(View.GONE); //xuameng下面EPG菜单隐藏
       ll_right_top_loading.setVisibility(View.GONE); //xuameng直播左上图标隐藏
       backcontroller.setVisibility(View.GONE);  //xuameng 隐藏进度条
       hideTimeXu(); //xuameng隐藏系统时间
       hideNetSpeedXu(); //XUAMENG隐藏右下网速
       view_line_XU.setVisibility(View.INVISIBLE); //xuamengEPG中的横线
       if(countDownTimer != null) {
          countDownTimer.cancel();
       }
    }

    public void HideBottomEpgTimer() {    //隐藏底部菜单到计时
        if(countDownTimer != null) {
           countDownTimer.cancel();
        }
        countDownTimer = new CountDownTimer(10000, 1000) { //底部epg隐藏时间设定
            public void onTick(long j) {}
            public void onFinish() {
                HideBottomEpg(); 
            }
        };
        countDownTimer.start();
    }

    private void cancelxToast() {   //xuameng清除toast
        if (xToast != null) {
            xToast.cancel();
            xToast = null;
        }
    }

    private void initVisualizer() {   //xuameng播放音乐柱状图
        releaseVisualizer();  // 确保先释放已有实例
        // 基础检查
        if (this == null || isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity context unavailable");
            return;
        }

        int sessionId = mVideoView != null ? mVideoView.getAudioSessionId() : 0;
        if (sessionId <= 0) {
            Log.w(TAG, "Invalid audio session ID");
            return;
        }
        try {
        // 统一创建Visualizer实例（仅一次）
            mVisualizer = new Visualizer(sessionId);
            // 智能采样率设置
            int targetRate = Visualizer.getMaxCaptureRate() / 2;
            // 设置数据捕获监听器
            mVisualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    @Override
                    public void onWaveFormDataCapture(Visualizer viz, byte[] bytes, int rate) {
                    // 可选波形数据捕获
                    }
                    @Override
                    public void onFftDataCapture(Visualizer visualizer, byte[] fftData, int samplingRate) {
                        if (fftData == null || customVisualizer == null) return;
                         // 1. 计算当前音量级别（0-1范围）
                        float volumeLevel = calculateVolumeLevel(LivePlayActivity.this);
                        Runnable updateTask = () -> {
                            try {
                                if (customVisualizer != null) {
                                    customVisualizer.updateVisualizer(fftData, volumeLevel);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Visualizer update error", e);
                            }
                        };
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            updateTask.run();
                        } else {
                            new Handler(Looper.getMainLooper()).post(updateTask);
                        }
                    }
                },
                targetRate,
                false,  // 不捕获波形数据
                true    // 捕获FFT数据
            );
            mVisualizer.setEnabled(true);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Visualizer state error", e);
            releaseVisualizer();
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "Device doesn't support Visualizer", e);
            releaseVisualizer();
        } catch (Exception e) {
            Log.e(TAG, "Visualizer init failed", e);
            releaseVisualizer();
        }
    }

    private synchronized void releaseVisualizer() {   //xuameng播放音乐柱状图
        try {
            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
                Log.d(TAG, "Visualizer released successfully");
            }
            if (customVisualizer != null) {
                customVisualizer.release();
            }
            if(customVisualizer.getVisibility() == View.VISIBLE) { //xuameng播放音乐柱状图
                customVisualizer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing visualizer", e);
        }
    }

    public static float calculateVolumeLevel(Context context) {  //系统音量监控
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        
        // 计算0-1范围的百分比
        float volumePercent = (float) currentVolume / maxVolume;
        
        // 保留两位小数
        return (float) Math.round(volumePercent * 100) / 100.0f;
    }
}
