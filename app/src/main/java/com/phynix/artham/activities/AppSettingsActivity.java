package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.utils.FontManager;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.utils.OnboardingManager;

// [FIX] Import the CategoryActivity from its new sub-package


public class AppSettingsActivity extends BaseActivity {

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String PREFS_APP = "AppPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";
    private static final String KEY_HAPTIC = "haptic_feedback_enabled";
    private static final String KEY_SETTINGS_SHOW_SUMMARY = "settings_show_summary";
    private static final String KEY_SETTINGS_SHOW_PIE_CHART = "settings_show_pie_chart";

    // UI Elements
    private SwitchMaterial calculatorSwitch, hapticSwitch, summarySwitch, pieChartSwitch, monthlySummarySwitch;
    private LinearLayout dataBackupLayout, languageLayout, themeLayout, fontLayout, manageCategoriesLayout, manageCashbookCategoriesLayout, replayTutorialLayout;
    private TextView currentLanguageTextView, currentThemeTextView, currentFontTextView;

    // To track theme/font changes upon return
    private String originalTheme;
    private String originalFont;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // [IMPORTANT] Apply Theme BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Store original theme to check for changes later in onResume
        originalTheme = ThemeManager.getTheme(this);
        originalFont = FontManager.getFont(this);

        initializeUI();
        loadSettings();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if theme changed while we were in ThemeSelectionActivity
        String currentTheme = ThemeManager.getTheme(this);
        String currentFont = FontManager.getFont(this);
        if (!originalTheme.equals(currentTheme) || !originalFont.equals(currentFont)) {
            // Theme or font changed, recreate this activity to apply immediately
            recreate();
        } else {
            updateThemeLabel();
            updateFontLabel();
        }
    }

    private void initializeUI() {
        dataBackupLayout = findViewById(R.id.dataBackupLayout);
        calculatorSwitch = findViewById(R.id.calculatorSwitch);
        hapticSwitch = findViewById(R.id.hapticSwitch);
        summarySwitch = findViewById(R.id.summarySwitch);
        pieChartSwitch = findViewById(R.id.pieChartSwitch);
        monthlySummarySwitch = findViewById(R.id.monthlySummarySwitch);
        languageLayout = findViewById(R.id.languageLayout);
        currentLanguageTextView = findViewById(R.id.currentLanguage);

        themeLayout = findViewById(R.id.themeLayout);
        currentThemeTextView = findViewById(R.id.currentTheme);

        fontLayout = findViewById(R.id.fontLayout);
        currentFontTextView = findViewById(R.id.currentFont);

        // Bind the Category Management Layout
        manageCategoriesLayout = findViewById(R.id.manageCategoriesLayout);
        manageCashbookCategoriesLayout = findViewById(R.id.manageCashbookCategoriesLayout);

        // Bind the Replay Tutorial Layout
        replayTutorialLayout = findViewById(R.id.replayTutorialLayout);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (calculatorSwitch != null) {
            calculatorSwitch.setChecked(prefs.getBoolean(KEY_CALCULATOR, true));
        }
        if (hapticSwitch != null) {
            hapticSwitch.setChecked(prefs.getBoolean(KEY_HAPTIC, true));
        }

        // Load transaction view settings from AppPrefs
        SharedPreferences appPrefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (summarySwitch != null) {
            summarySwitch.setChecked(appPrefs.getBoolean(KEY_SETTINGS_SHOW_SUMMARY, true));
        }
        if (pieChartSwitch != null) {
            pieChartSwitch.setChecked(appPrefs.getBoolean(KEY_SETTINGS_SHOW_PIE_CHART, true));
        }

        updateThemeLabel();
        updateFontLabel();
    }

    private void updateThemeLabel() {
        if (currentThemeTextView != null) {
            String currentTheme = ThemeManager.getTheme(this);
            String displayTheme;

            switch (currentTheme) {
                case ThemeManager.THEME_SYSTEM:
                    displayTheme = "System Default";
                    break;
                case ThemeManager.THEME_LIGHT:
                    displayTheme = "Light";
                    break;
                case ThemeManager.THEME_DARK:
                    displayTheme = "Dark";
                    break;
                case ThemeManager.THEME_PURPLE:
                    displayTheme = "Purple";
                    break;
                case ThemeManager.THEME_EMERALD:
                    displayTheme = "Emerald";
                    break;
                case ThemeManager.THEME_ROSE:
                    displayTheme = "Rose Gold";
                    break;
                case ThemeManager.THEME_RANDOM:
                    displayTheme = "Random";
                    break;
                default:
                    displayTheme = "System Default";
                    break;
            }

            currentThemeTextView.setText(displayTheme);
        }
    }

    private void updateFontLabel() {
        if (currentFontTextView != null) {
            String currentFont = FontManager.getFont(this);
            currentFontTextView.setText(FontManager.getDisplayName(currentFont));
        }
    }

    private void setupClickListeners() {
        View backBtn = findViewById(R.id.back_button);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        if (dataBackupLayout != null) {
            dataBackupLayout.setOnClickListener(v -> showBackupStatusDialog());
        }

        if (calculatorSwitch != null) {
            calculatorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                saveBooleanSetting(KEY_CALCULATOR, isChecked);
                String status = isChecked ? "Calculator enabled" : "Calculator disabled";
                Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
            });
        }

        if (hapticSwitch != null) {
            hapticSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                saveBooleanSetting(KEY_HAPTIC, isChecked);
                String status = isChecked ? "Haptic feedback enabled" : "Haptic feedback disabled";
                Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
            });
        }

        // Transaction View toggles — write to AppPrefs so TransactionActivity picks them up
        if (summarySwitch != null) {
            summarySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                        .putBoolean(KEY_SETTINGS_SHOW_SUMMARY, isChecked).apply();
                Toast.makeText(this,
                        isChecked ? "Summary cards enabled" : "Summary cards hidden",
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (pieChartSwitch != null) {
            pieChartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                        .putBoolean(KEY_SETTINGS_SHOW_PIE_CHART, isChecked).apply();
                Toast.makeText(this,
                        isChecked ? "Pie chart enabled" : "Pie chart hidden",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // Monthly Summary Notification toggle
        if (monthlySummarySwitch != null) {
            monthlySummarySwitch.setChecked(com.phynix.artham.utils.MonthlySummaryReceiver.isEnabled(this));
            monthlySummarySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                com.phynix.artham.utils.MonthlySummaryReceiver.setEnabled(this, isChecked);
                Toast.makeText(this,
                        isChecked ? "Monthly summary enabled" : "Monthly summary disabled",
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (languageLayout != null) {
            languageLayout.setOnClickListener(v ->
                    Toast.makeText(this, "Language selection coming soon!", Toast.LENGTH_SHORT).show());
        }

        // Open ThemeSelectionActivity
        if (themeLayout != null) {
            themeLayout.setOnClickListener(v -> {
                Intent intent = new Intent(AppSettingsActivity.this, ThemeSelectionActivity.class);
                startActivity(intent);
            });
        }

        // Open FontSelectionActivity
        if (fontLayout != null) {
            fontLayout.setOnClickListener(v -> {
                Intent intent = new Intent(AppSettingsActivity.this, FontSelectionActivity.class);
                startActivity(intent);
            });
        }

        if (manageCategoriesLayout != null) {
            manageCategoriesLayout.setOnClickListener(v -> {
                Intent intent = new Intent(AppSettingsActivity.this, CategoryActivity.class);

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    String currentCashbookId = prefs.getString("active_cashbook_id_" + user.getUid(), "");
                    if (!currentCashbookId.isEmpty()) {
                        intent.putExtra("cashbook_id", currentCashbookId);
                    }
                }

                startActivity(intent);
            });
        }

        if (manageCashbookCategoriesLayout != null) {
            manageCashbookCategoriesLayout.setOnClickListener(v -> {
                Intent intent = new Intent(AppSettingsActivity.this, ManageCashbookLabelsActivity.class);
                startActivity(intent);
            });
        }

        // Replay Tutorial button
        if (replayTutorialLayout != null) {
            replayTutorialLayout.setOnClickListener(v -> {
                OnboardingManager.getInstance(this).resetOnboarding();
                Toast.makeText(this, "Tutorial reset! You'll see the walkthrough on your next visit.", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void showBackupStatusDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = (user != null && user.getEmail() != null) ? user.getEmail() : "Unknown Account";

        new AlertDialog.Builder(this)
                .setTitle("Cloud Backup")
                .setMessage("Account: " + email + "\nStatus: \u2705 Active")
                .setPositiveButton("OK", null)
                .show();
    }

    private void saveBooleanSetting(String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(key, value);
        editor.apply();
    }
}