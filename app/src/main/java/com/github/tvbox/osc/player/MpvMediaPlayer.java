package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class MpvMediaPlayer extends AbstractPlayer {

    private static final String TAG = "MPV";

    // MPV Format Constants
    private static final int MPV_FORMAT_STRING = 1;
    private static final int MPV_FORMAT_FLAG   = 3;
    private static final int MPV_FORMAT_INT64  = 4;

    // MPV Event IDs
    private static final int MPV_EVENT_FILE_LOADED = 8;
    private static final int MPV_EVENT_END_FILE = 7;
    private static final int MPV_EVENT_SEEK = 20;
    private static final int MPV_EVENT_PLAYBACK_RESTART = 21;
    private static final int MPV_EVENT_VIDEO_RECONFIG = 23;

    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
    }

    private MPV mpv;
    private Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 进度与状态
    private long mPosition = 0;
    private long mDuration = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    // 状态标志
    private volatile boolean mPrepared = false;
    private volatile boolean mUserCalledStart = false; // 用户是否调用了start()
    private volatile boolean mPaused = false;
    private boolean mVideoSizeNotified = false;
    
    // UA 常量
    private static final String UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // seek 锁定
    private volatile boolean mSeekLock = false;
    private long mSeekTarget = 0;

    // 缓冲状态
    private volatile boolean mBuffering = false;
    
    // Surface 修复
    private Surface mPendingSurface = null;
    private boolean mSurfaceAttached = false;

    /* ========================= 缓冲通知 ========================= */
    private void notifyBufferingStart() {
        if (!mBuffering) {
            mBuffering = true;
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                }
            });
        }
    }

    private void notifyBufferingEnd() {
        if (mBuffering) {
            mBuffering = false;
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                }
            });
        }
    }

    /* ========================= Observer ========================= */

    private final MPV.EventObserver observer = new MPV.EventObserver() {
        public void eventProperty(String property) {}
        
        public void eventProperty(String property, long value) {
            if (mpv == null) return;
            Log.d(TAG, "property: " + property + " = " + value);
            
            if ("duration".equals(property)) {
                mDuration = value * 1000;
            } else if ("time-pos".equals(property)) {
                if (!mSeekLock) {
                    mPosition = value * 1000;
                }
            } else if ("demuxer-cache-time".equals(property)) {
                mCacheEnd = value;
            } else if ("dwidth".equals(property)) {
                mVideoWidth = (int) value;
                notifyVideoSizeIfReady();
            } else if ("dheight".equals(property)) {
                mVideoHeight = (int) value;
                notifyVideoSizeIfReady();
            }
        }

        public void eventProperty(String property, double value) {
            if (mpv == null) return;
            if ("duration".equals(property)) {
                mDuration = (long)(value * 1000);
            } else if ("time-pos".equals(property)) {
                if (!mSeekLock) {
                    mPosition = (long)(value * 1000);
                }
            }
        }

        public void eventProperty(String property, boolean value) {
            if (mpv == null) return;
            if ("paused-for-cache".equals(property)) {
                Log.d(TAG, "paused-for-cache=" + value);
                if (value) {
                    notifyBufferingStart();
                } else {
                    notifyBufferingEnd();
                }
            }
        }

        public void eventProperty(String property, String value) {
            if (mpv == null) return;
            if ("end-file-reason".equals(property) && "error".equals(value)) {
                Log.e(TAG, "end-file-reason=error");
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onError();
                    }
                });
            }
        }

        public void eventProperty(String property, MPVNode node) {}

        public void event(int eventId) { handleEvent(eventId); }
        public void event(int eventId, MPVNode node) { handleEvent(eventId); }

        private void handleEvent(int eventId) {
            if (mpv == null) return;
            Log.d(TAG, "MPV Event: " + eventId);

            if (eventId == MPV_EVENT_FILE_LOADED) {
                Log.d(TAG, "FILE_LOADED");
                // 注册关键属性监听
                mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
                mpv.observeProperty("duration", MPV_FORMAT_INT64);
                mpv.observeProperty("demuxer-cache-time", MPV_FORMAT_INT64);
                mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
                mpv.observeProperty("end-file-reason", MPV_FORMAT_STRING);
                mpv.observeProperty("dwidth", MPV_FORMAT_INT64);
                mpv.observeProperty("dheight", MPV_FORMAT_INT64);

                mPrepared = true;
                
                // 应用起始位置（修复进度读取）
                final long startPos = getStartPosition();
                if (startPos > 0 && !isStartPositionApplied()) {
                    Log.d(TAG, "apply startPosition: " + startPos);
                    mpv.command("seek", String.valueOf(startPos / 1000.0), "absolute");
                    markStartPositionApplied();
                }
                
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onPrepared();
                    }
                });

                // 如果是直播，延迟触发RENDERING_START
                Boolean isLive = (Boolean) android.content.SharedPreferences.Editor.getDefaultSharedPreferences(context).getBoolean("isLive", false);
                if (isLive) {
                    mainHandler.postDelayed(() -> {
                        if (mPlayerEventListener != null && mUserCalledStart) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                        }
                    }, 300);
                } else {
                    // 非直播，立即触发
                    if (mUserCalledStart) {
                        mainHandler.post(() -> {
                            if (mPlayerEventListener != null) {
                                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                            }
                        });
                    }
                }

            } else if (eventId == MPV_EVENT_VIDEO_RECONFIG) {
                // 视频尺寸确定
                Log.d(TAG, "VIDEO_RECONFIG w=" + mVideoWidth + " h=" + mVideoHeight);
                notifyVideoSizeIfReady();

            } else if (eventId == MPV_EVENT_PLAYBACK_RESTART) {
                Log.d(TAG, "PLAYBACK_RESTART");
                mSeekLock = false;
                notifyBufferingEnd();
                
                // 确保渲染开始通知
                if (!mUserCalledStart) {
                    mUserCalledStart = true;
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                        }
                    });
                }

            } else if (eventId == MPV_EVENT_SEEK) {
                Log.d(TAG, "SEEK");
                mSeekLock = true;
                notifyBufferingStart();

            } else if (eventId == MPV_EVENT_END_FILE) {
                Log.d(TAG, "END_FILE");
                mPrepared = false;
                mUserCalledStart = false;
                mPaused = false;
                mSeekLock = false;
                mSeekTarget = 0;
                mVideoSizeNotified = false;
                
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onCompletion();
                    }
                });
            }
        }
    };

    private void notifyVideoSizeIfReady() {
        if (!mVideoSizeNotified && mVideoWidth > 0 && mVideoHeight > 0) {
            mVideoSizeNotified = true;
            Log.d(TAG, "video size: " + mVideoWidth + "x" + mVideoHeight);
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }
            });
        }
    }

    /* ========================= Surface 时序修复（简化版） ========================= */
    @Override
    public void setSurface(Surface surface) {
        if (mpv != null) {
            if (surface != null) {
                mpv.attachSurface(surface);
                mSurfaceAttached = true;
                // 重新配置视频输出
                try {
                    mpv.command("vo-config");
                } catch (Exception e) {
                    Log.e(TAG, "vo-config error", e);
                }
            } else {
                mpv.detachSurface();
                mSurfaceAttached = false;
            }
        } else {
            mPendingSurface = surface;
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null) {
            setSurface(null);
        } else {
            setSurface(holder.getSurface());
        }
    }

    /* ========================= 生命周期 ========================= */

    public MpvMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void initPlayer() {
        if (mpv != null) {
            Log.d(TAG, "initPlayer: destroy old instance");
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }

        Log.d(TAG, "initPlayer: creating new instance");
        mpv = new MPV();
        mpv.create(context);
        
        // 优化渲染配置
        mpv.setOptionString("vo", "gpu");
        mpv.setOptionString("gpu-context", "android");
        mpv.setOptionString("ao", "audiotrack,opensles,null");
        mpv.setOptionString("audio-exclusive", "yes");
        mpv.setOptionString("hwdec", "auto-safe");
        mpv.setOptionString("keep-open", "yes");
        mpv.setOptionString("loop-file", "no");
        mpv.setOptionString("ytdl", "no");
        mpv.setOptionString("user-agent", UA);
        mpv.setOptionString("stream-lavf-o", "user_agent=" + UA);
        
        mpv.init();
        mpv.addObserver(observer);

        // 应用缓存的Surface
        if (mPendingSurface != null) {
            mpv.attachSurface(mPendingSurface);
            mSurfaceAttached = true;
            mPendingSurface = null;
        }

        // 重置状态
        mPrepared = false;
        mUserCalledStart = false;
        mPaused = false;
        mVideoSizeNotified = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mDuration = 0;
        mPosition = 0;
        mBuffering = false;
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        
        // 检查Surface是否已附加
        if (!mSurfaceAttached) {
            Log.w(TAG, "Surface not attached before setDataSource, may cause issues");
        }

        mPrepared = false;
        mUserCalledStart = false;
        mPaused = false;
        mVideoSizeNotified = false;
        mDuration = 0;
        mCacheEnd = 0;

        notifyBufferingStart();

        mpv.setOptionString("stream-lavf-o", "user_agent=" + UA);
        StringBuilder sb = new StringBuilder();
        sb.append("User-Agent: ").append(UA).append("\r\n");
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        sb.append("\r\n");
        mpv.setOptionString("http-header-fields", sb.toString());

        mpv.command("loadfile", path);
    }

    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("mpv: no AssetFileDescriptor");
    }

    public void start() {
        Log.d(TAG, "start called");
        mUserCalledStart = true;
        mPaused = false;
        if (mpv != null) {
            mpv.command("set", "pause", "no");
        }
        
        // 如果已经prepared但还没有发RENDERING_START，补发
        if (mPrepared) {
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                }
            });
        }
    }

    public void pause() {
        Log.d(TAG, "pause called");
        mPaused = true;
        if (mpv != null) mpv.command("set", "pause", "yes");
    }

    public void stop() {
        Log.d(TAG, "stop called");
        if (mpv != null) mpv.command("stop");
        mPrepared = false;
        mUserCalledStart = false;
        mPaused = false;
        mSeekLock = false;
        mSeekTarget = 0;
    }

    public void prepareAsync() {
        Log.d(TAG, "prepareAsync");
    }

    public void reset() {
        if (mpv != null) {
            mpv.command("stop");
            mpv.detachSurface();
            mSurfaceAttached = false;
        }
        mPrepared = false;
        mUserCalledStart = false;
        mPaused = false;
        mDuration = 0;
        mCacheEnd = 0;
        mSeekLock = false;
        mSeekTarget = 0;
        mVideoSizeNotified = false;
        mBuffering = false;
    }

    public boolean isPlaying() {
        return mUserCalledStart && !mPaused && mPrepared;
    }

    public void seekTo(long time) {
        Log.d(TAG, "seekTo: " + time);
        if (mpv != null) {
            mSeekLock = true;
            mSeekTarget = time;
            mPosition = time;
            notifyBufferingStart();
            mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
        }
    }

    public void release() {
        Log.d(TAG, "release");
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        mPrepared = false;
        mUserCalledStart = false;
        mPaused = false;
        mSeekLock = false;
        mSeekTarget = 0;
        mPendingSurface = null;
        mSurfaceAttached = false;
    }

    public void setVolume(float l, float r) {
        if (mpv != null) {
            mpv.command("set", "ao-volume", String.valueOf((int)((l + r) / 2 * 100)));
        }
    }

    public void setLooping(boolean loop) {
        if (mpv != null) {
            mpv.setOptionString("loop", loop ? "inf" : "no");
        }
    }

    public void setOptions() {}

    public void setSpeed(float speed) {
        if (mpv != null) {
            mpv.command("set", "speed", String.valueOf(speed));
        }
    }

    public float getSpeed() { return 1.0f; }

    public long getTcpSpeed() {
        return PlayerUtils.getNetSpeed(context);
    }

    public int getAudioSessionId() { return 0; }

    public long getDuration() { return mDuration; }

    public long getCurrentPosition() {
        if (mSeekLock) {
            return mSeekTarget;
        }
        return mPosition;
    }

    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) {
            return (int)Math.min(100, mCacheEnd * 1000 / mDuration);
        }
        return 0;
    }

    public void selectAudioTrack(int aid) { if (mpv != null) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null) mpv.command("set", "vid", String.valueOf(vid)); }
}
