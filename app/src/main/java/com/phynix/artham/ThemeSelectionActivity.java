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

    private CardView cardSystem, cardLight, cardDark, cardPurple, cardEmerald, cardRose;
    private RadioButton radioSystem, radioLight, radioDark, radioPurple, radioEmerald, radioRose;
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
        cardSystem = findViewById(R.id.cardSystem);
        cardLight = findViewById(R.id.cardLight);
        cardDark = findViewById(R.id.cardDark);
        cardPurple = findViewById(R.id.cardPurple);
        cardEmerald = findViewById(R.id.cardEmerald);
        cardRose = findViewById(R.id.cardRose);
        radioSystem = findViewById(R.id.radioSystem);
        radioLight = findViewById(R.id.radioLight);
        radioDark = findViewById(R.id.radioDark);
        radioPurple = findViewById(R.id.radioPurple);
        radioEmerald = findViewById(R.id.radioEmerald);
        radioRose = findViewById(R.id.radioRose);
    }

    private void setupCurrentState() {
        String currentTheme = ThemeManager.getTheme(this);
        radioSystem.setChecked(false);
        radioLight.setChecked(false);
        radioDark.setChecked(false);
        radioPurple.setChecked(false);
        radioEmerald.setChecked(false);
        radioRose.setChecked(false);

        switch (currentTheme) {
            case ThemeManager.THEME_LIGHT:
                radioLight.setChecked(true);
                break;
            case ThemeManager.THEME_DARK:
                radioDark.setChecked(true);
                break;
            case ThemeManager.THEME_PURPLE:
                radioPurple.setChecked(true);
                break;
            case ThemeManager.THEME_EMERALD:
                radioEmerald.setChecked(true);
                break;
            case ThemeManager.THEME_ROSE:
                radioRose.setChecked(true);
                break;
            default:
                radioSystem.setChecked(true);
                break;
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cardSystem.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_SYSTEM));
        cardLight.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_LIGHT));
        cardDark.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_DARK));
        cardPurple.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_PURPLE));
        cardEmerald.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_EMERALD));
        cardRose.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_ROSE));
    }

    private void handleThemeChange(String newTheme) {
        String currentTheme = ThemeManager.getTheme(this);

        if (!currentTheme.equals(newTheme)) {
            // 1. Save and Apply
            ThemeManager.saveTheme(this, newTheme);
            ThemeManager.applyTheme(newTheme);

            // 2. Update UI Radio Buttons
            radioSystem.setChecked(ThemeManager.THEME_SYSTEM.equals(newTheme));
            radioLight.setChecked(ThemeManager.THEME_LIGHT.equals(newTheme));
            radioDark.setChecked(ThemeManager.THEME_DARK.equals(newTheme));
            radioPurple.setChecked(ThemeManager.THEME_PURPLE.equals(newTheme));
            radioEmerald.setChecked(ThemeManager.THEME_EMERALD.equals(newTheme));
            radioRose.setChecked(ThemeManager.THEME_ROSE.equals(newTheme));

            // 3. Restart App to apply to ALL pages
            ThemeManager.restartApp(this);
        }
    }
}