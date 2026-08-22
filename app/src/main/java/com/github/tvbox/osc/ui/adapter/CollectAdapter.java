package com.github.tvbox.osc.ui.adapter;

import android.os.Build;
import android.text.TextUtils;
import android.text.Html;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.squareup.picasso.Picasso;
import com.github.tvbox.osc.util.ImgUtilCollect;   //xuamengBASE64图片

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class CollectAdapter extends BaseQuickAdapter<VodCollect, BaseViewHolder> {
    public CollectAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    private String removeHtmlTag(String info) {
        if (TextUtils.isEmpty(info))
            return "";
        String text = info.replaceAll("\\[a=cr:(?:\\{.*?\\}|\\[.*?\\])\\/](.*?)\\[\\/a]", "$1");
        text = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
                : Html.fromHtml(text).toString();
        return text.replaceAll("\\s", "");
    }

    @Override
    protected void convert(BaseViewHolder helper, VodCollect item) {
    	// takagen99: Add Delete Mode
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        if (HawkConfig.hotVodDelete) {
            tvDel.setVisibility(View.VISIBLE);
        } else {
            tvDel.setVisibility(View.GONE);
        }
        
        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
	//	helper.setVisible(R.id.tvNote, false);
        helper.setText(R.id.tvNote, "⭐我的收藏");
    //    helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvName, removeHtmlTag(item.name));
        TextView tvYear = helper.getView(R.id.tvYear);
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        tvYear.setText(source!=null?source.getName():"🔍搜索影片");
        
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        int radius = AutoSizeUtils.mm2px(mContext, 5);  //xuameng Base64 图片 圆角设置

        //由于部分电视机使用glide报错
        if (!TextUtils.isEmpty(item.pic)) {
            if(ImgUtilCollect.isBase64Image(item.pic)){
                // xuameng 如果是 Base64 图片，解码并设置
                ivThumb.setImageBitmap(
                    ImgUtilCollect.decodeBase64ToRoundBitmap(item.pic, radius)   //xuameng 用这个方法进行圆角设置
                );
            }else {
                Picasso.get()
                        .load(DefaultConfig.checkReplaceProxy(item.pic))
                        .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                                .centerCorp(true)
                                .override(AutoSizeUtils.mm2px(mContext, ImgUtilCollect.defaultWidth), AutoSizeUtils.mm2px(mContext, ImgUtilCollect.defaultHeight))
                                .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                        .placeholder(R.drawable.img_loading_placeholder)
                        .noFade()
                       // .error(R.drawable.img_loading_placeholder)
					    .error(ImgUtilCollect.createTextDrawable(item.name))
                        .into(ivThumb);
            }
        } else {
           // ivThumb.setImageResource(R.drawable.img_loading_placeholder);
			ivThumb.setImageDrawable(ImgUtilCollect.createTextDrawable(item.name));
        }
    }
}
