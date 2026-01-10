package com.phynix.artham;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
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
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;

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
    private String transactionType = "OUT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_category);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        previouslySelectedCategoryName = getIntent().getStringExtra("selected_category");
        if (getIntent().hasExtra("transaction_type")) {
            transactionType = getIntent().getStringExtra("transaction_type");
        }

        // Firebase Setup
        if (currentUser != null && currentCashbookId != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid()).child("cashbooks")
                    .child(currentCashbookId).child("categories")
                    .child(transactionType);
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

        if (previouslySelectedCategoryName != null && (previouslySelectedCategoryName.isEmpty() || previouslySelectedCategoryName.equals("No Category"))) {
            radioNoCategory.setChecked(true);
        }
    }

    private void setupRecyclerView() {
        // [UPDATED] Use GridLayoutManager with 2 columns
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        categoryAdapter = new CategoryAdapter(allCategories, this, this, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        addNewCategoryButton.setOnClickListener(v -> {
            if (userCategoriesRef == null) {
                Toast.makeText(this, "Cashbook ID missing", Toast.LENGTH_SHORT).show();
            } else {
                showAddCategoryDialog(null); // Add Mode
            }
        });

        findViewById(R.id.noCategoryClickable).setOnClickListener(v -> {
            radioNoCategory.setChecked(true);
            returnCategory("No Category");
        });

        if (quickFoodButton != null) quickFoodButton.setOnClickListener(v -> returnCategory("Food"));
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
        String[] predefinedNames = getResources().getStringArray(R.array.transaction_categories);
        for (String name : predefinedNames) {
            if (!"Select Category".equals(name) && !"No Category".equals(name)) {
                int colorInt = CategoryColorUtil.getCategoryColor(this, name);
                String colorHex = String.format("#%06X", (0xFFFFFF & colorInt));
                allCategories.add(new CategoryModel(name, colorHex, false));
            }
        }
        updateUI();
    }

    private void startListeningForCategories() {
        categoriesListener = userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                populatePredefinedCategories();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CategoryModel custom = snapshot.getValue(CategoryModel.class);
                    if (custom != null) allCategories.add(custom);
                }
                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateUI() {
        categoryAdapter.notifyDataSetChanged();
        categoryCountTextView.setText(allCategories.size() + " items");

        if (previouslySelectedCategoryName != null && !previouslySelectedCategoryName.isEmpty()) {
            for (CategoryModel c : allCategories) {
                if (c.getName().equals(previouslySelectedCategoryName)) {
                    categoryAdapter.setSelectedCategory(c);
                    radioNoCategory.setChecked(false);
                    break;
                }
            }
        }
    }

    // --- Actions ---

    @Override
    public void onCategoryClick(CategoryModel category) {
        returnCategory(category.getName());
    }

    @Override
    public void onEditCategory(CategoryModel category) {
        showAddCategoryDialog(category); // Edit Mode
    }

    @Override
    public void onDeleteCategory(CategoryModel category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete " + category.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> userCategoriesRef.child(category.getName()).removeValue())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void returnCategory(String name) {
        Intent result = new Intent();
        result.putExtra("selected_category", name);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void showAddCategoryDialog(CategoryModel categoryToEdit) {
        final boolean isEdit = categoryToEdit != null;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        container.setPadding(pad, pad, pad, 0);

        final EditText input = new EditText(this);
        input.setHint("Category Name");
        if (isEdit) {
            input.setText(categoryToEdit.getName());
            input.setSelection(input.getText().length());
        }
        container.addView(input);

        // Holder for the selected color
        final String[] tempColorHex = {isEdit ? categoryToEdit.getColorHex() : "#2196F3"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Category" : "Add New Category")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        saveCategoryToFirebase(name, tempColorHex[0], categoryToEdit);
                    }
                })
                .setNeutralButton("Choose Color", null) // Overridden below
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Handle Color Picker (Neutral Button)
    }

    private void saveCategoryToFirebase(String newName, String colorHex, CategoryModel oldCategory) {
        if (userCategoriesRef == null) return;

        if (oldCategory != null && !oldCategory.getName().equals(newName)) {
            userCategoriesRef.child(oldCategory.getName()).removeValue();
        }

        CategoryModel newCat = new CategoryModel(newName, colorHex, true);
        userCategoriesRef.child(newName).setValue(newCat)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show());
    }
}