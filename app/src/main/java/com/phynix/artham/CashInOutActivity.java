package com.phynix.artham;

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

import androidx.appcompat.app.AlertDialog;

// Using the newly updated CategoryPickerActivity
import com.phynix.artham.activities.CategoryPickerActivity;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.CashInOutViewModel;

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

    // UI Elements
    private TextView headerTitle, headerSubtitle;
    private ImageView backButton, selectedCategoryIcon;
    private TextView dateTextView, timeTextView, selectedCategoryTextView;
    private LinearLayout dateSelectorLayout, timeSelectorLayout;
    private MaterialCardView categorySelectorCard;
    private View selectedIconContainer;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline, radioCard;
    private View swapButton, calculatorButton, voiceInputButton, locationButton, contactBookButton;
    private CheckBox taxCheckbox;
    private TextInputLayout taxAmountLayout;
    private TextInputEditText taxAmountEditText, remarkEditText, tagsEditText, partyTextView;
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
    private String currentLocation = null;
    private boolean isSaveAndNew = false;

    // Timer
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private boolean isManualTimeSet = false;
    private Runnable timeRunnable;

    private FusedLocationProviderClient fusedLocationClient;

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initializeUI();
        initializeDateTime();
        setupClickListeners();
        setupActivityLaunchers();
        setupInitialState(transactionType);

        observeViewModel();
        startRealTimeClock();

        // Attach amount input validation: max 15 digits before decimal, 2 after
        amountEditText.addTextChangedListener(AmountFormatter.createAmountInputWatcher(amountEditText));
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
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

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
        locationButton = findViewById(R.id.locationButton);

        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveAndAddNewButton = findViewById(R.id.saveAndAddNewButton);
        clearButton = findViewById(R.id.clearButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
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

        taxCheckbox.setOnCheckedChangeListener((bv, isChecked) ->
                taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        voiceInputButton.setOnClickListener(v -> startVoiceInput());

        if (categorySelectorCard != null) {
            categorySelectorCard.setOnClickListener(v -> openCategorySelector());
        }

        partyTextView.setOnClickListener(v -> openPartySelector());
        contactBookButton.setOnClickListener(v -> openContactPicker());
        locationButton.setOnClickListener(v -> getCurrentLocation());

        saveEntryButton.setOnClickListener(v -> saveTransaction(false));
        saveAndAddNewButton.setOnClickListener(v -> saveTransaction(true));
        clearButton.setOnClickListener(v -> clearForm(false));

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

        TransactionModel transaction = new TransactionModel();
        try {
            transaction.setAmount(Double.parseDouble(amountStr));
        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid number");
            return;
        }

        transaction.setType(radioIn.isChecked() ? Constants.TRANSACTION_TYPE_IN : Constants.TRANSACTION_TYPE_OUT);
        String mode = "Online";
        if (radioCash.isChecked()) mode = "Cash";
        else if (radioCard != null && radioCard.isChecked()) mode = "Card";
        transaction.setPaymentMode(mode);
        transaction.setTransactionCategory(selectedCategory);
        transaction.setTimestamp(calendar.getTimeInMillis());
        transaction.setRemark(remarkEditText.getText().toString().trim());
        if (selectedParty != null) transaction.setPartyName(selectedParty);

        isSaveAndNew = addNew;
        viewModel.saveTransaction(currentCashbookId, transaction);
    }

    private void clearForm(boolean preserveContext) {
        amountEditText.setText("");
        remarkEditText.setText("");
        tagsEditText.setText("");
        selectedCategoryTextView.setText("Select Category");
        partyTextView.setText("");

        selectedCategory = "Other";
        selectedColorHex = "#9E9E9E";

        // Reset Visuals
        selectedIconContainer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(selectedColorHex)));
        selectedCategoryIcon.setImageResource(CategoryColorUtil.getCategoryIcon(selectedCategory));

        selectedParty = null;
        currentLocation = null;

        clearQuickAmountSelections();

        if (!preserveContext) {
            radioIn.setChecked(true);
            radioOnline.setChecked(true);
        }

        taxCheckbox.setChecked(false);
        taxAmountLayout.setVisibility(View.GONE);

        isManualTimeSet = false;
        startRealTimeClock();
        amountEditText.requestFocus();
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

        TextView display = view.findViewById(R.id.calc_display);
        display.setText(amountEditText.getText().toString().isEmpty() ? "0" : amountEditText.getText().toString());

        View.OnClickListener listener = v -> {
            Button b = (Button) v;
            String text = b.getText().toString();
            StringBuilder expression = new StringBuilder(display.getText().toString());

            switch (text) {
                case "C":
                    expression.setLength(0);
                    display.setText("0");
                    break;
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
                amountEditText.setText(display.getText().toString());
                amountEditText.setSelection(amountEditText.getText().length());
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
                return formatCalcResult(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]));
            } else if (expression.contains("-")) {
                String[] parts = expression.split("-");
                if (parts.length == 2) {
                    return formatCalcResult(Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]));
                }
            } else if (expression.contains("*")) {
                String[] parts = expression.split("\\*");
                return formatCalcResult(Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]));
            } else if (expression.contains("/")) {
                String[] parts = expression.split("/");
                return formatCalcResult(Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]));
            }
            return expression;
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
                        partyTextView.setText(partyName);
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
                    partyTextView.setText(name);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
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
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) getCurrentLocation();
            if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE) launchContactPicker();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeHandler.removeCallbacks(timeRunnable);
    }
}