package com.phynix.artham.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.phynix.artham.R; // [FIX] Imported R class from the main package
import com.phynix.artham.adapters.CategoryAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class CategoryActivity extends AppCompatActivity {

    private RecyclerView categoriesRecyclerView;
    private CategoryAdapter categoryAdapter;
    private DatabaseReference userCategoriesRef;

    private EditText searchEditText;
    private ImageButton sortButton;

    private List<CategoryModel> allCategories = new ArrayList<>();
    private String currentSearchQuery = "";
    private int currentSortMethod = 0; // 0 = Default, 1 = A-Z, 2 = Z-A

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        searchEditText = findViewById(R.id.searchEditText);
        sortButton = findViewById(R.id.sortButton);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(uid).child("categories");
        }

        setupRecyclerView();
        setupSearchAndSort();

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddDialog(null));

        loadCategories();
    }

    private void setupSearchAndSort() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase(Locale.US);
                applyFilterAndSort();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sortButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, sortButton);
            popup.getMenu().add(0, 0, 0, "Default Order");
            popup.getMenu().add(0, 1, 1, "A to Z");
            popup.getMenu().add(0, 2, 2, "Z to A");
            popup.setOnMenuItemClickListener(item -> {
                currentSortMethod = item.getItemId();
                applyFilterAndSort();
                return true;
            });
            popup.show();
        });
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);

        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (categoryAdapter.getItemViewType(position) == CategoryAdapter.TYPE_HEADER) {
                    return 2; // Headers take up 2 columns
                }
                return 1; // Items take up 1 column
            }
        });

        categoriesRecyclerView.setLayoutManager(layoutManager);

        categoryAdapter = new CategoryAdapter(new ArrayList<>(), this,
                category -> { /* Optional row click logic */ },
                new CategoryAdapter.OnCategoryActionListener() {
                    @Override
                    public void onEditCategory(CategoryModel category) { showAddDialog(category); }
                    @Override
                    public void onDeleteCategory(CategoryModel category) { deleteCategory(category); }
                });

        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void loadCategories() {
        if (userCategoriesRef == null) return;

        userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allCategories.clear();

                for (DataSnapshot s : snapshot.getChildren()) {
                    CategoryModel c = s.getValue(CategoryModel.class);
                    if (c != null) {
                        c.setId(s.getKey());
                        if ("OUT".equalsIgnoreCase(c.getType())) { // Currently showing only expenses
                            allCategories.add(c);
                        }
                    }
                }
                applyFilterAndSort();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CategoryActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilterAndSort() {
        // 1. Filter
        List<CategoryModel> filteredList = new ArrayList<>();
        for (CategoryModel c : allCategories) {
            if (c.getName() != null && c.getName().toLowerCase(Locale.US).contains(currentSearchQuery)) {
                filteredList.add(c);
            }
        }

        // 2. Sort
        if (currentSortMethod == 1) {
            Collections.sort(filteredList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        } else if (currentSortMethod == 2) {
            Collections.sort(filteredList, (a, b) -> b.getName().compareToIgnoreCase(a.getName()));
        }

        // 3. Group
        List<CategoryModel> defaultCategories = new ArrayList<>();
        List<CategoryModel> customCategories = new ArrayList<>();

        for(CategoryModel c : filteredList) {
            if (c.isCustom()) customCategories.add(c);
            else defaultCategories.add(c);
        }

        // Build list with headers
        List<Object> combinedList = new ArrayList<>();

        if (!defaultCategories.isEmpty()) {
            combinedList.add("Default Categories");
            combinedList.addAll(defaultCategories);
        }

        if (!customCategories.isEmpty()) {
            combinedList.add("Created Categories");
            combinedList.addAll(customCategories);
        }

        categoryAdapter.updateData(combinedList);
    }

    private void showAddDialog(CategoryModel category) {
        boolean isEdit = category != null;

        EditText input = new EditText(this);
        input.setHint("Category Name");
        input.setBackgroundResource(R.drawable.rounded_input_background);
        input.setTextColor(getThemeColor(R.attr.chk_textColorPrimary));
        input.setHintTextColor(getThemeColor(R.attr.chk_textColorHint));

        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        input.setPadding(padding, padding, padding, padding);

        if (isEdit) input.setText(category.getName());

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        container.setPadding(pad, pad, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Category" : "Add Category")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        String color = isEdit ? category.getColorHex() : getRandomColor();

                        if (isEdit && !category.getName().equals(name) && category.getId() != null) {
                            userCategoriesRef.child(category.getId()).removeValue();
                        }

                        String id = (isEdit && category.getId() != null) ? category.getId() : userCategoriesRef.push().getKey();

                        if (id != null) {
                            CategoryModel newCategory = new CategoryModel(name, "OUT", color, R.drawable.ic_category, true);
                            newCategory.setId(id);
                            userCategoriesRef.child(id).setValue(newCategory);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategory(CategoryModel c) {
        if (c.getId() == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete " + c.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> userCategoriesRef.child(c.getId()).removeValue())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getRandomColor() {
        Random r = new Random();
        return String.format("#%02X%02X%02X", r.nextInt(200) + 55, r.nextInt(200) + 55, r.nextInt(200) + 55);
    }

    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }
}