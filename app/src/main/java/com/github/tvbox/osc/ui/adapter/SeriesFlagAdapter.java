package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.TextView;
import android.app.Activity;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;

/**   xuameng 选源列表
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class SeriesFlagAdapter extends BaseQuickAdapter<VodInfo.VodSeriesFlag, BaseViewHolder> {
    public SeriesFlagAdapter() {
        super(R.layout.item_series_flag, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeriesFlag item) {
        TextView tvSeries = helper.getView(R.id.tvSeriesFlag);
        View select = helper.getView(R.id.tvSeriesFlagSelect);
        if (item.selected) {
            select.setVisibility(View.VISIBLE);
        } else {
            select.setVisibility(View.GONE);
        }
        helper.setText(R.id.tvSeriesFlag, item.name);
        View mSeriesGroupView = ((Activity) helper.itemView.getContext()).findViewById(R.id.mSeriesGroupView);
        if (mSeriesGroupView != null && mSeriesGroupView.getVisibility() == View.VISIBLE) {
            helper.itemView.setNextFocusDownId(R.id.mSeriesGroupView);
        }else {
            helper.itemView.setNextFocusDownId(R.id.mGridView);
        }
        if (helper.getLayoutPosition() == getData().size() - 1) {
            helper.itemView.setId(View.generateViewId());
            helper.itemView.setNextFocusRightId(helper.itemView.getId());  
        }else {
            helper.itemView.setNextFocusRightId(View.NO_ID);  //xuameng不超出item
        }
		helper.itemView.setNextFocusUpId(R.id.tvPlay);
    }
}
