package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import android.graphics.Typeface;   //xuameng字体加粗

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.ParseBean;

import java.util.ArrayList;

public class ParseAdapter extends BaseQuickAdapter<ParseBean, BaseViewHolder> {
    public ParseAdapter() {
        super(R.layout.item_play_parse, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, ParseBean item) {
        TextView tvParse = helper.getView(R.id.tvParse);
        tvParse.setVisibility(View.VISIBLE);

        if (item.isDefault()) {
            tvParse.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
            tvParse.setTypeface(Typeface.DEFAULT_BOLD);  // xuameng新增加粗
        } else {
            tvParse.setTextColor(Color.WHITE);
            tvParse.setTypeface(Typeface.DEFAULT);  // xuameng新增恢复默认
        }
        tvParse.setText(item.getName());

        tvParse.setOnFocusChangeListener(new View.OnFocusChangeListener() {   //xuameng // xuameng新增焦点状态刷新颜色改变
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (item.isDefault()) {
                    if (hasFocus) {
                        tvParse.setTextColor(Color.WHITE);
                    } else {
                        tvParse.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
                    }
                }
            }
        });
    }
}
