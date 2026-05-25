package com.phynix.artham.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.ThemeUtil;

import java.util.List;
import java.util.Locale;

public class DailyBalanceAdapter extends RecyclerView.Adapter<DailyBalanceAdapter.ViewHolder> {

    public static class DailyBalanceItem {
        public String dateStr;
        public double income;
        public double expense;
        public double netBalance;

        public DailyBalanceItem(String dateStr, double income, double expense, double netBalance) {
            this.dateStr = dateStr;
            this.income = income;
            this.expense = expense;
            this.netBalance = netBalance;
        }
    }

    private final List<DailyBalanceItem> itemList;

    public DailyBalanceAdapter(List<DailyBalanceItem> itemList) {
        this.itemList = itemList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily_net_balance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyBalanceItem item = itemList.get(position);
        Context context = holder.itemView.getContext();

        holder.dateTextView.setText(item.dateStr);
        holder.summaryTextView.setText(String.format(Locale.US, "IN: ₹%,.2f  |  OUT: ₹%,.2f", item.income, item.expense));

        // Format and color the Net Balance
        String sign = item.netBalance >= 0 ? "+ " : "- ";
        AmountFormatter.setAdaptiveAmount(holder.netBalanceTextView, Math.abs(item.netBalance), 13f, 9f);
        holder.netBalanceTextView.setText(android.text.TextUtils.concat(sign, holder.netBalanceTextView.getText()));

        int colorAttr = item.netBalance >= 0 ? R.attr.chk_incomeColor : R.attr.chk_expenseColor;
        int baseColor = ThemeUtil.getThemeAttrColor(context, colorAttr);
        holder.netBalanceTextView.setTextColor(baseColor);

        // Apply dynamic translucent background tint (12% opacity / alpha = 30)
        int softBgColor = android.graphics.Color.argb(30, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor));
        android.graphics.drawable.Drawable bg = holder.netBalanceTextView.getBackground();
        if (bg != null) {
            android.graphics.drawable.Drawable mutatedBg = bg.mutate();
            androidx.core.graphics.drawable.DrawableCompat.setTint(mutatedBg, softBgColor);
        }
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView dateTextView;
        public final TextView summaryTextView;
        public final TextView netBalanceTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            summaryTextView = itemView.findViewById(R.id.summaryTextView);
            netBalanceTextView = itemView.findViewById(R.id.netBalanceTextView);
        }
    }
}
