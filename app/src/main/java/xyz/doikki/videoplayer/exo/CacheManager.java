package xyz.doikki.videoplayer.exo;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.base.App;
import java.io.File;

public class CacheManager {

    private SimpleCache cache;

    private static class Loader {
        static volatile CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager get() {
        return Loader.INSTANCE;
    }

    public Cache getCache() {
        if (cache == null) create();
        return cache;
    }

    private void create() {
        cache = new SimpleCache(new File(FileUtils.getCachePath() + "exo-video-cache"), new NoOpCacheEvictor(), new StandaloneDatabaseProvider(App.getInstance()));
    }
}

