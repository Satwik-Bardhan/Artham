package com.phynix.artham;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.viewmodels.TransactionViewModel;
import com.phynix.artham.viewmodels.TransactionViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class EditTransactionActivity extends AppCompatActivity {

    private static final String TAG = "EditTransactionActivity";
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    // UI Components
    private ImageView backButton;
    private TextView dateTextView, timeTextView, selectedCategoryTextView, partyTextView;
    private TextView createdDateText, updatedDateText;
    private EditText amountEditText;
    private TextInputEditText remarkEditText, tagsEditText, taxAmountEditText;
    private TextInputLayout taxAmountLayout;
    private CheckBox taxCheckbox;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline, radioCard;
    private LinearLayout dateSelectorLayout, timeSelectorLayout, categorySelectorLayout, partySelectorLayout;
    private Button saveChangesButton, cancelButton;
    private ImageView calculatorButton, voiceInputButton, locationButton;

    // ViewModel & Data
    private TransactionViewModel viewModel;
    private TransactionModel transaction;
    private String currentCashbookId;
    private Calendar calendar;

    // Launchers
    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String category = result.getData().getStringExtra("selected_category");
                    if (category != null) selectedCategoryTextView.setText(category);
                }
            }
    );

    private final ActivityResultLauncher<Intent> voiceInputLauncher = registerForActivityResult(
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Get Data
        transaction = (TransactionModel) getIntent().getSerializableExtra("transaction_model");
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        if (transaction == null || currentCashbookId == null) {
            Toast.makeText(this, "Error loading transaction details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        calendar = Calendar.getInstance();
        calendar.setTimeInMillis(transaction.getTimestamp());

        initViewModel();
        initializeUI();
        populateData();
        setupClickListeners();
    }

    private void initViewModel() {
        TransactionViewModelFactory factory = new TransactionViewModelFactory(getApplication(), currentCashbookId);
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        });
    }

    private void initializeUI() {
        // Header
        backButton = findViewById(R.id.backButton);

        // Date & Time
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        // Toggles
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);

        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        radioCard = findViewById(R.id.radioCard);

        // Fields
        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton);

        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);

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
        if (transaction.getAmount() == (long) transaction.getAmount()) {
            amountEditText.setText(String.format(Locale.US, "%d", (long) transaction.getAmount()));
        } else {
            amountEditText.setText(String.valueOf(transaction.getAmount()));
        }

        // Type
        if ("IN".equalsIgnoreCase(transaction.getType())) radioIn.setChecked(true);
        else radioOut.setChecked(true);

        // Payment Mode
        String mode = transaction.getPaymentMode();
        if ("Online".equalsIgnoreCase(mode)) radioOnline.setChecked(true);
        else if ("Card".equalsIgnoreCase(mode) && radioCard != null) radioCard.setChecked(true);
        else radioCash.setChecked(true);

        // Category & Party
        selectedCategoryTextView.setText(transaction.getTransactionCategory());
        if (transaction.getPartyName() != null) partyTextView.setText(transaction.getPartyName());

        // Text Fields
        if (transaction.getRemark() != null) remarkEditText.setText(transaction.getRemark());
        if (transaction.getTags() != null) tagsEditText.setText(transaction.getTags());

        // Date & Time
        updateDateText();
        updateTimeText();

        // History
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        createdDateText.setText(sdf.format(transaction.getTimestamp()));
        updatedDateText.setText(sdf.format(System.currentTimeMillis()));
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());

        dateSelectorLayout.setOnClickListener(v -> showDatePicker());
        timeSelectorLayout.setOnClickListener(v -> showTimePicker());

        calculatorButton.setOnClickListener(v -> checkAndOpenCalculator());
        voiceInputButton.setOnClickListener(v -> startVoiceInput());

        categorySelectorLayout.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChooseCategoryActivity.class);
            intent.putExtra("selected_category", selectedCategoryTextView.getText().toString());
            intent.putExtra("cashbook_id", currentCashbookId);
            categoryLauncher.launch(intent);
        });

        partySelectorLayout.setOnClickListener(v -> openPartySelector());

        if (taxCheckbox != null) {
            taxCheckbox.setOnCheckedChangeListener((bv, isChecked) ->
                    taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        }

        saveChangesButton.setOnClickListener(v -> saveChanges());
    }

    private void saveChanges() {
        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount required");
            return;
        }

        try {
            // Update Transaction Model
            transaction.setAmount(Double.parseDouble(amountStr));
            transaction.setType(radioIn.isChecked() ? "IN" : "OUT");

            String mode = "Cash";
            if (radioOnline.isChecked()) mode = "Online";
            else if (radioCard != null && radioCard.isChecked()) mode = "Card";
            transaction.setPaymentMode(mode);

            transaction.setTransactionCategory(selectedCategoryTextView.getText().toString());
            transaction.setTimestamp(calendar.getTimeInMillis()); // Use updated time

            String party = partyTextView.getText().toString();
            transaction.setPartyName(party.equals("Select Party (Customer/Supplier)") ? "" : party);

            transaction.setRemark(remarkEditText.getText().toString().trim());
            transaction.setTags(tagsEditText.getText().toString().trim());

            // Note: Add location/attachment logic here if those UI elements are fully implemented

            // Save via ViewModel
            viewModel.addTransaction(transaction); // This typically handles upsert (update if ID exists)

            Toast.makeText(this, "Transaction Updated", Toast.LENGTH_SHORT).show();
            finish();

        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid amount");
        }
    }

    // --- Helpers ---

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateTimeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        timeTextView.setText(sdf.format(calendar.getTime()));
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

    private void checkAndOpenCalculator() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_CALCULATOR, true)) {
            showBuiltInCalculator();
        } else {
            openSystemCalculator();
        }
    }

    private void showBuiltInCalculator() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_calculator, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        TextView display = view.findViewById(R.id.calc_display);
        display.setText(amountEditText.getText().toString().isEmpty() ? "0" : amountEditText.getText().toString());
        StringBuilder expression = new StringBuilder(amountEditText.getText().toString());

        View.OnClickListener listener = v -> {
            Button b = (Button) v;
            String text = b.getText().toString();
            switch (text) {
                case "C": expression.setLength(0); display.setText("0"); break;
                case "⌫":
                    if (expression.length() > 0) expression.deleteCharAt(expression.length() - 1);
                    display.setText(expression.length() > 0 ? expression.toString() : "0");
                    break;
                case "=":
                    String result = safeEvaluate(expression.toString());
                    display.setText(result);
                    expression.setLength(0);
                    expression.append(result);
                    break;
                default: expression.append(text); display.setText(expression.toString()); break;
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
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+");
                return String.valueOf(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]));
            } else if (expression.contains("-")) {
                String[] parts = expression.split("-");
                return String.valueOf(Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]));
            } else if (expression.contains("×") || expression.contains("*")) {
                String[] parts = expression.split("[×*]");
                return String.valueOf(Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]));
            } else if (expression.contains("÷") || expression.contains("/")) {
                String[] parts = expression.split("[÷/]");
                return String.valueOf(Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]));
            }
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
        if (!currentParty.equals("Select Party (Customer/Supplier)")) {
            input.setText(currentParty);
        }
        builder.setTitle("Edit Party")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> partyTextView.setText(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}