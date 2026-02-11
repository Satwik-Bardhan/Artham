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
import com.google.android.material.card.MaterialCardView;
import com.phynix.artham.R;
import java.util.List;

public class MonthlyExpenseAdapter extends RecyclerView.Adapter<MonthlyExpenseAdapter.ViewHolder> {

    private final List<String> monthList;
    private int selectedPosition = 0;
    private final OnMonthClickListener listener;
    private final Context context;

    public interface OnMonthClickListener {
        void onMonthClick(String monthKey);
    }

    public MonthlyExpenseAdapter(Context context, List<String> monthList, int initialPosition, OnMonthClickListener listener) {
        this.context = context;
        this.monthList = monthList;
        this.selectedPosition = initialPosition; // Set initial selection
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_monthly_expense_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String fullDate = monthList.get(position);

        String[] parts = fullDate.split(" ");
        if (parts.length > 0) holder.monthNameTextView.setText(parts[0]);
        if (parts.length > 1) holder.yearTextView.setText(parts[1]);

        // Theme-aware Selection Logic
        if (selectedPosition == position) {
            int primaryColor = getThemeColor(android.R.attr.colorPrimary);
            holder.cardRoot.setStrokeColor(primaryColor);
            holder.cardRoot.setStrokeWidth(4);
            holder.cardRoot.setCardElevation(8f);
        } else {
            int dividerColor = getThemeColor(R.attr.chk_dividerHorizontal);
            if(dividerColor == 0) dividerColor = Color.parseColor("#40808080");

            holder.cardRoot.setStrokeColor(dividerColor);
            holder.cardRoot.setStrokeWidth(2);
            holder.cardRoot.setCardElevation(2f);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousPos = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPos);
            notifyItemChanged(selectedPosition);

            if(listener != null) {
                listener.onMonthClick(fullDate);
            }
        });
    }

    @Override
    public int getItemCount() {
        return monthList.size();
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView monthNameTextView;
        TextView yearTextView;
        MaterialCardView cardRoot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            monthNameTextView = itemView.findViewById(R.id.monthNameTextView);
            yearTextView = itemView.findViewById(R.id.yearTextView);
            cardRoot = itemView.findViewById(R.id.card_root);
        }
    }
}