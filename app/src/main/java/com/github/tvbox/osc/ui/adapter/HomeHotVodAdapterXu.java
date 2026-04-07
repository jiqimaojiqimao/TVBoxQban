package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;
import com.github.tvbox.osc.util.ImgUtil;   //xuameng base64图片
import android.view.ViewGroup;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeHotVodAdapterXu extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {

    private int defaultWidth;
    private final ImgUtil.Style style;
    private boolean mShowList ; //xuameng 判断是否 style为list

    /**xuameng 增加 boolean showList 判断是否 style为list 是就显示文件夹样式
     * style 数据结构：ratio 指定宽高比（宽 / 高），type 表示风格（例如 rect、list）
     */
    public HomeHotVodAdapterXu(boolean showList, ImgUtil.Style style) {
        super( showList ? R.layout.item_list:R.layout.item_user_hot_vod_xu, new ArrayList<>());
        this.mShowList = showList; //xuameng 判断是否 style为list
        if (style != null) {
            if ("list".equals(style.type)) {   //如果 style = list 用item_list显示文件夹样式
                style = null;
            } else {
                this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);   //style 来设置图片的宽高比例
            }
        }
        this.style = style;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
    	// takagen99: Add Delete Mode
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        if ( !this.mShowList && tvDel != null){
            if (HawkConfig.hotVodDelete) {   //xuameng增加判断item_list中没有tvDel
                tvDel.setVisibility(View.VISIBLE);
            } else {
                tvDel.setVisibility(View.GONE);
            }
        }

        TextView tvRate = helper.getView(R.id.tvRate);
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2 && !this.mShowList && tvRate != null){  //xuameng增加判断item_list中没有tvRate
            tvRate.setText(ApiConfig.get().getSource(item.sourceKey).getName());
        }else if(Hawk.get(HawkConfig.HOME_REC, 0) == 0 && !this.mShowList && tvRate != null){
            tvRate.setText("聚汇热播");          //xuameng显示主页聚汇热播左上小字
        }else if(Hawk.get(HawkConfig.HOME_REC, 0) == 1 && !this.mShowList && tvRate != null){
            tvRate.setText("聚汇推荐");
        }else {
            if (!this.mShowList && tvRate != null){
                tvRate.setVisibility(View.GONE);
            }
        }

        TextView tvNote = helper.getView(R.id.tvNote);
        if (item.note == null || item.note.isEmpty()) {
        //    tvNote.setVisibility(View.GONE);
            tvNote.setText("暂无信息");
            tvNote.setVisibility(View.VISIBLE);    
        } else {
            tvNote.setText(item.note);
            tvNote.setVisibility(View.VISIBLE);      
        }
        if (TextUtils.isEmpty(item.name)) {
            helper.setText(R.id.tvName, "聚汇影视");
        } else {
            helper.setText(R.id.tvName, item.name);
        }
     //   helper.setText(R.id.tvName, item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if (style != null) {
            newWidth = defaultWidth;
            float safeRatio = ImgUtil.normalizeRatio(style.ratio);  //xuameng normalizeRatio强行指定ratio值防止用户乱写
            newHeight = (int) (newWidth / safeRatio);
        }

        int radius = AutoSizeUtils.mm2px(mContext, 5);  //xuameng Base64 图片 圆角设置

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
                                .override(AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight))
                                .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                        .placeholder(R.drawable.img_loading_placeholder)
                        .noFade()
                     //   .error(R.drawable.img_loading_placeholder)
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
            float safeRatio = ImgUtil.normalizeRatio(style.ratio);
            int height = (int) (width / safeRatio);
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.height = AutoSizeUtils.mm2px(mContext, height); // 高度
            containerParams.width = AutoSizeUtils.mm2px(mContext, width); // 宽度
            container.setLayoutParams(containerParams);
        }
    }
}
