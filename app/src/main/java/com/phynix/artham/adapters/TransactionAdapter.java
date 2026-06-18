package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.CategoryColorUtil;
import com.phynix.artham.utils.ThemeUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.phynix.artham.utils.ThemeUtil;
/**
 * Artham Transaction Adapter
 * Implements date-wise grouping with separate headers for each day.
 * Manages Cash In and Cash Out items with unique layouts and Synced Category Icons.
 */
public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /**
     * Data class representing a date header with transaction counts.
     */
    static class DateHeaderInfo {
        final String dateText;
        final int inCount;
        final int outCount;

        DateHeaderInfo(String dateText, int inCount, int outCount) {
            this.dateText = dateText;
            this.inCount = inCount;
            this.outCount = outCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DateHeaderInfo)) return false;
            DateHeaderInfo that = (DateHeaderInfo) o;
            return inCount == that.inCount && outCount == that.outCount && Objects.equals(dateText, that.dateText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dateText, inCount, outCount);
        }
    }

    private final List<Object> items = new ArrayList<>(); // Mixed list: DateHeaderInfo (Headers) and TransactionModels
    private final OnItemClickListener listener;

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_IN = 1;
    private static final int VIEW_TYPE_OUT = 2;

    public interface OnItemClickListener {
        void onItemClick(TransactionModel transaction);
        void onEditClick(TransactionModel transaction);
        void onDeleteClick(TransactionModel transaction);
        void onCopyClick(TransactionModel transaction);
        void onAutoToggleClick(TransactionModel transaction);
    }

    public TransactionAdapter(List<TransactionModel> transactions, OnItemClickListener listener) {
        this.listener = listener;
        if (transactions != null) {
            updateData(transactions);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof DateHeaderInfo) {
            return VIEW_TYPE_HEADER;
        }
        TransactionModel transaction = (TransactionModel) item;
        return "IN".equalsIgnoreCase(transaction.getType()) ? VIEW_TYPE_IN : VIEW_TYPE_OUT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                return new HeaderViewHolder(inflater.inflate(R.layout.item_date_header, parent, false));
            case VIEW_TYPE_IN:
                return new TransactionViewHolder(inflater.inflate(R.layout.item_transaction_income, parent, false));
            default:
                return new TransactionViewHolder(inflater.inflate(R.layout.item_transaction_expense, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((DateHeaderInfo) item);
        } else if (holder instanceof TransactionViewHolder) {
            ((TransactionViewHolder) holder).bind((TransactionModel) item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateTransactions(List<TransactionModel> newTransactions) {
        updateData(newTransactions);
    }

    /**
     * Returns true if the item at the given position is a date header.
     */
    public boolean isHeader(int position) {
        if (position < 0 || position >= items.size()) return false;
        return items.get(position) instanceof DateHeaderInfo;
    }

    /**
     * Returns the DateHeaderInfo at the given position, or null if it's not a header.
     */
    public DateHeaderInfo getHeaderAtPosition(int position) {
        if (position < 0 || position >= items.size()) return null;
        Object item = items.get(position);
        return item instanceof DateHeaderInfo ? (DateHeaderInfo) item : null;
    }

    private void updateData(List<TransactionModel> transactions) {
        List<Object> newList = groupTransactionsByDate(transactions);

        TransactionDiffCallback diffCallback = new TransactionDiffCallback(this.items, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.items.clear();
        this.items.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    private List<Object> groupTransactionsByDate(List<TransactionModel> transactions) {
        if (transactions == null || transactions.isEmpty()) return new ArrayList<>();

        // First, compute running balances by sorting ascending (oldest first)
        List<TransactionModel> chronological = new ArrayList<>(transactions);
        Collections.sort(chronological, (t1, t2) -> Long.compare(t1.getTimestamp(), t2.getTimestamp()));

        double balance = 0.0;
        for (TransactionModel t : chronological) {
            if ("IN".equalsIgnoreCase(t.getType())) {
                balance += t.getAmount();
            } else {
                balance -= t.getAmount();
            }
            t.setRunningBalance(balance);
        }

        // Sort descending (Newest first) for display
        List<TransactionModel> sortedList = new ArrayList<>(transactions);
        Collections.sort(sortedList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

        // Pre-compute IN/OUT counts per date
        java.util.LinkedHashMap<String, int[]> dateCounts = new java.util.LinkedHashMap<>();
        for (TransactionModel t : sortedList) {
            String dateKey = getRelativeDateString(t.getTimestamp());
            int[] counts = dateCounts.get(dateKey);
            if (counts == null) {
                counts = new int[]{0, 0}; // [inCount, outCount]
                dateCounts.put(dateKey, counts);
            }
            if ("IN".equalsIgnoreCase(t.getType())) {
                counts[0]++;
            } else {
                counts[1]++;
            }
        }

        List<Object> grouped = new ArrayList<>();
        String lastDate = "";

        for (TransactionModel t : sortedList) {
            String currentDate = getRelativeDateString(t.getTimestamp());
            if (!currentDate.equals(lastDate)) {
                int[] counts = dateCounts.get(currentDate);
                int inCount = counts != null ? counts[0] : 0;
                int outCount = counts != null ? counts[1] : 0;
                grouped.add(new DateHeaderInfo(currentDate, inCount, outCount)); // Inject Date Header with counts
                lastDate = currentDate;
            }
            grouped.add(t); // Inject Transaction Item
        }
        return grouped;
    }

    private String getRelativeDateString(long timestamp) {
        java.util.Calendar today = java.util.Calendar.getInstance();
        java.util.Calendar yesterday = java.util.Calendar.getInstance();
        yesterday.add(java.util.Calendar.DATE, -1);

        java.util.Calendar target = java.util.Calendar.getInstance();
        target.setTimeInMillis(timestamp);

        if (today.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR)
                && today.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "Today";
        } else if (yesterday.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR)
                && yesterday.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        } else {
            SimpleDateFormat displayFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
            return displayFormat.format(new Date(timestamp));
        }
    }

    // --- ViewHolders ---

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.dateHeaderTextView);
        }

        void bind(DateHeaderInfo info) {
            Context context = itemView.getContext();
            int inColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor);
            int outColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_expenseColor);

            String inStr = String.valueOf(info.inCount);
            String outStr = String.valueOf(info.outCount);
            // Format: "TODAY (3,5)" or "16 MAY 2026 (3,5)"
            String full = info.dateText + " (" + inStr + "," + outStr + ")";

            SpannableStringBuilder spannable = new SpannableStringBuilder(full);

            // Color the in-count number green
            int inStart = info.dateText.length() + 2; // after " ("
            int inEnd = inStart + inStr.length();
            spannable.setSpan(new ForegroundColorSpan(inColor), inStart, inEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Color the out-count number red
            int outStart = inEnd + 1; // after ","
            int outEnd = outStart + outStr.length();
            spannable.setSpan(new ForegroundColorSpan(outColor), outStart, outEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            headerText.setText(spannable);
        }
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView categoryTextView, amountTextView, dateTextView, paymentModeTextView, remarkTextView, autoFrequencyText, balanceTextView;
        View transactionTypeIndicator, iconContainer, autoRepeatBadge;
        ImageView menuButton, categoryIcon;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryTextView = itemView.findViewById(R.id.categoryTextView);
            amountTextView = itemView.findViewById(R.id.amountTextView);
            balanceTextView = itemView.findViewById(R.id.balanceTextView);
            remarkTextView = itemView.findViewById(R.id.remarkTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            paymentModeTextView = itemView.findViewById(R.id.paymentModeTextView);
            transactionTypeIndicator = itemView.findViewById(R.id.transactionTypeIndicator);
            menuButton = itemView.findViewById(R.id.menuButton);

            // Auto-repeat badge
            autoRepeatBadge = itemView.findViewById(R.id.autoRepeatBadge);
            autoFrequencyText = itemView.findViewById(R.id.autoFrequencyText);

            // Added views for synced icons and colors
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            iconContainer = itemView.findViewById(R.id.iconContainer);
        }

        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        void bind(final TransactionModel transaction) {
            Context context = itemView.getContext();
            String catName = transaction.getTransactionCategory() != null ? transaction.getTransactionCategory() : "Other";

            // Tag for swipe action callback
            itemView.setTag(R.id.transactionTag, transaction);

            categoryTextView.setText(catName);
            paymentModeTextView.setText(transaction.getPaymentMode());

            if (transaction.getRemark() != null && !transaction.getRemark().isEmpty()) {
                remarkTextView.setText(transaction.getRemark());
                remarkTextView.setVisibility(View.VISIBLE);
            } else {
                remarkTextView.setVisibility(View.GONE);
            }

            // --- Category Syncing Logic ---
            if (categoryIcon != null) {
                categoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon(catName));
            }
            if (iconContainer != null) {
                int catColor = CategoryColorUtil.getCategoryColor(context, catName);
                iconContainer.setBackgroundTintList(ColorStateList.valueOf(catColor));
            }
            // ------------------------------

            boolean isIn = "IN".equalsIgnoreCase(transaction.getType());
            int colorAttr = isIn ? R.attr.chk_incomeColor : R.attr.chk_expenseColor;
            int color = ThemeUtil.getThemeAttrColor(context, colorAttr);

            String prefix = isIn ? "" : "- ";
            AmountFormatter.setAdaptiveAmount(amountTextView, transaction.getAmount(), 16f, 10f);
            amountTextView.setText(android.text.TextUtils.concat(prefix, amountTextView.getText()));
            amountTextView.setTextColor(color);
            if (transactionTypeIndicator != null) transactionTypeIndicator.setBackgroundColor(color);

            // Running balance display
            if (balanceTextView != null) {
                double bal = transaction.getRunningBalance();
                AmountFormatter.setAdaptiveAmount(balanceTextView, Math.abs(bal), 12f, 8f);
                balanceTextView.setText(android.text.TextUtils.concat("Balance: ", bal < 0 ? "- " : "", balanceTextView.getText()));
                balanceTextView.setTextColor(bal >= 0
                        ? ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor)
                        : ThemeUtil.getThemeAttrColor(context, R.attr.chk_expenseColor));
            }

            dateTextView.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(new Date(transaction.getTimestamp())));

            itemView.setOnClickListener(v -> listener.onItemClick(transaction));
            if (menuButton != null) {
                menuButton.setOnClickListener(v -> showPopupMenu(v, transaction));
            }

            // Auto-repeat badge & toggle icon
            String autoFreq = transaction.getAutoFrequency();
            boolean hasAuto = autoFreq != null && !autoFreq.isEmpty();
            if (autoRepeatBadge != null) {
                if (hasAuto) {
                    autoRepeatBadge.setVisibility(View.VISIBLE);
                    if (autoFrequencyText != null) {
                        autoFrequencyText.setText(autoFreq);
                    }
                } else {
                    autoRepeatBadge.setVisibility(View.GONE);
                }
            }
        }

        private void showPopupMenu(View view, TransactionModel transaction) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.inflate(R.menu.transaction_options);
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) listener.onEditClick(transaction);
                else if (id == R.id.action_copy) listener.onCopyClick(transaction);
                else if (id == R.id.action_delete) listener.onDeleteClick(transaction);
                return true;
            });
            popup.show();
        }
    }

    // --- Utilities ---

    private static class TransactionDiffCallback extends DiffUtil.Callback {
        private final List<Object> oldList, newList;
        TransactionDiffCallback(List<Object> oldList, List<Object> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            Object oldObj = oldList.get(oldPos);
            Object newObj = newList.get(newPos);
            if (oldObj instanceof DateHeaderInfo && newObj instanceof DateHeaderInfo) {
                return ((DateHeaderInfo) oldObj).dateText.equals(((DateHeaderInfo) newObj).dateText);
            }
            if (oldObj instanceof TransactionModel && newObj instanceof TransactionModel) {
                return Objects.equals(((TransactionModel) oldObj).getTransactionId(), ((TransactionModel) newObj).getTransactionId());
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            return Objects.equals(oldList.get(oldPos), newList.get(newPos));
        }
    }
}