package com.phynix.artham;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.view.MotionEvent;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.phynix.artham.adapters.TransactionAdapter;
import com.phynix.artham.databinding.ActivityTransactionBinding;
import com.phynix.artham.databinding.LayoutBottomNavigationBinding;
import com.phynix.artham.databinding.LayoutPieChartBinding;
import com.phynix.artham.databinding.LayoutSearchBarBinding;
import com.phynix.artham.databinding.LayoutSummaryCardsBinding;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.SwipeListener;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.TransactionViewModel;
import com.phynix.artham.viewmodels.TransactionViewModelFactory;
import com.phynix.artham.utils.PdfReportGenerator;
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

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class TransactionActivity extends AppCompatActivity {

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

    private View skeletonLayout; // Reference for the skeleton layout wrapper

    private TransactionItemFragment transactionFragment;
    private TransactionViewModel viewModel;

    private FirebaseAuth mAuth;
    private String currentCashbookId;
    private String currentCashbookName = "Artham Cashbook";
    private FirebaseUser currentUser;
    private SwipeListener swipeListener;

    private boolean isShowingAllTransactions = false;

    private ActivityResultLauncher<Intent> filterLauncher;
    private ActivityResultLauncher<Intent> downloadLauncher;

    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String action = result.getData().getStringExtra("action");
                    if ("delete".equals(action)) {
                        String txId = result.getData().getStringExtra("transaction_id");
                        if (txId != null && viewModel != null) {
                            viewModel.deleteTransaction(txId);
                            showSnackbar("Transaction deleted");
                        }
                    } else if ("duplicate".equals(action)) {
                        TransactionModel tx = (TransactionModel) result.getData().getSerializableExtra("transaction");
                        if (tx != null) duplicateTransaction(tx);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivityTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        currentMonthCalendar = Calendar.getInstance();

        if (currentCashbookId == null || currentUser == null) {
            showSnackbar("Error: No active cashbook found.");
            finish();
            return;
        }

        fetchCashbookName();
        initializeUI();
        initViewModel();
        setupTransactionFragment();
        setupClickListeners();
        setupBottomNavigation();
        setupLaunchers();
        observeViewModel();
        applySavedChartVisibility();
        setupSwipeNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUser != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedId = prefs.getString("active_cashbook_id_" + currentUser.getUid(), currentCashbookId);
            if (savedId != null && !savedId.equals(currentCashbookId)) {
                currentCashbookId = savedId;
                fetchCashbookName();
                if (viewModel != null) {
                    viewModel.setCashbookId(currentCashbookId);
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String newId = intent.getStringExtra("cashbook_id");
        if (newId == null) {
            newId = intent.getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        }
        if (newId != null && !newId.equals(currentCashbookId)) {
            currentCashbookId = newId;
            fetchCashbookName();
            if (viewModel != null) {
                viewModel.setCashbookId(currentCashbookId);
            }
        }
    }

    private void setupSwipeNavigation() {
        swipeListener = new SwipeListener(this) {
            @Override
            public void onSwipeLeft() {
                Intent intent = new Intent(TransactionActivity.this, SettingsActivity.class);
                intent.putExtra("cashbook_id", currentCashbookId);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                finish();
            }

            @Override
            public void onSwipeRight() {
                Intent intent = new Intent(TransactionActivity.this, HomePage.class);
                intent.putExtra("cashbook_id", currentCashbookId);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                finish();
            }
        };
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (swipeListener != null) swipeListener.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private void initializeUI() {
        summaryBinding = LayoutSummaryCardsBinding.bind(binding.summaryCardsLayout.getRoot());
        pieChartBinding = LayoutPieChartBinding.bind(binding.pieChartLayout.getRoot());
        searchBinding = LayoutSearchBarBinding.bind(binding.searchBarLayout.getRoot());
        bottomNavBinding = LayoutBottomNavigationBinding.bind(binding.bottomNavCard.getRoot());

        binding.swipeRefreshLayout.setColorSchemeColors(getThemeColor(R.attr.chk_primary_blue));

        // Initialize Skeleton View reference
        skeletonLayout = findViewById(R.id.skeletonLayout);

        // Pie Chart Configuration
        pieChartBinding.pieChart.setUsePercentValues(true);
        pieChartBinding.pieChart.getDescription().setEnabled(false);
        pieChartBinding.pieChart.getLegend().setEnabled(false);
        pieChartBinding.pieChart.setDrawEntryLabels(true);
        pieChartBinding.pieChart.setExtraOffsets(30.f, 10.f, 30.f, 10.f);
        pieChartBinding.pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChartBinding.pieChart.setDrawHoleEnabled(true);
        pieChartBinding.pieChart.setHoleColor(Color.TRANSPARENT);
        pieChartBinding.pieChart.setTransparentCircleRadius(61f);
        pieChartBinding.pieChart.setHoleRadius(58f);

        int textColor = getThemeColor(android.R.attr.textColorPrimary);
        pieChartBinding.pieChart.setNoDataTextColor(textColor);
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
            binding.swipeRefreshLayout.setRefreshing(false);
            toggleSkeletonLoading(false); // Data loaded, hide skeleton
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            toggleSkeletonLoading(isLoading); // Show/hide skeleton based on loading state
            if (transactionFragment != null) transactionFragment.showLoading(isLoading);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                showSnackbar(error);
                viewModel.clearError();
                binding.swipeRefreshLayout.setRefreshing(false);
                toggleSkeletonLoading(false); // Error occurred, hide skeleton
            }
        });
    }

    private void toggleSkeletonLoading(boolean show) {
        if (skeletonLayout != null) {
            skeletonLayout.setVisibility(show ? View.VISIBLE : View.GONE);

            // Handle Shimmer Animation securely
            if (skeletonLayout instanceof ShimmerFrameLayout) {
                if (show) {
                    ((ShimmerFrameLayout) skeletonLayout).startShimmer();
                } else {
                    ((ShimmerFrameLayout) skeletonLayout).stopShimmer();
                }
            }
        }

        // Hide primary content containers when loading
        int contentVisibility = show ? View.GONE : View.VISIBLE;
        binding.swipeRefreshLayout.setVisibility(contentVisibility);

        if (binding.fixedSearchBarContainer != null) {
            binding.fixedSearchBarContainer.setVisibility(contentVisibility);
        }
    }

    private void displayDataForCurrentMonth() {
        if (allTransactions == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        pieChartBinding.monthTitle.setText(isShowingAllTransactions ? "All Time" : sdf.format(currentMonthCalendar.getTime()));

        List<TransactionModel> transactionsToDisplay;

        if (isShowingAllTransactions) {
            transactionsToDisplay = new ArrayList<>(allTransactions);
            binding.allTransactionsButton.setText("Monthly");
        } else {
            transactionsToDisplay = allTransactions.stream()
                    .filter(t -> {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(t.getTimestamp());
                        return cal.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                                cal.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH);
                    }).collect(Collectors.toList());
            binding.allTransactionsButton.setText("All");
        }

        binding.transactionCountText.setText("(" + formatCount(transactionsToDisplay.size()) + ")");

        updateTotals(transactionsToDisplay);
        setupStyledPieChart(transactionsToDisplay);

        if (transactionFragment != null) {
            transactionFragment.updateTransactions(transactionsToDisplay);
        }
    }

    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) {
            if (count % 1000 == 0) return (count / 1000) + "k";
            return String.format(Locale.US, "%.1fk", count / 1000.0);
        }
        return String.format(Locale.US, "%.1fM", count / 1000000.0);
    }

    private void setupStyledPieChart(List<TransactionModel> transactionsForMonth) {
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalExpense = 0f;
        String highestCategory = "-";
        float maxExpense = 0f;

        for (TransactionModel transaction : transactionsForMonth) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory() != null ? transaction.getTransactionCategory() : "Other";
                float amount = (float) transaction.getAmount();
                expenseByCategory.put(category, expenseByCategory.getOrDefault(category, 0f) + amount);
                totalExpense += amount;
                if (expenseByCategory.get(category) > maxExpense) {
                    maxExpense = expenseByCategory.get(category);
                    highestCategory = category;
                }
            }
        }

        pieChartBinding.categoriesCount.setText(String.valueOf(expenseByCategory.size()));
        pieChartBinding.highestCategory.setText(highestCategory);

        int textColor = getThemeColor(android.R.attr.textColorPrimary);

        if (totalExpense == 0) {
            pieChartBinding.pieChart.clear();
            pieChartBinding.pieChart.setCenterText("No Expenses");
            pieChartBinding.pieChart.setCenterTextColor(textColor);
            pieChartBinding.pieChart.setCenterTextSize(14f);
            pieChartBinding.pieChart.invalidate();
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            String label = entry.getKey();
            if (label.length() > 5) label = label.substring(0, 5) + "..";
            entries.add(new PieEntry(entry.getValue(), label));
        }

        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#EF5350")); colors.add(Color.parseColor("#448AFF"));
        colors.add(Color.parseColor("#69F0AE")); colors.add(Color.parseColor("#FFCA28"));
        colors.add(Color.parseColor("#AB47BC")); colors.add(Color.parseColor("#26C6DA"));
        colors.add(Color.parseColor("#FF7043")); colors.add(Color.parseColor("#8D6E63"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(5f);
        dataSet.setDrawValues(true);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.5f);
        dataSet.setValueLineColor(textColor);
        dataSet.setValueTextColor(textColor);
        dataSet.setValueTextSize(10f);

        pieChartBinding.pieChart.setEntryLabelColor(textColor);
        pieChartBinding.pieChart.setEntryLabelTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) { return ""; }
        });

        PieData data = new PieData(dataSet);
        pieChartBinding.pieChart.setData(data);
        pieChartBinding.pieChart.setCenterText("Total\n" + AmountFormatter.formatCompact(totalExpense));
        pieChartBinding.pieChart.setCenterTextSize(16f);
        pieChartBinding.pieChart.setCenterTextColor(textColor);
        pieChartBinding.pieChart.animateY(1000, Easing.EaseInOutQuad);
        pieChartBinding.pieChart.invalidate();
    }

    @SuppressLint("SetTextI18n")
    private void updateTotals(List<TransactionModel> transactions) {
        double totalIncome = 0, totalExpense = 0;
        for (TransactionModel t : transactions) {
            if ("IN".equalsIgnoreCase(t.getType())) totalIncome += t.getAmount();
            else totalExpense += t.getAmount();
        }
        summaryBinding.incomeText.setText(AmountFormatter.formatCompactSpannable(totalIncome));
        summaryBinding.expenseText.setText(AmountFormatter.formatCompactSpannable(totalExpense));
        summaryBinding.balanceText.setText(AmountFormatter.formatCompactSpannable(totalIncome - totalExpense));
    }

    private void fetchCashbookName() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference()
                .child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("name");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) currentCashbookName = snapshot.getValue(String.class);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) return typedValue.data;
        return Color.BLACK;
    }

    private void setupLaunchers() {
        setupFilterLauncher();
        setupDownloadLauncher();
    }

    private void setupFilterLauncher() {
        searchBinding.clearSearchButton.setOnClickListener(v -> searchBinding.searchEditText.setText(""));
        searchBinding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchBinding.clearSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if(viewModel!=null) viewModel.filter(s.toString(), 0, 0, "All", null, null);
            }
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
                    exportReport(data.getLongExtra("startDate", 0), data.getLongExtra("endDate", 0),
                            data.getStringExtra("entryType"), data.getStringExtra("paymentMode"));
                } else {
                    requestPermissions();
                }
            }
        });
    }

    private void exportReport(long startDate, long endDate, String entryType, String paymentMode) {
        if (allTransactions.isEmpty()) { showSnackbar("No data"); return; }
        List<TransactionModel> exportList = allTransactions.stream()
                .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                .filter(t -> entryType == null || entryType.equals("All") || t.getType().equalsIgnoreCase(entryType))
                .filter(t -> paymentMode == null || paymentMode.equals("All") || t.getPaymentMode().equalsIgnoreCase(paymentMode))
                .collect(Collectors.toList());

        if (exportList.isEmpty()) { showSnackbar("No matching transactions"); return; }
        PdfReportGenerator.generateReport(this, exportList, currentCashbookName, startDate, endDate);
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

    private void setupClickListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            if (viewModel != null) viewModel.filter(searchBinding.searchEditText.getText().toString(), 0, 0, "All", null, null);
            else binding.swipeRefreshLayout.setRefreshing(false);
        });

        binding.allTransactionsButton.setOnClickListener(v -> {
            isShowingAllTransactions = !isShowingAllTransactions;
            displayDataForCurrentMonth();
        });

        pieChartBinding.pieChartHeader.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseAnalyticsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
        });

        pieChartBinding.monthBackwardButton.setOnClickListener(v -> {
            isShowingAllTransactions = false;
            currentMonthCalendar.add(Calendar.MONTH, -1);
            displayDataForCurrentMonth();
        });

        pieChartBinding.monthForwardButton.setOnClickListener(v -> {
            isShowingAllTransactions = false;
            currentMonthCalendar.add(Calendar.MONTH, 1);
            displayDataForCurrentMonth();
        });

        pieChartBinding.togglePieChartButton.setOnClickListener(v -> {
            boolean visible = pieChartBinding.pieChart.getVisibility() == View.VISIBLE;
            setChartVisibility(!visible);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_SHOW_CHART, !visible).apply();
        });

        binding.downloadReportButton.setOnClickListener(v -> {
            if(allTransactions.isEmpty()){ showSnackbar("No data"); return; }
            downloadLauncher.launch(new Intent(this, DownloadOptionsActivity.class));
        });
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
                currentCashbookId = newId; currentCashbookName = newName;
                showSnackbar("Switched to: " + newName);
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("active_cashbook_id_" + currentUser.getUid(), newId).apply();

                if (viewModel != null) {
                    viewModel.setCashbookId(currentCashbookId);
                }
            }
        }
    }

    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(new TransactionAdapter.OnItemClickListener() {
            @Override public void onItemClick(TransactionModel t) {
                Intent i = new Intent(TransactionActivity.this, TransactionDetailsActivity.class);
                i.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, t);
                i.putExtra("cashbook_id", currentCashbookId);
                detailsLauncher.launch(i);
            }
            @Override public void onEditClick(TransactionModel t) { openEditActivity(t); }
            @Override public void onDeleteClick(TransactionModel t) { showDeleteConfirmation(t); }
            @Override public void onCopyClick(TransactionModel t) { duplicateTransaction(t); }
        });
        getSupportFragmentManager().beginTransaction().replace(R.id.transaction_fragment_container, transactionFragment).commit();
    }

    private void openEditActivity(TransactionModel transaction) {
        Intent intent = new Intent(this, EditTransactionActivity.class);
        intent.putExtra("transaction_model", (Serializable) transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void showDeleteConfirmation(TransactionModel transaction) {
        new AlertDialog.Builder(this).setTitle("Delete").setMessage("Are you sure?")
                .setPositiveButton("Delete", (d, w) -> { if(viewModel!=null) viewModel.deleteTransaction(transaction.getTransactionId()); })
                .setNegativeButton("Cancel", null).show();
    }

    private void duplicateTransaction(TransactionModel transaction) {
        TransactionModel newT = new TransactionModel();
        newT.setAmount(transaction.getAmount()); newT.setType(transaction.getType());
        newT.setTransactionCategory(transaction.getTransactionCategory()); newT.setPaymentMode(transaction.getPaymentMode());
        newT.setPartyName(transaction.getPartyName()); newT.setRemark(transaction.getRemark() + " (Copy)");
        newT.setTimestamp(System.currentTimeMillis()); newT.setTags(transaction.getTags());
        viewModel.addTransaction(newT); showSnackbar("Duplicated");
    }

    private void applySavedChartVisibility() {
        boolean show = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_SHOW_CHART, true);
        setChartVisibility(show);
    }

    private void setChartVisibility(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        pieChartBinding.pieChart.setVisibility(visibility);
        View stats = pieChartBinding.getRoot().findViewById(R.id.statsLayout);
        if (stats != null) stats.setVisibility(visibility);
        pieChartBinding.togglePieChartButton.setText(show ? "Hide Pie Chart" : "Show Pie Chart");
    }

    private void showSnackbar(String msg) {
        View anchor = (bottomNavBinding != null) ? bottomNavBinding.getRoot() : null;
        SnackbarHelper.show(this, msg, anchor);
    }
}