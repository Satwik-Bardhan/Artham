package com.phynix.artham.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.List;
import java.util.Set;

public class CategorySelectionAdapter extends RecyclerView.Adapter<CategorySelectionAdapter.ViewHolder> {

    private List<CategoryModel> categoryList;
    private Set<String> selectedCategories;
    private OnCategorySelectedListener listener;

    public interface OnCategorySelectedListener {
        void onSelectionChanged(Set<String> selectedCategories);
    }

    public CategorySelectionAdapter(List<CategoryModel> categoryList, Set<String> selectedCategories, OnCategorySelectedListener listener) {
        this.categoryList = categoryList;
        this.selectedCategories = selectedCategories;
        this.listener = listener;
    }

    public void updateList(List<CategoryModel> newList) {
        this.categoryList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Uses the theme-compatible list_item_category_filter.xml
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_category_filter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);

        // Req #4: Distinguish custom user-created categories visually in the filter list
        if (category.isCustom()) {
            holder.categoryName.setText(category.getName() + " (Custom)");
        } else {
            holder.categoryName.setText(category.getName());
        }

        // Prevent unwanted listener triggers during recycling
        holder.categoryCheckbox.setOnCheckedChangeListener(null);

        // Set checkbox state based on our tracking Set
        holder.categoryCheckbox.setChecked(selectedCategories.contains(category.getName()));

        // Handle both row clicks and checkbox clicks consistently
        View.OnClickListener clickListener = v -> {
            boolean isChecked = !holder.categoryCheckbox.isChecked();
            holder.categoryCheckbox.setChecked(isChecked);

            if (isChecked) {
                selectedCategories.add(category.getName());
            } else {
                selectedCategories.remove(category.getName());
            }
            listener.onSelectionChanged(selectedCategories);
        };

        holder.itemView.setOnClickListener(clickListener);
        holder.categoryCheckbox.setOnClickListener(clickListener);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox categoryCheckbox;
        TextView categoryName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryCheckbox = itemView.findViewById(R.id.category_checkbox);
            categoryName = itemView.findViewById(R.id.category_name);
        }
    }
}