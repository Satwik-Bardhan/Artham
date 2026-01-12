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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
import com.phynix.artham.adapters.CashbookAdapter;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.ErrorHandler;
import com.phynix.artham.utils.ThemeManager;

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
    private View mainContent; // This refers to the CardView with ID mainCard

    // UI Components - Buttons
    private Button cancelButton;
    private Button addNewButton;
    private Button emptyStateCreateButton;
    private ImageView closeButton;

    // UI Components - Search & Filter
    private EditText searchEditText;
    private ChipGroup chipGroup;
    private LinearLayout sortButton; // FIXED: Changed to LinearLayout to match XML

    // UI Components - FAB
    private FloatingActionButton quickAddFab;

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
        // Apply Theme
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashbook_switch); // Ensure this matches your XML filename

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        currentCashbookId = getIntent().getStringExtra("current_cashbook_id");

        if (currentUser == null) {
            showSnackbar("Not authenticated. Please log in again.");
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
    }

    private void initViews() {
        // Main Containers
        cashbookRecyclerView = findViewById(R.id.cashbookRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        mainContent = findViewById(R.id.mainCard); // The CardView

        // Buttons
        cancelButton = findViewById(R.id.cancelButton);
        addNewButton = findViewById(R.id.addNewButton);
        emptyStateCreateButton = findViewById(R.id.emptyStateCreateButton);
        closeButton = findViewById(R.id.closeButton);

        // Search & Filter
        searchEditText = findViewById(R.id.searchEditText);
        chipGroup = findViewById(R.id.chipGroup);
        sortButton = findViewById(R.id.sortButton); // Now correctly cast as LinearLayout

        // FAB
        quickAddFab = findViewById(R.id.quickAddFab);
    }

    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // CRITICAL FIX: Disable nested scrolling to allow smooth scroll inside NestedScrollView
        cashbookRecyclerView.setNestedScrollingEnabled(false);

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

        // Set refresh colors to match your theme
        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary_blue,
                R.color.income_green,
                R.color.expense_red
        );
    }

    private void setupClickListeners() {
        // Navigation / Actions
        closeButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());

        // Creation Actions
        View.OnClickListener addAction = v -> handleAddNewCashbook();
        addNewButton.setOnClickListener(addAction);
        emptyStateCreateButton.setOnClickListener(addAction);
        quickAddFab.setOnClickListener(addAction);

        // Sort Action
        sortButton.setOnClickListener(v -> showSortOptions());
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
    }

    private void loadCashbooks() {
        if (isLoading) return;

        showLoading(true);

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

                            // Calculate stats for display
                            DataSnapshot transactionsSnapshot = snapshot.child("transactions");
                            calculateStatsForCashbook(cashbook, transactionsSnapshot);

                            allCashbooks.add(cashbook);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing cashbook: " + snapshot.getKey(), e);
                    }
                }

                applyFiltersAndSort();
                showLoading(false);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                swipeRefreshLayout.setRefreshing(false);
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
                Log.w(TAG, "Error parsing transaction", e);
            }
        }

        cashbook.setTotalBalance(totalIncome - totalExpense);
        cashbook.setTransactionCount(count);
    }

    private void handleAddNewCashbook() {
        showCreateCashbookDialog(null);
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
            Log.e(TAG, "Error showing dialog", e);
            showSnackbar("Error opening dialog");
        }
    }

    private void createNewCashbook(String name, String description) {
        String cashbookId = userCashbooksRef.push().getKey();
        if (cashbookId == null) return;

        CashbookModel newCashbook = new CashbookModel(cashbookId, name);
        newCashbook.setDescription(description);
        newCashbook.setUserId(currentUser.getUid());
        newCashbook.setCreatedDate(System.currentTimeMillis());
        newCashbook.setLastModified(System.currentTimeMillis());
        newCashbook.setActive(true);

        userCashbooksRef.child(cashbookId).setValue(newCashbook)
                .addOnSuccessListener(aVoid -> showSnackbar("Cashbook created successfully!"))
                .addOnFailureListener(e -> showSnackbar("Failed: " + e.getMessage()));
    }

    private void updateCashbook(CashbookModel cashbook, String newName, String newDescription) {
        cashbook.setName(newName);
        cashbook.setDescription(newDescription);

        userCashbooksRef.child(cashbook.getCashbookId()).child("name").setValue(newName);
        userCashbooksRef.child(cashbook.getCashbookId()).child("description").setValue(newDescription);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> showSnackbar("Cashbook updated"))
                .addOnFailureListener(e -> showSnackbar("Failed to update"));
    }

    private void handleFavoriteToggle(CashbookModel cashbook) {
        if (cashbook == null) return;
        boolean newFavoriteState = !cashbook.isFavorite();

        userCashbooksRef.child(cashbook.getCashbookId()).child("favorite").setValue(newFavoriteState);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> showSnackbar(newFavoriteState ? "Added to favorites" : "Removed from favorites"));
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

        userCashbooksRef.child(cashbook.getCashbookId()).child("active").setValue(newActiveState);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> showSnackbar(newActiveState ? "Cashbook activated" : "Cashbook deactivated"));
    }

    private void showDeleteConfirmation(CashbookModel cashbook) {
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
                .addOnSuccessListener(aVoid -> showSnackbar("Cashbook deleted successfully"));
    }

    private void showSortOptions() {
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
                })
                .show();
    }

    private void applyFiltersAndSort() {
        List<CashbookModel> filteredList;
        String query = searchEditText.getText().toString().toLowerCase().trim();

        // 1. Search Filtering
        List<CashbookModel> searchResults;
        if (query.isEmpty()) {
            searchResults = new ArrayList<>(allCashbooks);
        } else {
            searchResults = allCashbooks.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        // 2. Category Filtering
        switch (currentFilter) {
            case "active":
                filteredList = searchResults.stream().filter(CashbookModel::isActive).collect(Collectors.toList());
                break;
            case "favorites":
                filteredList = searchResults.stream().filter(CashbookModel::isFavorite).collect(Collectors.toList());
                break;
            default: // "all" or "recent"
                filteredList = searchResults;
                break;
        }

        // 3. Sorting
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

        // Logic: Only show "Empty State" (big folder icon) if the user HAS NO DATA at all.
        // If they have data but filtered it down to 0, just show empty list (so they can clear filter).
        if (allCashbooks.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
        }
    }

    private void onCashbookSelected(CashbookModel cashbook) {
        if (cashbook == null) return;

        Intent result = new Intent();
        result.putExtra("selected_cashbook_id", cashbook.getCashbookId());
        result.putExtra("cashbook_name", cashbook.getName());
        setResult(RESULT_OK, result);
        finish();
    }

    private void showLoading(boolean show) {
        isLoading = show;
        loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        // We only hide main content if we are loading to avoid flicker,
        // but generally keeping main content visible behind loading is fine too.
        if (show) {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        mainContent.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cashbooksListener != null && userCashbooksRef != null) {
            userCashbooksRef.removeEventListener(cashbooksListener);
        }
    }
}