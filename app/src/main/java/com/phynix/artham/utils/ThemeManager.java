package com.phynix.artham.utils;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    /**
     * Applies the selected theme to the entire application.
     * @param theme The theme to apply, either THEME_LIGHT or THEME_DARK.
     */
    public static void applyTheme(String theme) {
        switch (theme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                // Optional: follow system settings if no theme is specified
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}