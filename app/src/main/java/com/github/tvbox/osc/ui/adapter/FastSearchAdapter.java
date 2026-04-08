package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;
import com.github.tvbox.osc.util.ImgUtilXufa;   //xuamengBASE64图片
//import com.squareup.picasso.MemoryPolicy; //xuameng禁用缓存
//import com.squareup.picasso.NetworkPolicy; //xuameng禁用缓存

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class FastSearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    public FastSearchAdapter() {
        super(R.layout.item_search, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {

        // with preview
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvSite, ApiConfig.get().getSource(item.sourceKey).getName());
        helper.setVisible(R.id.tvNote, item.note != null && !item.note.isEmpty());
        if (item.note != null && !item.note.isEmpty()) {
            helper.setText(R.id.tvNote, item.note);
        }
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        int radius = AutoSizeUtils.mm2px(mContext, 5);  //xuameng Base64 图片 圆角设置

        if (!TextUtils.isEmpty(item.pic)) {
            if(ImgUtilXufa.isBase64Image(item.pic)){
                // xuameng 如果是 Base64 图片，解码并设置
                ivThumb.setImageBitmap(
                    ImgUtilXufa.decodeBase64ToRoundBitmap(item.pic, radius)   //xuameng 用这个方法进行圆角设置
                );
            }else {
                Picasso.get()
                        .load(item.pic)
                        .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                                .centerCorp(true)
                                .override(AutoSizeUtils.mm2px(mContext, ImgUtilXufa.defaultWidth), AutoSizeUtils.mm2px(mContext, ImgUtilXufa.defaultHeight))
                                .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                        .placeholder(R.drawable.img_loading_placeholder)
                        .noFade()
                      //.error(R.drawable.img_loading_placeholder)
					    .error(ImgUtilXufa.createTextDrawable(item.name))
                      //.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)  //xuameng禁用缓存
                      //.networkPolicy(NetworkPolicy.NO_CACHE)   //xuameng禁用缓存
                        .into(ivThumb);
            }
        } else {
           // ivThumb.setImageResource(R.drawable.img_loading_placeholder);
			ivThumb.setImageDrawable(ImgUtilXufa.createTextDrawable(item.name));
        }

    }
}
