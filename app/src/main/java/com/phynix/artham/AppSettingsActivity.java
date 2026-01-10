package com.phynix.artham;

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

public class AppSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    private SwitchMaterial calculatorSwitch;
    private LinearLayout dataBackupLayout, languageLayout, themeLayout; // Added themeLayout
    private TextView currentLanguageTextView, currentThemeTextView; // Added currentThemeTextView

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

        // Ensure you add this ID to your XML layout if it doesn't exist yet
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
            String currentTheme = ThemeManager.getCurrentTheme(this);
            String displayTheme = "Light"; // Default
            if (currentTheme.equals(ThemeManager.THEME_DARK)) displayTheme = "Dark";
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

        // Theme Click Listener
        if (themeLayout != null) {
            themeLayout.setOnClickListener(v -> showThemeSelectionDialog());
        }
    }

    private void showThemeSelectionDialog() {
        String[] themes = {"Light", "Dark", "Purple"};
        String currentTheme = ThemeManager.getCurrentTheme(this);
        int checkedItem = 0;

        if (currentTheme.equals(ThemeManager.THEME_DARK)) checkedItem = 1;
        else if (currentTheme.equals(ThemeManager.THEME_PURPLE)) checkedItem = 2;

        new AlertDialog.Builder(this)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    String selectedTheme = ThemeManager.THEME_LIGHT;
                    if (which == 1) selectedTheme = ThemeManager.THEME_DARK;
                    else if (which == 2) selectedTheme = ThemeManager.THEME_PURPLE;

                    // Save and Apply
                    ThemeManager.saveTheme(this, selectedTheme);
                    ThemeManager.applyGlobalNightMode(getApplicationContext());

                    dialog.dismiss();

                    // Restart Activity to apply changes
                    recreate();
                    // Ideally, you might want to restart the whole app or navigate to Home to refresh all UI
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBackupStatusDialog() {
        // ... (Existing code) ...
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