package com.phynix.artham.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;

import java.util.List;

public class ManageCategoryAdapter extends RecyclerView.Adapter<ManageCategoryAdapter.ViewHolder> {

    private static final String TAG = "ManageCatAdapter";
    private List<CategoryModel> categoryList;
    private final OnCategoryActionClickListener actionListener;

    public interface OnCategoryActionClickListener {
        void onMenuClick(CategoryModel category, View anchorView);
        void onCategoryClick(CategoryModel category);
    }

    public ManageCategoryAdapter(List<CategoryModel> categoryList, OnCategoryActionClickListener actionListener) {
        this.categoryList = categoryList;
        this.actionListener = actionListener;
    }

    public void updateData(List<CategoryModel> newCategoryList) {
        this.categoryList = newCategoryList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);

        holder.categoryName.setText(category.getName());

        // We hide the selection checkmark entirely on the Manage Screen
        holder.selectionCheck.setVisibility(View.GONE);

        // --- BULLETPROOF COLOR & ICON LOGIC ---
        int categoryColor;
        int iconRes;

        // If the category is Custom, we TRY to use its saved Hex Color and Icon.
        // If it fails, or if it's a Default category, we use CategoryColorUtil.
        if (category.isCustom()) {

            // 1. Resolve Custom Color
            String hexString = category.getColorHex();
            if (hexString != null && !hexString.trim().isEmpty()) {
                if (!hexString.startsWith("#")) {
                    hexString = "#" + hexString;
                }
                try {
                    categoryColor = Color.parseColor(hexString);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Failed to parse custom color: " + hexString + " for " + category.getName());
                    categoryColor = CategoryColorUtil.getCategoryColor(holder.itemView.getContext(), category.getName());
                }
            } else {
                categoryColor = CategoryColorUtil.getCategoryColor(holder.itemView.getContext(), category.getName());
            }

            // 2. Resolve Custom Icon
            if (category.getIconResId() != 0) {
                iconRes = category.getIconResId();
            } else {
                iconRes = CategoryColorUtil.getCategoryIcon(category.getName());
            }

        } else {
            // It's a Default Category - use the Dictionary
            categoryColor = CategoryColorUtil.getCategoryColor(holder.itemView.getContext(), category.getName());
            iconRes = CategoryColorUtil.getCategoryIcon(category.getName());
        }

        // Apply the Color and Icon
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(categoryColor));
        holder.categoryIcon.setImageResource(iconRes);


        // --- Mode Logic: Only show the 3-dot menu for Custom Categories ---
        if (category.isCustom()) {
            holder.categoryMenu.setVisibility(View.VISIBLE);
            holder.categoryMenu.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onMenuClick(category, holder.categoryMenu);
                }
            });
        } else {
            holder.categoryMenu.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList != null ? categoryList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View iconContainer;
        ImageView categoryIcon;
        TextView categoryName;
        ImageView selectionCheck;
        ImageView categoryMenu;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            categoryName = itemView.findViewById(R.id.categoryName);
            selectionCheck = itemView.findViewById(R.id.selectionCheck);
            categoryMenu = itemView.findViewById(R.id.categoryMenu);
        }
    }
}