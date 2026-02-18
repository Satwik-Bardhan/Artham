package com.phynix.artham;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.phynix.artham.adapters.CategoryManagementAdapter;
import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;
import java.util.ArrayList;
import java.util.List;

public class CategoryManagementActivity extends AppCompatActivity {

    private RecyclerView categoriesRecyclerView;
    private CategoryManagementAdapter adapter;
    private List<CategoryModel> categoryList;
    private DataRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);

        initViews();
        loadData();
    }

    private void initViews() {
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        ExtendedFloatingActionButton fab = findViewById(R.id.addNewCategoryButton);
        ImageView backButton = findViewById(R.id.backButton);

        repository = DataRepository.getInstance(getApplication());

        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        categoryList = new ArrayList<>();

        adapter = new CategoryManagementAdapter(this, categoryList, new CategoryManagementAdapter.OnCategoryActionListener() {
            @Override
            public void onDelete(CategoryModel category) {
                categoryList.remove(category);
                adapter.notifyDataSetChanged();
                Toast.makeText(CategoryManagementActivity.this, "Deleted: " + category.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onEdit(CategoryModel category) {
                // Reuse Create Activity for Editing
                Intent intent = new Intent(CategoryManagementActivity.this, CreateCategoryActivity.class);
                intent.putExtra("EDIT_NAME", category.getName());
                startActivity(intent);
            }
        });

        categoriesRecyclerView.setAdapter(adapter);

        fab.setOnClickListener(v -> startActivity(new Intent(this, CreateCategoryActivity.class)));
        backButton.setOnClickListener(v -> finish());
    }

    private void loadData() {
        categoryList.clear();
        // [UPDATE] Using "UNIVERSAL" type so categories work for BOTH Income and Expense
        categoryList.add(new CategoryModel("Food & Dining", "UNIVERSAL"));
        categoryList.add(new CategoryModel("Transport", "UNIVERSAL"));
        categoryList.add(new CategoryModel("Bills & Utility", "UNIVERSAL"));
        categoryList.add(new CategoryModel("Salary", "UNIVERSAL"));
        categoryList.add(new CategoryModel("Entertainment", "UNIVERSAL"));
        categoryList.add(new CategoryModel("Health", "UNIVERSAL"));

        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // In a real app, re-fetch from DB here
    }
}