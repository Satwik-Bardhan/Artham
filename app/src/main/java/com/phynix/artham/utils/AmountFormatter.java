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
        setAdaptiveAmount(tv, amount, 32f, 16f);
    }

    /**
     * General-purpose adaptive text sizing for any amount TextView.
     * Gradually reduces font size from maxSizeSp to minSizeSp as digit count grows.
     *
     * @param tv         Target TextView
     * @param amount     The monetary amount
     * @param maxSizeSp  Font size for small amounts (≤5 digits)
     * @param minSizeSp  Minimum font size for very large amounts (≥12 digits)
     */
    public static void setAdaptiveAmount(TextView tv, double amount, float maxSizeSp, float minSizeSp) {
        if (tv == null) return;

        String formatted = INR_FORMAT.format(amount);

        // Count significant digits (ignore ₹, commas, minus sign, decimal, spaces)
        int digitCount = 0;
        for (char c : formatted.toCharArray()) {
            if (Character.isDigit(c)) digitCount++;
        }

        // Gradually scale: starts shrinking after 5 digits
        // Each additional digit beyond 5 reduces size proportionally
        float sizeSp;
        if (digitCount <= 5) {
            sizeSp = maxSizeSp;
        } else if (digitCount >= 12) {
            sizeSp = minSizeSp;
        } else {
            // Linear interpolation between max and min over 5..12 digit range
            float ratio = (digitCount - 5f) / 7f;
            sizeSp = maxSizeSp - (ratio * (maxSizeSp - minSizeSp));
        }

        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);

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
     * Formats an amount using the Indian numbering system.
     * Always displays full numeric value (e.g. ₹1,23,456.00) — no abbreviations.
     */
    public static String formatCompact(double amount) {
        return INR_FORMAT.format(amount);
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
