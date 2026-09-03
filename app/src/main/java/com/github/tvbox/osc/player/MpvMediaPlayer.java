package com.github.tvbox.osc.player;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;

import is.xyz.mpv.MPV;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * MPV 播放器实现，基于 mpv-android-lib (io.github.abdallahmehiz:mpv-android-lib)
 *
 * 注意：MPV.kt 中的属性访问方式：
 *   - Kotlin 中：mpv.prop["key"] = value / val x = mpv.prop["key"]
 *   - Java 中等价于：mpv.getProp().set("key", value) / mpv.getProp().get("key")
 *
 * 由于 mpv-android-lib 0.1.12 的 MPV.kt 中 prop 是 internal visibility，
 * 从 Java 无法直接访问，因此全部改用 command() 和 getProperty() 方式。
 */
public class MpvMediaPlayer extends AbstractPlayer {

    private MPV mpv;
    private Context context;
    private float currentSpeed = 1.0f;

    public MpvMediaPlayer(Context context) {
        this.context = context;
        this.mpv = new MPV();
    }

    // ======================== AbstractPlayer 必须实现的方法 ========================

    @Override
    public void initPlayer() {
        mpv.create(context);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.init();
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        // 先设置请求头（必须在 loadfile 之前）
        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            mpv.setOptionString("http-header-fields", sb.toString());
        }
        // 使用 mpv command 加载文件
        mpv.command("loadfile", path);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        // TVBox 一般不用 asset 播放，简单用 file descriptor 路径
        // mpv 不支持直接传 AssetFileDescriptor，这里转为路径（实际场景很少用到）
        throw new UnsupportedOperationException("AssetFileDescriptor not supported in MpvMediaPlayer");
    }

    @Override
    public void start() {
        // mpv command: set pause no
        mpv.command("set", "pause", "no");
    }

    @Override
    public void pause() {
        // mpv command: set pause yes
        mpv.command("set", "pause", "yes");
    }

    @Override
    public void stop() {
        mpv.command("stop");
    }

    @Override
    public void prepareAsync() {
        // mpv 是自动 prepare 的，loadfile 后自动开始缓冲
        // do nothing
    }

    @Override
    public void reset() {
        mpv.command("stop");
    }

    @Override
    public boolean isPlaying() {
        // 通过 command 获取属性值
        // mpv command: get pause → 返回 "yes" 或 "no"
        // 注意：mpv-android-lib 的 command 返回 String
        String val = mpv.command("get", "pause");
        // "no" 表示正在播放，"yes" 表示暂停
        return val != null && val.equals("no");
    }

    @Override
    public void seekTo(long time) {
        // time 是毫秒，mpv seek 用秒
        mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
    }

    @Override
    public void release() {
        try {
            mpv.detachSurface();
        } catch (Exception ignored) {}
        try {
            mpv.destroy();
        } catch (Exception ignored) {}
    }

    @Override
    public long getCurrentPosition() {
        String val = mpv.command("get", "time-pos");
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
    public long getDuration() {
        String val = mpv.command("get", "duration");
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
    public int getBufferedPercentage() {
        String val = mpv.command("get", "cache-buffering-state");
        if (val != null) {
            try {
                return (int)Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public void setSurface(Surface surface) {
        mpv.attachSurface(surface);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder != null) {
            mpv.attachSurface(holder.getSurface());
        }
    }

    @Override
    public void setVolume(float v1, float v2) {
        // mpv 音量范围 0-100
        int vol = (int)(v1 * 100);
        mpv.command("set", "volume", String.valueOf(vol));
    }

    @Override
    public void setLooping(boolean isLooping) {
        mpv.command("set", "loop", isLooping ? "yes" : "no");
    }

    @Override
    public void setOptions() {
        // 可以在这里设置额外选项
    }

    @Override
    public void setSpeed(float speed) {
        this.currentSpeed = speed;
        mpv.command("set", "speed", String.valueOf(speed));
    }

    @Override
    public float getSpeed() {
        return currentSpeed;
    }

    @Override
    public long getTcpSpeed() {
        // mpv 没有直接的 TCP 速度 API，返回 0
        return 0;
    }

    @Override
    public int getAudioSessionId() {
        // libmpv 自己管理 AudioTrack，返回 0（Android 兼容默认值）
        return 0;
    }
}
