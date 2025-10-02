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

public class SelectDialogAdapter<T> extends ListAdapter<T, SelectDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {

        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);

        String getDisplay(T val);
    }


    public static DiffUtil.ItemCallback<String> stringDiff = new DiffUtil.ItemCallback<String>() {

        @Override
        public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }
    };


    private ArrayList<T> data = new ArrayList<>();
    private int select = 0;
    private SelectDialogInterface dialogInterface;

    private boolean isFocused = false; // xuameng新增焦点状态标志
    private static final int ACTIVE_COLOR = 0xffffffff;  // xuameng拥有焦点选中白色
    private static final int INACTIVE_COLOR = 0xff02f8e1; // xuameng失去焦点选中色

    public SelectDialogAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = defaultSelect;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    @Override
    public SelectDialogAdapter.SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectDialogAdapter.SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_select, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectDialogAdapter.SelectViewHolder holder, @SuppressLint("RecyclerView") int position) {
        T value = data.get(position);
        String name = dialogInterface.getDisplay(value);
        TextView view = holder.itemView.findViewById(R.id.tvName);
    
        // 动态绑定样式
        view.setTextColor(position == select ? 
            (isFocused ? ACTIVE_COLOR : INACTIVE_COLOR) :  // xuameng新增焦点状态标志颜色
            Color.WHITE);
        view.setTypeface(
            position == select ?        // xuameng新增焦点状态标志字体加粗
                Typeface.DEFAULT_BOLD :
                Typeface.DEFAULT
        );
        view.setText(name);
        // 点击事件
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (position == select)
                    return;
                notifyItemChanged(select);
                select = position;
                notifyItemChanged(select);
                dialogInterface.click(value, position);
            }
        });
    
        // 焦点事件    // xuameng新增焦点状态刷新
        holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (position == select) {
                    if (hasFocus) {
                        isFocused = true;
                    } else {
                        isFocused = false;
                    }
                    // 使用post延迟通知
                    holder.itemView.post(() -> {
                        if (holder.getAdapterPosition() == select) {
                            notifyItemChanged(select);
                        }
                    });
                }
            }
        });
    }
}
