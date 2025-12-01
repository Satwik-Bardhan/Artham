package com.satvik.artham;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
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

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditTransactionActivity extends AppCompatActivity {

    private static final String TAG = "EditTransactionActivity";
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    // UI Components
    private TextView headerSubtitle, dateTextView, timeTextView, selectedCategoryTextView, partyTextView;
    private TextView createdDateText, updatedDateText;
    private EditText amountEditText;
    private TextInputEditText remarkEditText, taxAmountEditText;
    private TextInputLayout taxAmountLayout;
    private CheckBox taxCheckbox;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private Button saveButton, cancelButton;
    private ImageView backButton, menuButton, timePickerIcon, swapButton, calculatorButton;
    private LinearLayout categorySelectorLayout, dateSelectorLayout, timeSelectorLayout, partySelectorLayout;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference transactionRef;

    // Data
    private TransactionModel currentTransaction;
    private String cashbookId;
    private Calendar calendar;

    // Activity Launcher for category selection
    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String selectedCategoryName = result.getData().getStringExtra("selected_category");
                    if (selectedCategoryName != null) {
                        currentTransaction.setTransactionCategory(selectedCategoryName);
                        updateCategoryUI();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        Intent intent = getIntent();
        currentTransaction = (TransactionModel) intent.getSerializableExtra("transaction_model");
        cashbookId = intent.getStringExtra("cashbook_id");

        if (currentUser == null || currentTransaction == null || currentTransaction.getTransactionId() == null) {
            showSnackbar("Error: Invalid transaction data");
            finish();
            return;
        }

        transactionRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid())
                .child("cashbooks")
                .child(cashbookId)
                .child("transactions")
                .child(currentTransaction.getTransactionId());

        initializeUI();
        populateData();
        setupClickListeners();
    }

    private void initializeUI() {
        headerSubtitle = findViewById(R.id.headerSubtitle);

        // Date & Time
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        timePickerIcon = findViewById(R.id.timePickerIcon);

        // Transaction Type
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        swapButton = findViewById(R.id.swap_horiz);

        // Amount & Calculator
        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton); // Requires ID in XML

        // Payment Mode
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);

        // Tax (Requires including layout_tax_amount.xml or adding elements manually)
        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

        // Other Fields
        remarkEditText = findViewById(R.id.remarkEditText);

        // Category (Use ID from layout_category.xml)
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        // Find the clickable linear layout. Check if ID is categoryInputLayout or categorySelectorLayout
        categorySelectorLayout = findViewById(R.id.categoryInputLayout);
        if (categorySelectorLayout == null) categorySelectorLayout = findViewById(R.id.categorySelectorLayout);

        // Party
        partyTextView = findViewById(R.id.partyTextView);
        partySelectorLayout = findViewById(R.id.partySelectorLayout); // Requires ID in layout_party_reference.xml

        // History
        createdDateText = findViewById(R.id.createdDateText);
        updatedDateText = findViewById(R.id.updatedDateText);

        // Buttons
        saveButton = findViewById(R.id.saveChangesButton);
        cancelButton = findViewById(R.id.CancelTransactionButton);
        backButton = findViewById(R.id.backButton);
        menuButton = findViewById(R.id.menuButton);

        calendar = Calendar.getInstance();
    }

    private void populateData() {
        if (currentTransaction == null) return;

        calendar.setTimeInMillis(currentTransaction.getTimestamp());
        updateDateText();
        updateTimeText();

        SimpleDateFormat headerDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (headerSubtitle != null) headerSubtitle.setText("Last modified: " + headerDateFormat.format(new Date()));

        if (amountEditText != null) {
            if (currentTransaction.getAmount() == (long) currentTransaction.getAmount()) {
                amountEditText.setText(String.format(Locale.US, "%d", (long)currentTransaction.getAmount()));
            } else {
                amountEditText.setText(String.valueOf(currentTransaction.getAmount()));
            }
        }

        if (remarkEditText != null) remarkEditText.setText(currentTransaction.getRemark() != null ? currentTransaction.getRemark() : "");
        if (partyTextView != null) partyTextView.setText(currentTransaction.getPartyName() != null ? currentTransaction.getPartyName() : "Select Party");

        if ("IN".equalsIgnoreCase(currentTransaction.getType())) radioIn.setChecked(true);
        else radioOut.setChecked(true);

        if ("Cash".equalsIgnoreCase(currentTransaction.getPaymentMode())) radioCash.setChecked(true);
        else radioOnline.setChecked(true);

        updateCategoryUI();

        // Populate History
        SimpleDateFormat historySdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);
        if (createdDateText != null) createdDateText.setText(historySdf.format(new Date(currentTransaction.getTimestamp())));
        if (updatedDateText != null) updatedDateText.setText(historySdf.format(new Date()));
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

        // Calculator
        if (calculatorButton != null) {
            calculatorButton.setOnClickListener(v -> checkAndOpenCalculator());
        }

        // Category
        if (categorySelectorLayout != null) {
            categorySelectorLayout.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChooseCategoryActivity.class);
                intent.putExtra("selected_category", selectedCategoryTextView.getText().toString());
                intent.putExtra("cashbook_id", cashbookId);
                categoryLauncher.launch(intent);
            });
        }

        // Party
        if (partySelectorLayout != null) {
            partySelectorLayout.setOnClickListener(v -> openPartySelector());
        }

        // Tax Checkbox
        if (taxCheckbox != null) {
            taxCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (taxAmountLayout != null) {
                    taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
            });
        }

        // Swap Transaction Type
        if (swapButton != null) {
            swapButton.setOnClickListener(v -> {
                if (radioIn.isChecked()) radioOut.setChecked(true);
                else radioIn.setChecked(true);
            });
        }

        if (saveButton != null) saveButton.setOnClickListener(v -> saveChanges());
        if (menuButton != null) menuButton.setOnClickListener(v -> showMoreOptionsMenu(v));
    }

    // --- Calculator Logic ---
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

    // --- Party Selector ---
    private void openPartySelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setHint("Enter party name");
        if (partyTextView.getText() != null && !partyTextView.getText().toString().equals("Select Party")) {
            input.setText(partyTextView.getText());
        }
        builder.setTitle("Select Party")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String partyName = input.getText().toString().trim();
                    if (!partyName.isEmpty()) partyTextView.setText(partyName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Date/Time Logic ---
    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateText();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            updateTimeText();
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (dateTextView != null) dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateTimeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        if (timeTextView != null) timeTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateCategoryUI() {
        String categoryName = currentTransaction.getTransactionCategory();
        if (selectedCategoryTextView != null) selectedCategoryTextView.setText(categoryName != null ? categoryName : "Select Category");
    }

    private void saveChanges() {
        String amountStr = amountEditText.getText().toString().trim();
        if (amountStr.isEmpty()) { showSnackbar("Please enter an amount"); return; }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) { showSnackbar("Amount must be greater than 0"); return; }

            Map<String, Object> updates = new HashMap<>();
            updates.put("amount", amount);
            updates.put("remark", remarkEditText.getText().toString());
            updates.put("timestamp", calendar.getTimeInMillis());
            updates.put("type", radioIn.isChecked() ? "IN" : "OUT");
            updates.put("paymentMode", radioCash.isChecked() ? "Cash" : "Online");
            updates.put("transactionCategory", selectedCategoryTextView.getText().toString());
            updates.put("partyName", partyTextView.getText().toString());

            // Save Tax if checked (Assuming TransactionModel has a tax field, if not, it will be ignored)
            if (taxCheckbox != null && taxCheckbox.isChecked() && taxAmountEditText != null) {
                String taxStr = taxAmountEditText.getText().toString().trim();
                if (!taxStr.isEmpty()) updates.put("taxAmount", Double.parseDouble(taxStr));
            }

            transactionRef.updateChildren(updates)
                    .addOnSuccessListener(aVoid -> { showSnackbar("Transaction updated successfully"); finish(); })
                    .addOnFailureListener(e -> showSnackbar("Failed to update transaction"));

        } catch (NumberFormatException e) { showSnackbar("Please enter a valid amount"); }
    }

    private void showMoreOptionsMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.transaction_detail_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_share_transaction) { shareTransaction(); return true; }
            else if (itemId == R.id.action_duplicate_transaction) { duplicateTransaction(); return true; }
            else if (itemId == R.id.action_delete_transaction) { showDeleteConfirmationDialog(); return true; }
            return false;
        });
        popup.show();
    }

    private void shareTransaction() {
        String shareText = "Transaction: ₹" + amountEditText.getText() + " (" + (radioIn.isChecked() ? "IN" : "OUT") + ")\nCategory: " + selectedCategoryTextView.getText();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share"));
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTransaction())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTransaction() {
        transactionRef.removeValue().addOnSuccessListener(aVoid -> { showSnackbar("Deleted"); finish(); });
    }

    private void duplicateTransaction() {
        // Logic similar to create new, using currentTransaction data
        // ... (Simplified for brevity, use same logic as save but with push())
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}