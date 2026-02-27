package com.phynix.artham.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.phynix.artham.R;
import com.phynix.artham.models.CategoryModel;

import java.util.ArrayList;
import java.util.List;

public class CategorySeeder {

    private static final String TAG = "CategorySeeder";

    public static void seedDefaultCategories() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        DatabaseReference categoryRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userId).child("categories");

        categoryRef.get().addOnSuccessListener(snapshot -> {
            boolean hasDefaults = false;

            // Check if there are any non-custom (default) categories in the database
            if (snapshot.exists() && snapshot.hasChildren()) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CategoryModel model = ds.getValue(CategoryModel.class);
                    if (model != null && !model.isCustom()) {
                        hasDefaults = true;
                        break;
                    }
                }
            }

            // Only seed if defaults are actually missing
            if (!hasDefaults) {
                Log.d(TAG, "Default categories missing. Seeding now...");
                List<CategoryModel> defaults = getDefaultCategories();

                for (CategoryModel category : defaults) {
                    String id = categoryRef.push().getKey();
                    if (id != null) {
                        category.setId(id);
                        categoryRef.child(id).setValue(category);
                    }
                }
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to check categories", e));
    }

    private static List<CategoryModel> getDefaultCategories() {
        List<CategoryModel> list = new ArrayList<>();

        // --- Expenses (OUT) ---
        list.add(new CategoryModel("Food & Dining", "OUT", "#FF7043", R.drawable.ic_food_dining, false));
        list.add(new CategoryModel("Groceries", "OUT", "#8BC34A", R.drawable.ic_shopping_cart, false));
        list.add(new CategoryModel("Bills & Utility", "OUT", "#26A69A", R.drawable.ic_utilities, false));
        list.add(new CategoryModel("Subscriptions", "OUT", "#3F51B5", R.drawable.ic_subscriptions, false));
        list.add(new CategoryModel("Transport", "OUT", "#29B6F6", R.drawable.ic_transportation, false));
        list.add(new CategoryModel("Travel", "OUT", "#03A9F4", R.drawable.ic_flight, false));
        list.add(new CategoryModel("Rent", "OUT", "#FFA726", R.drawable.ic_home, false));
        list.add(new CategoryModel("Insurance", "OUT", "#795548", R.drawable.ic_security, false));
        list.add(new CategoryModel("Shopping", "OUT", "#EC407A", R.drawable.ic_receipt, false));
        list.add(new CategoryModel("Entertainment", "OUT", "#AB47BC", R.drawable.ic_entertainment, false));
        list.add(new CategoryModel("Health", "OUT", "#EF5350", R.drawable.ic_medicine, false));
        list.add(new CategoryModel("Education", "OUT", "#5C6BC0", R.drawable.ic_book, false));

        // --- Income (IN) ---
        list.add(new CategoryModel("Salary", "IN", "#66BB6A", R.drawable.ic_money, false));
        list.add(new CategoryModel("Freelance", "IN", "#CDDC39", R.drawable.ic_work, false));
        list.add(new CategoryModel("Refunds", "IN", "#4DB6AC", R.drawable.ic_category, false));
        list.add(new CategoryModel("Investment", "IN", "#009688", R.drawable.ic_all_inclusive, false));

        return list;
    }
}