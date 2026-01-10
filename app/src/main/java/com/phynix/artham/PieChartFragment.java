package com.phynix.artham;

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

import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.CustomPieChartValueFormatter;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.util.ArrayList;
import java.util.List;

public class PieChartFragment extends Fragment {
    // ... (Same variables) ...
    private static final String TAG = "PieChartFragment";
    private PieChart pieChart;
    private TextView toggleButton;
    private LinearLayout statsLayout;
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

    // ... (setupToggleLogic, updateChartVisibility remain same) ...
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
        int textColor = ThemeUtil.getThemeAttrColor(getContext(), R.attr.chk_textColorPrimary);

        pieChart.setDrawHoleEnabled(true);
        pieChart.setUsePercentValues(true);
        pieChart.setEntryLabelTextSize(11f);
        pieChart.setEntryLabelColor(textColor);

        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setHoleColor(Color.TRANSPARENT);

        pieChart.setCenterText("Expenses");
        pieChart.setCenterTextSize(22f);
        pieChart.setCenterTextColor(textColor);
        pieChart.getDescription().setEnabled(false);

        // [FIX] Explicitly Disable Labels
        pieChart.setDrawEntryLabels(false);

        pieChart.setExtraOffsets(40.f, 10.f, 40.f, 10.f);

        Legend legend = pieChart.getLegend();
        legend.setEnabled(false);
    }

    private void loadPieChartData() {
        if (getContext() == null) return;
        int textColor = ThemeUtil.getThemeAttrColor(getContext(), R.attr.chk_textColorPrimary);
        int secondaryTextColor = ThemeUtil.getThemeAttrColor(getContext(), R.attr.chk_textColorSecondary);

        ArrayList<PieEntry> entries = new ArrayList<>();
        if (transactions != null && !transactions.isEmpty()) {
            java.util.Map<String, Double> categoryTotals = new java.util.HashMap<>();
            for (TransactionModel transaction : transactions) {
                if ("OUT".equalsIgnoreCase(transaction.getType())) {
                    String category = transaction.getTransactionCategory();
                    if (category == null || category.isEmpty()) category = "Other";
                    double amount = transaction.getAmount();
                    categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amount);
                }
            }
            for (java.util.Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            }
        }

        PieDataSet dataSet;
        if (entries.isEmpty()) {
            entries.add(new PieEntry(100f, "No Data"));
            dataSet = new PieDataSet(entries, "");
            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(ThemeUtil.getThemeAttrColor(getContext(), android.R.attr.dividerHorizontal));
            dataSet.setColors(colors);
        } else {
            dataSet = new PieDataSet(entries, "Expense Categories");
            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(Color.parseColor("#FF5252"));
            colors.add(Color.parseColor("#448AFF"));
            colors.add(Color.parseColor("#69F0AE"));
            colors.add(Color.parseColor("#FFD740"));
            colors.add(Color.parseColor("#E040FB"));
            colors.add(Color.parseColor("#18FFFF"));
            dataSet.setColors(colors);

            dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
            dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

            // [FIX] Increase spacing
            dataSet.setValueLinePart1OffsetPercentage(85.f);
            dataSet.setValueLinePart1Length(0.6f);
            dataSet.setValueLinePart2Length(0.5f);

            dataSet.setValueLineColor(secondaryTextColor);
            dataSet.setValueLineWidth(1f);
        }

        PieData data = new PieData(dataSet);
        data.setDrawValues(true);
        data.setValueTextSize(11f);
        data.setValueTextColor(textColor);

        // [FIX] Use Custom Formatter
        try {
            data.setValueFormatter(new CustomPieChartValueFormatter());
        } catch (Exception e) {
            data.setValueFormatter(new PercentFormatter(pieChart));
        }

        pieChart.setData(data);
        pieChart.invalidate();
        pieChart.animateY(1000);
    }

    public void updateData(ArrayList<TransactionModel> newTransactions) {
        this.transactions = newTransactions;
        if (pieChart != null && getContext() != null) {
            loadPieChartData();
        }
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK;
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}