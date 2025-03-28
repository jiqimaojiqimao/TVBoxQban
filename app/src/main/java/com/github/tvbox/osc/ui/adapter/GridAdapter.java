package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.MD5;
import com.squareup.picasso.Picasso;
import com.github.tvbox.osc.util.ImgUtil;   //xuamengBASE64图片

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList = false;

    public GridAdapter(boolean l) {
        super( l ? R.layout.item_list:R.layout.item_grid, new ArrayList<>());
        this.mShowList = l;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if(this.mShowList) {
        if (TextUtils.isEmpty(item.note)) {
        //    helper.setVisible(R.id.tvNote, false);
			helper.setText(R.id.tvNote, "暂无信息");
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        if (TextUtils.isEmpty(item.name)) {
            helper.setText(R.id.tvName, "聚汇影视");
        } else {
            helper.setText(R.id.tvName, item.name);
        }
 //           helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            //由于部分电视机使用glide报错
            if (!TextUtils.isEmpty(item.pic)) {
                item.pic=item.pic.trim();
                if(ImgUtil.isBase64Image(item.pic)){
                    // 如果是 Base64 图片，解码并设置
                    ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(item.pic));
                }else {
                    Picasso.get()
                            .load(DefaultConfig.checkReplaceProxy(item.pic))
                            .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                                    .centerCorp(true)
                                    .override(AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 320))
                                    .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                            .placeholder(R.drawable.img_loading_placeholder)
                            .noFade()
                       //     .error(R.drawable.img_loading_placeholder)
							.error(ImgUtil.createTextDrawable(item.name))
                            .into(ivThumb);
                }
            } else {
              //  ivThumb.setImageResource(R.drawable.img_loading_placeholder);
				ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            }
            return;
        }

        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        /*if (TextUtils.isEmpty(item.lang)) {
            tvLang.setVisibility(View.GONE);
        } else {
            tvLang.setText(item.lang);
            tvLang.setVisibility(View.VISIBLE);
        }*/
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        /*if (TextUtils.isEmpty(item.area)) {
            tvArea.setVisibility(View.GONE);
        } else {
            tvArea.setText(item.area);
            tvArea.setVisibility(View.VISIBLE);
        }*/
        if (TextUtils.isEmpty(item.note)) {
        //    helper.setVisible(R.id.tvNote, false);
			helper.setText(R.id.tvNote, "暂无信息");
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        if (TextUtils.isEmpty(item.name)) {
            helper.setText(R.id.tvName, "聚汇影视");
        } else {
            helper.setText(R.id.tvName, item.name);
        }
 //       helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        //由于部分电视机使用glide报错
        if (!TextUtils.isEmpty(item.pic)) {
            item.pic=item.pic.trim();
            if(ImgUtil.isBase64Image(item.pic)){
                // 如果是 Base64 图片，解码并设置
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(item.pic));
            }else {
                Picasso.get()
                        .load(DefaultConfig.checkReplaceProxy(item.pic))
                        .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                                .centerCorp(true)
                                .override(AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 320))
                                .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                        .placeholder(R.drawable.img_loading_placeholder)
                        .noFade()
                    //    .error(R.drawable.img_loading_placeholder)
						.error(ImgUtil.createTextDrawable(item.name))
                        .into(ivThumb);
            }
        } else {
           // ivThumb.setImageResource(R.drawable.img_loading_placeholder);
			ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
    }
}
