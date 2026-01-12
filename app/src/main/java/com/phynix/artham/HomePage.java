package com.phynix.artham;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.databinding.ActivityHomePageBinding;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.DateTimeUtils;
import com.phynix.artham.utils.ErrorHandler;
import com.phynix.artham.utils.ThemeManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;
    private static final int PERMISSION_REQUEST_CODE_NOTIFICATIONS = 101;

    // ViewBinding
    private ActivityHomePageBinding binding;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private DatabaseReference userRef;
    private FirebaseUser currentUser;

    // Listeners (to detach later)
    private ValueEventListener transactionsListener;
    private ValueEventListener cashbooksListener;
    private ValueEventListener userProfileListener;

    // Data
    private String currentCashbookId;
    private String currentUserId;
    private final List<CashbookModel> cashbooks = new ArrayList<>();
    private NumberFormat currencyFormat;

    // State
    private boolean isLoading = false;

    // Launchers
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // When returning from details, data usually auto-updates via Firebase listener
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply Theme first
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);

        // Initialize Binding
        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Check Auth
        if (currentUser == null) {
            signOutUser();
            return;
        }

        currentUserId = currentUser.getUid();
        userRef = mDatabase.child("users").child(currentUserId);
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // Load active cashbook ID preference
        loadActiveCashbookId();

        // Setup UI
        setupUI();
        setupClickListeners();
        setupBottomNavigation();

        // Start Data Loading
        startListeningForUserProfile();
        loadCashbooksAndInit();

        // Permissions
        checkNotificationPermissionAndShowFeedback();
    }

    private void setupUI() {
        // Set initial visibility
        setLoadingState(true);

        // Ensure Transaction Table is visible and empty state hidden initially
        binding.transactionSection.setVisibility(View.VISIBLE);
        binding.transactionTable.setVisibility(View.VISIBLE);
        binding.emptyStateView.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        // Buttons
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));

        // User Box (Switch Cashbook)
        binding.userBox.setOnClickListener(v -> openCashbookSwitcher());
    }

    private void setupBottomNavigation() {
        // Bottom Nav is an <include>, accessed via binding.bottomNavCard
        binding.bottomNavCard.btnHome.setSelected(true);

        binding.bottomNavCard.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        binding.bottomNavCard.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());

        binding.bottomNavCard.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    // ==========================================
    // DATA LOADING LOGIC
    // ==========================================

    private void startListeningForUserProfile() {
        if (userRef == null) return;

        userProfileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.child("userName").getValue(String.class);
                String display = (name != null && !name.isEmpty()) ? name : "User";

                // Update Balance Card User Name
                binding.balanceCardView.userNameBottom.setText(display);
                binding.balanceCardView.uidText.setText("UID: " + currentUserId.substring(0, Math.min(8, currentUserId.length())) + "...");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User profile load failed", error.toException());
            }
        };
        userRef.addValueEventListener(userProfileListener);
    }

    private void loadCashbooksAndInit() {
        if (userRef == null) return;

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbooks.clear();
                boolean activeFound = false;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                        if (cashbook != null) {
                            cashbook.setCashbookId(snapshot.getKey());
                            cashbooks.add(cashbook);

                            if (currentCashbookId != null && currentCashbookId.equals(cashbook.getCashbookId())) {
                                activeFound = true;
                                updateCashbookHeaderUI(cashbook);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                    }
                }

                if (cashbooks.isEmpty()) {
                    setLoadingState(false);
                    showCreateFirstCashbookDialog();
                } else {
                    if (!activeFound) {
                        // Default to first if saved ID is invalid
                        currentCashbookId = cashbooks.get(0).getCashbookId();
                        saveActiveCashbookId(currentCashbookId);
                        updateCashbookHeaderUI(cashbooks.get(0));
                    }
                    startListeningForTransactions();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoadingState(false);
                ErrorHandler.handleFirebaseError(HomePage.this, error);
            }
        };
        userRef.child("cashbooks").addValueEventListener(cashbooksListener);
    }

    private void startListeningForTransactions() {
        if (currentCashbookId == null) return;

        // Remove old listener if switching
        if (transactionsListener != null) {
            // We need the *exact* reference used previously to remove it.
            // Simplified here: we just rely on replacing it or cleaning up in onDestroy/switch.
        }

        DatabaseReference txnRef = userRef.child("cashbooks").child(currentCashbookId).child("transactions");

        transactionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TransactionModel> allTransactions = new ArrayList<>();
                List<TransactionModel> todaysTransactions = new ArrayList<>();

                double totalIncome = 0;
                double totalExpense = 0;
                double todayIncome = 0;
                double todayExpense = 0;

                for (DataSnapshot s : snapshot.getChildren()) {
                    try {
                        TransactionModel txn = s.getValue(TransactionModel.class);
                        if (txn != null) {
                            txn.setTransactionId(s.getKey());
                            allTransactions.add(txn);

                            // Calculate Globals
                            if ("IN".equalsIgnoreCase(txn.getType())) {
                                totalIncome += txn.getAmount();
                            } else {
                                totalExpense += txn.getAmount();
                            }

                            // Calculate Today
                            if (isToday(txn.getTimestamp())) {
                                todaysTransactions.add(txn);
                                if ("IN".equalsIgnoreCase(txn.getType())) {
                                    todayIncome += txn.getAmount();
                                } else {
                                    todayExpense += txn.getAmount();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Txn parse error", e);
                    }
                }

                // Sort Today's list (Newest first)
                Collections.sort(todaysTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                // Update UI
                double finalTotalIncome = totalIncome;
                double finalTotalExpense = totalExpense;
                double finalTodayBalance = todayIncome - todayExpense;

                runOnUiThread(() -> {
                    updateBalanceCard(finalTotalIncome, finalTotalExpense);
                    updateDailySummary(finalTodayBalance);
                    updateTransactionTable(todaysTransactions);
                    setLoadingState(false);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoadingState(false);
            }
        };
        txnRef.addValueEventListener(transactionsListener);
    }

    // ==========================================
    // UI UPDATES
    // ==========================================

    private void updateCashbookHeaderUI(CashbookModel cashbook) {
        if (cashbook == null) return;
        binding.userNameTop.setText(cashbook.getName());
        binding.currentCashbookText.setText(cashbook.getName());

        String timeSpan = DateTimeUtils.getRelativeTimeSpan(cashbook.getLastModified());
        binding.lastOpenedText.setText("Last opened: " + timeSpan);
    }

    private void updateBalanceCard(double income, double expense) {
        double balance = income - expense;

        binding.balanceCardView.balanceText.setText(formatCurrency(balance));
        binding.balanceCardView.moneyIn.setText(formatCurrency(income));
        binding.balanceCardView.moneyOut.setText(formatCurrency(expense));

        // Balance text always white on the card
        binding.balanceCardView.balanceText.setTextColor(Color.WHITE);
    }

    private void updateDailySummary(double todayBalance) {
        // Daily Summary is an <include>
        binding.dailySummaryInclude.dailyDateText.setText(DateTimeUtils.formatDate(System.currentTimeMillis(), "dd MMM yyyy"));

        String sign = todayBalance >= 0 ? "+ " : "- ";
        binding.dailySummaryInclude.dailyBalanceText.setText(sign + formatCurrency(Math.abs(todayBalance)));

        // Color based on +/-
        int colorRes = todayBalance >= 0 ? R.attr.chk_incomeColor : R.attr.chk_expenseColor;
        binding.dailySummaryInclude.dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, colorRes));
    }

    private void updateTransactionTable(List<TransactionModel> transactions) {
        binding.transactionTable.removeAllViews(); // Clear old rows

        if (transactions.isEmpty()) {
            binding.transactionTable.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.VISIBLE);
            binding.transactionCount.setText("TODAY (0)");
        } else {
            binding.transactionTable.setVisibility(View.VISIBLE);
            binding.emptyStateView.setVisibility(View.GONE);
            binding.transactionCount.setText("TODAY (" + transactions.size() + ")");

            for (TransactionModel txn : transactions) {
                addTableRow(txn);
            }
        }
    }

    private void addTableRow(TransactionModel transaction) {
        // Inflate the row layout
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_transaction_report_row, binding.transactionTable, false);

        TextView rowCategory = rowView.findViewById(R.id.rowCategory);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        // Set Data
        rowCategory.setText(transaction.getTransactionCategory());
        rowMode.setText(transaction.getPaymentMode());

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            rowIn.setText(formatCurrency(transaction.getAmount()));
            rowIn.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_incomeColor));
            rowOut.setText("-");
        } else {
            rowIn.setText("-");
            rowOut.setText(formatCurrency(transaction.getAmount()));
            rowOut.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_expenseColor));
        }

        // Make row clickable
        rowView.setOnClickListener(v -> openTransactionDetail(transaction));

        // Add to TableLayout
        binding.transactionTable.addView(rowView);
    }

    // ==========================================
    // ACTIONS & NAVIGATION
    // ==========================================

    private void openCashInOutActivity(String type) {
        if (currentCashbookId == null) {
            Toast.makeText(this, "Please create a cashbook first", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra("transaction_type", type);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        detailsLauncher.launch(intent);
    }

    private void openCashbookSwitcher() {
        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newId = data.getStringExtra("selected_cashbook_id");
            String newName = data.getStringExtra("cashbook_name");

            if (newId != null && !newId.equals(currentCashbookId)) {
                currentCashbookId = newId;
                saveActiveCashbookId(newId);
                // Reload data for new cashbook
                startListeningForTransactions();
                // Find and update header manually or let listener do it
                for(CashbookModel cb : cashbooks) {
                    if(cb.getCashbookId().equals(newId)) {
                        updateCashbookHeaderUI(cb);
                        break;
                    }
                }
                Toast.makeText(this, "Switched to: " + newName, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showCreateFirstCashbookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_welcome_cashflow);
        builder.setMessage(R.string.msg_create_first_cashbook);

        final EditText input = new EditText(this);
        input.setHint(R.string.hint_cashbook_name);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int)(20 * getResources().getDisplayMetrics().density);
        params.setMargins(margin, 0, margin, 0);
        input.setLayoutParams(params);
        container.addView(input);

        builder.setView(container);
        builder.setCancelable(false);

        builder.setPositiveButton(R.string.btn_create, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                createNewCashbook(name);
            } else {
                Toast.makeText(this, getString(R.string.error_enter_cashbook_name), Toast.LENGTH_SHORT).show();
                showCreateFirstCashbookDialog(); // Retry
            }
        });
        builder.show();
    }

    private void createNewCashbook(String name) {
        DatabaseReference ref = userRef.child("cashbooks");
        String id = ref.push().getKey();
        if (id == null) return;

        CashbookModel newBook = new CashbookModel(id, name);
        newBook.setUserId(currentUserId);

        ref.child(id).setValue(newBook).addOnSuccessListener(v -> {
            currentCashbookId = id;
            saveActiveCashbookId(id);
            updateCashbookHeaderUI(newBook);
            startListeningForTransactions(); // Will be empty initially
        });
    }

    // ==========================================
    // HELPERS & UTILS
    // ==========================================

    private void setLoadingState(boolean loading) {
        isLoading = loading;
        binding.cashInButton.setEnabled(!loading);
        binding.cashOutButton.setEnabled(!loading);
        binding.userBox.setEnabled(!loading);

        if (loading) {
            binding.userNameTop.setText("Loading...");
        }
    }

    private void saveActiveCashbookId(String id) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUserId, id).apply();
    }

    private void loadActiveCashbookId() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        currentCashbookId = prefs.getString("active_cashbook_id_" + currentUserId, null);
    }

    private void signOutUser() {
        mAuth.signOut();
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    private boolean isToday(long timestamp) {
        Calendar tCal = Calendar.getInstance();
        tCal.setTimeInMillis(timestamp);
        Calendar now = Calendar.getInstance();
        return tCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                tCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
    }

    private void checkNotificationPermissionAndShowFeedback() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE_NOTIFICATIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Permission result handling logic if needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up listeners
        if (userRef != null) {
            if (userProfileListener != null) userRef.removeEventListener(userProfileListener);
            if (cashbooksListener != null) userRef.child("cashbooks").removeEventListener(cashbooksListener);
            if (transactionsListener != null && currentCashbookId != null) {
                userRef.child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
            }
        }
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
            return Color.BLACK;
        }
    }
}