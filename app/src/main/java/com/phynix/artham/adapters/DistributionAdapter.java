package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
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
        // FIXED: Inflate legend_item.xml instead of item_category_report
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
        // Views declared to match legend_item.xml
        private TextView categoryName;
        private TextView categoryAmount;
        private TextView categoryPercentage;
        private View colorIndicator; // This is a View in XML, not ImageView

        public DistributionViewHolder(@NonNull View itemView) {
            super(itemView);
            // FIXED: IDs matched to legend_item.xml
            categoryName = itemView.findViewById(R.id.categoryName);
            categoryAmount = itemView.findViewById(R.id.categoryAmount);
            categoryPercentage = itemView.findViewById(R.id.categoryPercentage);
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
        }

        public void bind(DistributionItem item) {
            // Set Text Data
            categoryName.setText(item.getCategoryName());
            categoryAmount.setText(String.format(Locale.US, "₹%.2f", item.getAmount()));
            categoryPercentage.setText(String.format(Locale.US, "%.0f%%", item.getPercentage()));

            // Set Color Indicator
            if (colorIndicator != null) {
                colorIndicator.setBackgroundTintList(ColorStateList.valueOf(item.getColor()));
            }

            // Apply theme-aware text colors
            Context context = itemView.getContext();
            categoryName.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorPrimary));
            categoryAmount.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary));
            categoryPercentage.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_balanceColor));
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

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK;
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
            return Color.BLACK; // Fallback
        }
    }
}