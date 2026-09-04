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
    private boolean mSurfaceAttached = true;
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
                // ★ END_FILE 后 mpv 内部 time-pos 变 0，不再覆盖真实进度
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
                // ★ mPosition 保留，不被清 0
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
            Log.d(TAG, "initPlayer: reusing existing instance");
            try { mpv.command("stop"); } catch (Exception ignored) {}
            if (mLastSurface != null && mSurfaceAttached) {
                try { mpv.attachSurface(mLastSurface); } catch (Exception ignored) {}
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
        }

        mReleased = false;
        mPrepared = false;
        mVideoSizeNotified = false;
        mPausedByUser = false;
        mSeeking = false;
        mBufferingShown = false;
        mSurfaceAttached = true;
        mBufferingStartTime = 0;
        // ★ 不重置 mPosition / mDuration
        Log.d(TAG, "mpv initialized");
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        mPrepared = false;
        mVideoSizeNotified = false;
        mPausedByUser = false;
        mSeeking = false;
        mBufferingShown = false;
        mDuration = 0;
        // ★ mPosition 不清零

        mIsLive = (path != null) && (path.contains("proxyM3u8") || path.contains("live") || path.contains(".m3u8"));

        notifyBufferingStart();

        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            mpv.setOptionString("http-header-fields", sb.toString());
        }

        if (mLastSurface != null && mSurfaceAttached && mpv != null) {
            try { mpv.attachSurface(mLastSurface); } catch (Exception ignored) {}
        }

        mpv.command("loadfile", path);
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
        // ★ 不清 mPosition
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
        // ★ mPosition 已经在 observer 里实时更新着，release 时直接用缓存值
        // ★ 不需要再读 mpv.getInt()，直接保留 mPosition 即可
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.detachSurface(); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        mReleased = true;
        mLastSurface = null;
        mReleasing = false;
    }

    public void setSurface(Surface surface) {
        if (mpv == null || mReleased) return;
        if (surface != null) {
            if (surface != mLastSurface) {
                mpv.attachSurface(surface);
                mLastSurface = surface;
            }
            mSurfaceAttached = true;
        } else {
            mSurfaceAttached = false;
        }
    }

    public void setDisplay(SurfaceHolder holder) {
        setSurface(holder != null ? holder.getSurface() : null);
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
