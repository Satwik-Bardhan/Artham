package com.phynix.artham.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.List;
import java.util.Set;

public class CategorySelectionAdapter extends RecyclerView.Adapter<CategorySelectionAdapter.CategoryViewHolder> {

    private final List<CategoryModel> categoryList;
    private final Set<String> selectedCategories;

    public CategorySelectionAdapter(List<CategoryModel> categoryList, Set<String> selectedCategories) {
        this.categoryList = categoryList;
        this.selectedCategories = selectedCategories;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure you have a layout named 'list_item_category_filter'
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_category_filter, parent, false);
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
        TextView categoryName;
        CheckBox categoryCheckbox;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.category_name);
            categoryCheckbox = itemView.findViewById(R.id.category_checkbox);
        }

        void bind(CategoryModel category) {
            categoryName.setText(category.getName());

            // Prevent listener loops
            categoryCheckbox.setOnCheckedChangeListener(null);
            categoryCheckbox.setChecked(selectedCategories.contains(category.getName()));

            // Set Color Logic
            try {
                int color = Color.parseColor(category.getColorHex());
                categoryCheckbox.setButtonTintList(ColorStateList.valueOf(color));
            } catch (Exception e) {
                categoryCheckbox.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), R.color.purple_500)));
            }

            categoryCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCategories.add(category.getName());
                } else {
                    selectedCategories.remove(category.getName());
                }
            });

            itemView.setOnClickListener(v -> categoryCheckbox.toggle());
        }
    }
}