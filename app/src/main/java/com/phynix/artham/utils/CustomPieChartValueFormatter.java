package com.phynix.artham.utils;

import com.github.mikephil.charting.formatter.ValueFormatter;

public class CustomPieChartValueFormatter extends ValueFormatter {

    @Override
    public String getFormattedValue(float value) {
        // Requirement #7: We return an empty string here to keep the pie chart slices clean.
        // The exact percentages and colors are displayed in the LegendAdapter instead.
        return "";
    }
}