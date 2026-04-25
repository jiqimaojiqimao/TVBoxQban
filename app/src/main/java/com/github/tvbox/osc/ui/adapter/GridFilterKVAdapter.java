package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import android.widget.TextView;
import android.view.View; // xuameng导入 View 类

import java.util.ArrayList;

/**
 * @author xuameng
 * @date :2026/04/25
 * @description:  焦点状态全面修复
 */

public class GridFilterKVAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private int selectedPosition = -1;    // xuameng记录当前选中
    private int lastSelectedPosition = -1; // xuameng记录上次选中
    private int defaultColor;    //xuameng默认颜色
    private int selectedColor;   //xuameng选中颜色
    
    public GridFilterKVAdapter(int defaultColor, int selectedColor) {   //xuameng传入颜色
        super(R.layout.item_grid_filter_value, new ArrayList<>());
        this.defaultColor = defaultColor;
        this.selectedColor = selectedColor;
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {  //xuameng新增方法修正多个高亮BUG
        TextView valueTv = helper.getView(R.id.filterValue);
        helper.setText(R.id.filterValue, item);
    
        // 初始状态设置
        int position = helper.getAdapterPosition();
        boolean isSelected = (position == selectedPosition);
    
        if (isSelected) {
            valueTv.getPaint().setFakeBoldText(true);
            // 默认选中但未焦点时为绿色
            valueTv.setTextColor(selectedColor); // selectedColor 原为绿色
        } else {
            valueTv.getPaint().setFakeBoldText(false);
            valueTv.setTextColor(defaultColor); // defaultColor 原为白色
        }
    
        // 设置焦点变化监听
        valueTv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                TextView tv = (TextView) v;
                if (position == selectedPosition) {
                    if (hasFocus) {
                        // 选中且获取焦点：白色加粗
                        tv.setTextColor(defaultColor); // defaultColor 为白色
                        tv.getPaint().setFakeBoldText(true);
                    } else {
                        // 选中但未获取焦点：绿色加粗
                        tv.setTextColor(selectedColor); // selectedColor 为绿色
                        tv.getPaint().setFakeBoldText(true);
                    }
                } else {
                    // 未选中项：恢复默认
                    tv.getPaint().setFakeBoldText(false);
                    tv.setTextColor(defaultColor);
                }
            }
        });
    }
    
    public void setSelectedPosition(int position) {    //xuameng新增方法修正多个高亮BUG
        // 记录上一次选中的位置
        lastSelectedPosition = selectedPosition;
        this.selectedPosition = position;
        // 如果存在上一次选中的 item，更新它的状态   刷单个item避免焦点乱跳
        if (lastSelectedPosition != -1) {
            notifyItemChanged(lastSelectedPosition);
        }
        // 更新当前选中的 item  刷单个item避免焦点乱跳
        notifyItemChanged(selectedPosition);
    }
    
    public int getSelectedPosition() {    //xuameng新增方法修正多个高亮BUG
        return selectedPosition;
    }
}


