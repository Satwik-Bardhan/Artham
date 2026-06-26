package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
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

public class ThemeSelectionActivity extends BaseActivity {

    private CardView cardSystem, cardLight, cardDark, cardPurple, cardEmerald, cardRose, cardSunset, cardOcean, cardYellow, cardRandom;
    private RadioButton radioSystem, radioLight, radioDark, radioPurple, radioEmerald, radioRose, radioSunset, radioOcean, radioYellow, radioRandom;
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
        cardSunset = findViewById(R.id.cardSunset);
        cardOcean = findViewById(R.id.cardOcean);
        cardYellow = findViewById(R.id.cardYellow);
        cardRandom = findViewById(R.id.cardRandom);
        radioSystem = findViewById(R.id.radioSystem);
        radioLight = findViewById(R.id.radioLight);
        radioDark = findViewById(R.id.radioDark);
        radioPurple = findViewById(R.id.radioPurple);
        radioEmerald = findViewById(R.id.radioEmerald);
        radioRose = findViewById(R.id.radioRose);
        radioSunset = findViewById(R.id.radioSunset);
        radioOcean = findViewById(R.id.radioOcean);
        radioYellow = findViewById(R.id.radioYellow);
        radioRandom = findViewById(R.id.radioRandom);
    }

    private void setupCurrentState() {
        String currentTheme = ThemeManager.getTheme(this);
        radioSystem.setChecked(false);
        radioLight.setChecked(false);
        radioDark.setChecked(false);
        radioPurple.setChecked(false);
        radioEmerald.setChecked(false);
        radioRose.setChecked(false);
        radioSunset.setChecked(false);
        radioOcean.setChecked(false);
        radioYellow.setChecked(false);
        radioRandom.setChecked(false);

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
            case ThemeManager.THEME_SUNSET:
                radioSunset.setChecked(true);
                break;
            case ThemeManager.THEME_OCEAN:
                radioOcean.setChecked(true);
                break;
            case ThemeManager.THEME_YELLOW:
                radioYellow.setChecked(true);
                break;
            case ThemeManager.THEME_RANDOM:
                radioRandom.setChecked(true);
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
        cardSunset.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_SUNSET));
        cardOcean.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_OCEAN));
        cardYellow.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_YELLOW));
        cardRandom.setOnClickListener(v -> handleThemeChange(ThemeManager.THEME_RANDOM));
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
            radioSunset.setChecked(ThemeManager.THEME_SUNSET.equals(newTheme));
            radioOcean.setChecked(ThemeManager.THEME_OCEAN.equals(newTheme));
            radioYellow.setChecked(ThemeManager.THEME_YELLOW.equals(newTheme));
            radioRandom.setChecked(ThemeManager.THEME_RANDOM.equals(newTheme));

            // 3. Restart App to apply to ALL pages
            ThemeManager.restartApp(this);
        }
    }
}