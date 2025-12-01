package com.satvik.artham;

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
import android.util.Log;
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

import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.satvik.artham.databinding.ActivityTransactionBinding;
import com.satvik.artham.databinding.LayoutBottomNavigationBinding;
import com.satvik.artham.databinding.LayoutPieChartBinding;
import com.satvik.artham.databinding.LayoutSearchBarBinding;
import com.satvik.artham.databinding.LayoutSummaryCardsBinding;
import com.satvik.artham.utils.CustomPieChartValueFormatter;
import com.satvik.artham.utils.PdfReportGenerator;

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

public class TransactionActivity extends AppCompatActivity {

    private static final String TAG = "TransactionActivity";
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SHOW_CHART = "show_pie_chart";

    // Data
    private List<TransactionModel> allTransactions = new ArrayList<>();
    private Calendar currentMonthCalendar;
    // [FIX] Added variable to store the real cashbook name
    private String currentCashbookName = "My Cashbook";

    private ActivityTransactionBinding binding;
    private LayoutSummaryCardsBinding summaryBinding;
    private LayoutPieChartBinding pieChartBinding;
    private LayoutSearchBarBinding searchBinding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    private TransactionItemFragment transactionFragment;
    private TransactionViewModel viewModel;

    // Firebase
    private FirebaseAuth mAuth;
    private String currentCashbookId;
    private FirebaseUser currentUser;

    // Launchers
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

        // [FIX] Fetch the actual cashbook name when activity starts
        fetchCashbookName();

        initializeUI();
        initViewModel();
        setupTransactionFragment();
        setupClickListeners();
        setupBottomNavigation();
        setupLaunchers();
        observeViewModel();

        applySavedChartVisibility();

