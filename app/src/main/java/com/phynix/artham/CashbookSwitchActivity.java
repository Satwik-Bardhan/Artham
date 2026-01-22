package com.phynix.artham;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CashbookSwitchActivity extends AppCompatActivity {

    private static final String TAG = "CashbookSwitchActivity";
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SORT_ORDER = "cashbook_sort_order";

    // UI Components
    private RecyclerView cashbookRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;
    private View mainContent;

    // UI Components - Buttons
    private Button emptyStateCreateButton;
    private ImageView closeButton;

    // UI Components - Search & Filter
    private EditText searchEditText;
    private ChipGroup chipGroup;
    private LinearLayout sortButton;

    // UI Components - FAB
    private FloatingActionButton quickAddFab;

    // Adapter & Data
    private CashbookAdapter cashbookAdapter;
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private String currentFilter = "active";
    private String currentSort = "recent"; // Default sort
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
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashbook_manager);

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

        // Load persisted sort order
        loadSortPreference();

        userCashbooksRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(currentUser.getUid()).child("cashbooks");

        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupSearchListener();
        setupFilterListener();
        loadCashbooks();
    }

    private void loadSortPreference() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentSort = prefs.getString(KEY_SORT_ORDER, "recent");
    }

    private void saveSortPreference(String sortOrder) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_SORT_ORDER, sortOrder).apply();
        currentSort = sortOrder;
        applyFiltersAndSort();
    }

    private void initViews() {
        cashbookRecyclerView = findViewById(R.id.cashbookRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);

        // Use RecyclerView as main content view for visibility toggling
        mainContent = cashbookRecyclerView;

        emptyStateCreateButton = findViewById(R.id.emptyStateCreateButton);
        closeButton = findViewById(R.id.closeButton);

        searchEditText = findViewById(R.id.searchEditText);

        // FIXED: Use the ID of the <include> tag, not the ID inside the file
        chipGroup = findViewById(R.id.includedFilterLayout);

        sortButton = findViewById(R.id.sortButton);
        quickAddFab = findViewById(R.id.quickAddFab);
    }

    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(this));
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

        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary_blue,
                R.color.income_green,
                R.color.expense_red
        );
    }

    private void setupClickListeners() {
        closeButton.setOnClickListener(v -> finish());

        View.OnClickListener addAction = v -> handleAddNewCashbook();
        emptyStateCreateButton.setOnClickListener(addAction);
        quickAddFab.setOnClickListener(addAction);

        // Updated to show the new CENTERED dialog
        sortButton.setOnClickListener(v -> showSortOptionsDialog());
    }

    private void setupFilterListener() {
        if (chipGroup == null) {
            Log.e(TAG, "ChipGroup not found! Check XML IDs.");
            return;
        }

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
                            boolean isCurrent = currentCashbookId != null && currentCashbookId.equals(snapshot.getKey());
                            cashbook.setCurrent(isCurrent);

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
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_cashbook, null);

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            TextView titleView = dialogView.findViewById(R.id.dialogTitle);
            EditText nameInput = dialogView.findViewById(R.id.cashbookNameInput);
            EditText descInput = dialogView.findViewById(R.id.cashbookDescInput);
            Button btnSave = dialogView.findViewById(R.id.btnSave);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);

            String title = (cashbookToEdit == null) ? "Create New Cashbook" : "Edit Cashbook";
            String btnText = (cashbookToEdit == null) ? "Create" : "Update";

            titleView.setText(title);
            btnSave.setText(btnText);

            if (cashbookToEdit != null) {
                nameInput.setText(cashbookToEdit.getName());
                descInput.setText(cashbookToEdit.getDescription());
            }

            btnSave.setOnClickListener(v -> {
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
                dialog.dismiss();
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            dialog.show();

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

    // New CENTERED Dialog Implementation
    private void showSortOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Using the new centered layout
        View view = getLayoutInflater().inflate(R.layout.dialog_sort_cashbooks, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        // Make background transparent for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // UI References
        View optNameAsc = view.findViewById(R.id.optNameAsc);
        View optNameDesc = view.findViewById(R.id.optNameDesc); // NEW
        View optDateNewest = view.findViewById(R.id.optDateNewest);
        View optDateOldest = view.findViewById(R.id.optDateOldest);
        View optBalanceHigh = view.findViewById(R.id.optBalanceHigh);
        View optBalanceLow = view.findViewById(R.id.optBalanceLow); // NEW

        ImageView checkNameAsc = view.findViewById(R.id.checkNameAsc);
        ImageView checkNameDesc = view.findViewById(R.id.checkNameDesc); // NEW
        ImageView checkDateNewest = view.findViewById(R.id.checkDateNewest);
        ImageView checkDateOldest = view.findViewById(R.id.checkDateOldest);
        ImageView checkBalanceHigh = view.findViewById(R.id.checkBalanceHigh);
        ImageView checkBalanceLow = view.findViewById(R.id.checkBalanceLow); // NEW

        TextView textNameAsc = view.findViewById(R.id.textNameAsc);
        TextView textNameDesc = view.findViewById(R.id.textNameDesc); // NEW
        TextView textDateNewest = view.findViewById(R.id.textDateNewest);
        TextView textDateOldest = view.findViewById(R.id.textDateOldest);
        TextView textBalanceHigh = view.findViewById(R.id.textBalanceHigh);
        TextView textBalanceLow = view.findViewById(R.id.textBalanceLow); // NEW

        // Highlight Current Selection
        highlightSortOption(currentSort, "name_asc", textNameAsc, checkNameAsc);
        highlightSortOption(currentSort, "name_desc", textNameDesc, checkNameDesc);
        highlightSortOption(currentSort, "recent", textDateNewest, checkDateNewest);
        highlightSortOption(currentSort, "oldest", textDateOldest, checkDateOldest);
        highlightSortOption(currentSort, "balance_high", textBalanceHigh, checkBalanceHigh);
        highlightSortOption(currentSort, "balance_low", textBalanceLow, checkBalanceLow);

        // Listeners
        optNameAsc.setOnClickListener(v -> { saveSortPreference("name_asc"); dialog.dismiss(); });
        optNameDesc.setOnClickListener(v -> { saveSortPreference("name_desc"); dialog.dismiss(); });
        optDateNewest.setOnClickListener(v -> { saveSortPreference("recent"); dialog.dismiss(); });
        optDateOldest.setOnClickListener(v -> { saveSortPreference("oldest"); dialog.dismiss(); });
        optBalanceHigh.setOnClickListener(v -> { saveSortPreference("balance_high"); dialog.dismiss(); });
        optBalanceLow.setOnClickListener(v -> { saveSortPreference("balance_low"); dialog.dismiss(); });

        view.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void highlightSortOption(String currentSort, String targetSort, TextView text, ImageView check) {
        if (currentSort.equals(targetSort)) {
            check.setVisibility(View.VISIBLE);
            text.setTextColor(getThemeAttrColor(R.attr.chk_primary_blue));
            text.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            check.setVisibility(View.GONE);
            text.setTextColor(getThemeAttrColor(R.attr.chk_textColorPrimary));
            text.setTypeface(null, android.graphics.Typeface.NORMAL);
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
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        switch (currentFilter) {
            case "active":
                filteredList = searchResults.stream().filter(CashbookModel::isActive).collect(Collectors.toList());
                break;
            case "favorites":
                filteredList = searchResults.stream().filter(CashbookModel::isFavorite).collect(Collectors.toList());
                break;
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
            case "balance_high":
                filteredList.sort((c1, c2) -> Double.compare(c2.getTotalBalance(), c1.getTotalBalance()));
                break;
            case "balance_low":
                filteredList.sort((c1, c2) -> Double.compare(c1.getTotalBalance(), c2.getTotalBalance()));
                break;
            case "recent":
            default:
                filteredList.sort((c1, c2) -> Long.compare(c2.getLastModified(), c1.getLastModified()));
                break;
        }

        // --- NEW: Always move CURRENT cashbook to TOP ---
        Collections.sort(filteredList, (c1, c2) -> {
            if (c1.isCurrent()) return -1; // c1 is current, move up
            if (c2.isCurrent()) return 1;  // c2 is current, move up
            return 0; // neither is current, keep relative order from previous sort
        });
        // -----------------------------------------------

        cashbookAdapter.updateCashbooks(filteredList);

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
        if (show) {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        mainContent.setVisibility(show ? View.GONE : View.VISIBLE);
        if (quickAddFab != null) {
            quickAddFab.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    private int getThemeAttrColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.BLACK;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cashbooksListener != null && userCashbooksRef != null) {
            userCashbooksRef.removeEventListener(cashbooksListener);
        }
    }
}