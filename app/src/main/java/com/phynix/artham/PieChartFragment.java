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

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.CustomPieChartValueFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PieChartFragment extends Fragment {

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

        // Configuration for the Entry Label (e.g. "Food")
        pieChart.setEntryLabelTextSize(11f);
        pieChart.setEntryLabelColor(textColor);

        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setHoleColor(Color.TRANSPARENT);

        pieChart.setCenterText("Expenses");
        pieChart.setCenterTextSize(18f);
        pieChart.setCenterTextColor(textColor);
        pieChart.getDescription().setEnabled(false);

        // [IMPORTANT] Enable Entry Labels so "Food", "Travel" etc. are drawn
        pieChart.setDrawEntryLabels(true);

        // Increase offsets to prevent clipping of outside labels
        pieChart.setExtraOffsets(30.f, 10.f, 30.f, 10.f);

        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.getLegend().setEnabled(false);
    }

    private void loadPieChartData() {
        if (getContext() == null) return;
        int textColor = ThemeUtil.getThemeAttrColor(getContext(), android.R.attr.textColorPrimary);
        int secondaryTextColor = ThemeUtil.getThemeAttrColor(getContext(), android.R.attr.textColorSecondary);

        // 1. Calculate raw totals
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

        // 2. Group small slices into "Others" (Threshold: < 3%)
        ArrayList<PieEntry> entries = new ArrayList<>();
        double otherTotal = 0;
        double threshold = totalExpense * 0.03;

        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            if (entry.getValue() < threshold) {
                otherTotal += entry.getValue();
            } else {
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            }
        }

        if (otherTotal > 0) {
            entries.add(new PieEntry((float) otherTotal, "Others"));
        }

        // Sort descending
        Collections.sort(entries, (e1, e2) -> Float.compare(e2.getValue(), e1.getValue()));

        PieDataSet dataSet;
        if (entries.isEmpty()) {
            entries.add(new PieEntry(100f, "No Data"));
            dataSet = new PieDataSet(entries, "");
            dataSet.setColors(Color.LTGRAY);
            dataSet.setDrawValues(false);
        } else {
            dataSet = new PieDataSet(entries, "");

            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(Color.parseColor("#FF5252")); // Red
            colors.add(Color.parseColor("#448AFF")); // Blue
            colors.add(Color.parseColor("#69F0AE")); // Green
            colors.add(Color.parseColor("#FFD740")); // Yellow
            colors.add(Color.parseColor("#E040FB")); // Purple
            colors.add(Color.parseColor("#18FFFF")); // Cyan
            colors.add(Color.parseColor("#FFAB40")); // Orange
            colors.add(Color.parseColor("#FF4081")); // Pink
            colors.add(Color.parseColor("#9E9E9E")); // Grey
            dataSet.setColors(colors);

            dataSet.setSliceSpace(3f);
            dataSet.setSelectionShift(5f);

            // --- Configure OUTSIDE Labels ---
            // Move Category Name ("Food") Outside
            dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

            // Move Value Position Outside (Necessary for the line to be drawn to it)
            dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

            // Line configuration
            dataSet.setValueLinePart1OffsetPercentage(80.f);
            dataSet.setValueLinePart1Length(0.5f);
            dataSet.setValueLinePart2Length(0.4f);
            dataSet.setValueLineColor(secondaryTextColor);
            dataSet.setValueLineWidth(1.5f);

            // [IMPORTANT] Draw Values MUST be true for the lines to appear.
            // The text itself is hidden by the CustomPieChartValueFormatter below.
            dataSet.setDrawValues(true);
        }

        PieData data = new PieData(dataSet);

        // Use the updated custom formatter which returns ""
        data.setValueFormatter(new CustomPieChartValueFormatter());

        // Also set text size to 0 and color to transparent as a backup
        data.setValueTextSize(0f);
        data.setValueTextColor(Color.TRANSPARENT);

        pieChart.setData(data);
        pieChart.highlightValues(null);
        pieChart.invalidate();
        pieChart.animateY(1400, Easing.EaseInOutQuad);
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