        Log.d(TAG, "TransactionActivity created for cashbook: " + currentCashbookId);
    }

    // [FIX] New method to fetch cashbook name from Firebase
    private void fetchCashbookName() {
        DatabaseReference bookRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid())
                .child("cashbooks")
                .child(currentCashbookId);

        bookRef.child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        currentCashbookName = name;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch cashbook name", error.toException());
            }
        });
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
            if (transactionFragment != null) {
                transactionFragment.showLoading(isLoading);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                showToast(error);
                viewModel.clearError();
            }
        });
    }

    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(new TransactionAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(TransactionModel transaction) {
                Intent intent = new Intent(TransactionActivity.this, EditTransactionActivity.class);
                intent.putExtra("transaction_model", (Serializable) transaction);
                intent.putExtra("cashbook_id", currentCashbookId);
                startActivity(intent);
            }

            @Override
            public void onEditClick(TransactionModel transaction) {
                Intent intent = new Intent(TransactionActivity.this, EditTransactionActivity.class);
                intent.putExtra("transaction_model", (Serializable) transaction);
                intent.putExtra("cashbook_id", currentCashbookId);
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(TransactionModel transaction) {
                showDeleteConfirmation(transaction);
            }

            @Override
            public void onCopyClick(TransactionModel transaction) {
                duplicateTransaction(transaction);
            }
        });

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.transaction_fragment_container, transactionFragment)
                .commit();
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
        // [FIX] Update local name variable when switching
        if (cashbookName != null) {
            currentCashbookName = cashbookName;
        } else {
            fetchCashbookName(); // Fetch if name not passed
        }

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
            boolean isCurrentlyVisible = (pieChartBinding.pieChart.getVisibility() == View.VISIBLE);
            boolean newVisibility = !isCurrentlyVisible;
            setChartVisibility(newVisibility);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_SHOW_CHART, newVisibility).apply();
        });

        binding.downloadReportButton.setOnClickListener(v -> {
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
        if (show) {
            pieChartBinding.pieChart.setVisibility(View.VISIBLE);
            try {
                View stats = pieChartBinding.getRoot().findViewById(R.id.statsLayout);
                if (stats != null) stats.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {
            }
            pieChartBinding.togglePieChartButton.setText("Hide Pie Chart");
        } else {
            pieChartBinding.pieChart.setVisibility(View.GONE);
            try {
                View stats = pieChartBinding.getRoot().findViewById(R.id.statsLayout);
                if (stats != null) stats.setVisibility(View.GONE);
            } catch (Exception ignored) {
            }
            pieChartBinding.togglePieChartButton.setText("Show Pie Chart");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        displayDataForCurrentMonth();
    }

    private void displayDataForCurrentMonth() {
        if (allTransactions == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        pieChartBinding.monthTitle.setText(sdf.format(currentMonthCalendar.getTime()));

        List<TransactionModel> monthlyTransactions = allTransactions.stream()
                .filter(t -> {
                    Calendar transactionCal = Calendar.getInstance();
                    transactionCal.setTimeInMillis(t.getTimestamp());
                    return transactionCal.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                            transactionCal.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH);
                }).collect(Collectors.toList());

        updateTotals(monthlyTransactions);
        setupStyledPieChart(monthlyTransactions);

        if (transactionFragment != null) {
            transactionFragment.updateTransactions(monthlyTransactions);
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateTotals(List<TransactionModel> transactions) {
        double totalIncome = 0, totalExpense = 0;
        for (TransactionModel transaction : transactions) {
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else {
                totalExpense += transaction.getAmount();
            }
        }

        summaryBinding.incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        summaryBinding.expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        summaryBinding.balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));
    }

    private void setupStyledPieChart(List<TransactionModel> transactionsForMonth) {
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalExpense = 0f;
        String highestCategory = "-";
        float maxExpense = 0f;

        for (TransactionModel transaction : transactionsForMonth) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory() != null ?
                        transaction.getTransactionCategory() : "Other";
                float amount = (float) transaction.getAmount();
                expenseByCategory.put(category,
                        expenseByCategory.getOrDefault(category, 0f) + amount);

                if (expenseByCategory.get(category) > maxExpense) {
                    maxExpense = expenseByCategory.get(category);
                    highestCategory = category;
                }
                totalExpense += amount;
            }
        }

        pieChartBinding.categoriesCount.setText(String.valueOf(expenseByCategory.size()));
        pieChartBinding.highestCategory.setText(highestCategory);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.textColorPrimary, typedValue, true);
        int textColor = typedValue.data;

        if (totalExpense == 0) {
            pieChartBinding.pieChart.clear();
            pieChartBinding.pieChart.setCenterText("No Expenses");
            pieChartBinding.pieChart.setCenterTextColor(textColor);
            pieChartBinding.pieChart.invalidate();
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#EF5350")); // Red
        colors.add(Color.parseColor("#42A5F5")); // Blue
        colors.add(Color.parseColor("#66BB6A")); // Green
        colors.add(Color.parseColor("#FFCA28")); // Amber
        colors.add(Color.parseColor("#AB47BC")); // Purple
        colors.add(Color.parseColor("#26C6DA")); // Cyan
        colors.add(Color.parseColor("#FF7043")); // Deep Orange
        colors.add(Color.parseColor("#8D6E63")); // Brown
        colors.add(Color.parseColor("#78909C")); // Blue Grey
        colors.add(Color.parseColor("#EC407A")); // Pink

        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            float percentage = entry.getValue() / totalExpense * 100;
            entries.add(new PieEntry(percentage, entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setColors(colors);
        dataSet.setValueLinePart1OffsetPercentage(85f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.5f);
        dataSet.setValueLineColor(Color.parseColor("#828282"));
        dataSet.setValueLineWidth(1.5f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setDrawValues(true);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new CustomPieChartValueFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(textColor);

        pieChartBinding.pieChart.setUsePercentValues(true);
        pieChartBinding.pieChart.getDescription().setEnabled(false);
        pieChartBinding.pieChart.getLegend().setEnabled(false);
        pieChartBinding.pieChart.setRotationEnabled(true);
        pieChartBinding.pieChart.setDrawHoleEnabled(false);
        pieChartBinding.pieChart.setDrawEntryLabels(false);
        pieChartBinding.pieChart.setExtraOffsets(30, 10, 30, 10);
        pieChartBinding.pieChart.setBackgroundColor(Color.TRANSPARENT);

        pieChartBinding.pieChart.setData(data);
        pieChartBinding.pieChart.invalidate();
        pieChartBinding.pieChart.animateY(1200);
    }

    private void setupFilterLauncher() {
        searchBinding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (viewModel != null) {
                    viewModel.filter(s.toString(), 0, 0, "All", null, null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        searchBinding.filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            filterLauncher.launch(intent);
        });

        filterLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        long startDate = data.getLongExtra("startDate", 0);
                        long endDate = data.getLongExtra("endDate", 0);
                        String entryType = data.getStringExtra("entryType");
                        String paymentMode = data.getStringExtra("paymentMode");
                        ArrayList<String> categories = data.getStringArrayListExtra("categories");
                        String searchQuery = data.getStringExtra("searchQuery");

                        searchBinding.searchEditText.setText(searchQuery);

                        if (viewModel != null) {
                            viewModel.filter(searchQuery, startDate, endDate, entryType, categories,
                                    paymentMode != null ? new ArrayList<>(Collections.singletonList(paymentMode)) : null);
                        }
                    }
                }
        );
    }

    private void setupDownloadLauncher() {
        downloadLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        long startDate = data.getLongExtra("startDate", 0);
                        long endDate = data.getLongExtra("endDate", 0);
                        String entryType = data.getStringExtra("entryType");
                        String paymentMode = data.getStringExtra("paymentMode");
                        String format = data.getStringExtra("format");

                        if (checkPermissions()) {
                            List<TransactionModel> filteredTransactions = allTransactions.stream()
                                    .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                                    .filter(t -> entryType == null || entryType.equals("All") || t.getType().equals(entryType))
                                    .filter(t -> paymentMode == null || paymentMode.equals("All") || t.getPaymentMode().equals(paymentMode))
                                    .collect(Collectors.toList());

                            if ("PDF".equalsIgnoreCase(format)) {
                                // [FIX] Pass the real currentCashbookName instead of hardcoded string
                                PdfReportGenerator.generateReport(this, filteredTransactions, currentCashbookName, startDate, endDate);
                            } else {
                                showToast("Excel format coming soon!");
                            }
                        } else {
                            requestPermissions();
                        }
                    }
                }
        );
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Storage permission granted. Please try again.");
            } else {
                showToast("Storage permission denied.");
            }
        }
    }

    private void showDeleteConfirmation(TransactionModel transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to permanently delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (viewModel != null) {
                        viewModel.deleteTransaction(transaction.getTransactionId());
                        showToast("Transaction deleted");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void duplicateTransaction(TransactionModel transaction) {
        if (viewModel == null) return;

        TransactionModel newTransaction = new TransactionModel();
        newTransaction.setAmount(transaction.getAmount());
        newTransaction.setType(transaction.getType());
        newTransaction.setTransactionCategory(transaction.getTransactionCategory());
        newTransaction.setPaymentMode(transaction.getPaymentMode());
        newTransaction.setPartyName(transaction.getPartyName());
        newTransaction.setRemark("Copy of: " + transaction.getRemark());
        newTransaction.setTimestamp(System.currentTimeMillis());

        viewModel.addTransaction(newTransaction);
        showToast("Transaction duplicated");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TransactionActivity destroyed");
    }
}