package com.phynix.artham;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class ChooseCategoryActivity extends AppCompatActivity
        implements CategoryAdapter.OnCategoryClickListener, CategoryAdapter.OnCategoryActionListener {

    private RadioButton radioNoCategory;
    private RecyclerView categoriesRecyclerView;
    private ExtendedFloatingActionButton addNewCategoryButton;
    private TextView categoryCountTextView;
    private Button quickFoodButton, quickTransportButton, quickShoppingButton;

    private DatabaseReference userCategoriesRef;
    private ValueEventListener categoriesListener;

    private List<CategoryModel> allCategories = new ArrayList<>();
    private CategoryAdapter categoryAdapter;

    private String previouslySelectedCategoryName = "";
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_selection); // Make sure you have this layout

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        previouslySelectedCategoryName = getIntent().getStringExtra("selected_category");

        // Firebase Setup - [FIX] Unified Categories (No IN/OUT split)
        if (currentUser != null) {
            if (currentCashbookId != null && !currentCashbookId.isEmpty()) {
                userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(currentUser.getUid()).child("cashbooks")
                        .child(currentCashbookId).child("categories");
            } else {
                userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(currentUser.getUid()).child("categories");
            }
        }

        initializeUI();
        setupRecyclerView();
        setupListeners();
    }

    private void initializeUI() {
        radioNoCategory = findViewById(R.id.radioNoCategory);
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        addNewCategoryButton = findViewById(R.id.addNewCategoryButton);
        categoryCountTextView = findViewById(R.id.categoryCount);

        quickFoodButton = findViewById(R.id.quickCategoryFood);
        quickTransportButton = findViewById(R.id.quickCategoryTransport);
        quickShoppingButton = findViewById(R.id.quickCategoryShopping);

        if (previouslySelectedCategoryName != null &&
                (previouslySelectedCategoryName.isEmpty() || previouslySelectedCategoryName.equals("No Category"))) {
            if (radioNoCategory != null) radioNoCategory.setChecked(true);
        }
    }

    private void setupRecyclerView() {
        // Use GridLayoutManager with 2 columns
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        categoryAdapter = new CategoryAdapter(allCategories, this, this, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupListeners() {
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        if (addNewCategoryButton != null) {
            addNewCategoryButton.setOnClickListener(v -> {
                // Navigate to the full Create Category Page instead of showing a basic dialog
                Intent intent = new Intent(this, CreateCategoryActivity.class);
                intent.putExtra("cashbook_id", currentCashbookId);
                startActivity(intent);
            });
        }

        View noCatClickable = findViewById(R.id.noCategoryClickable);
        if (noCatClickable != null) {
            noCatClickable.setOnClickListener(v -> {
                if (radioNoCategory != null) radioNoCategory.setChecked(true);
                returnCategory("No Category");
            });
        }

        if (quickFoodButton != null) quickFoodButton.setOnClickListener(v -> returnCategory("Food & Dining"));
        if (quickTransportButton != null) quickTransportButton.setOnClickListener(v -> returnCategory("Transport"));
        if (quickShoppingButton != null) quickShoppingButton.setOnClickListener(v -> returnCategory("Shopping"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (userCategoriesRef != null) {
            startListeningForCategories();
        } else {
            populatePredefinedCategories();
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

        // Universal Defaults
        String[] predefinedNames = {
                "Food & Dining", "Transport", "Bills & Utility",
                "Salary", "Shopping", "Entertainment", "Health",
                "Education", "Personal", "Other"
        };

        for (String name : predefinedNames) {
            allCategories.add(new CategoryModel(name, "UNIVERSAL"));
        }
        updateUI();
    }

    private void startListeningForCategories() {
        categoriesListener = userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // If there are no custom categories, show the defaults
                if (!dataSnapshot.exists()) {
                    populatePredefinedCategories();
                    return;
                }

                allCategories.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CategoryModel custom = snapshot.getValue(CategoryModel.class);
                    if (custom != null) {
                        custom.setId(snapshot.getKey());
                        allCategories.add(custom);
                    }
                }
                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseCategoryActivity.this, "Failed to load", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateUI() {
        if (categoryCountTextView != null) {
            categoryCountTextView.setText(allCategories.size() + " items");
        }

        // Set the currently selected item visually
        if (previouslySelectedCategoryName != null && !previouslySelectedCategoryName.isEmpty()) {
            boolean found = false;
            for (CategoryModel c : allCategories) {
                if (c.getName().equals(previouslySelectedCategoryName)) {
                    categoryAdapter.setSelectedCategory(c);
                    if (radioNoCategory != null) radioNoCategory.setChecked(false);
                    found = true;
                    break;
                }
            }
            if (!found && radioNoCategory != null) {
                radioNoCategory.setChecked(true); // Fallback
            }
        }

        categoryAdapter.notifyDataSetChanged();
    }

    // --- Actions ---

    @Override
    public void onCategoryClick(CategoryModel category) {
        returnCategory(category.getName());
    }

    @Override
    public void onEditCategory(CategoryModel category) {
        // [UPDATE] Use the new Create/Edit Screen instead of dialog
        Intent intent = new Intent(this, CreateCategoryActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("EDIT_NAME", category.getName());
        startActivity(intent);
    }

    @Override
    public void onDeleteCategory(CategoryModel category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete " + category.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (userCategoriesRef != null && category.getName() != null) {
                        userCategoriesRef.child(category.getName()).removeValue();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void returnCategory(String name) {
        Intent result = new Intent();
        result.putExtra("selected_category", name);
        setResult(Activity.RESULT_OK, result);
        finish();
    }
}