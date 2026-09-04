package com.github.tvbox.osc.player;

import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import java.util.Map;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVLib;
import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * MPV Media Player for TVBox
 * 包名: is.xyz.mpv.MPV
 * 库: io.github.abdallahmehiz:mpv-android-lib:0.1.12
 */
public class MpvMediaPlayer extends AbstractPlayer implements MPVLib.EventObserver {

    private static final String TAG = "MpvMediaPlayer";

    // mpv event ids
    private static final int MPV_EVENT_START_FILE = 6;
    private static final int MPV_EVENT_END_FILE = 7;
    private static final int MPV_EVENT_FILE_LOADED = 8;
    private static final int MPV_EVENT_SEEK = 28;
    private static final int MPV_EVENT_PLAYBACK_RESTART = 29;

    private MPV mpv;
    private Surface mLastSurface;
    private boolean mSurfaceAttached = false;
    private boolean mPrepared = false;
    private boolean mPausedByUser = false;
    private boolean mReleased = false;
    private boolean mSeeking = false;
    private long mDuration = 0;
    private long mPosition = 0;
    private boolean mBuffering = false;

    // 内部状态，映射上层 STATE_*
    private int mInternalState = STATE_IDLE;

    public MpvMediaPlayer() {
    }

    // ===================== 状态管理 =====================

    private void setState(int state) {
        mInternalState = state;
    }

    private boolean isInState(int... states) {
        for (int s : states) {
            if (mInternalState == s) return true;
        }
        return false;
    }

    // ===================== 生命周期 =====================

    @Override
    public void initPlayer() {
        if (mReleased) {
            mReleased = false;
        }

        // 如果已有实例，先彻底销毁
        if (mpv != null) {
            try {
                mpv.detachSurface();
            } catch (Exception ignored) {}
            try {
                mpv.destroy();
            } catch (Exception ignored) {}
            mpv = null;
        }

        mpv = new MPV();
        mpv.create(null); // 传 null 用默认 config
        mpv.init();
        mpv.addObserver(this);

        // 基础选项
        mpv.setOptionString("vo", "gpu-next");
        mpv.setOptionString("gpu-context", "android");
        mpv.setOptionString("hwdec", "mediacodec");
        // ★ 关键：禁用 mediacodec surface callbacks，避免 Surface 重建时 assert 崩溃
        mpv.setOptionString("mediacodec-surface-callbacks", "no");
        mpv.setOptionString("ao", "audiotrack");
        mpv.setOptionString("pause", "yes");

        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        mSurfaceAttached = false;
        mBuffering = false;
        mDuration = 0;
        // ★ mPosition 不清零，保留给进度恢复
        setState(STATE_IDLE);
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        if (mpv == null || mReleased) return;

        // 如果已有 Surface，先 detach，避免 loadfile 时 mediacodec 绑到旧 Surface
        doDetachSurface();

        mPrepared = false;
        mSeeking = false;
        mBuffering = false;
        // ★ 不清 mPosition，保留上次进度
        setState(STATE_PREPARING);

        // 设置 start position（如果有）
        long startPos = getStartPosition();
        if (startPos > 0) {
            mpv.setOptionString("start", String.valueOf(startPos / 1000.0));
        }

        mpv.command("loadfile", path);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        // 不支持，忽略
    }

    @Override
    public void start() {
        if (mpv == null || mReleased) return;
        if (!mPrepared) return;

        mpv.setPause(false);
        mPausedByUser = false;
        setState(STATE_PLAYING);
    }

    @Override
    public void pause() {
        if (mpv == null || mReleased) return;
        if (!mPrepared) return;

        mpv.setPause(true);
        mPausedByUser = true;
        setState(STATE_PAUSED);
    }

    @Override
    public void stop() {
        if (mpv == null || mReleased) return;

        mpv.setPause(true);
        mpv.command("stop");
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        mBuffering = false;
        // ★ 不清 mPosition
        setState(STATE_IDLE);
    }

    @Override
    public void prepareAsync() {
        // loadfile 已经在 setDataSource 里做了，这里不需要额外操作
    }

    @Override
    public void reset() {
        if (mpv != null && !mReleased) {
            doDetachSurface();
            mpv.command("stop");
        }
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        mBuffering = false;
        // ★ 不清 mPosition
        setState(STATE_IDLE);
    }

    @Override
    public boolean isPlaying() {
        // 只有真正在播放状态才算 playing
        return mInternalState == STATE_PLAYING;
    }

    @Override
    public void seekTo(long time) {
        if (mpv == null || mReleased) return;
        if (!mPrepared) return;

        mSeeking = true;
        notifyBufferingStart();
        mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
    }

    @Override
    public void release() {
        mReleased = true;
        if (mpv != null) {
            try {
                doDetachSurface();
            } catch (Exception ignored) {}
            try {
                mpv.removeObserver(this);
            } catch (Exception ignored) {}
            try {
                mpv.destroy();
            } catch (Exception ignored) {}
            mpv = null;
        }
        mPrepared = false;
        mPausedByUser = false;
        mSeeking = false;
        mSurfaceAttached = false;
        mBuffering = false;
        mLastSurface = null;
        // ★ 不清 mPosition，让上层 saveProgress() 能读到
        setState(STATE_IDLE);
    }

    @Override
    public long getCurrentPosition() {
        // 返回缓存的位置，observer 在播放期间实时更新
        return mPosition;
    }

