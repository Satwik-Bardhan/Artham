package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    private List<Object> items; // Mixed list of Strings (headers) and CategoryModels
    private final Context context;
    private final OnCategoryClickListener listener;
    private final OnCategoryActionListener actionListener;
    private String selectedCategoryName = "";

    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryModel category);
    }

    public interface OnCategoryActionListener {
        void onEditCategory(CategoryModel category);
        void onDeleteCategory(CategoryModel category);
    }

    public CategoryAdapter(List<Object> items, Context context,
                           OnCategoryClickListener listener, OnCategoryActionListener actionListener) {
        this.items = items;
        this.context = context;
        this.listener = listener;
        this.actionListener = actionListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<Object> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setSelectedCategory(CategoryModel category) {
        this.selectedCategoryName = category != null ? category.getName() : "";
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof String) {
            return TYPE_HEADER;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_category_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
            return new CategoryViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            String title = (String) items.get(position);
            ((HeaderViewHolder) holder).headerTitle.setText(title);
        } else {
            CategoryModel category = (CategoryModel) items.get(position);
            ((CategoryViewHolder) holder).bind(category);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // --- ViewHolders ---

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(R.id.headerTitle);
        }
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        ImageView iconView, menuView, selectionCheck;

        // [FIX] Changed from FrameLayout to View to prevent ClassCastException
        View iconContainer;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.categoryName);
            iconView = itemView.findViewById(R.id.categoryIcon);
            menuView = itemView.findViewById(R.id.categoryMenu);
            selectionCheck = itemView.findViewById(R.id.selectionCheck);
            iconContainer = itemView.findViewById(R.id.iconContainer);
        }

        void bind(CategoryModel category) {
            nameTextView.setText(category.getName());

            // Always resolve icon and color by NAME for reliable mapping
            // (Firebase-stored iconResId values become stale across builds)
            int color;
            int iconRes;

            if (category.isCustom()) {
                // Custom categories: try stored color first, fallback to name lookup
                try {
                    color = Color.parseColor(category.getColorHex());
                } catch (Exception e) {
                    color = CategoryColorUtil.getCategoryColor(context, category.getName());
                }
                // Custom icon: try stored, fallback to name lookup
                iconRes = (category.getIconResId() != 0)
                        ? category.getIconResId()
                        : CategoryColorUtil.getCategoryIcon(category.getName());
            } else {
                // Default categories: ALWAYS resolve by name for correct icons
                color = CategoryColorUtil.getCategoryColor(context, category.getName());
                iconRes = CategoryColorUtil.getCategoryIcon(category.getName());
            }

            // Apply Background Color
            iconContainer.setBackgroundTintList(ColorStateList.valueOf(color));

            // Apply Icon
            iconView.setImageResource(iconRes);
            iconView.setImageTintList(ColorStateList.valueOf(Color.WHITE));

            // Selection Checkmark
            if (category.getName().equals(selectedCategoryName)) {
                selectionCheck.setVisibility(View.VISIBLE);
            } else {
                selectionCheck.setVisibility(View.GONE);
            }

            // Click Handlers
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCategoryClick(category);
            });

            // Show 3-Dot Menu ONLY for Custom Categories
            if (category.isCustom()) {
                menuView.setVisibility(View.VISIBLE);
                menuView.setOnClickListener(v -> showPopupMenu(menuView, category));
            } else {
                menuView.setVisibility(View.GONE);
            }
        }

        private void showPopupMenu(View view, CategoryModel category) {
            PopupMenu popup = new PopupMenu(context, view);
            popup.getMenu().add("Edit");
            popup.getMenu().add("Delete");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Edit")) {
                    actionListener.onEditCategory(category);
                } else if (item.getTitle().equals("Delete")) {
                    actionListener.onDeleteCategory(category);
                }
                return true;
            });
            popup.show();
        }
    }
}