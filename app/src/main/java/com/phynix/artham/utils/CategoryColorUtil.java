package com.phynix.artham.utils;

import android.content.Context;
import androidx.core.content.ContextCompat;

import com.phynix.artham.R;

import java.util.HashMap;
import java.util.Map;

public class CategoryColorUtil {

    private static final Map<String, Integer> categoryColorMap = new HashMap<>();

    // This block initializes the map with predefined category colors.
    static {
        categoryColorMap.put("Food", R.color.category_food);
        categoryColorMap.put("Transport", R.color.category_transport);
        categoryColorMap.put("Utilities", R.color.category_utilities);
        categoryColorMap.put("Rent", R.color.category_rent);
        categoryColorMap.put("Salary", R.color.category_salary);
        categoryColorMap.put("Freelance Income", R.color.category_freelance_income);
        categoryColorMap.put("Shopping", R.color.category_shopping);
        categoryColorMap.put("Entertainment", R.color.category_entertainment);
        categoryColorMap.put("Health", R.color.category_health);
        categoryColorMap.put("Education", R.color.category_education);
        categoryColorMap.put("Other", R.color.category_other);
        categoryColorMap.put("No Category", R.color.category_default); // [FIX] Added
        categoryColorMap.put("Select Category", R.color.category_default);
    }

    /**
     * Gets the color resource ID for a given category name.
     * @param context The context to resolve the color.
     * @param categoryName The name of the category.
     * @return The resolved color integer.
     */
    public static int getCategoryColor(Context context, String categoryName) {
        if (categoryName == null) {
            return ContextCompat.getColor(context, R.color.category_default);
        }
        Integer colorResId = categoryColorMap.get(categoryName);
        if (colorResId == null) {
            // Return a default color if the category name is not found.
            colorResId = R.color.category_default;
        }
        return ContextCompat.getColor(context, colorResId);
    }
}