    @Override
    public long getDuration() {
        return mDuration;
    }

    @Override
    public int getBufferedPercentage() {
        return 0; // mpv 不提供简单的 buffered percentage
    }

    @Override
    public void setSurface(Surface surface) {
        if (mpv == null || mReleased) {
            mLastSurface = surface;
            return;
        }

        if (surface == null) {
            // ★ Surface 被销毁（后台/刷新），主动 detach + stop
            doDetachSurface();
            // 不 stop 播放，只 detach Surface，让 mpv 的 vo 释放引用
            // 前台回来后重新 setSurface 会重新 attach
            return;
        }

        // 同一个 Surface 且已 attach，不需要重复操作
        if (mSurfaceAttached && surface == mLastSurface) {
            return;
        }

        // 先 detach 旧的
        if (mSurfaceAttached) {
            doDetachSurface();
        }

        // attach 新的
        doAttachSurface(surface);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder != null) {
            setSurface(holder.getSurface());
        } else {
            setSurface(null);
        }
    }

    @Override
    public void setVolume(float v1, float v2) {
        if (mpv == null || mReleased) return;
        int vol = (int) (v1 * 100);
        mpv.setOptionString("ao-volume", String.valueOf(vol));
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mpv == null || mReleased) return;
        mpv.setOptionString("loop-file", isLooping ? "yes" : "no");
    }

    @Override
    public void setOptions() {
        // 由 initPlayer 统一设置
    }

    @Override
    public void setSpeed(float speed) {
        if (mpv == null || mReleased) return;
        mpv.setOptionString("speed", String.valueOf(speed));
    }

    @Override
    public float getSpeed() {
        return 1.0f;
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    // ===================== Surface 操作 =====================

    private void doAttachSurface(Surface surface) {
        if (mpv == null || mReleased || surface == null) return;
        try {
            mpv.attachSurface(surface);
            mLastSurface = surface;
            mSurfaceAttached = true;
        } catch (Exception e) {
            // ignore
        }
    }

    private void doDetachSurface() {
        if (mpv == null || mReleased) return;
        if (!mSurfaceAttached) return;
        try {
            mpv.detachSurface();
        } catch (Exception ignored) {}
        mSurfaceAttached = false;
        mLastSurface = null;
    }

    // ===================== Buffering 通知 =====================

    private void notifyBufferingStart() {
        if (mBuffering) return;
        mBuffering = true;
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
        }
    }

    private void notifyBufferingEnd() {
        if (!mBuffering) return;
        mBuffering = false;
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, 0);
        }
    }

    // ===================== mpv 事件回调 =====================

    @Override
    public void event(int eventId) {
        if (mReleased) return;

        switch (eventId) {
            case MPV_EVENT_START_FILE:
                mPrepared = false;
                mSeeking = false;
                mBuffering = false;
                setState(STATE_PREPARING);
                break;

            case MPV_EVENT_FILE_LOADED:
                // observe 属性
                try {
                    mpv.observeProperty("time-pos", MPVLib.FORMAT_INT64);
                    mpv.observeProperty("duration", MPVLib.FORMAT_INT64);
                    mpv.observeProperty("pause", MPVLib.FORMAT_FLAG);
                } catch (Exception ignored) {}

                if (!mPrepared) {
                    mPrepared = true;
                    setState(STATE_PREPARED);
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onPrepared();
                    }
                }
                // FILE_LOADED 后如果 Surface 已准备好，确保 attach
                if (mLastSurface != null && !mSurfaceAttached) {
                    doAttachSurface(mLastSurface);
                }
                // 应用 start position
                long startPos = getStartPosition();
                if (startPos > 0 && !isStartPositionApplied()) {
                    mpv.command("seek", String.valueOf(startPos / 1000.0), "absolute");
                    markStartPositionApplied();
                }
                break;

            case MPV_EVENT_END_FILE:
                // ★ 不清 mPosition，保留给进度恢复
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onCompletion();
                }
                mPrepared = false;
                mPausedByUser = false;
                mSeeking = false;
                mBuffering = false;
                setState(STATE_PLAYBACK_COMPLETED);
                break;

            case MPV_EVENT_SEEK:
                mSeeking = true;
                notifyBufferingStart();
                break;

            case MPV_EVENT_PLAYBACK_RESTART:
                // ★ 第一帧真正渲染出来
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

    @Override
    public void eventProperty(String property, long value) {
        if (mReleased) return;

        switch (property) {
            case "time-pos":
                mPosition = value * 1000;
                break;
            case "duration":
                if (value > 0) {
                    mDuration = value * 1000;
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onVideoSizeChanged(1920, 818); // mpv 不直接给尺寸，用 observer 的
                    }
                }
                break;
        }
    }

    @Override
    public void eventProperty(String property, boolean value) {
        if (mReleased) return;

        if ("pause".equals(property)) {
            if (value) {
                // mpv 内部暂停了
                if (!mSeeking && mPrepared) {
                    setState(STATE_PAUSED);
                }
            } else {
                // mpv 内部恢复了
                if (!mSeeking && mPrepared) {
                    setState(STATE_PLAYING);
                }
            }
        }
    }

    @Override
    public void eventProperty(String property, String value) {
        // 不需要处理
    }

    @Override
    public void eventProperty(String property, double value) {
        if (mReleased) return;

        if ("duration".equals(property)) {
            mDuration = (long)(value * 1000);
        }
    }
}
