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
     * Also checks SharedPreferences as fallback since Supabase session may take
     * time to restore from storage on app cold start.
     */
    public static boolean isSignedIn(Context context) {
        if (isLocalMode(context)) return true;
        if (SupabaseAuthManager.isAuthenticated()) return true;

        // Fallback: check if we have saved user data from a previous login
        // This covers the case where Supabase session hasn't loaded from storage yet
        String savedEmail = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getString("user_email", "");
        return savedEmail != null && !savedEmail.trim().isEmpty();
    }

    /**
     * Generate a default UID in format "Artham" + 6-digit random number (e.g., Artham481920).
     * Uses SecureRandom for better uniqueness across users.
     */
    public static String generateDefaultUid() {
        int randomNum = 100000 + new java.security.SecureRandom().nextInt(900000);
        return "Artham" + randomNum;
    }

    private static boolean isRawUuid(String uid) {
        if (uid == null) return false;
        return uid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    /**
     * Get the current user's ID.
     * Returns custom UID if set, otherwise generates a unique "Artham" + number per user.
     * For guest/local users, the generated UID is persisted so it remains consistent.
     */
    public static String getUserId(Context context) {
        if (context != null) {
            android.content.SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            String customUid = prefs.getString("user_custom_uid", "");

            if (!customUid.trim().isEmpty() && !isRawUuid(customUid.trim())) {
                return customUid.trim();
            }

            // Check if restored from Supabase during login
            String restoredUid = SupabaseAuthManager.getRestoredCustomUid();
            if (restoredUid != null && !restoredUid.trim().isEmpty()) {
                prefs.edit().putString("user_custom_uid", restoredUid.trim()).apply();
                return restoredUid.trim();
            }

            // Return permanent Supabase Auth ID if authenticated
            String authId = SupabaseAuthManager.getCurrentUserId();
            if (authId != null && !authId.isEmpty()) {
                return authId;
            }

            // For guest/local users: generate once, then persist for consistency
            String guestUid = prefs.getString("local_guest_uid", "");
            if (!guestUid.isEmpty()) {
                return guestUid;
            }
            String newGuestUid = generateDefaultUid();
            prefs.edit().putString("local_guest_uid", newGuestUid).commit();
            return newGuestUid;
        }
        return generateDefaultUid();
    }

    public interface UidCheckCallback {
        void onResult(boolean isAvailable);
    }

    /**
     * Check if a custom UID is available (not already taken by another user).
     */
    public static void checkUidAvailability(Context context, String newUid, UidCheckCallback callback) {
        if (newUid == null || newUid.trim().isEmpty()) {
            if (callback != null) callback.onResult(false);
            return;
        }
        String trimmedUid = newUid.trim();
        String currentUid = getUserId(context);

        if (trimmedUid.equalsIgnoreCase(currentUid)) {
            if (callback != null) callback.onResult(true);
            return;
        }

        if (isLocalMode(context) || !SupabaseAuthManager.isAuthenticated()) {
            android.content.SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            String savedCustomUid = prefs.getString("user_custom_uid", "");
            if (!savedCustomUid.isEmpty() && !savedCustomUid.equalsIgnoreCase(currentUid) && savedCustomUid.equalsIgnoreCase(trimmedUid)) {
                if (callback != null) callback.onResult(false);
            } else {
                if (callback != null) callback.onResult(true);
            }
            return;
        }

        String currentAuthId = SupabaseAuthManager.getCurrentUserId();
        SupabaseAuthManager.checkUidAvailability(trimmedUid, currentAuthId, isAvailable -> {
            if (callback != null) callback.onResult(isAvailable);
        });
    }

    /**
     * Save custom UID to SharedPreferences and Supabase.
     */
    public static void saveCustomUid(Context context, String customUid) {
        if (context == null) return;
        String trimmed = customUid != null ? customUid.trim() : "";
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("user_custom_uid", trimmed)
                .commit();

        SupabaseAuthManager.setRestoredCustomUid(trimmed);

        String authId = SupabaseAuthManager.getCurrentUserId();
        if (authId != null && !trimmed.isEmpty()) {
            SupabaseAuthManager.updateCustomUid(authId, trimmed, null);
        }
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
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString("user_name", name);
        editor.putString("user_display_name", name);
        editor.putString("user_email", email);
        editor.putString("user_photo_url", photoUrl);
        if (photoUrl != null && !photoUrl.isEmpty()) {
            editor.putString("user_photo_path", photoUrl);
        }
        editor.apply();

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
     * Forces a cloud sync push BEFORE clearing local data to prevent data loss.
     */
    public static void signOut(Context context) {
        if (context != null) {
            // 1. Capture local mode BEFORE clearing prefs
            boolean wasLocalMode = isLocalMode(context);

            // 2. Force push ALL local data to cloud BEFORE clearing anything
            //    This ensures no user data is lost even if sync hadn't completed.
            //    Skip for local/guest mode users (they have no cloud account).
            if (!wasLocalMode) {
                try {
                    com.phynix.artham.db.sync.SyncEngine.forcePushAll(context, () -> {
                        // Push completed — now clear local database
                        clearAllLocalData(context);
                    });
                } catch (Exception e) {
                    android.util.Log.e("AuthManager", "Force push before sign-out failed", e);
                    // Still clear local data even if push failed
                    clearAllLocalData(context);
                }
            } else {
                // Local mode — no push needed, clear immediately
                clearAllLocalData(context);
            }

            // 3. Cancel periodic sync worker
            try {
                androidx.work.WorkManager.getInstance(context)
                        .cancelUniqueWork("artham_periodic_sync");
            } catch (Exception e) {
                android.util.Log.w("AuthManager", "Failed to cancel sync worker", e);
            }
        }
        com.phynix.artham.utils.SessionCache.getInstance().clear();
        SupabaseAuthManager.signOut(null);
    }

    /**
     * Sign out for account deletion — does NOT push data back to cloud.
     * Used exclusively by the account deletion flow where cloud data
     * has already been deleted and must not be re-uploaded.
     */
    public static void signOutForDeletion(Context context) {
        if (context != null) {
            // Clear all local data immediately (no push, no delay)
            clearAllLocalData(context);

            // Cancel periodic sync worker
            try {
                androidx.work.WorkManager.getInstance(context)
                        .cancelUniqueWork("artham_periodic_sync");
            } catch (Exception e) {
                android.util.Log.w("AuthManager", "Failed to cancel sync worker", e);
            }
        }
        com.phynix.artham.utils.SessionCache.getInstance().clear();
        SupabaseAuthManager.signOut(null);
    }

    /**
     * Clears all local user data: SharedPreferences, Room database, offline queue.
     */
    private static void clearAllLocalData(Context context) {
        // Clear ALL SharedPreferences
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .edit().clear().apply();
        context.getSharedPreferences("CashbookPrefs", Context.MODE_PRIVATE)
                .edit().clear().apply();
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply();
        context.getSharedPreferences("AppSettingsPrefs", Context.MODE_PRIVATE)
                .edit().clear().apply();
        context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply();

        // Clear offline transaction queue
        com.phynix.artham.utils.OfflineTransactionManager.clearQueue(context);

        // Clear local Room database
        android.app.Application app = (context.getApplicationContext() instanceof android.app.Application)
                ? (android.app.Application) context.getApplicationContext()
                : null;
        if (app != null) {
            com.phynix.artham.db.DataRepository.getInstance(app).clearLocalDatabase();
        }
    }
}
