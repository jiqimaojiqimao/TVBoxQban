package com.github.tvbox.osc.player.controller;
import android.animation.Animator;                      //xuameng动画
import android.animation.AnimatorListenerAdapter;       //xuameng动画
import android.animation.ObjectAnimator;                //xuameng动画
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;                          //xuameng获取颜色值
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FastClickCheckUtilxu; //xuameng防连击1秒
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.ScreenUtils;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;
import static xyz.doikki.videoplayer.util.PlayerUtils.stringForTime;
import com.squareup.picasso.Picasso;      //xuameng播放音频切换图片
import com.squareup.picasso.MemoryPolicy;  //xuameng播放音频切换图片
import com.squareup.picasso.NetworkPolicy;  //xuameng播放音频切换图片
import com.github.tvbox.osc.api.ApiConfig;  //xuameng播放音频切换图片

public class VodController extends BaseController {
    public VodController(@NonNull @NotNull Context context) {
        super(context);
        mHandlerCallback = new HandlerCallback() {
            @Override
            public void callback(Message msg) {
                switch (msg.what) {
                    case 1000: { // seek 刷新
                        mProgressRoot.setVisibility(VISIBLE);
						if (iv_circle_bg.getVisibility() == View.VISIBLE){   //xuameng音乐播放时图标
						iv_circle_bg.setVisibility(GONE);
						}
                        break;
                    }
                    case 1001: { // seek 关闭
                        mProgressRoot.setVisibility(GONE);
                        break;
                    }

                    case 1005: { // 隐藏底部菜单Xu
		                ObjectAnimator animator3 = ObjectAnimator.ofFloat(mBottomRoot, "translationY", -0,700);				//xuameng向下划出屏外
                        animator3.setDuration(300);				   //xuameng动画菜单        
                        animator3.addListener(new AnimatorListenerAdapter() {
                        @Override
						public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
						isAnimation = true;
			            }
                        public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mBottomRoot.setVisibility(GONE);			   //动画结束后隐藏下菜单
						mTopRoot1.setVisibility(GONE);				   //动画结束后隐藏上菜单
						mTopRoot2.setVisibility(GONE);                 //动画结束后隐藏上菜单
						MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
						isAnimation = false;
                        }
                        });
                        animator3.start();                          //XUAMENG隐藏底部菜单结束                        
				        ObjectAnimator animator4 = ObjectAnimator.ofFloat(mTopRoot1, "translationY", 0,-700);				//xuameng向上划出屏外
                        animator4.setDuration(300);				//xuameng动画菜单				
		                animator4.start();                          //XUAMENG隐藏上面菜单1结束
						ObjectAnimator animator5 = ObjectAnimator.ofFloat(mTopRoot2, "translationY", 0,-700);				//xuameng向上划出屏外
                        animator5.setDuration(300);			
		                animator5.start();                          //XUAMENG隐藏上面菜单2结束
                        backBtn.setVisibility(INVISIBLE);           //返回键隐藏菜单						
                        break;
                    }

                    case 1002: { // 显示底部菜单
                        mBottomRoot.setVisibility(VISIBLE);
						ObjectAnimator animator = ObjectAnimator.ofFloat(mBottomRoot, "translationY", 700,0);				//xuameng动画菜单
                        animator.setDuration(300);				//xuameng动画菜单
						animator.addListener(new AnimatorListenerAdapter() {
                        @Override
			            public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        MxuamengView.setVisibility(VISIBLE);	//xuameng动画开始防点击
						isDisplay = true;
			            }
                        public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
			            MxuamengView.setVisibility(GONE);		//xuameng动画结束可点击
						isDisplay = false;
                        }
                        });
                        animator.start();						//xuameng动画菜单
                        mTopRoot1.setVisibility(VISIBLE);
						ObjectAnimator animator1 = ObjectAnimator.ofFloat(mTopRoot1, "translationY", -700,0);				//xuameng动画菜单
                        animator1.setDuration(300);			//xuameng动画菜单
                        animator1.start();						//xuameng动画菜单
                        mTopRoot2.setVisibility(VISIBLE);
						ObjectAnimator animator2 = ObjectAnimator.ofFloat(mTopRoot2, "translationY", -700,0);				//xuameng动画菜单
                        animator2.setDuration(300);			//xuameng动画菜单
                        animator2.start();						//xuameng动画菜单
                        mxuPlay.requestFocus();				    //底部菜单默认焦点为播放
                        backBtn.setVisibility(ScreenUtils.isTv(context) ? INVISIBLE : VISIBLE);
                        showLockView();
						mPlayPauseTimexu.setVisibility(GONE);   //xuameng隐藏上面视频名称
                        mPlayTitle.setVisibility(GONE);         //xuameng隐藏上面时间
                        break;
                    }
                    case 1003: { // 隐藏底部菜单
		                ObjectAnimator animator3 = ObjectAnimator.ofFloat(mBottomRoot, "translationY", -0,700);				//xuameng向下划出屏外
                        animator3.setDuration(300);				   //xuameng动画菜单        
                        animator3.addListener(new AnimatorListenerAdapter() {
                        @Override
						public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
						isAnimation = true;
			            }
                        public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mBottomRoot.setVisibility(GONE);			   //动画结束后隐藏下菜单
						mTopRoot1.setVisibility(GONE);				   //动画结束后隐藏上菜单
						mTopRoot2.setVisibility(GONE);                 //动画结束后隐藏上菜单
						MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
						isAnimation = false;
                        }
                        });
                        animator3.start();                          //XUAMENG隐藏底部菜单结束                        
				        ObjectAnimator animator4 = ObjectAnimator.ofFloat(mTopRoot1, "translationY", 0,-700);				//xuameng向上划出屏外
                        animator4.setDuration(300);				//xuameng动画菜单				
		                animator4.start();                          //XUAMENG隐藏上面菜单1结束
						ObjectAnimator animator5 = ObjectAnimator.ofFloat(mTopRoot2, "translationY", 0,-700);				//xuameng向上划出屏外
                        animator5.setDuration(300);			
		                animator5.start();                          //XUAMENG隐藏上面菜单2结束
                        backBtn.setVisibility(INVISIBLE);           //返回键隐藏菜单
						if (mControlWrapper.isPlaying()){    //xuameng音乐播放时图标判断
						}else{
						mPlayTitle.setVisibility(VISIBLE);          //xuameng显示上面节目名称
				        ObjectAnimator animator6 = ObjectAnimator.ofFloat(mPlayTitle, "translationY", -700,0);				//xuameng动画菜单
                        animator6.setDuration(300);				//xuameng动画菜单
						animator6.start();						    //XUAMENG显示上面菜单结束
						mPlayPauseTimexu.setVisibility(VISIBLE);          //xuameng显示上面时间
				        ObjectAnimator animator7 = ObjectAnimator.ofFloat(mPlayPauseTimexu, "translationY", -700,0);	    //xuameng动画菜单
                        animator7.setDuration(300);				//xuameng动画菜单
						animator7.start();						    //XUAMENG显示上面菜单的时间结束
						}
                        break;
                    }
                    case 1004: { // 设置速度
                        if (isInPlaybackState()) {
                            try {
                                float speed = (float) mPlayerConfig.getDouble("sp");
                                mControlWrapper.setSpeed(speed);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } else
                            mHandler.sendEmptyMessageDelayed(1004, 100);
                        break;
                    }
                }
            }
        };
    }

    SeekBar mSeekBar;
    TextView mCurrentTime;
    TextView mTotalTime;
    boolean mIsDragging;
    LinearLayout mProgressRoot;
    TextView mProgressText;
    ImageView mProgressIcon;
    ImageView mLockView;
    LinearLayout mBottomRoot;
    LinearLayout mTopRoot1;
    LinearLayout mTopRoot2;
    LinearLayout mParseRoot;
	LinearLayout MxuamengView;			      //xuameng防点击
    LinearLayout mTvPausexu;				  //xuameng暂停动画
    TvRecyclerView mGridView;
    TextView mPlayTitle;
    TextView mPlayTitle1;
    TextView mPlayLoadNetSpeedRightTop;
    TextView mNextBtn;
    TextView mPreBtn;
    TextView mPlayerScaleBtn;
    public TextView mPlayerSpeedBtn;
    TextView mPlayerBtn;
    TextView mPlayerIJKBtn;
    TextView mPlayerRetry;
    TextView mPlayrefresh;
	TextView mxuPlay;                         //xuameng 底部播放ID
	private ImageView iv_circle_bg;  //xuameng音乐播放时图标
	private TextView tv_slide_progress_text;
	ImageView MxuamengMusic;       //xuameng播放音乐背景
    public TextView mPlayerTimeStartEndText;
    public TextView mPlayerTimeStartBtn;
    public TextView mPlayerTimeSkipBtn;
    public TextView mPlayerTimeResetBtn;
    TextView mPlayPauseTime;
	TextView mPlayPauseTimexu;                //xuameng系统时间
    TextView mPlayLoadNetSpeed;
    TextView mVideoSize;
    public SimpleSubtitleView mSubtitleView;
    TextView mZimuBtn;
    TextView mAudioTrackBtn;
    public TextView mLandscapePortraitBtn;
    private View backBtn;//返回键
    private boolean isClickBackBtn;
	private double DOUBLE_CLICK_TIME = 0L;    //xuameng返回键防连击1.5秒（为动画）
	private double DOUBLE_CLICK_TIME_2 = 0L;    //xuameng防连击1秒（为动画）
   
    LockRunnable lockRunnable = new LockRunnable();
    private boolean isLock = false;
	private boolean isSEEKBAR = false;       //xuameng进入SEEKBAR
	private boolean isPlaying = false;  //xuameng判断暂停动画
	private boolean isAnimation = false;  //xuameng判断隐藏菜单动画
	private boolean isDisplay = false;  //xuameng判断显示菜单动画
    Handler myHandle;
    Runnable myRunnable;
    int myHandleSeconds = 50000;            //闲置多少毫秒秒关闭底栏  默认100秒

    int videoPlayState = 0;

    private Runnable myRunnable2 = new Runnable() {
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            mPlayPauseTime.setText(timeFormat.format(date));
            String speed = PlayerHelper.getDisplaySpeed(mControlWrapper.getTcpSpeed());
            mPlayLoadNetSpeedRightTop.setText(speed);
            mPlayLoadNetSpeed.setText(speed);
            String width = Integer.toString(mControlWrapper.getVideoSize()[0]);
            String height = Integer.toString(mControlWrapper.getVideoSize()[1]);
            mVideoSize.setText("[ " + width + " X " + height +" ]");
            
			if (mControlWrapper.isPlaying()){    //xuameng音乐播放时图标判断
				if (!mIsDragging) {
					mControlWrapper.startProgress();    //xuameng启动进程
					mControlWrapper.startFadeOut();
				}

				mxuPlay.setText("暂停");
				if (!isPlaying && mTvPausexu.getVisibility() == View.VISIBLE){						
					ObjectAnimator animator10 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", -0,700);				//xuameng动画暂停菜单开始
					animator10.setDuration(300);			//xuameng动画暂停菜单
					animator10.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationStart(Animator animation) {
					super.onAnimationStart(animation);
					MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
					isPlaying = true;  //xuameng动画开启
					}
					public void onAnimationEnd(Animator animation) {
					super.onAnimationEnd(animation);
					MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
					mTvPausexu.setVisibility(GONE);                //xuameng动画暂停菜单隐藏 
					isPlaying = false;  //xuameng动画开启
					}
					});
					animator10.start();						      //xuameng动画暂停菜单结束					
				}

				if (mPlayPauseTimexu.getVisibility() == View.VISIBLE||mPlayTitle.getVisibility() == View.VISIBLE){
					mPlayPauseTimexu.setVisibility(GONE);   //xuameng隐藏上面视频名称
                    mPlayTitle.setVisibility(GONE);         //xuameng隐藏上面时间
				}	

				if (width.length() > 1 && height.length() > 1){
					if (iv_circle_bg.getVisibility() == View.VISIBLE){  //xuameng音乐播放时图标
					iv_circle_bg.setVisibility(GONE);
					}
					if (MxuamengMusic.getVisibility() == View.VISIBLE){  //xuameng播放音乐背景
					MxuamengMusic.setVisibility(GONE);
					}
				}else{
					if (MxuamengMusic.getVisibility() == View.GONE){  //xuameng播放音乐背景
					MxuamengMusic.setVisibility(VISIBLE);
					}
					if (mProgressRoot.getVisibility() == View.VISIBLE || mPlayLoadNetSpeed.getVisibility() == View.VISIBLE || tv_slide_progress_text.getVisibility() == View.VISIBLE){
						if (iv_circle_bg.getVisibility() == View.VISIBLE){  //xuameng音乐播放时图标
						iv_circle_bg.setVisibility(GONE);
						}
					}else {
						iv_circle_bg.setVisibility(VISIBLE);
						}
					}
			}else {
				iv_circle_bg.setVisibility(GONE);
			}   //xuameng音乐播放时图标判断完
				

            mHandler.postDelayed(this, 1000);
        }
    };

	private Runnable myRunnableMusic = new Runnable() {  //xuameng播放音频切换图片
        @Override
        public void run() {
			if (MxuamengMusic.getVisibility() == View.VISIBLE){
				if (!ApiConfig.get().musicwallpaper.isEmpty()){
				String Url = ApiConfig.get().musicwallpaper;
				Picasso.get()
				.load(Url)
//				.placeholder(R.drawable.xumusic)   //xuameng默认的站位图
				.noPlaceholder()   //不使用站位图，效果不好
				.resize(3840,2160)
				.centerCrop()
//				.error(R.drawable.xumusic)
				.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
				.networkPolicy(NetworkPolicy.NO_CACHE)
				.into(MxuamengMusic); // xuameng内容空显示banner
				}
			}
        mHandler.postDelayed(this, 15000);
       }
    };

   private Runnable xuRunnable = new Runnable() {                     //xuameng显示系统时间
        @Override
        public void run() {
            Date date1 = new Date();
            SimpleDateFormat timeFormat1 = new SimpleDateFormat("HH:mm:ss");
            mPlayPauseTimexu.setText(timeFormat1.format(date1));
			mHandler.postDelayed(this, 1000);
        }
    };																  //xuameng显示系统时间


    
    private void showLockView() {
        mLockView.setVisibility(ScreenUtils.isTv(getContext()) ? INVISIBLE : VISIBLE);
        mHandler.removeCallbacks(lockRunnable);
        mHandler.postDelayed(lockRunnable, 3000);
    }

    @Override
    protected void initView() {
        super.initView();
        mCurrentTime = findViewById(R.id.curr_time);
        mTotalTime = findViewById(R.id.total_time);
        mPlayTitle = findViewById(R.id.tv_info_name);
        mPlayTitle1 = findViewById(R.id.tv_info_name1);
        mPlayLoadNetSpeedRightTop = findViewById(R.id.tv_play_load_net_speed_right_top);
        mSeekBar = findViewById(R.id.seekBar);
        mProgressRoot = findViewById(R.id.tv_progress_container);
        mProgressIcon = findViewById(R.id.tv_progress_icon);
        mProgressText = findViewById(R.id.tv_progress_text);
        mBottomRoot = findViewById(R.id.bottom_container);
        mTopRoot1 = findViewById(R.id.tv_top_l_container);
        mTopRoot2 = findViewById(R.id.tv_top_r_container);
        mParseRoot = findViewById(R.id.parse_root);
        mGridView = findViewById(R.id.mGridView);
        mPlayerRetry = findViewById(R.id.play_retry);
        mPlayrefresh = findViewById(R.id.play_refresh);
        mNextBtn = findViewById(R.id.play_next);
        mPreBtn = findViewById(R.id.play_pre);
        mPlayerScaleBtn = findViewById(R.id.play_scale);
        mPlayerSpeedBtn = findViewById(R.id.play_speed);
        mPlayerBtn = findViewById(R.id.play_player);
        mPlayerIJKBtn = findViewById(R.id.play_ijk);
        mPlayerTimeStartEndText = findViewById(R.id.play_time_start_end_text);
        mPlayerTimeStartBtn = findViewById(R.id.play_time_start);
        mPlayerTimeSkipBtn = findViewById(R.id.play_time_end);
        mPlayerTimeResetBtn = findViewById(R.id.play_time_reset);
        mPlayPauseTime = findViewById(R.id.tv_sys_time);
        mPlayPauseTimexu = findViewById(R.id.tv_sys_time_xu);          //XUAMENG的系统时间
		MxuamengView = findViewById(R.id.xuamengView);				   //XUAMENG防点击
		mTvPausexu = findViewById(R.id.tv_pause_xu);				   //XUAMENG暂停动画
		iv_circle_bg = (ImageView) findViewById(R.id.iv_circle_bg);  //xuameng音乐播放时图标
		MxuamengMusic = (ImageView) findViewById(R.id.xuamengMusic);  //xuameng播放音乐背景
		tv_slide_progress_text = findViewById(R.id.tv_slide_progress_text);
        mPlayLoadNetSpeed = findViewById(R.id.tv_play_load_net_speed);
        mVideoSize = findViewById(R.id.tv_videosize);
        mSubtitleView = findViewById(R.id.subtitle_view);
        mZimuBtn = findViewById(R.id.zimu_select);
        mAudioTrackBtn = findViewById(R.id.audio_track_select);
        mLandscapePortraitBtn = findViewById(R.id.landscape_portrait);
        backBtn = findViewById(R.id.tv_back);
		mxuPlay = findViewById(R.id.mxuplay);		                  //xuameng  低菜单播放

		//xuameng音乐播放时图标
        ObjectAnimator animator20 = ObjectAnimator.ofFloat(iv_circle_bg, "rotation", 360.0f);
        animator20.setDuration(10000);
        animator20.setRepeatCount(-1);
        animator20.start();
		
        backBtn.setOnClickListener(new OnClickListener() {            //xuameng  屏幕上的返回键
            @Override
            public void onClick(View view) {
                if (getContext() instanceof Activity) {
					if (isDisplay || isAnimation || isPlaying){
						return;
					}else{
                    isClickBackBtn = true;
                    ((Activity) getContext()).onBackPressed();
					}
                }
            }
        });
        mLockView = findViewById(R.id.tv_lock);
        mLockView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
				if (isDisplay || isAnimation || isPlaying){
					return;
				}
                isLock = !isLock;
                mLockView.setImageResource(isLock ? R.drawable.icon_lock : R.drawable.icon_unlock);
                if (isLock) {
					if (mBottomRoot.getVisibility() == View.VISIBLE){
						Message obtain = Message.obtain();
						obtain.what = 1003;//隐藏底部菜单
						mHandler.sendMessage(obtain);
					}
                }
                showLockView();
            }
        });
        View rootView = findViewById(R.id.rootView);
        rootView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLock) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        showLockView();
                    }
                }
                return isLock;
            }
        });

        initSubtitleInfo();

        myHandle = new Handler();
        myRunnable = new Runnable() {
            @Override
            public void run() {
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
                hideBottom();
				}
            }
        };

        mPlayPauseTime.post(new Runnable() {
            @Override
            public void run() {
                mHandler.post(myRunnable2);
				mHandler.post(myRunnableMusic);  //xuameng播放音频切换图片
            }
        });

		mPlayPauseTimexu.post(new Runnable() {            //xuameng显示系统时间
            @Override
            public void run() {
                mHandler.post(xuRunnable);
            }
        });


        mGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 0, false));
        ParseAdapter parseAdapter = new ParseAdapter();
        parseAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                ParseBean parseBean = parseAdapter.getItem(position);
                // 当前默认解析需要刷新
                int currentDefault = parseAdapter.getData().indexOf(ApiConfig.get().getDefaultParse());
                parseAdapter.notifyItemChanged(currentDefault);
                ApiConfig.get().setDefaultParse(parseBean);
                parseAdapter.notifyItemChanged(position);
                listener.changeParse(parseBean);
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
                hideBottom();
				}
            }
        });
        mGridView.setAdapter(parseAdapter);
        parseAdapter.setNewData(ApiConfig.get().getParseBeanList());

        mParseRoot.setVisibility(VISIBLE);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }

                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrentTime != null)
                    mCurrentTime.setText(stringForTime((int) newPosition));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsDragging = true;
                mControlWrapper.stopProgress();
                mControlWrapper.stopFadeOut();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                mControlWrapper.seekTo((int) newPosition);
                mIsDragging = false;
                mControlWrapper.startProgress();
                mControlWrapper.startFadeOut();
            }
        });
        //xuameng监听底部进度条遥控器
		mSeekBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View arg0, int keycode, KeyEvent event) {
                if(event.getAction()==KeyEvent.ACTION_DOWN){
			    int keyCode = event.getKeyCode();
                int action = event.getAction();
				boolean isInPlayback = isInPlaybackState();
                    if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
		              if (isInPlayback) {
                      tvSlideStartXu(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                return true;
                    }
                  }
				if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
					if ((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 350){                  //xuameng 防播放打断动画
						return true;
					}
					DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
					if (isInPlayback) {
						if (!isDisplay || !isAnimation){
							if (mControlWrapper.isPlaying()){
							togglePlay();
							pauseIngXu();
							return true;
							}
							if (!mControlWrapper.isPlaying()){
							togglePlay();
							playIngXu();
							return true;
							}
						}
					return true;
                   }
                 }
		    	}
                if(event.getAction()==KeyEvent.ACTION_UP){
                int keyCode = event.getKeyCode();
                int action = event.getAction();
                boolean isInPlayback = isInPlaybackState();
		            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                       if (isInPlayback) {
                       tvSlideStopXu();			//xuameng修复SEEKBAR快进重新播放问题
                return true;
                    }
                  }	
                }
               return false;
		    }
        });
		//xuameng监听底部进度条遥控器结束

        mPlayerRetry.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.replay(true);
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
				hideBottomXu();
				}
            }
        });
        mPlayrefresh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.replay(false);
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
				hideBottomXu();
				}
            }
        });
        mNextBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.playNext(false);
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
				hideBottomXu();
				}
            }
        });
		mxuPlay.setOnClickListener(new OnClickListener() {			//xuameng 低菜单播放监听
            @Override												//xuameng 低菜单播放监听
            public void onClick(View view) {						//xuameng 低菜单播放监听
				boolean isInPlayback = isInPlaybackState();
				if ((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 350){                  //xuameng 防播放打断动画
					return;
				}
				DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
				if (isInPlayback) {
					if (!isDisplay || !isAnimation){
						if (mControlWrapper.isPlaying()){
							togglePlay();
							pauseIngXu();
							return;
						}
						if (!mControlWrapper.isPlaying()){
							togglePlay();
							playIngXu();
							return;
						}
					}													//xuameng 低菜单播放监听
				}
            }
        });

	   mxuPlay.setOnFocusChangeListener(new View.OnFocusChangeListener() {          //XUAMENG播放键预选取消SEEKBAR进度
            @Override         //xuameng进入SEEKBAR
	        public void onFocusChange(View v, boolean hasFocus){
            isSEEKBAR = false;       //xuameng进入SEEKBAR
	    }
	    });

        mPreBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.playPre();
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
				hideBottomXu();
				}
            }
        });
        mPlayerScaleBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int scaleType = mPlayerConfig.getInt("sc");
                    scaleType++;
                    if (scaleType > 5)
                        scaleType = 0;
                    mPlayerConfig.put("sc", scaleType);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    mControlWrapper.setScreenScaleType(scaleType);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerSpeedBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    float speed = (float) mPlayerConfig.getDouble("sp");
                    speed += 0.25f;
                    if (speed > 3)
                        speed = 0.5f;
                    mPlayerConfig.put("sp", speed);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    speed_old = speed;
                    mControlWrapper.setSpeed(speed);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        mPlayerSpeedBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("sp", 1.0f);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    speed_old = 1.0f;
                    mControlWrapper.setSpeed(1.0f);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int playerType = mPlayerConfig.getInt("pl");
                    ArrayList<Integer> exsitPlayerTypes = PlayerHelper.getExistPlayerTypes();
                    int playerTypeIdx = 0;
                    int playerTypeSize = exsitPlayerTypes.size();
                    for(int i = 0; i<playerTypeSize; i++) {
                        if (playerType == exsitPlayerTypes.get(i)) {
                            if (i == playerTypeSize - 1) {
                                playerTypeIdx = 0;
                            } else {
                                playerTypeIdx = i + 1;
                            }
                        }
                    }
                    playerType = exsitPlayerTypes.get(playerTypeIdx);
                    mPlayerConfig.put("pl", playerType);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
					if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
						hideBottomXu();
					}
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mPlayerBtn.requestFocus();
                mPlayerBtn.requestFocusFromTouch();
            }
        });

        mPlayerBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                FastClickCheckUtil.check(view);
				hideBottom();
                try {
                    int playerType = mPlayerConfig.getInt("pl");
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
                    dialog.setTip("请选择播放器");
                    dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            try {
                                dialog.cancel();
                                int thisPlayType = players.get(pos);
                                if (thisPlayType != playerType) {
                                    mPlayerConfig.put("pl", thisPlayType);
                                    updatePlayerCfgView();
                                    listener.updatePlayerCfg();
                                    listener.replay(false);
									if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
									hideBottomXu();
									}
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            mPlayerBtn.requestFocus();
                            mPlayerBtn.requestFocusFromTouch();
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
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerIJKBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    String ijk = mPlayerConfig.getString("ijk");
                    List<IJKCode> codecs = ApiConfig.get().getIjkCodes();
                    for (int i = 0; i < codecs.size(); i++) {
                        if (ijk.equals(codecs.get(i).getName())) {
                            if (i >= codecs.size() - 1)
                                ijk = codecs.get(0).getName();
                            else {
                                ijk = codecs.get(i + 1).getName();
                            }
                            break;
                        }
                    }
                    mPlayerConfig.put("ijk", ijk);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
					if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
					hideBottomXu();
					}
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mPlayerIJKBtn.requestFocus();
                mPlayerIJKBtn.requestFocusFromTouch();
            }
        });
