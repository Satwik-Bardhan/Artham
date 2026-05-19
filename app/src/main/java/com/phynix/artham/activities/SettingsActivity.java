package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import com.phynix.artham.SignInActivity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.ErrorHandler;
import com.phynix.artham.utils.SessionCache;

import com.phynix.artham.utils.NavPillAnimator;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.utils.OnboardingManager;
import com.phynix.artham.utils.OnboardingOverlay;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // ViewBinding
    private ActivitySettingsBinding binding;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;
    private DatabaseReference userRef;

    // Listeners
    private ValueEventListener userProfileListener;
    private ValueEventListener cashbookNameListener;

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

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        if (currentUser == null) {
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
            currentCashbookId = prefs.getString("active_cashbook_id_" + currentUser.getUid(), "");
        }

        userRef = mDatabase.child("users").child(currentUser.getUid());

        setupClickListeners();
        setupBottomNavigation();

    }



    @Override
    protected void onResume() {
        super.onResume();
        String currentTheme = ThemeManager.getTheme(this);
        if (originalTheme != null && !originalTheme.equals(currentTheme)) {
            recreate();
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
        if (currentUser != null) {
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
        if (userRef == null) return;

        if (userProfileListener != null) {
            userRef.removeEventListener(userProfileListener);
        }

        userProfileListener = new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    hideSkeleton();
                    return;
                }

                Users userProfile = dataSnapshot.getValue(Users.class);

                // Cache the profile for instant display on tab switches
                SessionCache.getInstance().cacheUserProfile(userProfile);

                populateProfileUI(userProfile);
                hideSkeleton();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                hideSkeleton();
                ErrorHandler.handleFirebaseError(SettingsActivity.this, databaseError);
            }
        };
        userRef.addValueEventListener(userProfileListener);
    }

    /** Populates profile card UI from a Users object (either cached or fresh from Firebase). */
    @SuppressLint("SetTextI18n")
    private void populateProfileUI(Users userProfile) {
        if (binding.primarySettingsLayout == null) return;

        if (userProfile != null && !TextUtils.isEmpty(userProfile.getUserName())) {
            binding.primarySettingsLayout.userName.setText(userProfile.getUserName());
        } else if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
            binding.primarySettingsLayout.userName.setText(currentUser.getDisplayName());
        } else {
            binding.primarySettingsLayout.userName.setText("Artham User");
        }

        if (userProfile != null && userProfile.getProfile() != null && !userProfile.getProfile().isEmpty()) {
            if (!isDestroyed() && !isFinishing()) {
                Glide.with(this)
                        .load(userProfile.getProfile())
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(binding.primarySettingsLayout.profileImg);
            }
        } else if (currentUser.getPhotoUrl() != null) {
            if (!isDestroyed() && !isFinishing()) {
                Glide.with(this)
                        .load(currentUser.getPhotoUrl())
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(binding.primarySettingsLayout.profileImg);
            }
        } else {
            binding.primarySettingsLayout.profileImg.setImageResource(R.drawable.ic_person_placeholder);
        }

        binding.primarySettingsLayout.uidText.setText("UID: " + currentUser.getUid().toUpperCase());

        if (currentUser.getMetadata() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
            String creationDate = sdf.format(new Date(currentUser.getMetadata().getCreationTimestamp()));
            binding.primarySettingsLayout.createdDate.setText("Created on " + creationDate);
        }
    }

    private void startListeningForCashbookName(String cashbookId) {
        if (userRef == null || cashbookId == null || cashbookId.isEmpty()) return;

        if (cashbookNameListener != null) {
            userRef.child("cashbooks").child(cashbookId).removeEventListener(cashbookNameListener);
        }

        cashbookNameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                CashbookModel cashbook = dataSnapshot.getValue(CashbookModel.class);
                if (cashbook != null && binding.primarySettingsLayout != null) {
                    binding.primarySettingsLayout.activeCashbookName.setText(cashbook.getName());
                    // Cache cashbook name for instant display on tab switches
                    SessionCache.getInstance().cacheCashbookName(cashbookId, cashbook.getName());
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                ErrorHandler.handleFirebaseError(SettingsActivity.this, databaseError);
            }
        };
        userRef.child("cashbooks").child(cashbookId).addValueEventListener(cashbookNameListener);
    }

    private void logoutUser() {
        SessionCache.getInstance().clear();
        mAuth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void removeFirebaseListeners() {
        if (userRef == null) return;

        if (userProfileListener != null) {
            userRef.removeEventListener(userProfileListener);
        }
        if (cashbookNameListener != null && currentCashbookId != null && !currentCashbookId.isEmpty()) {
            userRef.child("cashbooks").child(currentCashbookId).removeEventListener(cashbookNameListener);
        }
    }

    private void saveActiveCashbookId(String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUser.getUid(), cashbookId).apply();
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
}