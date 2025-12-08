package com.phynix.artham;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.phynix.artham.adapters.TransactionAdapter;
import com.phynix.artham.databinding.ActivityTransactionBinding;
import com.phynix.artham.databinding.LayoutBottomNavigationBinding;
import com.phynix.artham.databinding.LayoutPieChartBinding;
import com.phynix.artham.databinding.LayoutSearchBarBinding;
import com.phynix.artham.databinding.LayoutSummaryCardsBinding;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.viewmodels.TransactionViewModel;
import com.phynix.artham.viewmodels.TransactionViewModelFactory;
import com.phynix.artham.utils.CustomPieChartValueFormatter;
import com.phynix.artham.utils.PdfReportGenerator; // [UPDATED]
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.dialogs.TransactionDetailsDialog;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class TransactionActivity extends AppCompatActivity implements TransactionDetailsDialog.TransactionDialogListener {

    private static final String TAG = "TransactionActivity";
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SHOW_CHART = "show_pie_chart";

    private List<TransactionModel> allTransactions = new ArrayList<>();
    private Calendar currentMonthCalendar;

    private ActivityTransactionBinding binding;
    private LayoutSummaryCardsBinding summaryBinding;
    private LayoutPieChartBinding pieChartBinding;
    private LayoutSearchBarBinding searchBinding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    private TransactionItemFragment transactionFragment;
    private TransactionViewModel viewModel;

    private FirebaseAuth mAuth;
    private String currentCashbookId;
    private FirebaseUser currentUser;

    private ActivityResultLauncher<Intent> filterLauncher;
    private ActivityResultLauncher<Intent> downloadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        currentMonthCalendar = Calendar.getInstance();

        if (currentCashbookId == null || currentUser == null) {
            showToast("Error: No active cashbook found or user not logged in.");
            finish();
            return;
        }

        initializeUI();
        initViewModel();
        setupTransactionFragment();
        setupClickListeners();
        setupBottomNavigation();
        setupLaunchers();
        observeViewModel();
        applySavedChartVisibility();
    }

    private void initializeUI() {
        summaryBinding = binding.summaryCards;
        pieChartBinding = binding.pieChartComponent;
        searchBinding = binding.searchBarContainer;
        bottomNavBinding = binding.bottomNavCard;
    }

    private void initViewModel() {
        TransactionViewModelFactory factory = new TransactionViewModelFactory(getApplication(), currentCashbookId);
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);
    }

    private void observeViewModel() {
        if (viewModel == null) return;
        viewModel.getFilteredTransactions().observe(this, transactions -> {
            this.allTransactions = transactions;
            displayDataForCurrentMonth();
        });
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (transactionFragment != null) transactionFragment.showLoading(isLoading);
        });
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) { showToast(error); viewModel.clearError(); }
        });
    }

    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(new TransactionAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(TransactionModel transaction) {
                TransactionDetailsDialog dialog = TransactionDetailsDialog.newInstance(transaction);
                dialog.show(getSupportFragmentManager(), "TransactionDetailsDialog");
            }
            @Override
            public void onEditClick(TransactionModel transaction) { openEditActivity(transaction); }
            @Override
            public void onDeleteClick(TransactionModel transaction) { showDeleteConfirmation(transaction); }
            @Override
            public void onCopyClick(TransactionModel transaction) { duplicateTransaction(transaction); }
        });
        getSupportFragmentManager().beginTransaction().replace(R.id.transaction_fragment_container, transactionFragment).commit();
    }

    @Override
    public void onEditTransaction(TransactionModel transaction) { openEditActivity(transaction); }
    @Override
    public void onDeleteTransaction(TransactionModel transaction) { showDeleteConfirmation(transaction); }

    private void openEditActivity(TransactionModel transaction) {
        Intent intent = new Intent(TransactionActivity.this, EditTransactionActivity.class);
        intent.putExtra("transaction_model", (Serializable) transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void showDeleteConfirmation(TransactionModel transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (viewModel != null) viewModel.deleteTransaction(transaction.getTransactionId());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void duplicateTransaction(TransactionModel transaction) {
        TransactionModel newTransaction = new TransactionModel();
        newTransaction.setAmount(transaction.getAmount());
        newTransaction.setType(transaction.getType());
        newTransaction.setTransactionCategory(transaction.getTransactionCategory());
        newTransaction.setPaymentMode(transaction.getPaymentMode());
        newTransaction.setPartyName(transaction.getPartyName());
        newTransaction.setRemark("Copy of: " + transaction.getRemark());
        newTransaction.setTimestamp(System.currentTimeMillis());
        newTransaction.setTags(transaction.getTags());
        viewModel.addTransaction(newTransaction);
        showToast("Transaction Duplicated");
    }

    private void setupBottomNavigation() {
        bottomNavBinding.btnTransactions.setSelected(true);
        bottomNavBinding.btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });
        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());
        bottomNavBinding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void setupLaunchers() {
        setupFilterLauncher();
        setupDownloadLauncher();
    }

    private void openCashbookSwitcher() {
        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newCashbookId = data.getStringExtra("selected_cashbook_id");
            String cashbookName = data.getStringExtra("cashbook_name");
            if (newCashbookId != null && !newCashbookId.equals(currentCashbookId)) {
                switchCashbook(newCashbookId, cashbookName);
            }
        }
    }

    private void switchCashbook(String newCashbookId, String cashbookName) {
        currentCashbookId = newCashbookId;
        showToast("Switched to: " + cashbookName);
        saveActiveCashbookId(currentCashbookId);
        initViewModel();
        observeViewModel();
    }

    private void saveActiveCashbookId(String cashbookId) {
        if (currentUser == null) return;
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUser.getUid(), cashbookId).apply();
    }

    private void setupClickListeners() {
        pieChartBinding.pieChartHeader.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseAnalyticsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
        });
        pieChartBinding.monthBackwardButton.setOnClickListener(v -> {
            currentMonthCalendar.add(Calendar.MONTH, -1);
            displayDataForCurrentMonth();
        });
        pieChartBinding.monthForwardButton.setOnClickListener(v -> {
            currentMonthCalendar.add(Calendar.MONTH, 1);
            displayDataForCurrentMonth();
        });
        pieChartBinding.togglePieChartButton.setOnClickListener(v -> {
            boolean isVisible = (pieChartBinding.pieChart.getVisibility() == View.VISIBLE);
            setChartVisibility(!isVisible);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_SHOW_CHART, !isVisible).apply();
        });

        binding.downloadReportButton.setOnClickListener(v -> {
            if (allTransactions == null || allTransactions.isEmpty()) {
                showToast("No data to download");
                return;
            }
            Intent intent = new Intent(this, DownloadOptionsActivity.class);
            downloadLauncher.launch(intent);
        });
    }

    private void applySavedChartVisibility() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean shouldShow = prefs.getBoolean(KEY_SHOW_CHART, true);
        setChartVisibility(shouldShow);
    }

    private void setChartVisibility(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        pieChartBinding.pieChart.setVisibility(visibility);
        View stats = pieChartBinding.getRoot().findViewById(R.id.statsLayout);
        if (stats != null) stats.setVisibility(visibility);
        pieChartBinding.togglePieChartButton.setText(show ? "Hide Pie Chart" : "Show Pie Chart");
    }

    private void displayDataForCurrentMonth() {
        if (allTransactions == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        pieChartBinding.monthTitle.setText(sdf.format(currentMonthCalendar.getTime()));
        List<TransactionModel> monthlyTransactions = allTransactions.stream()
                .filter(t -> {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(t.getTimestamp());
                    return cal.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                            cal.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH);
                }).collect(Collectors.toList());
        updateTotals(monthlyTransactions);
        setupStyledPieChart(monthlyTransactions);
        if (transactionFragment != null) transactionFragment.updateTransactions(monthlyTransactions);
    }

    @SuppressLint("SetTextI18n")
    private void updateTotals(List<TransactionModel> transactions) {
        double totalIncome = 0, totalExpense = 0;
        for (TransactionModel t : transactions) {
            if ("IN".equalsIgnoreCase(t.getType())) totalIncome += t.getAmount();
            else totalExpense += t.getAmount();
        }
        summaryBinding.incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        summaryBinding.expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        summaryBinding.balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));
    }

    private void setupStyledPieChart(List<TransactionModel> transactions) {
        // (Chart logic unchanged)
        // ...
    }

    private void setupFilterLauncher() {
        searchBinding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (viewModel != null) viewModel.filter(s.toString(), 0, 0, "All", null, null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchBinding.filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            filterLauncher.launch(intent);
        });

        filterLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                long startDate = data.getLongExtra("startDate", 0);
                long endDate = data.getLongExtra("endDate", 0);
                String entryType = data.getStringExtra("entryType");
                String paymentMode = data.getStringExtra("paymentMode");
                ArrayList<String> categories = data.getStringArrayListExtra("categories");
                String searchQuery = data.getStringExtra("searchQuery");
                searchBinding.searchEditText.setText(searchQuery);
                if (viewModel != null) viewModel.filter(searchQuery, startDate, endDate, entryType, categories, null);
            }
        });
    }

    private void setupDownloadLauncher() {
        downloadLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                long startDate = data.getLongExtra("startDate", 0);
                long endDate = data.getLongExtra("endDate", 0);
                String entryType = data.getStringExtra("entryType");
                String paymentMode = data.getStringExtra("paymentMode");

                if (checkPermissions()) {
                    prepareAndExport(startDate, endDate, entryType, paymentMode);
                } else {
                    requestPermissions();
                }
            }
        });
    }

    private void prepareAndExport(long startDate, long endDate, String entryType, String paymentMode) {
        if (allTransactions == null || allTransactions.isEmpty()) {
            showToast("No data.");
            return;
        }

        // Filter Logic
        List<TransactionModel> exportList = allTransactions.stream()
                .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                .filter(t -> entryType == null || entryType.equals("All") || t.getType().equalsIgnoreCase(entryType))
                .filter(t -> paymentMode == null || paymentMode.equals("All") || t.getPaymentMode().equalsIgnoreCase(paymentMode))
                .collect(Collectors.toList());

        if (exportList.isEmpty()) {
            showToast("No transactions found.");
            return;
        }

        // [UPDATED] Use PdfReportGenerator
        PdfReportGenerator.generateReport(this, exportList, startDate, endDate);
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showToast("Permission granted.");
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}