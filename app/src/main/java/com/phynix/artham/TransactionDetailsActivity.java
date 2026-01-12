package com.phynix.artham;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.SnackbarHelper; // [NEW IMPORT]
import com.phynix.artham.viewmodels.TransactionViewModel;
import com.phynix.artham.viewmodels.TransactionViewModelFactory;

import java.io.Serializable;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION = "extra_transaction";
    private TransactionModel transaction;
    private String cashbookId;
    private TransactionViewModel viewModel;

    private final ActivityResultLauncher<Intent> editLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_TRANSACTION)) {
                transaction = (TransactionModel) intent.getSerializableExtra(EXTRA_TRANSACTION);
            }
            if (intent.hasExtra("cashbook_id")) {
                cashbookId = intent.getStringExtra("cashbook_id");
            }
        }

        if (transaction == null || cashbookId == null) {
            showSnackbar("Error loading transaction details");
            finish();
            return;
        }

        initViewModel();
        initializeViews();
    }

    private void initViewModel() {
        TransactionViewModelFactory factory = new TransactionViewModelFactory(getApplication(), cashbookId);
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);
    }

    private void initializeViews() {
        View closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> finish());

        View menuButton = findViewById(R.id.menuButton);


        TextView detailAmount = findViewById(R.id.detailAmount);
        TextView detailType = findViewById(R.id.detailType);
        TextView detailDate = findViewById(R.id.detailDate);
        TextView detailCategory = findViewById(R.id.detailCategory);
        TextView detailRemark = findViewById(R.id.detailRemark);
        TextView detailPaymentMode = findViewById(R.id.detailPaymentMode);

        View partySection = findViewById(R.id.partySection);
        TextView detailParty = findViewById(R.id.detailParty);

        View tagsSection = findViewById(R.id.tagsSection);
        TextView detailTags = findViewById(R.id.detailTags);

        ImageView typeIcon = findViewById(R.id.typeIcon);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        detailAmount.setText(currencyFormat.format(transaction.getAmount()));

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            detailType.setText("INCOME");
            int greenColor = Color.parseColor("#388E3C");
            detailType.setTextColor(greenColor);
            detailAmount.setTextColor(greenColor);
            typeIcon.setImageResource(R.drawable.ic_plus);
            typeIcon.setColorFilter(greenColor);
            typeIcon.setBackgroundResource(R.drawable.circle_background_soft_green);
        } else {
            detailType.setText("EXPENSE");
            int redColor = Color.parseColor("#D32F2F");
            detailType.setTextColor(redColor);
            detailAmount.setTextColor(redColor);
            typeIcon.setImageResource(R.drawable.ic_minus);
            typeIcon.setColorFilter(redColor);
            typeIcon.setBackgroundResource(R.drawable.circle_background_soft_red);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        detailDate.setText(dateFormat.format(new Date(transaction.getTimestamp())));

        detailCategory.setText(transaction.getTransactionCategory());
        if (detailPaymentMode != null) detailPaymentMode.setText(transaction.getPaymentMode());

        if (TextUtils.isEmpty(transaction.getRemark())) {
            detailRemark.setText("No details provided");
        } else {
            detailRemark.setText(transaction.getRemark());
        }

        if (TextUtils.isEmpty(transaction.getPartyName())) {
            if (partySection != null) partySection.setVisibility(View.GONE);
        } else {
            if (partySection != null) partySection.setVisibility(View.VISIBLE);
            detailParty.setText(transaction.getPartyName());
        }

        if (transaction.getTags() == null || transaction.getTags().isEmpty()) {
            if (tagsSection != null) tagsSection.setVisibility(View.GONE);
        } else {
            if (tagsSection != null) tagsSection.setVisibility(View.VISIBLE);
            detailTags.setText(transaction.getTags().toString().replace("[", "").replace("]", ""));
        }

        MaterialButton btnEdit = findViewById(R.id.btnEditTransaction);
        btnEdit.setOnClickListener(v -> openEditActivity());
    }

    private void openEditActivity() {
        Intent intent = new Intent(this, EditTransactionActivity.class);
        intent.putExtra("transaction_model", (Serializable) transaction);
        intent.putExtra("cashbook_id", cashbookId);
        editLauncher.launch(intent);
    }



    private void deleteTransaction() {
        viewModel.deleteTransaction(transaction.getTransactionId());
        showSnackbar("Transaction Deleted");

        Intent resultIntent = new Intent();
        resultIntent.putExtra("action", "delete");
        resultIntent.putExtra("transaction_id", transaction.getTransactionId());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void duplicateTransaction() {
        TransactionModel copy = transaction;

        Intent resultIntent = new Intent();
        resultIntent.putExtra("action", "duplicate");
        resultIntent.putExtra("transaction", transaction);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (d, w) -> deleteTransaction())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareTransactionDetails() {
        String shareBody = "Transaction Details:\n" +
                "Type: " + ("IN".equals(transaction.getType()) ? "Income (+)" : "Expense (-)") + "\n" +
                "Amount: " + transaction.getAmount() + "\n" +
                "Category: " + transaction.getTransactionCategory() + "\n" +
                "Date: " + new Date(transaction.getTimestamp()).toString();

        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Transaction Receipt");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    // [FIX] Snackbar with Helper logic
    private void showSnackbar(String message) {
        // Anchor to the footer (e.g., the Edit button container)
        SnackbarHelper.show(this, message, R.id.footerLayout);
    }
}