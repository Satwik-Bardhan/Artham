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

        // --- Expenses & Income (All Universal) ---
        list.add(new CategoryModel("Food & Dining",  "UNIVERSAL", "#FF7043", R.drawable.ic_food_dining,      false));
        list.add(new CategoryModel("Groceries",      "UNIVERSAL", "#8BC34A", R.drawable.ic_groceries,        false));
        list.add(new CategoryModel("Bills & Utility", "UNIVERSAL", "#FFDE21", R.drawable.ic_utilities,       false));
        list.add(new CategoryModel("Subscriptions",  "UNIVERSAL", "#3F51B5", R.drawable.ic_subscriptions,    false));
        list.add(new CategoryModel("Transport",      "UNIVERSAL", "#29B6F6", R.drawable.ic_transportation,   false));
        list.add(new CategoryModel("Travel",         "UNIVERSAL", "#03A9F4", R.drawable.ic_flight,           false));
        list.add(new CategoryModel("Rent",           "UNIVERSAL", "#FFA726", R.drawable.ic_home,             false));
        list.add(new CategoryModel("Insurance",      "UNIVERSAL", "#795548", R.drawable.ic_security,         false));
        list.add(new CategoryModel("Shopping",       "UNIVERSAL", "#EC407A", R.drawable.ic_shopping_cart,     false));
        list.add(new CategoryModel("Entertainment",  "UNIVERSAL", "#AB47BC", R.drawable.ic_entertainment,    false));
        list.add(new CategoryModel("Health",         "UNIVERSAL", "#EF5350", R.drawable.ic_medicine,         false));
        list.add(new CategoryModel("Education",      "UNIVERSAL", "#5C6BC0", R.drawable.ic_book,             false));
        list.add(new CategoryModel("Gifts & Charity", "UNIVERSAL", "#EC407A", R.drawable.ic_card_giftcard,    false));
        list.add(new CategoryModel("Business",        "UNIVERSAL", "#78909C", R.drawable.ic_work,             false));
        list.add(new CategoryModel("Taxes",           "UNIVERSAL", "#E53935", R.drawable.ic_receipt_outline,  false));
        list.add(new CategoryModel("Other Expenses",  "UNIVERSAL", "#9E9E9E", R.drawable.ic_category,         false));
        list.add(new CategoryModel("Salary",         "UNIVERSAL", "#66BB6A", R.drawable.ic_money,            false));
        list.add(new CategoryModel("Freelance",      "UNIVERSAL", "#CDDC39", R.drawable.ic_work,             false));
        list.add(new CategoryModel("Refunds",        "UNIVERSAL", "#4DB6AC", R.drawable.ic_assignment_return, false));
        list.add(new CategoryModel("Investment",     "UNIVERSAL", "#009688", R.drawable.ic_trending_up,      false));
        list.add(new CategoryModel("Gifts",           "UNIVERSAL", "#EC407A", R.drawable.ic_card_giftcard,    false));
        list.add(new CategoryModel("Rental Income",   "UNIVERSAL", "#FFA726", R.drawable.ic_home,             false));
        list.add(new CategoryModel("Interest & Dividends", "UNIVERSAL", "#00ACC1", R.drawable.ic_coins_outline, false));
        list.add(new CategoryModel("Business Revenue", "UNIVERSAL", "#3F51B5", R.drawable.ic_work,            false));
        list.add(new CategoryModel("Other Income",    "UNIVERSAL", "#9E9E9E", R.drawable.ic_category,         false));

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