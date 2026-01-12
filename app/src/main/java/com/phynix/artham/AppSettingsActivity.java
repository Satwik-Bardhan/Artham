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
    private LinearLayout dataBackupLayout, languageLayout, themeLayout;
    private TextView currentLanguageTextView, currentThemeTextView;

    // To track theme changes upon return
    private String originalTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Store original theme to check for changes later
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
            // Theme changed, recreate this activity to apply new colors
            recreate();
        } else {
            // Just update the text label
            updateThemeLabel();
        }
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
        updateThemeLabel();
    }

    private void updateThemeLabel() {
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
                String status = isChecked ? "Calculator enabled" : "Calculator disabled";
                Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
            });
        }

        if (languageLayout != null) {
            languageLayout.setOnClickListener(v -> Toast.makeText(this, "Language selection coming soon!", Toast.LENGTH_SHORT).show());
        }

        // NEW: Open ThemeSelectionActivity instead of Dialog
        if (themeLayout != null) {
            themeLayout.setOnClickListener(v -> {
                Intent intent = new Intent(AppSettingsActivity.this, ThemeSelectionActivity.class);
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