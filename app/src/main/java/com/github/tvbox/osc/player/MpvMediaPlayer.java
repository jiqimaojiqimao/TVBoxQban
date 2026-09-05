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

    // MPV Format Constants
    private static final int MPV_FORMAT_STRING = 1;
    private static final int MPV_FORMAT_FLAG   = 3;
    private static final int MPV_FORMAT_INT64  = 4;

    // MPV Event IDs
    private static final int MPV_EVENT_FILE_LOADED = 8;
    private static final int MPV_EVENT_END_FILE = 7;
    private static final int MPV_EVENT_SEEK = 20;
    private static final int MPV_EVENT_PLAYBACK_RESTART = 21;
    private static final int MPV_EVENT_VIDEO_RECONFIG = 23;

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

    // ★ 进度与状态
    private long mPosition = 0;
    private long mDuration = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    // ★ 内部状态标志
    private volatile boolean mPrepared = false;       // MPV是否加载完成
    private volatile boolean mStarted = false;        // 用户是否调用了start
    private volatile boolean mPaused = false;         // 当前是否暂停
    private volatile boolean mFirstFrameRendered = false; // 首帧是否已渲染
    private boolean mVideoSizeNotified = false;       // 尺寸是否已通知上层
    
    // ★ UA 常量
    private static final String UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // ★ seek 锁定
    private volatile boolean mSeekLock = false;
    private long mSeekTarget = 0;

    /* ========================= 缓冲通知辅助 ========================= */
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

    private void notifyRenderingStart() {
        mainHandler.post(() -> {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
            }
        });
    }

    /* ========================= Observer ========================= */

    private final MPV.EventObserver observer = new MPV.EventObserver() {
        public void eventProperty(String property) {}
        
        public void eventProperty(String property, long value) {
            if (mpv == null) return;
            if ("duration".equals(property)) {
                mDuration = value * 1000;
            } else if ("time-pos".equals(property)) {
                if (!mSeekLock) {
                    mPosition = value * 1000;
                }
            } else if ("demuxer-cache-time".equals(property)) {
                mCacheEnd = value;
            } else if ("dwidth".equals(property)) {
                // 仅更新变量，不直接通知，等待 VIDEO_RECONFIG 统一处理
                mVideoWidth = (int) value;
            } else if ("dheight".equals(property)) {
                mVideoHeight = (int) value;
            }
        }

        public void eventProperty(String property, double value) {
            if (mpv == null) return;
            if ("duration".equals(property)) {
                mDuration = (long)(value * 1000);
            } else if ("time-pos".equals(property)) {
                if (!mSeekLock) {
                    mPosition = (long)(value * 1000);
                }
            }
        }

        public void eventProperty(String property, boolean value) {
            if (mpv == null) return;
            if ("paused-for-cache".equals(property)) {
                if (value) {
                    notifyBufferingStart();
                } else {
                    // 只有当首帧已经渲染过，或者已经明确开始播放，才结束缓冲
                    if (mFirstFrameRendered || mStarted) {
                         notifyBufferingEnd();
                    }
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
            if (mpv == null) return;
            Log.d(TAG, "MPV Event: " + eventId);

            if (eventId == MPV_EVENT_FILE_LOADED) {
                Log.d(TAG, "FILE_LOADED");
                // 注册关键属性监听
                mpv.observeProperty("time-pos", MPV_FORMAT_INT64);
                mpv.observeProperty("duration", MPV_FORMAT_INT64);
                mpv.observeProperty("demuxer-cache-time", MPV_FORMAT_INT64);
                mpv.observeProperty("paused-for-cache", MPV_FORMAT_FLAG);
                mpv.observeProperty("end-file-reason", MPV_FORMAT_STRING);
                mpv.observeProperty("dwidth", MPV_FORMAT_INT64);
                mpv.observeProperty("dheight", MPV_FORMAT_INT64);

                mPrepared = true;
                
                // ★ 修复点3：此时不立即通知 onPrepared，等待 VIDEO_RECONFIG 确保尺寸就绪
                // 但为了兼容上层逻辑，如果某些流没有 VIDEO_RECONFIG，我们需要一个保底机制
                // 这里先标记 prepared，实际 onPrepared 回调由 VIDEO_RECONFIG 或超时触发会更稳
                // 但鉴于 AbstractPlayer 强依赖 onPrepared，我们在这里调用，但配合下方的尺寸逻辑
                
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onPrepared();
                    }
                });

                // 应用起始位置
                final long startPos = getStartPosition();
                if (startPos > 0 && !isStartPositionApplied()) {
                    Log.d(TAG, "apply startPosition: " + startPos);
                    mpv.command("seek", String.valueOf(startPos / 1000.0), "absolute");
                    markStartPositionApplied();
                }
                
                // ★ 修复点1：FILE_LOADED 时不要立即发送 BUFFERING_END，保持缓冲状态直到画面出来

            } else if (eventId == MPV_EVENT_VIDEO_RECONFIG) {
                // ★ 修复点3：视频尺寸确定事件，通常发生在第一帧解码前或同时
                Log.d(TAG, "VIDEO_RECONFIG w=" + mVideoWidth + " h=" + mVideoHeight);
                
                if (mVideoWidth > 0 && mVideoHeight > 0 && !mVideoSizeNotified) {
                    mVideoSizeNotified = true;
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                        }
                    });
                }
                
                // 如果此时还没有通知渲染开始，且用户已经调用了 start，则认为是首帧就绪
                if (!mFirstFrameRendered && mStarted) {
                    mFirstFrameRendered = true;
                    notifyRenderingStart();
                    // ★ 修复点1：首帧就绪，正式结束缓冲
                    notifyBufferingEnd();
                }

            } else if (eventId == MPV_EVENT_PLAYBACK_RESTART) {
                Log.d(TAG, "PLAYBACK_RESTART");
                mSeekLock = false;
                
                // 如果是 seek 后的重启，可能需要重新触发缓冲结束
                if (mFirstFrameRendered) {
                     notifyBufferingEnd();
                }
                
                // 兜底：如果 VIDEO_RECONFIG 没触发，这里作为第二道防线
                if (!mFirstFrameRendered && mStarted) {
                     // 稍微延迟一点，确保 vo 真的输出了
                     mainHandler.postDelayed(() -> {
                         if (!mFirstFrameRendered) {
                             mFirstFrameRendered = true;
                             notifyRenderingStart();
                             notifyBufferingEnd();
                         }
                     }, 100);
                }

            } else if (eventId == MPV_EVENT_SEEK) {
                Log.d(TAG, "SEEK");
                mSeekLock = true;
                // Seek 时重新进入缓冲状态
                notifyBufferingStart();
                // Seek 后首帧标记重置，以便再次触发 RENDERING_START (如果需要)
                // 但通常 RENDERING_START 只在第一次播放时重要，这里主要控制缓冲图标
                mFirstFrameRendered = false; 

            } else if (eventId == MPV_EVENT_END_FILE) {
                Log.d(TAG, "END_FILE");
                mPrepared = false;
                mStarted = false;
                mPaused = false;
                mSeekLock = false;
                mSeekTarget = 0;
                mVideoSizeNotified = false;
                mFirstFrameRendered = false;
                
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onCompletion();
                    }
                });
            }
        }
    };

    /* ========================= Surface ========================= */

    @Override
    public void setSurface(Surface surface) {
        if (mpv == null) return;
        if (surface != null) {
            mpv.attachSurface(surface);
            try {
                mpv.command("vo-config");
            } catch (Exception e) {
                Log.e(TAG, "vo-config error", e);
            }
        } else {
            mpv.detachSurface();
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
        
        // ★ 优化配置
        mpv.setOptionString("vo", "gpu");
        mpv.setOptionString("gpu-context", "android");
        mpv.setOptionString("gpu-api", "opengl");
        mpv.setOptionString("ao", "audiotrack,opensles,null");
        mpv.setOptionString("audio-exclusive", "yes");
        mpv.setOptionString("hwdec", "auto-safe");
        mpv.setOptionString("keep-open", "yes");
        mpv.setOptionString("loop-file", "no");
        mpv.setOptionString("ytdl", "no");
        mpv.setOptionString("user-agent", UA);
        mpv.setOptionString("stream-lavf-o", "user_agent=" + UA);
        
        mpv.init();
        mpv.addObserver(observer);

        // 重置所有状态
        mPrepared = false;
        mStarted = false;
        mPaused = false;
        mFirstFrameRendered = false;
        mVideoSizeNotified = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mDuration = 0;
        mPosition = 0;
    }

    public void setDataSource(String path, Map<String, String> headers) {
        Log.d(TAG, "setDataSource: " + path);
        // 重置播放相关状态，但保留 init 状态
        mPrepared = false;
        mStarted = false;
        mPaused = false;
        mFirstFrameRendered = false;
        mVideoSizeNotified = false;
        mDuration = 0;
        mCacheEnd = 0;

        notifyBufferingStart();

        mpv.setOptionString("stream-lavf-o", "user_agent=" + UA);
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
        Log.d(TAG, "start called");
        mStarted = true;
        mPaused = false;
        if (mpv != null) {
            mpv.command("set", "pause", "no");
        }
        
        // ★ 修复点2：如果此时已经 Prepared 但还没渲染，强制检查是否需要触发渲染开始
        // 这有助于解决某些情况下 PLAYBACK_RESTART 晚于 start 调用的问题
        if (mPrepared && !mFirstFrameRendered) {
             // 尝试强制刷新一帧，加速 VIDEO_RECONFIG 或 RENDERING 的到来
             try {
                 mpv.command("frame-step");
             } catch (Exception e) {
                 // ignore
             }
        }
    }

    public void pause() {
        Log.d(TAG, "pause called");
        mPaused = true;
        if (mpv != null) mpv.command("set", "pause", "yes");
    }

    public void stop() {
        Log.d(TAG, "stop called");
        if (mpv != null) mpv.command("stop");
        mPrepared = false;
        mStarted = false;
        mPaused = false;
        mSeekLock = false;
        mSeekTarget = 0;
        mFirstFrameRendered = false;
    }

    public void prepareAsync() {
        // MPV loadfile 是异步的，这里不需要做额外操作
        Log.d(TAG, "prepareAsync");
    }

    public void reset() {
        if (mpv != null) {
            mpv.command("stop");
            mpv.detachSurface();
        }
        mPrepared = false;
        mStarted = false;
        mPaused = false;
        mDuration = 0;
        mCacheEnd = 0;
        mSeekLock = false;
        mSeekTarget = 0;
        mVideoSizeNotified = false;
        mFirstFrameRendered = false;
    }

    /**
     * ★ 修复点2：严格控制 isPlaying 的状态
     * 只有当 mStarted 为真，且 mPaused 为假，且 mPrepared 为真时才认为在播放
     * 这样可以防止在 prepare 阶段就显示暂停图标
     */
    public boolean isPlaying() {
        return mStarted && !mPaused && mPrepared;
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
        Log.d(TAG, "release");
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }
        mPrepared = false;
        mStarted = false;
        mPaused = false;
        mSeekLock = false;
        mSeekTarget = 0;
        mFirstFrameRendered = false;
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

    public long getCurrentPosition() {
        if (mSeekLock) {
            return mSeekTarget;
        }
        return mPosition;
    }

    public int getBufferedPercentage() {
        if (mDuration > 0 && mCacheEnd > 0) {
            return (int)Math.min(100, mCacheEnd * 1000 / mDuration);
        }
        return 0;
    }

    public void selectAudioTrack(int aid) { if (mpv != null) mpv.command("set", "aid", String.valueOf(aid)); }
    public void selectSubtitleTrack(int sid) { if (mpv != null) mpv.command("set", "sid", String.valueOf(sid)); }
    public void disableSubtitle() { if (mpv != null) mpv.command("set", "sid", "no"); }
    public void addSubtitleFile(String p) { if (mpv != null) mpv.command("sub-add", p); }
    public void selectVideoTrack(int vid) { if (mpv != null) mpv.command("set", "vid", String.valueOf(vid)); }
}
