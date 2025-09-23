package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.TrafficStats;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultLoadControl;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.SimpleExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectionArray;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;
import androidx.media3.ui.PlayerView;
import com.github.tvbox.osc.util.HawkConfig;  //xuameng EXO解码
import com.orhanobut.hawk.Hawk; //xuameng EXO解码
import com.github.tvbox.osc.util.AudioTrackMemory;  //xuameng记忆选择音轨
import com.github.tvbox.osc.base.App;  //xuameng 提示消息

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class ExoMediaPlayer extends AbstractPlayer implements Player.Listener {

    protected Context mAppContext;
    protected SimpleExoPlayer mMediaPlayer;
    protected MediaSource mMediaSource;
    protected ExoMediaSourceHelper mMediaSourceHelper;
    protected ExoTrackNameProvider trackNameProvider;
    protected TrackSelectionArray mTrackSelections;
    private PlaybackParameters mSpeedPlaybackParameters;
    private boolean mIsPreparing;

    private LoadControl mLoadControl;
    private RenderersFactory mRenderersFactory;
    private DefaultTrackSelector mTrackSelector;
    private static AudioTrackMemory memory;    //xuameng记忆选择音轨

    private int errorCode = -100;
    private int mRetryCount = 0;  //xuameng播放出错重试十次
    private static final int MAX_RETRIES = 3;  //xuameng播放出错重试3次

    public ExoMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
        mMediaSourceHelper = ExoMediaSourceHelper.getInstance(context);
    }

    @Override
    public void initPlayer() {
        // xuameng释放旧实例
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer.removeListener(this);
        }
        // xuameng渲染器配置
        boolean exoDecode = Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false);
        int exoSelect = Hawk.get(HawkConfig.EXO_PLAY_SELECTCODE, 0);
        // ExoPlayer2 解码模式选择逻辑
        int rendererMode;
        if (exoSelect > 0) {
            // 选择器优先
            rendererMode = (exoSelect == 1) 
                ? EXTENSION_RENDERER_MODE_ON    // 硬解
                : EXTENSION_RENDERER_MODE_PREFER; // 软解
        } else {
            // 使用exoDecode配置
            rendererMode = exoDecode 
                ? EXTENSION_RENDERER_MODE_PREFER // 软解
                : EXTENSION_RENDERER_MODE_ON;   // 硬解
        }
        mRenderersFactory = new NextRenderersFactory(mAppContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(rendererMode);

        // xuameng轨道选择器配置
        mTrackSelector = new DefaultTrackSelector(mAppContext);

        //xuameng加载策略控制
        mLoadControl = new DefaultLoadControl();

		mTrackSelector.setParameters(mTrackSelector.getParameters().buildUpon()
            .setPreferredTextLanguages("ch", "chi", "zh", "zho", "en")           // 设置首选字幕语言为中文
            .setPreferredAudioLanguages("ch", "chi", "zh", "zho", "en")                        // 设置首选音频语言为中文
            .setTunnelingEnabled(true));   //xuameng字幕、音轨默认选择中文

// 创建优先使用扩展渲染器的播放器实例
SimpleExoPlayer mMediaPlayer = new SimpleExoPlayer.Builder(mAppContext)
    .setRenderersFactory(new DefaultRenderersFactory(mAppContext))
	.setLoadControl(mLoadControl)
	.setTrackSelector(mTrackSelector)
    .build();


        setOptions();
        mMediaPlayer.addListener(this);
    }

    public DefaultTrackSelector getTrackSelector() {
        return mTrackSelector;
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        mMediaSource = mMediaSourceHelper.getMediaSource(path, headers, false, errorCode);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        //no support
    }

    @Override
    public void start() {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.setPlayWhenReady(false);
    }

    @Override
    public void stop() {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.stop();
    }

    @Override
    public void prepareAsync() {
        if (mMediaPlayer == null)
            return;
        if (mMediaSource == null) return;
        if (mSpeedPlaybackParameters != null) {
            mMediaPlayer.setPlaybackParameters(mSpeedPlaybackParameters);
        }
        mIsPreparing = true;
        mMediaPlayer.setMediaSource(mMediaSource);
        mMediaPlayer.prepare();
    }

    @Override
    public void reset() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.clearMediaItems();
            mMediaPlayer.setVideoSurface(null);
            mIsPreparing = false;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mMediaPlayer == null)
            return false;
        int state = mMediaPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mMediaPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.seekTo(time);
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.removeListener(this);
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        mIsPreparing = false;
        mSpeedPlaybackParameters = null;
    }

    @Override
    public long getCurrentPosition() {
        if (mMediaPlayer == null)
            return 0;
        return mMediaPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if (mMediaPlayer == null)
            return 0;
        return mMediaPlayer.getDuration();
    }

    @Override
    public int getAudioSessionId() {       //XUAMENG 获取音频ID
        return mMediaPlayer.getAudioSessionId();
    }

    @Override
    public int getBufferedPercentage() {
        return mMediaPlayer == null ? 0 : mMediaPlayer.getBufferedPercentage();
    }

    @Override
    public void setSurface(Surface surface) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVideoSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null)
            setSurface(null);
        else
            setSurface(holder.getSurface());
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mMediaPlayer != null)
            mMediaPlayer.setVolume((leftVolume + rightVolume) / 2);
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mMediaPlayer != null)
            mMediaPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
    }

    @Override
    public void setOptions() {
        //准备好就开始播放
        mMediaPlayer.setPlayWhenReady(true);
    }

    @Override
    public void setSpeed(float speed) {
        PlaybackParameters playbackParameters = new PlaybackParameters(speed);
        mSpeedPlaybackParameters = playbackParameters;
        if (mMediaPlayer != null) {
            mMediaPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    @Override
    public float getSpeed() {
        if (mSpeedPlaybackParameters != null) {
            return mSpeedPlaybackParameters.speed;
        }
        return 1f;
    }

    @Override
    public long getTcpSpeed() {
        return PlayerUtils.getNetSpeed(mAppContext);
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
        if (trackNameProvider == null)
            trackNameProvider = new ExoTrackNameProvider(mAppContext.getResources());
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (mPlayerEventListener == null) return;
        if (mIsPreparing) {
            if (playbackState == Player.STATE_READY) {
                mPlayerEventListener.onPrepared();
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                mIsPreparing = false;
            }
            return;
        }
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                break;
            case Player.STATE_READY:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                break;
            case Player.STATE_ENDED:
                mPlayerEventListener.onCompletion();
                break;
            case Player.STATE_IDLE:
                break;
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
       String progressKey = Hawk.get(HawkConfig.EXO_PROGRESS_KEY, "");
        errorCode = error.errorCode;
        Log.e("EXOPLAYER", "" + error.errorCode);      //xuameng音频出错后尝试重播
        if (errorCode == 5001 && mRetryCount < MAX_RETRIES || errorCode == 5002 && mRetryCount < MAX_RETRIES || errorCode == 4001 && mRetryCount < MAX_RETRIES || errorCode == 4003 && mRetryCount < MAX_RETRIES || errorCode == 1004 && mRetryCount < MAX_RETRIES){
            boolean exoDecodeXu = Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false);
            int exoSelectXu = Hawk.get(HawkConfig.EXO_PLAY_SELECTCODE, 0);
            if (exoSelectXu == 1 && mRetryCount < MAX_RETRIES) {
                memory.getInstance(mAppContext).deleteExoTrack(progressKey);   //xuameng删除记忆音轨
                initPlayer();
                App.showToastShort(mAppContext, "音频获取错误！正在尝试切换可用音轨！如仍未成功请选择其它解码方式！");
                mRetryCount++;  // 计数器加一    重试3次
                prepareAsync();
                start();
                return;
            }
            if (exoSelectXu == 2 && mRetryCount < MAX_RETRIES) {
                memory.getInstance(mAppContext).deleteExoTrack(progressKey);   //xuameng删除记忆音轨
                App.showToastShort(mAppContext, "音频获取错误！正在重试！如仍未成功请选择其它解码方式！");
                mRetryCount++;  // 计数器加一    重试3次
                prepareAsync();
                start();
                return;
            }
            if(exoSelectXu == 0 && mRetryCount < MAX_RETRIES) {
                if(exoDecodeXu){
                   memory.getInstance(mAppContext).deleteExoTrack(progressKey);   //xuameng删除记忆音轨
                   mRetryCount++;  // 计数器加一    重试3次
                   App.showToastShort(mAppContext, "音频获取错误！正在重试！如仍未成功请选择其它解码方式！");
                   prepareAsync();
                   start();
                   return;
                }else{
                   memory.getInstance(mAppContext).deleteExoTrack(progressKey);   //xuameng删除记忆音轨
                   initPlayer();
                   App.showToastShort(mAppContext, "音频获取错误！正在尝试切换可用音轨！如仍未成功请选择其它解码方式！");
                   mRetryCount++;  // 计数器加一    重试3次
                   prepareAsync();
                   start();
                   return;
                }
	        }
        }else{
            mRetryCount = 0; // 重置计数器
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onError();
            }
        }
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(videoSize.width, videoSize.height);
            if (videoSize.unappliedRotationDegrees > 0) {
                mPlayerEventListener.onInfo(MEDIA_INFO_VIDEO_ROTATION_CHANGED, videoSize.unappliedRotationDegrees);
            }
        }
    }
}
