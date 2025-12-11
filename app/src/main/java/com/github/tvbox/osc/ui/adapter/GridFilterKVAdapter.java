package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import android.widget.TextView;

import java.util.ArrayList;

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
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.filterValue, item);
        
        TextView valueTv = helper.getView(R.id.filterValue);
        if (helper.getAdapterPosition() == selectedPosition) {         //xuameng新增方法修正多个高亮BUG
            valueTv.getPaint().setFakeBoldText(true);
            valueTv.setTextColor(selectedColor);
        } else {
            valueTv.getPaint().setFakeBoldText(false);           //xuameng新增方法修正多个高亮BUG
            valueTv.setTextColor(defaultColor);
        }
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


