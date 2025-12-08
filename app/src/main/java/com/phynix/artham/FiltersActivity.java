package com.phynix.artham;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context; // [FIX] Added context import
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue; // [FIX] Added for ThemeUtil
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * FiltersActivity - Advanced transaction filtering
 *
 * Features:
 * - Date range filtering (Today, Week, Month, Custom)
 * - Transaction type filtering (Income/Expense)
 * - Payment mode filtering (Cash/Online)
 * - Category filtering
 * - Search by remarks
 * - Tag filtering
 *
 * Updated: November 2025 - Fixed layout and theme issues
 */
public class FiltersActivity extends AppCompatActivity {

    private static final String TAG = "FiltersActivity";

    // UI Elements
    private ImageView backButton, resetButton;
    private Button filterTodayButton, filterWeekButton, filterMonthButton, clearAllButton, applyFiltersButton;
    private LinearLayout startDateLayout, endDateLayout;
    private TextView startDateText, endDateText, activeFiltersCount, selectedCategoryTextView;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private EditText searchTransactionInput, filterTagsInput;
    private LinearLayout categorySelectorLayout, partySelectorLayout;

    // Filter State
    private Calendar startCalendar, endCalendar;
    private String entryType = "All"; // "All", "IN", "OUT"
    private String paymentMode = "All"; // "All", "Cash", "Online"
    private Set<String> selectedCategories = new HashSet<>();

    private ActivityResultLauncher<Intent> categoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // [FIX] Point to the correct layout file
        setContentView(R.layout.activity_filters);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUI();
        receiveInitialFilters();
        setupCategoryLauncher();
        setupClickListeners();
        updateUIWithCurrentFilters();

