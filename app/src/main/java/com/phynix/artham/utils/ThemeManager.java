package com.phynix.artham.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.phynix.artham.HomePage;
import com.phynix.artham.R;

public class ThemeManager {
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_PURPLE = "purple";

    private static final String PREF_NAME = "app_theme";
    private static final String KEY_THEME = "selected_theme";

    /**
     * Applies the selected theme to the entire application using AppCompatDelegate.
     * This handles Night/Light mode switching.
     */
    public static void applyTheme(String theme) {
        switch (theme) {
            case THEME_LIGHT:
            case THEME_PURPLE:
                // Both Light and Purple use the "Light" mode base for resources
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                // [FIX] Default to LIGHT now
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    /**
     * Applies the specific style to the Activity context.
     * Call this BEFORE super.onCreate() in every Activity.
     */
    public static void applyActivityTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // [FIX] Default is LIGHT
        String theme = prefs.getString(KEY_THEME, THEME_LIGHT);

        if (THEME_PURPLE.equals(theme)) {
            activity.setTheme(R.style.Theme_Artham_Purple);
        } else if (THEME_DARK.equals(theme)) {
            activity.setTheme(R.style.Theme_Artham_Dark);
        } else {
            activity.setTheme(R.style.Theme_Artham); // Light
        }
    }

    public static void saveTheme(Context context, String theme) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME, theme).apply();
        applyTheme(theme);
    }

    public static String getTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // [FIX] Default is LIGHT
        return prefs.getString(KEY_THEME, THEME_LIGHT);
    }

    public static void restartApp(Activity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(activity, HomePage.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 200);
    }
}