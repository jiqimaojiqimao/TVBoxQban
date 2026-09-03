package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;

import is.xyz.mpv.MPV;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class MpvMediaPlayer extends AbstractPlayer {

    private MPV mpv;
    private Context context;

    // 用本地变量缓存状态，避免需要 getProperty
    private boolean mPaused = false;
    private long mDuration = 0;
    private long mPosition = 0;
    private int mBufferedPercent = 0;
    private float mSpeed = 1.0f;

    public MpvMediaPlayer(Context context) {
        this.context = context;
        mpv = new MPV();
    }

    @Override
    public void initPlayer() {
        mpv.create(context);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.init();
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
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

    @Override
    public void start() {
        mPaused = false;
        mpv.command("set", "pause", "no");
    }

    @Override
    public void pause() {
        mPaused = true;
        mpv.command("set", "pause", "yes");
    }

    @Override
    public void stop() {
        mpv.command("stop");
    }

    @Override
    public void prepareAsync() {
        // mpv 自动 prepare
    }

    @Override
    public void reset() {
        mpv.command("stop");
        mPaused = false;
        mDuration = 0;
        mPosition = 0;
        mBufferedPercent = 0;
    }

    @Override
    public boolean isPlaying() {
        return !mPaused;
    }

    @Override
    public void seekTo(long time) {
        mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
    }

    @Override
    public void release() {
        mpv.detachSurface();
        mpv.destroy();
    }

    @Override
    public void setSurface(Surface surface) {
        mpv.attachSurface(surface);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder != null) {
            mpv.attachSurface(holder.getSurface());
        } else {
            mpv.detachSurface();
        }
    }

    @Override
    public void setVolume(float v1, float v2) {
        // mpv 音量用 ao-volume，范围 0-100
        int vol = (int)((v1 + v2) / 2 * 100);
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
        mSpeed = speed;
        mpv.command("set", "speed", String.valueOf(speed));
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
        return mBufferedPercent;
    }
}
