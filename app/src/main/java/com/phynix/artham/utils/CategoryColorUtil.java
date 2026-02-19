package com.phynix.artham.utils;

import android.content.Context;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import com.phynix.artham.R;
import java.util.HashMap;
import java.util.Map;

public class CategoryColorUtil {

    private static final Map<String, Integer> categoryColorMap = new HashMap<>();
    private static final Map<String, Integer> categoryIconMap = new HashMap<>();

    static {
        // --- Static Mapping for Default Categories ---
        categoryColorMap.put("Food & Dining", R.color.category_food);
        categoryColorMap.put("Bills & Utility", R.color.category_utilities);
        categoryColorMap.put("Transport", R.color.category_transport);
        categoryColorMap.put("Rent", R.color.category_rent);
        categoryColorMap.put("Shopping", R.color.category_shopping);
        categoryColorMap.put("Entertainment", R.color.category_entertainment);
        categoryColorMap.put("Health", R.color.category_health);
        categoryColorMap.put("Education", R.color.category_education);
        categoryColorMap.put("Salary", R.color.category_salary);
        categoryColorMap.put("Investment", R.color.category_investment);
        categoryColorMap.put("Other", R.color.category_other);

        categoryIconMap.put("Food & Dining", R.drawable.ic_food_dining);
        categoryIconMap.put("Bills & Utility", R.drawable.ic_utilities);
        categoryIconMap.put("Transport", R.drawable.ic_transportation);
        categoryIconMap.put("Rent", R.drawable.ic_home);
        categoryIconMap.put("Shopping", R.drawable.ic_receipt);
        categoryIconMap.put("Entertainment", R.drawable.ic_entertainment);
        categoryIconMap.put("Health", R.drawable.ic_medicine);
        categoryIconMap.put("Education", R.drawable.ic_book);
        categoryIconMap.put("Salary", R.drawable.ic_money);
        categoryIconMap.put("Investment", R.drawable.ic_all_inclusive);
    }

    /**
     * Converts a Hex string to an Int color.
     * Handles standard #RRGGBB and #AARRGGBB.
     */
    public static int parseHexColor(String hex, int defaultColor) {
        try {
            if (hex == null || hex.isEmpty()) return defaultColor;
            return Color.parseColor(hex);
        } catch (Exception e) {
            return defaultColor;
        }
    }

    public static int getCategoryColor(Context context, String categoryName) {
        if (categoryName == null) return ContextCompat.getColor(context, R.color.category_default);

        if (categoryColorMap.containsKey(categoryName)) {
            return ContextCompat.getColor(context, categoryColorMap.get(categoryName));
        } else {
            // Consistent generated color based on name hash if no hex is provided
            return generateConsistentColor(categoryName);
        }
    }

    public static int getCategoryIcon(String categoryName) {
        if (categoryName != null && categoryIconMap.containsKey(categoryName)) {
            return categoryIconMap.get(categoryName);
        }
        return R.drawable.ic_category; // Default
    }

    private static int generateConsistentColor(String key) {
        int hash = key.hashCode();
        // Extract RGB, ensuring they stay in a "vibrant but readable" range
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);

        // Adjust brightness so colors aren't too dark or too light
        return Color.rgb((r + 40) % 200, (g + 40) % 200, (b + 40) % 200);
    }
}