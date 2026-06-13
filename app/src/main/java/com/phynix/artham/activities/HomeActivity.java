package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import com.phynix.artham.SignInActivity;
import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.phynix.artham.adapters.DailyBalanceAdapter;
import com.phynix.artham.databinding.ActivityHomeBinding;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.DateTimeUtils;
import com.phynix.artham.utils.SnackbarHelper;
import com.phynix.artham.utils.SessionCache;
import com.phynix.artham.db.DataRepository;

import com.phynix.artham.utils.NavPillAnimator;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.utils.OnboardingManager;

import com.phynix.artham.utils.OnboardingOverlay;
import com.phynix.artham.utils.DialogUtils;
import com.phynix.artham.viewmodels.HomeViewModel;

import android.app.Dialog;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.phynix.artham.utils.ThemeUtil;
import androidx.recyclerview.widget.RecyclerView;
public class HomeActivity extends BaseActivity {

    private static final String TAG = "HomeActivity";
    private static final int PERMISSION_REQUEST_CODE_NOTIFICATIONS = 101;
    private static final String PREFS_NAME = "AppPrefs";

    // ViewBinding
    private ActivityHomeBinding binding;

    // ViewModel
    private HomeViewModel viewModel;

    // Utils
    private NumberFormat currencyFormat;


    // Skeleton loading timeout handler (force-hide after 2 seconds when offline)
    private final Handler skeletonTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable skeletonTimeoutRunnable = () -> {
        // Force-hide the skeleton and show home content so the user can add entries
        // offline
        if (binding != null) {
            if (binding.homeShimmerLayout != null) {
                binding.homeShimmerLayout.stopShimmer();
                binding.homeShimmerLayout.setVisibility(View.GONE);
            }
            if (binding.homeContentLayout != null) {
                binding.homeContentLayout.setVisibility(View.VISIBLE);
            }
        }
    };

    // Tracking for "Last Opened" logic
    private String currentActiveBookId = null;
    private String currentCashbookId = null;
    private boolean isTimestampUpdatedForCurrentBook = false;

    // Front Card Views
    private View balanceCardFront;
    private TextView balanceCardCashbookName;

    private TextView balanceCardMoneyIn;
    private TextView balanceCardMoneyOut;

    // Back Card Views
    private View balanceCardBack;
    private TextView backCashbookIdText;
    private TextView backUserName;
    private ImageView backProfileImage;
    private View btnInstagram, btnWebsite, btnGmail, btnWhatsapp;

    private boolean isBackVisible = false;

    // Cached recent (previous) transactions for backfill display
    private List<TransactionModel> cachedRecentTransactions = new ArrayList<>();



    // Insights UI
    private TextView insightEmoji, insightTitle, insightMessage;
    private LinearLayout insightDots;
    private FrameLayout insightContentFrame;
    private View insightsCardInclude;
    private List<com.phynix.artham.utils.InsightsEngine.Insight> currentInsights = new ArrayList<>();
    private int currentInsightIndex = 0;
    private Handler insightHandler = new Handler(Looper.getMainLooper());
    private Runnable insightRunnable;

    // Firebase user listener for cleanup
    private DatabaseReference userRef;
    private ValueEventListener userListener;

    // --- Launchers ---

