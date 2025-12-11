package com.phynix.artham;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.InterruptionLevel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
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

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.databinding.ActivityHomePageBinding;
import com.phynix.artham.databinding.ComponentBalanceCardBinding;
import com.phynix.artham.databinding.LayoutBottomNavigationBinding;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.DateTimeUtils;
import com.phynix.artham.utils.ErrorHandler;
import com.phynix.artham.utils.SnackbarHelper;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;
    // Permission request code for Notifications
    private static final int PERMISSION_REQUEST_CODE_NOTIFICATIONS = 101;

    // ViewBinding
    private ActivityHomePageBinding binding;
    private ComponentBalanceCardBinding balanceCardBinding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    // UI Elements
    private View dailySummaryHeader;
    private TextView dailyDateText, dailyBalanceText;

    private LinearLayout transactionSection;
    private LinearLayout emptyStateView;
    private TableLayout transactionTable;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener, cashbooksListener, userProfileListener;
    private FirebaseUser currentUser;
    private DatabaseReference userRef;

    // Data
    private ArrayList<TransactionModel> allTransactions = new ArrayList<>();
    private List<CashbookModel> cashbooks = new ArrayList<>();

    // State
    private String currentCashbookId;
    private String currentUserId;
    private String fetchedUserName = null;
    private boolean isLoading = false;

    // Utils
    private NumberFormat currencyFormat;

    // Launchers
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Refresh logic if needed
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        balanceCardBinding = binding.balanceCardView;
        bottomNavBinding = binding.bottomNavCard;

        // Initialize Views
        dailySummaryHeader = findViewById(R.id.dailySummaryHeader);
        dailyDateText = findViewById(R.id.dailyDateText);
        dailyBalanceText = findViewById(R.id.dailyBalanceText);

        transactionSection = findViewById(R.id.transaction_section);
        emptyStateView = findViewById(R.id.emptyStateView);
        transactionTable = findViewById(R.id.transactionTable);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found.");
            signOutUser();
            return;
        }

        currentUserId = currentUser.getUid();
        userRef = mDatabase.child("users").child(currentUserId);
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        Log.d(TAG, "HomePage started for user: " + currentUserId);

        setupUI();
        setupClickListeners();
        setupBottomNavigation();

        // [NEW] Check permissions and show "Shake to Feedback" notification
        checkNotificationPermissionAndShowFeedback();
    }

    // [NEW] Logic to check Android 13+ Notification Permission
    private void checkNotificationPermissionAndShowFeedback() {
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 (Tiramisu) or higher
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted, ask for it
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE_NOTIFICATIONS);
            } else {
                // Permission already granted, enable feedback
                showFeedbackNotification();
            }
        } else {
            // Older Android versions don't need runtime permission
            showFeedbackNotification();
        }
    }

    // [NEW] Logic to actually trigger the Firebase App Distribution notification
    private void showFeedbackNotification() {
        try {
            FirebaseAppDistribution.getInstance().showFeedbackNotification(
                    "Shake your phone to start feedback!",
                    InterruptionLevel.HIGH
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to show feedback notification", e);
        }
    }

    // [NEW] Handle the user's response to the permission popup
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showFeedbackNotification();
            } else {
                // Permission denied. You might want to show a Snackbar explaining why.
                showSnackbar("Notifications disabled. Enable them to use Shake-to-Feedback.");
            }
        }
    }

    private void setupBottomNavigation() {
        bottomNavBinding.btnHome.setSelected(true);

        bottomNavBinding.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());

        bottomNavBinding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
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
            String newCashbookId = data.getStringExtra("selected_cashbook_id");
            String cashbookName = data.getStringExtra("cashbook_name");

            if (newCashbookId != null && !newCashbookId.equals(currentCashbookId)) {
                switchCashbook(newCashbookId);
                showSnackbar("Switched to: " + cashbookName);
            }
        }
    }

    private void loadCashbooksForBadge() {
        if (userRef == null) return;
        if (cashbooksListener != null) {
            userRef.child("cashbooks").removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbooks.clear();
                boolean activeCashbookFound = false;
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        cashbook.setCashbookId(snapshot.getKey());
                        cashbooks.add(cashbook);
                        if (cashbook.getCashbookId().equals(currentCashbookId)) {
                            activeCashbookFound = true;
                        }
                    }
                }

                if (!activeCashbookFound && !cashbooks.isEmpty()) {
                    currentCashbookId = cashbooks.get(0).getCashbookId();
                    saveActiveCashbookId(currentCashbookId);
                } else if (cashbooks.isEmpty()) {
                    setLoadingState(false);
                    updateUserUI();
                    if (emptyStateView != null) emptyStateView.setVisibility(View.VISIBLE);
                    showCreateFirstCashbookDialog();
                    return;
                }

                updateUserUI();
                startListeningForTransactions();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load cashbooks", databaseError.toException());
                setLoadingState(false);
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        };
        userRef.child("cashbooks").addValueEventListener(cashbooksListener);
    }

    private void startListeningForUserProfile() {
        if (userRef == null) return;

        if (userProfileListener != null) {
            userRef.removeEventListener(userProfileListener);
        }

        userProfileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.child("userName").getValue(String.class);
                fetchedUserName = (name != null && !name.isEmpty()) ? name : null;
                updateBalanceCardUser();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load user profile", error.toException());
            }
        };
        userRef.addValueEventListener(userProfileListener);
    }

    private void updateBalanceCardUser() {
        if (binding == null || currentUser == null) return;

        String displayName = (fetchedUserName != null) ? fetchedUserName : getDisplayName(currentUser);
        balanceCardBinding.userNameBottom.setText(displayName);
        balanceCardBinding.uidText.setText("UID: " + currentUserId.substring(0, 8) + "...");
    }

    private void setupUI() {
        setLoadingState(false);
        binding.cashInButton.setContentDescription("Add cash in transaction");
        binding.cashOutButton.setContentDescription("Add cash out transaction");
        binding.userBox.setContentDescription("User information and cashbook selector");

        // Ensure section is always visible
        if (transactionSection != null) transactionSection.setVisibility(View.VISIBLE);
    }

    private void setupClickListeners() {
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null) {
            loadActiveCashbookId();
            startListeningForUserProfile();
        } else {
            Log.e(TAG, "User is not logged in. Redirecting to Signin.");
            signOutUser();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeFirebaseListeners();
        binding = null;
        balanceCardBinding = null;
        bottomNavBinding = null;
    }

    private void signOutUser() {
        mAuth.signOut();
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadActiveCashbookId() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        if (currentCashbookId == null) {
            currentCashbookId = prefs.getString("active_cashbook_id_" + currentUserId, null);
        }
        loadCashbooksForBadge();
    }

    private void startListeningForTransactions() {
        if (transactionsListener != null && currentCashbookId != null && userRef != null) {
            userRef.child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        if (currentCashbookId == null) {
            setLoadingState(false);
            allTransactions.clear();
            renderUI(0, 0, 0, new ArrayList<>()); // Reset UI
            return;
        }

        DatabaseReference transactionsRef = userRef.child("cashbooks")
                .child(currentCashbookId).child("transactions");

        transactionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                new Thread(() -> {
                    ArrayList<TransactionModel> tempList = new ArrayList<>();
                    double calcTotalIn = 0;
                    double calcTotalOut = 0;
                    double calcTodayIn = 0;
                    double calcTodayOut = 0;
                    List<TransactionModel> calcTodaysTransactions = new ArrayList<>();

                    try {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                            if (transaction != null) {
                                transaction.setTransactionId(snapshot.getKey());
                                tempList.add(transaction);

                                if ("IN".equalsIgnoreCase(transaction.getType())) {
                                    calcTotalIn += transaction.getAmount();
                                } else {
                                    calcTotalOut += transaction.getAmount();
                                }

                                if (isToday(transaction.getTimestamp())) {
                                    calcTodaysTransactions.add(transaction);
                                    if ("IN".equalsIgnoreCase(transaction.getType())) {
                                        calcTodayIn += transaction.getAmount();
                                    } else {
                                        calcTodayOut += transaction.getAmount();
                                    }
                                }
                            }
                        }

                        Collections.sort(tempList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                        Collections.sort(calcTodaysTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                        final double finalTotalIn = calcTotalIn;
                        final double finalTotalOut = calcTotalOut;
                        final double finalTodayBalance = calcTodayIn - calcTodayOut;
                        final List<TransactionModel> finalTodayList = calcTodaysTransactions;

                        if (!isDestroyed() && !isFinishing()) {
                            runOnUiThread(() -> {
                                allTransactions = tempList;
                                renderUI(finalTotalIn, finalTotalOut, finalTodayBalance, finalTodayList);
                                setLoadingState(false);
                            });
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error processing transactions in bg", e);
                        runOnUiThread(() -> {
                            setLoadingState(false);
                            showSnackbar("Error loading data");
                        });
                    }
                }).start();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                setLoadingState(false);
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        };

        transactionsRef.addValueEventListener(transactionsListener);
    }

    private void updateUserUI() {
        if (currentUser != null && binding != null) {
            try {
                String cashbookName = "My Cashbook";
                long lastModified = System.currentTimeMillis();

                if (!cashbooks.isEmpty()) {
                    for (CashbookModel cashbook : cashbooks) {
                        if (cashbook.getCashbookId().equals(currentCashbookId)) {
                            cashbookName = cashbook.getName();
                            lastModified = cashbook.getLastModified();
                            break;
                        }
                    }
                }

                binding.userNameTop.setText(cashbookName);

                if (binding.lastOpenedText != null) {
                    String timeSpan = DateTimeUtils.getRelativeTimeSpan(lastModified);
                    binding.lastOpenedText.setText("Last opened: " + timeSpan);
                }

                if (binding.currentCashbookText != null) {
                    binding.currentCashbookText.setText(cashbookName);
                }

                updateBalanceCardUser();

            } catch (Exception e) {
                Log.e(TAG, "Error updating UI", e);
            }
        }
    }

    private String getDisplayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        } else if (user.getEmail() != null) {
            return user.getEmail();
        }
        return "CashFlow User";
    }

    @SuppressLint("SetTextI18n")
    private void renderUI(double totalIncome, double totalExpense, double todayBalance, List<TransactionModel> todaysTransactions) {
        if (binding == null) return;
        try {
            if (transactionTable != null) {
                transactionTable.removeAllViews();
            }

            double globalBalance = totalIncome - totalExpense;

            balanceCardBinding.balanceText.setText(formatCurrency(globalBalance));
            balanceCardBinding.moneyIn.setText(formatCurrency(totalIncome));
            balanceCardBinding.moneyOut.setText(formatCurrency(totalExpense));
            balanceCardBinding.balanceText.setTextColor(Color.WHITE);

            if (dailyDateText != null) {
                dailyDateText.setText(DateTimeUtils.formatDate(System.currentTimeMillis(), "dd MMM yyyy"));
            }

            if (dailyBalanceText != null) {
                String sign = todayBalance >= 0 ? "+ " : "- ";
                dailyBalanceText.setText(sign + formatCurrency(Math.abs(todayBalance)));

                if (todayBalance >= 0) {
                    dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.incomeColor));
                } else {
                    dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.expenseColor));
                }
            }

            if (todaysTransactions == null || todaysTransactions.isEmpty()) {
                if (emptyStateView != null) emptyStateView.setVisibility(View.VISIBLE);
                if (transactionTable != null) transactionTable.setVisibility(View.GONE);
                if (binding.transactionCount != null) binding.transactionCount.setText("TODAY (0)");
            } else {
                if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
                if (transactionTable != null) transactionTable.setVisibility(View.VISIBLE);

                if (binding.transactionCount != null) binding.transactionCount.setText("TODAY (" + todaysTransactions.size() + ")");

                for (TransactionModel transaction : todaysTransactions) {
                    addTransactionRow(transaction);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error rendering transaction table", e);
        }
    }

    private boolean isToday(long timestamp) {
        Calendar transactionCal = Calendar.getInstance();
        transactionCal.setTimeInMillis(timestamp);
        Calendar todayCal = Calendar.getInstance();
        return transactionCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                transactionCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR);
    }

    private String formatCurrency(double amount) {
        if(currencyFormat == null) return "₹" + amount;
        return currencyFormat.format(amount);
    }

    private void addTransactionRow(TransactionModel transaction) {
        if (transactionTable == null) return;

        View rowView = getLayoutInflater().inflate(R.layout.item_transaction_row_daily, transactionTable, false);

        TextView rowCategory = rowView.findViewById(R.id.rowCategory);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        if (rowCategory != null) rowCategory.setText(transaction.getTransactionCategory());
        if (rowMode != null) rowMode.setText(transaction.getPaymentMode());

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            if (rowIn != null) {
                rowIn.setText(formatCurrency(transaction.getAmount()));
                rowIn.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.incomeColor)); // Green
            }
            if (rowOut != null) rowOut.setText("-");
        } else {
            if (rowIn != null) rowIn.setText("-");
            if (rowOut != null) {
                rowOut.setText(formatCurrency(transaction.getAmount()));
                rowOut.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.expenseColor)); // Red
            }
        }

        rowView.setOnClickListener(v -> openTransactionDetail(transaction));

        transactionTable.addView(rowView);
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        detailsLauncher.launch(intent);
    }

    private void openCashInOutActivity(String type) {
        if (currentCashbookId == null) {
            showSnackbar("Please create a cashbook first");
            return;
        }
        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra("transaction_type", type);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void showCreateFirstCashbookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_welcome_cashflow);
        builder.setMessage(R.string.msg_create_first_cashbook);
        final EditText input = new EditText(this);
        input.setHint(R.string.hint_cashbook_name);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        builder.setView(container);
        builder.setCancelable(false);

        builder.setPositiveButton(R.string.btn_create, (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            } else {
                Toast.makeText(this, getString(R.string.error_enter_cashbook_name), Toast.LENGTH_SHORT).show();
                showCreateFirstCashbookDialog();
            }
        });
        builder.show();
    }

    private void createNewCashbook(String name) {
        if (currentUserId == null || userRef == null) return;
        DatabaseReference cashbooksRef = userRef.child("cashbooks");
        String cashbookId = cashbooksRef.push().getKey();
        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            newCashbook.setUserId(currentUserId);
            cashbooksRef.child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Cashbook '" + name + "' created", Toast.LENGTH_SHORT).show();
                        switchCashbook(cashbookId);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to create cashbook", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void switchCashbook(String newCashbookId) {
        if (currentUserId == null) return;

        if (transactionsListener != null && currentCashbookId != null && userRef != null) {
            userRef.child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentCashbookId);

        updateUserUI();
        startListeningForTransactions();
    }

    private void saveActiveCashbookId(String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUserId, cashbookId).apply();
    }

    private void setLoadingState(boolean loading) {
        if (binding == null) return;
        isLoading = loading;
        binding.cashInButton.setEnabled(!loading);
        binding.cashOutButton.setEnabled(!loading);
        binding.userBox.setEnabled(!loading);
    }

    // [FIX] Updated Snackbar to use Helper
    private void showSnackbar(String message) {
        View anchor = (bottomNavBinding != null) ? bottomNavBinding.getRoot() : null;
        SnackbarHelper.show(this, message, anchor);
    }

    private void removeFirebaseListeners() {
        if (userRef == null) return;
        try {
            if (transactionsListener != null && currentCashbookId != null) {
                userRef.child("cashbooks").child(currentCashbookId).child("transactions")
                        .removeEventListener(transactionsListener);
                transactionsListener = null;
            }
            if (cashbooksListener != null) {
                userRef.child("cashbooks").removeEventListener(cashbooksListener);
                cashbooksListener = null;
            }
            if (userProfileListener != null) {
                userRef.removeEventListener(userProfileListener);
                userProfileListener = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing listeners", e);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}