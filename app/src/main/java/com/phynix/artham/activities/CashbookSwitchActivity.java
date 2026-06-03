package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
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
import android.view.WindowManager;

import java.util.Locale;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;

import com.google.android.material.chip.Chip;
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
import com.phynix.artham.adapters.ColorSelectionAdapter;
import com.phynix.artham.adapters.CategoryIconAdapter;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.ErrorHandler;
import com.phynix.artham.utils.DialogUtils;

import com.phynix.artham.utils.PdfReportGenerator;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.utils.OnboardingManager;
import com.phynix.artham.utils.OnboardingOverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CashbookSwitchActivity extends BaseActivity {

    private static final String TAG = "CashbookSwitchActivity";
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SORT_ORDER = "cashbook_sort_order";

    // UI Components
    private RecyclerView cashbookRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;
    private ShimmerFrameLayout shimmerLoadingLayout;
    private View mainContent;

    // UI Components - Buttons
    private Button emptyStateCreateButton;
    private ImageView closeButton;

    // UI Components - Search & Filter
    private EditText searchEditText;
    private ChipGroup chipGroup;
    private LinearLayout sortButton;
    private TextView cashbookCountText;

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
    private com.phynix.artham.db.DataRepository repository;

    // State
    private boolean isLoading = false;

    // Custom Categories State
    private DatabaseReference userCategoriesRef;
    private ValueEventListener categoriesListener;
    private final List<String> customCategories = new ArrayList<>();
    private final List<com.google.android.material.chip.Chip> dynamicChips = new ArrayList<>();


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
        repository = com.phynix.artham.db.DataRepository.getInstance(getApplication());

        // 4. READ FROM PREFERENCES IF INTENT FAILS
        currentCashbookId = getIntent().getStringExtra("current_cashbook_id");
        if (currentCashbookId == null) {
            currentCashbookId = getIntent().getStringExtra("cashbook_id");
        }
        if (currentCashbookId == null) {
            currentCashbookId = getIntent().getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        }

        // Final ultimate fallback: Read from persistent secure storage
        if (currentCashbookId == null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            currentCashbookId = prefs.getString("last_selected_cashbook_id", null);
        }

        if (!repository.isLocalMode() && currentUser == null) {
            showSnackbar("Not authenticated. Please log in again.");
            finish();
            return;
        }

        loadSortPreference();

        if (!repository.isLocalMode() && currentUser != null) {
            userCashbooksRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(currentUser.getUid()).child("cashbooks");

            userCategoriesRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(currentUser.getUid()).child("cashbookCategories");
        }

        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupSearchListener();
        setupFilterListener();
        setupCategoriesListener();
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
        shimmerLoadingLayout = findViewById(R.id.shimmerLoadingLayout);

        mainContent = cashbookRecyclerView;

        emptyStateCreateButton = findViewById(R.id.emptyStateCreateButton);
        closeButton = findViewById(R.id.closeButton);

        searchEditText = findViewById(R.id.searchEditText);
        chipGroup = findViewById(R.id.chipGroup);
        sortButton = findViewById(R.id.sortButton);
        cashbookCountText = findViewById(R.id.cashbookCountText);
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

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::loadCashbooks);
            swipeRefreshLayout.setColorSchemeResources(
                    R.color.primary_blue,
                    R.color.income_green,
                    R.color.expense_red
            );
        }
    }

    private void setupClickListeners() {
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> finish());
        }

        View.OnClickListener addAction = v -> handleAddNewCashbook();
        if (emptyStateCreateButton != null) emptyStateCreateButton.setOnClickListener(addAction);
        if (quickAddFab != null) quickAddFab.setOnClickListener(addAction);
        View btnAddCashbookFilter = findViewById(R.id.btnAddCashbookFilter);
        if (btnAddCashbookFilter != null) {
            btnAddCashbookFilter.setOnClickListener(v -> showCreateCategoryDialog());
        }

        if (sortButton != null) sortButton.setOnClickListener(v -> showSortOptionsDialog());
    }

    private void setupFilterListener() {
        if (chipGroup == null) return;

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
                } else if (checkedId == R.id.chipInactive) {
                    currentFilter = "inactive";
                } else {
                    com.google.android.material.chip.Chip checkedChip = findViewById(checkedId);
                    if (checkedChip != null) {
                        currentFilter = checkedChip.getText().toString();
                    }
                }
                applyFiltersAndSort();
            }
        });
    }

    private void setupSearchListener() {
        if (searchEditText != null) {
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
    }

    private void loadCashbooks() {
        if (isLoading) return;

        showLoading(true);

        if (repository.isLocalMode()) {
            repository.getCashbooks(data -> {
                allCashbooks.clear();
                for (CashbookModel cb : data) {
                    boolean isCurrent = currentCashbookId != null && currentCashbookId.equals(cb.getCashbookId());
                    cb.setCurrent(isCurrent);

                    double totalIncome = 0;
                    double totalExpense = 0;
                    int count = 0;
                    for (TransactionModel t : cb.getTransactionList()) {
                        count++;
                        if ("IN".equalsIgnoreCase(t.getType())) {
                            totalIncome += t.getAmount();
                        } else {
                            totalExpense += t.getAmount();
                        }
                    }
                    cb.setTotalBalance(totalIncome - totalExpense);
                    cb.setTransactionCount(count);

                    allCashbooks.add(cb);
                }
                applyFiltersAndSort();
                showLoading(false);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                checkAndShowOnboarding();
            }, error -> {
                showLoading(false);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                showSnackbar(error);
            });
            return;
        }

        if (cashbooksListener != null && userCashbooksRef != null) {
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
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

                // ── Onboarding: Show tooltips on first visit ──
                checkAndShowOnboarding();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
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

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            TextView titleView = dialogView.findViewById(R.id.dialogTitle);
            EditText nameInput = dialogView.findViewById(R.id.cashbookNameInput);
            EditText descInput = dialogView.findViewById(R.id.cashbookDescInput);
            EditText categoryInput = dialogView.findViewById(R.id.cashbookCategoryInput);
            Button btnSave = dialogView.findViewById(R.id.btnSave);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);

            String title = (cashbookToEdit == null) ? "Create New Cashbook" : "Edit Cashbook";
            String btnText = (cashbookToEdit == null) ? "Create" : "Update";

            titleView.setText(title);
            btnSave.setText(btnText);

            // --- Custom Appearance Selectors Setup ---
            final String[] selectedColor = { (cashbookToEdit != null && cashbookToEdit.getThemeColor() != null) ? cashbookToEdit.getThemeColor() : "#3F51B5" };
            final String[] selectedIcon = { (cashbookToEdit != null && cashbookToEdit.getThemeIcon() != null) ? cashbookToEdit.getThemeIcon() : "ic_book" };

            RecyclerView colorRecyclerView = dialogView.findViewById(R.id.colorRecyclerView);
            RecyclerView iconRecyclerView = dialogView.findViewById(R.id.iconRecyclerView);

            List<String> colors = List.of(
                    "#3F51B5", "#009688", "#FF9800", "#E91E63", 
                    "#9C27B0", "#03A9F4", "#4CAF50", "#FF5722",
                    "#607D8B", "#8BC34A", "#00BCD4"
            );
            List<String> iconNames = List.of(
                    "ic_book", "ic_balance_wallet", "ic_account_balance", "ic_money",
                    "ic_home", "ic_work", "ic_shopping_cart", "ic_flight",
                    "ic_credit_card", "ic_receipt_long", "ic_card_giftcard", "ic_entertainment",
                    "ic_utilities", "ic_food_dining", "ic_groceries", "ic_transportation"
            );
            List<Integer> iconResIds = new ArrayList<>();
            for (String iconName : iconNames) {
                int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
                iconResIds.add(resId != 0 ? resId : R.drawable.ic_category);
            }

            if (colorRecyclerView != null) {
                colorRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                ColorSelectionAdapter colorAdapter = new ColorSelectionAdapter(colors, hexColor -> selectedColor[0] = hexColor);
                colorRecyclerView.setAdapter(colorAdapter);
                int initialColorPos = colors.indexOf(selectedColor[0]);
                if (initialColorPos != -1) {
                    colorAdapter.setSelectedIndex(initialColorPos);
                }
            }

            if (iconRecyclerView != null) {
                iconRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                CategoryIconAdapter iconAdapter = new CategoryIconAdapter(iconResIds, iconResId -> {
                    int index = iconResIds.indexOf(iconResId);
                    if (index != -1) {
                        selectedIcon[0] = iconNames.get(index);
                    }
                });
                iconRecyclerView.setAdapter(iconAdapter);
                int initialIconPos = iconNames.indexOf(selectedIcon[0]);
                if (initialIconPos != -1) {
                    iconAdapter.setSelectedIndex(initialIconPos);
                }
            }
            // -----------------------------------------

            if (categoryInput != null) {
                if (cashbookToEdit != null) {
                    categoryInput.setText(cashbookToEdit.getCategory());
                } else {
                    // If creating and currently filtering by a custom category, pre-fill it!
                    boolean isStandardFilter = "all".equalsIgnoreCase(currentFilter) ||
                            "active".equalsIgnoreCase(currentFilter) ||
                            "recent".equalsIgnoreCase(currentFilter) ||
                            "favorites".equalsIgnoreCase(currentFilter) ||
                            "inactive".equalsIgnoreCase(currentFilter);
                    if (!isStandardFilter) {
                        categoryInput.setText(currentFilter);
                    }
                }

                // Setup Suggested Labels Side-by-Side Chips
                android.widget.HorizontalScrollView suggestedScroll = dialogView.findViewById(R.id.dialogSuggestedLabelsScroll);
                com.google.android.material.chip.ChipGroup suggestedChipGroup = dialogView.findViewById(R.id.dialogSuggestedChipGroup);

                if (suggestedScroll != null && suggestedChipGroup != null) {
                    if (customCategories != null && !customCategories.isEmpty()) {
                        suggestedScroll.setVisibility(android.view.View.VISIBLE);
                        suggestedChipGroup.removeAllViews();

                        String initialCategory = (cashbookToEdit != null) ? cashbookToEdit.getCategory() : "";
                        if (initialCategory.isEmpty()) {
                            boolean isStandardFilter = "all".equalsIgnoreCase(currentFilter) ||
                                    "active".equalsIgnoreCase(currentFilter) ||
                                    "recent".equalsIgnoreCase(currentFilter) ||
                                    "favorites".equalsIgnoreCase(currentFilter) ||
                                    "inactive".equalsIgnoreCase(currentFilter);
                            if (!isStandardFilter) {
                                initialCategory = currentFilter;
                            }
                        }

                        for (String category : customCategories) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
                            chip.setText(category);
                            chip.setCheckable(true);
                            chip.setCheckedIconVisible(false);
                            
                            styleChipWithCategoryColor(chip, category);

                            if (category.equalsIgnoreCase(initialCategory)) {
                                chip.setChecked(true);
                            }

                            chip.setOnClickListener(v -> {
                                    if (chip.isChecked()) {
                                        categoryInput.setText(category);
                                    } else {
                                        categoryInput.setText("");
                                    }
                            });

                            suggestedChipGroup.addView(chip);
                        }

                        // Listen to text changes to update chip selection state dynamically
                        categoryInput.addTextChangedListener(new android.text.TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {}

                            @Override
                            public void afterTextChanged(android.text.Editable s) {
                                String typed = s.toString().trim();
                                for (int i = 0; i < suggestedChipGroup.getChildCount(); i++) {
                                    com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) suggestedChipGroup.getChildAt(i);
                                    chip.setChecked(chip.getText().toString().equalsIgnoreCase(typed));
                                }
                            }
                        });
                    } else {
                        suggestedScroll.setVisibility(android.view.View.GONE);
                    }
                }
            }

            if (cashbookToEdit != null) {
                nameInput.setText(cashbookToEdit.getName());
                descInput.setText(cashbookToEdit.getDescription());
            }

            btnSave.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                String description = descInput.getText().toString().trim();
                String category = categoryInput != null ? categoryInput.getText().toString().trim() : "";

                if (name.isEmpty()) {
                    showSnackbar("Please enter a cashbook name");
                    return;
                }

                // Validate: only allow alphabets, numerics, and spaces
                if (!name.matches("^[a-zA-Z0-9\\s]+$")) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Invalid Name")
                            .setMessage("Cashbook name can only contain alphabets and numerics.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }

                if (cashbookToEdit == null) {
                    createNewCashbook(name, description, category, selectedColor[0], selectedIcon[0]);
                } else {
                    updateCashbook(cashbookToEdit, name, description, category, selectedColor[0], selectedIcon[0]);
                }
                dialog.dismiss();
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }

            DialogUtils.applyBlurEffect(dialog, this);
            dialog.show();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing dialog", e);
            showSnackbar("Error opening dialog");
        }
    }

    private void createNewCashbook(String name, String description, String category, String color, String icon) {
        if (repository.isLocalMode()) {
            String cashbookId = "local_cb_" + UUID.randomUUID().toString();
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            newCashbook.setDescription(description);
            newCashbook.setCategory(category);
            newCashbook.setThemeColor(color);
            newCashbook.setThemeIcon(icon);
            newCashbook.setUserId("local_user");
            newCashbook.setCreatedDate(System.currentTimeMillis());
            newCashbook.setLastModified(System.currentTimeMillis());
            newCashbook.setActive(true);

            com.phynix.artham.db.DataRepository.LocalDataWrapper data = repository.loadLocalData();
            data.cashbooks.add(newCashbook);
            repository.saveLocalData(data);

            repository.createDefaultCategories(cashbookId, success -> {
                if (!category.isEmpty()) {
                    saveCustomCategory(category);
                }
                showSnackbar("Cashbook created successfully!");
                onCashbookSelected(newCashbook);
            });
            return;
        }

        String cashbookId = userCashbooksRef.push().getKey();
        if (cashbookId == null) return;

        CashbookModel newCashbook = new CashbookModel(cashbookId, name);
        newCashbook.setDescription(description);
        newCashbook.setCategory(category);
        newCashbook.setThemeColor(color);
        newCashbook.setThemeIcon(icon);
        newCashbook.setUserId(currentUser.getUid());
        newCashbook.setCreatedDate(System.currentTimeMillis());
        newCashbook.setLastModified(System.currentTimeMillis());
        newCashbook.setActive(true);

        userCashbooksRef.child(cashbookId).setValue(newCashbook)
                .addOnSuccessListener(aVoid -> {
                    if (!category.isEmpty() && !customCategories.contains(category)) {
                        saveCustomCategory(category);
                    }
                    showSnackbar("Cashbook created successfully!");
                    onCashbookSelected(newCashbook);
                })
                .addOnFailureListener(e -> showSnackbar("Failed: " + e.getMessage()));
    }

    private void updateCashbook(CashbookModel cashbook, String newName, String newDescription, String category, String color, String icon) {
        cashbook.setName(newName);
        cashbook.setDescription(newDescription);
        cashbook.setCategory(category);
        cashbook.setThemeColor(color);
        cashbook.setThemeIcon(icon);
        cashbook.setLastModified(System.currentTimeMillis());

        if (repository.isLocalMode()) {
            com.phynix.artham.db.DataRepository.LocalDataWrapper data = repository.loadLocalData();
            for (int i = 0; i < data.cashbooks.size(); i++) {
                if (data.cashbooks.get(i).getCashbookId().equals(cashbook.getCashbookId())) {
                    data.cashbooks.set(i, cashbook);
                    break;
                }
            }
            repository.saveLocalData(data);
            if (!category.isEmpty()) {
                saveCustomCategory(category);
            }
            showSnackbar("Cashbook updated");
            loadCashbooks();
            return;
        }

        userCashbooksRef.child(cashbook.getCashbookId()).child("name").setValue(newName);
        userCashbooksRef.child(cashbook.getCashbookId()).child("description").setValue(newDescription);
        userCashbooksRef.child(cashbook.getCashbookId()).child("category").setValue(category);
        userCashbooksRef.child(cashbook.getCashbookId()).child("themeColor").setValue(color);
        userCashbooksRef.child(cashbook.getCashbookId()).child("themeIcon").setValue(icon);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    if (!category.isEmpty() && !customCategories.contains(category)) {
                        saveCustomCategory(category);
                    }
                    showSnackbar("Cashbook updated");
                })
                .addOnFailureListener(e -> showSnackbar("Failed to update"));
    }

    private void handleFavoriteToggle(CashbookModel cashbook) {
        if (cashbook == null) return;
        boolean newFavoriteState = !cashbook.isFavorite();
        cashbook.setFavorite(newFavoriteState);
        cashbook.setLastModified(System.currentTimeMillis());

        if (repository.isLocalMode()) {
            com.phynix.artham.db.DataRepository.LocalDataWrapper data = repository.loadLocalData();
            for (int i = 0; i < data.cashbooks.size(); i++) {
                if (data.cashbooks.get(i).getCashbookId().equals(cashbook.getCashbookId())) {
                    data.cashbooks.set(i, cashbook);
                    break;
                }
            }
            repository.saveLocalData(data);
            showSnackbar(newFavoriteState ? "Added to favorites" : "Removed from favorites");
            loadCashbooks();
            return;
        }

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
            } else if (itemId == R.id.menu_export) {
                exportCashbookAsPdf(cashbook);
                return true;
            } else if (itemId == R.id.menu_change_label) {
                showChangeCategoryDialog(cashbook);
                return true;
            } else if (itemId == R.id.menu_delete) {
                showDeleteConfirmation(cashbook);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void exportCashbookAsPdf(CashbookModel cashbook) {
        if (cashbook == null) return;

        showSnackbar("Preparing export...");

        if (repository.isLocalMode()) {
            List<TransactionModel> transactions = cashbook.getTransactionList();
            if (transactions.isEmpty()) {
                showSnackbar("No transactions to export in this cashbook");
                return;
            }
            long earliest = Long.MAX_VALUE;
            long latest = Long.MIN_VALUE;
            for (TransactionModel txn : transactions) {
                if (txn.getTimestamp() < earliest) earliest = txn.getTimestamp();
                if (txn.getTimestamp() > latest) latest = txn.getTimestamp();
            }
            String bookName = cashbook.getName() != null ? cashbook.getName() : "Cashbook";
            PdfReportGenerator.generateReport(
                    CashbookSwitchActivity.this,
                    transactions,
                    bookName,
                    earliest,
                    latest
            );
            return;
        }

        DatabaseReference txnRef = userCashbooksRef
                .child(cashbook.getCashbookId())
                .child("transactions");

        txnRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<TransactionModel> transactions = new ArrayList<>();
                long earliest = Long.MAX_VALUE;
                long latest = Long.MIN_VALUE;

                for (DataSnapshot snap : dataSnapshot.getChildren()) {
                    try {
                        TransactionModel txn = snap.getValue(TransactionModel.class);
                        if (txn != null) {
                            txn.setTransactionId(snap.getKey());
                            transactions.add(txn);
                            if (txn.getTimestamp() < earliest) earliest = txn.getTimestamp();
                            if (txn.getTimestamp() > latest) latest = txn.getTimestamp();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing transaction for export", e);
                    }
                }

                if (transactions.isEmpty()) {
                    showSnackbar("No transactions to export in this cashbook");
                    return;
                }

                String bookName = cashbook.getName() != null ? cashbook.getName() : "Cashbook";
                PdfReportGenerator.generateReport(
                        CashbookSwitchActivity.this,
                        transactions,
                        bookName,
                        earliest,
                        latest
                );
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showSnackbar("Failed to fetch transactions: " + error.getMessage());
            }
        });
    }

    private void toggleCashbookActive(CashbookModel cashbook) {
        boolean newActiveState = !cashbook.isActive();
        cashbook.setActive(newActiveState);
        cashbook.setLastModified(System.currentTimeMillis());

        if (repository.isLocalMode()) {
            com.phynix.artham.db.DataRepository.LocalDataWrapper data = repository.loadLocalData();
            for (int i = 0; i < data.cashbooks.size(); i++) {
                if (data.cashbooks.get(i).getCashbookId().equals(cashbook.getCashbookId())) {
                    data.cashbooks.set(i, cashbook);
                    break;
                }
            }
            repository.saveLocalData(data);
            showSnackbar(newActiveState ? "Cashbook activated" : "Cashbook deactivated");
            loadCashbooks();
            return;
        }

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

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.title_delete_cashbook))
                .setMessage(getString(R.string.msg_delete_cashbook_confirmation, cashbook.getName()))
                .setPositiveButton(getString(R.string.btn_delete), (d, which) -> deleteCashbookFromFirebase(cashbook))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .create();

        DialogUtils.applyBlurEffect(dialog, this);
        dialog.show();
    }

    private void deleteCashbookFromFirebase(CashbookModel cashbook) {
        if (repository.isLocalMode()) {
            repository.deleteCashbook(cashbook.getCashbookId(), success -> {
                showSnackbar("Cashbook deleted");
                loadCashbooks();
            }, error -> {
                showSnackbar(error);
            });
            return;
        }

        DatabaseReference cashbookRef = userCashbooksRef.child(cashbook.getCashbookId());

        // Snapshot the entire cashbook node (including transactions) before deleting
        cashbookRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object deletedData = snapshot.getValue();

                cashbookRef.removeValue()
                        .addOnSuccessListener(aVoid -> {
                            Snackbar snackbar = Snackbar.make(
                                     findViewById(android.R.id.content),
                                    "Cashbook deleted",
                                    Snackbar.LENGTH_LONG);
                            snackbar.setAction("UNDO", v -> {
                                if (deletedData != null) {
                                    cashbookRef.setValue(deletedData)
                                            .addOnSuccessListener(a -> showSnackbar("Cashbook restored"))
                                            .addOnFailureListener(e -> showSnackbar("Failed to restore cashbook"));
                                }
                            });
                            snackbar.show();
                        })
                        .addOnFailureListener(e -> showSnackbar("Failed to delete cashbook"));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showSnackbar("Failed to delete cashbook");
            }
        });
    }

    private void showSortOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_sort_cashbooks, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View optNameAsc = view.findViewById(R.id.optNameAsc);
        View optNameDesc = view.findViewById(R.id.optNameDesc);
        View optDateNewest = view.findViewById(R.id.optDateNewest);
        View optDateOldest = view.findViewById(R.id.optDateOldest);
        View optBalanceHigh = view.findViewById(R.id.optBalanceHigh);
        View optBalanceLow = view.findViewById(R.id.optBalanceLow);

        ImageView checkNameAsc = view.findViewById(R.id.checkNameAsc);
        ImageView checkNameDesc = view.findViewById(R.id.checkNameDesc);
        ImageView checkDateNewest = view.findViewById(R.id.checkDateNewest);
        ImageView checkDateOldest = view.findViewById(R.id.checkDateOldest);
        ImageView checkBalanceHigh = view.findViewById(R.id.checkBalanceHigh);
        ImageView checkBalanceLow = view.findViewById(R.id.checkBalanceLow);

        TextView textNameAsc = view.findViewById(R.id.textNameAsc);
        TextView textNameDesc = view.findViewById(R.id.textNameDesc);
        TextView textDateNewest = view.findViewById(R.id.textDateNewest);
        TextView textDateOldest = view.findViewById(R.id.textDateOldest);
        TextView textBalanceHigh = view.findViewById(R.id.textBalanceHigh);
        TextView textBalanceLow = view.findViewById(R.id.textBalanceLow);

        highlightSortOption(currentSort, "name_asc", textNameAsc, checkNameAsc);
        highlightSortOption(currentSort, "name_desc", textNameDesc, checkNameDesc);
        highlightSortOption(currentSort, "recent", textDateNewest, checkDateNewest);
        highlightSortOption(currentSort, "oldest", textDateOldest, checkDateOldest);
        highlightSortOption(currentSort, "balance_high", textBalanceHigh, checkBalanceHigh);
        highlightSortOption(currentSort, "balance_low", textBalanceLow, checkBalanceLow);

        optNameAsc.setOnClickListener(v -> { saveSortPreference("name_asc"); dialog.dismiss(); });
        optNameDesc.setOnClickListener(v -> { saveSortPreference("name_desc"); dialog.dismiss(); });
        optDateNewest.setOnClickListener(v -> { saveSortPreference("recent"); dialog.dismiss(); });
        optDateOldest.setOnClickListener(v -> { saveSortPreference("oldest"); dialog.dismiss(); });
        optBalanceHigh.setOnClickListener(v -> { saveSortPreference("balance_high"); dialog.dismiss(); });
        optBalanceLow.setOnClickListener(v -> { saveSortPreference("balance_low"); dialog.dismiss(); });

        view.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        DialogUtils.applyBlurEffect(dialog, this);
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
        String query = "";
        if (searchEditText != null && searchEditText.getText() != null) {
            query = searchEditText.getText().toString().toLowerCase().trim();
        }

        List<CashbookModel> searchResults;
        if (query.isEmpty()) {
            searchResults = new ArrayList<>(allCashbooks);
        } else {
            String finalQuery = query;
            searchResults = allCashbooks.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(finalQuery)))
                    .collect(Collectors.toList());
        }

        switch (currentFilter) {
            case "active":
                filteredList = searchResults.stream().filter(CashbookModel::isActive).collect(Collectors.toList());
                break;
            case "favorites":
                filteredList = searchResults.stream().filter(CashbookModel::isFavorite).collect(Collectors.toList());
                break;
            case "inactive":
                filteredList = searchResults.stream().filter(c -> !c.isActive()).collect(Collectors.toList());
                break;
            case "recent":
                filteredList = new ArrayList<>(searchResults);
                break;
            case "all":
                filteredList = new ArrayList<>(searchResults);
                break;
            default:
                String categoryToMatch = currentFilter;
                filteredList = searchResults.stream()
                        .filter(c -> categoryToMatch.equalsIgnoreCase(c.getCategory()))
                        .collect(Collectors.toList());
                break;
        }

        Collections.sort(filteredList, (c1, c2) -> {
            if (c1.isCurrent() && !c2.isCurrent()) return -1;
            if (!c1.isCurrent() && c2.isCurrent()) return 1;

            if ("recent".equals(currentFilter)) {
                return Long.compare(c2.getCreatedDate(), c1.getCreatedDate());
            }

            switch (currentSort) {
                case "name_asc":
                    return c1.getName().compareToIgnoreCase(c2.getName());
                case "name_desc":
                    return c2.getName().compareToIgnoreCase(c1.getName());
                case "oldest":
                    return Long.compare(c1.getCreatedDate(), c2.getCreatedDate());
                case "most_transactions":
                    return Integer.compare(c2.getTransactionCount(), c1.getTransactionCount());
                case "balance_high":
                    return Double.compare(c2.getTotalBalance(), c1.getTotalBalance());
                case "balance_low":
                    return Double.compare(c1.getTotalBalance(), c2.getTotalBalance());
                case "recent":
                default:
                    return Long.compare(c2.getCreatedDate(), c1.getCreatedDate());
            }
        });

        cashbookAdapter.updateCashbooks(filteredList);

        // Update cashbook count text
        if (cashbookCountText != null) {
            cashbookCountText.setText("(" + formatCount(filteredList.size()) + ")");
        }

        if (allCashbooks.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
        }
    }

    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) {
            if (count % 1000 == 0) return (count / 1000) + "k";
            return String.format(Locale.US, "%.1fk", count / 1000.0);
        }
        return String.format(Locale.US, "%.1fM", count / 1000000.0);
    }

    private void onCashbookSelected(CashbookModel cashbook) {
        if (cashbook == null) return;

        // 5. FINALLY, ENSURE THE SELECTION IS SAVED EVERY TIME A BOOK IS CLICKED
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("last_selected_cashbook_id", cashbook.getCashbookId())
                .apply();

        Intent result = new Intent();
        result.putExtra("selected_cashbook_id", cashbook.getCashbookId());
        result.putExtra("cashbook_name", cashbook.getName());
        setResult(RESULT_OK, result);
        finish();
    }

    private void showLoading(boolean show) {
        isLoading = show;

        // Show or hide the shimmer skeleton container
        if (shimmerLoadingLayout != null) {
            shimmerLoadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                shimmerLoadingLayout.startShimmer();
            } else {
                shimmerLoadingLayout.stopShimmer();
            }
        }

        // Hide the actual list while loading, show it when done
        if (cashbookRecyclerView != null) {
            cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }

        if (show) {
            if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (mainContent != null) mainContent.setVisibility(show ? View.GONE : View.VISIBLE);
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
        if (categoriesListener != null && userCategoriesRef != null) {
            userCategoriesRef.removeEventListener(categoriesListener);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LABEL / CATEGORIES logic
    // ═══════════════════════════════════════════════════════════

    private void setupCategoriesListener() {
        if (repository.isLocalMode()) {
            customCategories.clear();
            for (CashbookModel cb : allCashbooks) {
                String cat = cb.getCategory();
                if (cat != null && !cat.trim().isEmpty() && !customCategories.contains(cat)) {
                    customCategories.add(cat);
                }
            }
            rebuildCategoryChips();
            return;
        }

        if (categoriesListener != null && userCategoriesRef != null) {
            userCategoriesRef.removeEventListener(categoriesListener);
        }
        categoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                customCategories.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    String catName = snap.getKey();
                    if (catName != null && !catName.trim().isEmpty()) {
                        customCategories.add(catName);
                    }
                }
                rebuildCategoryChips();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load custom categories", error.toException());
            }
        };
        userCategoriesRef.addValueEventListener(categoriesListener);
    }

    private void rebuildCategoryChips() {
        if (chipGroup == null) return;

        // Temporarily clear listener to prevent firing while we modify views
        chipGroup.setOnCheckedStateChangeListener(null);

        // Remove previously added dynamic chips
        for (com.google.android.material.chip.Chip chip : dynamicChips) {
            chipGroup.removeView(chip);
        }
        dynamicChips.clear();

        // Standard chips checking
        if ("all".equalsIgnoreCase(currentFilter)) {
            chipGroup.check(R.id.chipAll);
        } else if ("active".equalsIgnoreCase(currentFilter)) {
            chipGroup.check(R.id.chipActive);
        } else if ("recent".equalsIgnoreCase(currentFilter)) {
            chipGroup.check(R.id.chipRecent);
        } else if ("favorites".equalsIgnoreCase(currentFilter)) {
            chipGroup.check(R.id.chipFavorites);
        } else if ("inactive".equalsIgnoreCase(currentFilter)) {
            chipGroup.check(R.id.chipInactive);
        } else {
            // Uncheck standard chips since we are selecting a custom category
            chipGroup.clearCheck();
        }

        // Create and append dynamic chips
        for (String category : customCategories) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(category);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);

            styleChipWithCategoryColor(chip, category);

            chipGroup.addView(chip);
            dynamicChips.add(chip);

            // If the current filter matches this custom category, check it!
            if (category.equalsIgnoreCase(currentFilter)) {
                chip.setChecked(true);
            }
        }

        // Restore the listener
        setupFilterListener();
    }

    private void showCreateCategoryDialog() {
        showCreateCategoryDialogAndAssign(null);
    }

    private void showCreateCategoryDialogAndAssign(@Nullable CashbookModel cashbookToAssign) {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_cashbook_category, null);

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            EditText nameInput = dialogView.findViewById(R.id.categoryNameInput);
            Button btnSave = dialogView.findViewById(R.id.btnSave);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);

            btnSave.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();

                if (name.isEmpty()) {
                    showSnackbar("Please enter a category name");
                    return;
                }

                if (com.phynix.artham.utils.FirebasePathUtils.containsIllegalChars(name)) {
                    showSnackbar(com.phynix.artham.utils.FirebasePathUtils.getIllegalCharsMessage());
                    return;
                }

                saveCustomCategory(name);
                if (cashbookToAssign != null) {
                    updateCashbookCategory(cashbookToAssign, name);
                }
                dialog.dismiss();
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }

            DialogUtils.applyBlurEffect(dialog, this);
            dialog.show();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing category dialog", e);
            showSnackbar("Error opening dialog");
        }
    }

    private void showChangeCategoryDialog(CashbookModel cashbook) {
        if (cashbook == null) return;

        List<String> options = new ArrayList<>();
        options.add("None (No Category)");
        options.addAll(customCategories);

        String[] optionsArray = options.toArray(new String[0]);

        int checkedItem = 0; // Default to "None"
        String currentCat = cashbook.getCategory();
        if (currentCat != null && !currentCat.trim().isEmpty()) {
            int index = customCategories.indexOf(currentCat);
            if (index != -1) {
                checkedItem = index + 1; // offset by 1 because of "None"
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Change Category")
                .setSingleChoiceItems(optionsArray, checkedItem, (d, which) -> {
                    String selectedCategory = "";
                    if (which > 0) {
                        selectedCategory = customCategories.get(which - 1);
                    }
                    updateCashbookCategory(cashbook, selectedCategory);
                    d.dismiss();
                })
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setNeutralButton("+ New Category", (d, which) -> {
                    d.dismiss();
                    showCreateCategoryDialogAndAssign(cashbook);
                })
                .create();

        DialogUtils.applyBlurEffect(dialog, this);
        dialog.show();
    }

    private void updateCashbookCategory(CashbookModel cashbook, String category) {
        cashbook.setCategory(category);
        cashbook.setLastModified(System.currentTimeMillis());

        if (repository.isLocalMode()) {
            com.phynix.artham.db.DataRepository.LocalDataWrapper data = repository.loadLocalData();
            for (int i = 0; i < data.cashbooks.size(); i++) {
                if (data.cashbooks.get(i).getCashbookId().equals(cashbook.getCashbookId())) {
                    data.cashbooks.set(i, cashbook);
                    break;
                }
            }
            repository.saveLocalData(data);
            if (!category.isEmpty() && !customCategories.contains(category)) {
                saveCustomCategory(category);
            }
            showSnackbar("Category updated successfully");
            loadCashbooks();
            return;
        }

        userCashbooksRef.child(cashbook.getCashbookId()).child("category").setValue(category);
        userCashbooksRef.child(cashbook.getCashbookId()).child("lastModified").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    if (!category.isEmpty() && !customCategories.contains(category)) {
                        saveCustomCategory(category);
                    }
                    showSnackbar("Category updated successfully");
                })
                .addOnFailureListener(e -> showSnackbar("Failed to update category"));
    }

    private void saveCustomCategory(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) return;
        final String name = rawName.trim();

        // Validate: reject Firebase-illegal characters
        if (com.phynix.artham.utils.FirebasePathUtils.containsIllegalChars(name)) {
            showSnackbar(com.phynix.artham.utils.FirebasePathUtils.getIllegalCharsMessage());
            return;
        }

        if (repository.isLocalMode()) {
            if (!customCategories.contains(name)) {
                customCategories.add(name);
                rebuildCategoryChips();
            }
            showSnackbar("Category '" + name + "' created!");
            currentFilter = name;
            applyFiltersAndSort();
            return;
        }

        if (currentUser == null) return;

        try {
            DatabaseReference catRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(currentUser.getUid()).child("cashbookCategories");

            catRef.child(name).setValue(true)
                    .addOnSuccessListener(aVoid -> {
                        showSnackbar("Category '" + name + "' created!");
                        // Select the newly created category as active filter automatically!
                        currentFilter = name;
                        applyFiltersAndSort();
                    })
                    .addOnFailureListener(e -> showSnackbar("Failed to create category: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "Invalid Firebase path for category: " + name, e);
            showSnackbar("Invalid category name. " + com.phynix.artham.utils.FirebasePathUtils.getIllegalCharsMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ONBOARDING / TUTORIAL
    // ═══════════════════════════════════════════════════════════

    private boolean onboardingShownThisSession = false;

    private void checkAndShowOnboarding() {
        if (onboardingShownThisSession) return;
        OnboardingManager mgr = OnboardingManager.getInstance(this);
        if (mgr.isPageTutorialCompleted(OnboardingManager.PAGE_CASHBOOK_SWITCH)) return;
        onboardingShownThisSession = true;

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;

            OnboardingOverlay.builder(this)
                    .addStep(R.id.cashbookRecyclerView,
                            "Your Cashbooks",
                            "All your cashbooks appear here. Tap any one to switch to it.")
                    .addStep(R.id.includedFilterLayout,
                            "Filter Cashbooks",
                            "Filter by Active, Favorites, Recent, or Inactive cashbooks to find what you need.")
                    .addStep(R.id.quickAddFab,
                            "Create New",
                            "Tap this button to create a new cashbook for a different project, business, or trip.")
                    .setOnCompleteListener(() ->
                            OnboardingManager.getInstance(this)
                                    .markPageTutorialCompleted(OnboardingManager.PAGE_CASHBOOK_SWITCH))
                    .start();
        }, 600);
    }

    private void styleChipWithCategoryColor(Chip chip, String category) {
        int catColor = getCategoryColor(category);

        int[][] states = new int[][] {
            new int[] { android.R.attr.state_checked },
            new int[] {} // Default fallback for unchecked state
        };

        int[] colorsBg = new int[] {
            catColor,
            android.graphics.Color.TRANSPARENT
        };
        chip.setChipBackgroundColor(new android.content.res.ColorStateList(states, colorsBg));

        int[] colorsText = new int[] {
            android.graphics.Color.WHITE,
            getThemeAttrColor(R.attr.chk_textColorPrimary)
        };
        chip.setTextColor(new android.content.res.ColorStateList(states, colorsText));

        int[] colorsStroke = new int[] {
            catColor,
            catColor
        };
        chip.setChipStrokeColor(new android.content.res.ColorStateList(states, colorsStroke));

        float strokeWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        chip.setChipStrokeWidth(strokeWidthPx);

        int rippleColor = androidx.core.graphics.ColorUtils.setAlphaComponent(catColor, 40);
        chip.setRippleColor(android.content.res.ColorStateList.valueOf(rippleColor));
    }

    private int getCategoryColor(String category) {
        if (category == null || category.trim().isEmpty()) {
            return android.graphics.Color.GRAY;
        }
        int hash = category.hashCode();
        String[] colors = {
            "#3F51B5", "#009688", "#FF9800", "#E91E63", 
            "#9C27B0", "#03A9F4", "#4CAF50", "#FF5722",
            "#607D8B", "#8BC34A", "#00BCD4"
        };
        int index = Math.abs(hash) % colors.length;
        return android.graphics.Color.parseColor(colors[index]);
    }
}