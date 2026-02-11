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
import androidx.recyclerview.widget.RecyclerView;
import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;
import java.util.List;

public class CategoryManagementAdapter extends RecyclerView.Adapter<CategoryManagementAdapter.ViewHolder> {

    private final List<CategoryModel> categoryList;
    private final Context context;
    private final OnCategoryActionListener listener;

    public interface OnCategoryActionListener {
        void onDelete(CategoryModel category);
        void onEdit(CategoryModel category);
    }

    public CategoryManagementAdapter(Context context, List<CategoryModel> categoryList, OnCategoryActionListener listener) {
        this.context = context;
        this.categoryList = categoryList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_management, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);

        // [FIX] Changed getCategoryName() to getName()
        holder.categoryName.setText(category.getName());

        int color;
        int icon;

        // Logic: Use Custom values if available, otherwise fallback to Utils
        if (category.isCustom()) {
            // Parse custom color (e.g. "#FF5722")
            try {
                color = Color.parseColor(category.getColorHex());
            } catch (Exception e) {
                color = Color.GRAY; // Fallback
            }
            // Use custom icon resource
            icon = category.getIconResId();
            if (icon == 0) icon = R.drawable.ic_category;
        } else {
            // [FIX] Use Utils for default categories
            color = CategoryColorUtil.getCategoryColor(context, category.getName());
            icon = CategoryColorUtil.getCategoryIcon(category.getName());
        }

        holder.categoryIcon.setImageResource(icon);
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(color));

        // Hide Checkmark (Management view doesn't need selection)
        if (holder.selectionCheck != null) {
            holder.selectionCheck.setVisibility(View.GONE);
        }

        // Menu Click
        holder.categoryMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.categoryMenu);
            popup.getMenu().add("Edit");
            popup.getMenu().add("Delete");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Delete")) {
                    listener.onDelete(category);
                } else {
                    listener.onEdit(category);
                }
                return true;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView categoryName;
        ImageView categoryIcon, selectionCheck, categoryMenu;
        FrameLayout iconContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.categoryName);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            selectionCheck = itemView.findViewById(R.id.selectionCheck);
            categoryMenu = itemView.findViewById(R.id.categoryMenu);
            iconContainer = itemView.findViewById(R.id.iconContainer);
        }
    }
}