    // Launcher for Transaction Details
    private final ActivityResultLauncher<Intent> detailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String action = result.getData().getStringExtra("action");
                    if ("delete".equals(action)) {
                        TransactionModel deletedTx = (TransactionModel) result.getData().getSerializableExtra("transaction");
                        if (deletedTx != null && currentCashbookId != null) {
                            View anchor = (binding.bottomNavCard != null) ? binding.bottomNavCard.getRoot() : null;
                            SnackbarHelper.showWithAction(this, "Transaction deleted", "UNDO", v -> {
                                DataRepository.getInstance(getApplication())
                                        .updateTransaction(currentCashbookId, deletedTx, null);
                            }, anchor);
                        } else {
                            showSnackbar("Transaction deleted");
                        }
                    }
                }
            });

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

                        if (balanceCardCashbookName != null)
                            balanceCardCashbookName.setText(newName);

                        if (newName != null) {
                            // Save name for widget display & refresh widget
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                    .edit()
                                    .putString("last_selected_cashbook_name", newName)
                                    .apply();
                            refreshWidget();
                        }

                        showSnackbar("Switched to: " + (newName != null ? newName : "New Cashbook"));
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        if (!isLocal && FirebaseAuth.getInstance().getCurrentUser() == null) {
            signOutUser();
            return;
        }

        // Initialize category cache so user-created category icons/colors are used
        // everywhere
        com.phynix.artham.utils.CategoryColorUtil.initialize();

        // READ THE SELECTION SECURELY ON STARTUP
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String intentId = getIntent().getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        if (intentId == null)
            intentId = getIntent().getStringExtra("cashbook_id");
        if (intentId == null)
            intentId = getIntent().getStringExtra("current_cashbook_id");

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

        initInsightsUI();
        bindBalanceCardViews();
        setupBalanceCardFlip();

        setupBottomNavigation();
        setupClickListeners();
        setupStickyScrollLogic();
        observeViewModel();
        fetchUserDataDirectly();
        requestNotificationPermission();
        maybeShowInAppReview();

    }

    // HANDLE RESUMING FROM SETTINGS ACTIVITY
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String newId = intent.getStringExtra(Constants.EXTRA_CASHBOOK_ID);
        if (newId == null)
            newId = intent.getStringExtra("cashbook_id");
        if (newId == null)
            newId = intent.getStringExtra("current_cashbook_id");

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
        binding.mainScrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    // Safety check in case the view is null or hidden
                    if (binding.homeContentLayout == null || binding.homeContentLayout.getVisibility() != View.VISIBLE)
                        return;

                    // Wait to ensure views are measured
                    if (binding.originalButtons != null && binding.originalButtons.getRoot() != null) {
                        // Calculate absolute position inside the scroll view relative layout container
                        int buttonsTop = binding.originalButtons.getRoot().getTop()
                                + binding.homeContentLayout.getTop();

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



    private void setupBalanceCardFlip() {
        balanceCardFront = binding.balanceCardView.getRoot();
        if (balanceCardFront == null)
            return;

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
            btnInstagram = balanceCardBack.findViewById(R.id.btnInstagram);
            btnGmail = balanceCardBack.findViewById(R.id.btnGmail);
            btnWhatsapp = balanceCardBack.findViewById(R.id.btnWhatsapp);

            if (btnInstagram != null)
                btnInstagram.setOnClickListener(v -> openUrl("https://www.instagram.com/artham.in"));
            if (btnWebsite != null)
                btnWebsite.setOnClickListener(v -> openUrl("https://www.artham.com"));
            if (btnWhatsapp != null)
                btnWhatsapp.setOnClickListener(v -> openUrl("https://whatsapp.com/channel/0029Vb6sFJv7dmeibXDqc014"));
            if (btnGmail != null)
                btnGmail.setOnClickListener(v -> sendEmail());

            View socialLinksContainer = balanceCardBack.findViewById(R.id.socialLinksContainer);
            if (socialLinksContainer != null) {
                socialLinksContainer.setOnClickListener(v -> {
                    // Consume click to prevent card flip when tapping the social media row
                });
            }

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
            intent.setData(Uri.parse("mailto:arthamhq@gmail.com"));
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
        balanceCardCashbookName = binding.balanceCardView.getRoot().findViewById(R.id.balanceCardCashbookName);

        balanceCardMoneyIn = binding.balanceCardView.getRoot().findViewById(R.id.moneyIn);
        balanceCardMoneyOut = binding.balanceCardView.getRoot().findViewById(R.id.moneyOut);
    }

    private void initInsightsUI() {
        insightsCardInclude = binding.getRoot().findViewById(R.id.insightsCardInclude);
        if (insightsCardInclude != null) {
            insightEmoji = insightsCardInclude.findViewById(R.id.insightEmoji);
            insightTitle = insightsCardInclude.findViewById(R.id.insightTitle);
            insightMessage = insightsCardInclude.findViewById(R.id.insightMessage);
            insightDots = insightsCardInclude.findViewById(R.id.insightDots);
            insightContentFrame = insightsCardInclude.findViewById(R.id.insightContentFrame);
        }
    }

    private void observeViewModel() {

        // Handle Skeleton Loading State Toggle
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                // Delay skeleton show by 150ms — if data arrives from cache, skeleton never flashes
                skeletonTimeoutHandler.postDelayed(() -> {
                    if (binding == null) return;
                    // Only show skeleton if still loading (data hasn't arrived yet)
                    if (Boolean.TRUE.equals(viewModel.getIsLoading().getValue())) {
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
                    }
                }, 150);

                // Schedule a 1-second timeout to force-show home content when offline
                skeletonTimeoutHandler.removeCallbacks(skeletonTimeoutRunnable);
                skeletonTimeoutHandler.postDelayed(skeletonTimeoutRunnable, 1000);
            } else {
                // Data loaded successfully — cancel any pending timeout and skeleton show
                skeletonTimeoutHandler.removeCallbacksAndMessages(null);

                // Hide Skeleton, Stop Shimmer, Show Real Data
                if (binding.homeShimmerLayout != null) {
                    binding.homeShimmerLayout.stopShimmer();
                    binding.homeShimmerLayout.setVisibility(View.GONE);
                }
                if (binding.homeContentLayout != null) {
                    binding.homeContentLayout.setVisibility(View.VISIBLE);
                }

                // ── Onboarding: Check if first launch ──
                checkAndShowOnboarding();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty())
                showSnackbar(error);
        });

        viewModel.getTransactions().observe(this, this::updateInsights);

        viewModel.getUserProfile().observe(this, this::updateUserUI);

        viewModel.getActiveCashbook().observe(this, cashbook -> {
            if (cashbook != null) {
                // NORMAL STATE: Data exists
                if (balanceCardCashbookName != null)
                    balanceCardCashbookName.setText(cashbook.getName());

                if (backCashbookIdText != null)
                    backCashbookIdText.setText(cashbook.getCashbookId());

                // Visibility management
                if (binding.transactionSection != null)
                    binding.transactionSection.setVisibility(View.VISIBLE);
                if (binding.transactionTable != null)
                    binding.transactionTable.setVisibility(View.VISIBLE);
                if (binding.emptyStateView != null)
                    binding.emptyStateView.setVisibility(View.GONE);

                currentCashbookId = cashbook.getCashbookId();

                // Save cashbook name for widget display & refresh widget
                if (cashbook.getName() != null) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString("last_selected_cashbook_name", cashbook.getName())
                            .apply();
                    refreshWidget();
                }

                if (currentActiveBookId == null || !currentActiveBookId.equals(cashbook.getCashbookId())) {
                    currentActiveBookId = cashbook.getCashbookId();
                    isTimestampUpdatedForCurrentBook = false;

                }

                if (!isTimestampUpdatedForCurrentBook) {
                    updateCashbookLastOpened(cashbook.getCashbookId());
                    isTimestampUpdatedForCurrentBook = true;
                }

            } else {
                // EMPTY STATE: No Cashbook found
                // Removed userNameTop and currentCashbookText assignments
                if (balanceCardCashbookName != null)
                    balanceCardCashbookName.setText("No Cashbook Selected");
                // Removed lastOpenedText assignment

                // Visibility management
                if (binding.transactionSection != null)
                    binding.transactionSection.setVisibility(View.GONE);
                if (binding.transactionTable != null)
                    binding.transactionTable.setVisibility(View.GONE);
                if (binding.emptyStateView != null)
                    binding.emptyStateView.setVisibility(View.GONE);

                currentCashbookId = null;
            }
        });

        viewModel.getTotalIncome().observe(this, income -> {
            if (balanceCardMoneyIn != null)
                AmountFormatter.setAdaptiveAmount(balanceCardMoneyIn, income, 14f, 9f);
        });

        viewModel.getTotalExpense().observe(this, expense -> {
            if (balanceCardMoneyOut != null)
                AmountFormatter.setAdaptiveAmount(balanceCardMoneyOut, expense, 14f, 9f);
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

            if (dailyDateText != null)
                dailyDateText
                        .setText(DateTimeUtils.formatDate(System.currentTimeMillis(), Constants.DATE_FORMAT_DISPLAY));

            if (dailyBalanceText != null) {
                String sign = balance >= 0 ? "+ " : "- ";
                AmountFormatter.setAdaptiveAmount(dailyBalanceText, Math.abs(balance), 16f, 10f);
                // Prepend the sign to the adaptive text
                dailyBalanceText.setText(android.text.TextUtils.concat(sign, dailyBalanceText.getText()));
                dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this,
                        balance >= 0 ? R.attr.chk_incomeColor : R.attr.chk_expenseColor));
            }
        });

        viewModel.getTodaysTransactions().observe(this, this::updateTransactionTable);

        viewModel.getRecentTransactions().observe(this, recent -> {
            cachedRecentTransactions = (recent != null) ? recent : new ArrayList<>();
            // Re-render the table since backfill data changed
            List<TransactionModel> todayList = viewModel.getTodaysTransactions().getValue();
            updateTransactionTable(todayList);
        });

    }

    /**
     * Formats timestamp to exact dd MMM yyyy, hh:mm:ss a format in IST.
     */
    private String formatExactDateTimeIST(long timestamp) {
        if (timestamp <= 0)
            return "Never";

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault());
        // Set to Indian Standard Time
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

        return sdf.format(new Date(timestamp));
    }

    private void updateCashbookLastOpened(String cashbookId) {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null || cashbookId == null)
            return;

        DatabaseReference bookRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(fbUser.getUid())
                .child("cashbooks")
                .child(cashbookId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastOpenedAt", ServerValue.TIMESTAMP);

        bookRef.updateChildren(updates).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to update last opened time", e);
            if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                Log.e(TAG, "CHECK FIREBASE RULES: Write denied at " + bookRef.toString());
            }
        });
    }

    private void fetchUserDataDirectly() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null)
            return;

        // Instantly populate from session cache if available (prevents flicker on tab switch)
        SessionCache cache = SessionCache.getInstance();
        if (cache.hasUserProfile()) {
            updateUserUI(cache.getCachedUserProfile());
        } else {
            updateUserUI(null);
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(fbUser.getUid());
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing())
                    return;
                Users user = snapshot.getValue(Users.class);
                // Cache for other activities
                SessionCache.getInstance().cacheUserProfile(user);
                updateUserUI(user);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        userRef.addValueEventListener(userListener);
    }

    private void updateUserUI(Users user) {
        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        if (isLocal) {
            if (backUserName != null)
                backUserName.setText("Local User");
            if (backProfileImage != null && !isDestroyed() && !isFinishing()) {
                backProfileImage.clearColorFilter();
                backProfileImage.setImageResource(R.drawable.ic_person_placeholder);
            }
            return;
        }

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        String name = "User";
        String uid = "";
        String photoUrl = null;
        if (user != null) {
            if (user.getUserName() != null && !user.getUserName().isEmpty())
                name = user.getUserName();
            else if (user.getName() != null && !user.getName().isEmpty())
                name = user.getName();
            photoUrl = user.getProfile();
        }
        if (name.equals("User") && fbUser != null && fbUser.getDisplayName() != null
                && !fbUser.getDisplayName().isEmpty()) {
            name = fbUser.getDisplayName();
        }
        if (fbUser != null)
            uid = fbUser.getUid();
        if (photoUrl == null && fbUser != null && fbUser.getPhotoUrl() != null) {
            photoUrl = fbUser.getPhotoUrl().toString();
        }

        if (backUserName != null)
            backUserName.setText(name);
        if (backProfileImage != null && !isDestroyed() && !isFinishing()) {
            backProfileImage.clearColorFilter();
            Glide.with(this).load(photoUrl).placeholder(R.drawable.ic_person_placeholder).circleCrop()
                    .into(backProfileImage);
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
        }
    }

    // --- ENHANCED LOGIC: Backfill with previous entries when today is empty ---
    private void updateTransactionTable(List<TransactionModel> transactions) {
        if (binding.transactionTable == null || binding.transactionCount == null || binding.emptyStateView == null)
            return;

        binding.transactionTable.removeAllViews();

        int todayCount = (transactions != null) ? transactions.size() : 0;

        if (todayCount == 0 && (cachedRecentTransactions == null || cachedRecentTransactions.isEmpty())) {
            // No today entries AND no previous entries at all
            binding.transactionCount.setText("TODAY (0)");
            binding.emptyStateView.setVisibility(View.VISIBLE);
            binding.transactionTable.setVisibility(View.VISIBLE);
            if (binding.btnViewAll != null) {
                binding.btnViewAll.setVisibility(View.GONE);
            }
        } else if (todayCount >= 10) {
            // Enough today entries — show only today's, no backfill
            binding.transactionCount.setText("TODAY (" + todayCount + ")");
            binding.emptyStateView.setVisibility(View.GONE);
            binding.transactionTable.setVisibility(View.VISIBLE);
            if (binding.btnViewAll != null) {
                binding.btnViewAll.setVisibility(View.VISIBLE);
            }

            for (TransactionModel t : transactions)
                addTransactionRow(t, binding.transactionTable);
        } else {
            // Backfill: show today's entries + fill remaining slots with previous entries
            binding.emptyStateView.setVisibility(View.GONE);
            binding.transactionTable.setVisibility(View.VISIBLE);
            if (binding.btnViewAll != null) {
                binding.btnViewAll.setVisibility(View.VISIBLE);
            }

            if (todayCount > 0) {
                binding.transactionCount.setText("TODAY (" + todayCount + ")");
                for (TransactionModel t : transactions)
                    addTransactionRow(t, binding.transactionTable);
            } else {
                binding.transactionCount.setText("RECENT ENTRIES");
            }

            // Fill remaining slots with previous entries (up to 10 - todayCount)
            int slotsRemaining = 10 - todayCount;
            if (cachedRecentTransactions != null && !cachedRecentTransactions.isEmpty()) {
                // Add a subtle divider between today and previous if today has entries
                if (todayCount > 0) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (int) (1 * getResources().getDisplayMetrics().density)));
                    TypedValue tv = new TypedValue();
                    getTheme().resolveAttribute(R.attr.chk_dividerHorizontal, tv, true);
                    divider.setBackgroundResource(tv.resourceId);
                    binding.transactionTable.addView(divider);
                }

                int count = Math.min(slotsRemaining, cachedRecentTransactions.size());
                for (int i = 0; i < count; i++) {
                    addPreviousTransactionRow(cachedRecentTransactions.get(i), binding.transactionTable);
                }
            }
        }
    }

    private void updateInsights(List<TransactionModel> transactions) {
        if (insightsCardInclude == null || insightEmoji == null || insightTitle == null) return;
        
        // Cancel any pending animations
        insightHandler.removeCallbacksAndMessages(null);

        // Generate insights using the local engine
        currentInsights = com.phynix.artham.utils.InsightsEngine.generate(transactions);
        currentInsightIndex = 0;
        
        if (currentInsights.isEmpty()) {
            insightsCardInclude.setVisibility(View.GONE);
            return;
        }
        
        insightsCardInclude.setVisibility(View.VISIBLE);
        setupInsightDots();
        displayCurrentInsight(false); // First one without crossfade
        updateInsightDots(); // Highlight first dot initially

        // Set up the carousel runnable (cycles every 5 seconds)
        if (currentInsights.size() > 1) {
            insightRunnable = new Runnable() {
                @Override
                public void run() {
                    currentInsightIndex = (currentInsightIndex + 1) % currentInsights.size();
                    displayCurrentInsight(true);
                    updateInsightDots();
                    insightHandler.postDelayed(this, 5000);
                }
            };
            insightHandler.postDelayed(insightRunnable, 5000);
            
            // Allow manual click navigation: left 50% for previous, right 50% for next insight
            final float[] lastTouchX = new float[1];
            insightsCardInclude.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchX[0] = event.getX();
                }
                return false; // Return false so the click listener still gets called
            });
            insightsCardInclude.setOnClickListener(v -> {
                // Cancel pending auto-advance
                insightHandler.removeCallbacks(insightRunnable);
                
                float clickX = lastTouchX[0];
                int width = v.getWidth();
                
                if (clickX < width / 2.0f) {
                    // Left 50% clicked -> Previous insight
                    currentInsightIndex = (currentInsightIndex - 1 + currentInsights.size()) % currentInsights.size();
                } else {
                    // Right 50% clicked -> Next insight
                    currentInsightIndex = (currentInsightIndex + 1) % currentInsights.size();
                }
                
                displayCurrentInsight(true);
                updateInsightDots();
                
                // Restart timer
                insightHandler.postDelayed(insightRunnable, 5000);
            });
        } else {
            insightsCardInclude.setOnTouchListener(null);
            insightsCardInclude.setOnClickListener(null);
            insightsCardInclude.setClickable(false);
        }
    }

    private void setupInsightDots() {
        if (insightDots == null) return;
        insightDots.removeAllViews();
        
        if (currentInsights.size() <= 1) return; // No dots needed for single insight
        
        int dotSize = (int) (6 * getResources().getDisplayMetrics().density);
        int margin = (int) (3 * getResources().getDisplayMetrics().density);
        
        for (int i = 0; i < currentInsights.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.bg_dot_indicator);
            // Default inactive state
            dot.setAlpha(0.3f);
            insightDots.addView(dot);
        }
    }

    private void updateInsightDots() {
        if (insightDots == null || insightDots.getChildCount() == 0) return;
        
        for (int i = 0; i < insightDots.getChildCount(); i++) {
            View dot = insightDots.getChildAt(i);
            if (i == currentInsightIndex) {
                dot.animate().alpha(1.0f).setDuration(200).start();
            } else {
                dot.animate().alpha(0.3f).setDuration(200).start();
            }
        }
    }

    private void displayCurrentInsight(boolean animate) {
        if (currentInsights == null || currentInsights.isEmpty() || insightEmoji == null) return;
        
        com.phynix.artham.utils.InsightsEngine.Insight insight = currentInsights.get(currentInsightIndex);
        
        if (animate && insightContentFrame != null) {
            // Crossfade animation
            insightContentFrame.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        insightEmoji.setText(insight.emoji);
                        insightTitle.setText(insight.title);
                        insightMessage.setText(insight.message);
                        insightContentFrame.animate().alpha(1f).setDuration(250).start();
                    })
                    .start();
        } else {
            insightEmoji.setText(insight.emoji);
            insightTitle.setText(insight.title);
            insightMessage.setText(insight.message);
            if (insightContentFrame != null) {
                insightContentFrame.setAlpha(1f);
            }
        }
    }

    private void addTransactionRow(TransactionModel transaction, ViewGroup tableLayout) {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_transaction_report_row, tableLayout, false);
        TextView rowRemark = rowView.findViewById(R.id.rowRemark);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        // Show remark truncated to ~15 characters in the first column
        String remark = transaction.getRemark();
        if (remark != null && !remark.isEmpty()) {
            if (remark.length() > 15) remark = remark.substring(0, 15) + "…";
            rowRemark.setText(remark);
        } else {
            rowRemark.setText(transaction.getTransactionCategory());
        }

        // Show payment mode in the second column
        String mode = transaction.getPaymentMode();
        rowMode.setText(mode != null && !mode.isEmpty() ? mode : "-");

        rowIn.setGravity(Gravity.CENTER);
        rowOut.setGravity(Gravity.CENTER);

        if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(transaction.getType())) {
            AmountFormatter.setAdaptiveAmount(rowIn, transaction.getAmount(), 13f, 9f);
            rowIn.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_incomeColor));
            rowOut.setText("-");
        } else {
            rowIn.setText("-");
            AmountFormatter.setAdaptiveAmount(rowOut, transaction.getAmount(), 13f, 9f);
            rowOut.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_expenseColor));
        }
        rowView.setOnClickListener(v -> openTransactionDetail(transaction));
        tableLayout.addView(rowView);
    }

    /**
     * Adds a previous (non-today) transaction row with reduced opacity to visually
     * distinguish it from today's entries.
     */
    private void addPreviousTransactionRow(TransactionModel transaction, ViewGroup tableLayout) {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_transaction_report_row, tableLayout, false);
        TextView rowRemark = rowView.findViewById(R.id.rowRemark);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        // Show remark truncated to ~15 characters in the first column
        String remark = transaction.getRemark();
        if (remark != null && !remark.isEmpty()) {
            if (remark.length() > 15) remark = remark.substring(0, 15) + "…";
            rowRemark.setText(remark);
        } else {
            rowRemark.setText(transaction.getTransactionCategory());
        }

        // Show date as mode for previous entries so user knows when it was
        String dateStr = DateTimeUtils.formatDate(transaction.getTimestamp(), "dd MMM");
        rowMode.setText(dateStr);

        rowIn.setGravity(Gravity.CENTER);
        rowOut.setGravity(Gravity.CENTER);

        if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(transaction.getType())) {
            AmountFormatter.setAdaptiveAmount(rowIn, transaction.getAmount(), 13f, 9f);
            rowIn.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_incomeColor));
            rowOut.setText("-");
        } else {
            rowIn.setText("-");
            AmountFormatter.setAdaptiveAmount(rowOut, transaction.getAmount(), 13f, 9f);
            rowOut.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_expenseColor));
        }

        // Dim the row to visually distinguish from today's entries
        rowView.setAlpha(0.5f);
        rowView.setOnClickListener(v -> openTransactionDetail(transaction));
        tableLayout.addView(rowView);
    }

    private void setupBottomNavigation() {
        View navRoot = binding.bottomNavCard.getRoot();
        View pill = navRoot.findViewById(R.id.slidingPillIndicator);
        View targetContainer = NavPillAnimator.getPillContainerForTab(navRoot, NavPillAnimator.TAB_HOME);

        // Determine previous tab for slide animation
        int previousTab = getIntent().getIntExtra(NavPillAnimator.EXTRA_PREVIOUS_TAB, -1);
        navRoot.findViewById(R.id.btnHome).setSelected(true);

        if (previousTab >= 0 && previousTab != NavPillAnimator.TAB_HOME) {
            View fromContainer = NavPillAnimator.getPillContainerForTab(navRoot, previousTab);
            NavPillAnimator.slideFromTo(pill, fromContainer, targetContainer);
        } else {
            NavPillAnimator.positionAt(pill, targetContainer);
        }

        navRoot.findViewById(R.id.btnTransactions).setOnClickListener(v -> {
            String idToUse = (currentCashbookId != null) ? currentCashbookId : viewModel.getCurrentCashbookId();
            if (idToUse != null) {
                Intent intent = new Intent(this, TransactionActivity.class);
                intent.putExtra(Constants.EXTRA_CASHBOOK_ID, idToUse);
                intent.putExtra(NavPillAnimator.EXTRA_PREVIOUS_TAB, NavPillAnimator.TAB_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            } else {
                showSnackbar("Please select a cashbook first");
            }
        });
        navRoot.findViewById(R.id.btnCashbookSwitch)
                .setOnClickListener(v -> openCashbookSwitcher());
        navRoot.findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.putExtra(NavPillAnimator.EXTRA_PREVIOUS_TAB, NavPillAnimator.TAB_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }

    private void setupClickListeners() {
        // Removed userBox click listener

        // Setup original buttons utilizing ViewBinding's include handling
        if (binding.originalButtons != null) {
            binding.originalButtons.btnCashIn
                    .setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_IN));
            binding.originalButtons.btnCashOut
                    .setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_OUT));
        }

        // Setup sticky buttons utilizing ViewBinding's include handling
        if (binding.stickyButtons != null) {
            binding.stickyButtons.btnCashIn
                    .setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_IN));
            binding.stickyButtons.btnCashOut
                    .setOnClickListener(v -> openCashInOutActivity(Constants.TRANSACTION_TYPE_OUT));
        }

        if (binding.dailySummaryInclude != null) {
            binding.dailySummaryInclude.getRoot().setOnClickListener(v -> {
                if (currentCashbookId != null) {
                    Intent intent = new Intent(HomeActivity.this, DailySummaryActivity.class);
                    intent.putExtra(Constants.EXTRA_CASHBOOK_ID, currentCashbookId);
                    startActivity(intent);
                }
            });
        }

        if (binding.btnViewAll != null) {
            binding.btnViewAll.setOnClickListener(v -> navigateToTransactions());
        }
    }

    private void navigateToTransactions() {
        String idToUse = (currentCashbookId != null) ? currentCashbookId : viewModel.getCurrentCashbookId();
        if (idToUse != null) {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra(Constants.EXTRA_CASHBOOK_ID, idToUse);
            intent.putExtra(NavPillAnimator.EXTRA_PREVIOUS_TAB, NavPillAnimator.TAB_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else {
            showSnackbar("Please select a cashbook first");
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

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        PERMISSION_REQUEST_CODE_NOTIFICATIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Shows the Google Play In-App Review prompt if the user has:
     * 1. Made at least 5 transactions (engaged user)
     * 2. Not been prompted in the last 30 days
     * This avoids annoying users while maximizing genuine Play Store reviews.
     */
    private void maybeShowInAppReview() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastReviewPrompt = prefs.getLong("last_review_prompt_time", 0);
        int appOpenCount = prefs.getInt("app_open_count", 0);

        // Increment app open count
        appOpenCount++;
        prefs.edit().putInt("app_open_count", appOpenCount).apply();

        // Only show review after 5+ app opens and not within last 30 days
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        if (appOpenCount < 5 || (System.currentTimeMillis() - lastReviewPrompt) < thirtyDaysMs) {
            return;
        }

        try {
            ReviewManager reviewManager = ReviewManagerFactory.create(this);
            reviewManager.requestReviewFlow().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    ReviewInfo reviewInfo = task.getResult();
                    reviewManager.launchReviewFlow(this, reviewInfo).addOnCompleteListener(flow -> {
                        // Save the timestamp regardless of whether user actually reviewed
                        prefs.edit().putLong("last_review_prompt_time", System.currentTimeMillis()).apply();
                        Log.d(TAG, "In-App Review flow completed");
                    });
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "In-App Review failed", e);
        }
    }

    private String formatCurrency(double amount) {
        return (currencyFormat == null) ? "₹" + amount : currencyFormat.format(amount);
    }

    private void showSnackbar(String message) {
        View anchor = (binding.bottomNavCard != null) ? binding.bottomNavCard.getRoot() : null;
        SnackbarHelper.show(this, message, anchor);
    }

    private void signOutUser() {
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
        SessionCache.getInstance().clear();
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, SignInActivity.class));
        finish();
    }

    private void refreshWidget() {
        try {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);

            // Refresh Cash In/Out Widget
            ComponentName widgetComponent = new ComponentName(this,
                    com.phynix.artham.widget.CashInOutWidgetProvider.class);
            int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
            if (widgetIds != null && widgetIds.length > 0) {
                Intent updateIntent = new Intent(this,
                        com.phynix.artham.widget.CashInOutWidgetProvider.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
                sendBroadcast(updateIntent);
            }

            // Refresh Cashbook List Widget
            ComponentName listWidgetComponent = new ComponentName(this,
                    com.phynix.artham.widget.CashbookListWidgetProvider.class);
            int[] listWidgetIds = widgetManager.getAppWidgetIds(listWidgetComponent);
            if (listWidgetIds != null && listWidgetIds.length > 0) {
                Intent listUpdateIntent = new Intent(this,
                        com.phynix.artham.widget.CashbookListWidgetProvider.class);
                listUpdateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                listUpdateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, listWidgetIds);
                sendBroadcast(listUpdateIntent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Widget refresh failed", e);
        }
    }


    // ═══════════════════════════════════════════════════════════
    //  ONBOARDING / TUTORIAL
    // ═══════════════════════════════════════════════════════════

    private boolean onboardingShownThisSession = false;

    private void checkAndShowOnboarding() {
        if (onboardingShownThisSession) return;
        onboardingShownThisSession = true;

        OnboardingManager mgr = OnboardingManager.getInstance(this);

        if (mgr.isFirstLaunch()) {
            // Show welcome dialog first, then home tooltips
            showWelcomeDialog();
        } else if (!mgr.isPageTutorialCompleted(OnboardingManager.PAGE_HOME)) {
            // Welcome already shown (maybe skipped), but home tooltips not done
            new Handler(Looper.getMainLooper()).postDelayed(this::startHomeTooltips, 600);
        }
    }

    private void showWelcomeDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_onboarding_welcome);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        DialogUtils.applyBlurEffect(dialog, this);

        // "Let's Get Started" button
        View btnStart = dialog.findViewById(R.id.btnGetStarted);
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> {
                OnboardingManager.getInstance(this).markOnboardingCompleted();
                dialog.dismiss();
                // Start home page tooltips after a short delay
                new Handler(Looper.getMainLooper()).postDelayed(this::startHomeTooltips, 500);
            });
        }

        // "Skip Tutorial" link
        View btnSkip = dialog.findViewById(R.id.btnSkipWelcome);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                OnboardingManager mgr = OnboardingManager.getInstance(this);
                mgr.markOnboardingCompleted();
                mgr.markPageTutorialCompleted(OnboardingManager.PAGE_HOME);
                mgr.markPageTutorialCompleted(OnboardingManager.PAGE_TRANSACTIONS);
                mgr.markPageTutorialCompleted(OnboardingManager.PAGE_SETTINGS);
                mgr.markPageTutorialCompleted(OnboardingManager.PAGE_CASH_IN_OUT);
                mgr.markPageTutorialCompleted(OnboardingManager.PAGE_CASHBOOK_SWITCH);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void startHomeTooltips() {
        if (isDestroyed() || isFinishing() || binding == null) return;

        OnboardingOverlay.builder(this)
                .addStep(R.id.insightsCardInclude,
                        "Smart Insights",
                        "Get AI-driven tips and daily summaries of your spending habits right here.")
                .addStep(R.id.balanceCardView,
                        "Balance Card",
                        "Your total balance at a glance. Tap the card to flip it and see your profile & social links.")
                .addStep(R.id.btnCashIn,
                        "Cash In",
                        "Tap here to record income — salary, freelance payments, gifts, and more.")
                .addStep(R.id.btnCashOut,
                        "Cash Out",
                        "Tap here to record expenses — food, travel, bills, shopping, etc.")
                .addStep(R.id.dailySummaryInclude,
                        "Daily Summary",
                        "View a summary of your net cash flow for today. Tap the card to view historical daily summaries.")
                .addStep(R.id.transactionListCard,
                        "Today's Transactions",
                        "All entries for today appear here. See your daily cash flow at a glance.")
                .setOnCompleteListener(() ->
                        OnboardingManager.getInstance(this)
                                .markPageTutorialCompleted(OnboardingManager.PAGE_HOME))
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the skeleton timeout to prevent leaks
        skeletonTimeoutHandler.removeCallbacks(skeletonTimeoutRunnable);
        if (insightHandler != null) {
            insightHandler.removeCallbacksAndMessages(null);
        }

        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
        binding = null;
    }
}