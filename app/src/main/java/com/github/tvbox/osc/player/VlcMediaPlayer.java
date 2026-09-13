package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.AndroidUtil;

import java.util.ArrayList;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * VlcPlayer —— 基于 libVLC (org.videolan.android:libvlc-all) 的播放器实现
 * 与 ExoMediaPlayer / MpvMediaPlayer 保持同一套 AbstractPlayer 接口，可直接替换接入
 *
 * 接入方式（与现有 PlayerHelper 一致）：
 *   1. build.gradle: implementation 'org.videolan.android:libvlc-all:3.7.5'
 *   2. manifest: 添加 INTERNET / 本地文件相关权限
 *   3. 在 PlayerHelper 中增加 case 14 -> new PlayerFactory<VlcMediaPlayer>(){ create 返回 new VlcMediaPlayer(context) }
 *   4. 在 getPlayersInfo 增加 14 -> "VLC播放器"，getPlayersExistInfo 视情况加判定
 *
 * Created by tvbox on 2026-09.
 */
public class VlcMediaPlayer extends AbstractPlayer {

    private static final String TAG = "VLC";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;
    private Media mCurrentMedia;

    private Context context;
    private boolean mHWDecode = true;       // true=硬解  false=软解
    private boolean mPlayingNotified;       // 是否已通知 onPrepared，防止重复
    private volatile boolean mReleased;     // 是否已被释放，避免重复操作

    private int mVideoWidth, mVideoHeight;
    private volatile boolean mSizeProbed;

    public VlcMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    /* ========================= 生命周期 ========================= */

    @Override
    public void initPlayer() {
        if (mMediaPlayer != null) {
            try { mMediaPlayer.release(); } catch (Exception ignored) {}
            mMediaPlayer = null;
        }
        if (mLibVLC != null) {
            mLibVLC.release();
            mLibVLC = null;
        }

        // xuameng 解码模式：true=硬解 false=软解（可按需接配置）
        ArrayList<String> options = new ArrayList<>();
        if (mHWDecode) {
            // 硬解：优先使用 MediaCodec 硬解
            options.add("--codec=all");
            options.add("--decoder=medCodec");
        } else {
            // 软解：ffmpeg 软件解码
            options.add("--codec=all");
            options.add("--decoder=all");
        }
        options.add(":finish-on-close");
        options.add("--network-caching=300");   // 网络缓冲 300ms
        options.add("--rtsp-tcp");              // RTSP 走 TCP
        options.add("--drop-late-frames");
        options.add("--skip-frames");
        options.add("--no-audio-dsp");

        mLibVLC = new LibVLC(context, options);
        mMediaPlayer = new MediaPlayer(mLibVLC);
        mMediaPlayer.setEventListener(this::onEvent);
        mMediaPlayer.setAudioOutput("opensles"); // Android 上用 OpenSL ES

        mPlayingNotified = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mSizeProbed = false;
        mReleased = false;

        setOptions();
        // 启动尺寸探测：轮询 libVLC 当前视频尺寸，确保 onVideoSizeChanged 能触发
        startVideoSizeProbe();
    }

    /**
     * 尺寸探测：libVLC 在 Vout 建立后才有有效尺寸，主线程轻量轮询即可。
     * 用单次 postDelayed 链式探测，播放器释放后自动停止。
     */
    private void startVideoSizeProbe() {
        if (mReleased || mMediaPlayer == null) return;
        int w = mMediaPlayer.getVideoWidth();
        int h = mMediaPlayer.getVideoHeight();
        if (w > 0 && h > 0 && !mSizeProbed) {
            mSizeProbed = true;
            notifyVideoSizeIfReady(w, h);
        }
        mainHandler.postDelayed(() -> {
            // 再次探测，兼容延迟出现的尺寸（直播、软解等）
            int nw = mMediaPlayer != null ? mMediaPlayer.getVideoWidth() : 0;
            int nh = mMediaPlayer != null ? mMediaPlayer.getVideoHeight() : 0;
            if (nw > 0 && nh > 0 && !mSizeProbed) {
                mSizeProbed = true;
                notifyVideoSizeIfReady(nw, nh);
            }
        }, 500);
    }

    /* ========================= 核心 ========================= */

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        if (mMediaPlayer == null) {
            initPlayer();
        }
        // 重置状态
        mPlayingNotified = false;
        mVideoWidth = 0;
        mVideoHeight = 0;

