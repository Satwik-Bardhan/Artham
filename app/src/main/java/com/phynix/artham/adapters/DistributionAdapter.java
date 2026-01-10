package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_report, parent, false);
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
        private TextView categoryName, categoryAmount, categoryPercentage;
        private View colorIndicator;

        public DistributionViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.categoryName);
            categoryAmount = itemView.findViewById(R.id.categoryAmount);
            categoryPercentage = itemView.findViewById(R.id.categoryPercentage);
            colorIndicator = itemView.findViewById(R.id.categoryColorIndicator);
        }

        public void bind(DistributionItem item) {
            categoryName.setText(item.getCategoryName());
            categoryAmount.setText(String.format(Locale.US, "₹%.2f", item.getAmount())); // [FIX] Added currency symbol
            categoryPercentage.setText(String.format(Locale.US, "%.0f%%", item.getPercentage()));
            colorIndicator.setBackgroundColor(item.getColor());

            // [FIX] Apply theme-aware colors
            Context context = itemView.getContext();
            categoryName.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorPrimary));
            categoryAmount.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorPrimary));
            categoryPercentage.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary));
        }
    }

    public static class DistributionItem {
        private String categoryName;
        private double amount;
        private float percentage;
        private int color;

        public DistributionItem(String categoryName, double amount, float percentage, int color) {
            this.categoryName = categoryName;
            this.amount = amount;
            this.percentage = percentage;
            this.color = color;
        }

        // Getters
        public String getCategoryName() { return categoryName; }
        public double getAmount() { return amount; }
        public float getPercentage() { return percentage; }
        public int getColor() { return color; }
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK; // Fallback
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}