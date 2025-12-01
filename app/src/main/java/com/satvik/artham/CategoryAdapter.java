package com.satvik.artham;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final List<CategoryModel> categoryList;
    private final Context context;
    private final OnCategoryClickListener listener;
    private final OnCategoryActionListener actionListener; // New listener for menu actions
    private int selectedPosition = RecyclerView.NO_POSITION;

    // Updated interface to include Action Listener
    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryModel category);
    }

    public interface OnCategoryActionListener {
        void onEditCategory(CategoryModel category);
        void onDeleteCategory(CategoryModel category);
    }

    public CategoryAdapter(List<CategoryModel> categoryList, Context context,
                           OnCategoryClickListener listener,
                           OnCategoryActionListener actionListener) {
        this.categoryList = categoryList;
        this.context = context;
        this.listener = listener;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_chip, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);
        holder.bind(category, position);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setSelectedCategory(CategoryModel category) {
        if (category == null || category.getName() == null) {
            selectedPosition = RecyclerView.NO_POSITION;
            notifyDataSetChanged();
            return;
        }
        for (int i = 0; i < categoryList.size(); i++) {
            if (categoryList.get(i).getName().equals(category.getName())) {
                selectedPosition = i;
                notifyDataSetChanged();
                return;
            }
        }
        selectedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryNameTextView;
        View categoryColorDot;
        LinearLayout categoryChipLayout;
        ImageView categoryMenu;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryNameTextView = itemView.findViewById(R.id.categoryNameTextView);
            categoryColorDot = itemView.findViewById(R.id.categoryColorDot);
            categoryChipLayout = itemView.findViewById(R.id.categoryChipLayout);
            categoryMenu = itemView.findViewById(R.id.categoryMenu);
        }

        void bind(final CategoryModel category, final int position) {
            categoryNameTextView.setText(category.getName());

            // Set Dot Color
            try {
                int color = Color.parseColor(category.getColorHex());
                Drawable background = categoryColorDot.getBackground();
                if (background instanceof GradientDrawable) {
                    ((GradientDrawable) background.mutate()).setColor(color);
                }
            } catch (Exception e) {
                int defaultColor = ContextCompat.getColor(context, R.color.category_default);
                Drawable background = categoryColorDot.getBackground();
                if (background instanceof GradientDrawable) {
                    ((GradientDrawable) background.mutate()).setColor(defaultColor);
                }
            }

            // Selection Styling
            if (selectedPosition == position) {
                categoryChipLayout.setBackgroundColor(ThemeUtil.getThemeAttrColor(context, R.attr.balanceColor));
                categoryNameTextView.setTextColor(Color.WHITE);
                categoryMenu.setColorFilter(Color.WHITE); // Make dots white when selected
            } else {
                categoryChipLayout.setBackground(null); // Reset background
                categoryNameTextView.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.textColorPrimary));
                categoryMenu.setColorFilter(ThemeUtil.getThemeAttrColor(context, R.attr.textColorSecondary));
            }

            // Click Listener for Selection
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                    int prev = selectedPosition;
                    selectedPosition = getAdapterPosition();
                    notifyItemChanged(prev);
                    notifyItemChanged(selectedPosition);
                }
            });

            // 3-Dot Menu Logic
            // Only show menu for Custom categories
            if (category.isCustom()) {
                categoryMenu.setVisibility(View.VISIBLE);
                categoryMenu.setOnClickListener(v -> showPopupMenu(v, category));
            } else {
                categoryMenu.setVisibility(View.GONE); // Hide for default categories
            }
        }

        private void showPopupMenu(View view, CategoryModel category) {
            PopupMenu popup = new PopupMenu(context, view);
            popup.getMenu().add("Edit");
            popup.getMenu().add("Delete");

            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Edit")) {
                    if (actionListener != null) actionListener.onEditCategory(category);
                } else if (item.getTitle().equals("Delete")) {
                    if (actionListener != null) actionListener.onDeleteCategory(category);
                }
                return true;
            });
            popup.show();
        }
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}