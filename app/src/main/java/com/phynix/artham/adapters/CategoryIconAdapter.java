package com.phynix.artham.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;

import java.util.List;

public class CategoryIconAdapter extends RecyclerView.Adapter<CategoryIconAdapter.ViewHolder> {

    private final List<Integer> iconResIds;
    private int selectedPosition = 0; // Default to the first icon
    private final OnIconSelectedListener listener;

    public interface OnIconSelectedListener {
        void onIconSelected(int iconResId);
    }

    public CategoryIconAdapter(List<Integer> iconResIds, OnIconSelectedListener listener) {
        this.iconResIds = iconResIds;
        this.listener = listener;
    }

    /**
     * Used by CreateCategoryActivity to pre-select an icon when editing an existing category.
     */
    public void setSelectedIndex(int index) {
        if (index >= 0 && index < iconResIds.size()) {
            int previousPosition = selectedPosition;
            selectedPosition = index;
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Uses the item_icon_select.xml layout which is already theme-compatible
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_icon_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int iconResId = iconResIds.get(position);
        holder.iconImage.setImageResource(iconResId);

        // Highlight the currently selected icon
        if (selectedPosition == position) {
            holder.selectionIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.selectionIndicator.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = position;

            // Re-render only the two affected items for better performance
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);

            // Pass the selected icon back to the Activity
            if (listener != null) {
                listener.onIconSelected(iconResId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return iconResIds.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImage;
        View selectionIndicator;
        View iconBackground;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImage = itemView.findViewById(R.id.iconImage);
            selectionIndicator = itemView.findViewById(R.id.selectionIndicator);
            iconBackground = itemView.findViewById(R.id.iconBackground);
        }
    }
}