package com.phynix.artham;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.phynix.artham.databinding.ActivityTransactionBinding;
import com.phynix.artham.databinding.LayoutBottomNavigationBinding;
import com.phynix.artham.databinding.LayoutPieChartBinding;
import com.phynix.artham.databinding.LayoutSearchBarBinding;
import com.phynix.artham.databinding.LayoutSummaryCardsBinding;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.viewmodels.TransactionViewModel;
import com.phynix.artham.viewmodels.TransactionViewModelFactory;
import com.phynix.artham.utils.PdfReportGenerator; // Import Generator
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.dialogs.TransactionDetailsDialog;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TransactionActivity extends AppCompatActivity implements TransactionDetailsDialog.TransactionDialogListener {

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
    private String currentCashbookName = "My Cashbook"; // Default Name
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
        // Try to retrieve name from intent if passed, else pref, else default
        String nameFromIntent = getIntent().getStringExtra("cashbook_name");
        if (nameFromIntent != null) currentCashbookName = nameFromIntent;

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        currentMonthCalendar = Calendar.getInstance();

        if (currentCashbookId == null || currentUser == null) {
            showToast("Error: No active cashbook found.");
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

    // ... (UI Init, ViewModel Init, Observer methods unchanged) ...
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
        transactionFragment.setOnItemClickListener(new com.phynix.artham.adapters.TransactionAdapter.OnItemClickListener() {
            @Override public void onItemClick(TransactionModel transaction) {
                TransactionDetailsDialog.newInstance(transaction).show(getSupportFragmentManager(), "Details");
            }
            @Override public void onEditClick(TransactionModel transaction) { openEditActivity(transaction); }
            @Override public void onDeleteClick(TransactionModel transaction) { showDeleteConfirmation(transaction); }
            @Override public void onCopyClick(TransactionModel transaction) { duplicateTransaction(transaction); }
        });
        getSupportFragmentManager().beginTransaction().replace(R.id.transaction_fragment_container, transactionFragment).commit();
    }

    // ... (Edit, Delete, Duplicate logic unchanged) ...
    @Override public void onEditTransaction(TransactionModel transaction) { openEditActivity(transaction); }
    @Override public void onDeleteTransaction(TransactionModel transaction) { showDeleteConfirmation(transaction); }

    private void openEditActivity(TransactionModel transaction) {
        Intent intent = new Intent(this, EditTransactionActivity.class);
        intent.putExtra("transaction_model", (Serializable) transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void showDeleteConfirmation(TransactionModel transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete").setMessage("Delete transaction?")
                .setPositiveButton("Delete", (d, w) -> { if (viewModel != null) viewModel.deleteTransaction(transaction.getTransactionId()); })
                .setNegativeButton("Cancel", null).show();
    }

    private void duplicateTransaction(TransactionModel transaction) {
        TransactionModel newT = new TransactionModel();
        newT.setAmount(transaction.getAmount());
        newT.setType(transaction.getType());
        newT.setTransactionCategory(transaction.getTransactionCategory());
        newT.setPaymentMode(transaction.getPaymentMode());
        newT.setPartyName(transaction.getPartyName());
        newT.setRemark(transaction.getRemark() + " (Copy)");
        newT.setTimestamp(System.currentTimeMillis());
        viewModel.addTransaction(newT);
        showToast("Duplicated");
    }

    // ... (Navigation) ...
    private void setupBottomNavigation() {
        bottomNavBinding.btnTransactions.setSelected(true);
        bottomNavBinding.btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent); finish();
        });
        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());
        bottomNavBinding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent); finish();
        });
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
            String newId = data.getStringExtra("selected_cashbook_id");
            String newName = data.getStringExtra("cashbook_name");
            if (newId != null && !newId.equals(currentCashbookId)) {
                switchCashbook(newId, newName);
            }
        }
    }

    private void switchCashbook(String newId, String newName) {
        currentCashbookId = newId;
        currentCashbookName = newName; // Update Name
        showToast("Switched to: " + newName);
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().putString("active_cashbook_id_" + currentUser.getUid(), newId).apply();
        initViewModel();
        observeViewModel();
    }

    // Launchers
    private void setupLaunchers() {
        setupFilterLauncher();
        setupDownloadLauncher();
    }

    private void setupFilterLauncher() {
        searchBinding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if(viewModel!=null) viewModel.filter(s.toString(), 0,0,"All",null,null); }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchBinding.filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            filterLauncher.launch(intent);
        });
        filterLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                searchBinding.searchEditText.setText(data.getStringExtra("searchQuery"));
                if(viewModel!=null) viewModel.filter(
                        data.getStringExtra("searchQuery"),
                        data.getLongExtra("startDate", 0),
                        data.getLongExtra("endDate", 0),
                        data.getStringExtra("entryType"),
                        data.getStringArrayListExtra("categories"),
                        null
                );
            }
        });
    }

    private void setupDownloadLauncher() {
        downloadLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                if (checkPermissions()) {
                    exportTransactionsToPdf(
                            data.getLongExtra("startDate", 0),
                            data.getLongExtra("endDate", 0),
                            data.getStringExtra("entryType"),
                            data.getStringExtra("paymentMode")
                    );
                } else {
                    requestPermissions();
                }
            }
        });
    }

    // Export Logic
    private void exportTransactionsToPdf(long startDate, long endDate, String entryType, String paymentMode) {
        if (allTransactions == null || allTransactions.isEmpty()) { showToast("No data"); return; }

        List<TransactionModel> exportList = allTransactions.stream()
                .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                .filter(t -> entryType == null || entryType.equals("All") || t.getType().equalsIgnoreCase(entryType))
                .filter(t -> paymentMode == null || paymentMode.equals("All") || t.getPaymentMode().equalsIgnoreCase(paymentMode))
                .collect(Collectors.toList());

        if (exportList.isEmpty()) { showToast("No matching transactions"); return; }

        // [UPDATED] Pass cashbookName
        PdfReportGenerator.generateReport(this, exportList, currentCashbookName, startDate, endDate);
    }

    // ... (Permissions, Chart Logic, Click Listeners - omitted for brevity but remain the same) ...

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
            boolean visible = pieChartBinding.pieChart.getVisibility() == View.VISIBLE;
            setChartVisibility(!visible);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_SHOW_CHART, !visible).apply();
        });
        binding.downloadReportButton.setOnClickListener(v -> {
            if(allTransactions.isEmpty()){ showToast("No data"); return; }
            downloadLauncher.launch(new Intent(this, DownloadOptionsActivity.class));
        });
    }

    private void displayDataForCurrentMonth() {
        if(allTransactions==null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        pieChartBinding.monthTitle.setText(sdf.format(currentMonthCalendar.getTime()));
        List<TransactionModel> monthList = allTransactions.stream().filter(t -> {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(t.getTimestamp());
            return c.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH);
        }).collect(Collectors.toList());
        updateTotals(monthList);
        if(transactionFragment!=null) transactionFragment.updateTransactions(monthList);
    }

    @SuppressLint("SetTextI18n")
    private void updateTotals(List<TransactionModel> list) {
        double in = list.stream().filter(t->"IN".equalsIgnoreCase(t.getType())).mapToDouble(TransactionModel::getAmount).sum();
        double out = list.stream().filter(t->!"IN".equalsIgnoreCase(t.getType())).mapToDouble(TransactionModel::getAmount).sum();
        summaryBinding.incomeText.setText("₹" + String.format(Locale.US, "%.2f", in));
        summaryBinding.expenseText.setText("₹" + String.format(Locale.US, "%.2f", out));
        summaryBinding.balanceText.setText("₹" + String.format(Locale.US, "%.2f", in - out));
    }

    private void applySavedChartVisibility() {
        boolean show = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_SHOW_CHART, true);
        setChartVisibility(show);
    }

    private void setChartVisibility(boolean show) {
        pieChartBinding.pieChart.setVisibility(show ? View.VISIBLE : View.GONE);
        pieChartBinding.togglePieChartButton.setText(show ? "Hide Pie Chart" : "Show Pie Chart");
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
        if(requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showToast("Permission granted");
    }

    private void showToast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}