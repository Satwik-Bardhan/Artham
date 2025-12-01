package com.satvik.artham;

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

import com.satvik.artham.utils.CategoryColorUtil;
import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChooseCategoryActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    private static final String TAG = "ChooseCategoryActivity";

    private RadioButton radioNoCategory;
    private RecyclerView categoriesRecyclerView;
    private ExtendedFloatingActionButton addNewCategoryButton;
    private TextView categoryCountTextView;
    // [FIX] Add quick buttons
    private Button quickFoodButton, quickTransportButton, quickShoppingButton;

    private FirebaseAuth mAuth;
    private DatabaseReference userCategoriesRef;
    private ValueEventListener categoriesListener;

    private List<CategoryModel> allCategories = new ArrayList<>();
    private CategoryAdapter categoryAdapter;

    private String previouslySelectedCategoryName = "";
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_category);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        previouslySelectedCategoryName = getIntent().getStringExtra("selected_category");

        if (currentUser != null && currentCashbookId != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid()).child("cashbooks")
                    .child(currentCashbookId).child("categories");
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

        // [FIX] Initialize Quick Suggestion Buttons
        quickFoodButton = findViewById(R.id.quickCategoryFood);
        quickTransportButton = findViewById(R.id.quickCategoryTransport);
        quickShoppingButton = findViewById(R.id.quickCategoryShopping);
    }

    private void setupRecyclerView() {
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        categoryAdapter = new CategoryAdapter(allCategories, this, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // [FIX] Add New Category Button Listener
        addNewCategoryButton.setOnClickListener(v -> showAddCategoryDialog());

        findViewById(R.id.noCategoryCard).setOnClickListener(v -> {
            radioNoCategory.setChecked(true);
            returnCategory("No Category");
        });

        // [FIX] Quick Suggestion Button Listeners
        if (quickFoodButton != null) {
            quickFoodButton.setOnClickListener(v -> returnCategory("Food"));
        }
        if (quickTransportButton != null) {
            quickTransportButton.setOnClickListener(v -> returnCategory("Transport"));
        }
        if (quickShoppingButton != null) {
            quickShoppingButton.setOnClickListener(v -> returnCategory("Shopping"));
        }
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

    private void returnCategory(String name) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selected_category", name);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void showAddCategoryDialog() {
        if (userCategoriesRef == null) {
            Toast.makeText(this, "You must be logged in to add categories.", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText categoryNameEditText = new EditText(this);
        categoryNameEditText.setHint("Category Name");
        categoryNameEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 16, 48, 0);
        container.addView(categoryNameEditText);

        new AlertDialog.Builder(this)
                .setTitle("Add New Category")
                .setView(container)
                .setPositiveButton("Choose Color", (dialog, which) -> {
                    String newCategoryName = categoryNameEditText.getText().toString().trim();
                    if (!newCategoryName.isEmpty()) {
                        showColorPickerDialog(newCategoryName);
                    } else {
                        Toast.makeText(this, "Category name cannot be empty.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showColorPickerDialog(String categoryName) {
        ColorPickerDialogBuilder
                .with(this)
                .setTitle("Choose Category Color")
                .initialColor(ContextCompat.getColor(this, R.color.category_default))
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .setPositiveButton("OK", (dialog, chosenColor, allColors) -> {
                    String colorHex = String.format("#%06X", (0xFFFFFF & chosenColor));
                    addNewCategoryToFirebase(categoryName, colorHex);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .build()
                .show();
    }

    private void addNewCategoryToFirebase(String name, String colorHex) {
        if (userCategoriesRef == null) return;

        CategoryModel newCategory = new CategoryModel(name, colorHex, true);

        userCategoriesRef.child(name).setValue(newCategory)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Category '" + name + "' added!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add category.", Toast.LENGTH_LONG).show());
    }

    private boolean isNoCategory(String name) {
        return name == null || name.isEmpty() || "No Category".equals(name) || "Select Category".equals(name);
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}