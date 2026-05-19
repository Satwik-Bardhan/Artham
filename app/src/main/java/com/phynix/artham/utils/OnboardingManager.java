package com.phynix.artham.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Centralized manager for onboarding / tutorial state tracking.
 * Uses SharedPreferences to persist per-page completion flags.
 *
 * Usage:
 *   OnboardingManager mgr = OnboardingManager.getInstance(context);
 *   if (mgr.isFirstLaunch()) { ... show welcome dialog ... }
 *   if (!mgr.isPageTutorialCompleted("home")) { ... show tooltips ... }
 */
public class OnboardingManager {

    private static final String PREFS_NAME = "ArthamOnboarding";
    private static final String KEY_FIRST_LAUNCH = "first_launch_completed";
    private static final String KEY_PAGE_PREFIX = "page_tutorial_";

    // Page keys
    public static final String PAGE_HOME = "home";
    public static final String PAGE_TRANSACTIONS = "transactions";
    public static final String PAGE_SETTINGS = "settings";
    public static final String PAGE_CASH_IN_OUT = "cash_in_out";
    public static final String PAGE_CASHBOOK_SWITCH = "cashbook_switch";

    private static OnboardingManager sInstance;
    private final SharedPreferences prefs;

    private OnboardingManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized OnboardingManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new OnboardingManager(context);
        }
        return sInstance;
    }

    /**
     * Returns true if the user has NEVER completed the initial welcome dialog.
     */
    public boolean isFirstLaunch() {
        return !prefs.getBoolean(KEY_FIRST_LAUNCH, false);
    }

    /**
     * Marks the global welcome dialog as shown.
     */
    public void markOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, true).apply();
    }

    /**
     * Returns true if the tutorial for the given page has already been completed.
     */
    public boolean isPageTutorialCompleted(String pageKey) {
        return prefs.getBoolean(KEY_PAGE_PREFIX + pageKey, false);
    }

    /**
     * Marks the tutorial for the given page as completed so it won't show again.
     */
    public void markPageTutorialCompleted(String pageKey) {
        prefs.edit().putBoolean(KEY_PAGE_PREFIX + pageKey, true).apply();
    }

    /**
     * Resets ALL onboarding state — used by "Replay Tutorial" in App Settings.
     */
    public void resetOnboarding() {
        prefs.edit().clear().apply();
    }
}
