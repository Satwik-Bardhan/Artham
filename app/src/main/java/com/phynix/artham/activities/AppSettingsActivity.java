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
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.utils.ThemeManager;

// [FIX] Import the CategoryActivity from its new sub-package
import com.phynix.artham.activities.CategoryActivity;

public class AppSettingsActivity extends BaseActivity {

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String PREFS_APP = "AppPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";
    private static final String KEY_HAPTIC = "haptic_feedback_enabled";
    private static final String KEY_SETTINGS_SHOW_SUMMARY = "settings_show_summary";
    private static final String KEY_SETTINGS_SHOW_PIE_CHART = "settings_show_pie_chart";

    // UI Elements
    private SwitchMaterial calculatorSwitch, hapticSwitch, summarySwitch, pieChartSwitch;
    private LinearLayout dataBackupLayout, languageLayout, themeLayout, manageCategoriesLayout;
    private TextView currentLanguageTextView, currentThemeTextView;

    // To track theme changes upon return
    private String originalTheme;

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

        initializeUI();
        loadSettings();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if theme changed while we were in ThemeSelectionActivity
        String currentTheme = ThemeManager.getTheme(this);
        if (!originalTheme.equals(currentTheme)) {
            // Theme changed, recreate this activity to apply new colors immediately
            recreate();
        } else {
            updateThemeLabel();
        }
    }

    private void initializeUI() {
        dataBackupLayout = findViewById(R.id.dataBackupLayout);
        calculatorSwitch = findViewById(R.id.calculatorSwitch);
        hapticSwitch = findViewById(R.id.hapticSwitch);
        summarySwitch = findViewById(R.id.summarySwitch);
        pieChartSwitch = findViewById(R.id.pieChartSwitch);
        languageLayout = findViewById(R.id.languageLayout);
        currentLanguageTextView = findViewById(R.id.currentLanguage);

        themeLayout = findViewById(R.id.themeLayout);
        currentThemeTextView = findViewById(R.id.currentTheme);

        // Bind the Category Management Layout
        manageCategoriesLayout = findViewById(R.id.manageCategoriesLayout);
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
    }

    private void updateThemeLabel() {
        if (currentThemeTextView != null) {
            String currentTheme = ThemeManager.getTheme(this);
            String displayTheme = "Dark"; // Default

            if (ThemeManager.THEME_LIGHT.equals(currentTheme)) {
                displayTheme = "Light";
            } else if (ThemeManager.THEME_PURPLE.equals(currentTheme)) {
                displayTheme = "Purple";
            }

            currentThemeTextView.setText(displayTheme);
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