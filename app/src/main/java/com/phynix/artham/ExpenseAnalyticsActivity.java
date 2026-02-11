package com.phynix.artham;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.phynix.artham.adapters.LegendAdapter;
import com.phynix.artham.adapters.MonthlyExpenseAdapter;
import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.LegendData;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.CategoryColorUtil;
import com.phynix.artham.utils.ThemeManager; // Import ThemeManager

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExpenseAnalyticsActivity extends AppCompatActivity implements MonthlyExpenseAdapter.OnMonthClickListener {

    private ImageButton closeButton;
    private ProgressBar loadingProgressBar;
    private TextView noDataTextView, totalExpenseValue;
    private LinearLayout contentLayout;
    private RecyclerView monthlyCardsRecyclerView, detailedLegendRecyclerView;
    private PieChart pieChart;

    private DataRepository repository;
    private List<TransactionModel> allExpenses = new ArrayList<>();
    private Map<String, List<TransactionModel>> groupedData = new LinkedHashMap<>();
    private List<String> monthKeys = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // [FIX] Apply Theme BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_analytics);

        initViews();
        setupChart();
        loadData();
    }

    private void initViews() {
        closeButton = findViewById(R.id.closeButton);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        noDataTextView = findViewById(R.id.noDataTextView);
        totalExpenseValue = findViewById(R.id.totalExpenseValue);
        contentLayout = findViewById(R.id.contentLayout);
        monthlyCardsRecyclerView = findViewById(R.id.monthlyCardsRecyclerView);
        detailedLegendRecyclerView = findViewById(R.id.detailedLegendRecyclerView);
        pieChart = findViewById(R.id.fullScreenPieChart);

        closeButton.setOnClickListener(v -> finish());

        detailedLegendRecyclerView.setNestedScrollingEnabled(false);
        detailedLegendRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        monthlyCardsRecyclerView.setLayoutManager(layoutManager);

        repository = DataRepository.getInstance(getApplication());
    }

    private void setupChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(40, 10, 40, 10);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setTransparentCircleRadius(61f);
        pieChart.setHoleRadius(50f);
        pieChart.setDrawCenterText(true);
        pieChart.setCenterText("Expenses");

        pieChart.setCenterTextColor(getThemeColor(R.attr.chk_textColorPrimary));

        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.animateY(1400, Easing.EaseInOutQuad);
        pieChart.getLegend().setEnabled(false);

        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelColor(getThemeColor(R.attr.chk_textColorPrimary));
        pieChart.setEntryLabelTextSize(11f);
    }

    private void loadData() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        noDataTextView.setVisibility(View.GONE);

        repository.getCashbooks(cashbooks -> {
            if (cashbooks != null && !cashbooks.isEmpty()) {
                String activeCashbookId = cashbooks.get(0).getCashbookId();
                fetchTransactions(activeCashbookId);
            } else {
                showNoData();
            }
        }, error -> {
            showNoData();
            Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchTransactions(String cashbookId) {
        repository.getAllTransactions(cashbookId, transactions -> {
            processTransactions(transactions);
        }, error -> showNoData());
    }

    private void processTransactions(List<TransactionModel> rawData) {
        allExpenses.clear();
        for (TransactionModel t : rawData) {
            if ("Expense".equalsIgnoreCase(t.getType()) || "OUT".equalsIgnoreCase(t.getType())) {
                allExpenses.add(t);
            }
        }

        if (allExpenses.isEmpty()) {
            showNoData();
            return;
        }

        groupExpensesByMonth();

        String currentMonthKey = new SimpleDateFormat("MMM yyyy", Locale.US).format(new Date()).toUpperCase();
        int selectedIndex = 0;
        if (monthKeys.contains(currentMonthKey)) {
            selectedIndex = monthKeys.indexOf(currentMonthKey);
        }

        MonthlyExpenseAdapter adapter = new MonthlyExpenseAdapter(this, monthKeys, selectedIndex, this);
        monthlyCardsRecyclerView.setAdapter(adapter);
        monthlyCardsRecyclerView.scrollToPosition(selectedIndex);

        if (!monthKeys.isEmpty()) {
            updateDashboardForMonth(monthKeys.get(selectedIndex));
        }

        loadingProgressBar.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
    }

    private void showNoData() {
        loadingProgressBar.setVisibility(View.GONE);
        noDataTextView.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
    }

    private void groupExpensesByMonth() {
        groupedData.clear();
        monthKeys.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.US);

        Collections.sort(allExpenses, (o1, o2) -> Long.compare(o2.getTimestamp(), o1.getTimestamp()));

        for (TransactionModel t : allExpenses) {
            String key = sdf.format(new Date(t.getTimestamp())).toUpperCase();
            if (!groupedData.containsKey(key)) {
                groupedData.put(key, new ArrayList<>());
                monthKeys.add(key);
            }
            groupedData.get(key).add(t);
        }
    }

    @Override
    public void onMonthClick(String monthKey) {
        updateDashboardForMonth(monthKey);
    }

    private void updateDashboardForMonth(String monthKey) {
        List<TransactionModel> monthTransactions = groupedData.get(monthKey);
        if (monthTransactions == null) return;

        double total = 0;
        Map<String, Double> categoryMap = new HashMap<>();

        for (TransactionModel t : monthTransactions) {
            total += t.getAmount();
            String catName = t.getTransactionCategory();
            if (catName == null) catName = "Other";
            categoryMap.put(catName, categoryMap.getOrDefault(catName, 0.0) + t.getAmount());
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        totalExpenseValue.setText(currencyFormat.format(total));

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        List<LegendData> legendList = new ArrayList<>();

        for (Map.Entry<String, Double> entry : categoryMap.entrySet()) {
            float amount = entry.getValue().floatValue();
            float percentage = (float) (amount / total);

            entries.add(new PieEntry(amount, entry.getKey()));
            int color = CategoryColorUtil.getCategoryColor(this, entry.getKey());
            colors.add(color);

            legendList.add(new LegendData(entry.getKey(), entry.getValue(), percentage, color));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(10f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.4f);

        int themeTextColor = getThemeColor(R.attr.chk_textColorPrimary);
        dataSet.setValueLineColor(themeTextColor);
        dataSet.setValueTextColor(themeTextColor);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f%%", value);
            }
        });
        data.setValueTextSize(11f);

        pieChart.setEntryLabelColor(themeTextColor);
        pieChart.setData(data);
        pieChart.invalidate();

        Collections.sort(legendList, (o1, o2) -> Double.compare(o2.getAmount(), o1.getAmount()));
        LegendAdapter legendAdapter = new LegendAdapter(legendList);
        detailedLegendRecyclerView.setAdapter(legendAdapter);
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.GRAY;
    }
}