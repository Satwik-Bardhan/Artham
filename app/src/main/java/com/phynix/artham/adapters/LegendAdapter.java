package com.phynix.artham.adapters;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.LegendData;
import com.phynix.artham.R;

import java.util.List;
import java.util.Locale;

public class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {

    private List<LegendData> legendList;

    public LegendAdapter(List<LegendData> legendList) {
        this.legendList = legendList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.legend_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LegendData data = legendList.get(position);
        holder.bind(data);
    }

    @Override
    public int getItemCount() {
        return legendList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        // [FIX] Changed variable names to match IDs
        View colorIndicator;
        TextView categoryName, categoryAmount, categoryPercentage;

        public ViewHolder(View view) {
            super(view);
            // [FIX] These are the correct IDs from legend_item.xml
            colorIndicator = view.findViewById(R.id.colorIndicator);
            categoryName = view.findViewById(R.id.categoryName);
            categoryAmount = view.findViewById(R.id.categoryAmount);
            categoryPercentage = view.findViewById(R.id.categoryPercentage);
        }

        public void bind(LegendData data) {
            Context context = itemView.getContext();

            categoryName.setText(data.getCategoryName());

            try {
                // Assuming data.getAmount() returns a string like "5000"
                double amountValue = Double.parseDouble(data.getAmount());
                categoryAmount.setText(String.format(Locale.US, "₹%.2f", amountValue));
            } catch (NumberFormatException e) {
                categoryAmount.setText("₹--.--");
            }

            // data.getPercentage() is a float
            categoryPercentage.setText(String.format(Locale.US, "(%.1f%%)", data.getPercentage()));
            colorIndicator.setBackgroundColor(data.getColor());

            // Apply theme colors
            categoryName.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorPrimary));
            categoryAmount.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary));
            categoryPercentage.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_balanceColor));
        }
    }

    // Helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK; // Fallback
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}