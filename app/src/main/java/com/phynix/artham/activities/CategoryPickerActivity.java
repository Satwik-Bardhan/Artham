package com.phynix.artham.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.phynix.artham.adapters.CategoryPickerAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategorySeeder;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.phynix.artham.BaseActivity;

public class CategoryPickerActivity extends BaseActivity {

    // UI Elements
    private RecyclerView defaultRecyclerView;
    private RecyclerView customRecyclerView;
    private LinearLayout customCategoriesLayout;

    // Adapters and Lists for Section 1 (Defaults)
    private CategoryPickerAdapter defaultAdapter;
    private List<CategoryModel> defaultFullList = new ArrayList<>();
    private List<CategoryModel> defaultFilteredList = new ArrayList<>();

    // Adapters and Lists for Section 2 (User Created)
    private CategoryPickerAdapter customAdapter;
    private List<CategoryModel> customFullList = new ArrayList<>();
    private List<CategoryModel> customFilteredList = new ArrayList<>();

    private DatabaseReference dbRef;
    private String currentCashbookId;
    private String transactionType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_picker);

        currentCashbookId = getIntent().getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        transactionType = getIntent().getStringExtra("type");

        initViews();
        setupFirebase();
        setupSearch();
    }

    private void initViews() {
        defaultRecyclerView = findViewById(R.id.defaultRecyclerView);
        customRecyclerView = findViewById(R.id.customRecyclerView);
        customCategoriesLayout = findViewById(R.id.customCategoriesLayout);

        defaultRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        customRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        // Shared Listener for both lists
        CategoryPickerAdapter.OnCategoryPickedListener selectionListener = category -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("category_name", category.getName());
            resultIntent.putExtra("category_color", category.getColorHex());
            resultIntent.putExtra("category_icon_res", category.getIconResId());
            setResult(RESULT_OK, resultIntent);
            finish();
        };

        // Initialize both adapters
        defaultAdapter = new CategoryPickerAdapter(defaultFilteredList, "", selectionListener);
        customAdapter = new CategoryPickerAdapter(customFilteredList, "", selectionListener);

        defaultRecyclerView.setAdapter(defaultAdapter);
        customRecyclerView.setAdapter(customAdapter);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        findViewById(R.id.addNewCategoryButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateCategoryActivity.class);
            intent.putExtra(Constants.EXTRA_CASHBOOK_ID, currentCashbookId);
            startActivity(intent);
        });

        findViewById(R.id.noCategoryClickable).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra("category_name", "No Category");
            intent.putExtra("category_color", "#9E9E9E");
            intent.putExtra("category_icon_res", R.drawable.ic_category);
            setResult(RESULT_OK, intent);
            finish();
        });
    }

    private void setupFirebase() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        dbRef = FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("categories");

        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                defaultFullList.clear();
                customFullList.clear();
                boolean hasDefaults = false;

                if (snapshot.exists() && snapshot.hasChildren()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        CategoryModel model = ds.getValue(CategoryModel.class);
                        if (model != null) {
                            model.setId(ds.getKey());

                            if (!model.isCustom()) {
                                hasDefaults = true;
                            }

                            if (model.isCustom()) {
                                customFullList.add(model);
                            } else {
                                defaultFullList.add(model);
                            }
                        }
                    }
                }

                // If no defaults exist, tell the Seeder to make them.
                // We DO NOT 'return;' here anymore, so the UI can still update immediately!
                if (!hasDefaults) {
                    CategorySeeder.seedDefaultCategories();
                }

                // Sort lists alphabetically
                Collections.sort(defaultFullList, (c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
                Collections.sort(customFullList, (c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));

                updateLists("");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CategoryPickerActivity.this, "Error loading categories", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearch() {
        EditText searchBox = findViewById(R.id.searchEditText);
        if (searchBox != null) {
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateLists(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void updateLists(String query) {
        defaultFilteredList.clear();
        for (CategoryModel item : defaultFullList) {
            if (item.getName().toLowerCase().contains(query.toLowerCase().trim())) {
                defaultFilteredList.add(item);
            }
        }
        defaultAdapter.updateList(new ArrayList<>(defaultFilteredList));

        customFilteredList.clear();
        for (CategoryModel item : customFullList) {
            if (item.getName().toLowerCase().contains(query.toLowerCase().trim())) {
                customFilteredList.add(item);
            }
        }
        customAdapter.updateList(new ArrayList<>(customFilteredList));

        // Hide the "Created Categories" section if it's empty
        if (customFilteredList.isEmpty()) {
            customCategoriesLayout.setVisibility(View.GONE);
        } else {
            customCategoriesLayout.setVisibility(View.VISIBLE);
        }
    }
}