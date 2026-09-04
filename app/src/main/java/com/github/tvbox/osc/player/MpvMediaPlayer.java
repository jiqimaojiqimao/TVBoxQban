package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.Map;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class MpvMediaPlayer extends AbstractPlayer {

    // ★ 自己定义状态常量
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
    private static final int STATE_ERROR = -1;

    private static final String TAG = "MpvMediaPlayer";

    private static final int MPV_EVENT_START_FILE = 6;
    private static final int MPV_EVENT_END_FILE = 7;
    private static final int MPV_EVENT_FILE_LOADED = 8;
    private static final int MPV_EVENT_SEEK = 28;
    private static final int MPV_EVENT_PLAYBACK_RESTART = 29;

    private static final int MPV_FORMAT_INT64 = 4;
    private static final int MPV_FORMAT_FLAG = 3;

    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
    }

    private MPV mpv;
    private Surface mLastSurface;
    private boolean mSurfaceAttached = false;
    private boolean mPrepared = false;
    private boolean mPausedByUser = false;
    private boolean mReleased = false;
    private boolean mSeeking = false;
    private long mDuration = 0;
    private long mPosition = 0;
    private int mState = STATE_IDLE;

    /* ========================= 构造 ========================= */

    public MpvMediaPlayer() {}

    public MpvMediaPlayer(Context context) {
        this();
    }

    /* ========================= 状态 ========================= */

    private void setState(int state) {
        mState = state;
    }

    /* ========================= 缓冲 ========================= */

    private void notifyBufferingStart() {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
        }
    }

    private void notifyBufferingEnd() {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, 0);
        }
    }

    /* ========================= Surface ========================= */

    private void doAttachSurface(Surface surface) {
        if (mpv == null || mReleased || surface == null) return;
        try {
            mpv.attachSurface(surface);
            mLastSurface = surface;
            mSurfaceAttached = true;
        } catch (Exception ignored) {}
    }

    private void doDetachSurface() {
        if (mpv == null || mReleased || !mSurfaceAttached) return;
        try {
            mpv.detachSurface();
        } catch (Exception ignored) {}
        mSurfaceAttached = false;
        mLastSurface = null;
    }

    /* ========================= Observer ========================= */

    private final MPV.EventObserver observer = new MPV.EventObserver() {
        public void eventProperty(String property) {}
        public void eventProperty(String property, long value) {
            if (mpv == null || mReleased) return;
            if ("time-pos".equals(property)) {
                mPosition = value * 1000;
            } else if ("duration".equals(property)) {
                mDuration = value * 1000;
            }
        }
        public void eventProperty(String property, double value) {
            if (mpv == null || mReleased) return;
            if ("duration".equals(property)) {
                mDuration = (long)(value * 1000);
            }
        }
        public void eventProperty(String property, boolean value) {
            if (mpv == null || mReleased) return;
            if ("paused-for-cache".equals(property)) {
                if (value) notifyBufferingStart();
                else notifyBufferingEnd();
            }
        }
        public void eventProperty(String property, String value) {}
        public void eventProperty(String property, MPVNode node) {}

        public void event(int eventId) { handleEvent(eventId); }
        public void event(int eventId, MPVNode node) { handleEvent(eventId); }

        private void handleEvent(int eventId) {
            if (mpv == null || mReleased) return;

            switch (eventId) {
                case MPV_EVENT_START_FILE:
                    mPrepared = false;
                    mSeeking = false;
                    setState(STATE_PREPARING);
                    break;

                case MPV_EVENT_FILE_LOADED:
                    try {
                        mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
                        mpv.observeProperty("duration", MPV_FORMAT_INT64);
                        mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
                    } catch (Exception ignored) {}

                    if (!mPrepared) {
                        mPrepared = true;
                        setState(STATE_PREPARED);
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onPrepared();
                        }
                    }

                    if (mLastSurface != null && !mSurfaceAttached) {
                        doAttachSurface(mLastSurface);
                    }

                    long startPos = getStartPosition();
                    if (startPos > 0 && !isStartPositionApplied()) {
                        mpv.command("seek", String.valueOf(startPos / 1000.0), "absolute");
                        markStartPositionApplied();
                    }
                    break;

                case MPV_EVENT_END_FILE:
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onCompletion();
                    }
                    mPrepared = false;
                    mPausedByUser = false;
                    mSeeking = false;
                    setState(STATE_PLAYBACK_COMPLETED);
                    break;

                case MPV_EVENT_SEEK:
                    mSeeking = true;
                    notifyBufferingStart();
                    break;

                case MPV_EVENT_PLAYBACK_RESTART:
                    if (!mSeeking) {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                        }
                    }
                    if (mSeeking) {
                        mSeeking = false;
                        notifyBufferingEnd();
                    }
                    if (mPrepared && !mPausedByUser) {
                        setState(STATE_PLAYING);
                    }
                    break;
            }
        }
    };

    /* ========================= 生命周期 ========================= */

    public void initPlayer() {
        if (mReleased) mReleased = false;

        if (mpv != null) {
            try { doDetachSurface(); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }

        mpv = new MPV();
        mpv.create(null);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("mediacodec-surface-callbacks", "no");
        mpv.setOptionString("ao", "audiotrack");
        mpv.setOptionString("keep-open", "yes");
        mpv.setOptionString("loop-file", "no");
        mpv.init();
        mpv.addObserver(observer);

        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        mSurfaceAttached = false;
        mDuration = 0;
        setState(STATE_IDLE);
    }

    public void setDataSource(String path, Map<String, String> headers) {
        if (mpv == null || mReleased) return;

        doDetachSurface();

        mPrepared = false;
        mSeeking = false;
        setState(STATE_PREPARING);

        long startPos = getStartPosition();
        if (startPos > 0) {
            mpv.setOptionString("start", String.valueOf(startPos / 1000.0));
        }

        mpv.command("loadfile", path);
    }

    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("MPV does not support AssetFileDescriptor");
    }

    public void start() {
        if (mpv == null || mReleased || !mPrepared) return;
        mpv.command("set", "pause", "no");
        mPausedByUser = false;
        setState(STATE_PLAYING);
    }

    public void pause() {
        if (mpv == null || mReleased || !mPrepared) return;
        mpv.command("set", "pause", "yes");
        mPausedByUser = true;
        setState(STATE_PAUSED);
    }

    public void stop() {
        if (mpv == null || mReleased) return;
        mpv.command("set", "pause", "yes");
        try { mpv.command("stop"); } catch (Exception ignored) {}
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        setState(STATE_IDLE);
    }

    public void prepareAsync() {}

    public void reset() {
        if (mpv != null && !mReleased) {
            doDetachSurface();
            try { mpv.command("stop"); } catch (Exception ignored) {}
        }
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        setState(STATE_IDLE);
    }

    public boolean isPlaying() {
        return mState == STATE_PLAYING;
    }

    public void seekTo(long time) {
        if (mpv == null || mReleased || !mPrepared) return;
        mSeeking = true;
        notifyBufferingStart();
        mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
    }

    public void release() {
        mReleased = true;
        if (mpv != null) {
            try { doDetachSurface(); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        mSurfaceAttached = false;
        mLastSurface = null;
        setState(STATE_IDLE);
    }

    public long getCurrentPosition() {
        return mPosition;
    }

    public long getDuration() {
        return mDuration;
    }

    public int getBufferedPercentage() {
        return 0;
    }

    public void setSurface(Surface surface) {
        if (mpv == null || mReleased) {
            mLastSurface = surface;
            return;
        }
        if (surface == null) {
            try { mpv.setOptionString("vo", "null"); } catch (Exception ignored) {}
            mSurfaceAttached = false;
            mLastSurface = null;
            return;
        }
        if (mSurfaceAttached && surface == mLastSurface) return;

        try { mpv.setOptionString("vo", "gpu-next"); } catch (Exception ignored) {}
        doAttachSurface(surface);
    }

    public void setDisplay(SurfaceHolder holder) {
        setSurface(holder != null ? holder.getSurface() : null);
    }

    public void setVolume(float v1, float v2) {
        if (mpv == null || mReleased) return;
        mpv.setOptionString("ao-volume", String.valueOf((int)(v1 * 100)));
    }

    public void setLooping(boolean isLooping) {
        if (mpv == null || mReleased) return;
        mpv.setOptionString("loop", isLooping ? "inf" : "no");
    }

    public void setOptions() {}

    public void setSpeed(float speed) {
        if (mpv == null || mReleased) return;
        mpv.setOptionString("speed", String.valueOf(speed));
    }

    public float getSpeed() { return 1.0f; }
    public long getTcpSpeed() { return 0; }
    public int getAudioSessionId() { return 0; }
}
