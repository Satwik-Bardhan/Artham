package com.phynix.artham.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;

import java.util.List;

public class ColorSelectionAdapter extends RecyclerView.Adapter<ColorSelectionAdapter.ViewHolder> {

    private final List<String> hexColors;
    private int selectedPosition = 0; // Default to first color
    private final OnColorSelectedListener listener;

    public interface OnColorSelectedListener {
        void onColorSelected(String hexColor);
    }

    public ColorSelectionAdapter(List<String> hexColors, OnColorSelectedListener listener) {
        this.hexColors = hexColors;
        this.listener = listener;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < hexColors.size()) {
            int previousPosition = selectedPosition;
            selectedPosition = index;
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_color_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String hexColor = hexColors.get(position);

        try {
            int colorInt = Color.parseColor(hexColor);
            holder.colorCircle.setBackgroundTintList(ColorStateList.valueOf(colorInt));
        } catch (Exception e) {
            holder.colorCircle.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        }

        // Show checkmark only on the selected color
        if (selectedPosition == position) {
            holder.checkMark.setVisibility(View.VISIBLE);
        } else {
            holder.checkMark.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = position;

            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);

            if (listener != null) {
                listener.onColorSelected(hexColor);
            }
        });
    }

    @Override
    public int getItemCount() {
        return hexColors.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View colorCircle;
        ImageView checkMark;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            colorCircle = itemView.findViewById(R.id.colorCircle);
            checkMark = itemView.findViewById(R.id.checkMark);
        }
    }
}