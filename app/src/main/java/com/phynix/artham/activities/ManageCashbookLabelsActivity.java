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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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

public class ManageCashbookLabelsActivity extends BaseActivity implements CashbookLabelsAdapter.OnCategoryClickListener {

    private static final String TAG = "ManageCashbookCats";

    private RecyclerView categoriesRecyclerView;
    private EditText searchEditText;
    private View emptyStateLayout;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAdd;

    private DatabaseReference userCategoriesRef;
    private DatabaseReference userCashbooksRef;
    private ValueEventListener categoriesListener;
    private ValueEventListener cashbooksListener;

    private final List<String> allCategories = new ArrayList<>();
    private final List<String> filteredCategories = new ArrayList<>();
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private CashbookLabelsAdapter adapter;

    private FirebaseUser currentUser;
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_cashbook_labels);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Not authenticated. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = currentUser.getUid();
        userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("cashbookCategories");
        userCashbooksRef = FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("cashbooks");

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

    private void setupFirebaseListeners() {
        categoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allCategories.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    String catName = snap.getKey();
                    if (catName != null && !catName.trim().isEmpty()) {
                        allCategories.add(catName);
                    }
                }
                filterCategories(currentSearchQuery);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load labels", error.toException());
                Toast.makeText(ManageCashbookLabelsActivity.this, "Failed to load labels", Toast.LENGTH_SHORT).show();
            }
        };

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allCashbooks.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    CashbookModel cashbook = snap.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        allCashbooks.add(cashbook);
                    }
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load cashbooks", error.toException());
            }
        };

        userCategoriesRef.addValueEventListener(categoriesListener);
        userCashbooksRef.addValueEventListener(cashbooksListener);
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
        userCategoriesRef.child(name).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Label '" + name + "' created!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create label: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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

            TextView dialogDescription = dialogView.findViewById(R.id.dialogTitle).getRootView().findViewWithTag("desc");
            // If the layout has a description text view below title, let's update it or just let the default serve.
            // Alternatively, we can locate the TextView by content or index, but the TextInputLayout is standard.

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
        String uid = currentUser.getUid();
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        Map<String, Object> updates = new HashMap<>();

        // Remove old category from index, write new one
        updates.put("users/" + uid + "/cashbookCategories/" + oldName, null);
        updates.put("users/" + uid + "/cashbookCategories/" + newName, true);

        // Cascade rename to all associated cashbooks (case-insensitive)
        int updatedCount = 0;
        for (CashbookModel cb : allCashbooks) {
            if (cb.getCategory() != null && oldName.equalsIgnoreCase(cb.getCategory())) {
                updates.put("users/" + uid + "/cashbooks/" + cb.getCashbookId() + "/category", newName);
                updatedCount++;
            }
        }

        final int finalUpdatedCount = updatedCount;
        rootRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String msg = "Label renamed to '" + newName + "'";
                    if (finalUpdatedCount > 0) {
                        msg += " across " + finalUpdatedCount + " cashbooks";
                    }
                    Toast.makeText(ManageCashbookLabelsActivity.this, msg, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ManageCashbookLabelsActivity.this, "Failed to rename label: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
        String uid = currentUser.getUid();
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        Map<String, Object> updates = new HashMap<>();

        // Remove category node
        updates.put("users/" + uid + "/cashbookCategories/" + categoryName, null);

        // Safe clear of category tag in cashbooks (sets to "", leaving cashbooks otherwise untouched and unharmed)
        int updatedCount = 0;
        for (CashbookModel cb : allCashbooks) {
            if (cb.getCategory() != null && categoryName.equalsIgnoreCase(cb.getCategory())) {
                updates.put("users/" + uid + "/cashbooks/" + cb.getCashbookId() + "/category", "");
                updatedCount++;
            }
        }

        final int finalUpdatedCount = updatedCount;
        rootRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String msg = "Label deleted successfully";
                    if (finalUpdatedCount > 0) {
                        msg += " (removed from " + finalUpdatedCount + " cashbooks)";
                    }
                    Toast.makeText(ManageCashbookLabelsActivity.this, msg, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ManageCashbookLabelsActivity.this, "Failed to delete label: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (categoriesListener != null && userCategoriesRef != null) {
            userCategoriesRef.removeEventListener(categoriesListener);
        }
        if (cashbooksListener != null && userCashbooksRef != null) {
            userCashbooksRef.removeEventListener(cashbooksListener);
        }
    }
}
