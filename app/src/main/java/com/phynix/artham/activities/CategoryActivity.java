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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.phynix.artham.R;
import com.phynix.artham.adapters.CategoryAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CategoryActivity extends AppCompatActivity {

    private RecyclerView categoriesRecyclerView;
    private CategoryAdapter categoryAdapter;
    private DatabaseReference userCategoriesRef;

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

        // Container
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        container.setPadding(pad, pad, pad, 0);

        // Name Input
        EditText input = new EditText(this);
        input.setHint("Category Name");
        input.setBackgroundResource(R.drawable.rounded_input_background);
        input.setTextColor(getThemeColor(R.attr.chk_textColorPrimary));
        input.setHintTextColor(getThemeColor(R.attr.chk_textColorHint));
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        input.setPadding(padding, padding, padding, padding);

        if (isEdit) input.setText(category.getName());
        container.addView(input);

        // Color Label
        TextView colorLabel = new TextView(this);
        colorLabel.setText("Select Color");
        colorLabel.setTextColor(getThemeColor(R.attr.chk_textColorSecondary));
        colorLabel.setTextSize(14f);
        colorLabel.setPadding(0, pad, 0, 8);
        container.addView(colorLabel);

        // Color Picker Setup
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setScrollbarFadingEnabled(false);
        hsv.setHorizontalScrollBarEnabled(false);

        LinearLayout colorContainer = new LinearLayout(this);
        colorContainer.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(colorContainer);
        container.addView(hsv);

        // Track selected color (default to category's existing color if editing, else random/first)
        final String[] selectedColorHex = new String[1];
        selectedColorHex[0] = isEdit ? category.getColorHex() : COLOR_PALETTE[(int)(Math.random() * COLOR_PALETTE.length)];

        List<ImageView> checkmarks = new ArrayList<>();

        // Generate Color Circles dynamically
        int circleSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());

        for (String hexCode : COLOR_PALETTE) {
            FrameLayout frameLayout = new FrameLayout(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(circleSize, circleSize);
            params.setMargins(0, 0, margin, 0);
            frameLayout.setLayoutParams(params);

            // Colored Circle
            View circle = new View(this);
            circle.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            circle.setBackgroundResource(R.drawable.circle_shape);
            circle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hexCode)));

            // Checkmark (Hidden by default)
            ImageView checkmark = new ImageView(this);
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                    (int)(circleSize * 0.5), (int)(circleSize * 0.5));
            checkParams.gravity = Gravity.CENTER;
            checkmark.setLayoutParams(checkParams);
            checkmark.setImageResource(R.drawable.ic_check);
            checkmark.setImageTintList(ColorStateList.valueOf(Color.WHITE));

            // If this is the currently selected color, show its checkmark
            if (hexCode.equalsIgnoreCase(selectedColorHex[0])) {
                checkmark.setVisibility(View.VISIBLE);
            } else {
                checkmark.setVisibility(View.GONE);
            }

            checkmarks.add(checkmark);

            frameLayout.addView(circle);
            frameLayout.addView(checkmark);

            // Click logic: Update selected color and toggle checkmarks
            frameLayout.setOnClickListener(v -> {
                selectedColorHex[0] = hexCode;
                for (ImageView cm : checkmarks) cm.setVisibility(View.GONE); // Hide all
                checkmark.setVisibility(View.VISIBLE); // Show selected
            });

            colorContainer.addView(frameLayout);
        }

        // Build Dialog
        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Category" : "Add Category")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        // Inherit original properties if editing, else defaults
                        int iconRes = isEdit ? category.getIconResId() : R.drawable.ic_category;
                        boolean isCustom = isEdit ? category.isCustom() : true;

                        if (isEdit && !category.getName().equals(name) && category.getId() != null) {
                            userCategoriesRef.child(category.getId()).removeValue();
                        }

                        String id = (isEdit && category.getId() != null) ? category.getId() : userCategoriesRef.push().getKey();

                        if (id != null) {
                            // Apply the updated name, type, selected color, icon, and custom status
                            CategoryModel newCategory = new CategoryModel(name, "OUT", selectedColorHex[0], iconRes, isCustom);
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

    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }
}