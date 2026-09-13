package com.github.tvbox.osc.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class VlcMediaPlayer extends AbstractPlayer implements MediaPlayer.EventListener {

    private static final String TAG = "VlcMediaPlayer";

    private Context mContext;
    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;
    private Media mMedia;

    private boolean mIsPrepared = false;
    private boolean mStartPositionApplied = false;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private float mSpeed = 1.0f;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mVideoSizePollingRunnable;

    public VlcMediaPlayer(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        release(); // 先释放旧实例

        ArrayList<String> options = new ArrayList<>();
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        options.add("--rtsp-tcp");
        options.add("--network-caching=300");

        mLibVLC = new LibVLC(mContext, options);
        mMediaPlayer = new MediaPlayer(mLibVLC);
        mMediaPlayer.setEventListener(this);

        mIsPrepared = false;
        mStartPositionApplied = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        if (mMediaPlayer == null) initPlayer();

        // 处理本地文件
        if (!path.startsWith("http") && !path.startsWith("rtsp") && !path.startsWith("rtmp")) {
            if (!path.startsWith("/")) {
                path = "file://" + path;
            }
        }

        mMedia = new Media(mLibVLC, Uri.parse(path));
        mMedia.setHWDecoderEnabled(true, false);
        mMedia.addOption(":network-caching=300");

        // 设置 headers（如果有）
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                mMedia.addOption(":" + entry.getKey() + "=" + entry.getValue());
            }
        }

        mMediaPlayer.setMedia(mMedia);
        mMedia.release(); // Media 设完就可以释放
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("VLC: AssetFileDescriptor not supported");
    }

    @Override
    public void start() {
        if (mMediaPlayer != null) {
            mMediaPlayer.play();
        }
    }

    @Override
    public void pause() {
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
        }
    }

    @Override
    public void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
        }
    }

    @Override
    public void prepareAsync() {
        // VLC 是异步的，setMedia 后直接 play 即可
        if (mMediaPlayer != null) {
            long startPos = getStartPosition();
            if (startPos > 0) {
                mMediaPlayer.setTime(startPos);
                markStartPositionApplied();
            }
            mMediaPlayer.play();
        }
    }

    @Override
    public void reset() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.detachViews();
        }
        mIsPrepared = false;
        mStartPositionApplied = false;
        stopVideoSizePolling();
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.setEventListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.detachViews();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mLibVLC != null) {
            mLibVLC.release();
            mLibVLC = null;
        }
        mIsPrepared = false;
        stopVideoSizePolling();
    }

    @Override
    public boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setTime(time);
        }
    }

    @Override
    public long getCurrentPosition() {
        return mMediaPlayer != null ? mMediaPlayer.getTime() : 0;
    }

    @Override
    public long getDuration() {
        return mMediaPlayer != null ? mMediaPlayer.getLength() : 0;
    }

    @Override
    public int getBufferedPercentage() {
        if (mMediaPlayer == null) return 0;
        long cached = mMediaPlayer.getCachedBytes();
        long total = mMediaPlayer.getLength();
        if (total <= 0) return 0;
        return (int) (cached * 100 / total);
    }

    @Override
    public void setSurface(Surface surface) {
        if (mMediaPlayer == null) return;
        if (surface == null) {
            mMediaPlayer.detachViews();
        } else {
            // VLC 3.x 需要 attachViews
            mMediaPlayer.attachViews(null, null, false, false);
            // 通过 IVLCVout 设置 surface
            MediaPlayer.VLCVout vout = mMediaPlayer.getVLCVout();
            vout.setVideoSurface(surface, null);
            vout.attachViews();
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (mMediaPlayer == null) return;
        if (holder == null) {
            setSurface(null);
        } else {
            setSurface(holder.getSurface());
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mMediaPlayer == null) return;
        // 3.x: setVolume(int) 0-100
        int vol = (int) ((leftVolume + rightVolume) / 2 * 100);
        mMediaPlayer.setVolume(vol);
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mMediaPlayer == null) return;
        mMediaPlayer.setRepeatType(isLooping ? MediaPlayer.Repeat.All : MediaPlayer.Repeat.None);
    }

    @Override
    public void setOptions() {
        // 空实现
    }

    @Override
    public void setSpeed(float speed) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setRate(speed);
        }
        mSpeed = speed;
    }

    @Override
    public float getSpeed() {
        return mSpeed;
    }

    @Override
    public long getTcpSpeed() {
        return 0; // VLC 3.x 没有直接获取网速的 API
    }

    @Override
    public int getAudioSessionId() {
        return 0; // VLC 不暴露 AudioSessionId
    }

    // ==================== EventListener ====================

    @Override
    public void onEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Playing:
                if (!mIsPrepared) {
                    mIsPrepared = true;
                    mHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onPrepared();
                        }
                    });
                    // 应用 startPosition
                    long startPos = getStartPosition();
                    if (startPos > 0 && !mStartPositionApplied) {
                        mMediaPlayer.setTime(startPos);
                        markStartPositionApplied();
                    }
                    // 开始轮询视频尺寸
                    startVideoSizePolling();
                }
                mHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                    }
                });
                break;

            case MediaPlayer.Event.Paused:
                break;

            case MediaPlayer.Event.Stopped:
                break;

            case MediaPlayer.Event.EndReached:
                mHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onCompletion();
                    }
                });
                break;

            case MediaPlayer.Event.EncounteredError:
                mHandler.post(() -> {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onError();
                    }
                });
                break;

            case MediaPlayer.Event.Buffering:
                float buffering = event.getBuffering();
                if (buffering < 100) {
                    mHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, (int) buffering);
                        }
                    });
                } else {
                    mHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, 100);
                        }
                    });
                }
                break;

            case MediaPlayer.Event.TimeChanged:
                // 时间变化，可以用来更新进度
                break;

            case MediaPlayer.Event.PositionChanged:
                // 位置变化
                break;
        }
    }

    // ==================== 视频尺寸轮询 ====================

    private void startVideoSizePolling() {
        stopVideoSizePolling();
        mVideoSizePollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (mMediaPlayer == null || mMedia == null) return;
                int w = mMedia.getTrackCount() > 0 ? mMedia.getTrack(0).video.width : 0;
                int h = mMedia.getTrackCount() > 0 ? mMedia.getTrack(0).video.height : 0;
                if (w > 0 && h > 0) {
                    mVideoWidth = w;
                    mVideoHeight = h;
                    notifyVideoSizeIfReady();
                    stopVideoSizePolling();
                    return;
                }
                mHandler.postDelayed(this, 500);
            }
        };
        mHandler.post(mVideoSizePollingRunnable);
    }

    private void stopVideoSizePolling() {
        if (mVideoSizePollingRunnable != null) {
            mHandler.removeCallbacks(mVideoSizePollingRunnable);
            mVideoSizePollingRunnable = null;
        }
    }

    private void notifyVideoSizeIfReady() {
        if (mVideoWidth > 0 && mVideoHeight > 0 && mPlayerEventListener != null) {
            mHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }
            });
        }
    }
}
