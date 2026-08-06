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

import com.phynix.artham.auth.AuthManager;
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

    // private DatabaseReference dbRef;
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
            // Pass mapped category type so new categories get the correct type
            if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(transactionType)) {
                intent.putExtra("type", "Income");
            } else if (Constants.TRANSACTION_TYPE_OUT.equalsIgnoreCase(transactionType)) {
                intent.putExtra("type", "Expense");
            }
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

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
    }

    private void setupFirebase() {
        loadCategories();
    }

    private void loadCategories() {
        if (currentCashbookId == null || currentCashbookId.isEmpty()) {
            android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
            String uid = AuthManager.getUserId(this);
            currentCashbookId = prefs.getString("active_cashbook_id_" + uid, "");
        }

        // Map transaction type ("IN"/"OUT") to category type ("Income"/"Expense")
        String categoryTypeFilter = null;
        if (transactionType != null && !transactionType.isEmpty()) {
            if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(transactionType)) {
                categoryTypeFilter = "Income";
            } else if (Constants.TRANSACTION_TYPE_OUT.equalsIgnoreCase(transactionType)) {
                categoryTypeFilter = "Expense";
            }
        }
        final String filterType = categoryTypeFilter;

        com.phynix.artham.db.DataRepository.getInstance(getApplication()).getCategories(currentCashbookId, categories -> {
            defaultFullList.clear();
            customFullList.clear();
            if (categories != null) {
                for (CategoryModel c : categories) {
                    if (filterType != null && c.getType() != null) {
                        if (!c.getType().equalsIgnoreCase(filterType)) continue;
                    }
                    if (c.isCustom()) {
                        customFullList.add(c);
                    } else {
                        defaultFullList.add(c);
                    }
                }
            }
            updateLists("");
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