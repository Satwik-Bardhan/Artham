package com.phynix.artham;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.viewmodels.TransactionViewModel;
import com.phynix.artham.viewmodels.TransactionViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditTransactionActivity extends AppCompatActivity {

    private static final String TAG = "EditTransactionActivity";
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    // UI Components
    private ImageView backButton, menuButton, timePickerIcon, swapButton;
    private ImageView calculatorButton, voiceInputButton, locationButton;
    private TextView headerSubtitle, dateTextView, timeTextView, selectedCategoryTextView, partyTextView;
    private TextView createdDateText, updatedDateText;
    private EditText amountEditText;
    private TextInputEditText remarkEditText, tagsEditText, taxAmountEditText;
    private TextInputLayout taxAmountLayout;
    private CheckBox taxCheckbox;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline, radioCard;
    private LinearLayout dateSelectorLayout, timeSelectorLayout, categorySelectorLayout, partySelectorLayout;
    private Button saveChangesButton, cancelButton;

    // ViewModel & Data
    private TransactionViewModel viewModel;
    private TransactionModel currentTransaction;
    private String currentCashbookId;
    private Calendar calendar;

    // Activity Launchers
    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String category = result.getData().getStringExtra("selected_category");
                    if (category != null) {
                        selectedCategoryTextView.setText(category);
                        currentTransaction.setTransactionCategory(category);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // 1. Get Data from Intent
        currentTransaction = (TransactionModel) getIntent().getSerializableExtra("transaction_model");
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        if (currentTransaction == null || currentCashbookId == null) {
            showSnackbar("Error loading transaction details");
            finish();
            return;
        }

        calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTransaction.getTimestamp());

        // 2. Initialize ViewModel and UI
        initViewModel();
        initializeUI();
        populateData();
        setupClickListeners();
    }

    private void initViewModel() {
        TransactionViewModelFactory factory = new TransactionViewModelFactory(getApplication(), currentCashbookId);
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) showSnackbar(error);
        });
    }

    private void initializeUI() {
        // Headers & Navigation
        backButton = findViewById(R.id.backButton);
        menuButton = findViewById(R.id.menuButton);
        headerSubtitle = findViewById(R.id.headerSubtitle);

        // Date & Time
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        timePickerIcon = findViewById(R.id.timePickerIcon);
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        // Toggles
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        swapButton = findViewById(R.id.swap_horiz);

        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        radioCard = findViewById(R.id.radioCard);

        // Fields
        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton);

        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);
        if (categorySelectorLayout == null) categorySelectorLayout = findViewById(R.id.categoryInputLayout);

        partyTextView = findViewById(R.id.partyTextView);
        partySelectorLayout = findViewById(R.id.partySelectorLayout);

        remarkEditText = findViewById(R.id.remarkEditText);
        voiceInputButton = findViewById(R.id.voiceInputButton);

        tagsEditText = findViewById(R.id.tagsEditText);
        locationButton = findViewById(R.id.locationButton);

        // Tax
        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

        // History & Actions
        createdDateText = findViewById(R.id.createdDateText);
        updatedDateText = findViewById(R.id.updatedDateText);

        saveChangesButton = findViewById(R.id.saveChangesButton);
        cancelButton = findViewById(R.id.CancelTransactionButton);
    }

    private void populateData() {
        // Amount
        if (currentTransaction.getAmount() == (long) currentTransaction.getAmount()) {
            amountEditText.setText(String.format(Locale.US, "%d", (long) currentTransaction.getAmount()));
        } else {
            amountEditText.setText(String.valueOf(currentTransaction.getAmount()));
        }

        // Type
        if ("IN".equalsIgnoreCase(currentTransaction.getType())) radioIn.setChecked(true);
        else radioOut.setChecked(true);

        // Payment Mode
        String mode = currentTransaction.getPaymentMode();
        if ("Online".equalsIgnoreCase(mode)) radioOnline.setChecked(true);
        else if ("Card".equalsIgnoreCase(mode) && radioCard != null) radioCard.setChecked(true);
        else radioCash.setChecked(true);

        // Category & Party
        selectedCategoryTextView.setText(currentTransaction.getTransactionCategory());
        partyTextView.setText(currentTransaction.getPartyName() != null ? currentTransaction.getPartyName() : "Select Party");

        // Text Fields
        if (currentTransaction.getRemark() != null) remarkEditText.setText(currentTransaction.getRemark());
        if (currentTransaction.getTags() != null) tagsEditText.setText(currentTransaction.getTags());

        // Date & Time
        updateDateText();
        updateTimeText();

        // Last Modified Header
        SimpleDateFormat headerDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (headerSubtitle != null) headerSubtitle.setText("Last modified: " + headerDateFormat.format(new Date()));

        // History Section
        SimpleDateFormat historySdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        if (createdDateText != null) createdDateText.setText(historySdf.format(currentTransaction.getTimestamp()));
        if (updatedDateText != null) updatedDateText.setText(historySdf.format(System.currentTimeMillis()));
    }

    private void setupClickListeners() {
        if (backButton != null) backButton.setOnClickListener(v -> finish());
        if (cancelButton != null) cancelButton.setOnClickListener(v -> finish());

        // Date & Time
        View.OnClickListener dateListener = v -> showDatePicker();
        if (dateSelectorLayout != null) dateSelectorLayout.setOnClickListener(dateListener);
        if (dateTextView != null) dateTextView.setOnClickListener(dateListener);

        View.OnClickListener timeListener = v -> showTimePicker();
        if (timeSelectorLayout != null) timeSelectorLayout.setOnClickListener(timeListener);
        if (timeTextView != null) timeTextView.setOnClickListener(timeListener);
        if (timePickerIcon != null) timePickerIcon.setOnClickListener(timeListener);

        // Tools
        if (calculatorButton != null) calculatorButton.setOnClickListener(v -> checkAndOpenCalculator());
        if (voiceInputButton != null) voiceInputButton.setOnClickListener(v -> startVoiceInput());

        // Selectors
        if (categorySelectorLayout != null) {
            categorySelectorLayout.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChooseCategoryActivity.class);
                intent.putExtra("selected_category", selectedCategoryTextView.getText().toString());
                intent.putExtra("cashbook_id", currentCashbookId);
                categoryLauncher.launch(intent);
            });
        }

        if (partySelectorLayout != null) {
            partySelectorLayout.setOnClickListener(v -> openPartySelector());
        }

        // Tax Logic
        if (taxCheckbox != null) {
            taxCheckbox.setOnCheckedChangeListener((bv, isChecked) -> {
                if (taxAmountLayout != null) taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });
        }

        // Swap Type
        if (swapButton != null) {
            swapButton.setOnClickListener(v -> {
                if (radioIn.isChecked()) radioOut.setChecked(true);
                else radioIn.setChecked(true);
            });
        }

        // Main Actions
        if (saveChangesButton != null) saveChangesButton.setOnClickListener(v -> saveChanges());
        if (menuButton != null) menuButton.setOnClickListener(v -> showMoreOptionsMenu(v));
    }

    private void saveChanges() {
        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount required");
            return;
        }

        try {
            // 1. Update the Model Object
            currentTransaction.setAmount(Double.parseDouble(amountStr));
            currentTransaction.setType(radioIn.isChecked() ? "IN" : "OUT");

            String mode = "Cash";
            if (radioOnline.isChecked()) mode = "Online";
            else if (radioCard != null && radioCard.isChecked()) mode = "Card";
            currentTransaction.setPaymentMode(mode);

            currentTransaction.setTransactionCategory(selectedCategoryTextView.getText().toString());
            currentTransaction.setTimestamp(calendar.getTimeInMillis());

            String party = partyTextView.getText().toString();
            currentTransaction.setPartyName(party.equals("Select Party") || party.equals("Select Party (Customer/Supplier)") ? "" : party);

            currentTransaction.setRemark(remarkEditText.getText().toString().trim());
            if (tagsEditText != null) currentTransaction.setTags(tagsEditText.getText().toString().trim());

            // 2. Save via ViewModel
            viewModel.addTransaction(currentTransaction);

            showSnackbar("Transaction Updated");
            finish();

        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid amount");
        }
    }

    private void showMoreOptionsMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.transaction_detail_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_share_transaction) {
                shareTransaction();
                return true;
            } else if (itemId == R.id.action_duplicate_transaction) {
                duplicateTransaction();
                return true;
            } else if (itemId == R.id.action_delete_transaction) {
                showDeleteConfirmationDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void deleteTransaction() {
        viewModel.deleteTransaction(currentTransaction.getTransactionId());
        showSnackbar("Transaction Deleted");
        finish();
    }

    private void duplicateTransaction() {
        // Create a copy with a NEW ID
        TransactionModel copy = new TransactionModel();
        copy.setAmount(currentTransaction.getAmount());
        copy.setTransactionCategory(currentTransaction.getTransactionCategory());
        copy.setType(currentTransaction.getType());
        copy.setPaymentMode(currentTransaction.getPaymentMode());
        copy.setPartyName(currentTransaction.getPartyName());
        copy.setRemark(currentTransaction.getRemark());
        copy.setTags(currentTransaction.getTags());
        copy.setTimestamp(System.currentTimeMillis());

        viewModel.addTransaction(copy);
        showSnackbar("Transaction Duplicated");
        finish();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to permanently delete this transaction? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTransaction())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareTransaction() {
        String shareText = "Transaction Details:\n" +
                "Amount: ₹" + amountEditText.getText().toString() + "\n" +
                "Type: " + (radioIn.isChecked() ? "Income" : "Expense") + "\n" +
                "Category: " + selectedCategoryTextView.getText().toString() + "\n" +
                "Date: " + dateTextView.getText().toString();

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share Transaction"));
    }

    // --- Helper UI Methods ---

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

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    // --- Calculator & Voice Logic ---

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

        TextView display = view.findViewById(R.id.calc_display);
        display.setText(amountEditText.getText().toString().isEmpty() ? "0" : amountEditText.getText().toString());

        View.OnClickListener listener = v -> {
            Button b = (Button) v;
            String text = b.getText().toString();
            StringBuilder expression = new StringBuilder(display.getText().toString());

            switch (text) {
                case "C": expression.setLength(0); display.setText("0"); break;
                case "⌫":
                    if (expression.length() > 0) expression.deleteCharAt(expression.length() - 1);
                    display.setText(expression.length() > 0 ? expression.toString() : "0");
                    break;
                case "=":
                    String result = safeEvaluate(expression.toString());
                    display.setText(result);
                    break;
                default:
                    if (display.getText().toString().equals("0")) expression.setLength(0);
                    expression.append(text);
                    display.setText(expression.toString());
                    break;
            }
        };

        int[] btnIds = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_dot, R.id.btn_plus, R.id.btn_minus, R.id.btn_multiply, R.id.btn_divide, R.id.btn_percent, R.id.btn_clear, R.id.btn_backspace, R.id.btn_equals};
        for (int id : btnIds) view.findViewById(id).setOnClickListener(listener);

        view.findViewById(R.id.btn_done).setOnClickListener(v -> {
            if (!display.getText().toString().equals("Error")) amountEditText.setText(display.getText().toString());
            dialog.dismiss();
        });
        dialog.show();
    }

    private String safeEvaluate(String expression) {
        try {
            expression = expression.replace("%", "/100");
            // Basic parsing logic (Replace with exp4j or similar for robustness)
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+");
                return String.valueOf(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]));
            }
            // ... (Add other operators as needed)
            return expression;
        } catch (Exception e) { return "Error"; }
    }

    private void openSystemCalculator() {
        try {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            startActivity(intent);
        } catch (Exception e) { Toast.makeText(this, "Calculator not found", Toast.LENGTH_SHORT).show(); }
    }

    private void openPartySelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setHint("Enter party name");
        String currentParty = partyTextView.getText().toString();
        if (!currentParty.equals("Select Party") && !currentParty.equals("Select Party (Customer/Supplier)")) {
            input.setText(currentParty);
        }
        builder.setTitle("Edit Party")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> partyTextView.setText(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}