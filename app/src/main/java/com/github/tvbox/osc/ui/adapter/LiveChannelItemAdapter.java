package com.github.tvbox.osc.ui.adapter;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
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
 * @date 2026/2/10
 * @description 直播频道项适配器
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private ExecutorService favoriteCheckExecutor;
	private Handler mHandler = new Handler(Looper.getMainLooper());

    // 使用LinkedHashMap实现LRU缓存
    private static final int MAX_CACHE_SIZE = 200;
    private Map<String, Boolean> favoriteCache = new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    // RecyclerView和LayoutManager引用
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;

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
        
        // 先设置为GONE，等待异步检查结果
        tvFavoriteStar.setVisibility(View.GONE);
        
        final int position = holder.getLayoutPosition();
        
        // 总是检查收藏状态，但只在可见时更新UI
        checkChannelFavoriteAsync(item, position, new FavoriteCheckCallback() {
            @Override
            public void onFavoriteChecked(boolean isFavorited, int checkedPosition) {
                // 检查当前位置是否仍然可见
                if (isPositionVisible(checkedPosition)) {
                    // 找到对应的ViewHolder
                    if (mRecyclerView != null) {
                        RecyclerView.ViewHolder viewHolder = mRecyclerView.findViewHolderForAdapterPosition(checkedPosition);
                        if (viewHolder instanceof BaseViewHolder) {
                            BaseViewHolder baseHolder = (BaseViewHolder) viewHolder;
                            TextView starView = baseHolder.getView(R.id.ivFavoriteStar);
                            if (starView != null) {
                                if (isFavorited) {
                                    starView.setVisibility(View.VISIBLE);
                                    starView.setText("★");
                                    starView.setTextColor(Color.parseColor("#FFD700"));
                                }
                                // 如果不收藏，保持GONE状态
                            }
                        }
                    }
                }
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

    /**
     * 收藏状态检查回调接口
     */
    public interface FavoriteCheckCallback {
        void onFavoriteChecked(boolean isFavorited, int position);
    }

    /**
     * 生成缓存键（优化版本）
     * 使用频道名和当前URL的哈希值作为缓存键
     */
    private String getChannelCacheKey(LiveChannelItem channel) {
        return channel.getChannelName() + "|"
                + (channel.getUrl() != null ? channel.getUrl().hashCode() : 0);
    }

    /**
     * 异步检查频道收藏状态（先检查缓存）
     */
    private void checkChannelFavoriteAsync(LiveChannelItem channel, int position, FavoriteCheckCallback callback) {
        if (channel == null || callback == null) {
            return;
        }

        // 先检查缓存
        String cacheKey = getChannelCacheKey(channel);
        Boolean cachedResult = favoriteCache.get(cacheKey);
        if (cachedResult != null) {
            // 缓存命中，直接回调
            boolean finalResult = cachedResult;
            if (mContext instanceof Activity) {
                ((Activity) mContext).runOnUiThread(() -> 
                    callback.onFavoriteChecked(finalResult, position)
                );
            }
            return;
        }

        // 缓存未命中，执行异步查询
        if (favoriteCheckExecutor == null) {
            int coreCount = Runtime.getRuntime().availableProcessors();
            favoriteCheckExecutor = Executors.newFixedThreadPool(Math.max(1, coreCount - 1));
        }

        favoriteCheckExecutor.execute(() -> {
            boolean isFavorited = false;
            try {
                JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
                JsonObject currentChannelJson = LiveChannelItem.convertChannelToJson(channel);

                // 优化：使用更高效的比较方式
                if (favoriteArray != null && favoriteArray.size() > 0) {
                    for (int i = 0; i < favoriteArray.size(); i++) {
                        JsonObject favChannelJson = favoriteArray.get(i).getAsJsonObject();
                        if (LiveChannelItem.isSameChannel(favChannelJson, currentChannelJson)) {
                            isFavorited = true;
                            break;
                        }
                    }
                }

                // 更新缓存
                synchronized (favoriteCache) {
                    favoriteCache.put(cacheKey, isFavorited);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                boolean finalIsFavorited = isFavorited;
                if (mContext instanceof Activity) {
                    ((Activity) mContext).runOnUiThread(() -> 
                        callback.onFavoriteChecked(finalIsFavorited, position)
                    );
                }
            }
        });
    }

    /**
     * 清理收藏缓存
     */
    public void clearFavoriteCache() {
        synchronized (favoriteCache) {
            favoriteCache.clear();
        }
    }

    /**
     * 设置新数据（重写方法）
     */
    @Override
    public void setNewData(@Nullable List<LiveChannelItem> data) {
        clearFavoriteCache();
        super.setNewData(data);
        
        // 数据设置完成后，延迟刷新可见项的收藏状态
        scheduleInitialRefresh();
    }

    /**
     * 调度初始刷新
     */
    private void scheduleInitialRefresh() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mRecyclerView != null && mLayoutManager != null) {
                    refreshFavoriteStatusForVisibleItems();
                }
            }
        }, 100); // 延迟150ms，确保RecyclerView布局完成
    }

    /**
     * Adapter附着到RecyclerView时获取引用
     */
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.mRecyclerView = recyclerView;
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            this.mLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        }

        // 添加滚动监听，用于在滚动停止后刷新新出现的项
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // 当滚动停止时，检查当前可见项是否需要刷新收藏状态
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    refreshFavoriteStatusForVisibleItems();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // 可以在滚动过程中进行一些优化，例如延迟检查
            }
        });
    }

    /**
     * Adapter销毁时释放资源
     */
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (favoriteCheckExecutor != null && !favoriteCheckExecutor.isShutdown()) {
            favoriteCheckExecutor.shutdownNow();
            favoriteCheckExecutor = null;
        }
        clearFavoriteCache();

        // 清理引用
        this.mRecyclerView = null;
        this.mLayoutManager = null;
    }

    /**
     * 判断指定位置是否在RecyclerView的当前可见范围内
     */
    private boolean isPositionVisible(int position) {
        if (mLayoutManager == null) {
            // 如果没有LayoutManager，保守起见返回true，执行原有逻辑
            return true;
        }
        int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = mLayoutManager.findLastVisibleItemPosition();
        return position >= firstVisible && position <= lastVisible;
    }

