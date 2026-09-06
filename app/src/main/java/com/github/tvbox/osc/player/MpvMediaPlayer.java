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

    // ★ 静态块已删除，不再 System.loadLibrary
    // so 加载改到 MpvNativeLoader，在构造函数里触发

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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
    private boolean mPlayingNotified = false;

    private File mLocalM3u8File;

    /* ========================= startPosition 占位 ========================= */
    // 如果你已有这些方法的实现，保留你自己的；没有的话用这组占位
    private long mStartPosition = 0;
    private boolean mStartPositionApplied = false;

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
                    mPlayingNotified = false;
                    mainHandler.post(() -> {
                        if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
                    });
                    break;
            }
        }
    };

    private void checkAndNotifyPlaying(double timePosValue) {
        if (mPlayingNotified) return;
        if (timePosValue > 0) {
            mPlayingNotified = true;
            mPrepared = true;

            notifyVideoSizeIfReady();
            mainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onPrepared();
                }
            });

            // ★ startPosition
            final long startPos = getStartPosition();
            if (startPos > 0 && !isStartPositionApplied()) {
                Log.d(TAG, "apply startPosition: " + startPos);
                mpv.command("seek", String.valueOf(startPos / 1000.0), "absolute");
                markStartPositionApplied();
            }

            mainHandler.postDelayed(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                }
            }, 20);
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
        if (surface != null && mpv != null){
            mpv.attachSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null){
            setSurface(null);
        } else {
            setSurface(holder.getSurface());
        }
    }

    /* ========================= 生命周期 ========================= */
    public MpvMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
        MpvNativeLoader.load(this.context); // ★ 从 assets 拷 so 并 load
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
        // 缓冲优化
        mpv.setOptionString("cache-pause", "yes");
        mpv.setOptionString("cache-pause-wait", "3");
        mpv.setOptionString("cache-secs", "30");
        mpv.setOptionString("demuxer-max-bytes", "50M");
        mpv.setOptionString("allowed_extensions", "ALL");
        mpv.setOptionString("protocol_whitelist", "file,http,https,tls,crypto,data,tcp,udp");
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
        mPlayingNotified = false;
        mStartPositionApplied = false;
    }

    /* ========================= 核心 ========================= */
    public void setDataSource(String path, Map<String, String> headers) {
        if (mpv == null) initPlayer();
        Log.d(TAG, "setDataSource: " + path);

        mPrepared = false;
        mPaused = false;
        mDuration = 0;
        mCacheEnd = 0;
        mPlayingNotified = false;
        mStartPositionApplied = false;

        if (isLikelyHls(path)) {
            String localPath = downloadAndRewriteM3u8(path, headers);
            if (localPath != null) {
                Log.d(TAG, "loadfile: " + localPath);
                mpv.command("loadfile", localPath);
            } else {
                Log.w(TAG, "m3u8 download failed, fallback to direct: " + path);
                mpv.command("loadfile", path);
            }
        } else {
            mpv.command("loadfile", path);
        }
    }

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

            String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
            String origin = extractOrigin(m3u8Url);

            StringBuilder rewritten = new StringBuilder();
            String line;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()));

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#EXT-X-KEY") && line.contains("URI=")) {
                    line = rewriteKeyUri(line, origin, headers);
                    rewritten.append(line).append("\n");
                } else if (line.startsWith("#")) {
                    rewritten.append(line).append("\n");
                } else if (!line.isEmpty()) {
                    if (!line.startsWith("http://") && !line.startsWith("https://")) {
                        rewritten.append(baseUrl).append(line).append("\n");
                    } else {
                        rewritten.append(line).append("\n");
                    }
                }
            }
            reader.close();

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

    private String rewriteKeyUri(String line, String origin, Map<String, String> headers) {
        if (!line.contains("URI=\"")) return line;
        int uriStart = line.indexOf("URI=\"") + 5;
        int uriEnd = line.indexOf("\"", uriStart);
        if (uriStart <= 5 || uriEnd <= uriStart) return line;

        String keyUri = line.substring(uriStart, uriEnd);

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

        return line.substring(0, uriStart) + keyUri + line.substring(uriEnd);
    }

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
        return path != null && (path.contains(".m3u8") || path.contains("m3u") || path.contains("getM3u8"));
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
        mPlayingNotified = false;

        if (mLocalM3u8File != null) {
            mLocalM3u8File.delete();
            mLocalM3u8File = null;
        }
    }

    public boolean isPlaying() {
        if (mpv == null){
            return false;
        }
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

    /* ========================= Native Loader ========================= */
    /**
     * 从 assets/mpv/{abi}/ 拷贝 so 到 files 目录并 dlopen。
     * 这样完全绕开 jniLibs 和其他播放器的同名 so 冲突。
     */
    private static class MpvNativeLoader {
        private static final String[] LIBS = {
            "libavcodec.so"
            "libavdevice.so"
            "libavfilter.so"
            "libavformat.so"
            "libavutil.so"
            "libc++_shared.so"
            "libmpv.so"
            "libxml2.so"
            "libplayer.so"
            "libswresample.so"
            "libswscale.so"
        };

        private static boolean sLoaded = false;

        static synchronized void load(Context context) {
            if (sLoaded) return;

            // 取第一个支持的 ABI
            String abi = android.os.Build.SUPPORTED_ABIS[0];
            // ABI 名映射：armeabi-v7a → arm64-v8a 等，按你实际 assets 里放的目录名来
            // 如果你 assets 里只放了 arm64-v8a，直接写死：
            // abi = "arm64-v8a";

            File dir = new File(context.getFilesDir(), "mpv_native_libs");
            dir.mkdirs();

            try {
                for (String lib : LIBS) {
                    File out = new File(dir, lib);
                    if (!out.exists() || out.length() == 0) {
                        copyFromAssets(context, "mpv/" + abi + "/" + lib, out);
                    }
                }

                // 按依赖顺序 load
                for (String lib : LIBS) {
                    System.load(new File(dir, lib).getAbsolutePath());
                }

                sLoaded = true;
                Log.d(TAG, "mpv native libs loaded from: " + dir.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to load mpv native libs", e);
                throw new RuntimeException("Cannot load mpv native libraries", e);
            }
        }

        private static void copyFromAssets(Context context, String assetPath, File out) throws Exception {
            try (InputStream in = context.getAssets().open(assetPath);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            }
        }
    }
}
