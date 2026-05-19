package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.DefaultCategoryManager;
import com.phynix.artham.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Category Detail Activity
 * Shows all transactions for a specific category within a selected month.
 * Launched from ExpenseAnalyticsActivity when user clicks a category in the breakdown.
 */
public class CategoryDetailActivity extends BaseActivity {

    // Intent Extra Keys
    public static final String EXTRA_CATEGORY_NAME = "category_name";
    public static final String EXTRA_CATEGORY_AMOUNT = "category_amount";
    public static final String EXTRA_CATEGORY_PERCENTAGE = "category_percentage";
    public static final String EXTRA_CATEGORY_COLOR = "category_color";
    public static final String EXTRA_CATEGORY_ICON_RES_ID = "category_icon_res_id";
    public static final String EXTRA_MONTH_LABEL = "month_label";
    public static final String EXTRA_TRANSACTIONS = "transactions";
    public static final String EXTRA_CASHBOOK_ID = "cashbook_id";

    private String cashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_detail);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Extract intent data
        String categoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        float categoryAmount = getIntent().getFloatExtra(EXTRA_CATEGORY_AMOUNT, 0f);
        float categoryPercentage = getIntent().getFloatExtra(EXTRA_CATEGORY_PERCENTAGE, 0f);
        int categoryColor = getIntent().getIntExtra(EXTRA_CATEGORY_COLOR, Color.GRAY);
        int categoryIconResId = getIntent().getIntExtra(EXTRA_CATEGORY_ICON_RES_ID, R.drawable.ic_category);
        String monthLabel = getIntent().getStringExtra(EXTRA_MONTH_LABEL);
        cashbookId = getIntent().getStringExtra(EXTRA_CASHBOOK_ID);

        ArrayList<TransactionModel> transactions = (ArrayList<TransactionModel>)
                getIntent().getSerializableExtra(EXTRA_TRANSACTIONS);

        if (categoryName == null) {
            finish();
            return;
        }

        if (transactions == null) {
            transactions = new ArrayList<>();
        }

        // Sort transactions: newest first
        Collections.sort(transactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

        setupHeader(categoryName, monthLabel);
        setupSummaryCard(categoryName, categoryAmount, categoryPercentage, categoryColor, categoryIconResId, transactions.size());
        setupTransactionsList(transactions);
    }

    private void setupHeader(String categoryName, String monthLabel) {
        ImageButton backButton = findViewById(R.id.backButton);
        TextView headerTitle = findViewById(R.id.headerTitle);
        TextView headerSubtitle = findViewById(R.id.headerSubtitle);

        backButton.setOnClickListener(v -> finish());
        headerTitle.setText(categoryName);

        if (monthLabel != null && !monthLabel.isEmpty()) {
            headerSubtitle.setText(monthLabel);
            headerSubtitle.setVisibility(View.VISIBLE);
        } else {
            headerSubtitle.setVisibility(View.GONE);
        }
    }

    @SuppressLint("DefaultLocale")
    private void setupSummaryCard(String categoryName, float amount, float percentage, int color, int iconResId, int txnCount) {
        FrameLayout iconContainer = findViewById(R.id.categoryIconContainer);
        ImageView categoryIcon = findViewById(R.id.categoryIcon);
        TextView categoryNameTv = findViewById(R.id.categoryName);
        TextView categoryTotalAmount = findViewById(R.id.categoryTotalAmount);
        TextView categoryPercentage = findViewById(R.id.categoryPercentage);
        TextView transactionCount = findViewById(R.id.transactionCount);
        ProgressBar progressBar = findViewById(R.id.categoryProgressBar);

        // Set category icon and color
        if (iconResId != 0) {
            categoryIcon.setImageResource(iconResId);
        } else {
            categoryIcon.setImageResource(R.drawable.ic_category);
        }
        categoryIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        iconContainer.setBackgroundTintList(ColorStateList.valueOf(color));

        // Set texts
        categoryNameTv.setText(categoryName);
        AmountFormatter.setAdaptiveAmount(categoryTotalAmount, amount, 18f, 11f);
        categoryPercentage.setText(String.format(Locale.US, "%.1f%%", percentage));
        transactionCount.setText(String.valueOf(txnCount));

        // Progress bar
        progressBar.setProgress((int) percentage);
        progressBar.setProgressTintList(ColorStateList.valueOf(color));
    }

    private void setupTransactionsList(List<TransactionModel> transactions) {
        RecyclerView recyclerView = findViewById(R.id.transactionsRecyclerView);
        TextView emptyText = findViewById(R.id.emptyTransactionsText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (transactions.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            recyclerView.setAdapter(new CategoryTransactionAdapter(transactions, txn -> {
                Intent intent = new Intent(CategoryDetailActivity.this, TransactionDetailsActivity.class);
                intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, txn);
                intent.putExtra("cashbook_id", cashbookId);
                startActivity(intent);
            }));
        }
    }

    // --- Inner Adapter ---

    static class CategoryTransactionAdapter extends RecyclerView.Adapter<CategoryTransactionAdapter.ViewHolder> {

        private final List<TransactionModel> transactions;
        private final OnTransactionClickListener clickListener;

        interface OnTransactionClickListener {
            void onTransactionClick(TransactionModel transaction);
        }

        CategoryTransactionAdapter(List<TransactionModel> transactions, OnTransactionClickListener clickListener) {
            this.transactions = transactions;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category_transaction, parent, false);
            return new ViewHolder(view);
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TransactionModel txn = transactions.get(position);
            holder.bind(txn);
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onTransactionClick(txn);
            });
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView remarkText, dateText, paymentModeText, amountText;

            ViewHolder(View v) {
                super(v);
                remarkText = v.findViewById(R.id.txnRemarkText);
                dateText = v.findViewById(R.id.txnDateText);
                paymentModeText = v.findViewById(R.id.txnPaymentMode);
                amountText = v.findViewById(R.id.txnAmountText);
            }

            @SuppressLint("DefaultLocale")
            void bind(TransactionModel txn) {
                // Remark or party name — fallback to category
                String displayText = txn.getRemark();
                if (displayText == null || displayText.trim().isEmpty()) {
                    displayText = txn.getPartyName();
                }
                if (displayText == null || displayText.trim().isEmpty()) {
                    displayText = txn.getTransactionCategory() != null ? txn.getTransactionCategory() : "Expense";
                }
                remarkText.setText(displayText);

                // Date
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM • hh:mm a", Locale.US);
                dateText.setText(dateFormat.format(new Date(txn.getTimestamp())));

                // Payment mode
                String pMode = txn.getPaymentMode();
                if (pMode != null && !pMode.trim().isEmpty()) {
                    paymentModeText.setText(pMode);
                    paymentModeText.setVisibility(View.VISIBLE);
                } else {
                    paymentModeText.setVisibility(View.GONE);
                }

                // Amount
                com.phynix.artham.utils.AmountFormatter.setAdaptiveAmount(amountText, txn.getAmount(), 14f, 9f);
                amountText.setText(android.text.TextUtils.concat("- ", amountText.getText()));

                // Apply theme colors
                Context ctx = itemView.getContext();
                int expenseColor = getThemeAttrColor(ctx, R.attr.chk_expenseColor);
                amountText.setTextColor(expenseColor);
            }

            private static int getThemeAttrColor(Context context, int attr) {
                TypedValue typedValue = new TypedValue();
                if (context.getTheme().resolveAttribute(attr, typedValue, true)) return typedValue.data;
                return Color.BLACK;
            }
        }
    }
}
