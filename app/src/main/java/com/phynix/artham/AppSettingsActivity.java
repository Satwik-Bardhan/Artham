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

public class AppSettingsActivity extends AppCompatActivity {

    // SharedPreferences keys
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_CALCULATOR = "calculator_enabled";

    private SwitchMaterial calculatorSwitch;
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

        // Features & Tools
        calculatorSwitch = findViewById(R.id.calculatorSwitch);

        // Appearance & Language
        languageLayout = findViewById(R.id.languageLayout);
        currentLanguageTextView = findViewById(R.id.currentLanguage);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load switch states
        if (calculatorSwitch != null) {
            calculatorSwitch.setChecked(prefs.getBoolean(KEY_CALCULATOR, true));
        }
    }

    private void setupClickListeners() {
        // Back Button
        View backBtn = findViewById(R.id.back_button);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        // Data Backup & Sync - Shows Dialog
        if (dataBackupLayout != null) {
            dataBackupLayout.setOnClickListener(v -> showBackupStatusDialog());
        }

        // Calculator Switch
        if (calculatorSwitch != null) {
            calculatorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                saveBooleanSetting(KEY_CALCULATOR, isChecked);
                Toast.makeText(this, isChecked ? "Calculator will be shown" : "Calculator will be hidden", Toast.LENGTH_SHORT).show();
            });
        }

        // Language Layout
        if (languageLayout != null) {
            languageLayout.setOnClickListener(v -> {
                Toast.makeText(this, "Language selection coming soon!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showBackupStatusDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = (user != null && user.getEmail() != null) ? user.getEmail() : "Unknown Account";

        new AlertDialog.Builder(this)
                .setTitle("Cloud Backup & Sync")
                .setMessage("Your data is automatically backed up and synced securely with your Google Account.\n\n" +
                        "Account: " + email + "\n" +
                        "Status: \u2705 Active") // Checkmark icon
                .setPositiveButton("OK", null)
                .setNeutralButton("Export Data", (dialog, which) -> {
                    // Navigate to Download/Export options
                    try {
                        Intent intent = new Intent(AppSettingsActivity.this, DownloadOptionsActivity.class);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Export feature not available yet", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void saveBooleanSetting(String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(key, value);
        editor.apply();
    }
}