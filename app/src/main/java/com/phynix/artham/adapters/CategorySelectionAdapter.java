package com.phynix.artham.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;

import java.util.List;
import java.util.Set;

public class CategorySelectionAdapter extends RecyclerView.Adapter<CategorySelectionAdapter.CategoryViewHolder> {

    private final List<String> categoryList;
    private final Set<String> selectedCategories;

    public CategorySelectionAdapter(List<String> categoryList, Set<String> selectedCategories) {
        this.categoryList = categoryList;
        this.selectedCategories = selectedCategories;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_category_filter, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        String category = categoryList.get(position);
        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        CheckBox categoryCheckbox;
        TextView categoryName;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryCheckbox = itemView.findViewById(R.id.category_checkbox);
            categoryName = itemView.findViewById(R.id.category_name);
        }

        void bind(final String category) {
            categoryName.setText(category);

            // [FIX] Set listener to null before changing checked state
            // This prevents the listener from firing during view recycling
            categoryCheckbox.setOnCheckedChangeListener(null);

            // Set the checkbox state based on whether the category is in the selected set
            categoryCheckbox.setChecked(selectedCategories.contains(category));

            // [FIX] Re-attach the listener
            categoryCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCategories.add(category);
                } else {
                    selectedCategories.remove(category);
                }
            });

            // Also allow clicking the whole row to toggle the checkbox
            itemView.setOnClickListener(v -> categoryCheckbox.toggle());
        }
    }
}