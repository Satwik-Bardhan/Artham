package com.phynix.artham;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.auth.FirebaseAuth;
import com.phynix.artham.databinding.ActivityHomePageBinding;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.DateTimeUtils;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.HomePageViewModel;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;
    private static final int PERMISSION_REQUEST_CODE_NOTIFICATIONS = 101;

    // ViewBinding
    private ActivityHomePageBinding binding;

    // ViewModel
    private HomePageViewModel viewModel;

    // Utils
    private NumberFormat currencyFormat;

    // Launchers
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // ViewModel observes LiveData, so manual refresh is not needed here.
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply Theme first
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);

        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(HomePageViewModel.class);
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // 2. Check Auth
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            signOutUser();
            return;
        }

        // 3. Setup UI
        setupUI();
        setupBottomNavigation();
        setupClickListeners();

        // 4. Observe ViewModel Data
        observeViewModel();

        // 5. Check Permissions
        checkNotificationPermissionAndShowFeedback();
    }

    private void setupUI() {
        // Initial visibility states
        binding.transactionSection.setVisibility(View.VISIBLE);
        binding.transactionTable.setVisibility(View.VISIBLE);
        binding.emptyStateView.setVisibility(View.GONE);
    }

    private void observeViewModel() {
        // --- Status & Errors ---
        viewModel.getIsLoading().observe(this, isLoading -> {
            // Optional: binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                // Only show loading text if we don't have a cashbook name yet
                if (binding.userNameTop.getText().toString().isEmpty()) {
                    binding.userNameTop.setText("Loading...");
                }
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showSnackbar(error);
            }
        });

        // --- User Profile ---
        viewModel.getUserProfile().observe(this, user -> {
            updateUserUI(user);
        });

        // --- Cashbook Info ---
        viewModel.getActiveCashbook().observe(this, cashbook -> {
            if (cashbook != null) {
                binding.userNameTop.setText(cashbook.getName());
                binding.currentCashbookText.setText(cashbook.getName());
                String timeSpan = DateTimeUtils.getRelativeTimeSpan(cashbook.getLastModified());
                binding.lastOpenedText.setText("Last opened: " + timeSpan);
            } else {
                binding.userNameTop.setText("No Cashbook");
            }
        });

        // --- Financial Totals (Balance Card) ---
        viewModel.getTotalIncome().observe(this, income ->
                binding.balanceCardView.moneyIn.setText(formatCurrency(income)));

        viewModel.getTotalExpense().observe(this, expense ->
                binding.balanceCardView.moneyOut.setText(formatCurrency(expense)));

        viewModel.getCurrentBalance().observe(this, balance -> {
            binding.balanceCardView.balanceText.setText(formatCurrency(balance));
            binding.balanceCardView.balanceText.setTextColor(Color.WHITE);
        });

        // --- Daily Summary ---
        viewModel.getTodayBalance().observe(this, balance -> {
            // Using <include> binding directly
            binding.dailySummaryInclude.dailyDateText.setText(DateTimeUtils.formatDate(System.currentTimeMillis(), Constants.DATE_FORMAT_DISPLAY));

            String sign = balance >= 0 ? "+ " : "- ";
            binding.dailySummaryInclude.dailyBalanceText.setText(sign + formatCurrency(Math.abs(balance)));

            if (balance >= 0) {
                binding.dailySummaryInclude.dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_incomeColor));
            } else {
                binding.dailySummaryInclude.dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_expenseColor));
            }
        });

        // --- Transactions List (Today) ---
        viewModel.getTodaysTransactions().observe(this, transactions -> {
            updateTransactionTable(transactions);
        });
    }

    private void updateTransactionTable(List<TransactionModel> transactions) {
        binding.transactionTable.removeAllViews();

        if (transactions == null || transactions.isEmpty()) {
            binding.emptyStateView.setVisibility(View.VISIBLE);
            binding.transactionTable.setVisibility(View.GONE);
            binding.transactionCount.setText("TODAY (0)");
        } else {
            binding.emptyStateView.setVisibility(View.GONE);
            binding.transactionTable.setVisibility(View.VISIBLE);
            binding.transactionCount.setText("TODAY (" + transactions.size() + ")");

            for (TransactionModel t : transactions) {
                addTransactionRow(t);
            }
        }
    }

    private void addTransactionRow(TransactionModel transaction) {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_transaction_report_row, binding.transactionTable, false);

        TextView rowCategory = rowView.findViewById(R.id.rowCategory);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        rowCategory.setText(transaction.getTransactionCategory());
        rowMode.setText(transaction.getPaymentMode());

        if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(transaction.getType())) {
            rowIn.setText(formatCurrency(transaction.getAmount()));
            rowIn.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_incomeColor));
            rowOut.setText("-");
        } else {
            rowIn.setText("-");
            rowOut.setText(formatCurrency(transaction.getAmount()));
            rowOut.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_expenseColor));
        }

        rowView.setOnClickListener(v -> openTransactionDetail(transaction));
        binding.transactionTable.addView(rowView);
    }

    private void updateUserUI(Users user) {
        if (user == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            // Fallback to Firebase User object
            binding.balanceCardView.userNameBottom.setText(FirebaseAuth.getInstance().getCurrentUser().getDisplayName());
            return;
        }

        if (user != null) {
            String name = (user.getUserName() != null && !user.getUserName().isEmpty()) ? user.getUserName() : "User";
            binding.balanceCardView.userNameBottom.setText(name);
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            binding.balanceCardView.uidText.setText("UID: " + uid.substring(0, Math.min(8, uid.length())) + "...");
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavCard.btnHome.setSelected(true);

        binding.bottomNavCard.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        binding.bottomNavCard.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());

        binding.bottomNavCard.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    private void setupClickListeners() {
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_IN));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_OUT));
        binding.userBox.setOnClickListener(v -> openCashbookSwitcher());
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, transaction);
        intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
        detailsLauncher.launch(intent);
    }

    private void openCashInOutActivity(String type) {
        if (viewModel.getCurrentCashbookId() == null) {
            showSnackbar("Please create a cashbook first");
            return;
        }
        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra(Constants.EXTRA_TRANSACTION_TYPE, type);
        intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
        startActivity(intent);
    }

    private void openCashbookSwitcher() {
        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", viewModel.getCurrentCashbookId());
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newCashbookId = data.getStringExtra("selected_cashbook_id");
            String cashbookName = data.getStringExtra("cashbook_name");

            if (newCashbookId != null) {
                viewModel.switchCashbook(newCashbookId);
                showSnackbar("Switched to: " + cashbookName);
            }
        }
    }

    // --- Helper Methods ---

    private void checkNotificationPermissionAndShowFeedback() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE_NOTIFICATIONS);
            } else {
                showFeedbackNotification();
            }
        } else {
            showFeedbackNotification();
        }
    }

    private void showFeedbackNotification() {
        try {
            FirebaseAppDistribution.getInstance().showFeedbackNotification(
                    "Shake your phone to start feedback!",
                    InterruptionLevel.HIGH
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to show feedback notification", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showFeedbackNotification();
            }
        }
    }

    private String formatCurrency(double amount) {
        if (currencyFormat == null) return "₹" + amount;
        return currencyFormat.format(amount);
    }

    private void showSnackbar(String message) {
        View anchor = (binding.bottomNavCard != null) ? binding.bottomNavCard.getRoot() : null;
        SnackbarHelper.show(this, message, anchor);
    }

    private void signOutUser() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            boolean resolved = context.getTheme().resolveAttribute(attr, typedValue, true);
            if (resolved) {
                return typedValue.data;
            } else {
                return Color.BLACK;
            }
        }
    }
}