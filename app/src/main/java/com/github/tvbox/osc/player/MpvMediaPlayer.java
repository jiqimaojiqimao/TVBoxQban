package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.net.URL;
import java.util.Map;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class MpvMediaPlayer extends AbstractPlayer {

    private static final String TAG = "MPV";

    private static final int MPV_FORMAT_STRING = 1;
    private static final int MPV_FORMAT_FLAG = 3;
    private static final int MPV_FORMAT_INT64 = 4;

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

    // ★ 进度
    private long mPosition = 0;
    private long mDuration = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    // ★ 状态标志
    private volatile boolean mPrepared = false;
    private volatile boolean mPaused = false;

    // ★ UA（模拟 Chrome，兼容国内源）
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // ★ seek 锁定
    private volatile boolean mSeekLock = false;
    private long mSeekTarget = 0;

    // ★ 播放通知
    private volatile boolean mShouldNotifyPlaying = false;
    private boolean mPlayingNotified = false;

    /* ========================= 缓冲 ========================= */
    private void notifyBufferingStart() {
        mainHandler.post(() -> {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
            }
        });
    }

    private void notifyBufferingEnd() {
        mainHandler.post(() -> {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
            }
        });
    }

    /* ========================= Observer ========================= */
    private final MPV.EventObserver observer = new MPV.EventObserver() {

        public void eventProperty(String property) {}

        public void eventProperty(String property, long value) {
            if (mpv == null) return;
            switch (property) {
                case "duration":
                    mDuration = value * 1000;
                    break;
                case "time-pos":
                    mShouldNotifyPlaying = true;
                    mPlayingNotified = false;
                    checkAndNotifyPlaying();
                    if (!mSeekLock) mPosition = value * 1000;
                    break;
                case "demuxer-cache-time":
                    mCacheEnd = value;
                    break;
                case "dwidth":
                    mVideoWidth = (int) value;
                    notifyVideoSizeIfReady();
                    break;
                case "dheight":
                    mVideoHeight = (int) value;
                    notifyVideoSizeIfReady();
                    break;
            }
        }

        public void eventProperty(String property, double value) {
            if (mpv == null) return;
            if ("duration".equals(property)) {
                mDuration = (long) (value * 1000);
            } else if ("time-pos".equals(property)) {
                mShouldNotifyPlaying = true;
                mPlayingNotified = false;
                checkAndNotifyPlaying();
                if (!mSeekLock) mPosition = (long) (value * 1000);
            }
        }

        public void eventProperty(String property, boolean value) {
            if (mpv == null) return;
            if ("paused-for-cache".equals(property)) {
                Log.d(TAG, "paused-for-cache=" + value);
                if (value) notifyBufferingStart();
                else notifyBufferingEnd();
            }
        }

        public void eventProperty(String property, String value) {
            if (mpv == null) return;
            if ("end-file-reason".equals(property)) {
                Log.e(TAG, "end-file-reason=" + value);
                if ("error".equals(value)) {
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onError();
                        }
                    });
                }
            }
        }

        public void eventProperty(String property, MPVNode node) {}

        public void event(int eventId) {
            handleEvent(eventId);
        }

        public void event(int eventId, MPVNode node) {
            handleEvent(eventId);
        }

        private void handleEvent(int eventId) {
            Log.d(TAG, "event: " + eventId);
            if (mpv == null) return;

            switch (eventId) {
                case 8: // MPV_EVENT_FILE_LOADED
                    Log.d(TAG, "FILE_LOADED");
                    notifyVideoSizeIfReady();
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onPrepared();
                        }
                    });
                    break;

                case 21: // MPV_EVENT_PLAYBACK_RESTART
                    Log.d(TAG, "PLAYBACK_RESTART");
                    mSeekLock = false;
                    notifyBufferingEnd();
                    break;

                case 20: // MPV_EVENT_SEEK
                    Log.d(TAG, "SEEK -> BUFFERING_START");
                    notifyBufferingStart();
                    break;

                case 7: // MPV_EVENT_END_FILE
                    Log.d(TAG, "END_FILE");
                    mPrepared = false;
                    mPaused = false;
                    mSeekLock = false;
                    mSeekTarget = 0;
                    mShouldNotifyPlaying = false;
                    mPlayingNotified = false;
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onCompletion();
                        }
                    });
                    break;
            }
        }
    };

    private void checkAndNotifyPlaying() {
        if (!mShouldNotifyPlaying || mPlayingNotified) return;
        mPrepared = true;
        mPlayingNotified = true;
        notifyVideoSizeIfReady();
        mainHandler.post(() -> {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
            }
        });
    }

    private void notifyVideoSizeIfReady() {
        if (mVideoWidth > 0 && mVideoHeight > 0 && mPlayerEventListener != null) {
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
        if (surface != null && mpv != null) {
            mpv.attachSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null) setSurface(null);
        else setSurface(holder.getSurface());
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

        mpv = new MPV();
        mpv.create(context);

        // 解码 / 音频
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.setOptionString("keep-open", "yes");
        mpv.setOptionString("loop-file", "no");
        mpv.setOptionString("ytdl", "no");

        // ★ TLS 容错（国内 HTTPS 源很重要）
        mpv.setOptionString("tls-verify", "no");

        // ★ 缓冲优化
        mpv.setOptionString("cache-pause", "yes");
        mpv.setOptionString("cache-pause-wait", "5");
        mpv.setOptionString("cache-secs", "30");
        mpv.setOptionString("demuxer-max-bytes", "50M");

        mpv.init();
        mpv.addObserver(observer);

        // 属性监听
        mpv.observeProperty("dwidth", MPV_FORMAT_INT64);
        mpv.observeProperty("dheight", MPV_FORMAT_INT64);
        mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
        mpv.observeProperty("duration", MPV_FORMAT_INT64);
        mpv.observeProperty("demuxer-cache-time", MPV_FORMAT_INT64);
        mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
        mpv.observeProperty("end-file-reason", MPV_FORMAT_STRING);

        mPrepared = false;
        mPaused = false;
        mShouldNotifyPlaying = false;
        mPlayingNotified = false;
    }

    /* ========================= 关键：DataSource ========================= */
    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);

        mPrepared = false;
        mPaused = false;
        mDuration = 0;
        mCacheEnd = 0;
        mShouldNotifyPlaying = false;
        mPlayingNotified = false;

        // ★ 自动提取 Referer / Origin
        String referer = extractReferer(path);
        String origin = extractOrigin(path);

        // ---- http-header-fields ----
        StringBuilder sb = new StringBuilder();
        sb.append("User-Agent: ").append(UA).append("\r\n");
        if (!TextUtils.isEmpty(referer)) {
            sb.append("Referer: ").append(referer).append("\r\n");
        }
        if (!TextUtils.isEmpty(origin)) {
            sb.append("Origin: ").append(origin).append("\r\n");
        }
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        sb.append("\r\n");
        mpv.setOptionString("http-header-fields", sb.toString());

        // ---- lavf-o 兜底（非常重要）----
        StringBuilder lavfOpts = new StringBuilder();
        lavfOpts.append("user_agent=").append(UA.replace(",", "\\,"));
        if (!TextUtils.isEmpty(referer)) {
            lavfOpts.append(",referrer=").append(referer.replace(",", "\\,"));
        }
        mpv.setOptionString("demuxer-lavf-o", lavfOpts.toString());

        // ★ HLS 专用参数（参考 Media3 版）
        if (isLikelyHls(path)) {
            mpv.setOptionString("demuxer-lavf-format", "hls");
            mpv.setOptionString("demuxer-lavf-probesize", "10485760");
            mpv.setOptionString("demuxer-lavf-analyzeduration", "5");
        }

        mpv.command("loadfile", path);
    }

    /** 判断是否是 HLS */
    private boolean isLikelyHls(String path) {
        return path != null && (path.contains(".m3u8") || path.contains("m3u"));
    }

    /** 从 URL 提取 Referer */
    private String extractReferer(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return "";
        }
    }

    /** 从 URL 提取 Origin */
    private String extractOrigin(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return "";
        }
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
        mSeekLock = false;
        mSeekTarget = 0;
        mShouldNotifyPlaying = false;
        mPlayingNotified = false;
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
        mDuration = 0;
        mCacheEnd = 0;
        mSeekLock = false;
        mSeekTarget = 0;
        mShouldNotifyPlaying = false;
        mPlayingNotified = false;
    }

    public boolean isPlaying() {
        return mPrepared && !mPaused;
    }

    public void seekTo(long time) {
        Log.d(TAG, "seekTo: " + time);
        if (mpv != null) {
            mSeekLock = true;
            mSeekTarget = time;
            mPosition = time;
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
        mSeekLock = false;
        mSeekTarget = 0;
        mShouldNotifyPlaying = false;
        mPlayingNotified = false;
    }

    public void setVolume(float l, float r) {
        if (mpv != null) {
            mpv.command("set", "ao-volume", String.valueOf((int) ((l + r) / 2 * 100)));
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

    public long getCurrentPosition() {
        return mSeekLock ? mSeekTarget : mPosition;
    }

    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) {
            return (int) Math.min(100, mCacheEnd * 1000 / mDuration);
        }
        return 0;
    }

    public void selectAudioTrack(int aid) {
        if (mpv != null) mpv.command("set", "aid", String.valueOf(aid));
    }

    public void selectSubtitleTrack(int sid) {
        if (mpv != null) mpv.command("set", "sid", String.valueOf(sid));
    }

    public void disableSubtitle() {
        if (mpv != null) mpv.command("set", "sid", "no");
    }

    public void addSubtitleFile(String p) {
        if (mpv != null) mpv.command("sub-add", p);
    }

    public void selectVideoTrack(int vid) {
        if (mpv != null) mpv.command("set", "vid", String.valueOf(vid));
    }
}
