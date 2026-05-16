package com.phynix.artham.fragments;


import com.phynix.artham.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.phynix.artham.models.LegendData;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.CategoryColorUtil;
import com.phynix.artham.utils.CustomPieChartValueFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.phynix.artham.utils.ThemeUtil;
public class PieChartFragment extends Fragment {

    private static final String TAG = "PieChartFragment";
    private PieChart pieChart;
    private TextView toggleButton;
    private LinearLayout statsLayout;
    private TextView categoriesCountText;
    private TextView highestCategoryText;
    private List<TransactionModel> transactions;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SHOW_CHART = "show_pie_chart";

    public static PieChartFragment newInstance(ArrayList<TransactionModel> transactions) {
        PieChartFragment fragment = new PieChartFragment();
        Bundle args = new Bundle();
        args.putSerializable("transactions", transactions);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_pie_chart, container, false);

        pieChart = view.findViewById(R.id.pieChart);
        toggleButton = view.findViewById(R.id.togglePieChartButton);
        statsLayout = view.findViewById(R.id.statsLayout);
        categoriesCountText = view.findViewById(R.id.categoriesCount);
        highestCategoryText = view.findViewById(R.id.highestCategory);

        if (getArguments() != null) {
            try {
                transactions = (List<TransactionModel>) getArguments().getSerializable("transactions");
            } catch (Exception e) {
                transactions = new ArrayList<>();
            }
        } else {
            transactions = new ArrayList<>();
        }

