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
    public void start() {
        mpv.prop.set("pause", false);
    }

    @Override
    public void pause() {
        mpv.prop.set("pause", true);
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
        Boolean p = (Boolean) mpv.prop.get("pause");
        return p != null && !p;
    }

    @Override
    public long getDuration() {
        Double d = (Double) mpv.prop.get("duration");
        return d == null ? 0 : (long)(d * 1000);
    }

    @Override
    public long getCurrentPosition() {
        Double p = (Double) mpv.prop.get("time-pos");
        return p == null ? 0 : (long)(p * 1000);
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
        Double cache = (Double) mpv.prop.get("cache-buffering-state");
        return cache == null ? 0 : cache.intValue();
    }
}
