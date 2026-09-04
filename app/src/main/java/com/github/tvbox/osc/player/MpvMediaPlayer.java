package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class MpvMediaPlayer extends AbstractPlayer {

    private static final String TAG = "MPV";

    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
    }

    private static final int FMT_STRING = 1;
    private static final int FMT_FLAG   = 3;
    private static final int FMT_INT64  = 4;

    private MPV mpv;
    private Context context;

    private boolean mPrepared = false;
    private long mDuration = 0;
    private long mPosition = 0;
    private long mCacheEnd = 0;
    private boolean mPausedForCache = false;

    private final MPV.EventObserver observer = new MPV.EventObserver() {
        public void eventProperty(String property) {}
        public void eventProperty(String property, long value) {
            Log.d(TAG, "property " + property + " = " + value);
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
                if (mPlayerEventListener != null) {
                    if (value) mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                    else mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                }
            }
        }
        public void eventProperty(String property, String value) {
            Log.d(TAG, "property " + property + " = " + value);
            if ("end-file-reason".equals(property) && "error".equals(value)) {
                if (mPlayerEventListener != null) mPlayerEventListener.onError();
            }
        }
        public void eventProperty(String property, MPVNode node) {}
        public void event(int eventId, MPVNode node) {}
        public void event(int eventId) {
            Log.d(TAG, "event: " + eventId);
            if (mpv == null) return;
            if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
                Log.d(TAG, "FILE_LOADED");
                mpv.observeProperty("time-pos", FMT_INT64);
                mpv.observeProperty("duration", FMT_INT64);
                mpv.observeProperty("demuxer-cache-time", FMT_INT64);
                mpv.observeProperty("paused-for-cache", FMT_FLAG);
                mpv.observeProperty("end-file-reason", FMT_STRING);
            } else if (eventId == MPV.mpvEvent.MPV_EVENT_END_FILE) {
                Log.d(TAG, "END_FILE");
                if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
            }
        }

        private void checkPrepared() {
            if (!mPrepared && mDuration > 0) {
                mPrepared = true;
                Log.d(TAG, "prepared, duration=" + mDuration);
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onPrepared();
                    mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                }
            }
        }
    };

    public MpvMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void initPlayer() {
        mpv = new MPV();
        mpv.create(context);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.init();
        mpv.addObserver(observer);
        Log.d(TAG, "mpv initialized");
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        mPrepared = false;
        mDuration = 0;
        mPosition = 0;
        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            mpv.setOptionString("http-header-fields", sb.toString());
        }
        int ret = mpv.command(new String[]{"loadfile", path});
        Log.d(TAG, "loadfile ret=" + ret);
    }

    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("mpv: no AssetFileDescriptor");
    }

    public void start() {
        Log.d(TAG, "start");
        mpv.command(new String[]{"set", "pause", "no"});
    }
    public void pause() {
        Log.d(TAG, "pause");
        mpv.command(new String[]{"set", "pause", "yes"});
    }
    public void stop() {
        Log.d(TAG, "stop");
        mpv.command(new String[]{"stop"});
        mPrepared = false;
    }
    public void prepareAsync() {}
    public void reset() {
        mpv.command(new String[]{"stop"});
        mPrepared = false; mDuration = 0; mPosition = 0; mCacheEnd = 0;
    }
    public boolean isPlaying() {
        return mPrepared && !mPausedForCache;
    }
    public void seekTo(long time) {
        Log.d(TAG, "seekTo: " + time);
        mpv.command(new String[]{"seek", String.valueOf(time / 1000.0), "absolute"});
    }

    public void release() {
        Log.d(TAG, "release");
        if (mpv != null) {
            mpv.command(new String[]{"stop"});
            mpv.removeObserver(observer);
            mpv.detachSurface();
            mpv.destroy();
            mpv = null;
        }
        mPrepared = false;
        mDuration = 0;
        mPosition = 0;
        mCacheEnd = 0;
    }

    public void setSurface(Surface surface) {
        Log.d(TAG, "setSurface");
        if (mpv != null) mpv.attachSurface(surface);
    }
    public void setDisplay(SurfaceHolder holder) { setSurface(holder != null ? holder.getSurface() : null); }
    public void setVolume(float l, float r) {
        mpv.command(new String[]{"set", "ao-volume", String.valueOf((int)((l+r)/2 * 100))});
    }
    public void setLooping(boolean loop) { mpv.setOptionString("loop", loop ? "inf" : "no"); }
    public void setOptions() {}
    public void setSpeed(float speed) { mpv.command(new String[]{"set", "speed", String.valueOf(speed)}); }
    public float getSpeed() { return 1.0f; }
    public long getTcpSpeed() { return 0; }
    public int getAudioSessionId() { return 0; }

    public long getDuration() { return mDuration; }
    public long getCurrentPosition() { return mPosition; }
    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) return (int)Math.min(100, mCacheEnd * 1000 / mDuration);
        return 0;
    }
}
