package com.github.tvbox.osc.player;

import android.content.Context;
import android.view.Surface;

import is.xyz.mpv.MPV;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class MpvMediaPlayer extends AbstractPlayer {

    private MPV mpv;
    private Context context;

    public MpvMediaPlayer(Context context) {
        this.context = context;
        mpv = new MPV();
    }

    @Override
    public void init() {
        mpv.create(context);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.init();
    }

    @Override
    public void setDataSource(String path) {
        mpv.command("loadfile", path);
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
    }

    @Override
    public void prepareAsync() {
        // mpv 自动 prepare，无需操作
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
    public boolean isPlaying() {
        String val = mpv.getProperty("pause");
        return val == null || val.equals("false");
    }

    @Override
    public long getDuration() {
        String val = mpv.getProperty("duration");
        if (val != null) {
            try {
                return (long)(Double.parseDouble(val) * 1000);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public long getCurrentPosition() {
        String val = mpv.getProperty("time-pos");
        if (val != null) {
            try {
                return (long)(Double.parseDouble(val) * 1000);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public void seekTo(long ms) {
        mpv.command("seek", String.valueOf(ms / 1000.0), "absolute");
    }

    @Override
    public int getBufferedPercentage() {
        String val = mpv.getProperty("cache-buffering-state");
        if (val != null) {
            try {
                return (int)Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    // doikki AbstractPlayer 要求的方法
    @Override
    public long getTcpSpeed() {
        // mpv 没有直接暴露 TCP 速度，返回 0
        return 0;
    }
}
