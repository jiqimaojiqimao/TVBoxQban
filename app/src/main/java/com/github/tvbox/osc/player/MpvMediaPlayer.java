package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class MpvMediaPlayer extends AbstractPlayer {

    private static final String TAG = "MPV";

    private static final int MPV_FORMAT_STRING = 1;
    private static final int MPV_FORMAT_FLAG = 3;
    private static final int MPV_FORMAT_INT64 = 4;

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
    }

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private MPV mpv;
    private Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private long mPosition = 0;
    private long mDuration = 0;
    private long mCacheEnd = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    private volatile boolean mPrepared = false;
    private volatile boolean mPaused = false;
    private volatile boolean mSeekLock = false;
    private long mSeekTarget = 0;
    private volatile boolean mShouldNotifyPlaying = false;
    private boolean mPlayingNotified = false;

    private File mLocalM3u8File;

    /* ========================= 缓冲 ========================= */
    private void notifyBufferingStart() {
        mainHandler.post(() -> {
            if (mPlayerEventListener != null)
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
        });
    }

    private void notifyBufferingEnd() {
        mainHandler.post(() -> {
            if (mPlayerEventListener != null)
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
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
                    checkAndNotifyPlaying(value);
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
                checkAndNotifyPlaying(value);
                if (!mSeekLock) mPosition = (long) (value * 1000);
            }
        }

        public void eventProperty(String property, boolean value) {
            if (mpv == null) return;
            if ("paused-for-cache".equals(property)) {
                if (value) notifyBufferingStart();
                else notifyBufferingEnd();
            }
        }

        public void eventProperty(String property, String value) {
            if (mpv == null) return;
            if ("end-file-reason".equals(property) && "error".equals(value)) {
                Log.e(TAG, "end-file-reason=error");
                mainHandler.post(() -> {
                    if (mPlayerEventListener != null) mPlayerEventListener.onError();
                });
            }
        }

        public void eventProperty(String property, MPVNode node) {}

        public void event(int eventId) { handleEvent(eventId); }
        public void event(int eventId, MPVNode node) { handleEvent(eventId); }

        private void handleEvent(int eventId) {
            if (mpv == null) return;
            switch (eventId) {
                case 8: // FILE_LOADED
                    notifyVideoSizeIfReady();
                    break;
                case 21: // PLAYBACK_RESTART
                    mSeekLock = false;
                    notifyBufferingEnd();
                    break;
                case 20: // SEEK
                    notifyBufferingStart();
                    break;
                case 7: // END_FILE
                    mPrepared = false;
                    mPaused = false;
                    mSeekLock = false;
                    mSeekTarget = 0;
                    mShouldNotifyPlaying = false;
                    mPlayingNotified = false;
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
                    });
                    break;
            }
        }
    };

    /* ========================= 延迟发 RENDERING_START ========================= */
    private Runnable mNotifyRenderingRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
            }
            Log.d(TAG, "RENDERING_START fired");
        }
    };

    private void checkAndNotifyPlaying(double timePosValue) {
        if (!mShouldNotifyPlaying || mPlayingNotified) return;
        if (timePosValue > 0) {
            mPrepared = true;
            mPlayingNotified = true;
            notifyVideoSizeIfReady();
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onPrepared();
                }
            });
            // ★ 延迟 20ms 发 RENDERING_START
            mainHandler.postDelayed(mNotifyRenderingRunnable, 20);
        }
    }

    private void notifyVideoSizeIfReady() {
        if (mVideoWidth > 0 && mVideoHeight > 0 && mPlayerEventListener != null) {
            mainHandler.post(() -> {
                if (mPlayerEventListener != null)
                    mPlayerEventListener.onVideoSizeChanged(mVideoWidth, mVideoHeight);
            });
        }
    }

    /* ========================= Surface ========================= */
    @Override
    public void setSurface(Surface surface) {
        if (surface != null && mpv != null) mpv.attachSurface(surface);
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
            try { mpv.command("stop"); } catch (Exception ignored) {}
            try { mpv.removeObserver(observer); } catch (Exception ignored) {}
            try { mpv.destroy(); } catch (Exception ignored) {}
            mpv = null;
        }

        mpv = new MPV();
        mpv.create(context);
        mpv.setOptionString("hwdec", "auto");
        mpv.setOptionString("ao", "audiotrack");
        mpv.setOptionString("keep-open", "yes");
        mpv.setOptionString("loop-file", "no");
        mpv.setOptionString("ytdl", "no");
        mpv.setOptionString("tls-verify", "no");
        // ★ 协议白名单，允许 https
        mpv.setOptionString("protocol_whitelist", "file,http,https,tls,crypto,data,tcp,udp");
        // ★ 允许所有文件扩展名（key 文件必须，否则 ffmpeg 会拦截）
        mpv.setOptionString("allowed_extensions", "ALL");
        mpv.init();
        mpv.addObserver(observer);

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

    /* ========================= 核心：下载 m3u8 到本地 ========================= */
    public void setDataSource(String path, Map<String, String> headers) {
        // ★ 确保 mpv 已初始化（同步等待就绪）
        if (mpv == null) {
            initPlayer();
        }
        // ★ 等 mpv 完全就绪（create + init 是异步的，需要等）
        int waitCount = 0;
        while (mpv == null && waitCount < 100) {
            try { Thread.sleep(10); } catch (InterruptedException e) {}
            waitCount++;
        }
        if (mpv == null) {
            Log.e(TAG, "mpv still null, cannot proceed");
            return;
        }

        Log.d(TAG, "setDataSource: " + path);

        mPrepared = false;
        mPaused = false;
        mDuration = 0;
        mCacheEnd = 0;
        mShouldNotifyPlaying = false;
        mPlayingNotified = false;

        if (isLikelyHls(path)) {
            // ★ 直接同步执行（setDataSource 本身在子线程调用），不用开新线程
            String localPath = downloadAndRewriteM3u8(path, headers);
            if (localPath != null) {
                mpv.command("loadfile", localPath);
            } else {
                // 下载失败，直接播原始 URL（兜底）
                mpv.command("loadfile", path);
            }
        } else {
            // 非 HLS，直接播
            mpv.command("loadfile", path);
        }
    }

    /**
     * 下载 m3u8 文件，把 ts 相对路径改成绝对 URL，key 下载到本地
     * 写到本地缓存
     */
    private String downloadAndRewriteM3u8(String m3u8Url, Map<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(m3u8Url)
                    .header("User-Agent", UA);

            String referer = extractReferer(m3u8Url);
            if (!TextUtils.isEmpty(referer)) builder.header("Referer", referer);
            builder.header("Origin", extractOrigin(m3u8Url));

            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    builder.header(e.getKey(), e.getValue());
                }
            }

            Response response = httpClient.newCall(builder.build()).execute();
            if (!response.isSuccessful() || response.body() == null) {
                Log.e(TAG, "m3u8 download failed: " + response.code());
                return null;
            }

            // baseUrl 用于 ts 拼接
            String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
            // origin 用于 key URI 拼接（只有 host，没有 path）
            String origin = extractOrigin(m3u8Url);

            StringBuilder rewritten = new StringBuilder();
            String line;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()));

            while ((line = reader.readLine()) != null) {
                // ★ 处理 #EXT-X-KEY：下载 key 到本地，重写 URI
                if (line.startsWith("#EXT-X-KEY") && line.contains("URI=")) {
                    line = rewriteKeyUri(line, origin, headers);
                    rewritten.append(line).append("\n");
                } else if (line.startsWith("#")) {
                    // 其他 # 开头的行原样保留
                    rewritten.append(line).append("\n");
                } else if (!line.isEmpty()) {
                    // ts 行：相对路径 → 绝对 URL
                    if (!line.startsWith("http://") && !line.startsWith("https://")) {
                        rewritten.append(baseUrl).append(line).append("\n");
                    } else {
                        rewritten.append(line).append("\n");
                    }
                }
            }
            reader.close();

            // 写到本地缓存
            File cacheDir = new File(context.getCacheDir(), "mpv_hls");
            cacheDir.mkdirs();
            mLocalM3u8File = new File(cacheDir, "playlist_" + System.currentTimeMillis() + ".m3u8");
            try (FileOutputStream fos = new FileOutputStream(mLocalM3u8File)) {
                fos.write(rewritten.toString().getBytes("UTF-8"));
            }

            Log.d(TAG, "m3u8 rewritten to: " + mLocalM3u8File.getAbsolutePath());
            return "file://" + mLocalM3u8File.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "downloadAndRewriteM3u8 error", e);
            return null;
        }
    }

    /**
     * 重写 #EXT-X-KEY 行里的 URI：
     * 1. 提取 URI
     * 2. 拼成绝对 URL（用 origin，不会出现双斜杠）
     * 3. 用 OkHttp 下载 key
     * 4. 存到本地（加 .key 扩展名），替换成 file:// 路径
     */
    private String rewriteKeyUri(String line, String origin, Map<String, String> headers) {
        if (!line.contains("URI=\"")) return line;
        int uriStart = line.indexOf("URI=\"") + 5;
        int uriEnd = line.indexOf("\"", uriStart);
        if (uriStart <= 5 || uriEnd <= uriStart) return line;

        String keyUri = line.substring(uriStart, uriEnd);

        // 拼成绝对 URL
        if (!keyUri.startsWith("http://") && !keyUri.startsWith("https://")) {
            if (keyUri.startsWith("/")) {
                keyUri = origin + keyUri;
            } else {
                keyUri = origin + "/" + keyUri;
            }
        }

        Log.d(TAG, "downloading key: " + keyUri);
        byte[] keyData = downloadKey(keyUri, headers);
        if (keyData != null && keyData.length > 0) {
            try {
                File cacheDir = new File(context.getCacheDir(), "mpv_hls");
                cacheDir.mkdirs();
                // ★ key 文件加 .key 扩展名（ffmpeg 需要能识别它是 key 文件）
                File keyFile = new File(cacheDir, "key_" + System.currentTimeMillis() + ".key");
                try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                    fos.write(keyData);
                }
                String localKeyPath = "file://" + keyFile.getAbsolutePath();
                Log.d(TAG, "key saved to: " + localKeyPath);
                return line.substring(0, uriStart) + localKeyPath + line.substring(uriEnd);
            } catch (Exception e) {
                Log.e(TAG, "save key failed", e);
            }
        } else {
            Log.e(TAG, "key download failed, using original URI: " + keyUri);
        }

        // 下载失败，至少用正确的绝对 URL
        return line.substring(0, uriStart) + keyUri + line.substring(uriEnd);
    }

    /** 下载 key 文件 */
    private byte[] downloadKey(String keyUrl, Map<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(keyUrl)
                    .header("User-Agent", UA);

            String referer = extractReferer(keyUrl);
            if (!TextUtils.isEmpty(referer)) builder.header("Referer", referer);
            builder.header("Origin", extractOrigin(keyUrl));

            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    builder.header(e.getKey(), e.getValue());
                }
            }

            Response response = httpClient.newCall(builder.build()).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            } else {
                Log.e(TAG, "key download http error: " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "download key failed: " + keyUrl, e);
        }
        return null;
    }

    private boolean isLikelyHls(String path) {
        return path != null && (path.contains(".m3u8") || path.contains("m3u"));
    }

    private String extractReferer(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return "";
        }
    }

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
        mPaused = false;
        if (mpv != null) mpv.command("set", "pause", "no");
    }

    public void pause() {
        mPaused = true;
        if (mpv != null) mpv.command("set", "pause", "yes");
    }

    public void stop() {
        if (mpv != null) mpv.command("stop");
        mPrepared = false;
        mPaused = false;
        mSeekLock = false;
        mSeekTarget = 0;
        mShouldNotifyPlaying = false;
        mPlayingNotified = false;
    }

    public void prepareAsync() {}

    public void reset() {
        if (mpv != null) {
            try { mpv.command("stop"); } catch (Exception ignored) {}
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

        // 清理本地 m3u8 缓存
        if (mLocalM3u8File != null) {
            mLocalM3u8File.delete();
            mLocalM3u8File = null;
        }
    }

    public boolean isPlaying() {
        return mPrepared && !mPaused;
    }

    public void seekTo(long time) {
        if (mpv != null) {
            mSeekLock = true;
            mSeekTarget = time;
            mPosition = time;
            notifyBufferingStart();
            mpv.command("seek", String.valueOf(time / 1000.0), "absolute");
        }
    }

    public void release() {
        // 移除延迟任务
        mainHandler.removeCallbacks(mNotifyRenderingRunnable);
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
        if (mpv != null)
            mpv.command("set", "ao-volume", String.valueOf((int) ((l + r) / 2 * 100)));
    }

    public void setLooping(boolean loop) {
        if (mpv != null) mpv.setOptionString("loop", loop ? "inf" : "no");
    }

    public void setOptions() {}

    public void setSpeed(float speed) {
        if (mpv != null) mpv.command("set", "speed", String.valueOf(speed));
    }

    public float getSpeed() { return 1.0f; }

    public long getTcpSpeed() { return PlayerUtils.getNetSpeed(context); }

    public int getAudioSessionId() { return 0; }

    public long getDuration() { return mDuration; }

    public long getCurrentPosition() { return mSeekLock ? mSeekTarget : mPosition; }

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
