package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

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

        tvParse.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (item.isDefault()) {
                    if (hasFocus) {
                        tvParse.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
                    } else {
                        tvParse.setTextColor(Color.WHITE);
                    }
                }
            }
        });

        if (item.isDefault()) {
            tvParse.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
        } else {
            tvParse.setTextColor(Color.WHITE);
        }
        tvParse.setText(item.getName());
    }
}
