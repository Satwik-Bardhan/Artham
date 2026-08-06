package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

// Using the newly updated CategoryPickerActivity
import com.phynix.artham.activities.CategoryPickerActivity;
import com.phynix.artham.utils.AutoCategorySuggester;
import com.phynix.artham.utils.CategoryColorUtil;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.DialogUtils;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.utils.OnboardingManager;
import com.phynix.artham.utils.OnboardingOverlay;
import com.phynix.artham.viewmodels.CashInOutViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends BaseActivity {

    private static final String TAG = "CashInOutActivity";
    private static final int CONTACTS_PERMISSION_REQUEST_CODE = 1002;
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    // UI Elements
    private TextView headerTitle, headerSubtitle, autoRepeatText;
    private View autoRepeatButton;
    private ImageView autoRepeatIcon;
    private String selectedAutoFrequency = null; // null = off, "Daily", "Weekly", "Monthly"
    private ImageView backButton, selectedCategoryIcon;
    private TextView dateTextView, timeTextView, selectedCategoryTextView;
    private LinearLayout dateSelectorLayout, timeSelectorLayout;
    private MaterialCardView categorySelectorCard;
    private View selectedIconContainer;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline, radioCard;
    private View swapButton, calculatorButton, voiceInputButton, contactBookButton;
    private CheckBox taxCheckbox;
    private View taxDetailsContainer;
    private TextInputLayout taxAmountLayout;
    private TextInputEditText taxAmountEditText, remarkEditText, tagsEditText, partyTextView;
    private ChipGroup tagsChipGroup;
    private RadioGroup taxTypeToggle;
    private TextView taxBaseAmountTextView, taxCalculatedAmountTextView, taxTotalAmountTextView;
    private EditText amountEditText;
    private Button quickAmount100, quickAmount500, quickAmount1000, quickAmount5000;
    private Button saveEntryButton, saveAndAddNewButton, clearButton;
    private View loadingOverlay;

    // Logic & Data
    private CashInOutViewModel viewModel;
    private String currentCashbookId;
    private Calendar calendar;
    private String selectedCategory = "Other";
    private String selectedColorHex = "#9E9E9E"; // Default Grey
    private String selectedParty = null;
    private boolean isSaveAndNew = false;
    private boolean manualCategorySelected = false; // Track if user manually picked a category

    // Timer
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private boolean isManualTimeSet = false;
    private Runnable timeRunnable;



    // Launchers
    private ActivityResultLauncher<Intent> voiceInputLauncher;
    private ActivityResultLauncher<Intent> categoryLauncher;
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_in_out);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this).get(CashInOutViewModel.class);

        currentCashbookId = getIntent().getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        String transactionType = getIntent().getStringExtra(Constants.EXTRA_TRANSACTION_TYPE);

        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: Cashbook ID missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }



        initializeUI();
        initializeDateTime();
        setupClickListeners();
        setupActivityLaunchers();
        setupInitialState(transactionType);

        observeViewModel();
        startRealTimeClock();

        // Attach amount input validation: max 15 digits before decimal, 2 after
        amountEditText.addTextChangedListener(AmountFormatter.createAmountInputWatcher(amountEditText));

        // ── Auto Category Suggestion: analyze remark text ──
        setupAutoCategorySuggestion();

        // ── Onboarding: Show tooltips on first visit ──
        checkAndShowOnboarding();

        // ── Unsaved changes: Confirm before discarding ──
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(CashInOutActivity.this)
                            .setTitle("Discard Changes?")
                            .setMessage("You have unsaved changes. Are you sure you want to go back?")
                            .setPositiveButton("Discard", (dialog, which) -> finish())
                            .setNegativeButton("Keep Editing", null)
                            .show();
                } else {
                    finish();
                }
            }
        });
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            saveEntryButton.setEnabled(!isLoading);
            saveAndAddNewButton.setEnabled(!isLoading);
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getOperationSuccess().observe(this, success -> {
            if (success) {
                Toast.makeText(this, "Entry Saved Successfully", Toast.LENGTH_SHORT).show();
                if (isSaveAndNew) {
                    clearForm(true);
                    isSaveAndNew = false;
                } else {
                    finish();
                }
            }
        });

        viewModel.getSavedOffline().observe(this, isOffline -> {
            if (isOffline != null && isOffline) {
                Toast.makeText(this,
                        "📱 Entry saved offline — will sync when connected",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void initializeUI() {
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        backButton = findViewById(R.id.back_button);

        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        swapButton = findViewById(R.id.swap_horiz);

        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        radioCard = findViewById(R.id.radioCard);

        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxDetailsContainer = findViewById(R.id.taxDetailsContainer);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);
        taxTypeToggle = findViewById(R.id.taxTypeToggle);
        taxBaseAmountTextView = findViewById(R.id.taxBaseAmountTextView);
        taxCalculatedAmountTextView = findViewById(R.id.taxCalculatedAmountTextView);
        taxTotalAmountTextView = findViewById(R.id.taxTotalAmountTextView);

        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton);

        quickAmount100 = findViewById(R.id.quickAmount100);
        quickAmount500 = findViewById(R.id.quickAmount500);
        quickAmount1000 = findViewById(R.id.quickAmount1000);
        quickAmount5000 = findViewById(R.id.quickAmount5000);

        remarkEditText = findViewById(R.id.remarkEditText);
        voiceInputButton = findViewById(R.id.voiceInputButton);

        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorCard = findViewById(R.id.categorySelectorCard);
        selectedIconContainer = findViewById(R.id.selectedIconContainer);
        selectedCategoryIcon = findViewById(R.id.selectedCategoryIcon);

        partyTextView = findViewById(R.id.partyTextView);
        contactBookButton = findViewById(R.id.contactBookButton);

        tagsEditText = findViewById(R.id.tagsEditText);
        tagsChipGroup = findViewById(R.id.tagsChipGroup);
        setupTagsInput();

        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveAndAddNewButton = findViewById(R.id.saveAndAddNewButton);
        clearButton = findViewById(R.id.clearButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        // Auto repeat button
        autoRepeatButton = findViewById(R.id.autoRepeatButton);
        autoRepeatIcon = findViewById(R.id.autoRepeatIcon);
        autoRepeatText = findViewById(R.id.autoRepeatText);
    }

    private void initializeDateTime() {
        calendar = Calendar.getInstance();
        updateDateText();
        updateTimeText();
    }

    private void startRealTimeClock() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isManualTimeSet) {
                    calendar = Calendar.getInstance();
                    updateDateText();
                    updateTimeText();
                    timeHandler.postDelayed(this, 1000);
                }
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void stopRealTimeClock() {
        isManualTimeSet = true;
        timeHandler.removeCallbacks(timeRunnable);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        dateSelectorLayout.setOnClickListener(v -> {
            stopRealTimeClock();
            showDatePicker();
        });
        timeSelectorLayout.setOnClickListener(v -> {
            stopRealTimeClock();
            showTimePicker();
        });

        if (swapButton != null) swapButton.setOnClickListener(v -> swapTransactionType());
        if (inOutToggle != null) inOutToggle.setOnCheckedChangeListener(this::onTransactionTypeChanged);

        calculatorButton.setOnClickListener(v -> checkAndOpenCalculator());

        taxCheckbox.setOnCheckedChangeListener((bv, isChecked) -> {
            taxDetailsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            recalculateTax();
        });

        android.text.TextWatcher taxTextWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                recalculateTax();
            }
        };
        amountEditText.addTextChangedListener(taxTextWatcher);
        taxAmountEditText.addTextChangedListener(taxTextWatcher);

        taxTypeToggle.setOnCheckedChangeListener((group, checkedId) -> recalculateTax());

        if (voiceInputButton != null) {
            voiceInputButton.setOnClickListener(v -> startVoiceInput());
        }

        if (categorySelectorCard != null) {
            categorySelectorCard.setOnClickListener(v -> openCategorySelector());
        }

        if (contactBookButton != null) {
            contactBookButton.setOnClickListener(v -> openContactPicker());
        }

        saveEntryButton.setOnClickListener(v -> saveTransaction(false));
        saveAndAddNewButton.setOnClickListener(v -> saveTransaction(true));
        clearButton.setOnClickListener(v -> clearForm(false));

        if (autoRepeatButton != null) {
            autoRepeatButton.setOnClickListener(v -> showAutoFrequencyDialog());
        }

        setupQuickAmountButtons();
    }

    private void setupQuickAmountButtons() {
        View.OnClickListener quickAmountClickListener = v -> {
            clearQuickAmountSelections();
            v.setSelected(true);
            Button clickedButton = (Button) v;
            String amountText = clickedButton.getText().toString();
            String cleanAmount = amountText.replace("₹", "").replace("K", "000");
            amountEditText.setText(cleanAmount);
            amountEditText.setSelection(amountEditText.getText().length());
        };

        if (quickAmount100 != null) quickAmount100.setOnClickListener(quickAmountClickListener);
        if (quickAmount500 != null) quickAmount500.setOnClickListener(quickAmountClickListener);
        if (quickAmount1000 != null) quickAmount1000.setOnClickListener(quickAmountClickListener);
        if (quickAmount5000 != null) quickAmount5000.setOnClickListener(quickAmountClickListener);
    }

    private void clearQuickAmountSelections() {
        if (quickAmount100 != null) quickAmount100.setSelected(false);
        if (quickAmount500 != null) quickAmount500.setSelected(false);
        if (quickAmount1000 != null) quickAmount1000.setSelected(false);
        if (quickAmount5000 != null) quickAmount5000.setSelected(false);
    }

    private void onTransactionTypeChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.radioIn) {
            updateHeaderForTransactionType(Constants.TRANSACTION_TYPE_IN);
        } else if (checkedId == R.id.radioOut) {
            updateHeaderForTransactionType(Constants.TRANSACTION_TYPE_OUT);
        }

        // Reset category when switching between Cash In/Out since categories differ
        if (!manualCategorySelected || true) {
            selectedCategory = "Other";
            manualCategorySelected = false;
            if (selectedCategoryTextView != null) {
                selectedCategoryTextView.setText("Select Category");
                selectedCategoryTextView.setTextColor(Color.GRAY);
            }
            if (selectedCategoryIcon != null) {
                selectedCategoryIcon.setImageResource(
                        CategoryColorUtil.getCategoryIcon("Other"));
            }
            if (selectedIconContainer != null) {
                selectedIconContainer.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue)));
            }
        }
    }

    private void setupActivityLaunchers() {
        voiceInputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && !results.isEmpty()) {
                            remarkEditText.setText(results.get(0));
                        }
                    }
                }
        );

        // PERFECTED: Receives Icon, Name, and Color!
        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedCategory = result.getData().getStringExtra("category_name");
                        selectedColorHex = result.getData().getStringExtra("category_color");
                        int selectedIconRes = result.getData().getIntExtra("category_icon_res", 0);

                        if (selectedCategory != null) {
                            manualCategorySelected = true; // User explicitly picked a category
                            selectedCategoryTextView.setText(selectedCategory);
                            selectedCategoryTextView.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));

                            // Apply Color to Icon Container
                            if (selectedColorHex != null) {
                                try {
                                    if (!selectedColorHex.startsWith("#")) {
                                        selectedColorHex = "#" + selectedColorHex;
                                    }
                                    selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(selectedColorHex)));
                                } catch (Exception e) {
                                    selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue)));
                                }
                            }

                            // Apply Icon (with fallback to CategoryColorUtil)
                            if (selectedIconRes != 0) {
                                selectedCategoryIcon.setImageResource(selectedIconRes);
                            } else {
                                selectedCategoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon(selectedCategory));
                            }
                        }
                    }
                }
        );

        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri contactUri = result.getData().getData();
                        if (contactUri != null) {
                            fetchContactName(contactUri);
                        }
                    }
                }
        );
    }

    private void setupInitialState(String transactionType) {
        if (Constants.TRANSACTION_TYPE_OUT.equals(transactionType)) {
            radioOut.setChecked(true);
            updateHeaderForTransactionType(Constants.TRANSACTION_TYPE_OUT);
        } else {
            radioIn.setChecked(true);
            updateHeaderForTransactionType(Constants.TRANSACTION_TYPE_IN);
        }

        radioOnline.setChecked(true);
        amountEditText.requestFocus();

        // Safe initial UI population
        selectedCategoryTextView.setText(selectedCategory);
        try {
            selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(selectedColorHex)));
        } catch (Exception ignored) {}
        selectedCategoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon(selectedCategory));
    }

    private void updateHeaderForTransactionType(String type) {
        if (Constants.TRANSACTION_TYPE_IN.equals(type)) {
            headerTitle.setText("Add Income");
            headerSubtitle.setText("Record money received");
        } else {
            headerTitle.setText("Add Expense");
            headerSubtitle.setText("Record money spent");
        }
    }

    private void saveTransaction(boolean addNew) {
        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Required");
            return;
        }

        double enteredAmount = 0.0;
        try {
            enteredAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid number");
            return;
        }

        double taxRate = 0.0;
        double taxAmount = 0.0;
        double finalAmount = enteredAmount;
        boolean isInclusive = true;

        if (taxCheckbox.isChecked()) {
            String taxRateStr = taxAmountEditText.getText().toString().trim();
            try {
                if (!taxRateStr.isEmpty()) {
                    taxRate = Double.parseDouble(taxRateStr);
                }
            } catch (NumberFormatException ignored) {}

            isInclusive = taxTypeToggle.getCheckedRadioButtonId() == R.id.radioTaxInclusive;

            if (taxRate > 0) {
                if (isInclusive) {
                    double baseAmount = enteredAmount / (1.0 + (taxRate / 100.0));
                    taxAmount = enteredAmount - baseAmount;
                    finalAmount = enteredAmount;
                } else {
                    taxAmount = enteredAmount * (taxRate / 100.0);
                    finalAmount = enteredAmount + taxAmount;
                }
            }
        }

        TransactionModel transaction = new TransactionModel();
        transaction.setAmount(finalAmount);
        transaction.setTaxRate(taxRate);
        transaction.setTaxAmount(taxAmount);
        transaction.setTaxInclusive(isInclusive);

        transaction.setType(radioIn.isChecked() ? Constants.TRANSACTION_TYPE_IN : Constants.TRANSACTION_TYPE_OUT);
        String mode = "Online";
        if (radioCash.isChecked()) mode = "Cash";
        else if (radioCard != null && radioCard.isChecked()) mode = "Card";
        transaction.setPaymentMode(mode);
        transaction.setTransactionCategory(selectedCategory);
        transaction.setTimestamp(calendar.getTimeInMillis());
        transaction.setRemark(remarkEditText.getText().toString().trim());

        String party = partyTextView.getText().toString().trim();
        if (!party.isEmpty()) {
            transaction.setPartyName(party);
        }

        String tags = getTagsFromChips();
        if (!tags.isEmpty()) {
            transaction.setTags(tags);
        }

        transaction.setAutoFrequency(selectedAutoFrequency);

        isSaveAndNew = addNew;
        viewModel.saveTransaction(currentCashbookId, transaction);
    }

    private void clearForm(boolean preserveContext) {
        amountEditText.setText("");
        remarkEditText.setText("");
        tagsEditText.setText("");
        if (tagsChipGroup != null) tagsChipGroup.removeAllViews();
        selectedCategoryTextView.setText("Select Category");
        partyTextView.setText("");

        selectedCategory = "Other";
        selectedColorHex = "#9E9E9E";
        manualCategorySelected = false; // Reset so auto-suggest works again

        // Reset Visuals
        selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(selectedColorHex)));
        selectedCategoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon(selectedCategory));

        selectedParty = null;

        clearQuickAmountSelections();

        if (!preserveContext) {
            radioIn.setChecked(true);
            radioOnline.setChecked(true);
        }

        taxCheckbox.setChecked(false);
        taxAmountEditText.setText("");
        taxTypeToggle.check(R.id.radioTaxInclusive);
        taxDetailsContainer.setVisibility(View.GONE);
        recalculateTax();

        isManualTimeSet = false;
        startRealTimeClock();
        amountEditText.requestFocus();
    }

    private void recalculateTax() {
        if (taxBaseAmountTextView == null || taxCalculatedAmountTextView == null || taxTotalAmountTextView == null) return;

        if (!taxCheckbox.isChecked()) {
            taxBaseAmountTextView.setText("₹0.00");
            taxCalculatedAmountTextView.setText("₹0.00");
            taxTotalAmountTextView.setText("₹0.00");
            return;
        }

        String amountStr = amountEditText.getText().toString().trim();
        String taxRateStr = taxAmountEditText.getText().toString().trim();

        double enteredAmount = 0.0;
        double taxRate = 0.0;

        try {
            if (!amountStr.isEmpty()) {
                enteredAmount = Double.parseDouble(amountStr);
            }
        } catch (NumberFormatException ignored) {}

        try {
            if (!taxRateStr.isEmpty()) {
                taxRate = Double.parseDouble(taxRateStr);
            }
        } catch (NumberFormatException ignored) {}

        boolean isInclusive = taxTypeToggle.getCheckedRadioButtonId() == R.id.radioTaxInclusive;

        double baseAmount = 0.0;
        double taxAmount = 0.0;
        double totalAmount = 0.0;

        if (taxRate <= 0.0) {
            baseAmount = enteredAmount;
            taxAmount = 0.0;
            totalAmount = enteredAmount;
        } else {
            if (isInclusive) {
                totalAmount = enteredAmount;
                baseAmount = enteredAmount / (1.0 + (taxRate / 100.0));
                taxAmount = enteredAmount - baseAmount;
            } else {
                baseAmount = enteredAmount;
                taxAmount = enteredAmount * (taxRate / 100.0);
                totalAmount = enteredAmount + taxAmount;
            }
        }

        taxBaseAmountTextView.setText(String.format(Locale.US, "₹%.2f", baseAmount));
        taxCalculatedAmountTextView.setText(String.format(Locale.US, "₹%.2f", taxAmount));
        taxTotalAmountTextView.setText(String.format(Locale.US, "₹%.2f", totalAmount));
    }

    private void showDatePicker() {
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateText();
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateTimeText();
                },
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void updateDateText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.DATE_FORMAT_DISPLAY, Locale.US);
        dateTextView.setText(dateFormat.format(calendar.getTime()));
    }

    private void updateTimeText() {
        SimpleDateFormat timeFormat = new SimpleDateFormat(Constants.TIME_FORMAT_DISPLAY, Locale.US);
        timeTextView.setText(timeFormat.format(calendar.getTime()));
    }

    private void swapTransactionType() {
        if (radioIn.isChecked()) {
            radioOut.setChecked(true);
        } else {
            radioIn.setChecked(true);
        }
    }

    private void checkCalculatorSetting() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isCalculatorEnabled = prefs.getBoolean(KEY_CALCULATOR, true);
        calculatorButton.setVisibility(isCalculatorEnabled ? View.VISIBLE : View.GONE);
    }

    private void checkAndOpenCalculator() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isBuiltInEnabled = prefs.getBoolean(KEY_CALCULATOR, true);

        if (isBuiltInEnabled) {
            showBuiltInCalculator();
        } else {
            openSystemCalculator();
        }
    }

    // --- FULLY UPDATED CALCULATOR LOGIC ---
    private void showBuiltInCalculator() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_calculator, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView display = view.findViewById(R.id.calc_display);
        String initialValue = amountEditText.getText().toString().isEmpty() ? "0" : amountEditText.getText().toString();
        display.setText(initialValue);
        // Track if user hasn't typed yet (so first digit replaces the initial value)
        final boolean[] isNewInput = {!initialValue.equals("0")};

        View.OnClickListener listener = v -> {
            Button b = (Button) v;
            String text = b.getText().toString();
            StringBuilder expression = new StringBuilder(display.getText().toString());

            switch (text) {
                case "C":
                    expression.setLength(0);
                    display.setText("0");
                    isNewInput[0] = false;
                    break;
                case "⌫":
                    if (expression.length() > 0) expression.deleteCharAt(expression.length() - 1);
                    display.setText(expression.length() > 0 ? expression.toString() : "0");
                    isNewInput[0] = false;
                    break;
                case "=":
                    String result = safeEvaluate(expression.toString());
                    display.setText(result);
                    isNewInput[0] = true; // After evaluating, next digit should replace
                    break;
                default:
                    boolean isOperator = text.equals("+") || text.equals("-") ||
                            text.equals("×") || text.equals("÷") || text.equals("%");
                    if (isNewInput[0] && !isOperator) {
                        // First digit typed replaces the initial/previous amount
                        expression.setLength(0);
                        isNewInput[0] = false;
                    } else if (isOperator) {
                        isNewInput[0] = false;
                    }
                    if (display.getText().toString().equals("0") && !isOperator) expression.setLength(0);
                    expression.append(text);
                    display.setText(expression.toString());
                    break;
            }
        };

        int[] btnIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5,
                R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_dot,
                R.id.btn_plus, R.id.btn_minus, R.id.btn_multiply, R.id.btn_divide,
                R.id.btn_percent, R.id.btn_clear, R.id.btn_backspace, R.id.btn_equals
        };
        for (int id : btnIds) {
            View btn = view.findViewById(id);
            if (btn != null) btn.setOnClickListener(listener);
        }

        view.findViewById(R.id.btn_done).setOnClickListener(v -> {
            if (!display.getText().toString().equals("Error")) {
                String evaluated = safeEvaluate(display.getText().toString());
                if (!evaluated.equals("Error")) {
                    amountEditText.setText(evaluated);
                    amountEditText.setSelection(amountEditText.getText().length());
                } else {
                    Toast.makeText(this, "Invalid calculation", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            dialog.dismiss();
        });
        DialogUtils.applyBlurEffect(dialog, this);
        dialog.show();
    }

    private String safeEvaluate(String expression) {
        try {
            // Normalize Unicode operators to standard characters
            expression = expression.replace("×", "*");
            expression = expression.replace("÷", "/");
            expression = expression.replace("%", "/100");

            // Tokenize the expression into numbers and operators
            java.util.List<String> tokens = new java.util.ArrayList<>();
            StringBuilder currentNumber = new StringBuilder();

            for (int i = 0; i < expression.length(); i++) {
                char c = expression.charAt(i);
                // Handle negative numbers at the start or after an operator
                if (c == '-' && (i == 0 || "+-*/".indexOf(expression.charAt(i - 1)) >= 0)) {
                    currentNumber.append(c);
                } else if ("+-*/".indexOf(c) >= 0) {
                    if (currentNumber.length() > 0) {
                        tokens.add(currentNumber.toString());
                        currentNumber.setLength(0);
                    }
                    tokens.add(String.valueOf(c));
                } else {
                    currentNumber.append(c);
                }
            }
            if (currentNumber.length() > 0) {
                tokens.add(currentNumber.toString());
            }

            if (tokens.isEmpty()) return expression;

            // Evaluate left-to-right
            double result = Double.parseDouble(tokens.get(0));
            for (int i = 1; i < tokens.size() - 1; i += 2) {
                String operator = tokens.get(i);
                double nextNum = Double.parseDouble(tokens.get(i + 1));
                switch (operator) {
                    case "+": result += nextNum; break;
                    case "-": result -= nextNum; break;
                    case "*": result *= nextNum; break;
                    case "/": result /= nextNum; break;
                }
            }

            return formatCalcResult(result);
        } catch (Exception e) {
            return "Error";
        }
    }

    private String formatCalcResult(double result) {
        if (result == (long) result) {
            return String.format(Locale.US, "%d", (long) result);
        } else {
            return String.format(Locale.US, "%.2f", result);
        }
    }

    private void openSystemCalculator() {
        try {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Calculator app not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try {
            voiceInputLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCategorySelector() {
        Intent intent = new Intent(this, CategoryPickerActivity.class);
        intent.putExtra(Constants.EXTRA_CASHBOOK_ID, currentCashbookId);
        intent.putExtra("type", radioIn.isChecked() ? Constants.TRANSACTION_TYPE_IN : Constants.TRANSACTION_TYPE_OUT);
        categoryLauncher.launch(intent);
    }



    private void openContactPicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_REQUEST_CODE);
        } else {
            launchContactPicker();
        }
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    @SuppressLint("Range")
    private void fetchContactName(Uri contactUri) {
        try (Cursor cursor = getContentResolver().query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                if (name != null) {
                    selectedParty = name;
                    partyTextView.setText(name);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load contact", Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE) launchContactPicker();
        }
    }

    // ===== AUTO CATEGORY SUGGESTION =====

    private void setupAutoCategorySuggestion() {
        if (remarkEditText == null) return;

        remarkEditText.addTextChangedListener(AutoCategorySuggester.createAutoSuggestWatcher(categoryName -> {
            try {
                // Only auto-suggest if the user hasn't manually picked a category
                if (manualCategorySelected) return;

                if (categoryName != null) {
                    // Found a match — apply it
                    com.phynix.artham.models.CategoryModel model = AutoCategorySuggester.getSuggestedCategoryModel(categoryName);
                    if (model != null) {
                        selectedCategory = model.getName();
                        selectedColorHex = model.getColorHex();

                        if (selectedCategoryTextView != null) {
                            selectedCategoryTextView.setText(selectedCategory);
                            selectedCategoryTextView.setTextColor(ContextCompat.getColor(CashInOutActivity.this, R.color.primary_blue));
                        }

                        // Apply color to icon container
                        if (selectedIconContainer != null) {
                            try {
                                selectedIconContainer.setBackgroundTintList(
                                        ColorStateList.valueOf(Color.parseColor(selectedColorHex)));
                            } catch (Exception e) {
                                selectedIconContainer.setBackgroundTintList(
                                        ColorStateList.valueOf(ContextCompat.getColor(CashInOutActivity.this, R.color.primary_blue)));
                            }
                        }

                        // Apply icon
                        if (selectedCategoryIcon != null) {
                            selectedCategoryIcon.setImageResource(model.getIconResId());
                        }
                    }
                } else {
                    // No match — reset to default only if auto-suggested previously
                    if (!"Other".equals(selectedCategory) && !manualCategorySelected) {
                        selectedCategory = "Other";
                        selectedColorHex = "#9E9E9E";
                        if (selectedCategoryTextView != null) {
                            selectedCategoryTextView.setText("Select Category");
                        }
                        if (selectedIconContainer != null) {
                            try {
                                selectedIconContainer.setBackgroundTintList(
                                        ColorStateList.valueOf(Color.parseColor(selectedColorHex)));
                            } catch (Exception ignored) {}
                        }
                        if (selectedCategoryIcon != null) {
                            selectedCategoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon("Other"));
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("CashInOutActivity", "Auto-suggest error: " + e.getMessage());
            }
        }));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeHandler.removeCallbacks(timeRunnable);
    }

    /**
     * Checks if the user has entered any data that would be lost on back press.
     */
    private boolean hasUnsavedChanges() {
        if (amountEditText != null && amountEditText.getText() != null) {
            String amount = amountEditText.getText().toString().trim();
            if (!amount.isEmpty() && !"0".equals(amount)) return true;
        }
        if (remarkEditText != null && remarkEditText.getText() != null) {
            String remark = remarkEditText.getText().toString().trim();
            if (!remark.isEmpty()) return true;
        }
        return false;
    }

    // ===== AUTO REPEAT LOGIC =====

    private void showAutoFrequencyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_auto_frequency, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View optionDaily = dialogView.findViewById(R.id.optionDaily);
        View optionWeekly = dialogView.findViewById(R.id.optionWeekly);
        View optionMonthly = dialogView.findViewById(R.id.optionMonthly);
        TextView btnDisable = dialogView.findViewById(R.id.btnDisableAuto);

        // Show disable button if auto is currently active
        if (selectedAutoFrequency != null) {
            btnDisable.setVisibility(View.VISIBLE);
            // Highlight the currently selected option
            if ("Daily".equals(selectedAutoFrequency)) optionDaily.setSelected(true);
            else if ("Weekly".equals(selectedAutoFrequency)) optionWeekly.setSelected(true);
            else if ("Monthly".equals(selectedAutoFrequency)) optionMonthly.setSelected(true);
        }

        View.OnClickListener frequencyClick = v -> {
            String frequency = "";
            if (v.getId() == R.id.optionDaily) frequency = "Daily";
            else if (v.getId() == R.id.optionWeekly) frequency = "Weekly";
            else if (v.getId() == R.id.optionMonthly) frequency = "Monthly";

            selectedAutoFrequency = frequency;
            updateAutoRepeatButtonState();
            Toast.makeText(this, "Auto repeat set to " + frequency, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        };

        optionDaily.setOnClickListener(frequencyClick);
        optionWeekly.setOnClickListener(frequencyClick);
        optionMonthly.setOnClickListener(frequencyClick);

        btnDisable.setOnClickListener(v -> {
            selectedAutoFrequency = null;
            updateAutoRepeatButtonState();
            Toast.makeText(this, "Auto repeat disabled", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        DialogUtils.applyBlurEffect(dialog, this);
        dialog.show();
    }

    private void updateAutoRepeatButtonState() {
        if (autoRepeatButton == null || autoRepeatText == null || autoRepeatIcon == null) return;

        if (selectedAutoFrequency != null) {
            autoRepeatButton.setSelected(true);
            autoRepeatText.setText("Auto · " + selectedAutoFrequency);
            autoRepeatText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            autoRepeatIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.white));
        } else {
            autoRepeatButton.setSelected(false);
            autoRepeatText.setText("Auto Add Entries");
            // Reset to theme secondary text color
            android.util.TypedValue typedValue = new android.util.TypedValue();
            getTheme().resolveAttribute(R.attr.chk_textColorSecondary, typedValue, true);
            autoRepeatText.setTextColor(typedValue.data);
            autoRepeatIcon.setColorFilter(typedValue.data);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ONBOARDING / TUTORIAL
    // ═══════════════════════════════════════════════════════════

    private void checkAndShowOnboarding() {
        OnboardingManager mgr = OnboardingManager.getInstance(this);
        if (mgr.isPageTutorialCompleted(OnboardingManager.PAGE_CASH_IN_OUT)) return;

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;

            OnboardingOverlay.builder(this)
                    .addStep(R.id.amountEditText,
                            "Enter Amount",
                            "Type the transaction amount. Use the quick-fill chips or the built-in calculator for convenience.")
                    .addStep(R.id.taxCheckbox,
                            "GST & Taxes",
                            "Toggle this to calculate and track GST automatically (inclusive or exclusive) in real-time.")
                    .addStep(R.id.categorySelectorCard,
                            "Select Category",
                            "Choose a category to organize your transactions — Food, Travel, Bills, and more.")
                    .addStep(R.id.cashOnlineToggle,
                            "Payment Mode",
                            "Select how you paid — Cash, Online, or Card.")
                    .addStep(R.id.saveEntryButton,
                            "Save Entry",
                            "Hit Save to record the entry, or use 'Save & Add New' for bulk entries.")
                    .setOnCompleteListener(() ->
                            OnboardingManager.getInstance(this)
                                    .markPageTutorialCompleted(OnboardingManager.PAGE_CASH_IN_OUT))
                    .start();
        }, 600);
    }

    private void setupTagsInput() {
        if (tagsEditText == null || tagsChipGroup == null) return;
        tagsEditText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                String tagText = tagsEditText.getText().toString().trim();
                if (!tagText.isEmpty()) {
                    addTagChip(tagText);
                    tagsEditText.setText("");
                }
                return true;
            }
            return false;
        });
        tagsEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                String tagText = tagsEditText.getText().toString().trim();
                if (!tagText.isEmpty()) {
                    addTagChip(tagText);
                    tagsEditText.setText("");
                }
                return true;
            }
            return false;
        });
    }

    private void addTagChip(String tag) {
        if (tagsChipGroup == null) return;
        // Avoid duplicates
        for (int i = 0; i < tagsChipGroup.getChildCount(); i++) {
            View child = tagsChipGroup.getChildAt(i);
            if (child instanceof Chip) {
                if (((Chip) child).getText().toString().equalsIgnoreCase(tag)) {
                    return;
                }
            }
        }
        Chip chip = new Chip(this);
        chip.setText(tag);
        chip.setCloseIconVisible(true);
        chip.setCheckable(false);

        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(R.attr.chk_primary_blue, typedValue, true);
        int primaryColor = typedValue.data;
        getTheme().resolveAttribute(R.attr.chk_surfaceColor, typedValue, true);
        int surfaceColor = typedValue.data;
        getTheme().resolveAttribute(R.attr.chk_textColorPrimary, typedValue, true);
        int textColor = typedValue.data;

        chip.setChipBackgroundColor(ColorStateList.valueOf(surfaceColor));
        chip.setChipStrokeColor(ColorStateList.valueOf(primaryColor));
        chip.setChipStrokeWidth(1.0f * getResources().getDisplayMetrics().density);
        chip.setTextColor(textColor);
        chip.setCloseIconTint(ColorStateList.valueOf(primaryColor));
        chip.setChipCornerRadius(6.0f * getResources().getDisplayMetrics().density);

        chip.setOnCloseIconClickListener(v -> tagsChipGroup.removeView(chip));
        tagsChipGroup.addView(chip);
    }

    private String getTagsFromChips() {
        if (tagsChipGroup == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tagsChipGroup.getChildCount(); i++) {
            View child = tagsChipGroup.getChildAt(i);
            if (child instanceof Chip) {
                if (sb.length() > 0) sb.append(",");
                sb.append(((Chip) child).getText().toString().trim());
            }
        }
        return sb.toString();
    }
}