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
import com.phynix.artham.auth.AuthManager;

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

    // Firebase code removed
    private com.phynix.artham.db.DataRepository repository;

    // State
    private boolean isLoading = false;

    // Custom Categories State
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

        // Auth is validated in HomeActivity; skip redundant check here to avoid
        // false-positive logouts during rapid cashbook switching.
        if (!repository.isLocalMode() && !AuthManager.isSignedIn(this)) {
            Log.w(TAG, "Auth check failed in CashbookSwitchActivity — user may need to re-login");
            // Don't finish() here; let the user continue viewing cashbooks.
            // HomeActivity will handle the auth redirect on return.
        }

        loadSortPreference();

        if (!repository.isLocalMode() && AuthManager.isSignedIn(this)) {
            // Firebase setup removed
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
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (!repository.isLocalMode()) {
                    com.phynix.artham.db.sync.SyncEngine.triggerSync(this, () -> {
                        runOnUiThread(() -> {
                            loadCashbooks();
                            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        });
                    });
                } else {
                    loadCashbooks();
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                }
            });
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
        // Always stop swipe refresh first, regardless of isLoading state
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

        if (isLoading) return;

        showLoading(true);

        repository.getCashbooks(data -> {
            if (!isAlive()) return;
            allCashbooks.clear();
            for (CashbookModel cb : data) {
                boolean isCurrent = currentCashbookId != null && currentCashbookId.equals(cb.getCashbookId());
                cb.setCurrent(isCurrent);
                allCashbooks.add(cb);
            }
            setupCategoriesListener();
            applyFiltersAndSort();
            showLoading(false);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            checkAndShowOnboarding();
        }, error -> {
            if (!isAlive()) return;
            showLoading(false);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            showSnackbar(error);
        });
    }

    private void handleAddNewCashbook() {
        if (repository.isLocalMode() && allCashbooks.size() >= 1) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Backup Required")
                    .setMessage("Guest mode is limited to 1 cashbook. Please sign in with your Google account to create unlimited cashbooks and backup your data.")
                    .setPositiveButton("Sign In", (dialog, which) -> {
                        Intent intent = new Intent(this, com.phynix.artham.SignInActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
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

                // Validate: first character must be a letter or number; after that allow alphabets, numerics, spaces, #, comma, and dot
                if (!name.matches("^[a-zA-Z0-9][a-zA-Z0-9\\s#,.]*$")) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Invalid Name")
                            .setMessage("Cashbook name must start with a letter or number. After that, you can use alphabets, numbers, spaces, #, comma (,) and dot (.)")
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
    }    private void createNewCashbook(String name, String description, String category, String color, String icon) {
        String cashbookId = (repository.isLocalMode() ? "local_cb_" : "cb_") + UUID.randomUUID().toString();
        CashbookModel newCashbook = new CashbookModel(cashbookId, name);
        newCashbook.setDescription(description);
        newCashbook.setCategory(category);
        newCashbook.setThemeColor(color);
        newCashbook.setThemeIcon(icon);
        newCashbook.setUserId(AuthManager.getUserId(this));
        newCashbook.setCreatedDate(System.currentTimeMillis());
        newCashbook.setLastModified(System.currentTimeMillis());
        newCashbook.setActive(true);

        repository.createNewCashbook(newCashbook, successId -> {
            if (!isAlive()) return;
            if (!category.isEmpty()) {
                saveCustomCategory(category);
            }
            if (repository.isLocalMode()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Backup Your Cashbook")
                        .setMessage("Your cashbook has been created locally! Please sign in with Google now to back it up and prevent data loss.")
                        .setPositiveButton("Sign In", (dialog, which) -> {
                            Intent intent = new Intent(this, com.phynix.artham.SignInActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("Keep Offline", (dialog, which) -> {
                            showSnackbar("Cashbook created successfully!");
                            onCashbookSelected(newCashbook);
                        })
                        .show();
            } else {
                showSnackbar("Cashbook created successfully!");
                onCashbookSelected(newCashbook);
            }
        }, error -> {
            if (!isAlive()) return;
            showSnackbar("Failed to create cashbook: " + error);
        });
    }

    private void updateCashbook(CashbookModel cashbook, String newName, String newDescription, String category, String color, String icon) {
        cashbook.setName(newName);
        cashbook.setDescription(newDescription);
        cashbook.setCategory(category);
        cashbook.setThemeColor(color);
        cashbook.setThemeIcon(icon);
        cashbook.setLastModified(System.currentTimeMillis());

        repository.updateCashbook(cashbook, success -> {
            if (!isAlive()) return;
            if (!category.isEmpty()) {
                saveCustomCategory(category);
            }
            showSnackbar("Cashbook updated");
            loadCashbooks();
        });
    }

    private void handleFavoriteToggle(CashbookModel cashbook) {
        if (cashbook == null) return;
        boolean newFavoriteState = !cashbook.isFavorite();
        cashbook.setFavorite(newFavoriteState);
        cashbook.setLastModified(System.currentTimeMillis());

        if (cashbookAdapter != null) {
            cashbookAdapter.notifyDataSetChanged();
        }

        repository.updateCashbook(cashbook, success -> {
            if (!isAlive()) return;
            showSnackbar(newFavoriteState ? "Added to favorites" : "Removed from favorites");
            loadCashbooks();
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
                exportCashbookAsPdf(cashbook);
                return true;
            } else if (itemId == R.id.menu_copy) {
                duplicateCashbook(cashbook);
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

    private void duplicateCashbook(CashbookModel original) {
        if (original == null) return;

        String newName = "Copy of " + original.getName();
        repository.duplicateCashbook(original.getCashbookId(), newName, newId -> {
            if (!isAlive()) return;
            showSnackbar("Cashbook duplicated with all entries: " + newName);
            loadCashbooks();
        }, error -> {
            if (!isAlive()) return;
            showSnackbar("Failed to duplicate: " + error);
        });
    }

    private void exportCashbookAsPdf(CashbookModel cashbook) {
        if (cashbook == null) return;

        showSnackbar("Preparing export...");

        repository.getAllTransactions(cashbook.getCashbookId(), transactions -> {
            if (!isAlive()) return;
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
            String bookName = (cashbook.getName() != null && !cashbook.getName().trim().isEmpty())
                    ? cashbook.getName().trim() : "Cashbook";
            android.net.Uri fileUri = PdfReportGenerator.generateReport(
                    CashbookSwitchActivity.this,
                    transactions,
                    bookName,
                    earliest,
                    latest
            );
            if (fileUri != null) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(CashbookSwitchActivity.this)
                        .setTitle("Export Successful")
                        .setMessage("\"" + bookName + "\" report saved to Downloads/Artham. Open it now?")
                        .setPositiveButton("Open", (dialog, which) -> {
                            try {
                                android.content.Intent openIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                                openIntent.setDataAndType(fileUri, "application/pdf");
                                openIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                openIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY);
                                startActivity(openIntent);
                            } catch (android.content.ActivityNotFoundException e) {
                                showSnackbar("No application found to open PDF files");
                            }
                        })
                        .setNegativeButton("Maybe Later", null)
                        .show();
            }
        }, error -> {
            if (!isAlive()) return;
            showSnackbar("Failed to fetch transactions for export: " + error);
        });
    }

    private void toggleCashbookActive(CashbookModel cashbook) {
        boolean newActiveState = !cashbook.isActive();
        cashbook.setActive(newActiveState);
        cashbook.setLastModified(System.currentTimeMillis());

        repository.updateCashbook(cashbook, success -> {
            if (!isAlive()) return;
            showSnackbar(newActiveState ? "Cashbook activated" : "Cashbook deactivated");
            loadCashbooks();
        });
    }

    private void showDeleteConfirmation(CashbookModel cashbook) {
        if (allCashbooks.size() <= 1) {
            showSnackbar(getString(R.string.error_delete_last_cashbook));
            return;
        }

        // Build appropriate warning message for active/current cashbook
        String message;
        if (cashbook.isCurrent()) {
            message = getString(R.string.msg_delete_cashbook_confirmation, cashbook.getName())
                    + "\n\nThis is your currently selected cashbook. Another cashbook will be selected automatically.";
        } else {
            message = getString(R.string.msg_delete_cashbook_confirmation, cashbook.getName());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.title_delete_cashbook))
                .setMessage(message)
                .setPositiveButton(getString(R.string.btn_delete), (d, which) -> deleteCashbookFromFirebase(cashbook))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .create();

        DialogUtils.applyBlurEffect(dialog, this);
        dialog.show();
    }

    private void deleteCashbookFromFirebase(CashbookModel cashbook) {
        // If deleting the current cashbook, auto-reassign to another one first
        boolean wasCurrent = cashbook.isCurrent();

        repository.deleteCashbook(cashbook.getCashbookId(), success -> {
            if (!isAlive()) return;
            if (wasCurrent) {
                // Find another cashbook to set as current
                reassignActiveCashbook(cashbook.getCashbookId());
            }
            showSnackbar("Cashbook deleted");
            loadCashbooks();
        }, error -> {
            if (!isAlive()) return;
            showSnackbar(error);
        });
    }

    /**
     * After deleting the current cashbook, auto-select another available one.
     */
    private void reassignActiveCashbook(String deletedId) {
        CashbookModel replacement = null;
        for (CashbookModel cb : allCashbooks) {
            if (!cb.getCashbookId().equals(deletedId) && cb.isActive()) {
                replacement = cb;
                break;
            }
        }
        // Fallback: pick any remaining cashbook
        if (replacement == null) {
            for (CashbookModel cb : allCashbooks) {
                if (!cb.getCashbookId().equals(deletedId)) {
                    replacement = cb;
                    break;
                }
            }
        }
        if (replacement != null) {
            currentCashbookId = replacement.getCashbookId();
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String userId = AuthManager.getUserId(this);
            prefs.edit().putString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + userId, currentCashbookId).apply();
            replacement.setCurrent(true);
            repository.updateCashbook(replacement, s -> {
                if (!isAlive()) return;
            });
        }
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

        // Hide FAB during loading, restore when done
        if (quickAddFab != null) {
            quickAddFab.setVisibility(show ? View.GONE : View.VISIBLE);
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
        // Firebase listeners removed
    }

    // ═══════════════════════════════════════════════════════════
    //  LABEL / CATEGORIES logic
    // ═══════════════════════════════════════════════════════════

    private void setupCategoriesListener() {
        customCategories.clear();
        for (CashbookModel cb : allCashbooks) {
            String cat = cb.getCategory();
            if (cat != null && !cat.trim().isEmpty() && !customCategories.contains(cat)) {
                customCategories.add(cat);
            }
        }
        rebuildCategoryChips();
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

                if (com.phynix.artham.utils.NameValidationUtils.containsIllegalChars(name)) {
                    showSnackbar(com.phynix.artham.utils.NameValidationUtils.getIllegalCharsMessage());
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

        repository.updateCashbook(cashbook, success -> {
            if (!isAlive()) return;
            if (!category.isEmpty() && !customCategories.contains(category)) {
                saveCustomCategory(category);
            }
            showSnackbar("Category updated successfully");
            loadCashbooks();
        });
    }

    private void saveCustomCategory(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) return;
        final String name = rawName.trim();

        // Validate: reject illegal characters
        if (com.phynix.artham.utils.NameValidationUtils.containsIllegalChars(name)) {
            showSnackbar(com.phynix.artham.utils.NameValidationUtils.getIllegalCharsMessage());
            return;
        }

        if (!customCategories.contains(name)) {
            customCategories.add(name);
            rebuildCategoryChips();
        }
        showSnackbar("Category '" + name + "' created!");
        currentFilter = name;
        applyFiltersAndSort();
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