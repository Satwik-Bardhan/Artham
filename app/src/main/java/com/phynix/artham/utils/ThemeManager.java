package com.phynix.artham.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.phynix.artham.R;

public class ThemeManager {
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_PURPLE = "purple";

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_THEME = "app_theme";

    /**
     * Sets the correct theme style on an Activity.
     * MUST be called before setContentView() in every Activity's onCreate.
     */
    public static void applyActivityTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, THEME_LIGHT);

        switch (theme) {
            case THEME_DARK:
                activity.setTheme(R.style.Theme_Artham_Dark);
                break;
            case THEME_PURPLE:
                activity.setTheme(R.style.Theme_Artham_Purple);
                break;
            case THEME_LIGHT:
            default:
                activity.setTheme(R.style.Theme_Artham);
                break;
        }
    }

    /**
     * Applies the Night Mode setting globally.
     * Should be called in Application.onCreate() or when theme changes.
     */
    public static void applyGlobalNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, THEME_LIGHT);

        switch (theme) {
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_LIGHT:
            case THEME_PURPLE:
                // Both Light and Purple are "Day" modes in terms of system flags
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    public static String getCurrentTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME, THEME_LIGHT);
    }

    public static void saveTheme(Context context, String theme) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME, theme).apply();
    }
}