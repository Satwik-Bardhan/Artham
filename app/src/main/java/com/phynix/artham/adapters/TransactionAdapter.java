package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.TransactionModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private static final String TAG = "TransactionAdapter";
    private List<TransactionModel> transactionList;
    private final OnItemClickListener listener;

    private static final int VIEW_TYPE_IN = 1;
    private static final int VIEW_TYPE_OUT = 2;

    public interface OnItemClickListener {
        void onItemClick(TransactionModel transaction);
        void onEditClick(TransactionModel transaction);
        void onDeleteClick(TransactionModel transaction);
        void onCopyClick(TransactionModel transaction);
    }

    public TransactionAdapter(List<TransactionModel> transactionList, OnItemClickListener listener) {
        this.transactionList = new ArrayList<>(transactionList);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        TransactionModel transaction = transactionList.get(position);
        if ("IN".equalsIgnoreCase(transaction.getType())) {
            return VIEW_TYPE_IN;
        } else {
            return VIEW_TYPE_OUT;
        }
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_IN) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction_in, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction_out, parent, false);
        }
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        if (position < transactionList.size()) {
            TransactionModel transaction = transactionList.get(position);
            holder.bind(transaction);
        }
    }

    @Override
    public int getItemCount() {
        return transactionList != null ? transactionList.size() : 0;
    }

    public void updateTransactions(List<TransactionModel> newTransactions) {
        if (newTransactions == null) {
            newTransactions = new ArrayList<>();
        }

        TransactionDiffCallback diffCallback = new TransactionDiffCallback(this.transactionList, newTransactions);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.transactionList.clear();
        this.transactionList.addAll(newTransactions);
        diffResult.dispatchUpdatesTo(this);
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        // Core Views
        TextView titleTextView, amountTextView, partyTextView, dateTextView, paymentModeTextView, transactionTimeTextView;
        View transactionTypeIndicator;

        // Action Buttons
        ImageButton editButton, copyButton, deleteButton;

        // Expanded Details Views (Kept for compatibility with XML, but visibility logic is handled by Dialog now)
        LinearLayout expandedDetailsLayout;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            initializeViews();
        }

        private void initializeViews() {
            // Core
            titleTextView = itemView.findViewById(R.id.titleTextView);
            amountTextView = itemView.findViewById(R.id.amountTextView);
            partyTextView = itemView.findViewById(R.id.partyTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            paymentModeTextView = itemView.findViewById(R.id.paymentModeTextView);
            transactionTimeTextView = itemView.findViewById(R.id.transactionTimeTextView);
            transactionTypeIndicator = itemView.findViewById(R.id.transactionTypeIndicator);

            // Buttons
            editButton = itemView.findViewById(R.id.editButton);
            copyButton = itemView.findViewById(R.id.copyButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);

            // We find this to prevent crashes, but we won't toggle it on click anymore
            expandedDetailsLayout = itemView.findViewById(R.id.expandedDetailsLayout);
        }

        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        void bind(final TransactionModel transaction) {
            if (transaction == null) return;

            Context context = itemView.getContext();

            // Set Data
            titleTextView.setText(transaction.getTransactionCategory());
            partyTextView.setText(transaction.getPartyName() != null && !transaction.getPartyName().isEmpty()
                    ? transaction.getPartyName() : "No Party");
            paymentModeTextView.setText(transaction.getPaymentMode());

            // Set Colors & Formatting
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                amountTextView.setText("₹" + String.format("%.2f", transaction.getAmount()));
                int color = ThemeUtil.getThemeAttrColor(context, R.attr.incomeColor);
                amountTextView.setTextColor(color);
                if(transactionTypeIndicator != null) transactionTypeIndicator.setBackgroundColor(color);
            } else {
                amountTextView.setText("- ₹" + String.format("%.2f", transaction.getAmount()));
                int color = ThemeUtil.getThemeAttrColor(context, R.attr.expenseColor);
                amountTextView.setTextColor(color);
                if(transactionTypeIndicator != null) transactionTypeIndicator.setBackgroundColor(color);
            }

            // Date & Time
            if (transaction.getTimestamp() > 0) {
                Date date = new Date(transaction.getTimestamp());
                dateTextView.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date));
                transactionTimeTextView.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(date));
            }

            // Ensure expanded part is hidden (since we are using Dialog now)
            if (expandedDetailsLayout != null) {
                expandedDetailsLayout.setVisibility(View.GONE);
            }

            // [FIXED] Click listener now calls the interface method to open the Dialog
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(transaction);
                }
            });

            // Button Listeners
            editButton.setOnClickListener(v -> {
                if (listener != null) listener.onEditClick(transaction);
            });
            copyButton.setOnClickListener(v -> {
                if (listener != null) listener.onCopyClick(transaction);
            });
            deleteButton.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(transaction);
            });
        }
    }

    private static class TransactionDiffCallback extends DiffUtil.Callback {
        private final List<TransactionModel> oldList;
        private final List<TransactionModel> newList;

        public TransactionDiffCallback(List<TransactionModel> oldList, List<TransactionModel> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getTransactionId().equals(newList.get(newItemPosition).getTransactionId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TransactionModel oldItem = oldList.get(oldItemPosition);
            TransactionModel newItem = newList.get(newItemPosition);
            return oldItem.getAmount() == newItem.getAmount() &&
                    oldItem.getTimestamp() == newItem.getTimestamp() &&
                    Objects.equals(oldItem.getType(), newItem.getType()) &&
                    Objects.equals(oldItem.getRemark(), newItem.getRemark());
        }
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK;
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}