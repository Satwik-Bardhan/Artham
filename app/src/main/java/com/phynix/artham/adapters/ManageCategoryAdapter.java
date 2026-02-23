package com.phynix.artham.adapters;

import android.content.res.ColorStateList;
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

    private List<CategoryModel> categoryList;
    private OnCategoryActionClickListener listener;

    public interface OnCategoryActionClickListener {
        void onMenuClick(CategoryModel category, View anchorView);
        void onCategoryClick(CategoryModel category);
    }

    public ManageCategoryAdapter(List<CategoryModel> categoryList, OnCategoryActionClickListener listener) {
        this.categoryList = categoryList;
        this.listener = listener;
    }

    public void updateData(List<CategoryModel> newList) {
        this.categoryList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Uses the theme-compatible item_category.xml
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);

        holder.categoryName.setText(category.getName());

        // --- Req #4: Separate behavior for Default vs User-Created Categories ---
        if (category.isCustom()) {
            holder.categoryMenu.setVisibility(View.VISIBLE); // Show 3-dot menu for edit/delete
        } else {
            holder.categoryMenu.setVisibility(View.GONE); // Hide menu; defaults are locked
        }

        // --- Req #5 & #6: EXACT COLOR AND ICON MAPPING ---
        // Fetch the exact synced color from our universal utility
        int syncedColor = CategoryColorUtil.getCategoryColor(holder.itemView.getContext(), category.getName());
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(syncedColor));

        // Fetch the exact synced icon
        int iconRes = category.isCustom() && category.getIconResId() != 0
                ? category.getIconResId()
                : CategoryColorUtil.getCategoryIcon(category.getName());

        holder.categoryIcon.setImageResource(iconRes);

        // Click Listeners
        holder.categoryMenu.setOnClickListener(v -> {
            if (listener != null) listener.onMenuClick(category, holder.categoryMenu);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(category);
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View iconContainer;
        ImageView categoryIcon;
        TextView categoryName;
        ImageView categoryMenu;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            categoryName = itemView.findViewById(R.id.categoryName);
            categoryMenu = itemView.findViewById(R.id.categoryMenu);
        }
    }
}