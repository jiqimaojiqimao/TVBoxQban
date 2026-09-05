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

    private static final int MPV_EVENT_START_FILE        = 6;
    private static final int MPV_EVENT_END_FILE          = 7;
    private static final int MPV_EVENT_FILE_LOADED       = 8;
    private static final int MPV_EVENT_SEEK              = 20;
    private static final int MPV_EVENT_PLAYBACK_RESTART  = 21;

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

    // ★ 进度：全程保留，不被任何重置/END_FILE 清零，保证上层随时能读到真实进度
    private long mPosition = 0;

    private long mDuration = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    private boolean mPrepared = false;
    private boolean mVideoSizeNotified = false;
    private boolean mPausedByUser = false;
    private boolean mSeeking = false;
    private boolean mBufferingShown = false;
    private long mBufferingStartTime = 0;

    private volatile boolean mReleasing = false;
    private volatile boolean mReleased = true; // ★ 初始即 true，未创建时视为已释放

    // ★ Surface 管理：用一个显式的状态机，杜绝 aimagereader 拿已销毁 Surface
    private enum SurfaceState { DETACHED, ATTACHED }
    private SurfaceState mSurfaceState = SurfaceState.DETACHED;
    private Surface mPendingSurface = null; // mpv 未就绪时缓存的 Surface

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
            long dur = System.currentTimeMillis() - mBufferingStartTime;
            if (dur < 300) {
                // 极短抖动（切比例/重建）不显示
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
                // ★ END_FILE 后 mPrepared=false，此处不再更新，保留最后一次真实进度
                if (mPrepared) {
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
            if (mpv == null || mReleased) return;
            if ("duration".equals(property)) {
                mDuration = (long)(value * 1000);
            } else if ("time-pos".equals(property)) {
                if (mPrepared) {
                    mPosition = (long)(value * 1000);
                }
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
                Log.d(TAG, "FILE_LOADED");
                mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
                mpv.observeProperty("duration", MPV_FORMAT_INT64);
                mpv.observeProperty("demuxer-cache-time", MPV_FORMAT_INT64);
                mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
                mpv.observeProperty("end-file-reason", MPV_FORMAT_STRING);
                mpv.observeProperty("dwidth", MPV_FORMAT_INT64);
                mpv.observeProperty("dheight", MPV_FORMAT_INT64);

                // ★ 只在 FILE_LOADED 发 onPrepared（唯一入口，不提前发）
                if (!mPrepared) {
                    mPrepared = true;
                    Log.d(TAG, "prepared, duration=" + mDuration);
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null && !mReleased) {
                            mPlayerEventListener.onPrepared();
                        }
                    });
                }

                // ★ startPosition 在这里应用（onPrepared 之前 seek，避免多余缓冲状态）
                final long startPos = getStartPosition();
                if (startPos > 0 && !isStartPositionApplied()) {
                    Log.d(TAG, "apply startPosition: " + startPos);
                    mpv.command("seek", String.valueOf(startPos / 1000.0), "absolute");
                    markStartPositionApplied();
                }

                notifyBufferingEnd();

                if (!mPausedByUser) {
                    mpv.command("set", "pause", "no");
                }

            } else if (eventId == MPV_EVENT_PLAYBACK_RESTART) {
                Log.d(TAG, "PLAYBACK_RESTART, mSeeking=" + mSeeking);
                // ★★★ 真正的"第一帧渲染出来"信号，在此才发 RENDERING_START
                // ★★★ 这样上层收到时 isPlaying() 必然为 true，不会误显示暂停图标
                if (!mSeeking) {
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null && !mReleased) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                        }
                    });
                } else {
                    mSeeking = false;
                    notifyBufferingEnd();
                }
            } else if (eventId == MPV_EVENT_SEEK) {
                Log.d(TAG, "SEEK -> BUFFERING_START");
                mSeeking = true;
                notifyBufferingStart();
            } else if (eventId == MPV_EVENT_END_FILE) {
                Log.d(TAG, "END_FILE, isLive=" + mIsLive);
                mPrepared = false;
                mSeeking = false;
                // ★ mPosition 保留不清零；直播流不回调 completion
                if (!mIsLive && mPlayerEventListener != null && !mReleased) {
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null && !mReleased) {
                            mPlayerEventListener.onCompletion();
                        }
                    });
                }
            }
        }
    };

    private void notifyVideoSizeIfReady() {
        if (!mVideoSizeNotified && mVideoWidth > 0 && mVideoHeight > 0
                && mPlayerEventListener != null && !mReleased) {
            mVideoSizeNotified = true;
            Log.d(TAG, "video size: " + mVideoWidth + "x" + mVideoHeight);
            mainHandler.post(() -> {
                if (mPlayerEventListener != null && !mReleased) {
                    mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }
            });
        }
    }

    /* ========================= Surface 生命周期 ========================= */

    /**
     * ★★★ 核心：所有 Surface 操作都走这一个 attach 入口，保证 mpv 存在 + 状态正确
     * 后台→前台 / 刷新 时 Surface 重建，必须在这里和 mpv 的解码器正确同步。
     */
    private void doAttachSurface(Surface surface) {
        if (surface == null) return;
        if (mpv == null || mReleased) {
            // mpv 还没创建（initPlayer 之前 setDisplay 就来了），先缓存
            mPendingSurface = surface;
            Log.d(TAG, "doAttachSurface: mpv not ready, pending");
            return;
        }
        if (mSurfaceState == SurfaceState.ATTACHED) {
            // 已经 attach 的是同一个 Surface，跳过（重复 attach 会导致 aimagereader 异常）
            if (surface.equals(mPendingSurface)) {
                return;
            }
            // 换了新的 Surface，先 detach 旧的再 attach 新的
            try { mpv.detachSurface(); } catch (Exception ignored) {}
        }
        try {
            Log.d(TAG, "doAttachSurface: attachSurface");
            mpv.attachSurface(surface);
            mSurfaceState = SurfaceState.ATTACHED;
            mPendingSurface = surface;
        } catch (Exception e) {
            Log.e(TAG, "attachSurface failed", e);
            mSurfaceState = SurfaceState.DETACHED;
        }
    }

    /**
     * ★ 后台/销毁时调：彻底解绑，让 mediacodec / aimagereader 释放 Surface 引用。
     * 关键：不在 stop/loadfile 前后反复 attach/detach（那正是造成崩溃的原因）。
     */
    private void doDetachSurface() {
        if (mpv != null && !mReleased && mSurfaceState == SurfaceState.ATTACHED) {
            try {
                Log.d(TAG, "doDetachSurface");
                mpv.detachSurface();
            } catch (Exception ignored) {}
        }
        mSurfaceState = SurfaceState.DETACHED;
        // ★ 注意：保留 mPendingSurface，重建时可直接复用
    }

    @Override
    public void setSurface(Surface surface) {
        if (mpv != null) {
            mpv.setVideoSurface(surface);
            mLastSurface = surface;
            mSurfaceAttached = (surface != null);
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
        if (mReleasing) {
            Log.d(TAG, "initPlayer: release in progress, skip");
            return;
        }
        // ★ 不复用旧实例：旧的 mediacodec/aimagereader 一旦绑定过 Surface，
        // ★ 在 Surface 重建时极易出现 aimagereader acquireLatestImage 崩溃。
        // ★ 统一 destroy 旧的，创建全新实例，最稳。
        if (mpv != null) {
            Log.d(TAG, "initPlayer: destroy old instance before create new");
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.detachSurface(); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }

        Log.d(TAG, "initPlayer: creating new instance");
        mpv = new MPV();
        mpv.create(context);
        mpv.setOptionString("hwdec", "mediacodec");
        mpv.setOptionString("ao", "audiotrack");
        mpv.setOptionString("keep-open", "yes");
        mpv.setOptionString("loop-file", "no");
        // ★ 防止 mediacodec 占用 Surface 过久导致重建失败
        mpv.setOptionString("mediacodec-surface-callbacks", "no");
        mpv.init();
        mpv.addObserver(observer);

        mReleased = false;
        mPrepared = false;
        mVideoSizeNotified = false;
        mPausedByUser = false;
        mSeeking = false;
        mBufferingShown = false;
        mSurfaceState = SurfaceState.DETACHED;
        mBufferingStartTime = 0;

        // ★ 如果有缓存的 Surface（setDisplay 先于 initPlayer 到来），立即 attach
        if (mPendingSurface != null) {
            doAttachSurface(mPendingSurface);
        }
        Log.d(TAG, "mpv initialized, surfaceState=" + mSurfaceState);
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        mPrepared = false;
        mVideoSizeNotified = false;
        mPausedByUser = false;
        mSeeking = false;
        mBufferingShown = false;
        mDuration = 0;
        mCacheEnd = 0;
        // ★ mPosition 不清零 —— 保留上次播放进度供上层读取/恢复

        mIsLive = (path != null) && (path.contains("proxyM3u8") || path.contains("live") || path.contains(".m3u8"));

        notifyBufferingStart();

        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            mpv.setOptionString("http-header-fields", sb.toString());
        }

        // ★★★ 关键：loadfile 之前如果已有 Surface attach，先 detach
        // ★★★ 让 mediacodec/aimagereader 在打开新流前彻底释放旧 Surface 引用
        // ★★★ 这是修复"刷新/重播应用重启"的根本：aimagereader 不再对已销毁 Surface acquireLatestImage
        if (mSurfaceState == SurfaceState.ATTACHED) {
            Log.d(TAG, "setDataSource: detach surface before loadfile");
            doDetachSurface();
        }

        mpv.command("loadfile", path);

        // ★ loadfile 后重新 attach（用缓存的 Surface）
        // ★ 必须在主线程外也要保证 mpv 已 init；此处已在 initPlayer 之后，安全
        if (mPendingSurface != null) {
            doAttachSurface(mPendingSurface);
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
        // ★ 不清 mPosition
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
        // ★ 不清 mPosition / mPendingSurface
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
        // ★ 解绑 Surface，让 mediacodec/aimagereader 先释放，再 destroy mpv
        doDetachSurface();
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        mReleased = true;
        mPendingSurface = null;
        mSurfaceState = SurfaceState.DETACHED;
        mReleasing = false;
    }

    public void setVolume(float l, float r) {
        if (mpv != null && !mReleased) {
            mpv.command("set", "ao-volume", String.valueOf((int)((l + r) / 2 * 100)));
        }
    }

    public void setLooping(boolean loop) {
        if (mpv != null && !mReleased) {
            mpv.setOptionString("loop", loop ? "inf" : "no");
        }
    }

    public void setOptions() {}

    public void setSpeed(float speed) {
        if (mpv != null && !mReleased) {
            mpv.command("set", "speed", String.valueOf(speed));
        }
    }

    public float getSpeed() { return 1.0f; }

    public long getTcpSpeed() {
        return PlayerUtils.getNetSpeed(context);
    }

    public int getAudioSessionId() { return 0; }

    public long getDuration() { return mDuration; }

    /**
     * ★ 进度：mpv 存活时实时读，已释放则返回最后一次缓存的真实位置。
     * 上层（VideoView）在 release 前或 onPause 时读此值保存进度，永远不为 0。
     */
public long getCurrentPosition() {
        return mPosition;
    }

    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) {
            return (int)Math.min(100, mCacheEnd * 1000 / mDuration);
        }
        return 0;
    }

    public void selectAudioTrack(int aid) { if (mpv != null && !mReleased) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null && !mReleased) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null && !mReleased) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null && !mReleased) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null && !mReleased) mpv.command("set", "vid", String.valueOf(vid)); }
}
