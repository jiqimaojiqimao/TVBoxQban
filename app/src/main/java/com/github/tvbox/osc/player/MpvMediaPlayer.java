package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;

import is.xyz.mpv.MPV;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class MpvMediaPlayer extends AbstractPlayer {

    // ========== FFmpeg .so 按依赖顺序加载 ==========
    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
        // libmpv.so 由 MPV.<clinit>() 内部的 System.loadLibrary("mpv") 加载
    }

    private MPV mpv;
    private Context context;

    // ========== 播放状态缓存 ==========
    private boolean mPrepared = false;
    private long mDuration = 0;       // 毫秒
    private long mPosition = 0;       // 毫秒
    private long mCacheEndTime = 0;   // 秒，用于估算缓冲百分比
    private boolean mPausedForCache = false;

    // ========== mpv 事件观察者 ==========
    private final MPV.EventObserver observer = new MPV.EventObserver() {

        @Override
        public void eventProperty(String property, boolean value) {
            switch (property) {
                case "paused-for-cache":
                    mPausedForCache = value;
                    if (mPlayerEventListener != null) {
                        if (value) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                        } else {
                            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                        }
                    }
                    break;
            }
        }

        @Override
        public void eventProperty(String property, long value) {
            switch (property) {
                case "duration":
                    mDuration = value * 1000; // mpv 返回秒 → 毫秒
                    checkPrepared();
                    break;
                case "time-pos":
                    mPosition = value * 1000;
                    break;
                case "demuxer-cache-time":
                    mCacheEndTime = value;
                    break;
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
        public void eventProperty(String property, String value) {
            // 外挂字幕加载、track 变化等
        }

        @Override
        public void eventProperty(String property) {
            // 属性被删除
        }

        @Override
        public void event(int eventId) {
            switch (eventId) {
                case MPV.EVENT_FILE_LOADED:
                    // 文件加载完成，注册要观察的属性
                    mpv.observeProperty("time-pos", MPV.FORMAT_INT64);
                    mpv.observeProperty("duration", MPV.FORMAT_INT64);
                    mpv.observeProperty("demuxer-cache-time", MPV.FORMAT_INT64);
                    mpv.observeProperty("paused-for-cache", MPV.FORMAT_FLAG);
                    mpv.observeProperty("track-list", MPV.FORMAT_STRING);
                    mpv.observeProperty("end-file-reason", MPV.FORMAT_STRING);
                    break;

                case MPV.EVENT_END_FILE:
                    // end-file-reason 会通过 eventProperty(String, String) 回调
                    break;

                case MPV.EVENT_SHUTDOWN:
                    break;
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

    // ========== 构造 / 初始化 ==========

    public MpvMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
        mpv = new MPV();
    }

    @Override
    public void initPlayer() {
        mpv.create(context);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.init();
        mpv.addObserver(observer);
    }

    // ========== 数据源 ==========

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
        throw new UnsupportedOperationException("mpv does not support AssetFileDescriptor");
    }

    // ========== 播放控制 ==========

    @Override
    public void start() {
        mpv.command("set", "pause", "no");
    }

    @Override
    public void pause() {
        mpv.command("set", "pause", "yes");
    }

    @Override
    public void stop() {
        mpv.command("stop");
        mPrepared = false;
    }

    @Override
    public void prepareAsync() {
        // mpv 在 loadfile 后自动 prepare，不需要手动调用
    }

    @Override
    public void reset() {
        mpv.command("stop");
        mPrepared = false;
        mDuration = 0;
        mPosition = 0;
        mCacheEndTime = 0;
    }

    @Override
    public boolean isPlaying() {
        // 通过 paused-for-cache 和 mpv 内部状态判断
        // 简化：检查 mpv 是否在播放
        return mPrepared && !mPausedForCache;
    }

    @Override
    public void seekTo(long time) {
        mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
    }

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
        mCacheEndTime = 0;
    }

    // ========== Surface ==========

    @Override
    public void setSurface(Surface surface) {
        if (mpv != null) {
            mpv.attachSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder != null) {
            setSurface(holder.getSurface());
        } else {
            setSurface(null);
        }
    }

    // ========== 音量 / 循环 / 速度 ==========

    @Override
    public void setVolume(float v1, float v2) {
        int vol = (int) ((v1 + v2) / 2 * 100);
        mpv.command("set", "ao-volume", String.valueOf(vol));
    }

    @Override
    public void setLooping(boolean isLooping) {
        mpv.setOptionString("loop", isLooping ? "inf" : "no");
    }

    @Override
    public void setOptions() {
        // 留给外部设置额外选项
    }

    @Override
    public void setSpeed(float speed) {
        mpv.command("set", "speed", String.valueOf(speed));
    }

    @Override
    public float getSpeed() {
        // mpv 没有简单的 getProperty，返回缓存值或默认 1.0f
        return 1.0f;
    }

    @Override
    public long getTcpSpeed() {
        return 0; // mpv 没有直接的 TCP 速度 API
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    // ========== 进度 / 时长 / 缓冲 ==========

    @Override
    public long getDuration() {
        return mDuration;
    }

    @Override
    public long getCurrentPosition() {
        return mPosition;
    }

    @Override
    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEndTime > 0) {
            int percent = (int) (mCacheEndTime * 1000 / mDuration);
            return Math.min(percent, 100);
        }
        return 0;
    }

    // ========== 音轨 / 字幕 / 视轨切换（预留接口） ==========

    /**
     * 切换音轨（aid 为 mpv track-list 中的 id）
     */
    public void selectAudioTrack(int aid) {
        if (mpv != null) {
            mpv.command("set", "aid", String.valueOf(aid));
        }
    }

    /**
     * 切换字幕轨（sid 为 mpv track-list 中的 id）
     */
    public void selectSubtitleTrack(int sid) {
        if (mpv != null) {
            mpv.command("set", "sid", String.valueOf(sid));
        }
    }

    /**
     * 加载外挂字幕文件
     */
    public void addSubtitleFile(String path) {
        if (mpv != null) {
            mpv.command("sub-add", path);
        }
    }

    /**
     * 关闭字幕
     */
    public void disableSubtitle() {
        if (mpv != null) {
            mpv.command("set", "sid", "no");
        }
    }

    /**
     * 切换视频轨（vid 为 mpv track-list 中的 id）
     */
    public void selectVideoTrack(int vid) {
        if (mpv != null) {
            mpv.command("set", "vid", String.valueOf(vid));
        }
    }

    /**
     * 获取当前 track-list（JSON 字符串，需自行解析）
     */
    public void requestTrackList() {
        if (mpv != null) {
            mpv.observeProperty("track-list", MPV.FORMAT_STRING);
        }
    }
}
