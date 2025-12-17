package com.phynix.artham;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.adapters.CategoryAdapter;
import com.phynix.artham.utils.CategoryColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChooseCategoryActivity extends AppCompatActivity
        implements CategoryAdapter.OnCategoryClickListener, CategoryAdapter.OnCategoryActionListener {

    private static final String TAG = "ChooseCategoryActivity";

    private RadioButton radioNoCategory;
    private RecyclerView categoriesRecyclerView;
    private ExtendedFloatingActionButton addNewCategoryButton;
    private TextView categoryCountTextView;
    private Button quickFoodButton, quickTransportButton, quickShoppingButton;

    private FirebaseAuth mAuth;
    private DatabaseReference userCategoriesRef;
    private ValueEventListener categoriesListener;

    private List<CategoryModel> allCategories = new ArrayList<>();
    private CategoryAdapter categoryAdapter;

    private String previouslySelectedCategoryName = "";
    private String currentCashbookId;
    private String transactionType = "OUT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_category);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // 1. GET DATA
        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        previouslySelectedCategoryName = getIntent().getStringExtra("selected_category");
        if (getIntent().hasExtra("transaction_type")) {
            transactionType = getIntent().getStringExtra("transaction_type");
        }

        // 2. DEBUGGING CHECK (This will tell us if ID is missing)
        if (currentCashbookId == null) {
            Toast.makeText(this, "CRITICAL ERROR: Cashbook ID is Missing!", Toast.LENGTH_LONG).show();
        }

        // 3. INIT FIREBASE
        if (currentUser != null && currentCashbookId != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid()).child("cashbooks")
                    .child(currentCashbookId).child("categories")
                    .child(transactionType);
        }

        initializeUI();
        setupRecyclerView();
        setupListeners();

        if (isNoCategory(previouslySelectedCategoryName)) {
            radioNoCategory.setChecked(true);
        }
    }

    private void initializeUI() {
        radioNoCategory = findViewById(R.id.radioNoCategory);
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        addNewCategoryButton = findViewById(R.id.addNewCategoryButton);
        categoryCountTextView = findViewById(R.id.categoryCount);

        quickFoodButton = findViewById(R.id.quickCategoryFood);
        quickTransportButton = findViewById(R.id.quickCategoryTransport);
        quickShoppingButton = findViewById(R.id.quickCategoryShopping);
    }

    private void setupRecyclerView() {
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        categoryAdapter = new CategoryAdapter(allCategories, this, this, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // [UPDATED] Robust Click Listener
        addNewCategoryButton.setOnClickListener(v -> {
            // Debug Toast to confirm click is registered
            // Toast.makeText(this, "Button Clicked...", Toast.LENGTH_SHORT).show();

            if (userCategoriesRef == null) {
                // If this shows, your CashInOutActivity is NOT sending the ID correctly
                new AlertDialog.Builder(this)
                        .setTitle("Connection Error")
                        .setMessage("Cashbook ID is missing. Please go back to the previous screen and try again.")
                        .setPositiveButton("OK", null)
                        .show();
            } else {
                showAddCategoryDialog(null);
            }
        });

        findViewById(R.id.noCategoryClickable).setOnClickListener(v -> {
            radioNoCategory.setChecked(true);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                returnCategory("No Category");
            }, 150);
        });

        if (quickFoodButton != null) quickFoodButton.setOnClickListener(v -> returnCategory("Food"));
        if (quickTransportButton != null) quickTransportButton.setOnClickListener(v -> returnCategory("Transport"));
        if (quickShoppingButton != null) quickShoppingButton.setOnClickListener(v -> returnCategory("Shopping"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        populatePredefinedCategories();
        if (userCategoriesRef != null) {
            startListeningForCategories();
        } else {
            updateUI();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userCategoriesRef != null && categoriesListener != null) {
            userCategoriesRef.removeEventListener(categoriesListener);
        }
    }

    private void populatePredefinedCategories() {
        allCategories.clear();
        String[] predefinedNames = getResources().getStringArray(R.array.transaction_categories);
        for (String name : predefinedNames) {
            if (!"Select Category".equals(name) && !"No Category".equals(name)) {
                int colorInt = CategoryColorUtil.getCategoryColor(this, name);
                String colorHex = String.format("#%06X", (0xFFFFFF & colorInt));
                allCategories.add(new CategoryModel(name, colorHex, false));
            }
        }
    }

    private void startListeningForCategories() {
        categoriesListener = userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                populatePredefinedCategories();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CategoryModel customCategory = snapshot.getValue(CategoryModel.class);
                    if (customCategory != null) {
                        allCategories.add(customCategory);
                    }
                }
                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(ChooseCategoryActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateUI() {
        categoryAdapter.notifyDataSetChanged();
        categoryCountTextView.setText(allCategories.size() + " items");
        selectInitialCategory();
    }

    private void selectInitialCategory() {
        if (!isNoCategory(previouslySelectedCategoryName)) {
            for (CategoryModel category : allCategories) {
                if (category.getName().equals(previouslySelectedCategoryName)) {
                    categoryAdapter.setSelectedCategory(category);
                    radioNoCategory.setChecked(false);
                    return;
                }
            }
        }
    }

    @Override
    public void onCategoryClick(CategoryModel category) {
        returnCategory(category.getName());
    }

    @Override
    public void onEditCategory(CategoryModel category) {
        showAddCategoryDialog(category);
    }

    @Override
    public void onDeleteCategory(CategoryModel category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete '" + category.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCategoryFromFirebase(category.getName()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void returnCategory(String name) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selected_category", name);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void showAddCategoryDialog(CategoryModel categoryToEdit) {
        final boolean isEditMode = categoryToEdit != null;
        final String oldName = isEditMode ? categoryToEdit.getName() : "";

        // Use 'ChooseCategoryActivity.this' to ensure correct context
        final EditText categoryNameEditText = new EditText(ChooseCategoryActivity.this);
        categoryNameEditText.setHint("Category Name");
        categoryNameEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (isEditMode) {
            categoryNameEditText.setText(categoryToEdit.getName());
        }

        LinearLayout container = new LinearLayout(ChooseCategoryActivity.this);
        container.setOrientation(LinearLayout.VERTICAL);
        // Using DP for padding to prevent layout issues
        int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        container.setPadding(paddingPx, paddingPx/2, paddingPx, 0);
        container.addView(categoryNameEditText);

        new AlertDialog.Builder(ChooseCategoryActivity.this)
                .setTitle(isEditMode ? "Edit Category" : "Add New Category")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newCategoryName = categoryNameEditText.getText().toString().trim();
                    if (!newCategoryName.isEmpty()) {

                        // Auto-generate random color
                        String colorHex;
                        if (isEditMode) {
                            colorHex = categoryToEdit.getColorHex();
                        } else {
                            colorHex = getRandomColorHex();
                        }

                        if (isEditMode && !oldName.equals(newCategoryName)) {
                            deleteCategoryFromFirebase(oldName);
                        }
                        saveCategoryToFirebase(newCategoryName, colorHex);

                    } else {
                        Toast.makeText(ChooseCategoryActivity.this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getRandomColorHex() {
        Random random = new Random();
        int r = random.nextInt(200) + 55;
        int g = random.nextInt(200) + 55;
        int b = random.nextInt(200) + 55;
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private void saveCategoryToFirebase(String name, String colorHex) {
        if (userCategoriesRef == null) {
            Toast.makeText(this, "Database Error: Not Connected.", Toast.LENGTH_SHORT).show();
            return;
        }
        CategoryModel newCategory = new CategoryModel(name, colorHex, true);
        userCategoriesRef.child(name).setValue(newCategory)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Category Saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save.", Toast.LENGTH_LONG).show());
    }

    private void deleteCategoryFromFirebase(String name) {
        if (userCategoriesRef == null) return;
        userCategoriesRef.child(name).removeValue()
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete.", Toast.LENGTH_SHORT).show());
    }

    private boolean isNoCategory(String name) {
        return name == null || name.isEmpty() || "No Category".equals(name) || "Select Category".equals(name);
    }
}