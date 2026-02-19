package com.phynix.artham.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
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
import java.util.List;

public class CategoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CategoryAdapter adapter;
    private List<CategoryModel> categoryList = new ArrayList<>();
    private DatabaseReference dbRef;
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        initViews();
        setupFirebase();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.categoriesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter with Management Mode enabled
        adapter = new CategoryAdapter(categoryList, this,
                category -> {
                    // On Click: Open editor
                    openEditor(category);
                },
                new CategoryAdapter.OnCategoryActionListener() {
                    @Override
                    public void onEditCategory(CategoryModel category) {
                        openEditor(category);
                    }

                    @Override
                    public void onDeleteCategory(CategoryModel category) {
                        showDeleteConfirmation(category);
                    }
                });

        adapter.setManagementMode(true);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.fabAdd).setOnClickListener(v -> openEditor(null));
    }

    private void setupFirebase() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || currentCashbookId == null) return;

        // Note: This fetches BOTH Income and Expense categories if you want a master list,
        // or you can specificy a type. Here we fetch the custom categories node.
        dbRef = FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("cashbooks")
                .child(currentCashbookId).child("categories");

        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryList.clear();
                // Iterate through "IN", "OUT", and "UNIVERSAL"
                for (DataSnapshot typeSnapshot : snapshot.getChildren()) {
                    for (DataSnapshot categorySnap : typeSnapshot.getChildren()) {
                        CategoryModel model = categorySnap.getValue(CategoryModel.class);
                        if (model != null) categoryList.add(model);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void openEditor(CategoryModel category) {
        Intent intent = new Intent(this, CreateCategoryActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        if (category != null) {
            intent.putExtra("EDIT_NAME", category.getName());
            intent.putExtra("type", category.getType());
        }
        startActivity(intent);
    }

    private void showDeleteConfirmation(CategoryModel category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete '" + category.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbRef.child(category.getType()).child(category.getName()).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}