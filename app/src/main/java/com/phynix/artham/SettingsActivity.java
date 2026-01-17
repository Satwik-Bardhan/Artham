package com.phynix.artham;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.phynix.artham.databinding.ActivitySettingsBinding;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.ErrorHandler;
import com.phynix.artham.utils.ThemeManager;
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

public class SettingsActivity extends AppCompatActivity {

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
        // Apply Theme BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Track the current theme to detect changes when returning
        originalTheme = ThemeManager.getTheme(this);

        // Init Firebase & State
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        if (currentUser == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            logoutUser();
            return;
        }

        userRef = mDatabase.child("users").child(currentUser.getUid());

        setupClickListeners();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if theme changed while we were away (e.g., in App Settings)
        String currentTheme = ThemeManager.getTheme(this);
        if (originalTheme != null && !originalTheme.equals(currentTheme)) {
            recreate(); // Reload activity to apply new theme colors
        }
    }

    private void setupClickListeners() {
        binding.backButton.setOnClickListener(v -> finish());

        // Primary Settings Listeners
        binding.primarySettingsLayout.profileSection.setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));
        binding.primarySettingsLayout.editButton.setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));

        // General Settings Listeners
        binding.generalSettingsLayout.helpSupport.setOnClickListener(v ->
                startActivity(new Intent(this, HelpSupportActivity.class)));

        binding.generalSettingsLayout.appSettings.setOnClickListener(v ->
                startActivity(new Intent(this, AppSettingsActivity.class)));

        binding.generalSettingsLayout.yourProfile.setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));

        binding.generalSettingsLayout.aboutCashFlow.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        // Account Actions - Logout Only (Delete removed as requested)
        binding.logoutSection.setOnClickListener(v -> showLogoutConfirmationDialog());
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.btnSettings.setSelected(true);

        binding.bottomNavigation.btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });

        binding.bottomNavigation.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });

        binding.bottomNavigation.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());
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
            startListeningForUserProfile();
            if (currentCashbookId != null) {
                startListeningForCashbookName(currentCashbookId);
            } else {
                binding.primarySettingsLayout.activeCashbookName.setText("No Active Cashbook");
            }

            // Simple & Private Location Badge
            binding.primarySettingsLayout.dataLocation.setText("Google Cloud • Private Storage");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
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
                if (!dataSnapshot.exists()) return;

                Users userProfile = dataSnapshot.getValue(Users.class);

                // 1. Update User Name
                if (userProfile != null && !TextUtils.isEmpty(userProfile.getUserName())) {
                    binding.primarySettingsLayout.userName.setText(userProfile.getUserName());
                } else if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                    binding.primarySettingsLayout.userName.setText(currentUser.getDisplayName());
                } else {
                    binding.primarySettingsLayout.userName.setText("Artham User");
                }

                // 2. Update Profile Picture
                if (userProfile != null && userProfile.getProfile() != null && !userProfile.getProfile().isEmpty()) {
                    Glide.with(SettingsActivity.this)
                            .load(userProfile.getProfile())
                            .placeholder(R.drawable.ic_person_placeholder)
                            .error(R.drawable.ic_person_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .circleCrop()
                            .into(binding.primarySettingsLayout.profileImg);
                } else {
                    binding.primarySettingsLayout.profileImg.setImageResource(R.drawable.ic_person_placeholder);
                }

                // 3. Update UID & Date
                binding.primarySettingsLayout.uidText.setText("UID: " + currentUser.getUid().substring(0, 8) + "...");

                if (currentUser.getMetadata() != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
                    String creationDate = sdf.format(new Date(currentUser.getMetadata().getCreationTimestamp()));
                    binding.primarySettingsLayout.createdDate.setText("Created on " + creationDate);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                ErrorHandler.handleFirebaseError(SettingsActivity.this, databaseError);
            }
        };

        userRef.addValueEventListener(userProfileListener);
    }

    private void startListeningForCashbookName(String cashbookId) {
        if (userRef == null) return;

        if (cashbookNameListener != null) {
            userRef.child("cashbooks").child(cashbookId).removeEventListener(cashbookNameListener);
        }

        cashbookNameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                CashbookModel cashbook = dataSnapshot.getValue(CashbookModel.class);
                if (cashbook != null) {
                    binding.primarySettingsLayout.activeCashbookName.setText(cashbook.getName());
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
        mAuth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SigninActivity.class);
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
        if (cashbookNameListener != null && currentCashbookId != null) {
            userRef.child("cashbooks").child(currentCashbookId).removeEventListener(cashbookNameListener);
        }
    }

    private void saveActiveCashbookId(String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUser.getUid(), cashbookId).apply();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}