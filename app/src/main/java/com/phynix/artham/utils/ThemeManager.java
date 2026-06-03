package com.phynix.artham.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;

import java.util.Random;

import androidx.appcompat.app.AppCompatDelegate;

import com.phynix.artham.activities.HomeActivity;
import com.phynix.artham.R;

public class ThemeManager {
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_PURPLE = "purple";
    public static final String THEME_EMERALD = "emerald";
    public static final String THEME_ROSE = "rose";
    public static final String THEME_SUNSET = "sunset";
    public static final String THEME_OCEAN = "ocean";
    public static final String THEME_RANDOM = "random";

    /**
     * Pool of themes that the random option can pick from.
     * Excludes THEME_SYSTEM and THEME_RANDOM itself.
     */
    private static final String[] RANDOMIZABLE_THEMES = {
            THEME_LIGHT, THEME_DARK, THEME_PURPLE, THEME_EMERALD, THEME_ROSE, THEME_SUNSET, THEME_OCEAN
    };

    private static final String PREF_NAME = "app_theme";
    private static final String KEY_THEME = "selected_theme";

    /**
     * Applies the selected theme to the entire application using AppCompatDelegate.
     * This handles Night/Light mode switching.
     */
    public static void applyTheme(String theme) {
        // For random, resolve to an actual theme first
        String resolved = THEME_RANDOM.equals(theme) ? resolveRandomTheme() : theme;

        switch (resolved) {
            case THEME_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
            case THEME_PURPLE:
            case THEME_EMERALD:
            case THEME_ROSE:
            case THEME_SUNSET:
            case THEME_OCEAN:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Applies the specific style to the Activity context.
     * Call this BEFORE super.onCreate() in every Activity.
     */
    public static void applyActivityTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, THEME_SYSTEM);

        // If user chose "Random", resolve to an actual theme for this session
        if (THEME_RANDOM.equals(theme)) {
            theme = resolveRandomTheme();
        }


        switch (theme) {
            case THEME_SYSTEM:
                // Follow device setting: dark system → Dark theme, light system → Light theme
                if (isSystemDarkMode(activity)) {
                    activity.setTheme(R.style.Theme_Artham_Dark);
                } else {
                    activity.setTheme(R.style.Theme_Artham);
                }
                break;
            case THEME_LIGHT:
                activity.setTheme(R.style.Theme_Artham);
                break;
            case THEME_DARK:
                activity.setTheme(R.style.Theme_Artham_Dark);
                break;
            case THEME_PURPLE:
                activity.setTheme(R.style.Theme_Artham_Purple);
                break;
            case THEME_EMERALD:
                activity.setTheme(R.style.Theme_Artham_Emerald);
                break;
            case THEME_ROSE:
                activity.setTheme(R.style.Theme_Artham_Rose);
                break;
            case THEME_SUNSET:
                activity.setTheme(R.style.Theme_Artham_Sunset);
                break;
            case THEME_OCEAN:
                activity.setTheme(R.style.Theme_Artham_Ocean);
                break;
            default:
                activity.setTheme(R.style.Theme_Artham);
                break;
        }

        // Apply the user's font preference as a theme overlay.
        // This MUST happen after setTheme() and BEFORE super.onCreate()/setContentView()
        // so all inflated views (including RecyclerView items, dialogs) inherit the font.
        FontManager.applyFontOverlay(activity);
    }

    /**
     * Checks if the device is currently in dark mode.
     */
    public static boolean isSystemDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void saveTheme(Context context, String theme) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME, theme).apply();
        applyTheme(theme);
    }

    public static String getTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    /**
     * Returns a randomly selected theme from the pool.
     * Called once per app cold-start when user has "Random" selected.
     */
    private static String resolveRandomTheme() {
        return RANDOMIZABLE_THEMES[new Random().nextInt(RANDOMIZABLE_THEMES.length)];
    }

    public static void restartApp(Activity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(activity, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 200);
    }
}