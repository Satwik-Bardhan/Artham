package com.phynix.artham.utils;

import android.content.Context;
import android.graphics.Color;
import com.phynix.artham.R;

public class CategoryColorUtil {

    public static int getCategoryIcon(String categoryName) {
        if (categoryName == null) return R.drawable.ic_category;

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

    public static int getCategoryColor(Context context, String categoryName) {
        try {
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