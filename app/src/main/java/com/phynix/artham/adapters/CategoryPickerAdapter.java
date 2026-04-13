package com.phynix.artham.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
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

public class CategoryPickerAdapter extends RecyclerView.Adapter<CategoryPickerAdapter.ViewHolder> {

    private List<CategoryModel> categoryList;
    private String selectedCategoryName = "";
    private final OnCategoryPickedListener listener;

    public interface OnCategoryPickedListener {
        void onCategoryPicked(CategoryModel category);
    }

    public CategoryPickerAdapter(List<CategoryModel> categoryList, String selectedCategoryName, OnCategoryPickedListener listener) {
        this.categoryList = categoryList;
        this.selectedCategoryName = selectedCategoryName == null ? "" : selectedCategoryName;
        this.listener = listener;
    }

    public void updateList(List<CategoryModel> newList) {
        this.categoryList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);

        holder.categoryName.setText(category.getName());

        // Hide the 3-dot management menu in picker mode
        holder.categoryMenu.setVisibility(View.GONE);

        // Show checkmark if this is the currently selected category
        if (category.getName().equalsIgnoreCase(selectedCategoryName)) {
            holder.selectionCheck.setVisibility(View.VISIBLE);
        } else {
            holder.selectionCheck.setVisibility(View.GONE);
        }

        // Resolve icon and color by NAME for default categories
        // (Firebase-stored iconResId values become stale across builds)
        int categoryColor;
        int iconRes;

        if (category.isCustom()) {
            // Custom categories: use stored color/icon, fallback to name lookup
            try {
                categoryColor = Color.parseColor(category.getColorHex());
            } catch (Exception e) {
                categoryColor = CategoryColorUtil.getCategoryColor(holder.itemView.getContext(), category.getName());
            }
            iconRes = (category.getIconResId() != 0)
                    ? category.getIconResId()
                    : CategoryColorUtil.getCategoryIcon(category.getName());
        } else {
            // Default categories: ALWAYS resolve by name for correct icons
            categoryColor = CategoryColorUtil.getCategoryColor(holder.itemView.getContext(), category.getName());
            iconRes = CategoryColorUtil.getCategoryIcon(category.getName());
        }

        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(categoryColor));
        holder.categoryIcon.setImageResource(iconRes);

        // Handle clicks
        holder.itemView.setOnClickListener(v -> {
            String previousSelected = selectedCategoryName;
            selectedCategoryName = category.getName();
            notifyDataSetChanged();

            if (listener != null) {
                listener.onCategoryPicked(category);
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