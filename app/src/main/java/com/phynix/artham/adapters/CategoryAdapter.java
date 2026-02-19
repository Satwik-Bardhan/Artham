package com.phynix.artham.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

        // 1. Handle Colors & Icons
        int color;
        try {
            color = Color.parseColor(category.getColorHex());
        } catch (Exception e) {
            color = ContextCompat.getColor(context, R.color.category_default);
        }

        GradientDrawable bgShape = (GradientDrawable) holder.iconContainer.getBackground();
        bgShape.setColor(color);

        int iconRes = category.getIconResId() != 0 ? category.getIconResId() : R.drawable.ic_category;
        holder.iconImage.setImageResource(iconRes);

        // 2. Mode Logic: Management vs Selection
        if (isManagementMode) {
            holder.selectionCheck.setVisibility(View.GONE);
            // Only show menu for custom categories (predefined ones usually can't be deleted)
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
            clickListener.onCategoryClick(category);
        });
    }

    private void showPopupMenu(View view, CategoryModel category) {
        PopupMenu popup = new PopupMenu(context, view);
        popup.getMenu().add("Edit");
        popup.getMenu().add("Delete");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Edit")) {
                actionListener.onEditCategory(category);
            } else {
                actionListener.onDeleteCategory(category);
            }
            return true;
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
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