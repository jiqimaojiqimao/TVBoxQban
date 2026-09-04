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

    private static final int MPV_EVENT_FILE_LOADED      = 8;
    private static final int MPV_EVENT_END_FILE         = 7;
    private static final int MPV_EVENT_SEEK             = 20;
    private static final int MPV_EVENT_PLAYBACK_RESTART = 21;

    private static final int MPV_FORMAT_STRING = 1;
    private static final int MPV_FORMAT_FLAG   = 3;
    private static final int MPV_FORMAT_INT64  = 4;

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

    private boolean mPrepared = false;
    private boolean mVideoSizeNotified = false;
    private boolean mPausedByUser = false;
    private boolean mSeeking = false;
    private boolean mBufferingShown = false;
    private long mDuration = 0;
    private long mPosition = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private volatile boolean mReleasing = false;
    private volatile boolean mReleased = false;
    private boolean mSurfaceAttached = false;   // ★ 默认 false，只有确认 attach 成功才 true
    private Surface mLastSurface = null;
    private long mBufferingStartTime = 0;
    private boolean mIsLive = false;

    /* ========================= 缓冲节流 ========================= */

    private void notifyBufferingStart() {
        if (!mBufferingShown && mPlayerEventListener != null && !mReleased) {
            mBufferingStartTime = System.currentTimeMillis();
            mBufferingShown = true;
            mainHandler.post(() -> {
                if (mPlayerEventListener != null && !mReleased) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                }
            });
        }
    }

    private void notifyBufferingEnd() {
        if (mBufferingShown && mPlayerEventListener != null && !mReleased) {
            long bufferingDuration = System.currentTimeMillis() - mBufferingStartTime;
            if (bufferingDuration < 300) {
                Log.d(TAG, "buffering too short (" + bufferingDuration + "ms), ignoring");
                mBufferingShown = false;
                return;
            }
            if (bufferingDuration < 1500 && mPosition < 500) {
                Log.d(TAG, "buffering false alarm, ignoring");
                mBufferingShown = false;
                return;
            }
            mBufferingShown = false;
            mainHandler.post(() -> {
                if (mPlayerEventListener != null && !mReleased) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                }
            });
        }
    }

    /* ========================= Observer ========================= */

    private final MPV.EventObserver observer = new MPV.EventObserver() {
        public void eventProperty(String property) {}
        public void eventProperty(String property, long value) {
            if (mpv == null || mReleased) return;
            if ("duration".equals(property)) {
                mDuration = value * 1000;
            } else if ("time-pos".equals(property)) {
                if (!mPrepared) return;
                mPosition = value * 1000;
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
            if (mpv == null || mReleased) return;
            if ("duration".equals(property)) {
                mDuration = (long)(value * 1000);
            } else if ("time-pos".equals(property)) {
                if (!mPrepared) return;
                mPosition = (long)(value * 1000);
            }
        }
        public void eventProperty(String property, boolean value) {
            if (mpv == null || mReleased) return;
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
            if (mpv == null || mReleased) return;
            if ("end-file-reason".equals(property) && "error".equals(value)) {
                Log.e(TAG, "end-file-reason=error");
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null && !mReleased) {
                        mPlayerEventListener.onError();
                    }
                });
            }
        }
        public void eventProperty(String property, MPVNode node) {}

        public void event(int eventId) { handleEvent(eventId); }
        public void event(int eventId, MPVNode node) { handleEvent(eventId); }

        private void handleEvent(int eventId) {
            Log.d(TAG, "event: " + eventId);
            if (mpv == null || mReleased) return;

            if (eventId == MPV_EVENT_FILE_LOADED) {
                Log.d(TAG, "FILE_LOADED -> observe");
                mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
                mpv.observeProperty("duration", MPV_FORMAT_INT64);
                mpv.observeProperty("demuxer-cache-time", MPV_FORMAT_INT64);
                mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
                mpv.observeProperty("end-file-reason", MPV_FORMAT_STRING);
                mpv.observeProperty("dwidth", MPV_FORMAT_INT64);
                mpv.observeProperty("dheight", MPV_FORMAT_INT64);

                if (!mPrepared) {
                    mPrepared = true;
                    Log.d(TAG, "prepared by FILE_LOADED, duration=" + mDuration);
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null && !mReleased) {
                            mPlayerEventListener.onPrepared();
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                        }
                    });
                }

                notifyBufferingEnd();

                if (!mPausedByUser) {
                    mpv.command("set", "pause", "no");
                }

            } else if (eventId == MPV_EVENT_END_FILE) {
                Log.d(TAG, "END_FILE, isLive=" + mIsLive + ", surfaceAttached=" + mSurfaceAttached);
                mPrepared = false;
                mSeeking = false;
                if (mIsLive) {
                    Log.d(TAG, "live stream END_FILE ignored");
                } else if (mSurfaceAttached && mPlayerEventListener != null && !mReleased) {
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null && !mReleased) {
                            mPlayerEventListener.onCompletion();
                        }
                    });
                }

            } else if (eventId == MPV_EVENT_SEEK) {
                Log.d(TAG, "SEEK -> BUFFERING_START");
                mSeeking = true;
                notifyBufferingStart();

            } else if (eventId == MPV_EVENT_PLAYBACK_RESTART) {
                Log.d(TAG, "PLAYBACK_RESTART, mSeeking=" + mSeeking);
                if (mSeeking) {
                    mSeeking = false;
                    notifyBufferingEnd();
                }
            }
        }
    };

    private void notifyVideoSizeIfReady() {
        if (!mVideoSizeNotified && mVideoWidth > 0 && mVideoHeight > 0 && mPlayerEventListener != null && !mReleased) {
            mVideoSizeNotified = true;
            Log.d(TAG, "video size: " + mVideoWidth + "x" + mVideoHeight);
            mainHandler.post(() -> {
                if (mPlayerEventListener != null && !mReleased) {
                    mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }
            });
        }
    }

    /* ========================= Surface 管理（核心改动） ========================= */

    /**
     * ★ 彻底解绑当前 Surface，让 mediacodec 解码器释放对旧 Surface 的引用。
     * 必须在 loadfile 新流之前调用，否则旧解码器 surface 和新 surface 冲突 → 崩溃。
     */
    private void detachSurfaceInternal() {
        if (mpv != null) {
            try {
                Log.d(TAG, "detachSurfaceInternal");
                mpv.detachSurface();
            } catch (Exception e) {
                Log.w(TAG, "detachSurface failed", e);
            }
        }
        mLastSurface = null;
        mSurfaceAttached = false;
    }

    public void setSurface(Surface surface) {
        if (mpv == null || mReleased) {
            // ★ mpv 还没创建或已释放，先缓存 surface，等 initPlayer/setDataSource 时用
            mLastSurface = surface;
            mSurfaceAttached = (surface != null);
            return;
        }
        if (surface != null) {
            // ★ 同一个 surface 且已 attach，跳过（避免重复 attach 导致解码器重建）
            if (mSurfaceAttached && surface == mLastSurface) {
                return;
            }
            try {
                Log.d(TAG, "attachSurface: " + surface);
                mpv.attachSurface(surface);
                mLastSurface = surface;
                mSurfaceAttached = true;
            } catch (Exception e) {
                Log.e(TAG, "attachSurface failed", e);
                mSurfaceAttached = false;
            }
        } else {
            // ★ surface 为 null（如切换、重建），彻底解绑
            detachSurfaceInternal();
        }
    }

    public void setDisplay(SurfaceHolder holder) {
        setSurface(holder != null ? holder.getSurface() : null);
    }

    /* ========================= 生命周期 ========================= */

    public MpvMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void initPlayer() {
        if (mReleasing) {
            Log.d(TAG, "initPlayer: release in progress, skip");
            return;
        }

        if (mpv != null) {
            // ★ 复用实例：必须先解绑旧 Surface，再 stop，避免解码器残留 surface 引用
            Log.d(TAG, "initPlayer: reusing existing instance");
            detachSurfaceInternal();   // ← 关键：先解绑
            try { mpv.command("stop"); } catch (Exception ignored) {}
            // stop 之后再重新 attach 新 Surface（如果已经有了）
            if (mLastSurface != null) {
                try { mpv.attachSurface(mLastSurface); } catch (Exception ignored) {}
                mSurfaceAttached = true;
            }
        } else {
            Log.d(TAG, "initPlayer: creating new instance");
            mpv = new MPV();
            mpv.create(context);
            mpv.setOptionString("hwdec", "mediacodec");
            mpv.setOptionString("ao", "audiotrack");
            mpv.setOptionString("keep-open", "yes");
            mpv.setOptionString("loop-file", "no");
            mpv.init();
            mpv.addObserver(observer);

            // ★ 新建时也尝试 attach 已缓存的 Surface
            if (mLastSurface != null) {
                try { mpv.attachSurface(mLastSurface); } catch (Exception ignored) {}
                mSurfaceAttached = true;
            }
        }

        mReleased = false;
        mPrepared = false;
        mVideoSizeNotified = false;
        mPausedByUser = false;
        mSeeking = false;
        mBufferingShown = false;
        mBufferingStartTime = 0;
        Log.d(TAG, "mpv initialized, surfaceAttached=" + mSurfaceAttached);
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        mPrepared = false;
        mVideoSizeNotified = false;
        mPausedByUser = false;
        mSeeking = false;
        mBufferingShown = false;
        mDuration = 0;

        mIsLive = (path != null) && (path.contains("proxyM3u8") || path.contains("live") || path.contains(".m3u8"));

        notifyBufferingStart();

        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            mpv.setOptionString("http-header-fields", sb.toString());
        }

        // ★★★ 核心：loadfile 之前，如果已有 Surface attach，先解绑让解码器释放
        // 这样 loadfile 重新打开流时，会在 Surface attach 后再初始化 mediacodec
        // → 不会拿到 NULL 或冲突的 surface → 不崩溃
        if (mSurfaceAttached && mpv != null) {
            Log.d(TAG, "setDataSource: detaching surface before loadfile");
            detachSurfaceInternal();
        }

        mpv.command("loadfile", path);

        // ★ loadfile 后重新 attach Surface（确保解码器用新 surface 初始化）
        if (mLastSurface != null && mpv != null) {
            try {
                Log.d(TAG, "setDataSource: re-attaching surface after loadfile");
                mpv.attachSurface(mLastSurface);
                mSurfaceAttached = true;
            } catch (Exception e) {
                Log.e(TAG, "re-attachSurface failed", e);
            }
        }
    }

    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("mpv: no AssetFileDescriptor");
    }

    public void start() {
        Log.d(TAG, "start");
        mPausedByUser = false;
        if (mpv != null && !mReleased) mpv.command("set", "pause", "no");
    }

    public void pause() {
        Log.d(TAG, "pause");
        mPausedByUser = true;
        if (mpv != null && !mReleased) mpv.command("set", "pause", "yes");
    }

    public void stop() {
        Log.d(TAG, "stop");
        if (mpv != null && !mReleased) mpv.command("stop");
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
    }

    public void prepareAsync() {}

    public void reset() {
        if (mpv != null && !mReleased) mpv.command("stop");
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        mBufferingShown = false;
        mDuration = 0;
        mCacheEnd = 0;
        mVideoSizeNotified = false;
    }

    public boolean isPlaying() {
        return mPrepared && !mPausedByUser && !mReleased;
    }

    public void seekTo(long time) {
        Log.d(TAG, "seekTo: " + time);
        if (mpv != null && !mReleased) {
            notifyBufferingStart();
            mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
        }
    }

    public void release() {
        if (mReleasing) return;
        mReleasing = true;
        Log.d(TAG, "release (full destroy), lastPosition=" + mPosition);
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.detachSurface(); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        mReleased = true;
        mLastSurface = null;
        mSurfaceAttached = false;
        mReleasing = false;
    }

    public void setVolume(float l, float r) {
        if (mpv != null && !mReleased) mpv.command("set", "ao-volume", String.valueOf((int)((l + r) / 2 * 100)));
    }

    public void setLooping(boolean loop) {
        if (mpv != null && !mReleased) mpv.setOptionString("loop", loop ? "inf" : "no");
    }

    public void setOptions() {}

    public void setSpeed(float speed) {
        if (mpv != null && !mReleased) mpv.command("set", "speed", String.valueOf(speed));
    }

    public float getSpeed() { return 1.0f; }

    public long getTcpSpeed() {
        return PlayerUtils.getNetSpeed(context);
    }

    public int getAudioSessionId() { return 0; }

    public long getDuration() { return mDuration; }

    public long getCurrentPosition() {
        return mPosition;
    }

    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) return (int)Math.min(100, mCacheEnd * 1000 / mDuration);
        return 0;
    }

    public void selectAudioTrack(int aid) { if (mpv != null && !mReleased) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null && !mReleased) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null && !mReleased) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null && !mReleased) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null && !mReleased) mpv.command("set", "vid", String.valueOf(vid)); }
}
