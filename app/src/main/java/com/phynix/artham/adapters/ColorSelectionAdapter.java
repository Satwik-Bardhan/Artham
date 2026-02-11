package com.phynix.artham.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.phynix.artham.R;
import java.util.List;

public class ColorSelectionAdapter extends RecyclerView.Adapter<ColorSelectionAdapter.ViewHolder> {

    private final List<Integer> colors;
    private int selectedPosition = 0;
    private final Context context;

    public ColorSelectionAdapter(Context context, List<Integer> colors) {
        this.context = context;
        this.colors = colors;
    }

    public int getSelectedColor() {
        return colors.get(selectedPosition);
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = new View(context);
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(100, 100);
        params.setMargins(16, 16, 16, 16);
        view.setLayoutParams(params);
        view.setBackgroundResource(R.drawable.circle_shape); // Ensure this drawable exists
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int color = colors.get(position);
        holder.itemView.setBackgroundTintList(ColorStateList.valueOf(color));

        if (selectedPosition == position) {
            holder.itemView.setAlpha(1.0f);
            holder.itemView.setScaleX(1.2f);
            holder.itemView.setScaleY(1.2f);
        } else {
            holder.itemView.setAlpha(0.6f);
            holder.itemView.setScaleX(1.0f);
            holder.itemView.setScaleY(1.0f);
        }

        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
        });
    }

    @Override public int getItemCount() { return colors.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) { super(itemView); }
    }
}