package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
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

import java.util.List;
import java.util.Locale;

public class DistributionAdapter extends RecyclerView.Adapter<DistributionAdapter.DistributionViewHolder> {

    private List<DistributionItem> distributionItems;

    public DistributionAdapter(List<DistributionItem> distributionItems) {
        this.distributionItems = distributionItems;
    }

    @NonNull
    @Override
    public DistributionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the theme-compatible legend_item.xml
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.legend_item, parent, false);
        return new DistributionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DistributionViewHolder holder, int position) {
        DistributionItem item = distributionItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return distributionItems.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<DistributionItem> newItems) {
        this.distributionItems = newItems;
        notifyDataSetChanged();
    }

    static class DistributionViewHolder extends RecyclerView.ViewHolder {
        // Views matched EXACTLY to legend_item.xml
        private TextView categoryName;
        private TextView categoryAmount;
        private TextView categoryPercentage;
        private ImageView categoryIcon;
        private ProgressBar categoryProgressBar;

        public DistributionViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.categoryName);
            categoryAmount = itemView.findViewById(R.id.categoryAmount);
            categoryPercentage = itemView.findViewById(R.id.categoryPercentage);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            categoryProgressBar = itemView.findViewById(R.id.categoryProgressBar);
        }

        public void bind(DistributionItem item) {
            // Set Text Data
            categoryName.setText(item.getCategoryName());
            categoryAmount.setText(String.format(Locale.US, "₹%.2f", item.getAmount()));

            int percentInt = (int) item.getPercentage();
            categoryPercentage.setText(percentInt + "%");

            // Apply specific Category Icon
            if (categoryIcon != null) {
                categoryIcon.setImageResource(item.getIconResId());
                // Tint the circle behind the icon with the specific category color
                categoryIcon.setBackgroundTintList(ColorStateList.valueOf(item.getColor()));
            }

            // Apply Progress Bar distribution
            if (categoryProgressBar != null) {
                categoryProgressBar.setProgress(percentInt);
                // Tint the progress bar to match the exact category color
                categoryProgressBar.setProgressTintList(ColorStateList.valueOf(item.getColor()));
            }

            // Note: We no longer need ThemeUtil here because legend_item.xml
            // natively sets the text colors using ?attr/chk_textColorPrimary
            // and ?attr/chk_textColorSecondary.
        }
    }

    public static class DistributionItem {
        private String categoryName;
        private double amount;
        private float percentage;
        private int color;
        private int iconResId;

        // Constructor with Icon
        public DistributionItem(String categoryName, double amount, float percentage, int color, int iconResId) {
            this.categoryName = categoryName;
            this.amount = amount;
            this.percentage = percentage;
            this.color = color;
            this.iconResId = iconResId;
        }

        // Backward compatible constructor
        public DistributionItem(String categoryName, double amount, float percentage, int color) {
            this(categoryName, amount, percentage, color, R.drawable.ic_category);
        }

        // Getters
        public String getCategoryName() { return categoryName; }
        public double getAmount() { return amount; }
        public float getPercentage() { return percentage; }
        public int getColor() { return color; }
        public int getIconResId() { return iconResId; }
    }
}