        Media media = new Media(mLibVLC, path);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                media.addOption(":http-header=" + e.getKey() + ": " + e.getValue());
            }
        }
        media.setHWDecoderEnabled(mHWDecode, false);
        media.addOption(":network-caching=300");
        // 本地文件
        if (path.startsWith("/") || "file".equalsIgnoreCase(AndroidUtil.getScheme(path))) {
            media.setType(IMedia.Type.FILE);
        }

        releaseCurrentMedia();
        mCurrentMedia = media;
        mMediaPlayer.setMedia(media);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        if (mMediaPlayer == null) {
            initPlayer();
        }
        mPlayingNotified = false;
        mVideoWidth = 0;
        mVideoHeight = 0;

        Media media = new Media(mLibVLC, fd.getFileDescriptor());
        media.setHWDecoderEnabled(mHWDecode, false);
        media.addOption(":network-caching=300");

        releaseCurrentMedia();
        mCurrentMedia = media;
        mMediaPlayer.setMedia(media);
    }

    @Override
    public void start() {
        if (mMediaPlayer != null) {
            mMediaPlayer.play();
        }
    }

    @Override
    public void pause() {
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
        }
    }

    @Override
    public void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
        }
    }

    @Override
    public void prepareAsync() {
        if (mMediaPlayer == null) return;
        if (mCurrentMedia == null) return;
        mMediaPlayer.play(); // libVLC 是异步的，setMedia 后直接 play 即可
    }

    @Override
    public void reset() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.setVolume(0f, 0f);
        }
        releaseCurrentMedia();
        mPlayingNotified = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mSizeProbed = false;
    }

    @Override
    public void release() {
        if (mReleased) return;
        mReleased = true;
        releaseCurrentMedia();
        if (mMediaPlayer != null) {
            try { mMediaPlayer.release(); } catch (Exception ignored) {}
            mMediaPlayer = null;
        }
        if (mLibVLC != null) {
            mLibVLC.release();
            mLibVLC = null;
        }
    }

    private void releaseCurrentMedia() {
        if (mCurrentMedia != null) {
            try { mCurrentMedia.release(); } catch (Exception ignored) {}
            mCurrentMedia = null;
        }
    }

    /* ========================= 播放控制 ========================= */

    @Override
    public boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setTime(PlayerUtils.safeTimeMs(time));
        }
    }

    @Override
    public long getCurrentPosition() {
        if (mMediaPlayer == null) return 0;
        long t = mMediaPlayer.getTime();
        return t <= 0 ? 0 : t;
    }

    @Override
    public long getDuration() {
        if (mMediaPlayer == null) return 0;
        long d = mMediaPlayer.getDuration();
        return d <= 0 ? 0 : d;
    }

    @Override
    public int getBufferedPercentage() {
        if (mMediaPlayer == null) return 0;
        long buffered = mMediaPlayer.getBufferedBytes();
        long total = mMediaPlayer.getDuration();
        if (total <= 0) return 0;
        return (int) Math.min(100, (buffered * 100) / total);
    }

    @Override
    public int getAudioSessionId() {
        // libVLC 使用系统音频，返回 0 即可（与系统播放器一致）
        return 0;
    }

    @Override
    public void setSurface(Surface surface) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            if (holder == null) {
                mMediaPlayer.setSurface(null);
            } else {
                mMediaPlayer.setSurface(holder.getSurface());
            }
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume((leftVolume + rightVolume) / 2);
        }
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setLoop(isLooping);
        }
    }

    @Override
    public void setOptions() {
        // 预留：可在 init 后再做配置
    }

    @Override
    public void setSpeed(float speed) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setPlaybackRate(speed);
        }
    }

    @Override
    public float getSpeed() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getPlaybackRate();
        }
        return 1f;
    }

    @Override
    public long getTcpSpeed() {
        return PlayerUtils.getNetSpeed(context);
    }

    /* ========================= 事件回调 ========================= */

    private void onEvent(MediaPlayer.Event event) {
        if (event == null || mPlayerEventListener == null) return;
        switch (event.type) {
            case MediaPlayer.Event.Opening:       // 缓冲开始
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                break;
            case MediaPlayer.Event.Buffering:      // 缓冲进度
                if (event.getBuffering() >= 100f) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, 100);
                } else {
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, (int) event.getBuffering());
                }
                break;
            case MediaPlayer.Event.Playing:       // 开始渲染
                // 兼容 onPrepared：用 startPosition 决定是否 seek
                final long startPos = getStartPosition();
                if (!isStartPositionApplied() && startPos > 0) {
                    try {
                        mMediaPlayer.setTime(startPos);
                        markStartPositionApplied();
                    } catch (Exception ignored) {}
                }
                if (!mPlayingNotified) {
                    mPlayingNotified = true;
                    mainHandler.post(() -> mPlayerEventListener.onPrepared());
                }
                mainHandler.postDelayed(() -> {
                    if (mPlayerEventListener != null)
                        mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                }, 20);
                break;
            case MediaPlayer.Event.EndReached:     // 播放完成
                mPlayingNotified = false;
                mainHandler.post(() -> mPlayerEventListener.onCompletion());
                break;
            case MediaPlayer.Event.Error:          // 出错
                mPlayingNotified = false;
                Log.e(TAG, "VLC error: " + event.getError());
                mainHandler.post(() -> mPlayerEventListener.onError());
                break;
            case MediaPlayer.Event.VideoPlayableChanged:
                // 视频尺寸变化（部分版本用此事件）
                if (event.getVideoPlayableWidth() > 0) {
                    notifyVideoSizeIfReady(event.getVideoPlayableWidth(), event.getVideoPlayableHeight());
                }
                break;
        }
    }

    private void notifyVideoSizeIfReady(int w, int h) {
        if (w > 0 && h > 0 && mPlayerEventListener != null) {
            mainHandler.post(() -> {
                if (mPlayerEventListener != null)
                    mPlayerEventListener.onVideoSizeChanged(w, h);
            });
        }
    }
}
