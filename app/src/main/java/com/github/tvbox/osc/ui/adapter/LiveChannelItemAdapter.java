package com.github.tvbox.osc.ui.adapter;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xuameng
 * @date :2026/2/10
 * @description:
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private ExecutorService favoriteCheckExecutor;
    
    // xuameng使用LinkedHashMap实现LRU缓存
    private static final int MAX_CACHE_SIZE = 200;
    private Map<String, Boolean> favoriteCache = new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        TextView tvFavoriteStar = holder.getView(R.id.ivFavoriteStar);
        
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());
        tvFavoriteStar.setVisibility(View.GONE);
        
        int position = holder.getLayoutPosition();
        
       // xuameng异步检查收藏状态
        checkChannelFavoriteAsync(item, position, new FavoriteCheckCallback() {
            @Override
            public void onFavoriteChecked(boolean isFavorited, int checkedPosition) {
                // xuameng直接使用holder参数更新UI
                if (holder.getLayoutPosition() == checkedPosition) {
                    if (isFavorited) {
                        tvFavoriteStar.setVisibility(View.VISIBLE);
                        tvFavoriteStar.setText("★");
                        tvFavoriteStar.setTextColor(Color.parseColor("#FFD700"));
                    } else {
                        tvFavoriteStar.setVisibility(View.GONE);
                    }
                }
                // xuameng注意：这里不要调用notifyItemChanged
            }
        });

        int channelIndex = item.getChannelIndex();
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            tvChannelNum.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
            tvChannel.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
        } else {
            tvChannelNum.setTextColor(Color.WHITE);
            tvChannel.setTextColor(Color.WHITE);
        }
    }

    // xuameng新增接口定义
    public interface FavoriteCheckCallback {
        void onFavoriteChecked(boolean isFavorited, int position);
    }

    // xuameng生成缓存键的方法 - 优化版本
    private String getChannelCacheKey(LiveChannelItem channel) {
        // 使用频道名和当前URL的哈希值作为缓存键
        return channel.getChannelName() + "|" + 
               (channel.getUrl() != null ? channel.getUrl().hashCode() : 0);
    }

    // xuameng修改异步检查方法，先检查缓存
    private void checkChannelFavoriteAsync(LiveChannelItem channel, int position, FavoriteCheckCallback callback) {
        if (channel == null || callback == null) {
            return;
        }
        
        // xuameng先检查缓存
        String cacheKey = getChannelCacheKey(channel);
        Boolean cachedResult = favoriteCache.get(cacheKey);
        if (cachedResult != null) {
            // 缓存命中，直接回调
            boolean finalResult = cachedResult;
            if (mContext instanceof Activity) {
                ((Activity) mContext).runOnUiThread(() -> {
                    callback.onFavoriteChecked(finalResult, position);
                });
            }
            return;
        }
        
        // xuameng缓存未命中，执行异步查询
        if (favoriteCheckExecutor == null) {
            int coreCount = Runtime.getRuntime().availableProcessors();
            favoriteCheckExecutor = Executors.newFixedThreadPool(Math.max(1, coreCount - 1));
        }
        
        favoriteCheckExecutor.execute(() -> {
            boolean isFavorited = false;
            try {
                JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
                JsonObject currentChannelJson = LiveChannelItem.convertChannelToJson(channel);
                
                // xuameng优化：使用更高效的比较方式
                if (favoriteArray != null && favoriteArray.size() > 0) {
                    for (int i = 0; i < favoriteArray.size(); i++) {
                        JsonObject favChannelJson = favoriteArray.get(i).getAsJsonObject();
                        if (LiveChannelItem.isSameChannel(favChannelJson, currentChannelJson)) {
                            isFavorited = true;
                            break;
                        }
                    }
                }
                
                // xuameng更新缓存
                synchronized (favoriteCache) {
                    favoriteCache.put(cacheKey, isFavorited);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                boolean finalIsFavorited = isFavorited;
                if (mContext instanceof Activity) {
                    ((Activity) mContext).runOnUiThread(() -> {
                        callback.onFavoriteChecked(finalIsFavorited, position);
                    });
                }
            }
        });
    }

    // xuameng清理缓存的方法
    public void clearFavoriteCache() {
        synchronized (favoriteCache) {
            favoriteCache.clear();
        }
    }

    // xuameng在数据更新时清理缓存
    @Override
    public void setNewData(@Nullable List<LiveChannelItem> data) {
        clearFavoriteCache();
        super.setNewData(data);
    }

    // xuameng在Adapter销毁时释放资源
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (favoriteCheckExecutor != null && !favoriteCheckExecutor.isShutdown()) {
            favoriteCheckExecutor.shutdownNow();
            favoriteCheckExecutor = null;
        }
        clearFavoriteCache();
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) return;
        int preSelectedChannelIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preSelectedChannelIndex != -1)
            notifyItemChanged(preSelectedChannelIndex);
        if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    public int getSelectedChannelIndex() {
        return selectedChannelIndex;
    }

    public int getSelectedfocusedChannelIndex() {
        return focusedChannelIndex;
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int preFocusedChannelIndex = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (preFocusedChannelIndex != -1)
            notifyItemChanged(preFocusedChannelIndex);
        if (this.focusedChannelIndex != -1)
            notifyItemChanged(this.focusedChannelIndex);
        else if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    /**xuameng
     * 判断当前频道是否已被收藏（同步方法，保留供其他用途）
     */
    private boolean isChannelFavorited(LiveChannelItem channel) {
        if (channel == null) return false;
        JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
        JsonObject currentChannelJson = LiveChannelItem.convertChannelToJson(channel);

        for (int i = 0; i < favoriteArray.size(); i++) {
            JsonObject favChannelJson = favoriteArray.get(i).getAsJsonObject();
            if (LiveChannelItem.isSameChannel(favChannelJson, currentChannelJson)) {
                return true;
            }
        }
        return false;
    }


	/**xuameng
    * 更新指定频道的收藏状态缓存
    * @param channel 频道信息
    * @param isFavorited 是否已收藏
    * @param position 频道在列表中的位置（可选）
    */
    public void updateFavoriteCache(LiveChannelItem channel, boolean isFavorited, int position) {
        if (channel == null) return;
    
        String cacheKey = getChannelCacheKey(channel);
        synchronized (favoriteCache) {
            favoriteCache.put(cacheKey, isFavorited);
        }
    
        // 如果知道位置，只刷新这个item
        if (position >= 0) {
            notifyItemChanged(position);
        } else {
            // 不知道位置时，查找并刷新
            int itemPosition = findItemPosition(channel);
            if (itemPosition >= 0) {
                notifyItemChanged(itemPosition);
            }
        }
    }

    /**xuameng
     * 查找频道在列表中的位置
     */
    private int findItemPosition(LiveChannelItem channel) {
        List<LiveChannelItem> data = getData();
        if (data == null || channel == null) return -1;
    
        for (int i = 0; i < data.size(); i++) {
            LiveChannelItem item = data.get(i);
            if (item != null && item.getChannelName().equals(channel.getChannelName())) {
                // 比较URL或其他标识
                if (item.getUrl() != null && item.getUrl().equals(channel.getUrl())) {
                    return i;
                }
            }
        }
        return -1;
    }

}
