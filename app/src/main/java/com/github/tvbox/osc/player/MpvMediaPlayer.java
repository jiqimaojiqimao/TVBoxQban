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

public class MpvMediaPlayer extends AbstractPlayer {

    private static final String TAG = "MPV";

    private static final int MPV_EVENT_START_FILE  = 6;
    private static final int MPV_EVENT_END_FILE    = 7;
    private static final int MPV_EVENT_FILE_LOADED = 8;

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
    private long mDuration = 0;
    private long mPosition = 0;
    private long mCacheEnd = 0;
    private boolean mPausedForCache = false;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    private final MPV.EventObserver observer = new MPV.EventObserver() {
        public void eventProperty(String property) {}
        public void eventProperty(String property, long value) {
            Log.d(TAG, "prop " + property + " = " + value);
            if ("duration".equals(property)) {
                mDuration = value * 1000;
                checkPrepared();
            } else if ("time-pos".equals(property)) {
                mPosition = value * 1000;
            } else if ("demuxer-cache-time".equals(property)) {
                mCacheEnd = value;
            }
        }
        public void eventProperty(String property, double value) {
            if ("duration".equals(property)) {
                mDuration = (long)(value * 1000);
                checkPrepared();
            } else if ("time-pos".equals(property)) {
                mPosition = (long)(value * 1000);
            }
        }
        public void eventProperty(String property, boolean value) {
            if ("paused-for-cache".equals(property)) {
                mPausedForCache = value;
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        if (value) mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                        else mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                    }
                });
            }
        }
        public void eventProperty(String property, String value) {
            Log.d(TAG, "prop " + property + " = " + value);
            if ("end-file-reason".equals(property) && "error".equals(value)) {
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) mPlayerEventListener.onError();
                });
            }
        }
        public void eventProperty(String property, MPVNode node) {}

        public void event(int eventId) { handleEvent(eventId); }
        public void event(int eventId, MPVNode node) { handleEvent(eventId); }

        private void handleEvent(int eventId) {
            Log.d(TAG, "event: " + eventId);
            if (mpv == null) return;
            if (eventId == MPV_EVENT_FILE_LOADED) {
                Log.d(TAG, "FILE_LOADED -> observe");
                mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
                mpv.observeProperty("duration", MPV_FORMAT_INT64);
                mpv.observeProperty("demuxer-cache-time", MPV_FORMAT_INT64);
                mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
                mpv.observeProperty("end-file-reason", MPV_FORMAT_STRING);

                // 尝试获取视频尺寸
                try {
                    Object w = mpv.getProperty("dwidth");
                    Object h = mpv.getProperty("dheight");
                    if (w instanceof Long && h instanceof Long) {
                        mVideoWidth = ((Long) w).intValue();
                        mVideoHeight = ((Long) h).intValue();
                        Log.d(TAG, "video size from property: " + mVideoWidth + "x" + mVideoHeight);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "get video size failed: " + e.getMessage());
                }

                // HLS 可能 duration=0，直接 prepared
                if (!mPrepared) {
                    if (mDuration <= 0) mDuration = 0;
                    mPrepared = true;
                    Log.d(TAG, "prepared by FILE_LOADED, duration=" + mDuration);
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onPrepared();
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                            // 通知视频尺寸
                            if (!mVideoSizeNotified && mVideoWidth > 0 && mVideoHeight > 0) {
                                mVideoSizeNotified = true;
                                mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                            }
                        }
                    });
                }

                // 确保开始播放（FILE_LOADED 后 mpv 可能内部 paused）
                mpv.command("set", "pause", "no");

            } else if (eventId == MPV_EVENT_END_FILE) {
                Log.d(TAG, "END_FILE");
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
                });
            }
        }

        private void checkPrepared() {
            if (!mPrepared && mDuration > 0) {
                mPrepared = true;
                Log.d(TAG, "prepared by duration=" + mDuration);
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onPrepared();
                        mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                    }
                });
            }
        }
    };

    public MpvMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void initPlayer() {
        // 先释放旧实例，防止重复创建
        if (mpv != null) {
            try { release(); } catch (Exception ignored) {}
        }
        mpv = new MPV();
        mpv.create(context);
        mpv.setOptionString("hwdec", "mediacodec-copy");
        mpv.setOptionString("ao", "audiotrack");
        mpv.setOptionString("keep-open", "yes");
        mpv.init();
        mpv.addObserver(observer);
        mPrepared = false;
        mVideoSizeNotified = false;
        mDuration = 0;
        mPosition = 0;
        mCacheEnd = 0;
        Log.d(TAG, "mpv initialized");
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        mPrepared = false;
        mVideoSizeNotified = false;
        mDuration = 0;
        mPosition = 0;
        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            mpv.setOptionString("http-header-fields", sb.toString());
        }
        mpv.command("loadfile", path);
    }

    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("mpv: no AssetFileDescriptor");
    }

    public void start() {
        Log.d(TAG, "start");
        if (mpv != null) mpv.command("set", "pause", "no");
    }

    public void pause() {
        Log.d(TAG, "pause");
        if (mpv != null) mpv.command("set", "pause", "yes");
    }

    public void stop() {
        Log.d(TAG, "stop");
        if (mpv != null) mpv.command("stop");
        mPrepared = false;
    }

    public void prepareAsync() {}

    public void reset() {
        if (mpv != null) mpv.command("stop");
        mPrepared = false; mDuration = 0; mPosition = 0; mCacheEnd = 0; mVideoSizeNotified = false;
    }

    public boolean isPlaying() { return mPrepared && !mPausedForCache; }

    public void seekTo(long time) {
        Log.d(TAG, "seekTo: " + time);
        if (mpv != null) mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
    }

    public void release() {
        Log.d(TAG, "release");
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.detachSurface(); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        mPrepared = false;
        mDuration = 0;
        mPosition = 0;
        mCacheEnd = 0;
        mVideoSizeNotified = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    public void setSurface(Surface surface) {
        if (mpv != null) mpv.attachSurface(surface);
    }

    public void setDisplay(SurfaceHolder holder) {
        setSurface(holder != null ? holder.getSurface() : null);
    }

    public void setVolume(float l, float r) {
        if (mpv != null) mpv.command("set", "ao-volume", String.valueOf((int)((l+r)/2 * 100)));
    }

    public void setLooping(boolean loop) {
        if (mpv != null) mpv.setOptionString("loop", loop ? "inf" : "no");
    }

    public void setOptions() {}

    public void setSpeed(float speed) {
        if (mpv != null) mpv.command("set", "speed", String.valueOf(speed));
    }

    public float getSpeed() { return 1.0f; }
    public long getTcpSpeed() { return 0; }
    public int getAudioSessionId() { return 0; }

    public long getDuration() { return mDuration; }
    public long getCurrentPosition() { return mPosition; }

    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) return (int)Math.min(100, mCacheEnd * 1000 / mDuration);
        return 0;
    }

    public void selectAudioTrack(int aid) { if (mpv != null) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null) mpv.command("set", "vid", String.valueOf(vid)); }
}
