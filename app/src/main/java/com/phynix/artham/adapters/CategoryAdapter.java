package com.phynix.artham.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final List<CategoryModel> categoryList;
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

    public CategoryAdapter(List<CategoryModel> categoryList, Context context,
                           OnCategoryClickListener listener, OnCategoryActionListener actionListener) {
        this.categoryList = categoryList;
        this.context = context;
        this.listener = listener;
        this.actionListener = actionListener;
    }

    public void setSelectedCategory(CategoryModel category) {
        this.selectedCategoryName = category != null ? category.getName() : "";
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
        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        ImageView iconView, menuView, selectionCheck;
        FrameLayout iconContainer;
        View rootLayout;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.categoryName);
            iconView = itemView.findViewById(R.id.categoryIcon);
            menuView = itemView.findViewById(R.id.categoryMenu);
            selectionCheck = itemView.findViewById(R.id.selectionCheck);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            rootLayout = itemView.findViewById(R.id.rootLayout);
        }

        void bind(CategoryModel category) {
            nameTextView.setText(category.getName());

            // 1. Get Color
            int color;
            try {
                color = Color.parseColor(category.getColorHex());
            } catch (Exception e) {
                color = ContextCompat.getColor(context, R.color.category_default);
            }

            // 2. Apply Color to the Icon's Circle Background
            iconContainer.setBackgroundTintList(ColorStateList.valueOf(color));

            // 3. Show/Hide Selection Checkmark
            if (category.getName().equals(selectedCategoryName)) {
                selectionCheck.setVisibility(View.VISIBLE);
            } else {
                selectionCheck.setVisibility(View.GONE);
            }

            // 4. Click Listener
            itemView.setOnClickListener(v -> listener.onCategoryClick(category));

            // 5. Show/Hide 3-Dot Menu for custom categories
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