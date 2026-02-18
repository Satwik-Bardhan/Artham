package com.phynix.artham.utils;

import com.github.mikephil.charting.formatter.ValueFormatter;

public class CustomPieChartValueFormatter extends ValueFormatter {

    public CustomPieChartValueFormatter() {
        // No formatting logic needed since we are hiding the values
    }

    @Override
    public String getFormattedValue(float value) {
        // Return empty string to hide the percentage value completely
        return "";
    }
}