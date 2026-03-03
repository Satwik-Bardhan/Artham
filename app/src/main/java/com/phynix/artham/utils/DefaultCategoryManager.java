package com.phynix.artham.utils;

import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.ArrayList;
import java.util.List;

public class DefaultCategoryManager {

    /**
     * ALL Default Categories are defined here.
     * Modify the Name, Color Hex, or Icon Drawable here, and it will update everywhere in the app!
     */
    public static List<CategoryModel> getAllDefaultCategories() {
        List<CategoryModel> list = new ArrayList<>();

        // --- Expenses (OUT) ---
        list.add(new CategoryModel("Food & Dining", "OUT", "#FF7043", R.drawable.ic_food_dining, false));
        list.add(new CategoryModel("Groceries", "OUT", "#8BC34A", R.drawable.ic_groceries, false));
        list.add(new CategoryModel("Bills & Utility", "OUT", "#FFDE21", R.drawable.ic_utilities, false));
        list.add(new CategoryModel("Subscriptions", "OUT", "#3F51B5", R.drawable.ic_subscriptions, false));
        list.add(new CategoryModel("Transport", "OUT", "#29B6F6", R.drawable.ic_transportation, false));
        list.add(new CategoryModel("Travel", "OUT", "#03A9F4", R.drawable.ic_flight, false));
        list.add(new CategoryModel("Rent", "OUT", "#FFA726", R.drawable.ic_home, false));
        list.add(new CategoryModel("Insurance", "OUT", "#795548", R.drawable.ic_security, false));
        list.add(new CategoryModel("Shopping", "OUT", "#EC407A", R.drawable.ic_receipt, false)); // Note: Uses your existing receipt icon
        list.add(new CategoryModel("Entertainment", "OUT", "#AB47BC", R.drawable.ic_entertainment, false));
        list.add(new CategoryModel("Health", "OUT", "#EF5350", R.drawable.ic_medicine, false));
        list.add(new CategoryModel("Education", "OUT", "#5C6BC0", R.drawable.ic_book, false));

        // --- Income (IN) ---
        list.add(new CategoryModel("Salary", "IN", "#66BB6A", R.drawable.ic_money, false));
        list.add(new CategoryModel("Freelance", "IN", "#CDDC39", R.drawable.ic_work, false));
        list.add(new CategoryModel("Refunds", "IN", "#4DB6AC", R.drawable.ic_assignment_return, false));
        list.add(new CategoryModel("Investment", "IN", "#009688", R.drawable.ic_trending_up, false));

        return list;
    }

    /**
     * Helper method to instantly fetch a default category's properties by its name.
     * Used by Analytics to fetch fallbacks accurately.
     */
    public static CategoryModel getCategoryByName(String categoryName) {
        if (categoryName == null) return null;

        String searchName = categoryName.trim().toLowerCase();

        for (CategoryModel category : getAllDefaultCategories()) {
            if (category.getName().trim().toLowerCase().equals(searchName)) {
                return category;
            }
        }

        // Return null if it's a Custom Category and not in the default list
        return null;
    }
}