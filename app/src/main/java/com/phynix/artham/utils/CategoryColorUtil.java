package com.phynix.artham.utils;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized utility for resolving category icons and colors.
 * 
 * Priority order:
 * 1. Firebase user-created/custom categories (highest priority)
 * 2. Default hardcoded categories (fallback)
 * 
 * Call CategoryColorUtil.initialize(context) once at app startup (e.g. in HomePage or MyApplication)
 * to preload user categories from Firebase.
 */
public class CategoryColorUtil {

    private static final String TAG = "CategoryColorUtil";

    // Cache of Firebase user-created categories: name -> CategoryModel
    private static final Map<String, CategoryModel> userCategoryCache = new HashMap<>();
    private static boolean isInitialized = false;

    /**
     * Initialize the cache by loading user categories from Firebase.
     * Should be called early in the app lifecycle (e.g., MyApplication or HomePage).
     */
    public static void initialize() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference categoriesRef = FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("categories");

        categoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userCategoryCache.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    CategoryModel model = s.getValue(CategoryModel.class);
                    if (model != null && model.getName() != null) {
                        userCategoryCache.put(model.getName().trim().toLowerCase(), model);
                    }
                }
                isInitialized = true;
                Log.d(TAG, "Loaded " + userCategoryCache.size() + " user categories into cache");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load user categories: " + error.getMessage());
            }
        });
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

        // 2. Check DefaultCategoryManager
        CategoryModel defaultModel = DefaultCategoryManager.getCategoryByName(categoryName);
        if (defaultModel != null) {
            return defaultModel.getIconResId();
        }

        // 3. Legacy hardcoded fallback
        switch (categoryName.toLowerCase().trim()) {
            case "food & dining": case "food": return R.drawable.ic_food_dining;
            case "transport": case "transportation": return R.drawable.ic_transportation;
            case "shopping": case "groceries": return R.drawable.ic_shopping_cart;
            case "rent": case "home": return R.drawable.ic_home;
            case "entertainment": return R.drawable.ic_entertainment;
            case "health": case "medicine": return R.drawable.ic_medicine;
            case "education": return R.drawable.ic_book;
            case "salary": case "income": return R.drawable.ic_money;
            case "investment": return R.drawable.ic_all_inclusive;
            case "travel": case "flight": return R.drawable.ic_flight;
            case "subscriptions": return R.drawable.ic_subscriptions;
            case "freelance": case "work": return R.drawable.ic_work;
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

            // 2. Check DefaultCategoryManager
            CategoryModel defaultModel = DefaultCategoryManager.getCategoryByName(categoryName);
            if (defaultModel != null && defaultModel.getColorHex() != null) {
                return Color.parseColor(defaultModel.getColorHex());
            }

            // 3. Legacy hardcoded fallback
            switch (categoryName.toLowerCase().trim()) {
                case "food & dining": return Color.parseColor("#FF7043");
                case "groceries": return Color.parseColor("#8BC34A");
                case "transport": return Color.parseColor("#29B6F6");
                case "shopping": return Color.parseColor("#EC407A");
                case "salary": return Color.parseColor("#66BB6A");
                case "rent": return Color.parseColor("#FFA726");
                case "health": return Color.parseColor("#EF5350");
                case "entertainment": return Color.parseColor("#AB47BC");
                default: return Color.parseColor("#78909C");
            }
        } catch (Exception e) {
            return Color.parseColor("#78909C");
        }
    }
}