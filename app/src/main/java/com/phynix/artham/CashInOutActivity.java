package com.phynix.artham;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.CashInOutViewModel;
import com.phynix.artham.viewmodels.CashInOutViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends AppCompatActivity {

    private static final String TAG = "CashInOutActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int CONTACTS_PERMISSION_REQUEST_CODE = 1002;
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    // UI Elements - Header
    private TextView headerTitle, headerSubtitle;
    private View backButton;

    // UI Elements - Inputs
    private TextView dateTextView, timeTextView, selectedCategoryTextView;
    private LinearLayout dateSelectorLayout, timeSelectorLayout, categorySelectorLayout;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;

    private View swapButton;
    private View calculatorButton, voiceInputButton, locationButton;
    private View contactBookButton;

    private CheckBox taxCheckbox;
    private TextInputLayout taxAmountLayout;
    private TextInputEditText taxAmountEditText, remarkEditText, tagsEditText;
    private EditText amountEditText;

    private Button quickAmount100, quickAmount500, quickAmount1000, quickAmount5000;

    // Party Elements
    private TextInputEditText partyTextView;

    // Footer Buttons
    private Button saveEntryButton, saveAndAddNewButton, clearButton;

    // Logic
    private CashInOutViewModel viewModel;
    private String currentCashbookId;
    private Calendar calendar;
    private String selectedCategory = "Other";
    private String selectedParty = null;
    private String currentLocation = null;

    // Timer for live clock
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private boolean isManualTimeSet = false;
    private Runnable timeRunnable;

    private FusedLocationProviderClient fusedLocationClient;

    // Activity Launchers
    private ActivityResultLauncher<Intent> voiceInputLauncher;
    private ActivityResultLauncher<Intent> categoryLauncher;
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_in_out);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this, new CashInOutViewModelFactory(getApplication()))
                .get(CashInOutViewModel.class);

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: No Cashbook ID found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String transactionType = getIntent().getStringExtra("transaction_type");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initializeUI();
        initializeDateTime();
        setupClickListeners();
        setupActivityLaunchers();
        setupInitialState(transactionType);

        startRealTimeClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkCalculatorSetting();
    }

    private void checkCalculatorSetting() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isCalculatorEnabled = prefs.getBoolean(KEY_CALCULATOR, true);
        if (calculatorButton != null) {
            calculatorButton.setVisibility(isCalculatorEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void initializeUI() {
        // Header
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        backButton = findViewById(R.id.back_button);

        // Date & Time
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        // Type
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        swapButton = findViewById(R.id.swap_horiz);

        // Payment Mode & Tax
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

        // Amount Input
        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton);
        quickAmount100 = findViewById(R.id.quickAmount100);
        quickAmount500 = findViewById(R.id.quickAmount500);
        quickAmount1000 = findViewById(R.id.quickAmount1000);
        quickAmount5000 = findViewById(R.id.quickAmount5000);

        // Remarks & Category
        remarkEditText = findViewById(R.id.remarkEditText);
        voiceInputButton = findViewById(R.id.voiceInputButton);
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);

        // Party
        partyTextView = findViewById(R.id.partyTextView);
        contactBookButton = findViewById(R.id.contactBookButton);

        // Tags & Location
        tagsEditText = findViewById(R.id.tagsEditText);
        locationButton = findViewById(R.id.locationButton);

        // Actions
        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveAndAddNewButton = findViewById(R.id.saveAndAddNewButton);
        clearButton = findViewById(R.id.clearButton);
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
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        if (dateSelectorLayout != null) {
            dateSelectorLayout.setOnClickListener(v -> {
                stopRealTimeClock();
                showDatePicker();
            });
        }
        if (timeSelectorLayout != null) {
            timeSelectorLayout.setOnClickListener(v -> {
                stopRealTimeClock();
                showTimePicker();
            });
        }

        if (swapButton != null) swapButton.setOnClickListener(v -> swapTransactionType());
        if (inOutToggle != null) inOutToggle.setOnCheckedChangeListener(this::onTransactionTypeChanged);

        if (calculatorButton != null) calculatorButton.setOnClickListener(v -> checkAndOpenCalculator());

        if (taxCheckbox != null) {
            taxCheckbox.setOnCheckedChangeListener((bv, isChecked) -> {
                if (taxAmountLayout != null) taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });
        }
        if (voiceInputButton != null) voiceInputButton.setOnClickListener(v -> startVoiceInput());
        if (categorySelectorLayout != null) categorySelectorLayout.setOnClickListener(v -> openCategorySelector());

        if (partyTextView != null) partyTextView.setOnClickListener(v -> openPartySelector());
        if (contactBookButton != null) contactBookButton.setOnClickListener(v -> openContactPicker());

        if (locationButton != null) locationButton.setOnClickListener(v -> getCurrentLocation());

        if (saveEntryButton != null) saveEntryButton.setOnClickListener(v -> saveTransaction(false));
        if (saveAndAddNewButton != null) saveAndAddNewButton.setOnClickListener(v -> saveTransaction(true));
        if (clearButton != null) clearButton.setOnClickListener(v -> clearForm(false));

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
            if (amountEditText != null) amountEditText.setSelection(amountEditText.getText().length());
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
            updateHeaderForTransactionType("IN");
        } else if (checkedId == R.id.radioOut) {
            updateHeaderForTransactionType("OUT");
        }
    }

    private void setupActivityLaunchers() {
        voiceInputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && !results.isEmpty() && remarkEditText != null) {
                            remarkEditText.setText(results.get(0));
                        }
                    }
                }
        );

        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedCategory = result.getData().getStringExtra("selected_category");
                        if (selectedCategory != null && selectedCategoryTextView != null) {
                            selectedCategoryTextView.setText(selectedCategory);
                            int color = ContextCompat.getColor(this, R.color.primary_blue);
                            selectedCategoryTextView.setTextColor(color);
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
        if ("OUT".equals(transactionType)) {
            if (radioOut != null) radioOut.setChecked(true);
            updateHeaderForTransactionType("OUT");
        } else {
            if (radioIn != null) radioIn.setChecked(true);
            updateHeaderForTransactionType("IN");
        }
        if (amountEditText != null) amountEditText.requestFocus();
        if (selectedCategoryTextView != null) selectedCategoryTextView.setText(selectedCategory);
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateText();
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateTimeText();
                },
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);
        timePickerDialog.show();
    }

    private void updateDateText() {
        if (dateTextView != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            dateTextView.setText(dateFormat.format(calendar.getTime()));
        }
    }

    private void updateTimeText() {
        if (timeTextView != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
            timeTextView.setText(timeFormat.format(calendar.getTime()));
        }
    }

    private void swapTransactionType() {
        if (radioIn != null && radioOut != null) {
            if (radioIn.isChecked()) {
                radioOut.setChecked(true);
            } else {
                radioIn.setChecked(true);
            }
        }
    }

    private void updateHeaderForTransactionType(String type) {
        if ("IN".equals(type)) {
            if (headerTitle != null) headerTitle.setText("Add Income");
            if (headerSubtitle != null) headerSubtitle.setText("Record money received");
        } else {
            if (headerTitle != null) headerTitle.setText("Add Expense");
            if (headerSubtitle != null) headerSubtitle.setText("Record money spent");
        }
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

    private void showBuiltInCalculator() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_calculator, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextView display = view.findViewById(R.id.calc_display);
        String currentText = amountEditText.getText().toString();
        display.setText(currentText.isEmpty() ? "0" : currentText);

        StringBuilder expression = new StringBuilder();
        if(!currentText.isEmpty()) {
            expression.append(currentText);
        }

        View.OnClickListener listener = v -> {
            Button b = (Button) v;
            String text = b.getText().toString();

            switch (text) {
                case "C":
                    expression.setLength(0);
                    display.setText("0");
                    break;
                case "⌫":
                    if (expression.length() > 0) {
                        expression.deleteCharAt(expression.length() - 1);
                        display.setText(expression.length() > 0 ? expression.toString() : "0");
                    }
                    break;
                case "=":
                    String result = safeEvaluate(expression.toString());
                    display.setText(result);
                    expression.setLength(0);
                    expression.append(result);
                    break;
                default:
                    expression.append(text);
                    display.setText(expression.toString());
                    break;
            }
        };

        int[] btnIds = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9,
                R.id.btn_dot, R.id.btn_plus, R.id.btn_minus, R.id.btn_multiply,
                R.id.btn_divide, R.id.btn_percent, R.id.btn_clear, R.id.btn_backspace, R.id.btn_equals};

        for (int id : btnIds) {
            View btn = view.findViewById(id);
            if(btn != null) btn.setOnClickListener(listener);
        }

        view.findViewById(R.id.btn_done).setOnClickListener(v -> {
            String result = display.getText().toString();
            if(!result.equals("Error") && amountEditText != null) {
                amountEditText.setText(result);
                amountEditText.setSelection(result.length());
            }
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
            }
            return expression;
        } catch (Exception e) {
            return "Error";
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your remark");
        try {
            voiceInputLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCategorySelector() {
        Intent intent = new Intent(this, ChooseCategoryActivity.class);
        intent.putExtra("selected_category", selectedCategory);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("transaction_type", (radioIn != null && radioIn.isChecked()) ? "IN" : "OUT");
        categoryLauncher.launch(intent);
    }

    private void openPartySelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setHint("Enter party name");
        builder.setTitle("Select Party")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String partyName = input.getText().toString().trim();
                    if (!partyName.isEmpty()) {
                        selectedParty = partyName;
                        if (partyTextView != null) partyTextView.setText(partyName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                    if (partyTextView != null) partyTextView.setText(name);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void getCurrentLocation() {
        if (fusedLocationClient == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = location.getLatitude() + ", " + location.getLongitude();
                        Toast.makeText(this, "Location captured", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            }
        } else if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker();
            }
        }
    }

    private void saveTransaction(boolean addNew) {
        if (!validateForm()) return;

        TransactionModel transaction = createTransactionFromForm();
        viewModel.saveTransaction(currentCashbookId, transaction);

        Toast.makeText(this, "Entry Saved", Toast.LENGTH_SHORT).show();
        if (addNew) {
            clearForm(true);
        } else {
            finish();
        }
    }

    private boolean validateForm() {
        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Required");
            return false;
        }
        try {
            if (Double.parseDouble(amountStr) <= 0) {
                amountEditText.setError("Must be > 0");
                return false;
            }
        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid");
            return false;
        }
        if (selectedCategory == null || "Select Category".equals(selectedCategory)) {
            Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private TransactionModel createTransactionFromForm() {
        TransactionModel transaction = new TransactionModel();
        transaction.setAmount(Double.parseDouble(amountEditText.getText().toString().trim()));
        transaction.setType((radioIn != null && radioIn.isChecked()) ? "IN" : "OUT");
        transaction.setPaymentMode((radioCash != null && radioCash.isChecked()) ? "Cash" : "Online");
        transaction.setTransactionCategory(selectedCategory);
        transaction.setTimestamp(calendar.getTimeInMillis());
        transaction.setRemark(remarkEditText.getText().toString().trim());
        if (selectedParty != null) transaction.setPartyName(selectedParty);
        return transaction;
    }

    private void clearForm(boolean keepTransactionType) {
        if (amountEditText != null) amountEditText.setText("");
        if (remarkEditText != null) remarkEditText.setText("");
        if (tagsEditText != null) tagsEditText.setText("");
        if (selectedCategoryTextView != null) selectedCategoryTextView.setText("Select Category");
        if (partyTextView != null) partyTextView.setText("");

        selectedCategory = "Other";
        selectedParty = null;
        currentLocation = null;

        clearQuickAmountSelections();

        if (!keepTransactionType && radioIn != null) radioIn.setChecked(true);

        if (radioCash != null) radioCash.setChecked(true);
        if (taxCheckbox != null) taxCheckbox.setChecked(false);
        if (taxAmountLayout != null) taxAmountLayout.setVisibility(View.GONE);

        isManualTimeSet = false;
        startRealTimeClock();
        if (amountEditText != null) amountEditText.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }
}