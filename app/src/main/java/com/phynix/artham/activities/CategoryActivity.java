package com.phynix.artham.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.R;
import com.phynix.artham.adapters.ManageCategoryAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoryActivity extends AppCompatActivity implements ManageCategoryAdapter.OnCategoryActionClickListener {

    private RecyclerView categoriesRecyclerView;
    private FloatingActionButton fabAdd;
    private ImageView backButton;

    private ManageCategoryAdapter adapter;
    private List<CategoryModel> categoryList;
    private DatabaseReference categoryRef;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Theme initialization applied before super.onCreate
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            categoryRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(userId).child("categories");
        }

        initViews();
        setupRecyclerView();
        loadCategories();
    }

    private void initViews() {
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> onBackPressed());

        fabAdd.setOnClickListener(v -> {
            // Navigate to Create Category Screen
            Intent intent = new Intent(CategoryActivity.this, CreateCategoryActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        categoryList = new ArrayList<>();
        adapter = new ManageCategoryAdapter(categoryList, this);

        // UPDATED: Using GridLayoutManager to display 2 columns perfectly matching the XML
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        categoriesRecyclerView.setAdapter(adapter);
    }

    private void loadCategories() {
        if (categoryRef == null) return;

        categoryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryList.clear();
                List<CategoryModel> defaultCats = new ArrayList<>();
                List<CategoryModel> customCats = new ArrayList<>();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    CategoryModel category = dataSnapshot.getValue(CategoryModel.class);
                    if (category != null) {
                        category.setId(dataSnapshot.getKey());
                        // Requirement #4: Separate Default and Custom
                        if (category.isCustom()) {
                            customCats.add(category);
                        } else {
                            defaultCats.add(category);
                        }
                    }
                }

                // Sort alphabetically within their groups
                Collections.sort(defaultCats, (c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
                Collections.sort(customCats, (c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));

                // Combine: Defaults first, then User-Created
                categoryList.addAll(defaultCats);
                categoryList.addAll(customCats);

                adapter.updateData(categoryList);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CategoryActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMenuClick(CategoryModel category, View anchorView) {
        // Only Custom categories will trigger this due to adapter logic (Req #4)
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.menu_category_actions, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit) {
                // Navigate to edit screen (pass category ID)
                Intent intent = new Intent(CategoryActivity.this, CreateCategoryActivity.class);
                intent.putExtra("CATEGORY_ID", category.getId());
                startActivity(intent);
                return true;
            } else if (itemId == R.id.action_delete) {
                showDeleteConfirmationDialog(category);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    public void onCategoryClick(CategoryModel category) {
        // Optional: Do something when the whole category row is clicked
    }

    private void showDeleteConfirmationDialog(CategoryModel category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete '" + category.getName() + "'? Transactions using this category will not be deleted, but may show as 'Other'.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (categoryRef != null && category.getId() != null) {
                        categoryRef.child(category.getId()).removeValue()
                                .addOnSuccessListener(aVoid -> Toast.makeText(CategoryActivity.this, "Category deleted", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(CategoryActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}