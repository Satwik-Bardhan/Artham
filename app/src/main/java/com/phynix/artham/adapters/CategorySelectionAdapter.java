package com.phynix.artham.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.List;
import java.util.Set;

public class CategorySelectionAdapter extends RecyclerView.Adapter<CategorySelectionAdapter.CategoryViewHolder> {

    private Context context;
    private List<CategoryModel> categoryList;

    // For Single Selection (Transaction Screen)
    private OnCategorySelectedListener singleSelectListener;
    private int selectedPosition = -1;

    // For Multi Selection (Filter Screen)
    private boolean isMultiSelect = false;
    private Set<String> selectedCategoryIds;

    public interface OnCategorySelectedListener {
        void onCategorySelected(CategoryModel category);
    }

    // Constructor 1: Single Selection
    public CategorySelectionAdapter(Context context, List<CategoryModel> categoryList, OnCategorySelectedListener listener) {
        this.context = context;
        this.categoryList = categoryList;
        this.singleSelectListener = listener;
        this.isMultiSelect = false;
    }

    // Constructor 2: Multi Selection (Fixes the error)
    public CategorySelectionAdapter(List<CategoryModel> categoryList, Set<String> selectedCategoryIds) {
        this.categoryList = categoryList;
        this.selectedCategoryIds = selectedCategoryIds;
        this.isMultiSelect = true;
    }

    public void setCategoryList(List<CategoryModel> categoryList) {
        this.categoryList = categoryList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = (context != null) ? context : parent.getContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.item_category_chip, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);
        Context ctx = holder.itemView.getContext();

        holder.categoryName.setText(category.getName());

        int color = category.getColor();
        if (color == 0) {
            color = ContextCompat.getColor(ctx, R.color.category_default);
        }

        holder.iconBackground.setBackgroundTintList(ColorStateList.valueOf(color));

        if (isMultiSelect) {
            // Multi-Select Logic
            boolean isSelected = selectedCategoryIds.contains(category.getName());

            if (isSelected) {
                holder.itemView.setBackgroundResource(R.drawable.bg_category_chip);
                holder.itemView.setAlpha(1.0f);
            } else {
                holder.itemView.setBackground(null);
                holder.itemView.setAlpha(0.6f);
            }

            holder.itemView.setOnClickListener(v -> {
                if (isSelected) {
                    selectedCategoryIds.remove(category.getName());
                } else {
                    selectedCategoryIds.add(category.getName());
                }
                notifyItemChanged(holder.getAdapterPosition());
            });

        } else {
            // Single-Select Logic
            if (selectedPosition == position) {
                holder.itemView.setBackgroundResource(R.drawable.bg_category_chip);
                holder.itemView.setAlpha(1.0f);
            } else {
                holder.itemView.setBackground(null);
                holder.itemView.setAlpha(0.6f);
            }

            holder.itemView.setOnClickListener(v -> {
                int previousPosition = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(previousPosition);
                notifyItemChanged(selectedPosition);
                if (singleSelectListener != null) {
                    singleSelectListener.onCategorySelected(category);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {

        TextView categoryName;
        FrameLayout iconBackground;
        ImageView categoryIcon;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.categoryName);
            iconBackground = itemView.findViewById(R.id.iconContainer);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
        }
    }
}