        setupPieChart();
        loadPieChartData();
        setupToggleLogic();
        return view;
    }

    private void setupToggleLogic() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isVisible = prefs.getBoolean(KEY_SHOW_CHART, true);
        updateChartVisibility(isVisible);
        toggleButton.setOnClickListener(v -> {
            boolean newVisibility = (pieChart.getVisibility() != View.VISIBLE);
            updateChartVisibility(newVisibility);
            prefs.edit().putBoolean(KEY_SHOW_CHART, newVisibility).apply();
        });
    }

    private void updateChartVisibility(boolean show) {
        if (show) {
            pieChart.setVisibility(View.VISIBLE);
            if (statsLayout != null) statsLayout.setVisibility(View.VISIBLE);
            toggleButton.setText("Hide Pie Chart");
        } else {
            pieChart.setVisibility(View.GONE);
            if (statsLayout != null) statsLayout.setVisibility(View.GONE);
            toggleButton.setText("Show Pie Chart");
        }
    }

    private void setupPieChart() {
        if (getContext() == null) return;
        int textColor = ThemeUtil.getThemeAttrColor(getContext(), android.R.attr.textColorPrimary);

        pieChart.setDrawHoleEnabled(true);
        pieChart.setUsePercentValues(true);
        pieChart.setEntryLabelTextSize(11f);
        pieChart.setEntryLabelColor(textColor);
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setCenterText("Expenses");
        pieChart.setCenterTextSize(18f);
        pieChart.setCenterTextColor(textColor);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawEntryLabels(true);
        pieChart.setExtraOffsets(30.f, 10.f, 30.f, 10.f);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.getLegend().setEnabled(false);
    }

    private void loadPieChartData() {
        if (getContext() == null) return;
        int secondaryTextColor = ThemeUtil.getThemeAttrColor(getContext(), android.R.attr.textColorSecondary);

        Map<String, Double> categoryTotals = new HashMap<>();
        double totalExpense = 0;

        if (transactions != null && !transactions.isEmpty()) {
            for (TransactionModel transaction : transactions) {
                if ("OUT".equalsIgnoreCase(transaction.getType())) {
                    String category = transaction.getTransactionCategory();
                    if (category == null || category.isEmpty()) category = "Other";
                    double amount = transaction.getAmount();
                    categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amount);
                    totalExpense += amount;
                }
            }
        }

        // Update stats summary UI
        if (categoriesCountText != null) {
            categoriesCountText.setText(String.valueOf(categoryTotals.size()));
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> exactSliceColors = new ArrayList<>();
        double otherTotal = 0;
        double threshold = totalExpense * 0.03;

        String highestCat = "-";
        double highestAmount = 0;

        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            String catName = entry.getKey();
            double catAmount = entry.getValue();

            // Find highest expense
            if (catAmount > highestAmount) {
                highestAmount = catAmount;
                highestCat = catName;
            }

            if (catAmount < threshold) {
                otherTotal += catAmount;
            } else {
                entries.add(new PieEntry((float) catAmount, catName));
                // EXACT COLOR MAPPING: Pie Chart slice matches the Category color perfectly
                exactSliceColors.add(CategoryColorUtil.getCategoryColor(getContext(), catName));
            }
        }

        if (highestCategoryText != null) {
            highestCategoryText.setText(highestCat);
        }

        if (otherTotal > 0) {
            entries.add(new PieEntry((float) otherTotal, "Other"));
            exactSliceColors.add(CategoryColorUtil.getCategoryColor(getContext(), "Other"));
        }

        // Sort entries by value descending to make the pie chart look clean
        // Note: We need to sort colors along with entries to keep them mapped properly!
        for (int i = 0; i < entries.size() - 1; i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                if (entries.get(i).getValue() < entries.get(j).getValue()) {
                    Collections.swap(entries, i, j);
                    Collections.swap(exactSliceColors, i, j); // Swap colors to match
                }
            }
        }

        PieDataSet dataSet;
        if (entries.isEmpty()) {
            entries.add(new PieEntry(100f, "No Data"));
            dataSet = new PieDataSet(entries, "");
            dataSet.setColors(Color.LTGRAY);
            dataSet.setDrawValues(false);
        } else {
            dataSet = new PieDataSet(entries, "");
            dataSet.setColors(exactSliceColors); // Apply EXACT calculated colors
            dataSet.setSliceSpace(3f);
            dataSet.setSelectionShift(5f);

            dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
            dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
            dataSet.setValueLinePart1OffsetPercentage(80.f);
            dataSet.setValueLinePart1Length(0.5f);
            dataSet.setValueLinePart2Length(0.4f);
            dataSet.setValueLineColor(secondaryTextColor);
            dataSet.setValueLineWidth(1.5f);
            dataSet.setDrawValues(true);
        }

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new CustomPieChartValueFormatter());
        data.setValueTextSize(0f);
        data.setValueTextColor(Color.TRANSPARENT);

        pieChart.setData(data);
        pieChart.highlightValues(null);
        pieChart.invalidate();
        pieChart.animateY(1400, Easing.EaseInOutQuad);
    }

    /**
     * Helper method to generate exactly matching LegendData for the Parent Activity's LegendAdapter
     */
    public List<LegendData> generateLegendData() {
        List<LegendData> legendList = new ArrayList<>();
        Map<String, Double> categoryTotals = new HashMap<>();
        double totalExpense = 0;

        if (transactions == null) return legendList;

        for (TransactionModel transaction : transactions) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory();
                if (category == null || category.isEmpty()) category = "Other";
                double amount = transaction.getAmount();
                categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amount);
                totalExpense += amount;
            }
        }

        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            String catName = entry.getKey();
            double amount = entry.getValue();
            float percentage = (float) (amount / totalExpense);

            // Generate matching colors and icons
            int color = CategoryColorUtil.getCategoryColor(getContext(), catName);
            int iconResId = CategoryColorUtil.getCategoryIcon(catName);

            legendList.add(new LegendData(catName, amount, percentage, color, iconResId));
        }

        // Sort highest expense first
        Collections.sort(legendList, (l1, l2) -> Double.compare(l2.getAmount(), l1.getAmount()));
        return legendList;
    }

    public void updateData(ArrayList<TransactionModel> newTransactions) {
        this.transactions = newTransactions;
        if (pieChart != null && getContext() != null) {
            loadPieChartData();
        }
    }
}