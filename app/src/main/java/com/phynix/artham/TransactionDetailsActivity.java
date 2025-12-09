package com.phynix.artham;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.CategoryColorUtil;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION = "extra_transaction";
    private TransactionModel transaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details); // We will update the layout name below

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Retrieve Data
        if (getIntent() != null && getIntent().hasExtra(EXTRA_TRANSACTION)) {
            transaction = (TransactionModel) getIntent().getSerializableExtra(EXTRA_TRANSACTION);
        }

        if (transaction == null) {
            finish(); // Close if no data
            return;
        }

        initializeViews();
    }

    private void initializeViews() {
        // --- Header ---
        ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> finish()); // Close Activity

        // --- Hero Section ---
        TextView detailRemark = findViewById(R.id.detailRemark);
        TextView detailAmount = findViewById(R.id.detailAmount);
        TextView detailTypeChip = findViewById(R.id.detailTypeChip);

        TextView detailCategoryName = findViewById(R.id.detailCategoryName);
        View iconCard = findViewById(R.id.iconCard);

        // 1. Remark Logic
        if (TextUtils.isEmpty(transaction.getRemark())) {
            detailRemark.setText(transaction.getTransactionCategory());
        } else {
            detailRemark.setText(transaction.getRemark());
        }

        // 2. Amount & Type Styling
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        detailAmount.setText(currencyFormat.format(transaction.getAmount()));

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            detailTypeChip.setText("Income");
            int incomeColor = getThemeColor(R.attr.incomeColor);
            detailAmount.setTextColor(incomeColor);
            detailTypeChip.setTextColor(incomeColor);
        } else {
            detailTypeChip.setText("Expense");
            int expenseColor = getThemeColor(R.attr.expenseColor);
            detailAmount.setTextColor(expenseColor);
            detailTypeChip.setTextColor(expenseColor);
        }

        // 3. Category Logic
        detailCategoryName.setText(transaction.getTransactionCategory());
        int categoryColor = CategoryColorUtil.getCategoryColor(this, transaction.getTransactionCategory());

        if (iconCard instanceof CardView) {
            ((CardView) iconCard).setCardBackgroundColor(categoryColor);
        } else if (iconCard instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) iconCard).setCardBackgroundColor(categoryColor);
        }

        // --- Metadata Section ---
        TextView detailDateTime = findViewById(R.id.detailDateTime);
        TextView detailPaymentMode = findViewById(R.id.detailPaymentMode);

        TextView detailParty = findViewById(R.id.detailParty);
        View detailPartyLayout = findViewById(R.id.detailPartyLayout);

        TextView detailTags = findViewById(R.id.detailTags);
        View detailTagsLayout = findViewById(R.id.detailTagsLayout);

        // Date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
        detailDateTime.setText(dateFormat.format(new Date(transaction.getTimestamp())));

        // Mode
        detailPaymentMode.setText(transaction.getPaymentMode());

        // Party Visibility
        if (TextUtils.isEmpty(transaction.getPartyName())) {
            detailPartyLayout.setVisibility(View.GONE);
        } else {
            detailPartyLayout.setVisibility(View.VISIBLE);
            detailParty.setText(transaction.getPartyName());
        }

        // Tags Visibility
        if (transaction.getTags() == null || transaction.getTags().isEmpty()) {
            detailTagsLayout.setVisibility(View.GONE);
        } else {
            detailTagsLayout.setVisibility(View.VISIBLE);
            detailTags.setText(transaction.getTags().toString().replace("[", "").replace("]", ""));
        }

        // Hide unused layout sections safely
        safeHide(R.id.detailTaxLayout);
        safeHide(R.id.detailLocationLayout);
        safeHide(R.id.attachmentsLabel);
        safeHide(R.id.attachmentPreview);

        // --- Action Buttons ---
        MaterialButton btnEdit = findViewById(R.id.btnEditTransaction);
        MaterialButton btnDelete = findViewById(R.id.btnDeleteTransaction);
        MaterialButton btnShare = findViewById(R.id.btnShareTransaction);

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditTransactionActivity.class);
            intent.putExtra("transaction_model", transaction);
            startActivity(intent);
            finish();
        });

        btnDelete.setOnClickListener(v -> {
            // Since this is a new activity, you might want to return a result to the previous screen
            // Or just trigger your delete logic here. For now, we simulate returning.
            Intent resultIntent = new Intent();
            resultIntent.putExtra("action", "delete");
            resultIntent.putExtra("transaction_id", transaction.getTransactionId());
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        btnShare.setOnClickListener(v -> shareTransactionDetails());
    }

    private void safeHide(int id) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(View.GONE);
    }

    private int getThemeColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private void shareTransactionDetails() {
        String shareBody = "Transaction Details:\n" +
                "Remark: " + (TextUtils.isEmpty(transaction.getRemark()) ? transaction.getTransactionCategory() : transaction.getRemark()) + "\n" +
                "Amount: " + transaction.getAmount() + "\n" +
                "Type: " + transaction.getType() + "\n" +
                "Date: " + new Date(transaction.getTimestamp()).toString();

        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Transaction Details");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }
}