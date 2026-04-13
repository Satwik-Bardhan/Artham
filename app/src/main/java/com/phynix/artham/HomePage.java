package com.phynix.artham;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.facebook.shimmer.ShimmerFrameLayout;

import com.bumptech.glide.Glide;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.databinding.ActivityHomePageBinding;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.DateTimeUtils;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.SwipeListener;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.HomePageViewModel;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int PERMISSION_REQUEST_CODE_NOTIFICATIONS = 101;
    private static final String PREFS_NAME = "AppPrefs";

    // ViewBinding
    private ActivityHomePageBinding binding;

    // ViewModel
    private HomePageViewModel viewModel;

    // Utils
    private NumberFormat currencyFormat;
    private SwipeListener swipeListener;

    // Tracking for "Last Opened" logic
    private String currentActiveBookId = null;
    private String currentCashbookId = null;
    private boolean isTimestampUpdatedForCurrentBook = false;

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
    private ImageView btnYoutube, btnInstagram, btnWebsite, btnGmail, btnFacebook, btnWhatsapp;

    private boolean isBackVisible = false;

    // Firebase user listener for cleanup
    private DatabaseReference userRef;
    private ValueEventListener userListener;

    // --- Launchers ---

    // Launcher for Transaction Details
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Handle any result from details if needed (e.g., refresh data)
            }
    );

    // Launcher for Cashbook Switcher
    private final ActivityResultLauncher<Intent> cashbookSwitchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String newId = result.getData().getStringExtra("selected_cashbook_id");
                    String newName = result.getData().getStringExtra("cashbook_name");

                    if (newId != null) {
                        currentActiveBookId = null;
                        isTimestampUpdatedForCurrentBook = false;

                        currentCashbookId = newId;

                        // SAVE THE SELECTION SECURELY
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putString("last_selected_cashbook_id", newId)
                                .apply();

                        viewModel.switchCashbook(newId);

                        if (newName != null) {
                            binding.userNameTop.setText(newName);
                            binding.currentCashbookText.setText(newName);
                        }

                        showSnackbar("Switched to: " + (newName != null ? newName : "New Cashbook"));
                    }
                }
            }
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

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(HomePageViewModel.class);

        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            signOutUser();
            return;
        }

        // Initialize category cache so user-created category icons/colors are used everywhere
        com.phynix.artham.utils.CategoryColorUtil.initialize();

        // READ THE SELECTION SECURELY ON STARTUP
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String intentId = getIntent().getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        if (intentId == null) intentId = getIntent().getStringExtra("cashbook_id");
        if (intentId == null) intentId = getIntent().getStringExtra("current_cashbook_id");

        if (intentId != null) {
            currentCashbookId = intentId;
            prefs.edit().putString("last_selected_cashbook_id", intentId).apply();
            viewModel.switchCashbook(intentId);
        } else {
            String savedId = prefs.getString("last_selected_cashbook_id", null);
            if (savedId != null) {
                currentCashbookId = savedId;
                viewModel.switchCashbook(savedId);
            }
        }

        bindBalanceCardViews();
        setupBalanceCardFlip();
        setupBottomNavigation();
        setupClickListeners();
        setupStickyScrollLogic();
        observeViewModel();
        fetchUserDataDirectly();
        checkNotificationPermissionAndShowFeedback();
        setupSwipeNavigation();
    }

    // HANDLE RESUMING FROM SETTINGS ACTIVITY
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String newId = intent.getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        if (newId == null) newId = intent.getStringExtra("cashbook_id");
        if (newId == null) newId = intent.getStringExtra("current_cashbook_id");

        if (newId != null && !newId.equals(currentCashbookId)) {
            currentCashbookId = newId;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString("last_selected_cashbook_id", newId)
                    .apply();
            viewModel.switchCashbook(newId);
        }
    }

    private void setupStickyScrollLogic() {
        binding.mainScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            // Safety check in case the view is null or hidden
            if (binding.homeContentLayout == null || binding.homeContentLayout.getVisibility() != View.VISIBLE) return;

            // Wait to ensure views are measured
            if (binding.originalButtons != null && binding.originalButtons.getRoot() != null) {
                // Calculate absolute position inside the scroll view relative layout container
                int buttonsTop = binding.originalButtons.getRoot().getTop() + binding.homeContentLayout.getTop();

                // Show sticky header when original buttons are scrolled out of view
                if (scrollY >= buttonsTop && buttonsTop > 0) {
                    if (binding.stickyActionButtonsContainer.getVisibility() != View.VISIBLE) {
                        binding.stickyActionButtonsContainer.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (binding.stickyActionButtonsContainer.getVisibility() == View.VISIBLE) {
                        binding.stickyActionButtonsContainer.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    private void setupSwipeNavigation() {
        swipeListener = new SwipeListener(this) {
            @Override
            public void onSwipeLeft() {
                // Swipe Left -> Go to Transactions List (if loaded)
                String idToUse = (currentCashbookId != null) ? currentCashbookId : viewModel.getCurrentCashbookId();
                if (idToUse != null) {
                    Intent intent = new Intent(HomePage.this, TransactionActivity.class);
                    intent.putExtra(Constants.EXTRA_CASHBOOK_ID, idToUse);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                } else {
                    showSnackbar("Please wait, loading cashbook...");
                }
            }
            @Override
            public void onSwipeRight() {
                // Swipe Right -> Open Cashbook Switcher
                openCashbookSwitcher();
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        };
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (swipeListener != null) {
            swipeListener.onTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    private void setupBalanceCardFlip() {
        balanceCardFront = binding.balanceCardView.getRoot();
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

            backUserName = balanceCardBack.findViewById(R.id.backUserName);
            backProfileImage = balanceCardBack.findViewById(R.id.backProfileImage);
            btnYoutube = balanceCardBack.findViewById(R.id.btnYoutube);
            btnInstagram = balanceCardBack.findViewById(R.id.btnInstagram);
            btnGmail = balanceCardBack.findViewById(R.id.btnGmail);
            btnFacebook = balanceCardBack.findViewById(R.id.btnFacebook);
            btnWhatsapp = balanceCardBack.findViewById(R.id.btnWhatsapp);

            if (btnYoutube != null) btnYoutube.setOnClickListener(v -> openUrl("https://www.youtube.com/@ArthamApp"));
            if (btnInstagram != null) btnInstagram.setOnClickListener(v -> openUrl("https://www.instagram.com/artham.in"));
            if (btnWebsite != null) btnWebsite.setOnClickListener(v -> openUrl("https://www.artham.com"));
            if (btnFacebook != null) btnFacebook.setOnClickListener(v -> openUrl("https://www.facebook.com/arthamapp"));
            if (btnWhatsapp != null) btnWhatsapp.setOnClickListener(v -> openUrl("https://whatsapp.com/channel/0029Vb6sFJv7dmeibXDqc014"));
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
        // Safe access through the included balanceCardView binding
        balanceCardUidText = binding.balanceCardView.getRoot().findViewById(R.id.uidText);
        balanceCardCopyUidButton = binding.balanceCardView.getRoot().findViewById(R.id.copyUidButton);
        balanceCardUserName = binding.balanceCardView.getRoot().findViewById(R.id.userNameBottom);
        balanceCardMoneyIn = binding.balanceCardView.getRoot().findViewById(R.id.moneyIn);
        balanceCardMoneyOut = binding.balanceCardView.getRoot().findViewById(R.id.moneyOut);
    }

    private void observeViewModel() {

        // Handle Skeleton Loading State Toggle
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                // Show Skeleton with Shimmer Animation, Hide Real Data
                if (binding.homeShimmerLayout != null) {
                    binding.homeShimmerLayout.setVisibility(View.VISIBLE);
                    binding.homeShimmerLayout.startShimmer();
                }
                if (binding.homeContentLayout != null) {
                    binding.homeContentLayout.setVisibility(View.GONE);
                }
                if (binding.stickyActionButtonsContainer != null) {
                    binding.stickyActionButtonsContainer.setVisibility(View.GONE);
                }
            } else {
                // Hide Skeleton, Stop Shimmer, Show Real Data
                if (binding.homeShimmerLayout != null) {
                    binding.homeShimmerLayout.stopShimmer();
                    binding.homeShimmerLayout.setVisibility(View.GONE);
                }
                if (binding.homeContentLayout != null) {
                    binding.homeContentLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) showSnackbar(error);
        });

        viewModel.getUserProfile().observe(this, this::updateUserUI);

        viewModel.getActiveCashbook().observe(this, cashbook -> {
            if (cashbook != null) {
                // NORMAL STATE: Data exists
                if(binding.userNameTop != null) binding.userNameTop.setText(cashbook.getName());
                if(binding.currentCashbookText != null) binding.currentCashbookText.setText(cashbook.getName());

                if (backCashbookIdText != null) backCashbookIdText.setText(cashbook.getCashbookId());

                // Visibility management
                if(binding.transactionSection != null) binding.transactionSection.setVisibility(View.VISIBLE);
                if(binding.transactionTable != null) binding.transactionTable.setVisibility(View.VISIBLE);
                if(binding.emptyStateView != null) binding.emptyStateView.setVisibility(View.GONE);

                currentCashbookId = cashbook.getCashbookId();

                if (currentActiveBookId == null || !currentActiveBookId.equals(cashbook.getCashbookId())) {
                    currentActiveBookId = cashbook.getCashbookId();
                    isTimestampUpdatedForCurrentBook = false;

                    // Set Exact Date and Time BEFORE we push the new update to Firebase
                    if(binding.lastOpenedText != null) binding.lastOpenedText.setText("Last opened: " + formatExactDateTimeIST(cashbook.getLastModified()));
                }

                if (!isTimestampUpdatedForCurrentBook) {
                    updateCashbookLastOpened(cashbook.getCashbookId());
                    isTimestampUpdatedForCurrentBook = true;
                }

            } else {
                // EMPTY STATE: No Cashbook found
                if(binding.userNameTop != null) binding.userNameTop.setText("Welcome!");
                if(binding.currentCashbookText != null) binding.currentCashbookText.setText("No Cashbook Selected");
                if(binding.lastOpenedText != null) binding.lastOpenedText.setText("Create a new cashbook to start");

                // Visibility management
                if(binding.transactionSection != null) binding.transactionSection.setVisibility(View.GONE);
                if(binding.transactionTable != null) binding.transactionTable.setVisibility(View.GONE);
                if(binding.emptyStateView != null) binding.emptyStateView.setVisibility(View.GONE);

                currentCashbookId = null;
            }
        });

        viewModel.getTotalIncome().observe(this, income -> {
            if (balanceCardMoneyIn != null) balanceCardMoneyIn.setText(AmountFormatter.formatCompactSpannable(income));
        });

        viewModel.getTotalExpense().observe(this, expense -> {
            if (balanceCardMoneyOut != null) balanceCardMoneyOut.setText(AmountFormatter.formatCompactSpannable(expense));
        });

        viewModel.getCurrentBalance().observe(this, balance -> {
            // Using ViewBinding to get the balanceText within the balance card
            TextView balanceText = binding.balanceCardView.getRoot().findViewById(R.id.balanceText);
            if (balanceText != null) {
                // Adaptive sizing: text shrinks as digit count grows, paise rendered smaller
                AmountFormatter.setAdaptiveBalance(balanceText, balance);
                balanceText.setTextColor(Color.WHITE);
            }
        });

        viewModel.getTodayBalance().observe(this, balance -> {
            // Utilizing the included layout reference for safe access
            TextView dailyDateText = binding.dailySummaryInclude.getRoot().findViewById(R.id.dailyDateText);
            TextView dailyBalanceText = binding.dailySummaryInclude.getRoot().findViewById(R.id.dailyBalanceText);

            if(dailyDateText != null) dailyDateText.setText(DateTimeUtils.formatDate(System.currentTimeMillis(), Constants.DATE_FORMAT_DISPLAY));

            if(dailyBalanceText != null) {
                String sign = balance >= 0 ? "+ " : "- ";
                String raw = sign + AmountFormatter.formatCompact(Math.abs(balance));
                dailyBalanceText.setText(AmountFormatter.buildPaiseSpannable(raw));
                dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, balance >= 0 ? R.attr.chk_incomeColor : R.attr.chk_expenseColor));
            }
        });

        viewModel.getTodaysTransactions().observe(this, this::updateTransactionTable);
    }

    /**
     * Formats timestamp to exact dd MMM yyyy, hh:mm:ss a format in IST.
     */
    private String formatExactDateTimeIST(long timestamp) {
        if (timestamp <= 0) return "Never";

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault());
        // Set to Indian Standard Time
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

        return sdf.format(new Date(timestamp));
    }

    private void updateCashbookLastOpened(String cashbookId) {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null || cashbookId == null) return;

        DatabaseReference bookRef = FirebaseDatabase.getInstance()
                .getReference("cashbooks")
                .child(fbUser.getUid())
                .child(cashbookId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastModified", ServerValue.TIMESTAMP);

        bookRef.updateChildren(updates).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to update last opened time", e);
            if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                Log.e(TAG, "CHECK FIREBASE RULES: Write denied at " + bookRef.toString());
            }
        });
    }

    private void fetchUserDataDirectly() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) return;
        updateUserUI(null);
        userRef = FirebaseDatabase.getInstance().getReference("users").child(fbUser.getUid());
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing()) return;
                Users user = snapshot.getValue(Users.class);
                updateUserUI(user);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        userRef.addValueEventListener(userListener);
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
        if (balanceCardUserName != null) balanceCardUserName.setText(name);
        if (balanceCardUidText != null) balanceCardUidText.setText("UID: " + uid);
        if (backUserName != null) backUserName.setText(name);
        if (backProfileImage != null && !isDestroyed() && !isFinishing()) {
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

    // --- ENHANCED LOGIC FOR EMPTY STATE & DEMO ROW ---
    private void updateTransactionTable(List<TransactionModel> transactions) {
        if(binding.transactionTable == null || binding.transactionCount == null || binding.emptyStateView == null) return;

        binding.transactionTable.removeAllViews();

        if (transactions == null || transactions.isEmpty()) {
            binding.transactionCount.setText("TODAY (0)");

            // Show our styled empty state banner
            binding.emptyStateView.setVisibility(View.VISIBLE);
            // Keep the table visible so the user can see the fake demo row
            binding.transactionTable.setVisibility(View.VISIBLE);

        } else {
            binding.transactionCount.setText("TODAY (" + transactions.size() + ")");

            // Hide the empty state banner as we have real data
            binding.emptyStateView.setVisibility(View.GONE);
            binding.transactionTable.setVisibility(View.VISIBLE);

            for (TransactionModel t : transactions) addTransactionRow(t, binding.transactionTable);
        }
    }

    private void addTransactionRow(TransactionModel transaction, ViewGroup tableLayout) {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_transaction_report_row, tableLayout, false);
        TextView rowCategory = rowView.findViewById(R.id.rowCategory);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        rowCategory.setText(transaction.getTransactionCategory());

        String mode = transaction.getPaymentMode();
        rowMode.setText(mode != null && !mode.isEmpty() ? mode : "Online");

        rowIn.setGravity(Gravity.CENTER);
        rowOut.setGravity(Gravity.CENTER);

        if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(transaction.getType())) {
            rowIn.setText(AmountFormatter.formatCompactSpannable(transaction.getAmount()));
            rowIn.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_incomeColor));
            rowOut.setText("-");
        } else {
            rowIn.setText("-");
            rowOut.setText(AmountFormatter.formatCompactSpannable(transaction.getAmount()));
            rowOut.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_expenseColor));
        }
        rowView.setOnClickListener(v -> openTransactionDetail(transaction));
        tableLayout.addView(rowView);
    }

    private void setupBottomNavigation() {
        // Updated to use the viewbinding mapped object for bottom navigation
        binding.bottomNavCard.getRoot().findViewById(R.id.btnHome).setSelected(true);
        binding.bottomNavCard.getRoot().findViewById(R.id.btnTransactions).setOnClickListener(v -> {
            String idToUse = (currentCashbookId != null) ? currentCashbookId : viewModel.getCurrentCashbookId();
            if (idToUse != null) {
                Intent intent = new Intent(this, TransactionActivity.class);
                intent.putExtra(Constants.EXTRA_CASHBOOK_ID, idToUse);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            } else {
                showSnackbar("Please select a cashbook first");
            }
        });
        binding.bottomNavCard.getRoot().findViewById(R.id.btnCashbookSwitch).setOnClickListener(v -> openCashbookSwitcher());
        binding.bottomNavCard.getRoot().findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void setupClickListeners() {
        if(binding.userBox != null) {
            binding.userBox.setOnClickListener(v -> openCashbookSwitcher());
        }

        // Setup original buttons utilizing ViewBinding's include handling
        if (binding.originalButtons != null) {
            binding.originalButtons.btnCashIn.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_IN));
            binding.originalButtons.btnCashOut.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_OUT));
        }

        // Setup sticky buttons utilizing ViewBinding's include handling
        if(binding.stickyButtons != null) {
            binding.stickyButtons.btnCashIn.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_IN));
            binding.stickyButtons.btnCashOut.setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_OUT));
        }
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, transaction);
        intent.putExtra(Constants.EXTRA_CASHBOOK_ID, currentCashbookId);
        detailsLauncher.launch(intent);
    }

    private void openCashInOutActivity(String type) {
        String idToUse = (currentCashbookId != null) ? currentCashbookId : viewModel.getCurrentCashbookId();

        if (idToUse == null) {
            showSnackbar("Please create a cashbook first");
            openCashbookSwitcher();
            return;
        }
        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra(Constants.EXTRA_TRANSACTION_TYPE, type);
        intent.putExtra(Constants.EXTRA_CASHBOOK_ID, idToUse);
        startActivity(intent);
    }

    private void openCashbookSwitcher() {
        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        cashbookSwitchLauncher.launch(intent);
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
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
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