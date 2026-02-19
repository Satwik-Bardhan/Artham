package com.phynix.artham.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.phynix.artham.R;
import java.util.List;

public class IconSelectionAdapter extends RecyclerView.Adapter<IconSelectionAdapter.IconViewHolder> {

    private final Context context;
    private final List<Integer> iconList;
    private int selectedPosition = 0;

    public IconSelectionAdapter(Context context, List<Integer> iconList) {
        this.context = context;
        this.iconList = iconList;
    }

    public int getSelectedIcon() {
        return iconList.get(selectedPosition);
    }

    @NonNull
    @Override
    public IconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // FIXED: Pointing to item_icon_select
        View view = LayoutInflater.from(context).inflate(R.layout.item_icon_select, parent, false);
        return new IconViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IconViewHolder holder, int position) {
        int iconRes = iconList.get(position);
        holder.iconImage.setImageResource(iconRes);

        // Show selection ring if this icon is selected
        if (selectedPosition == position) {
            holder.selectionIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.selectionIndicator.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);
        });
    }

    @Override
    public int getItemCount() {
        return iconList.size();
    }

    static class IconViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImage;
        View selectionIndicator;

        public IconViewHolder(@NonNull View itemView) {
            super(itemView);
            // These IDs must match item_icon_select.xml
            iconImage = itemView.findViewById(R.id.iconImage);
            selectionIndicator = itemView.findViewById(R.id.selectionIndicator);
        }
    }
}