package com.phynix.artham.utils;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized utility for resolving category icons and colors.
 * 
 * Priority order:
 * 1. User-created/custom categories from Room DB cache (highest priority)
 * 2. DefaultCategoryManager lookup (covers all default categories)
 * 3. Legacy hardcoded fallback switch (safety net)
 */
public class CategoryColorUtil {

    private static final String TAG = "CategoryColorUtil";

    // Cache of user-created categories: name -> CategoryModel
    private static final Map<String, CategoryModel> userCategoryCache = new HashMap<>();
    private static boolean isInitialized = false;

    /**
     * Initialize the cache by loading user categories.
     * Should be called early in the app lifecycle (e.g., MyApplication or HomeActivity).
     */
    public static void initialize(Context context) {
        String uid = AuthManager.getUserId(context);
        if (uid == null) return;

        isInitialized = true;
    }

    /**
     * Add a user-created category to the runtime cache.
     * This ensures custom categories get proper colors/icons in pie charts.
     */
    public static void cacheUserCategory(CategoryModel category) {
        if (category != null && category.getName() != null) {
            userCategoryCache.put(category.getName().trim().toLowerCase(), category);
        }
    }

    /**
     * Get the icon resource ID for a category.
     * Checks user-created categories first, then falls back to defaults.
     */
    public static int getCategoryIcon(String categoryName) {
        if (categoryName == null) return R.drawable.ic_category;

        // 1. Check user-created category cache
        CategoryModel userCategory = userCategoryCache.get(categoryName.trim().toLowerCase());
        if (userCategory != null && userCategory.getIconResId() != 0) {
            return userCategory.getIconResId();
        }

        // 2. Check DefaultCategoryManager (covers all 29 default categories)
        CategoryModel defaultModel = DefaultCategoryManager.getCategoryByName(categoryName);
        if (defaultModel != null && defaultModel.getIconResId() != 0) {
            return defaultModel.getIconResId();
        }

        // 3. Legacy hardcoded fallback
        switch (categoryName.toLowerCase().trim()) {
            case "food & dining": case "food": return R.drawable.ic_food_dining;
            case "groceries": return R.drawable.ic_groceries;
            case "bills & utility": case "bills": return R.drawable.ic_utilities;
            case "subscriptions": return R.drawable.ic_subscriptions;
            case "transport": case "transportation": return R.drawable.ic_transportation;
            case "travel": case "flight": return R.drawable.ic_flight;
            case "rent": case "home": return R.drawable.ic_home;
            case "insurance": return R.drawable.ic_security;
            case "shopping": return R.drawable.ic_shopping_cart;
            case "entertainment": return R.drawable.ic_entertainment;
            case "health": case "medicine": case "medical": return R.drawable.ic_medicine;
            case "education": return R.drawable.ic_book;
            case "personal": return R.drawable.ic_person;
            case "gifts & charity": case "gifts": return R.drawable.ic_card_giftcard;
            case "business": return R.drawable.ic_work;
            case "taxes": return R.drawable.ic_receipt_outline;
            case "emi & loans": case "emi": case "loan": return R.drawable.ic_account_balance;
            case "kids & family": case "family": return R.drawable.ic_group_outline;
            case "pets": return R.drawable.ic_star_outline;
            case "salary": case "income": return R.drawable.ic_money;
            case "freelance": case "work": return R.drawable.ic_work;
            case "business revenue": return R.drawable.ic_bar_graph;
            case "investment": return R.drawable.ic_trending_up;
            case "rental income": return R.drawable.ic_home;
            case "interest & dividends": return R.drawable.ic_coins_outline;
            case "refunds": return R.drawable.ic_assignment_return;
            default:
                return R.drawable.ic_category;
        }
    }

    /**
     * Get the color for a category.
     * Checks user-created categories first, then falls back to defaults.
     */
    public static int getCategoryColor(Context context, String categoryName) {
        if (categoryName == null) return Color.parseColor("#78909C");

        try {
            // 1. Check user-created category cache
            CategoryModel userCategory = userCategoryCache.get(categoryName.trim().toLowerCase());
            if (userCategory != null && userCategory.getColorHex() != null) {
                return Color.parseColor(userCategory.getColorHex());
            }

            // 2. Check DefaultCategoryManager (covers all 29 default categories)
            CategoryModel defaultModel = DefaultCategoryManager.getCategoryByName(categoryName);
            if (defaultModel != null && defaultModel.getColorHex() != null) {
                return Color.parseColor(defaultModel.getColorHex());
            }

            // 3. Legacy hardcoded fallback
            switch (categoryName.toLowerCase().trim()) {
                case "food & dining": case "food": return Color.parseColor("#FF7043");
                case "groceries": return Color.parseColor("#8BC34A");
                case "bills & utility": case "bills": return Color.parseColor("#FFDE21");
                case "subscriptions": return Color.parseColor("#3F51B5");
                case "transport": case "transportation": return Color.parseColor("#29B6F6");
                case "travel": return Color.parseColor("#03A9F4");
                case "rent": return Color.parseColor("#FFA726");
                case "insurance": return Color.parseColor("#795548");
                case "shopping": return Color.parseColor("#EC407A");
                case "entertainment": return Color.parseColor("#AB47BC");
                case "health": case "medicine": case "medical": return Color.parseColor("#EF5350");
                case "education": return Color.parseColor("#5C6BC0");
                case "personal": return Color.parseColor("#607D8B");
                case "gifts & charity": return Color.parseColor("#E91E63");
                case "business": return Color.parseColor("#78909C");
                case "taxes": return Color.parseColor("#E53935");
                case "emi & loans": case "emi": case "loan": return Color.parseColor("#FF8A65");
                case "kids & family": case "family": return Color.parseColor("#CE93D8");
                case "pets": return Color.parseColor("#A1887F");
                case "other expenses": return Color.parseColor("#9E9E9E");
                case "salary": case "income": return Color.parseColor("#66BB6A");
                case "freelance": case "work": return Color.parseColor("#CDDC39");
                case "business revenue": return Color.parseColor("#42A5F5");
                case "investment": return Color.parseColor("#009688");
                case "rental income": return Color.parseColor("#FFA726");
                case "interest & dividends": return Color.parseColor("#00ACC1");
                case "gifts": return Color.parseColor("#FFEB3B");
                case "refunds": return Color.parseColor("#4DB6AC");
                case "other income": return Color.parseColor("#9E9E9E");
                default: return Color.parseColor("#78909C");
            }
        } catch (Exception e) {
            return Color.parseColor("#78909C");
        }
    }
}