/**
 * 刷新当前所有可见Item的收藏状态
 * 优化版本：使用直接更新ViewHolder的方式，避免完整重绑定
 */
private void refreshFavoriteStatusForVisibleItems() {
    if (mLayoutManager == null) {
        return;
    }

    int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
    int lastVisible = mLayoutManager.findLastVisibleItemPosition();
    
    if (firstVisible < 0 || lastVisible < 0) {
        return;
    }

    // 批量检查可见项的收藏状态
    List<Integer> positionsToCheck = new ArrayList<>();
    List<LiveChannelItem> itemsToCheck = new ArrayList<>();
    
    for (int i = firstVisible; i <= lastVisible; i++) {
        LiveChannelItem item = getItem(i);
        if (item != null) {
            String cacheKey = getChannelCacheKey(item);
            Boolean cachedResult = favoriteCache.get(cacheKey);
            
            if (cachedResult != null) {
                // 缓存命中，直接更新UI
                updateFavoriteUI(i, cachedResult);
            } else {
                // 缓存未命中，添加到待检查列表
                positionsToCheck.add(i);
                itemsToCheck.add(item);
            }
        }
    }
    
    // 批量检查未缓存的项
    if (!itemsToCheck.isEmpty()) {
        batchCheckFavorites(itemsToCheck, positionsToCheck);
    }
}


    /**
     * 批量检查收藏状态，减少线程切换开销
     */
    private void batchCheckFavorites(List<LiveChannelItem> items, List<Integer> positions) {
        if (favoriteCheckExecutor == null) {
            int coreCount = Runtime.getRuntime().availableProcessors();
            favoriteCheckExecutor = Executors.newFixedThreadPool(Math.max(1, coreCount - 1));
        }
        
        favoriteCheckExecutor.execute(() -> {
            try {
                JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
                
                for (int i = 0; i < items.size(); i++) {
                    LiveChannelItem item = items.get(i);
                    int position = positions.get(i);
                    boolean isFavorited = false;
                    
                    if (favoriteArray != null && favoriteArray.size() > 0) {
                        JsonObject currentChannelJson = LiveChannelItem.convertChannelToJson(item);
                        for (int j = 0; j < favoriteArray.size(); j++) {
                            JsonObject favChannelJson = favoriteArray.get(j).getAsJsonObject();
                            if (LiveChannelItem.isSameChannel(favChannelJson, currentChannelJson)) {
                                isFavorited = true;
                                break;
                            }
                        }
                    }
                    
                    // 更新缓存
                    String cacheKey = getChannelCacheKey(item);
                    synchronized (favoriteCache) {
                        favoriteCache.put(cacheKey, isFavorited);
                    }
                    
                    // 更新UI
                    final int finalPosition = position;
                    final boolean finalIsFavorited = isFavorited;
                    if (mContext instanceof Activity) {
                        ((Activity) mContext).runOnUiThread(() -> {
                            if (isPositionVisible(finalPosition)) {
                                updateFavoriteUI(finalPosition, finalIsFavorited);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Getter和Setter方法
    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) {
            return;
        }
        int preSelectedChannelIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preSelectedChannelIndex != -1) {
            notifyItemChanged(preSelectedChannelIndex);
        }
        if (this.selectedChannelIndex != -1) {
            notifyItemChanged(this.selectedChannelIndex);
        }
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
        if (preFocusedChannelIndex != -1) {
            notifyItemChanged(preFocusedChannelIndex);
        }
        if (this.focusedChannelIndex != -1) {
            notifyItemChanged(this.focusedChannelIndex);
        } else if (this.selectedChannelIndex != -1) {
            notifyItemChanged(this.selectedChannelIndex);
        }
    }

    /**
     * 判断当前频道是否已被收藏（同步方法，保留供其他用途）
     */
    private boolean isChannelFavorited(LiveChannelItem channel) {
        if (channel == null) {
            return false;
        }
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

    /**
     * 更新指定频道的收藏状态缓存
     *
     * @param channel     频道信息
     * @param isFavorited 是否已收藏
     * @param position    频道在列表中的位置（可选）
     */
    public void updateFavoriteCache(LiveChannelItem channel, boolean isFavorited, int position) {
        if (channel == null) {
            return;
        }

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

    /**
     * 查找频道在列表中的位置
     */
    private int findItemPosition(LiveChannelItem channel) {
        List<LiveChannelItem> data = getData();
        if (data == null || channel == null) {
            return -1;
        }

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

    /**
     * 直接更新指定位置的收藏状态UI，避免完整重绑定
     */
    private void updateFavoriteUI(int position, boolean isFavorited) {
        if (mRecyclerView == null || position < 0 || position >= getItemCount()) {
            return;
        }

    // 添加可见性检查
    if (!isPositionVisible(position)) {
        return;
    }
        
        // 通过RecyclerView找到对应位置的ViewHolder
        RecyclerView.ViewHolder viewHolder = mRecyclerView.findViewHolderForAdapterPosition(position);
        
        // 检查是否是当前Adapter的ViewHolder
        if (viewHolder instanceof BaseViewHolder) {
            BaseViewHolder holder = (BaseViewHolder) viewHolder;
            
            // 直接更新UI控件
            TextView tvFavoriteStar = holder.getView(R.id.ivFavoriteStar);
            if (tvFavoriteStar != null) {
                if (isFavorited) {
                    tvFavoriteStar.setVisibility(View.VISIBLE);
                    tvFavoriteStar.setText("★");
                    tvFavoriteStar.setTextColor(Color.parseColor("#FFD700"));
                } else {
                    tvFavoriteStar.setVisibility(View.GONE);
                }
            }
        }
    }
}
