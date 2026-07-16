package com.phynix.artham.auth;

import android.content.Context;

import com.phynix.artham.utils.Constants;

/**
 * AuthManager — Centralized authentication abstraction.
 *
 * Replaces all direct FirebaseAuth calls throughout the app.
 * Delegates to SupabaseAuthManager for actual auth operations.
 * Provides Java-friendly static methods for use across Activities, ViewModels, etc.
 */
public class AuthManager {

    /**
     * Check if a user is currently signed in.
     * Returns true for both authenticated users (Supabase) and local/guest mode.
     */
    public static boolean isSignedIn(Context context) {
        if (isLocalMode(context)) return true;
        return SupabaseAuthManager.isAuthenticated();
    }

    /**
     * Get the current user's ID.
     * Returns "local_user" for guest mode, Supabase auth ID for signed-in users.
     */
    public static String getUserId(Context context) {
        if (isLocalMode(context)) return "local_user";
        return SupabaseAuthManager.getCurrentUserId();
    }

    /**
     * Check if the app is in local/guest mode (no account).
     */
    public static boolean isLocalMode(Context context) {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean("is_local_mode", false);
    }

    /**
     * Save the user profile details to SharedPreferences and SessionCache.
     */
    public static void saveUserProfile(Context context, String name, String email, String photoUrl) {
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("user_name", name)
                .putString("user_email", email)
                .putString("user_photo_url", photoUrl)
                .apply();

        // Populate session cache immediately
        com.phynix.artham.models.Users user = new com.phynix.artham.models.Users(
                getUserId(context), name, email, null, photoUrl
        );
        com.phynix.artham.utils.SessionCache.getInstance().cacheUserProfile(user);
    }

    public static String getUserName(Context context) {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getString("user_name", "User");
    }

    public static String getUserEmail(Context context) {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getString("user_email", "");
    }

    public static String getUserPhotoUrl(Context context) {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getString("user_photo_url", null);
    }

    /**
     * Sign out the current user from Supabase.
     */
    public static void signOut(Context context) {
        if (context != null) {
            context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove("user_name")
                    .remove("user_email")
                    .remove("user_photo_url")
                    .apply();
        }
        com.phynix.artham.utils.SessionCache.getInstance().clear();
        SupabaseAuthManager.signOut(null);
    }
}
