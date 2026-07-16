package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import com.phynix.artham.SignInActivity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.phynix.artham.databinding.ActivitySettingsBinding;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.ErrorHandler;
import com.phynix.artham.utils.SessionCache;

import com.phynix.artham.utils.NavPillAnimator;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.utils.OnboardingManager;
import com.phynix.artham.utils.OnboardingOverlay;
import com.phynix.artham.utils.UpdateChecker;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import com.phynix.artham.auth.AuthManager;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // ViewBinding
    private ActivitySettingsBinding binding;



    // Data
    private String currentCashbookId;

    // Theme Tracking
    private String originalTheme;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        originalTheme = ThemeManager.getTheme(this);

        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        if (!isLocal && !AuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            logoutUser();
            return;
        }

        // --- CRITICAL FIX: Robust Cashbook ID Resolution ---
        // 1. Try to get it from the intent
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        // 2. If the intent missed it (due to fast navigation), pull it instantly from local memory
        if (currentCashbookId == null || currentCashbookId.trim().isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            String uid = AuthManager.getUserId(this);
            currentCashbookId = prefs.getString("active_cashbook_id_" + uid, "");
        }



        setupClickListeners();
        setupBottomNavigation();

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (binding.versionText != null) {
                binding.versionText.setText("Version " + versionName);
            }
        } catch (Exception e) {
            if (binding.versionText != null) {
                binding.versionText.setText("Version 1.2");
            }
        }

        // Check for app updates
        checkForAppUpdate();
    }

    // ═══════════════════════════════════════════════════════════
    //  APP UPDATE CHECK
    // ═══════════════════════════════════════════════════════════

    private void checkForAppUpdate() {
        UpdateChecker checker = UpdateChecker.getInstance();

        // If we already know an update is available, show immediately
        if (checker.isUpdateAvailable()) {
            showUpdateIndicators();
        }

        // Always perform a fresh check (the API caches internally)
        checker.checkForUpdate(this, updateAvailable -> {
            if (updateAvailable && !isDestroyed() && !isFinishing()) {
                showUpdateIndicators();
            }
        });
    }

    private void showUpdateIndicators() {
        // Show the speech bubble popup above ARTHAM text
        if (binding.updateBubbleContainer != null) {
            binding.updateBubbleContainer.setVisibility(View.VISIBLE);
        }

        // Show the notification dot on the Settings nav icon
        View dot = binding.bottomNavigation.getRoot().findViewById(R.id.settingsUpdateDot);
        if (dot != null) {
            dot.setVisibility(View.VISIBLE);
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        String currentTheme = ThemeManager.getTheme(this);
        if (originalTheme != null && !originalTheme.equals(currentTheme)) {
            recreate();
            return;
        }
        // Reload profile data from SharedPreferences (e.g., after EditProfileActivity)
        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        if (isLocal) {
            populateProfileUI(null);
        } else {
            SessionCache cache = SessionCache.getInstance();
            if (cache.hasUserProfile()) {
                populateProfileUI(cache.getCachedUserProfile());
            } else {
                populateProfileUI(null);
            }
        }
    }

    private void setupClickListeners() {
        binding.backButton.setOnClickListener(v -> finish());

        if (binding.primarySettingsLayout != null) {
            binding.primarySettingsLayout.editButton.setOnClickListener(v ->
                    startActivity(new Intent(this, EditProfileActivity.class)));
        }

        if (binding.generalSettingsLayout != null) {
            binding.generalSettingsLayout.helpSupport.setOnClickListener(v ->
                    startActivity(new Intent(this, HelpSupportActivity.class)));
            binding.generalSettingsLayout.appSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, AppSettingsActivity.class)));
            binding.generalSettingsLayout.yourProfile.setOnClickListener(v ->
                    startActivity(new Intent(this, EditProfileActivity.class)));
            binding.generalSettingsLayout.aboutCashFlow.setOnClickListener(v ->
                    startActivity(new Intent(this, AboutActivity.class)));
        }

        if (binding.logoutSection != null) {
            binding.logoutSection.setOnClickListener(v -> showLogoutConfirmationDialog());
        }

        if (binding.brandTitle != null) {
            binding.brandTitle.setOnClickListener(v -> openPlayStore());
        }

        // Tapping the speech bubble also opens Play Store
        if (binding.updateBubbleContainer != null) {
            binding.updateBubbleContainer.setOnClickListener(v -> openPlayStore());
        }

        // Guest Mode: Show sign-in banner and handle sign-in button
        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        if (isLocal) {
            if (binding.guestSignInBanner != null) {
                binding.guestSignInBanner.setVisibility(View.VISIBLE);
            }
            if (binding.guestSignInButton != null) {
                binding.guestSignInButton.setOnClickListener(v -> {
                    // Navigate to SignInActivity for Google sign-in
                    // DataRepository.migrateLocalDataToFirebase() will run automatically
                    // in HomeViewModel after successful sign-in
                    Intent intent = new Intent(this, SignInActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }
        }
    }

    private void setupBottomNavigation() {
        View navRoot = binding.bottomNavigation.getRoot();
        View pill = navRoot.findViewById(R.id.slidingPillIndicator);
        View targetContainer = NavPillAnimator.getPillContainerForTab(navRoot, NavPillAnimator.TAB_SETTINGS);

        int previousTab = getIntent().getIntExtra(NavPillAnimator.EXTRA_PREVIOUS_TAB, -1);
        binding.bottomNavigation.btnSettings.setSelected(true);

        if (previousTab >= 0 && previousTab != NavPillAnimator.TAB_SETTINGS) {
            View fromContainer = NavPillAnimator.getPillContainerForTab(navRoot, previousTab);
            NavPillAnimator.slideFromTo(pill, fromContainer, targetContainer);
        } else {
            NavPillAnimator.positionAt(pill, targetContainer);
        }

        binding.bottomNavigation.btnHome.setOnClickListener(v -> navigateToActivity(HomeActivity.class, NavPillAnimator.TAB_SETTINGS));
        binding.bottomNavigation.btnTransactions.setOnClickListener(v -> navigateToActivity(TransactionActivity.class, NavPillAnimator.TAB_SETTINGS));
        binding.bottomNavigation.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());
    }

    // --- CRITICAL FIX: Unrestricted Navigation ---
    private void navigateToActivity(Class<?> targetActivityClass, int fromTab) {
        // We removed the block here! Even if data is still loading,
        // you can seamlessly click away to the Home page or Transactions without getting trapped.
        Intent intent = new Intent(this, targetActivityClass);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra(NavPillAnimator.EXTRA_PREVIOUS_TAB, fromTab);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
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
                currentCashbookId = newCashbookId;
                saveActiveCashbookId(currentCashbookId);
                showToast("Switched to: " + cashbookName);
                startListeningForCashbookName(currentCashbookId);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        if (isLocal) {
            populateProfileUI(null);
            hideSkeletonInstant();
            
            if (currentCashbookId != null && !currentCashbookId.isEmpty()) {
                DataRepository.getInstance(getApplication()).getCashbooks(cashbooks -> {
                    for (CashbookModel cb : cashbooks) {
                        if (cb.getCashbookId().equals(currentCashbookId)) {
                            if (binding.primarySettingsLayout != null) {
                                binding.primarySettingsLayout.activeCashbookName.setText(cb.getName());
                            }
                            break;
                        }
                    }
                }, null);
            } else if (binding.primarySettingsLayout != null) {
                binding.primarySettingsLayout.activeCashbookName.setText("No Active Cashbook");
            }
        } else if (AuthManager.isSignedIn(this)) {
            // Check session cache first — skip skeleton if we already have data
            SessionCache cache = SessionCache.getInstance();
            if (cache.hasUserProfile()) {
                // Instantly populate from cache — no flicker
                populateProfileUI(cache.getCachedUserProfile());
                hideSkeletonInstant();
            } else {
                showSkeleton();
            }

            // Always attach Firebase listener for live updates
            startListeningForUserProfile();

            if (currentCashbookId != null && !currentCashbookId.isEmpty()) {
                // Check cache for cashbook name
                String cachedName = cache.getCachedCashbookName(currentCashbookId);
                if (cachedName != null && binding.primarySettingsLayout != null) {
                    binding.primarySettingsLayout.activeCashbookName.setText(cachedName);
                }
                startListeningForCashbookName(currentCashbookId);
            } else if (binding.primarySettingsLayout != null) {
                binding.primarySettingsLayout.activeCashbookName.setText("No Active Cashbook");
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
    }

    private void showSkeleton() {
        if (binding.profileShimmerLayout != null && binding.profileContentLayout != null) {
            binding.profileShimmerLayout.setVisibility(View.VISIBLE);
            binding.profileShimmerLayout.startShimmer();

            binding.profileContentLayout.setVisibility(View.INVISIBLE);
        }
    }

    private void hideSkeleton() {
        if (binding.profileShimmerLayout != null && binding.profileContentLayout != null) {
            // Decreased to 600ms for a snappier feel.
            binding.profileShimmerLayout.postDelayed(() -> {

                // CRITICAL SAFETY CHECK: If you click away while it's loading,
                // this safely stops the animation without crashing or locking up the dead page.
                if (isDestroyed() || isFinishing()) return;

                hideSkeletonInstant();
            }, 600);
        }
    }

    /** Immediately hides skeleton without delay — used when cache data is available. */
    private void hideSkeletonInstant() {
        if (binding.profileShimmerLayout != null) {
            binding.profileShimmerLayout.stopShimmer();
            binding.profileShimmerLayout.setVisibility(View.GONE);
        }
        if (binding.profileContentLayout != null) {
            binding.profileContentLayout.setVisibility(View.VISIBLE);
        }

        // ── Onboarding: Show tooltips on first visit ──
        checkAndShowOnboarding();
    }

    private void startListeningForUserProfile() {
        String userId = AuthManager.getUserId(this);
        
        // Prefer user-edited values from SharedPreferences over AuthManager (Google) defaults
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String editedName = prefs.getString("user_display_name", "");
        String editedPhotoPath = prefs.getString("user_photo_path", "");
        
        String name = !editedName.isEmpty() ? editedName : AuthManager.getUserName(this);
        String email = AuthManager.getUserEmail(this);
        String photoUrl = (!editedPhotoPath.isEmpty()) ? editedPhotoPath : AuthManager.getUserPhotoUrl(this);
        
        Users profile = new Users(userId, name, email, null, photoUrl);
        SessionCache.getInstance().cacheUserProfile(profile);
        populateProfileUI(profile);
        hideSkeletonInstant();
    }

    /** Populates profile card UI from a Users object (either cached or fresh from Firebase). */
    @SuppressLint("SetTextI18n")
    private void populateProfileUI(Users userProfile) {
        if (binding.primarySettingsLayout == null) return;

        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedName = prefs.getString("user_display_name", "");
        String savedPhotoPath = prefs.getString("user_photo_path", "");

        if (isLocal) {
            String displayName = !savedName.isEmpty() ? savedName : "Local User";
            binding.primarySettingsLayout.userName.setText(displayName);

            // Load saved profile photo
            if (!savedPhotoPath.isEmpty()) {
                java.io.File photoFile = new java.io.File(savedPhotoPath);
                if (photoFile.exists() && !isDestroyed() && !isFinishing()) {
                    Glide.with(this)
                            .load(photoFile)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .error(R.drawable.ic_person_placeholder)
                            .circleCrop()
                            .into(binding.primarySettingsLayout.profileImg);
                } else {
                    binding.primarySettingsLayout.profileImg.setImageResource(R.drawable.ic_person_placeholder);
                }
            } else {
                binding.primarySettingsLayout.profileImg.setImageResource(R.drawable.ic_person_placeholder);
            }

            binding.primarySettingsLayout.uidText.setText("UID: LOCAL_USER");
            if (binding.primarySettingsLayout.copyUidButton != null) {
                binding.primarySettingsLayout.copyUidButton.setOnClickListener(v -> {
                    copyToClipboard("User ID", "LOCAL_USER");
                });
            }
            binding.primarySettingsLayout.createdDate.setText("Using local storage");
            return;
        }

        if (userProfile != null && !TextUtils.isEmpty(userProfile.getUserName())) {
            binding.primarySettingsLayout.userName.setText(userProfile.getUserName());
        } else {
            // Fallback: load saved name from SharedPreferences
            String nameToUse = !savedName.isEmpty() ? savedName : "Artham User";
            binding.primarySettingsLayout.userName.setText(nameToUse);
        }

        // Try profile photo (could be URL or local file path)
        boolean photoLoaded = false;
        if (userProfile != null && userProfile.getProfile() != null && !userProfile.getProfile().isEmpty()) {
            if (!isDestroyed() && !isFinishing()) {
                String profileSource = userProfile.getProfile();
                Object glideSource = profileSource.startsWith("/") 
                        ? new java.io.File(profileSource) : profileSource;
                Glide.with(this)
                        .load(glideSource)
                        .skipMemoryCache(true)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(binding.primarySettingsLayout.profileImg);
                photoLoaded = true;
            }
        }

        if (!photoLoaded) {
            // Fallback: load saved photo from internal storage
            if (!savedPhotoPath.isEmpty()) {
                java.io.File fallbackFile = new java.io.File(savedPhotoPath);
                if (fallbackFile.exists() && !isDestroyed() && !isFinishing()) {
                    Glide.with(this)
                            .load(fallbackFile)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .error(R.drawable.ic_person_placeholder)
                            .circleCrop()
                            .into(binding.primarySettingsLayout.profileImg);
                    photoLoaded = true;
                }
            }
        }

        if (!photoLoaded) {
            binding.primarySettingsLayout.profileImg.setImageResource(R.drawable.ic_person_placeholder);
        }

        String uidString = AuthManager.getUserId(this).toUpperCase();
        binding.primarySettingsLayout.uidText.setText("UID: " + uidString);

        if (binding.primarySettingsLayout.copyUidButton != null) {
            binding.primarySettingsLayout.copyUidButton.setOnClickListener(v -> {
                copyToClipboard("User ID", uidString);
            });
        }

        binding.primarySettingsLayout.createdDate.setVisibility(View.GONE);
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            android.widget.Toast.makeText(this, "Copied UID to clipboard", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void startListeningForCashbookName(String cashbookId) {
        if (cashbookId == null || cashbookId.isEmpty()) return;
        DataRepository.getInstance(getApplication()).getCashbooks(cashbooks -> {
            for (CashbookModel cb : cashbooks) {
                if (cb.getCashbookId().equals(cashbookId)) {
                    SessionCache.getInstance().cacheCashbookName(cashbookId, cb.getName());
                    if (binding.primarySettingsLayout != null) {
                        binding.primarySettingsLayout.activeCashbookName.setText(cb.getName());
                    }
                    break;
                }
            }
        }, null);
    }

    private void logoutUser() {
        removeFirebaseListeners();
        SessionCache.getInstance().clear();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("is_local_mode", false).apply();
        AuthManager.signOut(this);
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLogoutConfirmationDialog() {
        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
        String title = isLocal ? "Exit Guest Mode" : "Logout";
        String message = isLocal
                ? "Your local data will remain on this device. You can sign in with Google to backup and sync your data."
                : "Are you sure you want to log out?";
        String positiveText = isLocal ? "Exit" : "Logout";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText, (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void removeFirebaseListeners() {
    }

    private void saveActiveCashbookId(String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String uid = AuthManager.getUserId(this);
        prefs.edit().putString("active_cashbook_id_" + uid, cashbookId).apply();
    }

    // ═══════════════════════════════════════════════════════════
    //  ONBOARDING / TUTORIAL
    // ═══════════════════════════════════════════════════════════

    private boolean onboardingShownThisSession = false;

    private void checkAndShowOnboarding() {
        if (onboardingShownThisSession) return;
        OnboardingManager mgr = OnboardingManager.getInstance(this);
        if (mgr.isPageTutorialCompleted(OnboardingManager.PAGE_SETTINGS)) return;
        onboardingShownThisSession = true;

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;

            OnboardingOverlay.builder(this)
                    .addStep(R.id.primarySettingsLayout,
                            "Your Profile",
                            "Your profile info and active cashbook. Tap 'Edit' to update your details.")
                    .addStep(R.id.appSettings,
                            "App Settings",
                            "Customize themes, fonts, haptic feedback, categories, and more.")
                    .addStep(R.id.helpSupport,
                            "Help & Support",
                            "Find answers, contact support, or support the developer here.")
                    .setOnCompleteListener(() ->
                            OnboardingManager.getInstance(this)
                                    .markPageTutorialCompleted(OnboardingManager.PAGE_SETTINGS))
                    .start();
        }, 600);
    }

    private void openPlayStore() {
        String packageName = getPackageName();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
            startActivity(intent);
        }
    }
}