package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.ViewGroup;

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
 * @xuameng
 * @date :2026/04/05
 * 增加Base64 图片圆角处理
 * 增加如果style是list直接显示文件夹样式  删除所有mShowList判断
 * GridAdapter 支持传入 style 如style 类型不是list 就用style来设置图片的宽高比例，
 * 如果不传 style 则保留旧的默认风格（XML 中 item_grid.xml 定义的尺寸）。
 */
public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
	private int defaultWidth;
    public ImgUtil.Style style; // 动态风格，传入时调整图片宽高比

    /**xuameng 如果 style = list 就以文件夹显示 style = null 用 item_grid.xml  video.tag.equals("folder" 用 item_list以文件夹显示
     * 如果 style不是list  传 null，则采用 item_grid.xml 中的默认尺寸
     */
    public GridAdapter(boolean showList, ImgUtil.Style style) {
        super( showList ? R.layout.item_list:R.layout.item_grid, new ArrayList<>());
        if (style != null) {
            if ("list".equals(style.type)) {   //xuameng如果 style = list 就以文件夹显示 转style = null 用 item_list
                style = null;
            } else {
                this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);   //style 来设置图片的宽高比例
            }
        }
        this.style = style;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
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
 //       helper.setText(R.id.tvActor, item.actor);
        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if(style!=null){
             newWidth = defaultWidth;
             newHeight = (int)(newWidth / style.ratio);
         }
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        int radius = AutoSizeUtils.mm2px(mContext, 8);  //xuameng Base64 图片 圆角设置

        //由于部分电视机使用glide报错
        if (!TextUtils.isEmpty(item.pic)) {
            item.pic=item.pic.trim();
            if(ImgUtil.isBase64Image(item.pic)){
                // xuameng 如果是 Base64 图片，解码并设置
                ivThumb.setImageBitmap(
                    ImgUtil.decodeBase64ToRoundBitmap(item.pic, radius)   //xuameng 用这个方法进行圆角设置
                );
            }else {
                Picasso.get()
                        .load(DefaultConfig.checkReplaceProxy(item.pic))
                        .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                                .centerCorp(true)
                                .override(AutoSizeUtils.mm2px(mContext,newWidth), AutoSizeUtils.mm2px(mContext,newHeight))
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
		applyStyleToImage(ivThumb);//动态设置宽高
    }
	    /**
     * 根据传入的 style 动态设置 ImageView 的高度：高度 = 宽度 / ratio
     */
    private void applyStyleToImage(final ImageView ivThumb) {
        if(style!=null){
            ViewGroup container = (ViewGroup) ivThumb.getParent();
            int width = defaultWidth;
            int height = (int) (width / style.ratio);
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.height = AutoSizeUtils.mm2px(mContext, height); // 高度
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT; // 宽度
            container.setLayoutParams(containerParams);
        }
    }
}
