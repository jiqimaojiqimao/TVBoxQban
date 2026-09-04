package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class MpvMediaPlayer extends AbstractPlayer {

    // FFmpeg .so 按依赖顺序加载（libmpv.so 由 MPV 类内部 loadLibrary("mpv","player") 加载）
    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
    }

    // mpv format 常量（对应 MPV.mpvFormat）
    private static final int FMT_NONE   = 0;
    private static final int FMT_STRING = 1;
    private static final int FMT_FLAG   = 3;
    private static final int FMT_INT64  = 4;
    private static final int FMT_DOUBLE = 5;

    private MPV mpv;
    private Context context;

    private boolean mPrepared = false;
    private long mDuration = 0;     // ms
    private long mPosition = 0;     // ms
    private long mCacheEnd = 0;     // s
    private boolean mPausedForCache = false;

    private final MPV.EventObserver observer = new MPV.EventObserver() {
        @Override
        public void eventProperty(String property) {}

        @Override
        public void eventProperty(String property, long value) {
            if ("duration".equals(property)) {
                mDuration = value * 1000;
                checkPrepared();
            } else if ("time-pos".equals(property)) {
                mPosition = value * 1000;
            } else if ("demuxer-cache-time".equals(property)) {
                mCacheEnd = value;
            }
        }

        @Override
        public void eventProperty(String property, double value) {
            if ("duration".equals(property)) {
                mDuration = (long) (value * 1000);
                checkPrepared();
            } else if ("time-pos".equals(property)) {
                mPosition = (long) (value * 1000);
            }
        }

        @Override
        public void eventProperty(String property, boolean value) {
            if ("paused-for-cache".equals(property)) {
                mPausedForCache = value;
                if (mPlayerEventListener != null) {
                    if (value) mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                    else mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                }
            }
        }

        @Override
        public void eventProperty(String property, String value) {
            if ("end-file-reason".equals(property) && "error".equals(value)) {
                if (mPlayerEventListener != null) mPlayerEventListener.onError();
            }
        }

        // 0.1.12 新增的 node 回调，必须实现
        @Override
        public void event(int eventId, MPVNode node) {
            // track-list / chapter-list 等复杂属性走这里，暂不解析
        }

        @Override
        public void event(int eventId) {
            if (mpv == null) return;
            if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
                mpv.observeProperty("time-pos", FMT_INT64);
                mpv.observeProperty("duration", FMT_INT64);
                mpv.observeProperty("demuxer-cache-time", FMT_INT64);
                mpv.observeProperty("paused-for-cache", FMT_FLAG);
                mpv.observeProperty("end-file-reason", FMT_STRING);
                mpv.observeProperty("track-list", FMT_STRING);
            } else if (eventId == MPV.mpvEvent.MPV_EVENT_END_FILE) {
                if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
            } else if (eventId == MPV.mpvEvent.MPV_EVENT_SHUTDOWN) {
                // no-op
            }
        }

        private void checkPrepared() {
            if (!mPrepared && mDuration > 0) {
                mPrepared = true;
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

    @Override
    public void initPlayer() {
        mpv = new MPV();
        mpv.create(context);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.init();
        mpv.addObserver(observer);
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
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
        mpv.command("loadfile", path);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("mpv: no AssetFileDescriptor");
    }

    @Override
    public void start() { mpv.command("set", "pause", "no"); }

    @Override
    public void pause() { mpv.command("set", "pause", "yes"); }

    @Override
    public void stop() { mpv.command("stop"); mPrepared = false; }

    @Override
    public void prepareAsync() { /* loadfile 后自动 prepare */ }

    @Override
    public void reset() { mpv.command("stop"); mPrepared = false; mDuration = 0; mPosition = 0; mCacheEnd = 0; }

    @Override
    public boolean isPlaying() { return mPrepared && !mPausedForCache; }

    @Override
    public void seekTo(long time) { mpv.command("seek", String.valueOf(time / 1000.0), "absolute"); }

    @Override
    public void release() {
        if (mpv != null) {
            mpv.command("stop");
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

    @Override
    public void setSurface(Surface surface) { if (mpv != null) mpv.attachSurface(surface); }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder != null) setSurface(holder.getSurface());
        else setSurface(null);
    }

    @Override
    public void setVolume(float l, float r) {
        int vol = (int) ((l + r) / 2 * 100);
        mpv.command("set", "ao-volume", String.valueOf(vol));
    }

    @Override
    public void setLooping(boolean loop) { mpv.setOptionString("loop", loop ? "inf" : "no"); }

    @Override
    public void setOptions() {}

    @Override
    public void setSpeed(float speed) { mpv.command("set", "speed", String.valueOf(speed)); }

    @Override
    public float getSpeed() {
        Double s = mpv.getPropertyDouble("speed");
        return s != null ? s.floatValue() : 1.0f;
    }

    @Override
    public long getTcpSpeed() { return 0; }

    @Override
    public int getAudioSessionId() { return 0; }

    @Override
    public long getDuration() { return mDuration; }

    @Override
    public long getCurrentPosition() { return mPosition; }

    @Override
    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) {
            return (int) Math.min(100, mCacheEnd * 1000 / mDuration);
        }
        return 0;
    }

    // ---- 音轨/字幕/视轨 ----
    public void selectAudioTrack(int aid) { if (mpv != null) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null) mpv.command("set", "vid", String.valueOf(vid)); }
}
