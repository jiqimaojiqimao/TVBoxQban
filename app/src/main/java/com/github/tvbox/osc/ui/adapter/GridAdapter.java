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
 * @author pj567
 * @date :2020/12/21
 * @description:
  * GridAdapter 支持传入 style 来设置图片的宽高比例，
  * 如果不传 style 则保留旧的默认风格（XML 中 item_grid.xml 定义的尺寸）。
 */
public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList ;
     private int defaultWidth = 280;
     private final Style style; // 动态风格，传入时调整图片宽高比

    /**
     * style 数据结构：ratio 指定宽高比（宽 / 高），type 表示风格（例如 rect、list）
     */
    public static class Style {
        public float ratio;
        public String type;

        public Style(float ratio, String type) {
            this.ratio = ratio;
            this.type = type;
        }
    }

    /**
     * 如果 style 传 null，则采用 item_grid.xml 中的默认尺寸
     */
    public GridAdapter(boolean showList, Style style) {
        super( showList ? R.layout.item_list:R.layout.item_grid, new ArrayList<>());
        this.mShowList = showList;
        if(style!=null ){
            if(style.type.equals("list"))this.mShowList=true;
            if(style.ratio<1)this.defaultWidth=220;
            if(style.ratio>1.7)this.defaultWidth=340;
        }
        this.style = style;
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

        int newWidth = 240;
        int newHeight = 320;
        if(style!=null){
             newWidth = defaultWidth;
             newHeight = (int)(newWidth / style.ratio);
         }
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
                                  //  .override(AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 320))
					        .override(AutoSizeUtils.mm2px(mContext,newWidth), AutoSizeUtils.mm2px(mContext,newHeight))
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
		applyStyleToImage(ivThumb);//动态设置宽高
    }
	    /**
     * 根据传入的 style 动态设置 ImageView 的高度：高度 = 宽度 / ratio
     */
    private void applyStyleToImage(final ImageView ivThumb) {
        ViewGroup container = (ViewGroup) ivThumb.getParent();
        int width = defaultWidth;
        if(style!=null){
            int height = (int) (width / style.ratio);
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.height = AutoSizeUtils.mm2px(mContext, height); // 高度
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT; // 宽度
            container.setLayoutParams(containerParams);
        }
    }
}
