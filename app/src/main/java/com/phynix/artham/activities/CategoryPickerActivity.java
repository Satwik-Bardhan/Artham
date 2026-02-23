package com.phynix.artham.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.phynix.artham.R;
import com.phynix.artham.adapters.CategoryPickerAdapter;
import com.phynix.artham.dialogs.QuickAddCategoryDialog;
import com.phynix.artham.models.CategoryModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoryPickerActivity extends AppCompatActivity {

    private ImageView backButton;
    private EditText searchEditText;
    private View noCategoryClickable;
    private RadioButton radioNoCategory;
    private TextView categoryCount;
    private RecyclerView categoriesRecyclerView;
    private ExtendedFloatingActionButton addNewCategoryButton;

    private DatabaseReference categoryRef;
    private CategoryPickerAdapter adapter;
    private List<CategoryModel> fullCategoryList;
    private List<CategoryModel> filteredCategoryList;

    private String selectedCategoryName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Note: activity_choose_category.xml was renamed to activity_category_picker.xml
        setContentView(R.layout.activity_category_picker);

        if (getIntent() != null && getIntent().hasExtra("SELECTED_CATEGORY")) {
            selectedCategoryName = getIntent().getStringExtra("SELECTED_CATEGORY");
        }

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            categoryRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(userId).child("categories");
        }

        initViews();
        setupRecyclerView();
        setupSearch();
        setupListeners();
        loadCategories();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        searchEditText = findViewById(R.id.searchEditText);
        noCategoryClickable = findViewById(R.id.noCategoryClickable);
        radioNoCategory = findViewById(R.id.radioNoCategory);
        categoryCount = findViewById(R.id.categoryCount);
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        addNewCategoryButton = findViewById(R.id.addNewCategoryButton);

        if (selectedCategoryName.isEmpty() || selectedCategoryName.equals("No Category")) {
            radioNoCategory.setChecked(true);
        }
    }

    private void setupRecyclerView() {
        fullCategoryList = new ArrayList<>();
        filteredCategoryList = new ArrayList<>();

        adapter = new CategoryPickerAdapter(filteredCategoryList, selectedCategoryName, category -> {
            radioNoCategory.setChecked(false);

            // Return the selected category back to the calling activity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("CATEGORY_NAME", category.getName());
            resultIntent.putExtra("CATEGORY_COLOR", category.getColorHex());
            resultIntent.putExtra("CATEGORY_ICON_RES", category.getIconResId());
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Use the 2-span GridLayoutManager exactly as defined in your XML
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        categoriesRecyclerView.setAdapter(adapter);
    }

    private void loadCategories() {
        if (categoryRef == null) return;

        categoryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fullCategoryList.clear();
                List<CategoryModel> defaultCats = new ArrayList<>();
                List<CategoryModel> customCats = new ArrayList<>();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    CategoryModel category = dataSnapshot.getValue(CategoryModel.class);
                    if (category != null) {
                        if (category.isCustom()) {
                            customCats.add(category);
                        } else {
                            defaultCats.add(category);
                        }
                    }
                }

                Collections.sort(defaultCats, (c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
                Collections.sort(customCats, (c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));

                fullCategoryList.addAll(defaultCats);
                fullCategoryList.addAll(customCats);

                categoryCount.setText("All Categories (" + fullCategoryList.size() + ")");
                filterList("");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CategoryPickerActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterList(String query) {
        filteredCategoryList.clear();
        if (query.trim().isEmpty()) {
            filteredCategoryList.addAll(fullCategoryList);
        } else {
            for (CategoryModel model : fullCategoryList) {
                if (model.getName().toLowerCase().contains(query.toLowerCase().trim())) {
                    filteredCategoryList.add(model);
                }
            }
        }
        adapter.updateList(filteredCategoryList);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> onBackPressed());

        noCategoryClickable.setOnClickListener(v -> {
            radioNoCategory.setChecked(true);
            Intent resultIntent = new Intent();
            resultIntent.putExtra("CATEGORY_NAME", "Other");
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        addNewCategoryButton.setOnClickListener(v -> {
            // Open the Quick Add Dialog we created previously
            QuickAddCategoryDialog dialog = new QuickAddCategoryDialog(this, newCategory -> {
                // When a user successfully creates a new category, automatically select it!
                Intent resultIntent = new Intent();
                resultIntent.putExtra("CATEGORY_NAME", newCategory.getName());
                resultIntent.putExtra("CATEGORY_COLOR", newCategory.getColorHex());
                resultIntent.putExtra("CATEGORY_ICON_RES", newCategory.getIconResId());
                setResult(RESULT_OK, resultIntent);
                finish();
            });
            dialog.show();
        });
    }
}