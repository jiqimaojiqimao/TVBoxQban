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
        SystemLibrary("avcodec");
        System.loadLibrary("avformat");
    }

    private MPV mpv;
    private Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ★ 进度
    private long mPosition = 0;
    private long mDuration = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    // ★ 状态标志
    private boolean mPrepared = false;
    private boolean mVideoSizeNotified = false;
    private boolean mPaused = false;

    // ★ 记忆播放位置
    private long mStartPosition = 0;

    // ★ UA 常量
    private static final String UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

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
                mPaused = false;
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
        mpv.setOptionString("user-agent", UA);
        // ★★★ 关键：把 UA 通过 stream-lavf-o 塞给 FFmpeg 的 http 协议层 ★★★
        mpv.setOptionString("stream-lavf-o", "user_agent=" + UA);
        mpv.setOptionString("mediacodec-surface-callbacks", "no");
        mpv.init();
        mpv.addObserver(observer);

        // ★ 重置所有状态
        mPrepared = false;
        mVideoSizeNotified = false;
        mPaused = false;
        mStartPosition = 0;
        Log.d(TAG, "mpv initialized, stream-lavf-o user_agent set");
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        mPrepared = false;
        mVideoSizeNotified = false;
        mPaused = false;
        mDuration = 0;
        mCacheEnd = 0;

        notifyBufferingStart();

        // ★★★ 每次 setDataSource 时重新设置 stream-lavf-o，确保 UA 生效 ★★★
        mpv.setOptionString("stream-lavf-o", "user_agent=" + UA);

        // 同时也设 http-header-fields（双重保险，对某些 mpv 版本有效）
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
        Log.d(TAG, "start");
        mPaused = false;
        if (mpv != null) mpv.command("set", "pause", "no");
    }

    public void pause() {
        Log.d(TAG, "pause");
        mPaused = true;
        if (mpv != null) mpv.command("set", "pause", "yes");
    }

    public void stop() {
        Log.d(TAG, "stop");
        if (mpv != null) mpv.command("stop");
        mPrepared = false;
        mPaused = false;
    }

    public void prepareAsync() {
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
        mDuration = 0;
        mCacheEnd = 0;
        mVideoSizeNotified = false;
    }

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
        mPrepared = false;
        mPaused = false;
        mStartPosition = 0;
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

    public void setStartPosition(long position) {
        mStartPosition = position;
    }

    public void selectAudioTrack(int aid) { if (mpv != null) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null) mpv.command("set", "vid", String.valueOf(vid)); }
}
