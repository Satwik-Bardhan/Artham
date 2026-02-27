package com.phynix.artham.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final List<CategoryModel> categoryList;
    private final Context context;
    private final OnCategoryClickListener clickListener;
    private final OnCategoryActionListener actionListener;

    private String selectedCategoryName = "";
    private boolean isManagementMode = false;

    // Interfaces for interaction
    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryModel category);
    }

    public interface OnCategoryActionListener {
        void onEditCategory(CategoryModel category);
        void onDeleteCategory(CategoryModel category);
    }

    public CategoryAdapter(List<CategoryModel> categoryList, Context context,
                           OnCategoryClickListener clickListener, OnCategoryActionListener actionListener) {
        this.categoryList = categoryList;
        this.context = context;
        this.clickListener = clickListener;
        this.actionListener = actionListener;
    }

    public void setManagementMode(boolean managementMode) {
        this.isManagementMode = managementMode;
    }

    public void setSelectedCategory(CategoryModel category) {
        this.selectedCategoryName = (category != null) ? category.getName() : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);
        holder.nameText.setText(category.getName());

        // 1. THE BULLETPROOF COLOR FIX
        int categoryColor;
        try {
            String hexColor = category.getColorHex();
            // Safety check: Ensure the hex string has a '#' symbol so it doesn't crash
            if (hexColor != null && !hexColor.isEmpty() && !hexColor.startsWith("#")) {
                hexColor = "#" + hexColor;
            }
            // Parse the color straight from Firebase
            categoryColor = Color.parseColor(hexColor);
        } catch (Exception e) {
            // If Firebase color is completely broken, fallback to the dictionary/grey
            categoryColor = CategoryColorUtil.getCategoryColor(context, category.getName());
        }

        // Apply the color
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(categoryColor));

        // 2. THE BULLETPROOF ICON FIX
        int iconRes;
        if (category.getIconResId() != 0) {
            // Use the icon saved in Firebase
            iconRes = category.getIconResId();
        } else {
            // Fallback to the dictionary if Firebase is missing the icon
            iconRes = CategoryColorUtil.getCategoryIcon(category.getName());
        }
        holder.iconImage.setImageResource(iconRes);

        // 3. Mode Logic: Management vs Selection
        if (isManagementMode) {
            holder.selectionCheck.setVisibility(View.GONE);
            // We still check isCustom() here so users can't delete default app categories
            holder.menuBtn.setVisibility(category.isCustom() ? View.VISIBLE : View.GONE);

            holder.menuBtn.setOnClickListener(v -> showPopupMenu(v, category));
        } else {
            holder.menuBtn.setVisibility(View.GONE);
            boolean isSelected = category.getName().equals(selectedCategoryName);
            holder.selectionCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (!isManagementMode) {
                selectedCategoryName = category.getName();
                notifyDataSetChanged();
            }
            if (clickListener != null) {
                clickListener.onCategoryClick(category);
            }
        });
    }

    private void showPopupMenu(View view, CategoryModel category) {
        PopupMenu popup = new PopupMenu(context, view);
        popup.getMenu().add("Edit");
        popup.getMenu().add("Delete");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Edit")) {
                if (actionListener != null) actionListener.onEditCategory(category);
            } else {
                if (actionListener != null) actionListener.onDeleteCategory(category);
            }
            return true;
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return categoryList != null ? categoryList.size() : 0;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        ImageView iconImage, selectionCheck, menuBtn;
        View iconContainer;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.categoryName);
            iconImage = itemView.findViewById(R.id.categoryIcon);
            selectionCheck = itemView.findViewById(R.id.selectionCheck);
            menuBtn = itemView.findViewById(R.id.categoryMenu);
            iconContainer = itemView.findViewById(R.id.iconContainer);
        }
    }
}