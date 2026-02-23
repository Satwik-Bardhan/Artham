package com.phynix.artham.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.phynix.artham.models.CategoryModel;

import java.util.ArrayList;
import java.util.List;

public class CategorySeeder {

    private static final String TAG = "CategorySeeder";

    /**
     * Seeds default categories into the user's Firebase database if they don't exist.
     * Call this during user registration or initial app load.
     */
    public static void seedDefaultCategories() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        DatabaseReference categoryRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userId).child("categories");

        // Check if categories already exist to avoid duplicating
        categoryRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists() || !snapshot.hasChildren()) {
                Log.d(TAG, "No categories found. Seeding defaults...");
                List<CategoryModel> defaults = getDefaultCategories();

                for (CategoryModel category : defaults) {
                    String id = categoryRef.push().getKey();
                    if (id != null) {
                        category.setId(id);
                        categoryRef.child(id).setValue(category);
                    }
                }
            } else {
                Log.d(TAG, "Categories already exist. Skipping seed.");
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to check categories", e));
    }

    /**
     * Requirement #4, #5, #6: Defines the exact default categories,
     * locking them as custom = false.
     */
    private static List<CategoryModel> getDefaultCategories() {
        List<CategoryModel> list = new ArrayList<>();

        // --- Expenses (OUT) ---
        list.add(new CategoryModel("Food & Dining", "#FF7043", "OUT", com.phynix.artham.R.drawable.ic_food_dining, false));
        list.add(new CategoryModel("Groceries", "#8BC34A", "OUT", com.phynix.artham.R.drawable.ic_shopping_cart, false)); // New
        list.add(new CategoryModel("Bills & Utility", "#26A69A", "OUT", com.phynix.artham.R.drawable.ic_utilities, false));
        list.add(new CategoryModel("Subscriptions", "#3F51B5", "OUT", com.phynix.artham.R.drawable.ic_subscriptions, false)); // New
        list.add(new CategoryModel("Transport", "#29B6F6", "OUT", com.phynix.artham.R.drawable.ic_transportation, false));
        list.add(new CategoryModel("Travel", "#03A9F4", "OUT", com.phynix.artham.R.drawable.ic_flight, false)); // New
        list.add(new CategoryModel("Rent", "#FFA726", "OUT", com.phynix.artham.R.drawable.ic_home, false));
        list.add(new CategoryModel("Insurance", "#795548", "OUT", com.phynix.artham.R.drawable.ic_security, false)); // New
        list.add(new CategoryModel("Shopping", "#EC407A", "OUT", com.phynix.artham.R.drawable.ic_receipt, false));
        list.add(new CategoryModel("Entertainment", "#AB47BC", "OUT", com.phynix.artham.R.drawable.ic_entertainment, false));
        list.add(new CategoryModel("Health", "#EF5350", "OUT", com.phynix.artham.R.drawable.ic_medicine, false));
        list.add(new CategoryModel("Education", "#5C6BC0", "OUT", com.phynix.artham.R.drawable.ic_book, false));
        list.add(new CategoryModel("Gifts & Donations", "#F06292", "OUT", com.phynix.artham.R.drawable.ic_card_giftcard, false)); // New

        // --- Income (IN) ---
        list.add(new CategoryModel("Salary", "#66BB6A", "IN", com.phynix.artham.R.drawable.ic_money, false));
        list.add(new CategoryModel("Freelance", "#CDDC39", "IN", com.phynix.artham.R.drawable.ic_work, false)); // New
        list.add(new CategoryModel("Refunds", "#4DB6AC", "IN", com.phynix.artham.R.drawable.ic_assignment_return, false)); // New
        list.add(new CategoryModel("Investment", "#009688", "IN", com.phynix.artham.R.drawable.ic_all_inclusive, false));

        // --- Universal / Other ---
        list.add(new CategoryModel("Other", "#78909C", "UNIVERSAL", com.phynix.artham.R.drawable.ic_category, false));

        return list;
    }
}