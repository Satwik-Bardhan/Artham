package com.phynix.artham;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.phynix.artham.viewmodels.CashInOutViewModel;
import com.phynix.artham.viewmodels.CashInOutViewModelFactory;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends AppCompatActivity {

    private static final String TAG = "CashInOutActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    // UI Elements
    private TextView headerTitle, headerSubtitle;
    private ImageView backButton, menuButton;
    private TextView dateTextView, timeTextView, selectedCategoryTextView;
    private LinearLayout dateSelectorLayout, timeSelectorLayout, categorySelectorLayout;
    private RadioGroup inOutToggle, cashOnlineToggle;

    // [UPDATE] Added radioCard
    private RadioButton radioIn, radioOut, radioCash, radioOnline, radioCard;

    private ImageView swapButton;
    private CheckBox taxCheckbox;
    private TextInputLayout taxAmountLayout;
    private TextInputEditText taxAmountEditText, remarkEditText, tagsEditText;
    private EditText amountEditText;
    private ImageView calculatorButton, voiceInputButton, locationButton;
    private Button quickAmount100, quickAmount500, quickAmount1000, quickAmount5000;
    private ImageView cameraButton, scanButton, attachFileButton;
    private TextView partyTextView;
    private LinearLayout partySelectorLayout;
    private Button saveEntryButton, saveAndAddNewButton, clearButton;

    private LinearLayout attachedFilesSection;
    private LinearLayout attachedImageLayout, attachedQrLayout, attachedPdfLayout;
    private TextView attachedImageText, attachedQrText, attachedPdfText;
    private ImageView removeAttachedImage, removeAttachedQr, removeAttachedPdf;

    private CashInOutViewModel viewModel;
    private String currentCashbookId;

    // State Variables
    private Calendar calendar;
    private String selectedCategory = "Other";
    private String selectedParty = null;
    private String currentLocation = null;

    private Uri attachedImageUri = null;
    private String attachedQrData = null;
    private Uri attachedFileUri = null;

    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private boolean isManualTimeSet = false;
    private Runnable timeRunnable;

    private FusedLocationProviderClient fusedLocationClient;

    private ActivityResultLauncher<Intent> voiceInputLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> categoryLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        updateAttachmentVisibility();

        Log.d(TAG, "CashInOutActivity initialized for cashbook: " + currentCashbookId);
    }

    private void initializeUI() {
        // Header
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        backButton = findViewById(R.id.back_button);
        menuButton = findViewById(R.id.menu_button);

        // Date & Time
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        // Transaction Type
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        swapButton = findViewById(R.id.swap_horiz);

        // Payment Method
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);

        // [UPDATE] Initialize RadioCard
        radioCard = findViewById(R.id.radioCard);

        radioOnline = findViewById(R.id.radioOnline);
        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

        // Amount
        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton);
        quickAmount100 = findViewById(R.id.quickAmount100);
        quickAmount500 = findViewById(R.id.quickAmount500);
        quickAmount1000 = findViewById(R.id.quickAmount1000);
        quickAmount5000 = findViewById(R.id.quickAmount5000);

        // Remark & Voice
        remarkEditText = findViewById(R.id.remarkEditText);
        voiceInputButton = findViewById(R.id.voiceInputButton);

        // Category
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);

        // Attachments
        cameraButton = findViewById(R.id.cameraButton);
        scanButton = findViewById(R.id.scanButton);
        attachFileButton = findViewById(R.id.attachFileButton);

        // Party & Reference
        partyTextView = findViewById(R.id.partyTextView);
        partySelectorLayout = findViewById(R.id.partySelectorLayout);

        // Tags & Location
        tagsEditText = findViewById(R.id.tagsEditText);
        locationButton = findViewById(R.id.locationButton);

        // Action Buttons
        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveAndAddNewButton = findViewById(R.id.saveAndAddNewButton);
        clearButton = findViewById(R.id.clearButton);

        // Attachment Views
        attachedFilesSection = findViewById(R.id.attachedFilesSection);
        attachedImageLayout = findViewById(R.id.attachedImageLayout);
        attachedQrLayout = findViewById(R.id.attachedQrLayout);
        attachedPdfLayout = findViewById(R.id.attachedPdfLayout);

        attachedImageText = findViewById(R.id.attachedImageText);
        attachedQrText = findViewById(R.id.attachedQrText);
        attachedPdfText = findViewById(R.id.attachedPdfText);

        removeAttachedImage = findViewById(R.id.removeAttachedImage);
        removeAttachedQr = findViewById(R.id.removeAttachedQr);
        removeAttachedPdf = findViewById(R.id.removeAttachedPdf);
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
        if (menuButton != null) menuButton.setOnClickListener(v -> showMenuOptions());

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
                if (taxAmountLayout != null) {
                    taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
            });
        }

        if (voiceInputButton != null) voiceInputButton.setOnClickListener(v -> startVoiceInput());
        if (categorySelectorLayout != null) categorySelectorLayout.setOnClickListener(v -> openCategorySelector());
        if (partySelectorLayout != null) partySelectorLayout.setOnClickListener(v -> openPartySelector());
        if (locationButton != null) locationButton.setOnClickListener(v -> getCurrentLocation());

        if (saveEntryButton != null) saveEntryButton.setOnClickListener(v -> saveTransaction(false));
        if (saveAndAddNewButton != null) saveAndAddNewButton.setOnClickListener(v -> saveTransaction(true));
        if (clearButton != null) clearButton.setOnClickListener(v -> clearForm());

        setupQuickAmountButtons();

        if (cameraButton != null) cameraButton.setOnClickListener(v -> openCamera());
        if (scanButton != null) scanButton.setOnClickListener(v -> openScanner());
        if (attachFileButton != null) attachFileButton.setOnClickListener(v -> openFilePicker());

        if (removeAttachedImage != null) {
            removeAttachedImage.setOnClickListener(v -> {
                attachedImageUri = null;
                updateAttachmentVisibility();
            });
        }
        if (removeAttachedQr != null) {
            removeAttachedQr.setOnClickListener(v -> {
                attachedQrData = null;
                updateAttachmentVisibility();
            });
        }
        if (removeAttachedPdf != null) {
            removeAttachedPdf.setOnClickListener(v -> {
                attachedFileUri = null;
                updateAttachmentVisibility();
            });
        }
    }

    private void updateAttachmentVisibility() {
        boolean hasImage = attachedImageUri != null;
        boolean hasQr = attachedQrData != null;
        boolean hasFile = attachedFileUri != null;

        if (attachedImageLayout != null) attachedImageLayout.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        if (attachedQrLayout != null) attachedQrLayout.setVisibility(hasQr ? View.VISIBLE : View.GONE);
        if (attachedPdfLayout != null) attachedPdfLayout.setVisibility(hasFile ? View.VISIBLE : View.GONE);

        if (attachedFilesSection != null) {
            if (hasImage || hasQr || hasFile) {
                attachedFilesSection.setVisibility(View.VISIBLE);
            } else {
                attachedFilesSection.setVisibility(View.GONE);
            }
        }
    }

    // [FIXED] Added Null Checks to prevent Crash
    private void setupQuickAmountButtons() {
        View.OnClickListener quickAmountClickListener = v -> {
            clearQuickAmountSelections();
            v.setSelected(true);
            Button clickedButton = (Button) v;
            String amountText = clickedButton.getText().toString();
            String cleanAmount = amountText.replace("₹", "").replace("K", "000");
            amountEditText.setText(cleanAmount);
            showQuickAmountSelectionFeedback(clickedButton);
        };

        if (quickAmount100 != null) quickAmount100.setOnClickListener(quickAmountClickListener);
        if (quickAmount500 != null) quickAmount500.setOnClickListener(quickAmountClickListener);
        if (quickAmount1000 != null) quickAmount1000.setOnClickListener(quickAmountClickListener);
        if (quickAmount5000 != null) quickAmount5000.setOnClickListener(quickAmountClickListener);
    }

    // [FIXED] Added Null Checks
    private void clearQuickAmountSelections() {
        if (quickAmount100 != null) quickAmount100.setSelected(false);
        if (quickAmount500 != null) quickAmount500.setSelected(false);
        if (quickAmount1000 != null) quickAmount1000.setSelected(false);
        if (quickAmount5000 != null) quickAmount5000.setSelected(false);
    }

    private void showQuickAmountSelectionFeedback(Button selectedButton) {
        selectedButton.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                .withEndAction(() -> selectedButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start())
                .start();
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
                        if (results != null && !results.isEmpty()) {
                            remarkEditText.setText(results.get(0));
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        attachedImageUri = Uri.parse("file://dummy/image.jpg");
                        if (result.getData() != null && result.getData().getData() != null) {
                            attachedImageUri = result.getData().getData();
                        }
                        attachedImageText.setText("Image captured");
                        updateAttachmentVisibility();
                        Toast.makeText(this, "Photo attached", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedCategory = result.getData().getStringExtra("selected_category");
                        if (selectedCategory != null) {
                            selectedCategoryTextView.setText(selectedCategory);
                            int color = ContextCompat.getColor(this, R.color.primary_blue);
                            selectedCategoryTextView.setTextColor(color);
                        }
                    }
                }
        );

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        attachedFileUri = result.getData().getData();
                        if (attachedFileUri != null) {
                            attachedPdfText.setText(attachedFileUri.getLastPathSegment());
                            updateAttachmentVisibility();
                            Toast.makeText(this, "File attached", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void setupInitialState(String transactionType) {
        if ("OUT".equals(transactionType)) {
            radioOut.setChecked(true);
            updateHeaderForTransactionType("OUT");
        } else {
            radioIn.setChecked(true);
            updateHeaderForTransactionType("IN");
        }
        amountEditText.requestFocus();
        selectedCategoryTextView.setText(selectedCategory);
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
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        dateTextView.setText(dateFormat.format(calendar.getTime()));
    }

    private void updateTimeText() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        timeTextView.setText(timeFormat.format(calendar.getTime()));
    }

    private void swapTransactionType() {
        if (radioIn.isChecked()) {
            radioOut.setChecked(true);
        } else {
            radioIn.setChecked(true);
        }
    }

    private void updateHeaderForTransactionType(String type) {
        if ("IN".equals(type)) {
            headerTitle.setText("Add Income");
            headerSubtitle.setText("Record money received");
        } else {
            headerTitle.setText("Add Expense");
            headerSubtitle.setText("Record money spent");
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
        display.setText(amountEditText.getText().toString().isEmpty() ? "0" : amountEditText.getText().toString());

        StringBuilder expression = new StringBuilder();
        if(!amountEditText.getText().toString().isEmpty()) {
            expression.append(amountEditText.getText().toString());
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
            view.findViewById(id).setOnClickListener(listener);
        }

        view.findViewById(R.id.btn_done).setOnClickListener(v -> {
            String result = display.getText().toString();
            if(!result.equals("Error")) {
                amountEditText.setText(result);
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
            } else if (expression.contains("×") || expression.contains("*")) {
                String[] parts = expression.split("[×*]");
                return String.valueOf(Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]));
            } else if (expression.contains("÷") || expression.contains("/")) {
                String[] parts = expression.split("[÷/]");
                return String.valueOf(Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]));
            }

            return expression;
        } catch (Exception e) {
            return "Error";
        }
    }

    private void openSystemCalculator() {
        try {
            Intent calculatorIntent = new Intent();
            calculatorIntent.setAction(Intent.ACTION_MAIN);
            calculatorIntent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            startActivity(calculatorIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Calculator not found", Toast.LENGTH_SHORT).show();
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
        categoryLauncher.launch(intent);
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(cameraIntent);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void openScanner() {
        attachedQrData = "Scanned Data Code";
        attachedQrText.setText("QR Code Scanned");
        updateAttachmentVisibility();
        Toast.makeText(this, "Simulated Scan Complete", Toast.LENGTH_SHORT).show();
    }

    private void openFilePicker() {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("*/*");
        try {
            filePickerLauncher.launch(Intent.createChooser(fileIntent, "Select File"));
        } catch (Exception e) {
            Toast.makeText(this, "File picker not available", Toast.LENGTH_SHORT).show();
        }
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
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showMenuOptions() {
        String[] options = {"Duplicate Entry", "Save as Template"};
        new AlertDialog.Builder(this)
                .setTitle("Menu Options")
                .setItems(options, (dialog, which) -> {
                    Toast.makeText(this, "Feature coming soon", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveTransaction(boolean addNew) {
        if (!validateForm()) {
            return;
        }

        TransactionModel transaction = createTransactionFromForm();

        viewModel.saveTransaction(currentCashbookId, transaction);

        Toast.makeText(this, "Entry Saved", Toast.LENGTH_SHORT).show();
        if (addNew) {
            clearForm();
        } else {
            finish();
        }
    }

    private boolean validateForm() {
        String amountStr = amountEditText.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount cannot be empty");
            amountEditText.requestFocus();
            return false;
        }
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                amountEditText.setError("Amount must be greater than zero");
                amountEditText.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid amount");
            amountEditText.requestFocus();
            return false;
        }

        if (selectedCategory == null || "Select Category".equals(selectedCategory)) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private TransactionModel createTransactionFromForm() {
        TransactionModel transaction = new TransactionModel();

        transaction.setAmount(Double.parseDouble(amountEditText.getText().toString().trim()));
        transaction.setType(radioIn.isChecked() ? "IN" : "OUT");

        // [UPDATE] Logic for Payment Mode including Card
        String paymentMode = "Cash";
        if (radioCard != null && radioCard.isChecked()) {
            paymentMode = "Card";
        } else if (radioOnline != null && radioOnline.isChecked()) {
            paymentMode = "Online";
        }
        transaction.setPaymentMode(paymentMode);

        transaction.setTransactionCategory(selectedCategory);
        transaction.setTimestamp(calendar.getTimeInMillis());
        transaction.setRemark(remarkEditText.getText().toString().trim());
        if (selectedParty != null) transaction.setPartyName(selectedParty);

        return transaction;
    }

    private void clearForm() {
        amountEditText.setText("");
        remarkEditText.setText("");
        tagsEditText.setText("");
        selectedCategoryTextView.setText("Select Category");
        partyTextView.setText("Select Party (Customer/Supplier)");

        selectedCategory = "Other";
        selectedParty = null;
        currentLocation = null;

        attachedImageUri = null;
        attachedQrData = null;
        attachedFileUri = null;
        updateAttachmentVisibility();

        clearQuickAmountSelections();

        radioIn.setChecked(true);
        radioCash.setChecked(true);
        taxCheckbox.setChecked(false);
        taxAmountLayout.setVisibility(View.GONE);

        isManualTimeSet = false;
        startRealTimeClock();

        amountEditText.requestFocus();
        Toast.makeText(this, "Form cleared", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }
}