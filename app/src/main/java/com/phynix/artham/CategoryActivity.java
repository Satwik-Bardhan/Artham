package com.phynix.artham;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.adapters.CategoryManagementAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class CategoryActivity extends AppCompatActivity {

    private RecyclerView categoriesRecyclerView;
    private CategoryManagementAdapter adapter;
    private List<CategoryModel> categoryList;

    private DatabaseReference userCategoriesRef;
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);

        // Uses the new layout containing the RecyclerView
        setContentView(R.layout.activity_category_management);

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        String uid = FirebaseAuth.getInstance().getUid();

        // Initialize Firebase Reference (Universal path for both IN and OUT)
        if (uid != null) {
            if (currentCashbookId != null && !currentCashbookId.isEmpty()) {
                // Specific cashbook categories
                userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(uid).child("cashbooks")
                        .child(currentCashbookId).child("categories");
            } else {
                // Fallback to user global categories
                userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(uid).child("categories");
            }
        }

        initViews();
        loadCategories();
    }

    private void initViews() {
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        ExtendedFloatingActionButton fabAddCategory = findViewById(R.id.addNewCategoryButton);
        ImageView backButton = findViewById(R.id.backButton);
        Button restoreButton = findViewById(R.id.restoreDefaultsButton);

        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        categoryList = new ArrayList<>();

        adapter = new CategoryManagementAdapter(this, categoryList, new CategoryManagementAdapter.OnCategoryActionListener() {
            @Override
            public void onDelete(CategoryModel category) {
                if (userCategoriesRef != null && category.getName() != null) {
                    userCategoriesRef.child(category.getName()).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(CategoryActivity.this, "Category Deleted", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(CategoryActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onEdit(CategoryModel category) {
                // Navigate to CreateCategoryActivity to handle the edit process
                Intent intent = new Intent(CategoryActivity.this, CreateCategoryActivity.class);
                intent.putExtra("EDIT_NAME", category.getName());
                intent.putExtra("cashbook_id", currentCashbookId);
                startActivity(intent);
            }
        });

        categoriesRecyclerView.setAdapter(adapter);

        // Open Creation Page
        if (fabAddCategory != null) {
            fabAddCategory.setOnClickListener(v -> {
                Intent intent = new Intent(CategoryActivity.this, CreateCategoryActivity.class);
                intent.putExtra("cashbook_id", currentCashbookId);
                startActivity(intent);
            });
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Restore Defaults
        if (restoreButton != null) {
            restoreButton.setOnClickListener(v -> restoreDefaultCategories());
        }
    }

    private void loadCategories() {
        if (userCategoriesRef == null) return;

        userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryList.clear();

                // If there are no categories yet, load defaults automatically
                if (!snapshot.exists()) {
                    restoreDefaultCategories();
                    return;
                }

                for (DataSnapshot s : snapshot.getChildren()) {
                    CategoryModel c = s.getValue(CategoryModel.class);
                    if (c != null) {
                        // Ensure we use the Firebase key as the ID just in case
                        c.setId(s.getKey());
                        categoryList.add(c);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CategoryActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void restoreDefaultCategories() {
        if (userCategoriesRef == null) return;

        // Universal list of Default Categories
        String[] defaults = {
                "Food & Dining", "Transport", "Bills & Utility",
                "Salary", "Shopping", "Entertainment", "Health",
                "Education", "Personal", "Other"
        };

        for (String catName : defaults) {
            // Use the convenience constructor for default categories (name, type)
            CategoryModel defaultCat = new CategoryModel(catName, "UNIVERSAL");
            defaultCat.setId(catName); // Set ID as the name for easy lookup
            userCategoriesRef.child(catName).setValue(defaultCat);
        }

        Toast.makeText(this, "Default Categories Configured", Toast.LENGTH_SHORT).show();
    }
}