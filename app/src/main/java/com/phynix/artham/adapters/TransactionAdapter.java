package com.phynix.artham.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
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
        void onEditClick(TransactionModel transaction);   // Can be used if needed
        void onDeleteClick(TransactionModel transaction); // Triggered by Menu
        void onCopyClick(TransactionModel transaction);   // Triggered by Menu
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
        // Inflate the appropriate layout based on type
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

        // Using DiffUtil to calculate changes efficiently
        TransactionDiffCallback diffCallback = new TransactionDiffCallback(this.transactionList, newTransactions);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.transactionList.clear();
        this.transactionList.addAll(newTransactions);
        diffResult.dispatchUpdatesTo(this);
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        // Core Views
        TextView categoryTextView, amountTextView, dateTextView, paymentModeTextView, remarkTextView;
        View transactionTypeIndicator;

        // 3-Dot Menu Button
        ImageButton menuButton;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            initializeViews();
        }

        private void initializeViews() {
            // Core Data Views
            categoryTextView = itemView.findViewById(R.id.categoryTextView);
            amountTextView = itemView.findViewById(R.id.amountTextView);
            remarkTextView = itemView.findViewById(R.id.remarkTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            paymentModeTextView = itemView.findViewById(R.id.paymentModeTextView);

            // Visual Indicators
            transactionTypeIndicator = itemView.findViewById(R.id.transactionTypeIndicator);

            // Menu Button
            menuButton = itemView.findViewById(R.id.menuButton);
        }

        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        void bind(final TransactionModel transaction) {
            if (transaction == null) return;

            Context context = itemView.getContext();

            // 1. Set Text Data
            categoryTextView.setText(transaction.getTransactionCategory());
            paymentModeTextView.setText(transaction.getPaymentMode());

            // Handle Remark Visibility
            if (transaction.getRemark() != null && !transaction.getRemark().isEmpty()) {
                remarkTextView.setText(transaction.getRemark());
                remarkTextView.setVisibility(View.VISIBLE);
            } else {
                remarkTextView.setVisibility(View.GONE);
            }

            // 2. Set Colors & Amount Formatting
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                amountTextView.setText("₹" + String.format("%.2f", transaction.getAmount()));
                int color = ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor); // Green
                amountTextView.setTextColor(color);
                if (transactionTypeIndicator != null) transactionTypeIndicator.setBackgroundColor(color);
            } else {
                amountTextView.setText("- ₹" + String.format("%.2f", transaction.getAmount()));
                int color = ThemeUtil.getThemeAttrColor(context, R.attr.chk_expenseColor); // Red
                amountTextView.setTextColor(color);
                if (transactionTypeIndicator != null) transactionTypeIndicator.setBackgroundColor(color);
            }

            // 3. Date Formatting
            if (transaction.getTimestamp() > 0) {
                Date date = new Date(transaction.getTimestamp());
                String dateStr = new SimpleDateFormat("MMM dd", Locale.US).format(date);
                String timeStr = new SimpleDateFormat("hh:mm a", Locale.US).format(date);
                dateTextView.setText(dateStr + " • " + timeStr);
            }

            // 4. Main Item Click -> Open Details Dialog
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(transaction);
                }
            });

            // 5. Menu Button Click -> Show Popup Menu
            if (menuButton != null) {
                menuButton.setOnClickListener(v -> {
                    // Create Popup Menu anchored to the button
                    PopupMenu popup = new PopupMenu(context, v);
                    // Ensure you have created res/menu/transaction_options.xml
                    popup.inflate(R.menu.transaction_options);

                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            int id = item.getItemId();

                            // [NEW] Handle Edit
                            if (id == R.id.action_edit) {
                                if (listener != null) listener.onEditClick(transaction);
                                return true;
                            }
                            // Existing Copy logic
                            else if (id == R.id.action_copy) {
                                if (listener != null) listener.onCopyClick(transaction);
                                return true;
                            }
                            // Existing Delete logic
                            else if (id == R.id.action_delete) {
                                if (listener != null) listener.onDeleteClick(transaction);
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.show();
                });
            }
        }
    }

    // --- DiffUtil for efficient updates ---
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
            // Check for content equality to trigger redraw if needed
            return oldItem.getAmount() == newItem.getAmount() &&
                    oldItem.getTimestamp() == newItem.getTimestamp() &&
                    Objects.equals(oldItem.getType(), newItem.getType()) &&
                    Objects.equals(oldItem.getRemark(), newItem.getRemark());
        }
    }

    // --- Helper for Theme Colors ---
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK;
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
            return Color.BLACK; // Default fallback
        }
    }
}