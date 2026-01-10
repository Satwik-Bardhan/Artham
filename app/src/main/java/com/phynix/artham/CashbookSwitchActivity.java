package com.phynix.artham;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.phynix.artham.adapters.CashbookAdapter;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.ErrorHandler;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CashbookSwitchActivity extends AppCompatActivity {

    private static final String TAG = "CashbookSwitchActivity";

    // UI Components
    private RecyclerView cashbookRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;
    private View mainContent;

    // UI Components - Buttons
    private Button cancelButton;
    private Button addNewButton;
    private Button emptyStateCreateButton;
    private ImageView closeButton;

    // UI Components - Search & Filter
    private EditText searchEditText;
    private ChipGroup chipGroup;
    private TextView sortButton;

    // UI Components - FAB
    private FloatingActionButton quickAddFab;

    // [FIX] Removed stats textviews

    // Adapter & Data
    private CashbookAdapter cashbookAdapter;
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private String currentFilter = "active";
    private String currentSort = "recent";
    private String currentCashbookId;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference userCashbooksRef;
    private ValueEventListener cashbooksListener;
    private FirebaseUser currentUser;

    // State
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashbook_switch);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        currentCashbookId = getIntent().getStringExtra("current_cashbook_id");

        if (currentUser == null) {
            showSnackbar("Not authenticated. Please log in again.");
            Log.w(TAG, "No authenticated user");
            finish();
            return;
        }

        userCashbooksRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(currentUser.getUid()).child("cashbooks");

        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupSearchListener();
        setupFilterListener();
        loadCashbooks(); // Initial load

        Log.d(TAG, "CashbookSwitchActivity created");
    }

    private void initViews() {
        cashbookRecyclerView = findViewById(R.id.cashbookRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        mainContent = findViewById(R.id.mainCard);

        cancelButton = findViewById(R.id.cancelButton);
        addNewButton = findViewById(R.id.addNewButton);
        emptyStateCreateButton = findViewById(R.id.emptyStateCreateButton);
        closeButton = findViewById(R.id.closeButton);

        searchEditText = findViewById(R.id.searchEditText);
        chipGroup = findViewById(R.id.chipGroup);
        sortButton = findViewById(R.id.sortButton);

        quickAddFab = findViewById(R.id.quickAddFab);
        // [FIX] Removed findViewById calls for stats

        try {
            closeButton.setContentDescription(getString(R.string.close_button));
            addNewButton.setContentDescription(getString(R.string.add_cashbook_desc));
            quickAddFab.setContentDescription(getString(R.string.quick_add_cashbook));
            searchEditText.setHint(getString(R.string.search_cashbooks_hint));
        } catch (Exception e) {
            Log.w(TAG, "Error setting content descriptions", e);
        }

        Log.d(TAG, "Views initialized");
    }

    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        cashbookAdapter = new CashbookAdapter(this, new ArrayList<>(), new CashbookAdapter.OnCashbookClickListener() {
            @Override
            public void onCashbookClick(CashbookModel cashbook) {
                onCashbookSelected(cashbook);
            }

            @Override
            public void onFavoriteClick(CashbookModel cashbook) {
                handleFavoriteToggle(cashbook);
            }

            @Override
            public void onMenuClick(CashbookModel cashbook, View anchorView) {
                showCashbookOptions(cashbook, anchorView);
            }
        });

        cashbookRecyclerView.setAdapter(cashbookAdapter);
        swipeRefreshLayout.setOnRefreshListener(this::loadCashbooks);

        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary_blue,
                R.color.income_green,
                R.color.expense_red
        );

        Log.d(TAG, "RecyclerView setup complete");
    }

    private void setupClickListeners() {
        closeButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());
        addNewButton.setOnClickListener(v -> handleAddNewCashbook());
        emptyStateCreateButton.setOnClickListener(v -> handleAddNewCashbook());
        quickAddFab.setOnClickListener(v -> handleAddNewCashbook());
        sortButton.setOnClickListener(v -> showSortOptions());
        Log.d(TAG, "Click listeners setup complete");
    }

    private void setupFilterListener() {
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipAll) {
                    currentFilter = "all";
                } else if (checkedId == R.id.chipActive) {
                    currentFilter = "active";
                } else if (checkedId == R.id.chipRecent) {
                    currentFilter = "recent";
                } else if (checkedId == R.id.chipFavorites) {
                    currentFilter = "favorites";
                }
                applyFiltersAndSort();
            }
        });
        Log.d(TAG, "Filter listener setup complete");
    }

    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFiltersAndSort();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {}
        });
        Log.d(TAG, "Search listener setup complete");
    }

    private void loadCashbooks() {
        if (isLoading) return;
        if (currentUser == null) {
            showSnackbar("Not authenticated");
            return;
        }

        showLoading(true);
        Log.d(TAG, "Loading cashbooks for user: " + currentUser.getUid());

        if (cashbooksListener != null) {
            userCashbooksRef.removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allCashbooks.clear();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                        if (cashbook != null) {
                            cashbook.setCashbookId(snapshot.getKey());
                            cashbook.setCurrent(cashbook.getCashbookId().equals(currentCashbookId));

                            // Calculate stats from transactions
                            DataSnapshot transactionsSnapshot = snapshot.child("transactions");
                            calculateStatsForCashbook(cashbook, transactionsSnapshot);

                            allCashbooks.add(cashbook);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing cashbook: " + snapshot.getKey(), e);
                    }
                }

                Log.d(TAG, "Loaded " + allCashbooks.size() + " cashbooks");
                // [FIX] Removed updateStats call
                applyFiltersAndSort();
                showLoading(false);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                swipeRefreshLayout.setRefreshing(false);
                Log.e(TAG, "Error loading cashbooks", error.toException());
                ErrorHandler.handleFirebaseError(CashbookSwitchActivity.this, error);
            }
        };
        userCashbooksRef.addValueEventListener(cashbooksListener);
    }

    private void calculateStatsForCashbook(CashbookModel cashbook, DataSnapshot transactionsSnapshot) {
        double totalIncome = 0;
        double totalExpense = 0;
        int count = 0;

        for (DataSnapshot txnSnapshot : transactionsSnapshot.getChildren()) {
            try {
                TransactionModel transaction = txnSnapshot.getValue(TransactionModel.class);
                if (transaction != null) {
                    count++;
                    if ("IN".equalsIgnoreCase(transaction.getType())) {
                        totalIncome += transaction.getAmount();
                    } else {
                        totalExpense += transaction.getAmount();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing transaction for stats", e);
            }
        }

        cashbook.setTotalBalance(totalIncome - totalExpense);
        cashbook.setTransactionCount(count);
    }

    private void handleAddNewCashbook() {
        showCreateCashbookDialog(null);
        Log.d(TAG, "Add new cashbook clicked");
    }

    private void showCreateCashbookDialog(@Nullable CashbookModel cashbookToEdit) {
        try {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_cashbook, null);
            EditText nameInput = dialogView.findViewById(R.id.cashbookNameInput);
            EditText descInput = dialogView.findViewById(R.id.cashbookDescInput);

            String title = (cashbookToEdit == null) ? "Create New Cashbook" : "Edit Cashbook";
            String positiveButton = (cashbookToEdit == null) ? "Create" : "Update";

            if (cashbookToEdit != null) {
                nameInput.setText(cashbookToEdit.getName());
                descInput.setText(cashbookToEdit.getDescription());
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setView(dialogView)
                    .setPositiveButton(positiveButton, (dialog, which) -> {
                        String name = nameInput.getText().toString().trim();
                        String description = descInput.getText().toString().trim();

                        if (name.isEmpty()) {
                            showSnackbar("Please enter a cashbook name");
                            return;
                        }

                        if (cashbookToEdit == null) {
                            createNewCashbook(name, description);
                        } else {
                            updateCashbook(cashbookToEdit, name, description);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing create/edit cashbook dialog", e);
            showSnackbar("Error creating dialog");
        }
    }

    private void createNewCashbook(String name, String description) {
        if (currentUser == null) {
            showSnackbar("Not authenticated");
            return;
        }

        String cashbookId = userCashbooksRef.push().getKey();
        if (cashbookId == null) {
            showSnackbar("Error generating cashbook ID");
            return;
        }

        CashbookModel newCashbook = new CashbookModel(cashbookId, name);
        newCashbook.setDescription(description);
        newCashbook.setUserId(currentUser.getUid());

        userCashbooksRef.child(cashbookId).setValue(newCashbook)
                .addOnSuccessListener(aVoid -> {
                    showSnackbar("Cashbook created successfully!");
                    Log.d(TAG, "Cashbook created: " + name);
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Failed to create cashbook: " + e.getMessage());
                    Log.e(TAG, "Error creating cashbook", e);
                });
    }

    private void updateCashbook(CashbookModel cashbook, String newName, String newDescription) {
        cashbook.setName(newName);
        cashbook.setDescription(newDescription);
        cashbook.setLastModified(System.currentTimeMillis());

        userCashbooksRef.child(cashbook.getCashbookId()).child("name").setValue(newName);
        userCashbooksRef.child(cashbook.getCashbookId()).child("description").setValue(newDescription);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(cashbook.getLastModified())
                .addOnSuccessListener(aVoid -> showSnackbar("Cashbook updated"))
                .addOnFailureListener(e -> showSnackbar("Failed to update cashbook"));
    }

    private void handleFavoriteToggle(CashbookModel cashbook) {
        if (cashbook == null) return;
        boolean newFavoriteState = !cashbook.isFavorite();
        long lastModified = System.currentTimeMillis();

        userCashbooksRef.child(cashbook.getCashbookId()).child("favorite").setValue(newFavoriteState);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(lastModified)
                .addOnSuccessListener(aVoid -> {
                    showSnackbar(newFavoriteState ? "Added to favorites" : "Removed from favorites");
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Failed to update favorite status");
                    Log.e(TAG, "Error updating favorite", e);
                });
    }

    private void showCashbookOptions(CashbookModel cashbook, View anchorView) {
        if (cashbook == null) return;

        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.menu_cashbook_item, popup.getMenu());

        popup.getMenu().findItem(R.id.menu_favorite)
                .setTitle(cashbook.isFavorite() ? R.string.remove_from_favorites : R.string.add_to_favorites);
        popup.getMenu().findItem(R.id.menu_toggle_active)
                .setTitle(cashbook.isActive() ? R.string.deactivate_cashbook : R.string.activate_cashbook);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_edit) {
                showCreateCashbookDialog(cashbook);
                return true;
            } else if (itemId == R.id.menu_favorite) {
                handleFavoriteToggle(cashbook);
                return true;
            } else if (itemId == R.id.menu_toggle_active) {
                toggleCashbookActive(cashbook);
                return true;
            } else if (itemId == R.id.menu_export) {
                showSnackbar("Export feature coming soon!");
                return true;
            } else if (itemId == R.id.menu_delete) {
                showDeleteConfirmation(cashbook);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void toggleCashbookActive(CashbookModel cashbook) {
        boolean newActiveState = !cashbook.isActive();
        long lastModified = System.currentTimeMillis();

        userCashbooksRef.child(cashbook.getCashbookId()).child("active").setValue(newActiveState);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(lastModified)
                .addOnSuccessListener(aVoid -> showSnackbar(newActiveState ? "Cashbook activated" : "Cashbook deactivated"))
                .addOnFailureListener(e -> showSnackbar("Failed to update status"));
    }

    private void showDeleteConfirmation(CashbookModel cashbook) {
        if (cashbook == null) return;

        if (allCashbooks.size() <= 1) {
            showSnackbar(getString(R.string.error_delete_last_cashbook));
            return;
        }
        if (cashbook.isCurrent()) {
            showSnackbar(getString(R.string.error_delete_current_cashbook));
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.title_delete_cashbook))
                .setMessage(getString(R.string.msg_delete_cashbook_confirmation, cashbook.getName()))
                .setPositiveButton(getString(R.string.btn_delete), (dialog, which) -> deleteCashbookFromFirebase(cashbook))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void deleteCashbookFromFirebase(CashbookModel cashbook) {
        userCashbooksRef.child(cashbook.getCashbookId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    showSnackbar("Cashbook deleted successfully");
                    Log.d(TAG, "Cashbook deleted: " + cashbook.getName());
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Failed to delete cashbook");
                    Log.e(TAG, "Error deleting cashbook", e);
                });
    }

    private void showSortOptions() {
        try {
            String[] sortOptions = {
                    getString(R.string.sort_recent_first),
                    getString(R.string.sort_name_asc),
                    getString(R.string.sort_name_desc),
                    getString(R.string.sort_oldest_first),
                    getString(R.string.sort_most_transactions)
            };

            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.sort_cashbooks_title))
                    .setItems(sortOptions, (dialog, which) -> {
                        switch (which) {
                            case 0: currentSort = "recent"; break;
                            case 1: currentSort = "name_asc"; break;
                            case 2: currentSort = "name_desc"; break;
                            case 3: currentSort = "oldest"; break;
                            case 4: currentSort = "most_transactions"; break;
                        }
                        applyFiltersAndSort();
                        Log.d(TAG, "Sort applied: " + currentSort);
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing sort options", e);
        }
    }

    private void applyFiltersAndSort() {
        List<CashbookModel> filteredList;
        String query = searchEditText.getText().toString().toLowerCase().trim();

        List<CashbookModel> searchResults;
        if (query.isEmpty()) {
            searchResults = new ArrayList<>(allCashbooks);
        } else {
            searchResults = allCashbooks.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(query)) ||
                            (c.getDescription() != null && c.getDescription().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        switch (currentFilter) {
            case "all":
                filteredList = searchResults;
                break;
            case "active":
                filteredList = searchResults.stream().filter(CashbookModel::isActive).collect(Collectors.toList());
                break;
            case "favorites":
                filteredList = searchResults.stream().filter(CashbookModel::isFavorite).collect(Collectors.toList());
                break;
            case "recent":
            default:
                filteredList = searchResults;
                break;
        }

        switch (currentSort) {
            case "name_asc":
                filteredList.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
                break;
            case "name_desc":
                filteredList.sort((c1, c2) -> c2.getName().compareToIgnoreCase(c1.getName()));
                break;
            case "oldest":
                filteredList.sort((c1, c2) -> Long.compare(c1.getCreatedDate(), c2.getCreatedDate()));
                break;
            case "most_transactions":
                filteredList.sort((c1, c2) -> Integer.compare(c2.getTransactionCount(), c1.getTransactionCount()));
                break;
            case "recent":
            default:
                filteredList.sort((c1, c2) -> Long.compare(c2.getLastModified(), c1.getLastModified()));
                break;
        }

        cashbookAdapter.updateCashbooks(filteredList);

        if (allCashbooks.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
        }

        Log.d(TAG, "Filters/Sort applied. Filter: " + currentFilter + ", Sort: " + currentSort + ", Query: " + query + ". Result: " + filteredList.size() + " items");
    }

    private void onCashbookSelected(CashbookModel cashbook) {
        if (cashbook == null) {
            Log.e(TAG, "onCashbookSelected: cashbook is null");
            return;
        }

        Intent result = new Intent();
        result.putExtra("selected_cashbook_id", cashbook.getCashbookId());
        result.putExtra("cashbook_name", cashbook.getName());
        setResult(RESULT_OK, result);
        Log.d(TAG, "Cashbook selected: " + cashbook.getName());
        finish();
    }

    private void showLoading(boolean show) {
        isLoading = show;
        loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        mainContent.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        mainContent.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            loadingLayout.setVisibility(View.GONE);
        }
    }

    private void updateStats(List<CashbookModel> cashbooks) {
        // Removed updateStats method implementation as views are removed
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cashbooksListener != null && userCashbooksRef != null) {
            userCashbooksRef.removeEventListener(cashbooksListener);
            Log.d(TAG, "Firebase listener removed");
        }
        Log.d(TAG, "CashbookSwitchActivity destroyed");
    }
}