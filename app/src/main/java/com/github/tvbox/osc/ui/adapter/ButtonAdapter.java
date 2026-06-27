package com.github.tvbox.osc.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xuameng
 * @date :2026/06/27
 * @description:   弹幕设置
 */

public class ButtonAdapter<T> extends ListAdapter<T, ButtonAdapter.SelectViewHolder> {

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);
        String getDisplay(T val);
    }

    static class SelectViewHolder extends RecyclerView.ViewHolder {
        // 记录当前 ViewHolder 绑定的 position，防止焦点监听回调时 position 错乱
        int currentPosition;

        SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    private final ArrayList<T> data = new ArrayList<>();
    private final SelectDialogInterface<T> dialogInterface;
    private int select;

    public ButtonAdapter(SelectDialogInterface<T> dialogInterface, DiffUtil.ItemCallback<T> diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = Math.max(0, Math.min(defaultSelect, data.size() - 1));
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @NotNull
    @Override
    public SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_button, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectViewHolder holder, @SuppressLint("RecyclerView") int position) {
        T value = data.get(position);
        TextView item = holder.itemView.findViewById(R.id.tvName);
        item.setText(dialogInterface.getDisplay(value));

        // 1. 绑定当前真实的 position
        holder.currentPosition = position;

        // 2. 进入页面及刷新时，立即更新样式
        updateItemStyle(holder, item);

        // 3. 设置焦点监听，处理获得/失去焦点时的样式切换
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            // 防止 RecyclerView 复用导致回调的 position 与当前 View 不匹配
            if (holder.currentPosition == position) {
                updateItemStyle(holder, item);
            }
        });

        // 4. 点击事件处理
        holder.itemView.setOnClickListener(v -> {
            if (position == select) return;
            int oldSelect = select;
            select = position;
            notifyItemChanged(oldSelect);
            notifyItemChanged(select);
            dialogInterface.click(value, position);
        });
    }

    /**
     * 统一处理 Item 的样式更新
     */
    private void updateItemStyle(SelectViewHolder holder, TextView item) {
        int position = holder.currentPosition;
        if (position == select) {
            // 选中状态：严格判断当前 itemView 是否拥有焦点
            // 去掉了 getParent().hasFocus() 的判断，避免 TV 端焦点流转导致误判
            if (holder.itemView.hasFocus()) {
                // 拥有焦点：白色加粗
                item.setTextColor(Color.WHITE);
            } else {
                // 失去焦点：青色(0xff02f8e1)加粗
                item.setTextColor(0xff02f8e1);
            }
            item.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        } else {
            // 非选中状态：白色不加粗
            item.setTextColor(Color.WHITE);
            item.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
        }
    }
}