//        增加播放页面片头片尾时间重置
        mPlayerTimeResetBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    mPlayerConfig.put("et", 0);
                    mPlayerConfig.put("st", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerTimeStartBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int current = (int) mControlWrapper.getCurrentPosition();
                    int duration = (int) mControlWrapper.getDuration();
                    if (current > duration / 2) return;
                    mPlayerConfig.put("st",current/1000);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerTimeStartBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("st", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerTimeSkipBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int current = (int) mControlWrapper.getCurrentPosition();
                    int duration = (int) mControlWrapper.getDuration();
                    if (current < duration / 2) return;
                    mPlayerConfig.put("et", (duration - current)/1000);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerTimeSkipBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("et", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mZimuBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.selectSubtitle();
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
                hideBottom();
				}
            }
        });
        mZimuBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
				FastClickCheckUtil.check(view);                   //xuameng 防播放打断动画
				if (mSubtitleView.getVisibility() == View.GONE) {
					if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
					hideBottom();
					}
					mSubtitleView.setVisibility(VISIBLE);
                    Toast.makeText(getContext(), "字幕已开启!", Toast.LENGTH_SHORT).show();
				} else if (mSubtitleView.getVisibility() == View.VISIBLE){
						if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
						hideBottom();
						}
					mSubtitleView.setVisibility(View.GONE);
