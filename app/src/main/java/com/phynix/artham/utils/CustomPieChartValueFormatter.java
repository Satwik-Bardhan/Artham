package com.phynix.artham.utils;

import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DecimalFormat;

public class CustomPieChartValueFormatter extends ValueFormatter {

    private final DecimalFormat format;

    public CustomPieChartValueFormatter() {
        // Format to 1 decimal place, e.g. "24.5%"
        format = new DecimalFormat("###,###,##0.0");
    }

    @Override
    public String getPieLabel(float value, PieEntry pieEntry) {
        // [FIX] Return 2 lines:
        // Line 1: Percentage
        // Line 2: Category Name
        if (pieEntry != null) {
            return format.format(value) + "%\n" + pieEntry.getLabel();
        }
        return format.format(value) + "%";
    }

    @Override
    public String getFormattedValue(float value) {
        return format.format(value) + "%";
    }
}