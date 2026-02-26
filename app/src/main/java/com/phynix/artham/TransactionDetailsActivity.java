package com.phynix.artham;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.ThemeManager;
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
        // Apply Theme BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

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
        menuButton.setOnClickListener(v -> showPopupMenu(v));

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

        // --- Running Balance Section Logic ---
        LinearLayout btnViewRunningBalance = findViewById(R.id.btnViewRunningBalance);
        TextView runningBalanceLabel = findViewById(R.id.runningBalanceLabel);
        TextView runningBalanceAmount = findViewById(R.id.runningBalanceAmount);
        ImageView runningBalanceActionIcon = findViewById(R.id.runningBalanceActionIcon);

        btnViewRunningBalance.setOnClickListener(v -> {
            boolean isVisible = runningBalanceAmount.getVisibility() == View.VISIBLE;

            if (isVisible) {
                // Collapse
                runningBalanceAmount.setVisibility(View.GONE);
                runningBalanceLabel.setText("Tap to view balance impact");

                // Animate the drop-down arrow rotating back to normal (points down)
                runningBalanceActionIcon.animate().rotation(0f).setDuration(200).start();
            } else {
                // Expand and Calculate
                double calculatedRunningBalance = viewModel.getRunningBalanceUpTo(transaction.getTimestamp());

                runningBalanceAmount.setText(currencyFormat.format(calculatedRunningBalance));
                runningBalanceAmount.setVisibility(View.VISIBLE);
                runningBalanceLabel.setText("Balance after this entry");

                // Animate the drop-down arrow rotating 180 degrees (points up)
                runningBalanceActionIcon.animate().rotation(180f).setDuration(200).start();

                // Color text conditionally based on positive/negative balance
                if (calculatedRunningBalance >= 0) {
                    runningBalanceAmount.setTextColor(Color.parseColor("#388E3C")); // Green
                } else {
                    runningBalanceAmount.setTextColor(Color.parseColor("#D32F2F")); // Red
                }
            }
        });

        MaterialButton btnEdit = findViewById(R.id.btnEditTransaction);
        btnEdit.setOnClickListener(v -> openEditActivity());
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.inflate(R.menu.transaction_options);

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit) {
                openEditActivity();
                return true;
            } else if (itemId == R.id.action_copy) {
                duplicateTransaction();
                return true;
            } else if (itemId == R.id.action_delete) {
                showDeleteConfirmation();
                return true;
            }
            return false;
        });

        popupMenu.show();
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

    private void showSnackbar(String message) {
        SnackbarHelper.show(this, message, R.id.footerLayout);
    }
}