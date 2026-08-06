package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.phynix.artham.activities.CategoryPickerActivity;
import com.phynix.artham.utils.AutoCategorySuggester;
import com.phynix.artham.utils.CategoryColorUtil;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.DialogUtils;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.TransactionViewModel;
import com.phynix.artham.viewmodels.TransactionViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditTransactionActivity extends BaseActivity {

    private static final String TAG = "EditTransactionActivity";
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";
    private static final int CONTACT_PERMISSION_CODE = 200;

    // UI Components
    private ImageView backButton, timePickerIcon, swapButton;
    private View calculatorButton, voiceInputButton, contactBookButton;
    private View autoRepeatButton;
    private ImageView autoRepeatIcon;
    private TextView autoRepeatText;
    private String selectedAutoFrequency = null; // null = off, "Daily", "Weekly", "Monthly"

    // Visual Category Indicators
    private View selectedIconContainer;
    private ImageView selectedCategoryIcon;

    private TextView headerSubtitle, dateTextView, timeTextView, selectedCategoryTextView, partyTextView;
    private TextView createdDateText, updatedDateText;
    private EditText amountEditText;
    private TextInputEditText remarkEditText, tagsEditText, taxAmountEditText;
    private ChipGroup tagsChipGroup;
    private TextInputLayout taxAmountLayout;
    private CheckBox taxCheckbox;
    private View taxDetailsContainer;
    private RadioGroup taxTypeToggle;
    private TextView taxBaseAmountTextView, taxCalculatedAmountTextView, taxTotalAmountTextView;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline, radioCard;
    private LinearLayout dateSelectorLayout, timeSelectorLayout;
    private View partySelectorLayout;

    // Quick Amount Buttons
    private Button quickAmount100, quickAmount500, quickAmount1000, quickAmount5000;

    // Category Selector Views (We define both to be safe!)
    private View categorySelectorLayout;
    private View categorySelectorCard;

    private Button saveChangesButton, cancelButton;

    // ViewModel & Data
    private TransactionViewModel viewModel;
    private TransactionModel currentTransaction;
    private String currentCashbookId;
    private Calendar calendar;
    private boolean manualCategorySelected = false; // Track if user manually picked a category
    private boolean hasUserMadeChanges = false; // Track if user modified any field

    // --- Activity Launchers ---
    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String category = result.getData().getStringExtra("category_name");
                    String colorHex = result.getData().getStringExtra("category_color");
                    int iconRes = result.getData().getIntExtra("category_icon_res", 0);

                    if (category != null) {
                        manualCategorySelected = true; // User explicitly picked a category
                        selectedCategoryTextView.setText(category);
                        selectedCategoryTextView.setTextColor(getThemeColor(R.attr.chk_primary_blue));
                        currentTransaction.setTransactionCategory(category);

                        // Apply Color to Icon Container safely
                        if (selectedIconContainer != null && colorHex != null) {
                            try {
                                if (!colorHex.trim().isEmpty() && !colorHex.startsWith("#")) {
                                    colorHex = "#" + colorHex;
                                }
                                selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(colorHex)));
                            } catch (Exception e) {
                                selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(getThemeColor(R.attr.chk_primary_blue)));
                            }
                        }

                        // Apply Icon safely
                        if (selectedCategoryIcon != null) {
                            if (iconRes != 0) {
                                selectedCategoryIcon.setImageResource(iconRes);
                            } else {
                                selectedCategoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon(category));
                            }
                        }
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> voiceInputLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (results != null && !results.isEmpty()) {
                        remarkEditText.setText(results.get(0));
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    retrieveContactName(contactUri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        currentTransaction = (TransactionModel) getIntent().getSerializableExtra("transaction_model");
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        if (currentTransaction == null || currentCashbookId == null) {
            showSnackbar("Error loading transaction details");
            finish();
            return;
        }

        calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTransaction.getTimestamp());

        initViewModel();
        initializeUI();
        populateData();
        setupClickListeners();

        // ── Auto Category Suggestion: analyze remark text ──
        setupAutoCategorySuggestion();

        // ── Unsaved changes: Confirm before discarding ──
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasUserMadeChanges) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(EditTransactionActivity.this)
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

        // Track field changes after initial population
        setupChangeTracking();
    }

    /**
     * Sets up TextWatchers to detect when the user modifies any field.
     * Called AFTER populateData() so initial population doesn't trigger the flag.
     */
    private void setupChangeTracking() {
        android.text.TextWatcher changeWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                hasUserMadeChanges = true;
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        if (amountEditText != null) amountEditText.addTextChangedListener(changeWatcher);
        if (remarkEditText != null) remarkEditText.addTextChangedListener(changeWatcher);
    }

    private void initViewModel() {
        TransactionViewModelFactory factory = new TransactionViewModelFactory(getApplication(), currentCashbookId);
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) showSnackbar(error);
        });
    }

    private void initializeUI() {
        backButton = findViewById(R.id.backButton);
        headerSubtitle = findViewById(R.id.headerSubtitle);

        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        timePickerIcon = findViewById(R.id.timePickerIcon);
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

        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton);

        // Initialize Quick Amount Buttons
        quickAmount100 = findViewById(R.id.quickAmount100);
        quickAmount500 = findViewById(R.id.quickAmount500);
        quickAmount1000 = findViewById(R.id.quickAmount1000);
        quickAmount5000 = findViewById(R.id.quickAmount5000);

        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);

        // Try finding both possible ID names for the category button
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);
        categorySelectorCard = findViewById(R.id.categorySelectorCard);

        selectedIconContainer = findViewById(R.id.selectedIconContainer);
        selectedCategoryIcon = findViewById(R.id.selectedCategoryIcon);

        partyTextView = findViewById(R.id.partyTextView);
        partySelectorLayout = findViewById(R.id.partySelectorLayout);
        contactBookButton = findViewById(R.id.contactBookButton);

        remarkEditText = findViewById(R.id.remarkEditText);
        voiceInputButton = findViewById(R.id.voiceInputButton);

        tagsEditText = findViewById(R.id.tagsEditText);
        tagsChipGroup = findViewById(R.id.tagsChipGroup);
        setupTagsInput();

        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxDetailsContainer = findViewById(R.id.taxDetailsContainer);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);
        taxTypeToggle = findViewById(R.id.taxTypeToggle);
        taxBaseAmountTextView = findViewById(R.id.taxBaseAmountTextView);
        taxCalculatedAmountTextView = findViewById(R.id.taxCalculatedAmountTextView);
        taxTotalAmountTextView = findViewById(R.id.taxTotalAmountTextView);

        createdDateText = findViewById(R.id.createdDateText);
        updatedDateText = findViewById(R.id.updatedDateText);

        saveChangesButton = findViewById(R.id.saveChangesButton);
        cancelButton = findViewById(R.id.CancelTransactionButton);

        // Auto repeat button
        autoRepeatButton = findViewById(R.id.autoRepeatButton);
        autoRepeatIcon = findViewById(R.id.autoRepeatIcon);
        autoRepeatText = findViewById(R.id.autoRepeatText);

        // Attach amount input validation: max 15 digits before decimal, 2 after
        amountEditText.addTextChangedListener(AmountFormatter.createAmountInputWatcher(amountEditText));
    }

    private void populateData() {
        double displayAmount = currentTransaction.getAmount();
        if (currentTransaction.getTaxRate() > 0 && !currentTransaction.isTaxInclusive()) {
            displayAmount = currentTransaction.getAmount() - currentTransaction.getTaxAmount();
        }
        if (displayAmount == (long) displayAmount) {
            amountEditText.setText(String.format(Locale.US, "%d", (long) displayAmount));
        } else {
            amountEditText.setText(String.valueOf(displayAmount));
        }

        if ("IN".equalsIgnoreCase(currentTransaction.getType())) radioIn.setChecked(true);
        else radioOut.setChecked(true);

        String mode = currentTransaction.getPaymentMode();
        if ("Online".equalsIgnoreCase(mode)) radioOnline.setChecked(true);
        else if ("Card".equalsIgnoreCase(mode) && radioCard != null) radioCard.setChecked(true);
        else radioCash.setChecked(true);

        String catName = currentTransaction.getTransactionCategory();
        selectedCategoryTextView.setText(catName);
        if (selectedIconContainer != null) {
            int catColor = CategoryColorUtil.getCategoryColor(this, catName);
            selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(catColor));
        }
        if (selectedCategoryIcon != null) {
            selectedCategoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon(catName));
        }

        String party = currentTransaction.getPartyName();
        if (party != null && !party.isEmpty()) {
            partyTextView.setText(party);
            partyTextView.setTextColor(getThemeColor(R.attr.chk_primary_blue));
        } else {
            partyTextView.setText("");
        }

        if (currentTransaction.getRemark() != null) remarkEditText.setText(currentTransaction.getRemark());

        if (tagsChipGroup != null) {
            tagsChipGroup.removeAllViews();
        }
        String currentTags = currentTransaction.getTags();
        if (currentTags != null && !currentTags.trim().isEmpty()) {
            String cleanTags = currentTags.replace("[", "").replace("]", "");
            String[] splitTags = cleanTags.split(",");
            for (String t : splitTags) {
                String trimT = t.trim();
                if (!trimT.isEmpty()) {
                    addTagChip(trimT);
                }
            }
        }

        updateDateText();
        updateTimeText();

        // Load auto-repeat frequency
        String autoFreq = currentTransaction.getAutoFrequency();
        if (autoFreq != null && !autoFreq.isEmpty()) {
            selectedAutoFrequency = autoFreq;
            updateAutoRepeatButtonState();
        }

        SimpleDateFormat headerDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (headerSubtitle != null) headerSubtitle.setText("Last modified: " + headerDateFormat.format(new Date()));

        SimpleDateFormat historySdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        if (createdDateText != null) createdDateText.setText(historySdf.format(currentTransaction.getTimestamp()));
        if (updatedDateText != null) updatedDateText.setText(historySdf.format(System.currentTimeMillis()));

        // Populate Tax/GST details
        if (currentTransaction.getTaxRate() > 0) {
            taxCheckbox.setChecked(true);
            taxAmountEditText.setText(String.valueOf(currentTransaction.getTaxRate()));
            if (currentTransaction.isTaxInclusive()) {
                taxTypeToggle.check(R.id.radioTaxInclusive);
            } else {
                taxTypeToggle.check(R.id.radioTaxExclusive);
            }
            if (taxDetailsContainer != null) taxDetailsContainer.setVisibility(View.VISIBLE);
        } else {
            taxCheckbox.setChecked(false);
            if (taxDetailsContainer != null) taxDetailsContainer.setVisibility(View.GONE);
        }
        recalculateTax();
    }

    private void setupClickListeners() {
        if (backButton != null) backButton.setOnClickListener(v -> finish());
        if (cancelButton != null) cancelButton.setOnClickListener(v -> finish());

        View.OnClickListener dateListener = v -> showDatePicker();
        if (dateSelectorLayout != null) dateSelectorLayout.setOnClickListener(dateListener);
        if (dateTextView != null) dateTextView.setOnClickListener(dateListener);

        View.OnClickListener timeListener = v -> showTimePicker();
        if (timeSelectorLayout != null) timeSelectorLayout.setOnClickListener(timeListener);
        if (timeTextView != null) timeTextView.setOnClickListener(timeListener);
        if (timePickerIcon != null) timePickerIcon.setOnClickListener(timeListener);

        if (calculatorButton != null) calculatorButton.setOnClickListener(v -> checkAndOpenCalculator());
        
        if (voiceInputButton != null) {
            voiceInputButton.setOnClickListener(v -> startVoiceInput());
        }

        // Setup Quick Amount Buttons
        setupQuickAmountButtons();

        // We attach the click listener to BOTH possible ID variables, and the Text View!
        View.OnClickListener categoryClickListener = v -> {
            Intent intent = new Intent(this, CategoryPickerActivity.class);
            intent.putExtra("selected_category", selectedCategoryTextView.getText().toString());
            intent.putExtra("cashbook_id", currentCashbookId);
            String type = radioIn.isChecked() ? "IN" : "OUT";
            intent.putExtra("type", type);
            categoryLauncher.launch(intent);
        };

        if (categorySelectorLayout != null) categorySelectorLayout.setOnClickListener(categoryClickListener);
        if (categorySelectorCard != null) categorySelectorCard.setOnClickListener(categoryClickListener);
        if (selectedCategoryTextView != null) selectedCategoryTextView.setOnClickListener(categoryClickListener);

        if (contactBookButton != null) {
            contactBookButton.setOnClickListener(v -> {
                if (checkContactPermission()) {
                    openContactPicker();
                } else {
                    requestContactPermission();
                }
            });
        }

        if (taxCheckbox != null) {
            taxCheckbox.setOnCheckedChangeListener((bv, isChecked) -> {
                if (taxDetailsContainer != null) {
                    taxDetailsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
                recalculateTax();
            });
        }

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
        if (taxAmountEditText != null) {
            taxAmountEditText.addTextChangedListener(taxTextWatcher);
        }

        if (taxTypeToggle != null) {
            taxTypeToggle.setOnCheckedChangeListener((group, checkedId) -> recalculateTax());
        }

        if (swapButton != null) {
            swapButton.setOnClickListener(v -> {
                if (radioIn.isChecked()) radioOut.setChecked(true);
                else radioIn.setChecked(true);
            });
        }

        if (saveChangesButton != null) saveChangesButton.setOnClickListener(v -> saveChanges());

        if (autoRepeatButton != null) {
            autoRepeatButton.setOnClickListener(v -> showAutoFrequencyDialog());
        }
    }

    // THE FIX: Quick Amount Logic Added
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


    private void saveChanges() {
        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount required");
            return;
        }

        try {
            double enteredAmount = Double.parseDouble(amountStr);
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

            currentTransaction.setAmount(finalAmount);
            currentTransaction.setTaxRate(taxRate);
            currentTransaction.setTaxAmount(taxAmount);
            currentTransaction.setTaxInclusive(isInclusive);
            currentTransaction.setType(radioIn.isChecked() ? "IN" : "OUT");

            String mode = "Cash";
            if (radioOnline.isChecked()) mode = "Online";
            else if (radioCard != null && radioCard.isChecked()) mode = "Card";
            currentTransaction.setPaymentMode(mode);

            currentTransaction.setTransactionCategory(selectedCategoryTextView.getText().toString());
            currentTransaction.setTimestamp(calendar.getTimeInMillis());

            String party = partyTextView.getText().toString();
            if(party.equals("Select Party")) party = "";
            currentTransaction.setPartyName(party);

            currentTransaction.setRemark(remarkEditText.getText().toString().trim());

            String tagsStr = getTagsFromChips();
            currentTransaction.setTags(tagsStr);
            currentTransaction.setAutoFrequency(selectedAutoFrequency);

            viewModel.updateTransaction(currentTransaction);

            showSnackbar("Transaction Updated");
            Intent result = new Intent();
            setResult(RESULT_OK, result);
            finish();

        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid amount");
        }
    }

    private void deleteTransaction() {
        viewModel.deleteTransaction(currentTransaction.getTransactionId());
        showSnackbar("Transaction Deleted");
        finish();
    }

    private void recalculateTax() {
        if (taxBaseAmountTextView == null || taxCalculatedAmountTextView == null || taxTotalAmountTextView == null) return;

        if (taxCheckbox == null || !taxCheckbox.isChecked()) {
            taxBaseAmountTextView.setText("₹0.00");
            taxCalculatedAmountTextView.setText("₹0.00");
            taxTotalAmountTextView.setText("₹0.00");
            return;
        }

        String amountStr = amountEditText.getText().toString().trim();
        String taxRateStr = taxAmountEditText != null ? taxAmountEditText.getText().toString().trim() : "";

        double enteredAmount = 0.0;
        double taxRate = 0.0;

        try {
            if (!amountStr.isEmpty()) {
                enteredAmount = Double.parseDouble(amountStr);
            }
        } catch (NumberFormatException ignored) {}

        try {
            if (taxRateStr != null && !taxRateStr.isEmpty()) {
                taxRate = Double.parseDouble(taxRateStr);
            }
        } catch (NumberFormatException ignored) {}

        boolean isInclusive = taxTypeToggle != null && taxTypeToggle.getCheckedRadioButtonId() == R.id.radioTaxInclusive;

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



    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private boolean checkContactPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestContactPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACT_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACT_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openContactPicker();
            } else {
                Toast.makeText(this, "Permission Denied. Cannot access contacts.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("Range")
    private void retrieveContactName(Uri contactUri) {
        try (Cursor cursor = getContentResolver().query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                if (name != null) {
                    partyTextView.setText(name);
                    partyTextView.setTextColor(getThemeColor(R.attr.chk_primary_blue));
                    currentTransaction.setPartyName(name);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to get contact name", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (dateTextView != null) dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateTimeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        if (timeTextView != null) timeTextView.setText(sdf.format(calendar.getTime()));
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            updateDateText();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            updateTimeText();
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void checkAndOpenCalculator() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_CALCULATOR, true)) {
            showBuiltInCalculator();
        } else {
            openSystemCalculator();
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak remark");
        try {
            voiceInputLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show();
        }
    }

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
                    isNewInput[0] = true;
                    break;
                default:
                    boolean isOperator = text.equals("+") || text.equals("-") ||
                            text.equals("×") || text.equals("÷") || text.equals("%");
                    if (isNewInput[0] && !isOperator) {
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

        int[] btnIds = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_dot, R.id.btn_plus, R.id.btn_minus, R.id.btn_multiply, R.id.btn_divide, R.id.btn_percent, R.id.btn_clear, R.id.btn_backspace, R.id.btn_equals};
        for (int id : btnIds) view.findViewById(id).setOnClickListener(listener);

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
        } catch (Exception e) { Toast.makeText(this, "Calculator not found", Toast.LENGTH_SHORT).show(); }
    }

    private void showSnackbar(String message) {
        SnackbarHelper.show(this, message, findViewById(R.id.footerLayout));
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.BLACK;
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
            autoRepeatText.setText(selectedAutoFrequency);
            autoRepeatText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            autoRepeatIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.white));
        } else {
            autoRepeatButton.setSelected(false);
            autoRepeatText.setText("Auto");
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.chk_textColorSecondary, typedValue, true);
            autoRepeatText.setTextColor(typedValue.data);
            autoRepeatIcon.setColorFilter(typedValue.data);
        }
    }

    // ===== AUTO CATEGORY SUGGESTION =====

    private void setupAutoCategorySuggestion() {
        if (remarkEditText == null) return;

        remarkEditText.addTextChangedListener(AutoCategorySuggester.createAutoSuggestWatcher(categoryName -> {
            try {
                // Only auto-suggest if user hasn't manually picked a category in this session
                if (manualCategorySelected) return;
                if (selectedCategoryTextView == null || currentTransaction == null) return;

                // Only auto-suggest if the current category is "Other" or generic
                String currentCat = selectedCategoryTextView.getText().toString().trim();
                boolean isDefaultCategory = "Other".equalsIgnoreCase(currentCat)
                        || "Other Expenses".equalsIgnoreCase(currentCat)
                        || "Other Income".equalsIgnoreCase(currentCat)
                        || "Select Category".equalsIgnoreCase(currentCat)
                        || currentCat.isEmpty();

                // Also allow overriding if the current category was previously auto-suggested
                boolean wasAutoSuggested = !isDefaultCategory && !manualCategorySelected;

                if (!isDefaultCategory && !wasAutoSuggested) return;

                if (categoryName != null) {
                    // Found a match — apply it
                    com.phynix.artham.models.CategoryModel model = AutoCategorySuggester.getSuggestedCategoryModel(categoryName);
                    if (model != null) {
                        selectedCategoryTextView.setText(model.getName());
                        selectedCategoryTextView.setTextColor(getThemeColor(R.attr.chk_primary_blue));
                        currentTransaction.setTransactionCategory(model.getName());

                        // Apply color to icon container
                        if (selectedIconContainer != null) {
                            try {
                                selectedIconContainer.setBackgroundTintList(
                                        ColorStateList.valueOf(Color.parseColor(model.getColorHex())));
                            } catch (Exception e) {
                                selectedIconContainer.setBackgroundTintList(
                                        ColorStateList.valueOf(getThemeColor(R.attr.chk_primary_blue)));
                            }
                        }

                        // Apply icon
                        if (selectedCategoryIcon != null) {
                            selectedCategoryIcon.setImageResource(model.getIconResId());
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("EditTransactionActivity", "Auto-suggest error: " + e.getMessage());
            }
        }));
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