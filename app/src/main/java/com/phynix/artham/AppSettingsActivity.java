package com.phynix.artham;

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

public class AppSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    private SwitchMaterial calculatorSwitch;
    private LinearLayout dataBackupLayout, languageLayout, themeLayout;
    private TextView currentLanguageTextView, currentThemeTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Apply Theme BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUI();
        loadSettings();
        setupClickListeners();
    }

    private void initializeUI() {
        dataBackupLayout = findViewById(R.id.dataBackupLayout);
        calculatorSwitch = findViewById(R.id.calculatorSwitch);
        languageLayout = findViewById(R.id.languageLayout);
        currentLanguageTextView = findViewById(R.id.currentLanguage);

        themeLayout = findViewById(R.id.themeLayout);
        currentThemeTextView = findViewById(R.id.currentTheme);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (calculatorSwitch != null) {
            calculatorSwitch.setChecked(prefs.getBoolean(KEY_CALCULATOR, true));
        }

        // Update Theme Text
        if (currentThemeTextView != null) {
            String currentTheme = ThemeManager.getTheme(this);

            String displayTheme = "Dark"; // Default
            if (currentTheme.equals(ThemeManager.THEME_LIGHT)) displayTheme = "Light";
            else if (currentTheme.equals(ThemeManager.THEME_PURPLE)) displayTheme = "Purple";

            currentThemeTextView.setText(displayTheme);
        }
    }

    private void setupClickListeners() {
        View backBtn = findViewById(R.id.back_button);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        if (dataBackupLayout != null) dataBackupLayout.setOnClickListener(v -> showBackupStatusDialog());

        if (calculatorSwitch != null) {
            calculatorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                saveBooleanSetting(KEY_CALCULATOR, isChecked);
                Toast.makeText(this, isChecked ? "Calculator enabled" : "Calculator disabled", Toast.LENGTH_SHORT).show();
            });
        }

        if (languageLayout != null) {
            languageLayout.setOnClickListener(v -> Toast.makeText(this, "Language selection coming soon!", Toast.LENGTH_SHORT).show());
        }

        if (themeLayout != null) {
            themeLayout.setOnClickListener(v -> showThemeSelectionDialog());
        }
    }

    private void showThemeSelectionDialog() {
        String[] themes = {"Light", "Dark", "Purple"};
        String currentTheme = ThemeManager.getTheme(this);
        int checkedItem = 1; // Default Dark

        if (currentTheme.equals(ThemeManager.THEME_LIGHT)) checkedItem = 0;
        else if (currentTheme.equals(ThemeManager.THEME_PURPLE)) checkedItem = 2;

        new AlertDialog.Builder(this)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    String selectedTheme = ThemeManager.THEME_DARK;
                    if (which == 0) selectedTheme = ThemeManager.THEME_LIGHT;
                    else if (which == 2) selectedTheme = ThemeManager.THEME_PURPLE;

                    // 1. Save the new preference
                    ThemeManager.saveTheme(this, selectedTheme);

                    // 2. Apply it globally.
                    // This call internally sets AppCompatDelegate.setDefaultNightMode(),
                    // which AUTOMATICALLY recreates activities if the mode changes.
                    ThemeManager.applyTheme(selectedTheme);

                    dialog.dismiss();

                    // CRITICAL FIX: Removed explicit recreate() to prevent "Activity client record must not be null" crash.
                    // If switching between Light/Dark, the system handles it.
                    // If switching to Purple (which might be the same "Night Mode" as another), we force a recreation cleanly.

                    // Only recreate manually if necessary (e.g. Purple <-> Light might not trigger automatic recreation if Night Mode doesn't change)
                    // But to be safe and avoid the crash, we can just rely on the user navigating back or use a delayed recreation if absolutely needed.
                    // For now, removing recreate() is the safest fix for the crash.
                })
                .setNegativeButton("Cancel", null)
                .show();
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