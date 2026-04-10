package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;

/**
 * @author xuameng
 * @date 2026/04/08
 * @description: 首页分类菜单 Adapter（修复焦点及多高亮等BUG）
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {

    // ✅ 唯一真实选中状态
    private int selectedPosition = 0;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    /**
     * ✅ 外部设置选中项（焦点 / 滑动 / 点击 统一入口）
     */
    public void setSelectedPosition(int pos) {
        if (pos < 0 || pos >= getData().size()) return;

        int old = selectedPosition;
        selectedPosition = pos;

        // 只更新受影响的项目，避免不必要的刷新
        if (old != selectedPosition) {
            if (old >= 0 && old < getData().size()) {
                notifyItemChanged(old);  //更新上一个选中项状态
            }
            notifyItemChanged(selectedPosition);  //更新当前选中项状态
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        int pos = helper.getAdapterPosition();
        boolean isSelected = pos == selectedPosition;
        
        // 主页（位置0）不显示筛选图标
        boolean isHomePage = pos == 0;

        helper.setText(R.id.tvTitle, item.name);

        helper.setTextColor(
                R.id.tvTitle,
                isSelected
                        ? mContext.getResources().getColor(R.color.color_FFFFFF)
                        : mContext.getResources().getColor(R.color.color_BBFFFFFF)
        );

        helper.setTypeface(
                R.id.tvTitle,
                isSelected
                        ? Typeface.DEFAULT_BOLD
                        : Typeface.DEFAULT
        );

        // 创建圆角背景drawable
        GradientDrawable selectedBackground = new GradientDrawable();
        selectedBackground.setColor(mContext.getResources().getColor(R.color.color_BD0CADE2));
        selectedBackground.setCornerRadius(mContext.getResources().getDimension(R.dimen.vs_20));

        // 设置背景：选中时有背景，非选中时无背景
        if (isSelected) {
            helper.itemView.setBackground(selectedBackground);
        } else {
            helper.itemView.setBackground(null);
        }

        // 设置缩放
        helper.itemView.setPivotX(helper.itemView.getWidth() / 2f);
        helper.itemView.setPivotY(helper.itemView.getHeight() / 2f);
        float targetScaleX = isSelected ? 1.05f : 1.0f;
        float targetScaleY = isSelected ? 1.05f : 1.0f;
        helper.itemView.setScaleX(targetScaleX);
        helper.itemView.setScaleY(targetScaleY);

        // 筛选图标显示逻辑：仅在当前选中的item上显示
        boolean hasFilterSelected = isSelected && !isHomePage && item.filterSelectCount() > 0;
        boolean hasFiltersAvailable = isSelected && !isHomePage && !item.filters.isEmpty() && item.filterSelectCount() <= 0;
        
        // 只有在符合条件时才显示对应图标，否则隐藏
        helper.setGone(R.id.tvFilterColor, hasFilterSelected);
        helper.setGone(R.id.tvFilter, hasFiltersAvailable);
    }
}