//                  mSubtitleView.destroy();
//                  mSubtitleView.clearSubtitleCache();
//                  mSubtitleView.isInternal = false;
                    Toast.makeText(getContext(), "字幕已关闭!", Toast.LENGTH_SHORT).show();
				} 
			    return true;
            }
        });
        mAudioTrackBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.selectAudioTrack();
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
                hideBottom();
				}
            }
        });
        mLandscapePortraitBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                setLandscapePortrait();
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
                hideBottom();
				}
            }
        });
        mxuPlay.setNextFocusRightId(R.id.seekBar);					//xuameng底部菜单播放右键是进度条
        mSeekBar.setNextFocusLeftId(R.id.mxuplay);					//xuameng底部菜单进度条左键是播放
        mNextBtn.setNextFocusLeftId(R.id.audio_track_select);       //xuameng底部菜单下一集左键是音轨
		mxuPlay.setNextFocusLeftId(R.id.seekBar);                   //xuameng底部菜单播放左键是进度条
		mAudioTrackBtn.setNextFocusRightId(R.id.play_next);         //xuameng底部菜音轨右键是下一集
        mxuPlay.setNextFocusDownId(R.id.play_next);                 //xuameng底部菜单所有键上键都是播放
		mSeekBar.setNextFocusDownId(R.id.play_next);
		mNextBtn.setNextFocusUpId(R.id.mxuplay);
		mPreBtn.setNextFocusUpId(R.id.mxuplay);
		mPlayerRetry.setNextFocusUpId(R.id.mxuplay);
		mPlayrefresh.setNextFocusUpId(R.id.mxuplay);
		mPlayerScaleBtn.setNextFocusUpId(R.id.mxuplay);
		mPlayerSpeedBtn.setNextFocusUpId(R.id.mxuplay);
		mPlayerBtn.setNextFocusUpId(R.id.mxuplay);
		mPlayerIJKBtn.setNextFocusUpId(R.id.mxuplay);
		mPlayerTimeStartEndText.setNextFocusUpId(R.id.mxuplay);
		mPlayerTimeStartBtn.setNextFocusUpId(R.id.mxuplay);
		mPlayerTimeSkipBtn.setNextFocusUpId(R.id.mxuplay);
		mPlayerTimeResetBtn.setNextFocusUpId(R.id.mxuplay);
		mZimuBtn.setNextFocusUpId(R.id.mxuplay);
		mAudioTrackBtn.setNextFocusUpId(R.id.mxuplay);				//xuameng底部菜单所有键上键都是播放完
    }

    private void hideLiveAboutBtn() {
        if (mControlWrapper != null && mControlWrapper.getDuration() == 0) {
            mPlayerSpeedBtn.setVisibility(GONE);
            mPlayerTimeStartEndText.setVisibility(GONE);
            mPlayerTimeStartBtn.setVisibility(GONE);
            mPlayerTimeSkipBtn.setVisibility(GONE);
            mPlayerTimeResetBtn.setVisibility(GONE);
            mNextBtn.setNextFocusLeftId(R.id.audio_track_select);		     //xuameng底部菜单下一集左键是音轨
        } else {
            mPlayerSpeedBtn.setVisibility(View.VISIBLE);
            mPlayerTimeStartEndText.setVisibility(View.VISIBLE);
            mPlayerTimeStartBtn.setVisibility(View.VISIBLE);
            mPlayerTimeSkipBtn.setVisibility(View.VISIBLE);
            mPlayerTimeResetBtn.setVisibility(View.VISIBLE);
            mNextBtn.setNextFocusLeftId(R.id.audio_track_select);		    //xuameng底部菜单下一集左键是音轨
        }
    }

    public void initLandscapePortraitBtnInfo() {
        if(mControlWrapper!=null && mActivity!=null){
            int width = mControlWrapper.getVideoSize()[0];
            int height = mControlWrapper.getVideoSize()[1];
            double screenSqrt = ScreenUtils.getSqrt(mActivity);
            if (screenSqrt < 10.0 && width < height) {
                mLandscapePortraitBtn.setVisibility(View.VISIBLE);
                mLandscapePortraitBtn.setText("竖屏");
            }
        }
    }

    void setLandscapePortrait() {
        int requestedOrientation = mActivity.getRequestedOrientation();
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            mLandscapePortraitBtn.setText("横屏");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            mLandscapePortraitBtn.setText("竖屏");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    void initSubtitleInfo() {
        int subtitleTextSize = SubtitleHelper.getTextSize(mActivity);
        mSubtitleView.setTextSize(subtitleTextSize);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_vod_control_view;
    }

    public void showParse(boolean userJxList) {
        mParseRoot.setVisibility(userJxList ? VISIBLE : GONE);
    }

    private JSONObject mPlayerConfig = null;

    public void setPlayerConfig(JSONObject playerCfg) {
        this.mPlayerConfig = playerCfg;
        updatePlayerCfgView();
    }

    void updatePlayerCfgView() {
        try {
            int playerType = mPlayerConfig.getInt("pl");
            mPlayerBtn.setText(PlayerHelper.getPlayerName(playerType));
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerIJKBtn.setText(mPlayerConfig.getString("ijk"));
            mPlayerIJKBtn.setVisibility(playerType == 1 ? VISIBLE : GONE);
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerSpeedBtn.setText("x" + mPlayerConfig.getDouble("sp"));
            mPlayerTimeStartBtn.setText(PlayerUtils.stringForTime(mPlayerConfig.getInt("st") * 1000));
            mPlayerTimeSkipBtn.setText(PlayerUtils.stringForTime(mPlayerConfig.getInt("et") * 1000));
            mAudioTrackBtn.setVisibility((playerType == 1||playerType == 2) ? VISIBLE : GONE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setTitle(String playTitleInfo) {
        mPlayTitle.setText(playTitleInfo);
        mPlayTitle1.setText(playTitleInfo);
    }

    public void setUrlTitle(String playTitleInfo) {
        mPlayTitle.setText(playTitleInfo);
    }

    public void resetSpeed() {
        skipEnd = true;
        mHandler.removeMessages(1004);
        mHandler.sendEmptyMessageDelayed(1004, 100);
    }

    public interface VodControlListener {
        void playNext(boolean rmProgress);

        void playPre();

        void prepared();

        void changeParse(ParseBean pb);

        void updatePlayerCfg();

        void replay(boolean replay);

        void errReplay();

        void selectSubtitle();

        void selectAudioTrack();
    }

    public void setListener(VodControlListener listener) {
        this.listener = listener;
    }

    private VodControlListener listener;

    private boolean skipEnd = true;

    @Override
    protected void setProgress(int duration, int position) {

        if (mIsDragging) {
            return;
        }
        super.setProgress(duration, position);
        if (skipEnd && position != 0 && duration != 0) {
            int et = 0;
            try {
                et = mPlayerConfig.getInt("et");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (et > 0 && position + (et * 1000) >= duration) {
                skipEnd = false;
                listener.playNext(true);
            }
        }
		mCurrentTime.setText(PlayerUtils.stringForTime(position));        //xuameng当前进程时间
        mTotalTime.setText(PlayerUtils.stringForTime(duration));	   //xuameng总进程时间
        if (duration > 0) {
            mSeekBar.setEnabled(true);
            mSeekBar.setProgress(position);	 //xuameng当前进程
			mSeekBar.setMax(duration);       //xuameng设置总进程必须
        } else {
            mSeekBar.setEnabled(false);
        }
        int percent = mControlWrapper.getBufferedPercentage();
		int totalBuffer = percent * duration;
		int SecondaryProgress = totalBuffer / 100;
        if (percent >= 98) {
            mSeekBar.setSecondaryProgress(duration);
        } else {
            mSeekBar.setSecondaryProgress(SecondaryProgress);   //xuameng缓冲进度
        }
    }

    private boolean simSlideStart = false;
    private int simSeekPosition = 0;
    private long simSlideOffset = 0;
	private long mSpeedTimeUp = 0;         //xuameng上键间隔时间

    public void tvSlideStop() {
		mIsDragging = false;                //xuamengsetProgress监听
        mControlWrapper.startProgress();    //xuameng启动进程
        mControlWrapper.startFadeOut();
		mSpeedTimeUp = 0;
        if (!simSlideStart)
            return;
        mControlWrapper.seekTo(simSeekPosition);
        if (!mControlWrapper.isPlaying())
        //xuameng快进暂停就暂停测试    mControlWrapper.start();    //测试成功，如果想暂停时快进自动播放取消注销
        simSlideStart = false;
        //simSeekPosition = 0;  //XUAMENG重要要不然重0播放
        simSlideOffset = 0;
    }

    public void tvSlideStopXu() {           //xuameng修复SEEKBAR快进重新播放问题
		mIsDragging = false;                //xuamengsetProgress监听
        mControlWrapper.startProgress();    //xuameng启动进程
        mControlWrapper.startFadeOut();
		mSpeedTimeUp = 0;
        if (!simSlideStart)
            return;
		if (isSEEKBAR){
        mControlWrapper.seekTo(simSeekPosition);
		}
        if (!mControlWrapper.isPlaying())
        //xuameng快进暂停就暂停测试    mControlWrapper.start();    //测试成功，如果想暂停时快进自动播放取消注销
        simSlideStart = false;
//		simSeekPosition = 0;      //XUAMENG重要
        simSlideOffset = 0;
    }

    public void tvSlideStart(int dir) {
		mIsDragging = true;                 //xuamengsetProgress不监听
        mControlWrapper.stopProgress();		//xuameng结束进程
        mControlWrapper.stopFadeOut();
        int duration = (int) mControlWrapper.getDuration();
        if (duration <= 0)
            return;
        if (!simSlideStart) {
            simSlideStart = true;
        }
        // 每次10秒
		if (mSpeedTimeUp == 0){
			mSpeedTimeUp = System.currentTimeMillis();
		}
		if (System.currentTimeMillis() - mSpeedTimeUp < 3000) {
        simSlideOffset += (10000.0f * dir);
		}
	    if (System.currentTimeMillis() - mSpeedTimeUp > 3000 && System.currentTimeMillis() - mSpeedTimeUp < 6000) {
        simSlideOffset += (30000.0f * dir);
		}
	    if (System.currentTimeMillis() - mSpeedTimeUp > 6000 && System.currentTimeMillis() - mSpeedTimeUp < 9000) {
        simSlideOffset += (60000.0f * dir);
		}
	    if (System.currentTimeMillis() - mSpeedTimeUp > 9000) {
        simSlideOffset += (120000.0f * dir);
		}
        int currentPosition = (int) mControlWrapper.getCurrentPosition();
        int position = (int) (simSlideOffset + currentPosition);
        if (position > duration) position = duration;
        if (position < 0) position = 0;
        updateSeekUI(currentPosition, position, duration);
        simSeekPosition = position;
		mSeekBar.setProgress(simSeekPosition);  //xuameng设置SEEKBAR当前进度
		mCurrentTime.setText(PlayerUtils.stringForTime(simSeekPosition));  //xuameng设置SEEKBAR当前进度
    }

	public void tvSlideStartXu(int dir) {
		isSEEKBAR = true;
		mIsDragging = true;                 //xuamengsetProgress不监听
        mControlWrapper.stopProgress();		//xuameng结束进程
        mControlWrapper.stopFadeOut();
        int duration = (int) mControlWrapper.getDuration();
        if (duration <= 0)
            return;
        if (!simSlideStart) {
            simSlideStart = true;
        }
        // 每次10秒
		if (mSpeedTimeUp == 0){
			mSpeedTimeUp = System.currentTimeMillis();
		}
		if (System.currentTimeMillis() - mSpeedTimeUp < 3000) {
        simSlideOffset += (10000.0f * dir);
		}
	    if (System.currentTimeMillis() - mSpeedTimeUp > 3000 && System.currentTimeMillis() - mSpeedTimeUp < 6000) {
        simSlideOffset += (30000.0f * dir);
		}
	    if (System.currentTimeMillis() - mSpeedTimeUp > 6000 && System.currentTimeMillis() - mSpeedTimeUp < 9000) {
        simSlideOffset += (60000.0f * dir);
		}
	    if (System.currentTimeMillis() - mSpeedTimeUp > 9000) {
        simSlideOffset += (120000.0f * dir);
		}
        int currentPosition = (int) mControlWrapper.getCurrentPosition();
        int position = (int) (simSlideOffset + currentPosition);
        if (position > duration) position = duration;
        if (position < 0) position = 0;
        simSeekPosition = position;
		mSeekBar.setProgress(simSeekPosition);  //xuameng设置SEEKBAR当前进度
		mCurrentTime.setText(PlayerUtils.stringForTime(simSeekPosition));  //xuameng设置SEEKBAR当前进度
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, int duration) {            //xuameng手机滑动屏幕快进
        super.updateSeekUI(curr, seekTo, duration);
        if (seekTo > curr) {
            mProgressIcon.setImageResource(R.drawable.icon_prexu);                     //xuameng快进图标更换
        } else {
            mProgressIcon.setImageResource(R.drawable.icon_backxu);					   //xuameng快进图标更换
        }
		mIsDragging = false;                //xuamengsetProgress监听
        mControlWrapper.startProgress();    //xuameng启动进程 手机滑动快进时候暂停图标文字跟随变化
        mControlWrapper.startFadeOut();
        mProgressText.setText(PlayerUtils.stringForTime(seekTo) + " / " + PlayerUtils.stringForTime(duration));
        mHandler.sendEmptyMessage(1000);
        mHandler.removeMessages(1001);
        mHandler.sendEmptyMessageDelayed(1001, 1000);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        videoPlayState = playState;
        switch (playState) {
            case VideoView.STATE_IDLE:
				if(!isPlaying && mTvPausexu.getVisibility() == View.VISIBLE){
			    ObjectAnimator animator30 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", -0,700);				//xuameng动画暂停菜单开始
                animator30.setDuration(300);			//xuameng动画暂停菜单
                animator30.addListener(new AnimatorListenerAdapter() {
                @Override
			    public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
				isPlaying = true;  //xuameng动画开启
			    }
                public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
			    MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
			    mTvPausexu.setVisibility(GONE);                //xuameng动画暂停菜单隐藏 
				isPlaying = false;  //xuameng动画开启
                }
                });
			    animator30.start();						      //xuameng动画暂停菜单结束
			    }
				mxuPlay.setText("准备");
				mVideoSize.setText("[ 0 X 0 ]");
				if (MxuamengMusic.getVisibility() == View.VISIBLE){  //xuameng播放音乐背景
					MxuamengMusic.setVisibility(GONE);
					}
				if (iv_circle_bg.getVisibility() == View.VISIBLE){  //xuameng音乐播放时图标
					iv_circle_bg.setVisibility(GONE);
					}
                break;
            case VideoView.STATE_PLAYING:
                initLandscapePortraitBtnInfo();
                startProgress();
				mxuPlay.setText("暂停");               //xuameng底部菜单显示暂停
				//playIngXu();	
                break;
            case VideoView.STATE_PAUSED:
				mxuPlay.setText("播放");			   //xuameng底部菜单显示播放
                //mTopRoot1.setVisibility(GONE);       //xuameng隐藏上面菜单
                //mTopRoot2.setVisibility(GONE);       //xuameng隐藏上面菜单
                //mPlayTitle.setVisibility(VISIBLE);   //xuameng显示上面菜单
			    //pauseIngXu();
                break;
            case VideoView.STATE_ERROR:
                listener.errReplay();
				mxuPlay.setText("错误");
				if(!isPlaying && mTvPausexu.getVisibility() == View.VISIBLE){
			    ObjectAnimator animator31 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", -0,700);				//xuameng动画暂停菜单开始
                animator31.setDuration(300);			//xuameng动画暂停菜单
                animator31.addListener(new AnimatorListenerAdapter() {
                @Override
			    public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
				isPlaying = true;  //xuameng动画开启
			    }
                public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
			    MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
			    mTvPausexu.setVisibility(GONE);                //xuameng动画暂停菜单隐藏 
				isPlaying = false;  //xuameng动画开启
                }
                });
			    animator31.start();						      //xuameng动画暂停菜单结束
			    }
                break;
            case VideoView.STATE_PREPARED:
                mPlayLoadNetSpeed.setVisibility(GONE);
                hideLiveAboutBtn();
                listener.prepared();
                break;
            case VideoView.STATE_BUFFERED:
                mPlayLoadNetSpeed.setVisibility(GONE);
                break;
            case VideoView.STATE_PREPARING:
				simSeekPosition = 0;       //XUAMENG重要,换视频时重新记录进度
				mxuPlay.setText("准备");
				if(!isPlaying && mTvPausexu.getVisibility() == View.VISIBLE){
			    ObjectAnimator animator32 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", -0,700);				//xuameng动画暂停菜单开始
                animator32.setDuration(300);			//xuameng动画暂停菜单
                animator32.addListener(new AnimatorListenerAdapter() {
                @Override
			    public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
				isPlaying = true;  //xuameng动画开启
			    }
                public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
			    MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
			    mTvPausexu.setVisibility(GONE);                //xuameng动画暂停菜单隐藏 
				isPlaying = false;  //xuameng动画开启
                }
                });
			    animator32.start();						      //xuameng动画暂停菜单结束
			    }
            case VideoView.STATE_BUFFERING:
                if(mProgressRoot.getVisibility()==GONE)mPlayLoadNetSpeed.setVisibility(VISIBLE);
				if (iv_circle_bg.getVisibility() == View.VISIBLE){  //xuameng音乐播放时图标
					iv_circle_bg.setVisibility(GONE);
					}
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                listener.playNext(true);
                break;
        }
    }

    boolean isBottomVisible() {
        return mBottomRoot.getVisibility() == VISIBLE;
    }

	boolean ismTvPausexuVisible() {				//xuameng判断暂停动画是否显示
        return mTvPausexu.getVisibility() == VISIBLE;
    }

    void showBottom() {
		isSEEKBAR = false;        //XUAMENG隐藏菜单时修复进度条BUG
        mHandler.removeMessages(1003);
        mHandler.sendEmptyMessage(1002);
    }

    void hideBottom() {
		isSEEKBAR = false;        //XUAMENG隐藏菜单时修复进度条BUG
        mHandler.removeMessages(1002);
        mHandler.sendEmptyMessage(1003);
    }

    void hideBottomXu() {
		isSEEKBAR = false;        //XUAMENG隐藏菜单时修复进度条BUG
        mHandler.removeMessages(1002);
        mHandler.sendEmptyMessage(1005);
    }

	public void playIngXu() {
		mxuPlay.setVisibility(View.VISIBLE);
        mxuPlay.setTextColor(Color.WHITE);
        mxuPlay.setText("暂停");               //xuameng底部菜单显示暂停
		
		if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
			hideBottom();
		}
		ObjectAnimator animator9 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", -0,700);				//xuameng动画暂停菜单开始
        animator9.setDuration(300);			//xuameng动画暂停菜单
        animator9.addListener(new AnimatorListenerAdapter() {
        @Override
		public void onAnimationStart(Animator animation) {
        super.onAnimationStart(animation);
        MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
		isPlaying = true;  //xuameng动画开启
		}
        public void onAnimationEnd(Animator animation) {
        super.onAnimationEnd(animation);
		MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
		mTvPausexu.setVisibility(GONE);                //xuameng动画暂停菜单隐藏 
		isPlaying = false;  //xuameng动画开启
        }
        });
		animator9.start();						      //xuameng动画暂停菜单结束
    }

	public void pauseIngXu() {
		mTvPausexu.setVisibility(VISIBLE);
		if (mBottomRoot.getVisibility() == View.GONE && !isDisplay) {              //xuameng如果没显示菜单就显示
            showBottom();
            myHandle.postDelayed(myRunnable, myHandleSeconds);
        }
        ObjectAnimator animator8 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", 700,0);				//xuameng动画暂停菜单开始
        animator8.setDuration(300);			//xuameng动画暂停菜单
        animator8.addListener(new AnimatorListenerAdapter() {
        @Override
		public void onAnimationStart(Animator animation) {
        super.onAnimationStart(animation);
        MxuamengView.setVisibility(VISIBLE);		   //xuameng动画开始防点击
		isPlaying = true;  //xuameng动画开启
		}
        public void onAnimationEnd(Animator animation) {
        super.onAnimationEnd(animation);
		MxuamengView.setVisibility(GONE);			   //xuameng动画结束可点击
		isPlaying = false;  //xuameng动画开启
        }
        });
		animator8.start();						       //xuameng动画暂停菜单结束
		mxuPlay.setVisibility(View.VISIBLE);
        mxuPlay.setTextColor(Color.WHITE);	   //xuameng底部菜单显示播放颜色
        mxuPlay.setText("播放");			   //xuameng底部菜单显示播放
		mPlayPauseTimexu.setVisibility(GONE);  //xuameng隐藏上面时间
        mPlayTitle.setVisibility(GONE);        //xuameng隐藏上面视频名称
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        myHandle.removeCallbacks(myRunnable);
        if (super.onKeyEvent(event)) {
            return true;
        }
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        if (isBottomVisible()) {
            mHandler.removeMessages(1002);
    //        mHandler.removeMessages(1003);      xuameng重大BUG修复
            myHandle.postDelayed(myRunnable, myHandleSeconds);
            return super.dispatchKeyEvent(event);
        }
        boolean isInPlayback = isInPlaybackState();
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
					if ((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 350){                  //xuameng 防播放打断动画					
						return true;
					}
					DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
					if (isInPlayback) {
						if (!isDisplay || !isAnimation){
							if (mControlWrapper.isPlaying()){
							togglePlay();
							pauseIngXu();
							return true;
							}
							if (!mControlWrapper.isPlaying()){
							togglePlay();
							playIngXu();
							return true;
							}
						}
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode== KeyEvent.KEYCODE_MENU) {
					if ((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 350){                  //xuameng 防播放打断动画					
						return true;
					}
					DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                if (mBottomRoot.getVisibility() == View.GONE && !isDisplay) {
                    showBottom();
                    myHandle.postDelayed(myRunnable, myHandleSeconds);
                    return true;
                }
            }      
            } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStop();
                    return true;
               }
            }
        }
        return super.dispatchKeyEvent(event);
    }


    private boolean fromLongPress;
    private float speed_old = 1.0f;
    @Override
    public void onLongPress(MotionEvent e) {
        if (videoPlayState!=VideoView.STATE_PAUSED) {
            fromLongPress = true;
            try {
                speed_old = (float) mPlayerConfig.getDouble("sp");
                float speed = 3.0f;
                mPlayerConfig.put("sp", speed);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                mControlWrapper.setSpeed(speed);
            } catch (JSONException f) {
                f.printStackTrace();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (fromLongPress) {
                fromLongPress =false;
                try {
                    float speed = speed_old;
                    mPlayerConfig.put("sp", speed);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    mControlWrapper.setSpeed(speed);
                } catch (JSONException f) {
                    f.printStackTrace();
                }
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        myHandle.removeCallbacks(myRunnable);
        if (mBottomRoot.getVisibility() == View.GONE && !isDisplay) {
            showBottom();
            // 闲置计时关闭
            myHandle.postDelayed(myRunnable, myHandleSeconds);
        } else {
				if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
                hideBottom();
				}
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {      //xuameng双击
		if ((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 350){                  //xuameng 防播放打断动画
			return true;
			}
		DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
        if (!isLock && isInPlaybackState()){
			if (!isDisplay || !isAnimation){
				if (mControlWrapper.isPlaying()){
				togglePlay();
				pauseIngXu();
				return true;
				}
				if (!mControlWrapper.isPlaying()){
				togglePlay();
				playIngXu();
				return true;
				}
			}
		}
        return true;
    }
    
    private class LockRunnable implements Runnable {
        @Override
        public void run() {
            mLockView.setVisibility(INVISIBLE);
        }
    }
    
    @Override
    public boolean onBackPressed() {
		if (isBottomVisible() && (System.currentTimeMillis() - DOUBLE_CLICK_TIME) < 350) {               //xuameng返回键防连击1.5秒（为动画,当动画显示时）
            DOUBLE_CLICK_TIME = System.currentTimeMillis();
            return true;
            }
		if ((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 350){                  //xuameng 防播放打断动画					
			return true;
			}
			DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
        if (isClickBackBtn) {
            isClickBackBtn = false;
            if ((System.currentTimeMillis() - DOUBLE_CLICK_TIME) > 350) {                                //xuameng  屏幕上的返回键退出
            DOUBLE_CLICK_TIME = System.currentTimeMillis();
            mBottomRoot.setVisibility(GONE);	        //动画结束后隐藏下菜单
            mTopRoot1.setVisibility(GONE);	            //动画结束后隐藏上菜单
            mTopRoot2.setVisibility(GONE);              //动画结束后隐藏上菜单
            mPlayPauseTimexu.setVisibility(GONE);       //xuameng隐藏上面时间
            mPlayTitle.setVisibility(GONE);             //xuameng隐藏上面视频名称
            backBtn.setVisibility(INVISIBLE);           //返回键隐藏菜单
			mTvPausexu.setVisibility(GONE);				//隐藏暂停菜单
			mLockView.setVisibility(INVISIBLE);         //xuameng隐藏屏幕锁
            }
            return false;
        }
        if (super.onBackPressed()) {                                                                      //xuameng返回退出
			iv_circle_bg.setVisibility(GONE);  //xuameng音乐播放时图标
			MxuamengMusic.setVisibility(GONE);  //xuameng播放音乐背景
			mHandler.removeCallbacks(myRunnable2);
			mHandler.removeCallbacks(xuRunnable);
			mHandler.removeCallbacks(myRunnableMusic);	
            return true;
        }
        if (isBottomVisible() && (System.currentTimeMillis() - DOUBLE_CLICK_TIME > 350)) {			      //xuameng按返回键退出
			DOUBLE_CLICK_TIME = System.currentTimeMillis();
			if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE){
              hideBottom();
			}
            return true;
        }
		mPlayPauseTimexu.setVisibility(GONE);       //xuameng隐藏上面时间
        mPlayTitle.setVisibility(GONE);             //xuameng隐藏上面视频名称
        backBtn.setVisibility(INVISIBLE);           //返回键隐藏菜单
	    mTvPausexu.setVisibility(GONE);				//隐藏暂停菜单
		mLockView.setVisibility(INVISIBLE);         //xuameng隐藏屏幕锁
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(myRunnable2);
		mHandler.removeCallbacks(xuRunnable);
		mHandler.removeCallbacks(myRunnableMusic);		
    }
}
