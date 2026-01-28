package com.phynix.artham;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
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
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.DateTimeUtils;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.HomePageViewModel;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;
    private static final int PERMISSION_REQUEST_CODE_NOTIFICATIONS = 101;

    // ViewBinding
    private ActivityHomePageBinding binding;

    // ViewModel
    private HomePageViewModel viewModel;

    // Utils
    private NumberFormat currencyFormat;

    // Front Card Views
    private View balanceCardFront;
    private TextView balanceCardUidText;
    private ImageView balanceCardCopyUidButton;
    private TextView balanceCardUserName;
    private TextView balanceCardMoneyIn;
    private TextView balanceCardMoneyOut;

    // Back Card Views
    private View balanceCardBack;
    private TextView backCashbookIdText;
    private TextView backUserName;
    private ImageView backProfileImage;
    private ImageView btnYoutube, btnInstagram, btnWebsite, btnGmail, btnFacebook;

    private boolean isBackVisible = false;

    // Launchers
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {}
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);

        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        viewModel = new ViewModelProvider(this).get(HomePageViewModel.class);
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            signOutUser();
            return;
        }

        bindBalanceCardViews();
        setupUI();
        setupBalanceCardFlip();
        setupBottomNavigation();
        setupClickListeners();
        observeViewModel();
        fetchUserDataDirectly();
        checkNotificationPermissionAndShowFeedback();
    }

    private void setupBalanceCardFlip() {
        balanceCardFront = findViewById(R.id.balanceCardView);
        if (balanceCardFront == null) return;

        balanceCardBack = LayoutInflater.from(this).inflate(R.layout.component_balance_card_back, null);

        ViewGroup parent = (ViewGroup) balanceCardFront.getParent();
        if (parent != null) {
            int index = parent.indexOfChild(balanceCardFront);

            ViewGroup.LayoutParams params = balanceCardFront.getLayoutParams();
            balanceCardBack.setLayoutParams(params);
            balanceCardBack.setVisibility(View.GONE);

            parent.addView(balanceCardBack, index);

            float scale = getResources().getDisplayMetrics().density;
            balanceCardFront.setCameraDistance(8000 * scale);
            balanceCardBack.setCameraDistance(8000 * scale);

            // Bind Back Views
            backUserName = balanceCardBack.findViewById(R.id.backUserName);
            backProfileImage = balanceCardBack.findViewById(R.id.backProfileImage);
            btnYoutube = balanceCardBack.findViewById(R.id.btnYoutube);
            btnInstagram = balanceCardBack.findViewById(R.id.btnInstagram);
            btnWebsite = balanceCardBack.findViewById(R.id.btnWebsite);
            btnGmail = balanceCardBack.findViewById(R.id.btnGmail);
            btnFacebook = balanceCardBack.findViewById(R.id.btnFacebook);

            // Setup Social Links
            if (btnYoutube != null) btnYoutube.setOnClickListener(v -> openUrl("https://www.youtube.com/@ArthamApp"));
            if (btnInstagram != null) btnInstagram.setOnClickListener(v -> openUrl("https://www.instagram.com/artham.in"));
            if (btnWebsite != null) btnWebsite.setOnClickListener(v -> openUrl("https://www.artham.com"));
            if (btnFacebook != null) btnFacebook.setOnClickListener(v -> openUrl("https://www.facebook.com/arthamapp"));
            if (btnGmail != null) btnGmail.setOnClickListener(v -> sendEmail());

            balanceCardFront.setOnClickListener(v -> flipCard());
            balanceCardBack.setOnClickListener(v -> flipCard());
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendEmail() {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@artham.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void flipCard() {
        final View visibleView = isBackVisible ? balanceCardBack : balanceCardFront;
        final View invisibleView = isBackVisible ? balanceCardFront : balanceCardBack;

        ObjectAnimator flipOut = ObjectAnimator.ofFloat(visibleView, "rotationY", 0f, 90f);
        flipOut.setDuration(250);
        flipOut.setInterpolator(new AccelerateDecelerateInterpolator());

        final ObjectAnimator flipIn = ObjectAnimator.ofFloat(invisibleView, "rotationY", -90f, 0f);
        flipIn.setDuration(250);
        flipIn.setInterpolator(new DecelerateInterpolator());

        flipOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                visibleView.setVisibility(View.GONE);
                invisibleView.setVisibility(View.VISIBLE);
                flipIn.start();
                isBackVisible = !isBackVisible;
            }
        });

        flipOut.start();
    }

    private void bindBalanceCardViews() {
        balanceCardUidText = findViewById(R.id.uidText);
        balanceCardCopyUidButton = findViewById(R.id.copyUidButton);
        balanceCardUserName = findViewById(R.id.userNameBottom);
        balanceCardMoneyIn = findViewById(R.id.moneyIn);
        balanceCardMoneyOut = findViewById(R.id.moneyOut);
    }

    private void setupUI() {
        binding.transactionSection.setVisibility(View.VISIBLE);
        binding.transactionTable.setVisibility(View.VISIBLE);
        binding.emptyStateView.setVisibility(View.GONE);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading && binding.userNameTop.getText().toString().isEmpty()) {
                binding.userNameTop.setText("Loading...");
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) showSnackbar(error);
        });

        viewModel.getUserProfile().observe(this, this::updateUserUI);

        viewModel.getActiveCashbook().observe(this, cashbook -> {
            if (cashbook != null) {
                binding.userNameTop.setText(cashbook.getName());
                binding.currentCashbookText.setText(cashbook.getName());
                binding.lastOpenedText.setText("Last opened: " + DateTimeUtils.getRelativeTimeSpan(cashbook.getLastModified()));
                if (backCashbookIdText != null) backCashbookIdText.setText(cashbook.getCashbookId());
            } else {
                binding.userNameTop.setText("No Cashbook");
            }
        });

        viewModel.getTotalIncome().observe(this, income -> {
            if (balanceCardMoneyIn != null) balanceCardMoneyIn.setText(formatCurrency(income));
        });
        viewModel.getTotalExpense().observe(this, expense -> {
            if (balanceCardMoneyOut != null) balanceCardMoneyOut.setText(formatCurrency(expense));
        });
        viewModel.getCurrentBalance().observe(this, balance -> {
            if (binding.balanceCardView != null) {
                binding.balanceCardView.balanceText.setText(formatCurrency(balance));
                binding.balanceCardView.balanceText.setTextColor(Color.WHITE);
            }
        });

        viewModel.getTodayBalance().observe(this, balance -> {
            binding.dailySummaryInclude.dailyDateText.setText(DateTimeUtils.formatDate(System.currentTimeMillis(), Constants.DATE_FORMAT_DISPLAY));
            String sign = balance >= 0 ? "+ " : "- ";
            binding.dailySummaryInclude.dailyBalanceText.setText(sign + formatCurrency(Math.abs(balance)));
            binding.dailySummaryInclude.dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, balance >= 0 ? R.attr.chk_incomeColor : R.attr.chk_expenseColor));
        });

        viewModel.getTodaysTransactions().observe(this, this::updateTransactionTable);
    }

    private void fetchUserDataDirectly() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) return;
        updateUserUI(null);
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(fbUser.getUid());
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Users user = snapshot.getValue(Users.class);
                updateUserUI(user);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateUserUI(Users user) {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        String name = "User";
        String uid = "";
        String photoUrl = null;

        if (user != null) {
            if (user.getUserName() != null && !user.getUserName().isEmpty()) name = user.getUserName();
            else if (user.getName() != null && !user.getName().isEmpty()) name = user.getName();
            photoUrl = user.getProfile();
        }

        if (name.equals("User") && fbUser != null && fbUser.getDisplayName() != null && !fbUser.getDisplayName().isEmpty()) {
            name = fbUser.getDisplayName();
        }
        if (fbUser != null) uid = fbUser.getUid();
        if (photoUrl == null && fbUser != null && fbUser.getPhotoUrl() != null) {
            photoUrl = fbUser.getPhotoUrl().toString();
        }

        // Update Front
        if (balanceCardUserName != null) balanceCardUserName.setText(name);
        if (balanceCardUidText != null) balanceCardUidText.setText("UID: " + uid);

        // Update Back
        if (backUserName != null) backUserName.setText(name);
        if (backProfileImage != null) {
            backProfileImage.clearColorFilter();
            Glide.with(this).load(photoUrl).placeholder(R.drawable.ic_person_placeholder).circleCrop().into(backProfileImage);
        }

        if (balanceCardCopyUidButton != null && !uid.isEmpty()) {
            final String uidToCopy = uid;
            balanceCardCopyUidButton.setOnClickListener(v -> copyToClipboard("UID", uidToCopy));
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateTransactionTable(List<TransactionModel> transactions) {
        binding.transactionTable.removeAllViews();
        if (transactions == null || transactions.isEmpty()) {
            binding.emptyStateView.setVisibility(View.VISIBLE);
            binding.transactionTable.setVisibility(View.GONE);
            binding.transactionCount.setText("TODAY (0)");
        } else {
            binding.emptyStateView.setVisibility(View.GONE);
            binding.transactionTable.setVisibility(View.VISIBLE);
            binding.transactionCount.setText("TODAY (" + transactions.size() + ")");
            for (TransactionModel t : transactions) addTransactionRow(t);
        }
    }

    private void addTransactionRow(TransactionModel transaction) {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_transaction_report_row, binding.transactionTable, false);
        TextView rowCategory = rowView.findViewById(R.id.rowCategory);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        rowCategory.setText(transaction.getTransactionCategory());
        rowMode.setText(transaction.getPaymentMode());

        // [FIX] Center align In/Out amounts
        rowIn.setGravity(Gravity.CENTER);
        rowOut.setGravity(Gravity.CENTER);

        if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(transaction.getType())) {
            rowIn.setText(formatCurrency(transaction.getAmount()));
            rowIn.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_incomeColor));
            rowOut.setText("-");
        } else {
            rowIn.setText("-");
            rowOut.setText(formatCurrency(transaction.getAmount()));
            rowOut.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_expenseColor));
        }

        rowView.setOnClickListener(v -> openTransactionDetail(transaction));
        binding.transactionTable.addView(rowView);
    }

    private void setupBottomNavigation() {
        binding.bottomNavCard.btnHome.setSelected(true);
        binding.bottomNavCard.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
        binding.bottomNavCard.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());
        binding.bottomNavCard.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    private void setupClickListeners() {
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_IN));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_OUT));
        binding.userBox.setOnClickListener(v -> openCashbookSwitcher());
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, transaction);
        intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
        detailsLauncher.launch(intent);
    }

    private void openCashInOutActivity(String type) {
        if (viewModel.getCurrentCashbookId() == null) {
            showSnackbar("Please create a cashbook first");
            return;
        }
        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra(Constants.EXTRA_TRANSACTION_TYPE, type);
        intent.putExtra(Constants.EXTRA_CASHBOOK_ID, viewModel.getCurrentCashbookId());
        startActivity(intent);
    }

    private void openCashbookSwitcher() {
        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", viewModel.getCurrentCashbookId());
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newId = data.getStringExtra("selected_cashbook_id");
            if (newId != null) {
                viewModel.switchCashbook(newId);
                showSnackbar("Switched to: " + data.getStringExtra("cashbook_name"));
            }
        }
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
        if (requestCode == PERMISSION_REQUEST_CODE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showFeedbackNotification();
        }
    }

    private void showFeedbackNotification() {
        try {
            FirebaseAppDistribution.getInstance().showFeedbackNotification("Shake to feedback!", InterruptionLevel.HIGH);
        } catch (Exception e) {}
    }

    private String formatCurrency(double amount) {
        return (currencyFormat == null) ? "₹" + amount : currencyFormat.format(amount);
    }

    private void showSnackbar(String message) {
        View anchor = (binding.bottomNavCard != null) ? binding.bottomNavCard.getRoot() : null;
        SnackbarHelper.show(this, message, anchor);
    }

    private void signOutUser() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, SigninActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) return typedValue.data;
            return Color.BLACK;
        }
    }
}