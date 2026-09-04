package com.phynix.artham.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.auth.AuthManager;

import com.phynix.artham.R;
import com.phynix.artham.adapters.CategoryAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.utils.SnackbarHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import com.phynix.artham.BaseActivity;

public class CategoryActivity extends BaseActivity {

    private RecyclerView categoriesRecyclerView;
    private CategoryAdapter categoryAdapter;

    private EditText searchEditText;
    private ImageButton sortButton;

    private List<CategoryModel> allCategories = new ArrayList<>();
    private String currentSearchQuery = "";
    private int currentSortMethod = 0; // 0 = Default, 1 = A-Z, 2 = Z-A

    // Material Design Color Palette for the Color Picker
    private final String[] COLOR_PALETTE = {
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
            "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        searchEditText = findViewById(R.id.searchEditText);
        sortButton = findViewById(R.id.sortButton);

        String uid = AuthManager.getUserId(this);
        // Firebase initialization removed

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

    private String cashbookId;

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
    }

    private void loadCategories() {
        if (cashbookId == null || cashbookId.isEmpty()) {
            android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
            String uid = AuthManager.getUserId(this);
            cashbookId = prefs.getString("active_cashbook_id_" + uid, getIntent().getStringExtra("cashbook_id"));
        }
        com.phynix.artham.db.DataRepository.getInstance(getApplication()).getCategories(cashbookId, categories -> {
            if (!isAlive()) return;
            allCategories.clear();
            if (categories != null) {
                allCategories.addAll(categories);
            }
            applyFilterAndSort();
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
        android.content.Intent intent = new android.content.Intent(this, CreateCategoryActivity.class);
        if (category != null && category.getId() != null) {
            intent.putExtra("CATEGORY_ID", category.getId());
        }
        intent.putExtra("cashbook_id", cashbookId);
        startActivity(intent);
    }

    private void deleteCategory(CategoryModel c) {
        if (c == null || c.getId() == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete " + c.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    com.phynix.artham.db.DataRepository.getInstance(getApplication()).deleteCategory(c.getId(), success -> {
                        if (!isAlive()) return;
                        Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show();
                        loadCategories();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }
}