package com.phynix.artham.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.phynix.artham.R;
import java.util.List;

public class IconSelectionAdapter extends RecyclerView.Adapter<IconSelectionAdapter.ViewHolder> {

    private final List<Integer> icons;
    private int selectedPosition = 0;
    private final Context context;

    public IconSelectionAdapter(Context context, List<Integer> icons) {
        this.context = context;
        this.icons = icons;
    }

    public int getSelectedIcon() {
        return icons.get(selectedPosition);
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_chip, parent, false);
        // Reusing existing small layout or create a simple ImageView layout
        // For simplicity, let's assume a simple square layout is used or create one dynamically
        // Actually, let's use a standard view for clarity:
        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(120, 120));
        imageView.setPadding(24, 24, 24, 24);
        return new ViewHolder(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.iconView.setImageResource(icons.get(position));

        if (selectedPosition == position) {
            holder.iconView.setBackgroundResource(R.drawable.circle_shape);
            holder.iconView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY));
            holder.iconView.setColorFilter(Color.BLACK);
        } else {
            holder.iconView.setBackground(null);
            holder.iconView.setColorFilter(Color.GRAY);
        }

        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
        });
    }

    @Override public int getItemCount() { return icons.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        ViewHolder(View itemView) { super(itemView); iconView = (ImageView) itemView; }
    }
}