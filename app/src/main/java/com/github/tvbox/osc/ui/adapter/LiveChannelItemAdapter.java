package com.github.tvbox.osc.ui.adapter;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

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
 * 直播频道项适配器
 * 优化点：
 * 1. 基于可见性直接更新ViewHolder，避免完整重绑定
 * 2. 延迟刷新+批量检查，减少性能开销
 * 3. LRU缓存+状态变化检测，避免无效UI操作
 *
 * @author xuameng
 * @date 2026/2/10
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    // ==================== 成员变量 ====================
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private ExecutorService favoriteCheckExecutor;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mRefreshRunnable;
    private static final long REFRESH_DELAY_MS = 100;

    // 使用LinkedHashMap实现LRU缓存（最大200条）
    private static final int MAX_CACHE_SIZE = 200;
    private Map<String, Boolean> favoriteCache = new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    // RecyclerView核心引用
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;

    // 记录上一次可见范围（避免重复刷新）
    private int mLastFirstVisible = -1;
    private int mLastLastVisible = -1;

    // ==================== 构造方法 ====================
    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());
    }

    // ==================== 核心方法 ====================
    @Override
    protected void convert(@NonNull BaseViewHolder holder, @Nullable LiveChannelItem item) {
        // 1. 初始化基础视图
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        TextView tvFavoriteStar = holder.getView(R.id.ivFavoriteStar);

        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // 2. 基于缓存初始化收藏星标（避免重复设置）
        final int position = holder.getLayoutPosition();
        String cacheKey = getChannelCacheKey(item);
        Boolean cachedResult = favoriteCache.get(cacheKey);
        if (cachedResult != null && cachedResult) {
            tvFavoriteStar.setVisibility(View.VISIBLE);
            tvFavoriteStar.setText("★");
            tvFavoriteStar.setTextColor(Color.parseColor("#FFD700"));
        } else {
            tvFavoriteStar.setVisibility(View.GONE);
        }

        // 3. 异步检查收藏状态（仅状态变化时更新UI）
        checkChannelFavoriteAsync(item, position, new FavoriteCheckCallback() {
            @Override
            public void onFavoriteChecked(boolean isFavorited, int checkedPosition) {
                if (isPositionVisible(checkedPosition) && mRecyclerView != null) {
                    RecyclerView.ViewHolder viewHolder = mRecyclerView.findViewHolderForAdapterPosition(checkedPosition);
                    if (viewHolder instanceof BaseViewHolder) {
                        BaseViewHolder baseHolder = (BaseViewHolder) viewHolder;
                        TextView starView = baseHolder.getView(R.id.ivFavoriteStar);
                        if (starView != null) {
                            // 仅当状态变化时才更新（减少无效UI操作）
                            boolean currentVisible = starView.getVisibility() == View.VISIBLE;
                            if (currentVisible != isFavorited) {
                                starView.setVisibility(isFavorited ? View.VISIBLE : View.GONE);
                                if (isFavorited) {
                                    starView.setText("★");
                                    starView.setTextColor(Color.parseColor("#FFD700"));
                                }
                            }
                        }
                    }
                }
            }
        });

        // 4. 设置选中/焦点状态颜色
        int channelIndex = item.getChannelIndex();
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            tvChannelNum.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
            tvChannel.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
        } else {
            tvChannelNum.setTextColor(Color.WHITE);
            tvChannel.setTextColor(Color.WHITE);
        }
    }

    // ==================== 缓存与异步检查 ====================
    /**
     * 生成缓存键（频道名+URL哈希，确保唯一性）
     */
    private String getChannelCacheKey(@NonNull LiveChannelItem channel) {
        return channel.getChannelName() + "|" +
                (channel.getUrl() != null ? channel.getUrl().hashCode() : 0);
    }

    /**
     * 异步检查频道收藏状态（先查缓存，未命中则异步查询）
     */
    private void checkChannelFavoriteAsync(@NonNull LiveChannelItem channel, int position, @NonNull FavoriteCheckCallback callback) {
        if (channel == null || callback == null) return;

        // 1. 缓存命中：直接回调
        String cacheKey = getChannelCacheKey(channel);
        Boolean cachedResult = favoriteCache.get(cacheKey);
        if (cachedResult != null) {
            runOnUiThread(() -> callback.onFavoriteChecked(cachedResult, position));
            return;
        }

        // 2. 缓存未命中：初始化线程池+异步查询
        if (favoriteCheckExecutor == null) {
            int coreCount = Runtime.getRuntime().availableProcessors();
            favoriteCheckExecutor = Executors.newFixedThreadPool(Math.max(1, coreCount - 1));
        }

        favoriteCheckExecutor.execute(() -> {
            boolean isFavorited = false;
            try {
                // 从Hawk获取收藏列表
                JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
                JsonObject currentChannelJson = LiveChannelItem.convertChannelToJson(channel);

                // 高效比较：遍历收藏列表，匹配则标记为已收藏
                if (favoriteArray != null && favoriteArray.size() > 0) {
                    for (int i = 0; i < favoriteArray.size(); i++) {
                        JsonObject favChannelJson = favoriteArray.get(i).getAsJsonObject();
                        if (LiveChannelItem.isSameChannel(favChannelJson, currentChannelJson)) {
                            isFavorited = true;
                            break;
                        }
                    }
                }

                // 更新LRU缓存
                synchronized (favoriteCache) {
                    favoriteCache.put(cacheKey, isFavorited);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 回调到主线程更新UI
                runOnUiThread(() -> callback.onFavoriteChecked(isFavorited, position));
            }
        });
    }

    /**
     * 安全切换到主线程（避免类型转换重复代码）
     */
    private void runOnUiThread(Runnable runnable) {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(runnable);
        }
    }

    // ==================== 数据刷新与可见性 ====================
    @Override
    public void setNewData(@Nullable List<LiveChannelItem> data) {
        clearFavoriteCache();
        super.setNewData(data);
        scheduleInitialRefresh(); // 数据更新后延迟刷新可见项
    }

    /**
     * 调度初始刷新（确保RecyclerView布局完成后再刷新）
     */
    private void scheduleInitialRefresh() {
        mHandler.postDelayed(() -> {
            if (mRecyclerView != null && mLayoutManager != null) {
                refreshFavoriteStatusForVisibleItems();
            }
        }, 100); // 延迟100ms，避免布局未完成导致获取可见范围失败
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
        // 获取LinearLayoutManager（仅线性布局支持可见范围计算）
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            mLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        }

        // 添加滚动监听：滚动停止后延迟刷新新可见项
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // 取消未执行的刷新任务（避免滚动中重复刷新）
                if (mRefreshRunnable != null) {
                    mHandler.removeCallbacks(mRefreshRunnable);
                }
                // 滚动停止：延迟100ms刷新（减少频繁操作）
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mRefreshRunnable = () -> refreshFavoriteStatusForVisibleItems();
                    mHandler.postDelayed(mRefreshRunnable, REFRESH_DELAY_MS);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // 可扩展：滚动中延迟检查（如防抖）
            }
        });
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        // 释放线程池
        if (favoriteCheckExecutor != null && !favoriteCheckExecutor.isShutdown()) {
            favoriteCheckExecutor.shutdownNow();
            favoriteCheckExecutor = null;
        }
        // 清理缓存和引用
        clearFavoriteCache();
        mRecyclerView = null;
        mLayoutManager = null;
    }

    /**
     * 判断位置是否在RecyclerView可见范围内
     */
    private boolean isPositionVisible(int position) {
        if (mLayoutManager == null) return true; // 无LayoutManager时保守处理
        int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = mLayoutManager.findLastVisibleItemPosition();
        return position >= firstVisible && position <= lastVisible;
    }

    // ==================== 批量刷新与UI更新 ====================
    /**
     * 刷新当前可见项的收藏状态（仅刷新新出现的项，避免重复）
     */
    private void refreshFavoriteStatusForVisibleItems() {
        if (mLayoutManager == null) return;

        int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = mLayoutManager.findLastVisibleItemPosition();
        if (firstVisible < 0 || lastVisible < 0) return;

        // 计算需刷新的起始位置（仅刷新新出现的项）
        int start = (mLastLastVisible < firstVisible) ? firstVisible : Math.max(firstVisible, mLastLastVisible + 1);

        // 收集需检查的项（缓存命中的直接更新，未命中的批量检查）
        List<Integer> positionsToCheck = new ArrayList<>();
        List<LiveChannelItem> itemsToCheck = new ArrayList<>();
        for (int i = start; i <= lastVisible; i++) {
            LiveChannelItem item = getItem(i);
            if (item != null) {
                String cacheKey = getChannelCacheKey(item);
                Boolean cachedResult = favoriteCache.get(cacheKey);
                if (cachedResult != null) {
                    updateFavoriteUI(i, cachedResult);
                } else {
                    positionsToCheck.add(i);
                    itemsToCheck.add(item);
                }
            }
        }

        // 批量检查未缓存的项（减少线程切换开销）
        if (!itemsToCheck.isEmpty()) {
            batchCheckFavorites(itemsToCheck, positionsToCheck);
        }

        // 更新可见范围记录
        mLastFirstVisible = firstVisible;
        mLastLastVisible = lastVisible;
    }

    /**
     * 批量检查未缓存的收藏状态（合并线程任务，提升性能）
     */
    private void batchCheckFavorites(@NonNull List<LiveChannelItem> items, @NonNull List<Integer> positions) {
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

                    // 检查是否收藏
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

                    // 更新缓存+UI
                    String cacheKey = getChannelCacheKey(item);
                    synchronized (favoriteCache) {
                        favoriteCache.put(cacheKey, isFavorited);
                    }
                    // 仅可见项更新UI
                    if (isPositionVisible(position)) {
                        runOnUiThread(() -> updateFavoriteUI(position, isFavorited));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ==================== 缓存与UI更新 ====================
    /**
     * 清理收藏缓存
     */
    public void clearFavoriteCache() {
        synchronized (favoriteCache) {
            favoriteCache.clear();
        }
    }

    /**
     * 直接更新指定位置的收藏UI（避免notifyItemChanged）
     */
    private void updateFavoriteUI(int position, boolean isFavorited) {
        if (mRecyclerView == null || position < 0 || position >= getItemCount()) return;
        if (!isPositionVisible(position)) return; // 不可见项不更新

        // 获取ViewHolder并更新UI
        RecyclerView.ViewHolder viewHolder = mRecyclerView.findViewHolderForAdapterPosition(position);
        if (viewHolder instanceof BaseViewHolder) {
            BaseViewHolder holder = (BaseViewHolder) viewHolder;
            TextView tvFavoriteStar = holder.getView(R.id.ivFavoriteStar);
            if (tvFavoriteStar != null) {
                tvFavoriteStar.setVisibility(isFavorited ? View.VISIBLE : View.GONE);
                if (isFavorited) {
                    tvFavoriteStar.setText("★");
                    tvFavoriteStar.setTextColor(Color.parseColor("#FFD700"));
                }
            }
        }
    }

    // ==================== 回调接口与工具方法 ====================
    /**
     * 收藏状态检查回调接口
     */
    public interface FavoriteCheckCallback {
        void onFavoriteChecked(boolean isFavorited, int position);
    }

    /**
     * 更新收藏缓存并同步UI（外部调用入口）
     *
     * @param channel     频道信息
     * @param isFavorited 是否已收藏
     * @param position    频道位置（可选，未知则自动查找）
     */
    public void updateFavoriteCache(@NonNull LiveChannelItem channel, boolean isFavorited, int position) {
        String cacheKey = getChannelCacheKey(channel);
        synchronized (favoriteCache) {
            favoriteCache.put(cacheKey, isFavorited);
        }

        // 直接更新UI（优先用position，未知则查找）
        if (position >= 0) {
            updateFavoriteUI(position, isFavorited);
        } else {
            int itemPosition = findItemPosition(channel);
            if (itemPosition >= 0) {
                updateFavoriteUI(itemPosition, isFavorited);
            }
        }
    }

    /**
     * 查找频道在列表中的位置（频道名+URL双重匹配）
     */
    private int findItemPosition(@NonNull LiveChannelItem channel) {
        List<LiveChannelItem> data = getData();
        if (data == null) return -1;

        for (int i = 0; i < data.size(); i++) {
            LiveChannelItem item = data.get(i);
            if (item != null && item.getChannelName().equals(channel.getChannelName())) {
                if (item.getUrl() != null && item.getUrl().equals(channel.getUrl())) {
                    return i;
                }
            }
        }
        return -1;
    }

    // ==================== Getter/Setter ====================
    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) return;
        int preSelectedChannelIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preSelectedChannelIndex != -1) notifyItemChanged(preSelectedChannelIndex);
        if (this.selectedChannelIndex != -1) notifyItemChanged(this.selectedChannelIndex);
    }

    public int getSelectedChannelIndex() {
        return selectedChannelIndex;
    }

    public int getSelectedFocusedChannelIndex() {
        return focusedChannelIndex;
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int preFocusedChannelIndex = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (preFocusedChannelIndex != -1) notifyItemChanged(preSelectedChannelIndex);
        if (this.focusedChannelIndex != -1) {
            notifyItemChanged(this.focusedChannelIndex);
        } else if (this.selectedChannelIndex != -1) {
            notifyItemChanged(this.selectedChannelIndex);
        }
    }
}
