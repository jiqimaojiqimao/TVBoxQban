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

    // ★ 进度：全程保留，不被 reset / 错误 / 结束清零
    private long mPosition = 0;
    private long mDuration = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    // ★ 状态标志
    private boolean mPrepared = false;          // FILE_LOADED 后 true
    private boolean mVideoSizeNotified = false; // 避免重复回调 onVideoSizeChanged
    private boolean mPaused = false;            // 跟踪暂停状态，让 isPlaying() 与 VideoView 同步

    // ★ 记忆播放位置（VideoView 通过 setStartPosition 传入）
    private long mStartPosition = 0;
    private boolean mStartPositionApplied = false;

    /* ========================= 缓冲 ========================= */
    private void notifyBufferingStart() {
        if (mPlayerEventListener != null) {
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                }
            });
        }
    }

    private void notifyBufferingEnd() {
        if (mPlayerEventListener != null) {
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
            if ("duration".equals(property)) {
                mDuration = value * 1000;
            } else if ("time-pos".equals(property)) {
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
            if (mpv == null) return;
            if ("duration".equals(property)) {
                mDuration = (long)(value * 1000);
            } else if ("time-pos".equals(property)) {
                if (mPrepared) {
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
            Log.d(TAG, "event: " + eventId);
            if (mpv == null) return;

            if (eventId == 8 /* MPV_EVENT_FILE_LOADED */) {
                Log.d(TAG, "FILE_LOADED");
                mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
                mpv.observeProperty("duration", MPV_FORMAT_INT64);
                mpv.observeProperty("demuxer-cache-time", MPV_FORMAT_INT64);
                mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
                mpv.observeProperty("end-file-reason", MPV_FORMAT_STRING);
                mpv.observeProperty("dwidth", MPV_FORMAT_INT64);
                mpv.observeProperty("dheight", MPV_FORMAT_INT64);

                if (!mPrepared) {
                    mPrepared = true;
                    Log.d(TAG, "prepared, duration=" + mDuration);
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onPrepared();
                        }
                    });
                }

                // ★ 记忆播放位置：FILE_LOADED 后自动 seek（VideoView 的 onPrepared 也会再 seek 一次，幂等）
                if (mStartPosition > 0 && !mStartPositionApplied) {
                    mpv.command("seek", String.valueOf(mStartPosition / 1000.0), "absolute");
                    mStartPositionApplied = true;
                }

                notifyBufferingEnd();
                mpv.command("set", "pause", "no");

            } else if (eventId == 21 /* MPV_EVENT_PLAYBACK_RESTART */) {
                Log.d(TAG, "PLAYBACK_RESTART");
                notifyBufferingEnd();
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                    }
                });

            } else if (eventId == 20 /* MPV_EVENT_SEEK */) {
                Log.d(TAG, "SEEK -> BUFFERING_START");
                notifyBufferingStart();

            } else if (eventId == 7 /* MPV_EVENT_END_FILE */) {
                Log.d(TAG, "END_FILE");
                mPrepared = false;
                mPaused = false;          // ★ 结束播放时重置暂停态，错误/完成后再点播放可正常恢复
                mVideoSizeNotified = false;
                if (mPlayerEventListener != null) {
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onCompletion();
                        }
                    });
                }
            }
        }
    };

    private void notifyVideoSizeIfReady() {
        if (!mVideoSizeNotified && mVideoWidth > 0 && mVideoHeight > 0
                && mPlayerEventListener != null) {
            mVideoSizeNotified = true;
            Log.d(TAG, "video size: " + mVideoWidth + "x" + mVideoHeight);
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }
            });
        }
    }

    /* ========================= Surface ========================= */

    @Override
    public void setSurface(Surface surface) {
        if (mpv != null && surface != null) {
            mpv.attachSurface(surface);
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
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.setOptionString("keep-open", "yes");
        mpv.setOptionString("loop-file", "no");
        mpv.setOptionString("ytdl", "no");
        mpv.setOptionString("user-agent", "Mozilla/5.0 (Linux; Android)");
        mpv.setOptionString("mediacodec-surface-callbacks", "no");
        mpv.init();
        mpv.addObserver(observer);

        // ★ 重置所有状态
        mPrepared = false;
        mVideoSizeNotified = false;
        mPaused = false;
        mStartPosition = 0;
        mStartPositionApplied = false;
        Log.d(TAG, "mpv initialized");
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        mPrepared = false;
        mVideoSizeNotified = false;
        mPaused = false;
        mDuration = 0;
        mCacheEnd = 0;

        notifyBufferingStart();

        // ★ 强制注入 UA header，格式严格每行 \r\n 结尾，最后补一个空行
        StringBuilder sb = new StringBuilder();
        sb.append("User-Agent: Mozilla/5.0 (Linux; Android)\r\n");
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        sb.append("\r\n");

        // ★ 每次 setDataSource 前先清一次，避免上一次残留
        mpv.setOptionString("http-header-fields", "");
        mpv.setOptionString("http-header-fields", sb.toString());
        mpv.command("loadfile", path);
    }

    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("mpv: no AssetFileDescriptor");
    }

    public void start() {
        Log.d(TAG, "start");
        mPaused = false;   // ★ 与 VideoView.resume() 同步
        if (mpv != null) mpv.command("set", "pause", "no");
    }

    public void pause() {
        Log.d(TAG, "pause");
        mPaused = true;    // ★ 与 VideoView.pause() 同步
        if (mpv != null) mpv.command("set", "pause", "yes");
    }

    public void stop() {
        Log.d(TAG, "stop");
        if (mpv != null) mpv.command("stop");
        mPrepared = false;
        mPaused = false;
    }

    public void prepareAsync() {
        // mpv 的 loadfile 已在 setDataSource 中触发异步准备
        // VideoView 的状态流转由 onPrepared() 回调驱动，此处无需额外操作
        Log.d(TAG, "prepareAsync (mpv: loadfile already started)");
    }

    public void reset() {
        if (mpv != null) {
            mpv.command("stop");
            mpv.detachSurface();
        }
        mPrepared = false;
        mPaused = false;
        mStartPosition = 0;
        mStartPositionApplied = false;
        mDuration = 0;
        mCacheEnd = 0;
        mVideoSizeNotified = false;
    }

    /**
     * ★ 关键修复：跟踪暂停状态，让 VideoView 的
     *   resume() -> isInPlaybackState() && !isPlaying()
     * 判断链正确工作，不再出现“点播放永远暂停”。
     */
    public boolean isPlaying() {
        return mPrepared && !mPaused && mpv != null;
    }

    public void seekTo(long time) {
        Log.d(TAG, "seekTo: " + time);
        if (mpv != null) {
            notifyBufferingStart();
            mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
        }
    }

    public void release() {
        Log.d(TAG, "release (full destroy), lastPosition=" + mPosition);
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        // ★ 重置全部状态，错误恢复后可正常重建
        mPrepared = false;
        mPaused = false;
        mStartPosition = 0;
        mStartPositionApplied = false;
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

    public long getCurrentPosition() { return mPosition; }

    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) {
            return (int)Math.min(100, mCacheEnd * 1000 / mDuration);
        }
        return 0;
    }

    /* ========================= AbstractPlayer 扩展方法 ========================= */

    /**
     * 对齐 IjkPlayer/VideoView：设置起始播放位置，prepareAsync 前由 VideoView 调用。
     * 返回 false 表示不支持，VideoView 会跳过记忆 seek。
     */
    public void setStartPosition(long position) {
        mStartPosition = position;
        mStartPositionApplied = false;
    }

    /**
     * 对齐 VideoView.onPrepared 里的判断：记忆 seek 是否已应用。
     */
    public boolean isStartPositionApplied() {
        return mStartPositionApplied;
    }

    /* ========================= 轨道选择 ========================= */

    public void selectAudioTrack(int aid) { if (mpv != null) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null) mpv.command("set", "vid", String.valueOf(vid)); }
}
