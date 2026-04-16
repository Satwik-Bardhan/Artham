package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Artham Transaction Adapter
 * Implements date-wise grouping with separate headers for each day.
 * Manages Cash In and Cash Out items with unique layouts and Synced Category Icons.
 */
public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Object> items = new ArrayList<>(); // Mixed list: Strings (Headers) and TransactionModels
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
        if (item instanceof String) {
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
            ((HeaderViewHolder) holder).bind((String) item);
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

        // Sort descending (Newest first)
        List<TransactionModel> sortedList = new ArrayList<>(transactions);
        Collections.sort(sortedList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

        List<Object> grouped = new ArrayList<>();
        String lastDate = "";
        SimpleDateFormat headerFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

        for (TransactionModel t : sortedList) {
            String currentDate = headerFormat.format(new Date(t.getTimestamp()));
            if (!currentDate.equals(lastDate)) {
                grouped.add(currentDate); // Inject Date Header
                lastDate = currentDate;
            }
            grouped.add(t); // Inject Transaction Item
        }
        return grouped;
    }

    // --- ViewHolders ---

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerText;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.dateHeaderTextView);
        }
        void bind(String date) {
            headerText.setText(date);
        }
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView categoryTextView, amountTextView, dateTextView, paymentModeTextView, remarkTextView, autoFrequencyText;
        View transactionTypeIndicator, iconContainer, autoRepeatBadge;
        ImageView menuButton, categoryIcon;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryTextView = itemView.findViewById(R.id.categoryTextView);
            amountTextView = itemView.findViewById(R.id.amountTextView);
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
            CharSequence amountSpan = AmountFormatter.formatCompactSpannable(transaction.getAmount());
            amountTextView.setText(android.text.TextUtils.concat(prefix, amountSpan));
            amountTextView.setTextColor(color);
            if (transactionTypeIndicator != null) transactionTypeIndicator.setBackgroundColor(color);

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
            if (oldObj instanceof String && newObj instanceof String) return oldObj.equals(newObj);
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

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) return typedValue.data;
            return Color.BLACK;
        }
    }
}