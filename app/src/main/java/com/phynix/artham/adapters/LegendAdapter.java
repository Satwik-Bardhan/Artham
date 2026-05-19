package com.phynix.artham.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.phynix.artham.R;
import com.phynix.artham.models.LegendData;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {

    private final List<LegendData> legendList;

    public LegendAdapter(List<LegendData> legendList) {
        this.legendList = legendList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Using legend_item.xml as the single source of truth for category reporting
        // This XML already contains the ?attr/ tags for Day/Night/Purple theme compatibility
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.legend_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LegendData data = legendList.get(position);

        holder.categoryName.setText(data.getCategoryName());

        com.phynix.artham.utils.AmountFormatter.setAdaptiveAmount(holder.amount, data.getAmount(), 14f, 9f);

        int percentInt = (int) (data.getPercentage() * 100);
        holder.percentage.setText(percentInt + "%");

        // --- EXACT ICON AND COLOR MAPPING (Req #5 & #6) ---
        // We set the exact icon chosen by the user or the default system icon
        holder.icon.setImageResource(data.getIconResId());

        // We apply the exact category color to the background circle of the icon
        holder.icon.setBackgroundTintList(ColorStateList.valueOf(data.getColor()));

        // We apply the exact category color to the progress bar to match the Pie Chart slice
        holder.progressBar.setProgress(percentInt);
        holder.progressBar.setProgressTintList(ColorStateList.valueOf(data.getColor()));
    }

    @Override
    public int getItemCount() {
        return legendList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView categoryName;
        TextView amount;
        TextView percentage;
        ProgressBar progressBar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.categoryIcon);
            categoryName = itemView.findViewById(R.id.categoryName);
            amount = itemView.findViewById(R.id.categoryAmount);
            percentage = itemView.findViewById(R.id.categoryPercentage);
            progressBar = itemView.findViewById(R.id.categoryProgressBar);
        }
    }
}