package com.phynix.artham.activities;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.phynix.artham.auth.AuthManager;


import com.phynix.artham.BaseActivity;
import com.phynix.artham.R;
import com.phynix.artham.adapters.CashbookLabelsAdapter;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.utils.ThemeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import com.phynix.artham.db.DataRepository;

public class ManageCashbookLabelsActivity extends BaseActivity implements CashbookLabelsAdapter.OnCategoryClickListener {

    private static final String TAG = "ManageCashbookCats";

    private RecyclerView categoriesRecyclerView;
    private EditText searchEditText;
    private View emptyStateLayout;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAdd;



    private final List<String> allCategories = new ArrayList<>();
    private final List<String> filteredCategories = new ArrayList<>();
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private CashbookLabelsAdapter adapter;


    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_cashbook_labels);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (!AuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Not authenticated. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = AuthManager.getUserId(this);

        initViews();
        setupRecyclerView();
        setupSearchListener();
        setupFirebaseListeners();
    }

    private void initViews() {
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        searchEditText = findViewById(R.id.searchEditText);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        fabAdd = findViewById(R.id.fabAdd);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        fabAdd.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void setupRecyclerView() {
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CashbookLabelsAdapter(filteredCategories, allCashbooks, this);
        categoriesRecyclerView.setAdapter(adapter);
    }

    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                filterCategories(currentSearchQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void setupFirebaseListeners() {
        loadData();
    }

    private void loadData() {
        DataRepository repo = DataRepository.getInstance(getApplication());
        repo.getCashbooks(cashbooks -> {
            allCashbooks.clear();
            if (cashbooks != null) {
                allCashbooks.addAll(cashbooks);
            }

            Set<String> uniqueCategories = new LinkedHashSet<>();
            for (CashbookModel cb : allCashbooks) {
                if (cb.getCategory() != null && !cb.getCategory().trim().isEmpty()) {
                    uniqueCategories.add(cb.getCategory());
                }
            }

            allCategories.clear();
            allCategories.addAll(uniqueCategories);
            filterCategories(currentSearchQuery);
        }, null);
    }

    private void filterCategories(String query) {
        filteredCategories.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredCategories.addAll(allCategories);
        } else {
            String lowerQuery = query.trim().toLowerCase(Locale.getDefault());
            for (String category : allCategories) {
                if (category.toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                    filteredCategories.add(category);
                }
            }
        }
        if (adapter != null) {
            adapter.updateData(filteredCategories, allCashbooks);
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredCategories.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            categoriesRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            categoriesRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showAddCategoryDialog() {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_cashbook_category, null);

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
            dialogTitle.setText("New Label");

            TextInputEditText categoryNameInput = dialogView.findViewById(R.id.categoryNameInput);
            Button btnSave = dialogView.findViewById(R.id.btnSave);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                String name = categoryNameInput.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a label name", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate: reject illegal characters
                if (com.phynix.artham.utils.NameValidationUtils.containsIllegalChars(name)) {
                    Toast.makeText(this, com.phynix.artham.utils.NameValidationUtils.getIllegalCharsMessage(), Toast.LENGTH_LONG).show();
                    return;
                }

                // Check for duplicates (case-insensitive)
                for (String cat : allCategories) {
                    if (cat.equalsIgnoreCase(name)) {
                        Toast.makeText(this, "Label '" + name + "' already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                saveCategoryToFirebase(name);
                dialog.dismiss();
            });

            dialog.show();

            // Set layout dimensions beautifully after showing the dialog
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing add category dialog", e);
        }
    }

    private void saveCategoryToFirebase(String name) {
        if (name == null || name.trim().isEmpty()) return;
        String trimmed = name.trim();
        if (!allCategories.contains(trimmed)) {
            allCategories.add(trimmed);
            filterCategories(currentSearchQuery);
            Toast.makeText(this, "Label '" + trimmed + "' created", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRenameClick(String categoryName) {
        showRenameCategoryDialog(categoryName);
    }

    @Override
    public void onDeleteClick(String categoryName) {
        showDeleteCategoryDialog(categoryName);
    }

    private void showRenameCategoryDialog(String oldName) {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_cashbook_category, null);

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
            dialogTitle.setText("Rename Label");

            TextInputEditText categoryNameInput = dialogView.findViewById(R.id.categoryNameInput);
            categoryNameInput.setText(oldName);
            // Pre-select all text for ease of renaming
            categoryNameInput.setSelection(oldName.length());

            Button btnSave = dialogView.findViewById(R.id.btnSave);
            btnSave.setText("Rename");

            Button btnCancel = dialogView.findViewById(R.id.btnCancel);

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                String newName = categoryNameInput.getText().toString().trim();

                if (newName.isEmpty()) {
                    Toast.makeText(this, "Please enter a label name", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate: reject illegal characters
                if (com.phynix.artham.utils.NameValidationUtils.containsIllegalChars(newName)) {
                    Toast.makeText(this, com.phynix.artham.utils.NameValidationUtils.getIllegalCharsMessage(), Toast.LENGTH_LONG).show();
                    return;
                }

                if (newName.equalsIgnoreCase(oldName)) {
                    dialog.dismiss(); // No change
                    return;
                }

                // Check for duplicates (case-insensitive) other than the old name itself
                for (String cat : allCategories) {
                    if (cat.equalsIgnoreCase(newName) && !cat.equalsIgnoreCase(oldName)) {
                        Toast.makeText(this, "Label '" + newName + "' already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                renameCategoryInFirebase(oldName, newName);
                dialog.dismiss();
            });

            dialog.show();

            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing rename category dialog", e);
        }
    }

    private void renameCategoryInFirebase(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        DataRepository repo = DataRepository.getInstance(getApplication());
        for (CashbookModel cb : allCashbooks) {
            if (oldName.equalsIgnoreCase(cb.getCategory())) {
                cb.setCategory(newName);
                repo.updateCashbook(cb, null);
            }
        }
        int idx = allCategories.indexOf(oldName);
        if (idx != -1) {
            allCategories.set(idx, newName);
        }
        filterCategories(currentSearchQuery);
        Toast.makeText(this, "Label renamed to '" + newName + "'", Toast.LENGTH_SHORT).show();
    }

    private void showDeleteCategoryDialog(String categoryName) {
        // Count associated cashbooks to warn the user accurately
        int count = 0;
        for (CashbookModel cb : allCashbooks) {
            if (cb.getCategory() != null && categoryName.equalsIgnoreCase(cb.getCategory())) {
                count++;
            }
        }

        String warningMessage = "Are you sure you want to delete the label '" + categoryName + "'?";
        if (count > 0) {
            warningMessage += "\n\n" + count + " cashbooks are currently using this label. "
                    + "Deleting the label will clear it from these cashbooks, but they will NOT be harmed or deleted. "
                    + "They will function normally as before.";
        } else {
            warningMessage += "\n\nThis action cannot be undone.";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Label")
                .setMessage(warningMessage)
                .setPositiveButton("Delete", (dialog, which) -> deleteCategoryFromFirebase(categoryName))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategoryFromFirebase(String categoryName) {
        if (categoryName == null) return;
        DataRepository repo = DataRepository.getInstance(getApplication());
        for (CashbookModel cb : allCashbooks) {
            if (categoryName.equalsIgnoreCase(cb.getCategory())) {
                cb.setCategory("");
                repo.updateCashbook(cb, null);
            }
        }
        allCategories.remove(categoryName);
        filterCategories(currentSearchQuery);
        Toast.makeText(this, "Label deleted", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
