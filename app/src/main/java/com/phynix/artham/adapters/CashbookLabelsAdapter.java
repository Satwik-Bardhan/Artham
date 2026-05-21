package com.phynix.artham.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CashbookModel;

import java.util.ArrayList;
import java.util.List;

public class CashbookLabelsAdapter extends RecyclerView.Adapter<CashbookLabelsAdapter.ViewHolder> {

    private List<String> categories;
    private List<CashbookModel> cashbooks;
    private final OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onRenameClick(String categoryName);
        void onDeleteClick(String categoryName);
    }

    public CashbookLabelsAdapter(List<String> categories, List<CashbookModel> cashbooks, OnCategoryClickListener listener) {
        this.categories = categories != null ? categories : new ArrayList<>();
        this.cashbooks = cashbooks != null ? cashbooks : new ArrayList<>();
        this.listener = listener;
    }

    public void updateData(List<String> newCategories, List<CashbookModel> newCashbooks) {
        this.categories = newCategories != null ? newCategories : new ArrayList<>();
        this.cashbooks = newCashbooks != null ? newCashbooks : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cashbook_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String categoryName = categories.get(position);
        holder.categoryName.setText(categoryName);

        // Circular Badge Letter
        String firstLetter = "";
        if (categoryName != null && !categoryName.trim().isEmpty()) {
            firstLetter = categoryName.trim().substring(0, 1).toUpperCase();
        }
        holder.categoryLetter.setText(firstLetter);

        // Circular Badge Background Dynamic Hash Color
        int color = getCategoryColor(categoryName);
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(color));

        // Count associated cashbooks (case-insensitive to match home filters)
        int count = 0;
        for (CashbookModel cb : cashbooks) {
            if (cb.getCategory() != null && categoryName.equalsIgnoreCase(cb.getCategory())) {
                count++;
            }
        }
        holder.cashbookCount.setText(count == 1 ? "1 cashbook" : count + " cashbooks");

        // Action Click Listeners
        holder.btnRename.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRenameClick(categoryName);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(categoryName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    private int getCategoryColor(String category) {
        if (category == null || category.trim().isEmpty()) {
            return Color.GRAY;
        }
        int hash = category.hashCode();
        String[] colors = {
            "#3F51B5", "#009688", "#FF9800", "#E91E63", 
            "#9C27B0", "#03A9F4", "#4CAF50", "#FF5722",
            "#607D8B", "#8BC34A", "#00BCD4"
        };
        int index = Math.abs(hash) % colors.length;
        return Color.parseColor(colors[index]);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View iconContainer;
        TextView categoryLetter;
        TextView categoryName;
        TextView cashbookCount;
        ImageButton btnRename;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            categoryLetter = itemView.findViewById(R.id.categoryLetter);
            categoryName = itemView.findViewById(R.id.categoryName);
            cashbookCount = itemView.findViewById(R.id.cashbookCount);
            btnRename = itemView.findViewById(R.id.btnRename);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
