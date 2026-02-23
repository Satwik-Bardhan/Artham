package com.phynix.artham.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
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
import com.phynix.artham.adapters.CategoryAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class ChooseCategoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CategoryAdapter adapter;
    private List<CategoryModel> fullList = new ArrayList<>();
    private List<CategoryModel> filteredList = new ArrayList<>();

    private DatabaseReference dbRef;
    private String currentCashbookId;
    private String transactionType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_picker);

        // Get data from Intent
        currentCashbookId = getIntent().getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        transactionType = getIntent().getStringExtra("type");

        if (transactionType == null) transactionType = Constants.TRANSACTION_TYPE_OUT;

        initViews();
        setupFirebase();
        setupSearch();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.categoriesRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new CategoryAdapter(filteredList, this, category -> {
            // Return selected category to CashInOutActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("category_name", category.getName());
            resultIntent.putExtra("category_color", category.getColorHex());
            setResult(RESULT_OK, resultIntent);
            finish();
        }, null);

        recyclerView.setAdapter(adapter);

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
            setResult(RESULT_OK, intent);
            finish();
        });
    }

    private void setupFirebase() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // FIXED: Point to a single shared "all_categories" folder for BOTH In and Out
        dbRef = FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("cashbooks")
                .child(currentCashbookId).child("all_categories");

        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Scenario: Database is empty. Add universal defaults.
                    addDefaultCategories();
                } else {
                    fullList.clear();
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        CategoryModel model = ds.getValue(CategoryModel.class);
                        if (model != null) fullList.add(model);
                    }
                    updateList("");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseCategoryActivity.this, "Error loading categories", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addDefaultCategories() {
        // Standard categories to help the user get started
        String[][] defaults = {
                {"Food", "#FF5722"},    // Orange
                {"Shopping", "#E91E63"}, // Pink
                {"Travel", "#2196F3"},   // Blue
                {"Bills", "#9C27B0"},    // Purple
                {"Health", "#4CAF50"},   // Green
                {"Salary", "#009688"},   // Teal
                {"Business", "#3F51B5"}, // Indigo
                {"Other", "#9E9E9E"}     // Grey
        };

        for (String[] cat : defaults) {
            // Save them all as UNIVERSAL so they show up everywhere
// We add R.drawable.ic_category at the end!
            CategoryModel model = new CategoryModel(cat[0], cat[1], "UNIVERSAL", R.drawable.ic_category);
            dbRef.child(cat[0]).setValue(model);
        }
    }

    private void setupSearch() {
        EditText searchBox = findViewById(R.id.searchEditText);
        if (searchBox != null) {
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateList(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
    }

    private void updateList(String query) {
        filteredList.clear();
        for (CategoryModel item : fullList) {
            if (item.getName().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }
}