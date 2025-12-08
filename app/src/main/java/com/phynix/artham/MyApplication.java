package com.phynix.artham;

import android.app.Application;
import android.content.SharedPreferences;

import com.phynix.artham.utils.ThemeManager;

public class MyApplication extends Application {

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_THEME = "app_theme";

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved theme as soon as the application starts
        applyAppTheme();
    }

    private void applyAppTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // Default to dark theme if no preference is saved yet
        String currentTheme = prefs.getString(KEY_THEME, ThemeManager.THEME_DARK);
        ThemeManager.applyTheme(currentTheme);
    }
}