        Log.d(TAG, "FiltersActivity created");
    }

    /**
     * Initialize all UI components
     */
    private void initializeUI() {
        // Header
        backButton = findViewById(R.id.backButton);
        resetButton = findViewById(R.id.resetButton);

        // Search
        searchTransactionInput = findViewById(R.id.searchTransactionInput);

        // Date Filters
        filterTodayButton = findViewById(R.id.filterTodayButton);
        filterWeekButton = findViewById(R.id.filterWeekButton);
        filterMonthButton = findViewById(R.id.filterMonthButton);
        startDateLayout = findViewById(R.id.startDateLayout);
        endDateLayout = findViewById(R.id.endDateLayout);
        startDateText = findViewById(R.id.startDateText);
        endDateText = findViewById(R.id.endDateText);

        // Type & Method Filters
        inOutToggle = findViewById(R.id.inOutToggle);
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);

        // Category & Party
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);
        partySelectorLayout = findViewById(R.id.partySelectorLayout);

        // Tags
        filterTagsInput = findViewById(R.id.filterTagsInput);

        // Bottom Bar
        activeFiltersCount = findViewById(R.id.activeFiltersCount);
        clearAllButton = findViewById(R.id.clearAllButton);
        applyFiltersButton = findViewById(R.id.applyFiltersButton);

        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();
    }

    /**
     * Receive initial filter values from intent
     */
    private void receiveInitialFilters() {
        Intent intent = getIntent();
        long initialStartDate = intent.getLongExtra("startDate", 0);
        long initialEndDate = intent.getLongExtra("endDate", 0);

        if (initialStartDate > 0) {
            startCalendar.setTimeInMillis(initialStartDate);
        } else {
            startCalendar.setTimeInMillis(0); // [FIX] Ensure it's 0 if not provided
        }

        if (initialEndDate > 0) {
            endCalendar.setTimeInMillis(initialEndDate);
        } else {
            endCalendar.setTimeInMillis(0); // [FIX] Ensure it's 0 if not provided
        }

        entryType = intent.getStringExtra("entryType");
        if (entryType == null) {
            entryType = "All";
        }

        ArrayList<String> categories = intent.getStringArrayListExtra("categories");
        if (categories != null) {
            selectedCategories = new HashSet<>(categories);
        }

        Log.d(TAG, "Initial filters received: entryType=" + entryType + ", categories=" + selectedCategories.size());
    }

    /**
     * Setup category launcher for activity result
     */
    private void setupCategoryLauncher() {
        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        // [FIX] This should be 'selected_category', not 'selected_categories' from ChooseCategoryActivity
                        String returnedCategory = result.getData().getStringExtra("selected_category");
                        if (returnedCategory != null) {
                            // This filter UI seems to only support one category, but the logic supports many.
                            // For simplicity, we'll replace the set with the one new category.
                            selectedCategories.clear();
                            if (!"No Category".equals(returnedCategory)) {
                                selectedCategories.add(returnedCategory);
                            }
                            updateUIWithCurrentFilters();
                            Log.d(TAG, "Category updated: " + selectedCategories.size());
                        }
                    }
                }
        );
    }

    /**
     * Setup all click listeners
     */
    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        resetButton.setOnClickListener(v -> clearAllFilters());
        clearAllButton.setOnClickListener(v -> clearAllFilters());
        applyFiltersButton.setOnClickListener(v -> applyFiltersAndFinish());

        // Date Listeners
        filterTodayButton.setOnClickListener(v -> setDateRangeToToday());
        filterWeekButton.setOnClickListener(v -> setDateRangeToThisWeek());
        filterMonthButton.setOnClickListener(v -> setDateRangeToThisMonth());
        startDateLayout.setOnClickListener(v -> showDatePicker(true));
        endDateLayout.setOnClickListener(v -> showDatePicker(false));

        // Category Listener
        categorySelectorLayout.setOnClickListener(v -> {
            Intent categoryIntent = new Intent(this, ChooseCategoryActivity.class);
            // Pass the first (and likely only) category back
            if (!selectedCategories.isEmpty()) {
                categoryIntent.putExtra("selected_category", selectedCategories.iterator().next());
            }
            categoryLauncher.launch(categoryIntent);
            Log.d(TAG, "Category selector opened");
        });

        // Party Listener
        partySelectorLayout.setOnClickListener(v ->
                showSnackbar("Party selection coming soon!"));

        // Radio Group Listeners
        inOutToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioIn) {
                entryType = "IN";
            } else if (checkedId == R.id.radioOut) {
                entryType = "OUT";
            }
            updateActiveFilterCount();
        });

        cashOnlineToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioCash) {
                paymentMode = "Cash";
            } else if (checkedId == R.id.radioOnline) {
                paymentMode = "Online";
            }
            updateActiveFilterCount();
        });
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendarToShow = isStartDate ? startCalendar : endCalendar;
        // [FIX] Ensure calendar is not 0
        if (calendarToShow.getTimeInMillis() == 0) {
            calendarToShow = Calendar.getInstance();
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (isStartDate) {
                        startCalendar.set(year, month, dayOfMonth, 0, 0, 0);
                        Log.d(TAG, "Start date set to: " + year + "-" + (month + 1) + "-" + dayOfMonth);
                    } else {
                        endCalendar.set(year, month, dayOfMonth, 23, 59, 59);
                        Log.d(TAG, "End date set to: " + year + "-" + (month + 1) + "-" + dayOfMonth);
                    }
                    updateUIWithCurrentFilters();
                },
                calendarToShow.get(Calendar.YEAR),
                calendarToShow.get(Calendar.MONTH),
                calendarToShow.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void setDateRangeToToday() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        updateUIWithCurrentFilters();
        showSnackbar("Filter set to Today");
        Log.d(TAG, "Date range set to Today");
    }

    private void setDateRangeToThisWeek() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.DAY_OF_WEEK, startCalendar.getFirstDayOfWeek());
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = (Calendar) startCalendar.clone();
        endCalendar.add(Calendar.DAY_OF_WEEK, 6);
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        updateUIWithCurrentFilters();
        showSnackbar("Filter set to This Week");
        Log.d(TAG, "Date range set to This Week");
    }

    private void setDateRangeToThisMonth() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        updateUIWithCurrentFilters();
        showSnackbar("Filter set to This Month");
        Log.d(TAG, "Date range set to This Month");
    }

    private void clearAllFilters() {
        startCalendar.setTimeInMillis(0);
        endCalendar.setTimeInMillis(0);
        entryType = "All";
        paymentMode = "All";
        selectedCategories.clear();

        searchTransactionInput.setText("");
        filterTagsInput.setText("");
        inOutToggle.clearCheck(); // This will default to nothing, which is fine
        cashOnlineToggle.clearCheck();

        updateUIWithCurrentFilters();
        showSnackbar("All filters cleared");
        Log.d(TAG, "All filters cleared");
    }

    private void updateUIWithCurrentFilters() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        int hintColor = ThemeUtil.getThemeAttrColor(this, R.attr.textColorHint);
        int primaryColor = ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary);

        // Update start date display
        if (startCalendar.getTimeInMillis() != 0) {
            startDateText.setText(sdf.format(startCalendar.getTime()));
            startDateText.setTextColor(primaryColor);
        } else {
            startDateText.setText("Start Date");
            startDateText.setTextColor(hintColor);
        }

        // Update end date display
        if (endCalendar.getTimeInMillis() != 0) {
            endDateText.setText(sdf.format(endCalendar.getTime()));
            endDateText.setTextColor(primaryColor);
        } else {
            endDateText.setText("End Date");
            endDateText.setTextColor(hintColor);
        }

        // Update category display
        if (selectedCategories.isEmpty()) {
            selectedCategoryTextView.setText("Select Category");
            selectedCategoryTextView.setTextColor(hintColor);
        } else {
            // Display the first selected category
            selectedCategoryTextView.setText(selectedCategories.iterator().next());
            selectedCategoryTextView.setTextColor(primaryColor);
        }

        updateActiveFilterCount();
    }

    /**
     * Update the active filters count badge
     */
    private void updateActiveFilterCount() {
        int count = 0;

        if (startCalendar.getTimeInMillis() != 0) count++;
        // [FIX] Don't count end date if it's the same as start date (e.g. "Today")
        if (endCalendar.getTimeInMillis() != 0 && endCalendar.getTimeInMillis() > startCalendar.getTimeInMillis()) count++;
        if (!"All".equals(entryType) && inOutToggle.getCheckedRadioButtonId() != -1) count++;
        if (!"All".equals(paymentMode) && cashOnlineToggle.getCheckedRadioButtonId() != -1) count++;
        if (!selectedCategories.isEmpty()) count++;
        if (!searchTransactionInput.getText().toString().trim().isEmpty()) count++;
        if (!filterTagsInput.getText().toString().trim().isEmpty()) count++;

        if (count > 0) {
            activeFiltersCount.setText(String.valueOf(count));
            activeFiltersCount.setVisibility(View.VISIBLE);
        } else {
            activeFiltersCount.setText("0");
            activeFiltersCount.setVisibility(View.GONE);
        }

        Log.d(TAG, "Active filter count: " + count);
    }

    /**
     * Apply filters and return to calling activity
     */
    private void applyFiltersAndFinish() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("startDate", startCalendar.getTimeInMillis());
        resultIntent.putExtra("endDate", endCalendar.getTimeInMillis());
        resultIntent.putExtra("entryType", entryType);
        resultIntent.putExtra("paymentMode", paymentMode);
        resultIntent.putStringArrayListExtra("categories", new ArrayList<>(selectedCategories));
        resultIntent.putExtra("searchQuery", searchTransactionInput.getText().toString());
        resultIntent.putExtra("tagsQuery", filterTagsInput.getText().toString());

        setResult(Activity.RESULT_OK, resultIntent);

        Log.d(TAG, "Filters applied and returning to caller");
        finish();
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}