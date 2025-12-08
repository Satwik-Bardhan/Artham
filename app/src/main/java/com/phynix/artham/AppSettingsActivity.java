package com.phynix.artham;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.phynix.artham.utils.ThemeManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class AppSettingsActivity extends AppCompatActivity {

    // SharedPreferences keys for saving settings
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_APP_LOCK = "app_lock_enabled";
    private static final String KEY_CALCULATOR = "calculator_enabled";
    private static final String KEY_THEME = "app_theme";

    private SwitchMaterial appLockSwitch, calculatorSwitch, darkThemeSwitch;
    private LinearLayout dataBackupLayout, languageLayout;
    private TextView currentLanguageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        // Security & Privacy
        dataBackupLayout = findViewById(R.id.dataBackupLayout);
        appLockSwitch = findViewById(R.id.appLockSwitch);

        // Features & Tools
        calculatorSwitch = findViewById(R.id.calculatorSwitch);

        // Appearance & Language
        languageLayout = findViewById(R.id.languageLayout);
        currentLanguageTextView = findViewById(R.id.currentLanguage);
        darkThemeSwitch = findViewById(R.id.darkThemeSwitch);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load switch states
        appLockSwitch.setChecked(prefs.getBoolean(KEY_APP_LOCK, false));
        calculatorSwitch.setChecked(prefs.getBoolean(KEY_CALCULATOR, true));

        // Load theme state
        String currentTheme = prefs.getString(KEY_THEME, ThemeManager.THEME_DARK);
        darkThemeSwitch.setChecked(ThemeManager.THEME_DARK.equals(currentTheme));
    }

    private void setupClickListeners() {
        // Back Button
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        // Data Backup Layout
        dataBackupLayout.setOnClickListener(v -> {
            Toast.makeText(this, "Data Backup & Sync settings coming soon!", Toast.LENGTH_SHORT).show();
        });

        // App Lock Switch
        appLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveBooleanSetting(KEY_APP_LOCK, isChecked);
            if (isChecked) {
                Toast.makeText(this, "App Lock Enabled (Setup Required)", Toast.LENGTH_SHORT).show();
                // You can navigate to a new activity here to set up a PIN or Fingerprint
            } else {
                Toast.makeText(this, "App Lock Disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Calculator Switch
        calculatorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveBooleanSetting(KEY_CALCULATOR, isChecked);
            Toast.makeText(this, isChecked ? "Calculator will be shown" : "Calculator will be hidden", Toast.LENGTH_SHORT).show();
        });

        // Language Layout
        languageLayout.setOnClickListener(v -> {
            Toast.makeText(this, "Language selection coming soon!", Toast.LENGTH_SHORT).show();
            // You can show a language picker dialog or a new activity here
        });

        // Dark Theme Switch
        darkThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String theme = isChecked ? ThemeManager.THEME_DARK : ThemeManager.THEME_LIGHT;
            saveStringSetting(KEY_THEME, theme);
            ThemeManager.applyTheme(theme);
        });
    }

    // Helper method to save a boolean setting
    private void saveBooleanSetting(String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    // Helper method to save a string setting
    private void saveStringSetting(String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(key, value);
        editor.apply();
    }
}