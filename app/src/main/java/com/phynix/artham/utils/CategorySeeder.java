package com.phynix.artham.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
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
            List<CategoryModel> masterDefaults = DefaultCategoryManager.getAllDefaultCategories();

            if (!snapshot.exists() || !snapshot.hasChildren()) {
                // 1. FIRST TIME USER: No categories exist. Push all defaults to Firebase.
                Log.d(TAG, "No categories found. Seeding initial defaults...");
                for (CategoryModel category : masterDefaults) {
                    String id = categoryRef.push().getKey();
                    if (id != null) {
                        category.setId(id);
                        categoryRef.child(id).setValue(category);
                    }
                }
            } else {
                // 2. EXISTING USER: Sync defaults to catch any color/name updates from code
                Log.d(TAG, "Categories exist. Syncing default properties...");
                List<String> existingDefaultNames = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    CategoryModel dbModel = ds.getValue(CategoryModel.class);
                    if (dbModel != null) {
                        if (!dbModel.isCustom()) {
                            // Track existing default names to check for missing ones later
                            existingDefaultNames.add(dbModel.getName());

                            // Look up this category in our master code file
                            CategoryModel masterModel = DefaultCategoryManager.getCategoryByName(dbModel.getName());

                            if (masterModel != null) {
                                boolean needsUpdate = false;

                                // If the developer changed the color in code, force update Firebase!
                                if (!dbModel.getColorHex().equalsIgnoreCase(masterModel.getColorHex())) {
                                    dbModel.setColorHex(masterModel.getColorHex());
                                    needsUpdate = true;
                                }

                                if (needsUpdate) {
                                    Log.d(TAG, "Updating default category: " + dbModel.getName());
                                    categoryRef.child(ds.getKey()).setValue(dbModel);
                                }
                            }
                        }
                    }
                }

                // 3. Add any NEW default categories you might have added to DefaultCategoryManager later
                for (CategoryModel masterCategory : masterDefaults) {
                    if (!existingDefaultNames.contains(masterCategory.getName())) {
                        Log.d(TAG, "Adding missing new default category: " + masterCategory.getName());
                        String id = categoryRef.push().getKey();
                        if (id != null) {
                            masterCategory.setId(id);
                            categoryRef.child(id).setValue(masterCategory);
                        }
                    }
                }
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to check categories", e));
    }
}