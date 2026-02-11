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
        // --- Colors ---
        categoryColorMap.put("Food & Dining", R.color.category_food);
        categoryColorMap.put("Bills & Utility", R.color.category_utilities);
        categoryColorMap.put("Transport", R.color.category_transport);
        categoryColorMap.put("Rent", R.color.category_rent);
        categoryColorMap.put("Shopping", R.color.category_shopping);
        categoryColorMap.put("Entertainment", R.color.category_entertainment);
        categoryColorMap.put("Health", R.color.category_health);
        categoryColorMap.put("Education", R.color.category_education);
        categoryColorMap.put("Personal", R.color.purple_primary);
        categoryColorMap.put("Other", R.color.category_other);

        // --- Icons ---
        categoryIconMap.put("Food & Dining", R.drawable.ic_food_dining);
        categoryIconMap.put("Bills & Utility", R.drawable.ic_utilities);
        categoryIconMap.put("Transport", R.drawable.ic_transportation);
        categoryIconMap.put("Rent", R.drawable.ic_home);
        categoryIconMap.put("Shopping", R.drawable.ic_receipt);
        categoryIconMap.put("Entertainment", R.drawable.ic_entertainment);
        categoryIconMap.put("Health", R.drawable.ic_medicine);
        categoryIconMap.put("Education", R.drawable.ic_book);
        categoryIconMap.put("Personal", R.drawable.ic_person);
        categoryIconMap.put("Other", R.drawable.ic_all_inclusive);
    }

    public static int getCategoryColor(Context context, String categoryName) {
        if (categoryName == null) return ContextCompat.getColor(context, R.color.category_default);

        if (categoryColorMap.containsKey(categoryName)) {
            return ContextCompat.getColor(context, categoryColorMap.get(categoryName));
        } else {
            // Generate a consistent "Random" color for custom categories based on name hash
            return generateRandomColor(categoryName);
        }
    }

    public static int getCategoryIcon(String categoryName) {
        if (categoryName != null && categoryIconMap.containsKey(categoryName)) {
            return categoryIconMap.get(categoryName);
        }
        // Default Icon for custom categories
        return R.drawable.ic_category;
    }

    private static int generateRandomColor(String key) {
        int hash = key.hashCode();
        // Generate RGB values from hash to ensure same name always gets same color
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);

        // Ensure color isn't too light (so it shows on white bg)
        return Color.rgb((r + 50) % 200, (g + 50) % 200, (b + 50) % 200);
    }
}