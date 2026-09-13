package com.github.tvbox.osc.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

import android.content.res.AssetFileDescriptor;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.util.ArrayList;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class VlcMediaPlayer extends AbstractPlayer
        implements MediaPlayer.EventListener, IVLCVout.Callback {

    private Context mContext;
    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;

    private boolean mIsPrepared = false;
    private boolean mStartPositionApplied = false;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private float mSpeed = 1.0f;

    private Surface mAttachedSurface = null;

    public boolean mIsLooping = false;

    public VlcMediaPlayer(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        release();

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

        if (!path.startsWith("http") && !path.startsWith("rtsp") && !path.startsWith("rtmp")) {
            if (!path.startsWith("/")) {
                path = "file://" + path;
            }
        }

        Media media = new Media(mLibVLC, Uri.parse(path));
        media.setHWDecoderEnabled(true, false);
        media.addOption(":network-caching=300");

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                media.addOption(":" + entry.getKey() + "=" + entry.getValue());
            }
        }

        mMediaPlayer.setMedia(media);
        // 不手动 release
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        throw new UnsupportedOperationException("VLC: AssetFileDescriptor not supported");
    }

    @Override
    public void start() {
        if (mMediaPlayer != null) mMediaPlayer.play();
    }

    @Override
    public void pause() {
        if (mMediaPlayer != null) mMediaPlayer.pause();
    }

    @Override
    public void stop() {
        if (mMediaPlayer != null) mMediaPlayer.stop();
    }

    @Override
    public void prepareAsync() {
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
            IVLCVout vout = mMediaPlayer.getVLCVout();
            vout.removeCallback(this);
            vout.detachViews();
            mMediaPlayer.stop();
            mMediaPlayer.setVideoTrackEnabled(false);
        }
        mAttachedSurface = null;
        mIsPrepared = false;
        mStartPositionApplied = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            IVLCVout vout = mMediaPlayer.getVLCVout();
            vout.removeCallback(this);
            vout.detachViews();
            mMediaPlayer.setEventListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.setVideoTrackEnabled(false);
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mLibVLC != null) {
            mLibVLC.release();
            mLibVLC = null;
        }
        mAttachedSurface = null;
        mIsPrepared = false;
        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    @Override
    public boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        if (mMediaPlayer != null) mMediaPlayer.setTime(time);
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
        return 0;
    }

    // ==================== surface 绑定 ====================

    @Override
    public void setSurface(Surface surface) {
        if (mMediaPlayer == null) return;
        if (mAttachedSurface == surface) return;

        IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.detachViews();

        if (surface != null && surface.isValid()) {
            vout.setVideoSurface(surface, null);
            vout.addCallback(this);
            vout.attachViews();
            mMediaPlayer.setVideoTrackEnabled(true);
            mAttachedSurface = surface;
        } else {
            mMediaPlayer.setVideoTrackEnabled(false);
            mAttachedSurface = null;
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (mMediaPlayer == null) return;

        IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.detachViews();

        if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
            vout.setVideoSurface(holder.getSurface(), holder);
            vout.addCallback(this);
            vout.attachViews();
            mMediaPlayer.setVideoTrackEnabled(true);
            mAttachedSurface = holder.getSurface();
        } else {
            mMediaPlayer.setVideoTrackEnabled(false);
            mAttachedSurface = null;
        }
    }

    // ==================== 其余控制 ====================

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mMediaPlayer == null) return;
        int vol = (int) ((leftVolume + rightVolume) / 2 * 100);
        mMediaPlayer.setVolume(vol);
    }

    public void setLooping(boolean isLooping) {
        mIsLooping = isLooping;
    }

    @Override
    public void setOptions() {
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
        return 0;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    // ==================== MediaPlayer.EventListener ====================

    @Override
    public void onEvent(MediaPlayer.Event event) {
        if (mMediaPlayer == null) return;

        switch (event.type) {
            case MediaPlayer.Event.Playing:
                if (!mIsPrepared) {
                    mIsPrepared = true;
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onPrepared();
                    }
                    readVideoSizeFromTracks();
                    long startPos = getStartPosition();
                    if (startPos > 0 && !mStartPositionApplied) {
                        mMediaPlayer.setTime(startPos);
                        markStartPositionApplied();
                    }
                }
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                }
                break;

            case MediaPlayer.Event.Vout:
                readVideoSizeFromTracks();
                break;

            case MediaPlayer.Event.EndReached:
                if (mIsLooping) {
                    mMediaPlayer.setTime(0);
                    mMediaPlayer.play();
                } else {
                    if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
                }
                break;

            case MediaPlayer.Event.EncounteredError:
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onError();
                }
                break;

            case MediaPlayer.Event.Buffering:
                float buffering = event.getBuffering();
                if (buffering < 100) {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, (int) buffering);
                    }
                } else {
                    if (mPlayerEventListener != null) {
                        mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, 100);
                    }
                }
                break;
        }
    }

    // ==================== IVLCVout.Callback ====================

    @Override
    public void onSurfacesCreated(IVLCVout vout) {
        readVideoSizeFromTracks();
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vout) {
    }

    @Override
    public void onNewVideoLayout(IVLCVout vout, int width, int height,
            int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (width > 0 && height > 0) {
            mVideoWidth = width;
            mVideoHeight = height;
            notifyVideoSizeIfReady();
        }
    }

    // ==================== 视频尺寸 ====================

    private void readVideoSizeFromTracks() {
        if (mMediaPlayer == null) return;
        org.videolan.libvlc.interfaces.IMedia imedia = mMediaPlayer.getMedia();
        if (imedia == null) return;

        for (int i = 0; i < imedia.getTrackCount(); i++) {
            org.videolan.libvlc.interfaces.IMedia.Track track = imedia.getTrack(i);
            if (track.type == org.videolan.libvlc.interfaces.IMedia.Track.Type.Video) {
                org.videolan.libvlc.interfaces.IMedia.VideoTrack vt =
                        (org.videolan.libvlc.interfaces.IMedia.VideoTrack) track;
                if (vt.width > 0 && vt.height > 0) {
                    mVideoWidth = vt.width;
                    mVideoHeight = vt.height;
                    notifyVideoSizeIfReady();
                }
                break;
            }
        }
    }

    private void notifyVideoSizeIfReady() {
        if (mVideoWidth > 0 && mVideoHeight > 0 && mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
        }
    }
}
