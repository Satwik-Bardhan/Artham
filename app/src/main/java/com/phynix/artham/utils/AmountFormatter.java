package com.phynix.artham.utils;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility for smart amount formatting across the app.
 *
 * Features:
 * - Adaptive text size for the balance card based on digit count
 * - Paise (decimal portion) rendered smaller for better visual hierarchy
 * - Compact notation (1L, 1Cr, 1B, 1T) for summary sections
 */
public class AmountFormatter {

    private static final NumberFormat INR_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    // =========================================================================
    // 1. ADAPTIVE BALANCE TEXT — scales font size down as the number grows
    // =========================================================================

    /**
     * Sets the balance amount on the given TextView with:
     *   - Auto-scaled text size based on digit count
     *   - Smaller paise (decimal) portion
     *
     * Size tiers (sp):
     *   ≤ 7 digits  → 32sp  (H1)
     *   8 digits    → 28sp  (H2)
     *   9 digits    → 24sp  (H3)
     *  10 digits    → 20sp  (H4)
     *  11 digits    → 18sp  (H5)
     *  ≥12 digits   → 16sp  (H6)
     */
    public static void setAdaptiveBalance(TextView tv, double amount) {
        if (tv == null) return;

        String formatted = INR_FORMAT.format(amount);

        // Count significant digits (ignore ₹, commas, minus sign, decimal, spaces)
        int digitCount = 0;
        for (char c : formatted.toCharArray()) {
            if (Character.isDigit(c)) digitCount++;
        }

        // Determine the text size tier
        float baseSizeSp;
        if (digitCount <= 7) {
            baseSizeSp = 32f;
        } else if (digitCount <= 8) {
            baseSizeSp = 28f;
        } else if (digitCount <= 9) {
            baseSizeSp = 24f;
        } else if (digitCount <= 10) {
            baseSizeSp = 20f;
        } else if (digitCount <= 11) {
            baseSizeSp = 18f;
        } else {
            baseSizeSp = 16f;
        }

        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSizeSp);

        // Build spannable with smaller paise
        SpannableString spannable = buildPaiseSpannable(formatted);
        tv.setText(spannable);
    }

    /**
     * Creates a SpannableString where everything after the decimal point
     * is rendered at 65% of the main text size.
     * Public so it can be used on any pre-formatted amount string.
     */
    public static SpannableString buildPaiseSpannable(String formatted) {
        int dotIndex = formatted.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= formatted.length() - 1) {
            // No decimal portion — return as-is
            return new SpannableString(formatted);
        }

        SpannableString spannable = new SpannableString(formatted);
        // Make the decimal + paise portion smaller (65% of main size)
        spannable.setSpan(
                new RelativeSizeSpan(0.65f),
                dotIndex,
                formatted.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return spannable;
    }

    // =========================================================================
    // 2. COMPACT NOTATION — for Total In / Total Out / Daily / Transaction rows
    // =========================================================================

    /**
     * Formats an amount in compact notation using the Indian numbering system.
     * Uses ₹ prefix.
     *
     * Thresholds:
     *   < 1,00,000   → full Indian format   (e.g. ₹45,000.00)
     *   ≥ 1,00,000   → ₹1.00L   (Lakh)
     *   ≥ 1,00,00,000 → ₹1.00Cr  (Crore)
     *   ≥ 1,00,00,00,000 → ₹1.00B   (Billion – i.e. 100 Cr)
     *   ≥ 1,00,00,00,00,000 → ₹1.00T   (Trillion)
     */
    public static String formatCompact(double amount) {
        boolean negative = amount < 0;
        double abs = Math.abs(amount);
        String result;

        if (abs < 1_00_000) {
            // Below 1 lakh — use normal Indian currency format
            result = INR_FORMAT.format(amount);
            return result;
        } else if (abs < 1_00_00_000) {
            // Lakhs
            double val = abs / 1_00_000.0;
            result = "₹" + formatCompactValue(val) + "L";
        } else if (abs < 1_00_00_00_000L) {
            // Crores
            double val = abs / 1_00_00_000.0;
            result = "₹" + formatCompactValue(val) + "Cr";
        } else if (abs < 1_00_00_00_00_000L) {
            // Billions (100 Cr)
            double val = abs / 1_00_00_00_000L;
            result = "₹" + formatCompactValue(val) + "B";
        } else {
            // Trillions
            double val = abs / 1_00_00_00_00_000L;
            result = "₹" + formatCompactValue(val) + "T";
        }

        return negative ? "-" + result : result;
    }

    /**
     * Returns a SpannableString with compact notation AND smaller paise.
     * Use this with setText() on any TextView displaying amounts.
     */
    public static CharSequence formatCompactSpannable(double amount) {
        String compact = formatCompact(amount);
        return buildPaiseSpannable(compact);
    }

    /**
     * Formats the compact value with up to 2 decimal places, dropping trailing zeros.
     * e.g. 1.50 → "1.5", 2.00 → "2", 3.14 → "3.14"
     */
    private static String formatCompactValue(double val) {
        if (val == (long) val) {
            return String.valueOf((long) val);
        }
        String formatted = String.format(Locale.US, "%.2f", val);
        // Remove trailing zeros after decimal
        formatted = formatted.replaceAll("0+$", "");
        formatted = formatted.replaceAll("\\.$", "");
        return formatted;
    }

    // =========================================================================
    // 4. AMOUNT INPUT VALIDATION — max 15 digits before decimal, 2 after
    // =========================================================================

    /**
     * Creates and attaches a TextWatcher to the given EditText that enforces:
     *   - Max 15 digits before the decimal point
     *   - Max 2 digits after the decimal point
     * Shows an error on the EditText if the user tries to exceed these limits.
     */
    public static android.text.TextWatcher createAmountInputWatcher(android.widget.EditText editText) {
        return new android.text.TextWatcher() {
            private String previousText = "";
            private boolean isEditing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!isEditing) previousText = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isEditing) return;
                String text = s.toString();

                // Allow empty input
                if (text.isEmpty()) {
                    editText.setError(null);
                    return;
                }

                // Split by decimal
                String[] parts = text.split("\\.", -1);
                String integerPart = parts[0].replaceAll("[^0-9]", "");
                String decimalPart = parts.length > 1 ? parts[1].replaceAll("[^0-9]", "") : "";

                boolean hasError = false;

                if (integerPart.length() > 15) {
                    editText.setError("Max 15 digits before decimal");
                    hasError = true;
                }

                if (decimalPart.length() > 2) {
                    editText.setError("Max 2 digits after decimal");
                    hasError = true;
                }

                if (hasError) {
                    // Revert to previous valid text
                    isEditing = true;
                    s.replace(0, s.length(), previousText);
                    editText.setSelection(Math.min(previousText.length(), s.length()));
                    isEditing = false;
                } else {
                    editText.setError(null);
                    previousText = text;
                }
            }
        };
    }
}
