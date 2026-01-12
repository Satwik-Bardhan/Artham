package com.phynix.artham;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.TaskStackBuilder;

import com.phynix.artham.utils.ThemeManager;

public class ThemeSelectionActivity extends AppCompatActivity {

    private CardView cardLight, cardDark, cardPurple;
    private RadioButton radioLight, radioDark, radioPurple;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_selection);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        setupCurrentState();
        setupClickListeners();
    }

    private void initViews() {
        backButton = findViewById(R.id.back_button);
        cardLight = findViewById(R.id.cardLight);
        cardDark = findViewById(R.id.cardDark);
        cardPurple = findViewById(R.id.cardPurple);
        radioLight = findViewById(R.id.radioLight);
        radioDark = findViewById(R.id.radioDark);
        radioPurple = findViewById(R.id.radioPurple);
    }

    private void setupCurrentState() {
        String currentTheme = ThemeManager.getTheme(this);
        radioLight.setChecked(false);
        radioDark.setChecked(false);
        radioPurple.setChecked(false);

        if (ThemeManager.THEME_LIGHT.equals(currentTheme)) {
            radioLight.setChecked(true);
        } else if (ThemeManager.THEME_PURPLE.equals(currentTheme)) {
            radioPurple.setChecked(true);
        } else {
            radioDark.setChecked(true);
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cardLight.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_LIGHT));
        cardDark.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_DARK));
        cardPurple.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_PURPLE));
    }

    private void handleThemeChange(String newTheme) {
        String currentTheme = ThemeManager.getTheme(this);

        if (!currentTheme.equals(newTheme)) {
            // 1. Save and Apply
            ThemeManager.saveTheme(this, newTheme);
            ThemeManager.applyTheme(newTheme);

            // 2. Update UI Radio Buttons
            radioLight.setChecked(ThemeManager.THEME_LIGHT.equals(newTheme));
            radioDark.setChecked(ThemeManager.THEME_DARK.equals(newTheme));
            radioPurple.setChecked(ThemeManager.THEME_PURPLE.equals(newTheme));

            // 3. Restart App to apply to ALL pages
            ThemeManager.restartApp(this);
        }
    }
}