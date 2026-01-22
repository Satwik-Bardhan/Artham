package com.phynix.artham;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.ThemeManager;
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
    private static final int CONTACT_PERMISSION_CODE = 200;

    // UI Components
    private ImageView backButton, menuButton, timePickerIcon, swapButton;

    // [FIX] Changed from ImageView to View to prevent ClassCastException.
    // These might be FrameLayouts or LinearLayouts in your XML.
    private View calculatorButton, voiceInputButton, locationButton;

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

    // --- Activity Launchers ---

    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String category = result.getData().getStringExtra("selected_category");
                    if (category != null) {
                        selectedCategoryTextView.setText(category);
                        selectedCategoryTextView.setTextColor(getThemeColor(R.attr.chk_primary_blue));
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
        // Apply theme before super.onCreate to ensure context is set correctly
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
        menuButton = findViewById(R.id.menuButton);
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

        // [FIX] Initializing as generic View handles any layout type
        calculatorButton = findViewById(R.id.calculatorButton);

        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);

        partyTextView = findViewById(R.id.partyTextView);
        partySelectorLayout = findViewById(R.id.partySelectorLayout);

        remarkEditText = findViewById(R.id.remarkEditText);
        voiceInputButton = findViewById(R.id.voiceInputButton);

        tagsEditText = findViewById(R.id.tagsEditText);
        locationButton = findViewById(R.id.locationButton);

        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

        createdDateText = findViewById(R.id.createdDateText);
        updatedDateText = findViewById(R.id.updatedDateText);

        saveChangesButton = findViewById(R.id.saveChangesButton);
        cancelButton = findViewById(R.id.CancelTransactionButton);
    }

    private void populateData() {
        if (currentTransaction.getAmount() == (long) currentTransaction.getAmount()) {
            amountEditText.setText(String.format(Locale.US, "%d", (long) currentTransaction.getAmount()));
        } else {
            amountEditText.setText(String.valueOf(currentTransaction.getAmount()));
        }

        if ("IN".equalsIgnoreCase(currentTransaction.getType())) radioIn.setChecked(true);
        else radioOut.setChecked(true);

        String mode = currentTransaction.getPaymentMode();
        if ("Online".equalsIgnoreCase(mode)) radioOnline.setChecked(true);
        else if ("Card".equalsIgnoreCase(mode) && radioCard != null) radioCard.setChecked(true);
        else radioCash.setChecked(true);

        selectedCategoryTextView.setText(currentTransaction.getTransactionCategory());

        String party = currentTransaction.getPartyName();
        if (party != null && !party.isEmpty()) {
            partyTextView.setText(party);
            partyTextView.setTextColor(getThemeColor(R.attr.chk_primary_blue));
        } else {
            partyTextView.setText("Select Party");
        }

        if (currentTransaction.getRemark() != null) remarkEditText.setText(currentTransaction.getRemark());

        if (currentTransaction.getTags() != null) {
            tagsEditText.setText(currentTransaction.getTags().toString().replace("[", "").replace("]", ""));
        }

        updateDateText();
        updateTimeText();

        SimpleDateFormat headerDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (headerSubtitle != null) headerSubtitle.setText("Last modified: " + headerDateFormat.format(new Date()));

        SimpleDateFormat historySdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        if (createdDateText != null) createdDateText.setText(historySdf.format(currentTransaction.getTimestamp()));
        if (updatedDateText != null) updatedDateText.setText(historySdf.format(System.currentTimeMillis()));
    }

    private void setupClickListeners() {
        if (backButton != null) backButton.setOnClickListener(v -> finish());
        if (cancelButton != null) cancelButton.setOnClickListener(v -> finish());

        if (menuButton != null) {
            menuButton.setOnClickListener(v -> showPopupMenu(v));
        }

        View.OnClickListener dateListener = v -> showDatePicker();
        if (dateSelectorLayout != null) dateSelectorLayout.setOnClickListener(dateListener);
        if (dateTextView != null) dateTextView.setOnClickListener(dateListener);

        View.OnClickListener timeListener = v -> showTimePicker();
        if (timeSelectorLayout != null) timeSelectorLayout.setOnClickListener(timeListener);
        if (timeTextView != null) timeTextView.setOnClickListener(timeListener);
        if (timePickerIcon != null) timePickerIcon.setOnClickListener(timeListener);

        if (calculatorButton != null) calculatorButton.setOnClickListener(v -> checkAndOpenCalculator());
        if (voiceInputButton != null) voiceInputButton.setOnClickListener(v -> startVoiceInput());

        if (categorySelectorLayout != null) {
            categorySelectorLayout.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChooseCategoryActivity.class);
                intent.putExtra("selected_category", selectedCategoryTextView.getText().toString());
                intent.putExtra("cashbook_id", currentCashbookId);
                String type = radioIn.isChecked() ? "IN" : "OUT";
                intent.putExtra("transaction_type", type);
                categoryLauncher.launch(intent);
            });
        }

        if (partySelectorLayout != null) {
            partySelectorLayout.setOnClickListener(v -> {
                if (checkContactPermission()) {
                    openContactPicker();
                } else {
                    requestContactPermission();
                }
            });
        }

        if (taxCheckbox != null) {
            taxCheckbox.setOnCheckedChangeListener((bv, isChecked) -> {
                if (taxAmountLayout != null) taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });
        }

        if (swapButton != null) {
            swapButton.setOnClickListener(v -> {
                if (radioIn.isChecked()) radioOut.setChecked(true);
                else radioIn.setChecked(true);
            });
        }

        if (saveChangesButton != null) saveChangesButton.setOnClickListener(v -> saveChanges());
    }

    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add("Delete");
        popup.getMenu().add("Share");
        popup.getMenu().add("Duplicate");

        popup.setOnMenuItemClickListener(item -> {
            if ("Delete".equals(item.getTitle())) {
                showDeleteConfirmationDialog();
                return true;
            } else if ("Share".equals(item.getTitle())) {
                shareTransaction();
                return true;
            } else if ("Duplicate".equals(item.getTitle())) {
                duplicateTransaction();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void saveChanges() {
        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount required");
            return;
        }

        try {
            currentTransaction.setAmount(Double.parseDouble(amountStr));
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

            String tagsStr = tagsEditText.getText().toString().trim();
            currentTransaction.setTags(tagsStr);

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

    private void duplicateTransaction() {
        TransactionModel copy = new TransactionModel();
        copy.setAmount(currentTransaction.getAmount());
        copy.setTransactionCategory(currentTransaction.getTransactionCategory());
        copy.setType(currentTransaction.getType());
        copy.setPaymentMode(currentTransaction.getPaymentMode());
        copy.setPartyName(currentTransaction.getPartyName());
        copy.setRemark(currentTransaction.getRemark() + " (Copy)");
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
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+");
                return String.valueOf(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]));
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

    private void showSnackbar(String message) {
        // Anchor snackbar to footer
        SnackbarHelper.show(this, message, findViewById(R.id.footerLayout));
    }

    // Helper for theme colors
    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.BLACK;
    }
}