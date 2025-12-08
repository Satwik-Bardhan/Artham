package com.phynix.artham;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.utils.CategoryColorUtil;

public class CategoryActivity extends AppCompatActivity {

    private static final String TAG = "CategoryActivity";

    private ChipGroup categoryChipGroup;
    private FloatingActionButton fabAddCategory;

    private DatabaseReference userCategoriesRef;
    private FirebaseUser currentUser;
    private ValueEventListener categoriesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        fabAddCategory = findViewById(R.id.fab_add_category);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        // Assuming we edit global custom categories here, or pass cashbook_id via Intent if specific
        if (currentUser != null) {
            // Note: If categories are per-cashbook, fetch the active cashbook ID here
            // For now, storing in a global 'customCategories' node for simplicity
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid()).child("customCategories");
        }

        setupClickListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadCategories();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userCategoriesRef != null && categoriesListener != null) {
            userCategoriesRef.removeEventListener(categoriesListener);
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        fabAddCategory.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void loadCategories() {
        if (userCategoriesRef == null) {
            setupDefaultCategoryChips(); // Show defaults if offline
            return;
        }

        categoriesListener = userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryChipGroup.removeAllViews();

                // 1. Add Defaults First
                setupDefaultCategoryChips();

                // 2. Add Custom Categories
                for (DataSnapshot data : snapshot.getChildren()) {
                    CategoryModel category = data.getValue(CategoryModel.class);
                    if (category != null) {
                        // Store the Firebase Key in the model temporarily or handle via map
                        addCategoryChip(category, data.getKey());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load categories", error.toException());
            }
        });
    }

    private void setupDefaultCategoryChips() {
        String[] predefinedNames = getResources().getStringArray(R.array.transaction_categories);
        for (String name : predefinedNames) {
            if (!"Select Category".equals(name)) {
                int color = CategoryColorUtil.getCategoryColor(this, name);
                String hexColor = String.format("#%06X", (0xFFFFFF & color));
                CategoryModel model = new CategoryModel(name, hexColor, false);
                addCategoryChip(model, null);
            }
        }
    }

    private void addCategoryChip(CategoryModel category, String firebaseKey) {
        Chip chip = new Chip(this);
        chip.setText(category.getName());
        chip.setCheckable(false);
        chip.setClickable(true);

        try {
            int color = android.graphics.Color.parseColor(category.getColorHex());
            chip.setChipIconVisible(true);
            chip.setChipIconTint(android.content.res.ColorStateList.valueOf(color));
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surfaceColor)));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(color));
            chip.setChipStrokeWidth(2f); // Make it visible
        } catch (IllegalArgumentException e) {
            chip.setChipIconTintResource(R.color.category_default);
        }

        // Only allow editing for custom categories
        if (category.isCustom() && firebaseKey != null) {
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> deleteCategory(firebaseKey, category.getName()));
            chip.setOnClickListener(v -> showEditCategoryDialog(category, firebaseKey));
        } else {
            // Predefined categories cannot be edited/deleted
            chip.setOnClickListener(v -> Toast.makeText(this, "Default categories cannot be edited.", Toast.LENGTH_SHORT).show());
        }

        categoryChipGroup.addView(chip);
    }

    private void showAddCategoryDialog() {
        showCategoryDialog(null, null);
    }

    private void showEditCategoryDialog(CategoryModel category, String key) {
        showCategoryDialog(category, key);
    }

    private void showCategoryDialog(CategoryModel existingCategory, String key) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(existingCategory == null ? "Add New Category" : "Edit Category");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(50, 40, 50, 10);

        final EditText nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setHint("Category Name");
        if (existingCategory != null) {
            nameInput.setText(existingCategory.getName());
        }
        container.addView(nameInput);

        builder.setView(container);

        builder.setPositiveButton(existingCategory == null ? "Choose Color" : "Update Color", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                return;
            }
            showColorPicker(name, key, existingCategory != null ? existingCategory.getColorHex() : "#808080");
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showColorPicker(String name, String key, String initialColorHex) {
        int initialColor;
        try {
            initialColor = android.graphics.Color.parseColor(initialColorHex);
        } catch (Exception e) {
            initialColor = ContextCompat.getColor(this, R.color.category_default);
        }

        ColorPickerDialogBuilder
                .with(this)
                .setTitle("Pick Color")
                .initialColor(initialColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .setPositiveButton("Save", (dialog, selectedColor, allColors) -> {
                    String hex = String.format("#%06X", (0xFFFFFF & selectedColor));
                    saveCategory(name, hex, key);
                })
                .setNegativeButton("Cancel", null)
                .build()
                .show();
    }

    private void saveCategory(String name, String colorHex, String key) {
        if (userCategoriesRef == null) return;

        CategoryModel category = new CategoryModel(name, colorHex, true);

        if (key == null) {
            // Create new
            String newKey = userCategoriesRef.push().getKey();
            if (newKey != null) {
                userCategoriesRef.child(newKey).setValue(category);
                Toast.makeText(this, "Category added", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Update existing
            userCategoriesRef.child(key).setValue(category);
            Toast.makeText(this, "Category updated", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteCategory(String key, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete '" + name + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (userCategoriesRef != null) {
                        userCategoriesRef.child(key).removeValue();
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}