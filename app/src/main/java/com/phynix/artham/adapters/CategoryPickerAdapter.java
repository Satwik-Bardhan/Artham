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
        // Uses the theme-compatible item_category.xml
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);

        // Requirement #4: Distinguish custom categories
        if (category.isCustom()) {
            holder.categoryName.setText(category.getName() + " (Custom)");
        } else {
            holder.categoryName.setText(category.getName());
        }

        // Hide the 3-dot management menu in picker mode
        holder.categoryMenu.setVisibility(View.GONE);

        // Show checkmark if this is the currently selected category
        if (category.getName().equalsIgnoreCase(selectedCategoryName)) {
            holder.selectionCheck.setVisibility(View.VISIBLE);
        } else {
            holder.selectionCheck.setVisibility(View.GONE);
        }

        // Requirement #5 & #6: Apply EXACT synced colors and icons
        int syncedColor = CategoryColorUtil.getCategoryColor(holder.itemView.getContext(), category.getName());
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(syncedColor));

        int iconRes = category.isCustom() && category.getIconResId() != 0
                ? category.getIconResId()
                : CategoryColorUtil.getCategoryIcon(category.getName());
        holder.categoryIcon.setImageResource(iconRes);

        // Handle clicks
        holder.itemView.setOnClickListener(v -> {
            String previousSelected = selectedCategoryName;
            selectedCategoryName = category.getName();
            notifyDataSetChanged(); // Refresh to move the checkmark

            if (listener != null) {
                listener.onCategoryPicked(category);
            }
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