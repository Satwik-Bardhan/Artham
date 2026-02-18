package com.phynix.artham;

import android.os.Bundle;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.adapters.CategoryAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CategoryActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener, CategoryAdapter.OnCategoryActionListener {

    private RecyclerView categoriesRecyclerView;
    private CategoryAdapter adapter;
    private List<CategoryModel> categoryList;
    private DatabaseReference userCategoriesRef;
    private String currentCashbookId;
    private String transactionType = "OUT"; // Default to Expense

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);

        initViews();
        setupFirebase();
        loadData();
    }

    private void initViews() {
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        ExtendedFloatingActionButton fab = findViewById(R.id.addNewCategoryButton);
        ImageView backButton = findViewById(R.id.backButton);

        // Grid Layout for better visualization (2 columns)
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        categoryList = new ArrayList<>();
        adapter = new CategoryAdapter(categoryList, this, this, this);
        categoriesRecyclerView.setAdapter(adapter);

        if (fab != null) {
            fab.setOnClickListener(v -> showAddDialog(null));
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    private void setupFirebase() {
        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        if (getIntent().hasExtra("type")) {
            transactionType = getIntent().getStringExtra("type");
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && currentCashbookId != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(uid).child("cashbooks")
                    .child(currentCashbookId).child("categories").child(transactionType);
        }
    }

    private void loadData() {
        if (userCategoriesRef == null) return;

        userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryList.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    CategoryModel c = s.getValue(CategoryModel.class);
                    if (c != null) {
                        c.setId(s.getKey());
                        categoryList.add(c);
                    }
                }

                // If list is empty, offer to add defaults
                if (categoryList.isEmpty()) {
                    addDefaultCategories();
                } else {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CategoryActivity.this, "Failed to load categories", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addDefaultCategories() {
        saveCategoryToFirebase("Food & Dining", "#FF5733", R.drawable.ic_food_dining, false);
        saveCategoryToFirebase("Transport", "#33C1FF", R.drawable.ic_transportation, false);
        saveCategoryToFirebase("Bills & Utility", "#FFC300", R.drawable.ic_utilities, false);
        saveCategoryToFirebase("Shopping", "#DAF7A6", R.drawable.ic_receipt_long, false);
        saveCategoryToFirebase("Health", "#C70039", R.drawable.ic_medicine, false);
    }

    private void showAddDialog(CategoryModel category) {
        boolean isEdit = (category != null);

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
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        container.setPadding(margin, margin, margin, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Category" : "New Category")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        // If editing and name changed, delete old one first
                        if (isEdit && !category.getName().equals(name)) {
                            if (userCategoriesRef != null) userCategoriesRef.child(category.getName()).removeValue();
                        }

                        String color = isEdit ? category.getColorHex() : getRandomColor();
                        int icon = isEdit ? category.getIconResId() : R.drawable.ic_category;

                        // Pass 'true' for custom if it's user-added
                        saveCategoryToFirebase(name, color, icon, true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveCategoryToFirebase(String name, String colorHex, int iconResId, boolean isCustom) {
        if (userCategoriesRef == null) return;

        CategoryModel newCategory = new CategoryModel(
                name,
                transactionType,
                colorHex,
                iconResId,
                isCustom
        );
        userCategoriesRef.child(name).setValue(newCategory);
    }

    @Override
    public void onCategoryClick(CategoryModel category) {
        // Handle click
    }

    @Override
    public void onEditCategory(CategoryModel category) {
        showAddDialog(category);
    }

    @Override
    public void onDeleteCategory(CategoryModel category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete " + category.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (userCategoriesRef != null) {
                        userCategoriesRef.child(category.getName()).removeValue();
                        Toast.makeText(this, "Deleted: " + category.getName(), Toast.LENGTH_SHORT).show();
                    }
                })
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
