package com.phynix.artham.utils;

import android.content.Context;
import android.util.Log;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Singleton utility that checks for available app updates via the
 * Google Play In-App Updates API.
 *
 * Usage:
 *   UpdateChecker.getInstance().checkForUpdate(context, updateAvailable -> {
 *       // Show/hide update indicators
 *   });
 *
 *   // Later, quick check without callback:
 *   if (UpdateChecker.getInstance().isUpdateAvailable()) { ... }
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    // ⚠️ SET TO true TO TEST THE UPDATE BUBBLE UI
    // ⚠️ SET BACK TO false BEFORE PUBLISHING TO PLAY STORE
    public static final boolean DEBUG_TEST_UPDATE = false;

    private static volatile UpdateChecker instance;

    private boolean updateAvailable = false;
    private boolean hasChecked = false;

    private UpdateChecker() {}

    public static UpdateChecker getInstance() {
        if (instance == null) {
            synchronized (UpdateChecker.class) {
                if (instance == null) {
                    instance = new UpdateChecker();
                }
            }
        }
        return instance;
    }

    /**
     * Callback interface for update check results.
     */
    public interface UpdateCheckCallback {
        void onResult(boolean updateAvailable);
    }

    /**
     * Checks with the Play Store whether an update is available.
     * The result is cached so subsequent calls to {@link #isUpdateAvailable()}
     * return instantly without hitting the network.
     *
     * @param context  any Context (Application context preferred)
     * @param callback receives true if an update is available, false otherwise
     */
    public void checkForUpdate(Context context, UpdateCheckCallback callback) {
        if (DEBUG_TEST_UPDATE) {
            updateAvailable = true;
            hasChecked = true;
            if (callback != null) callback.onResult(true);
            return;
        }

        try {
            AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(context.getApplicationContext());

            appUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(appUpdateInfo -> {
                    int availability = appUpdateInfo.updateAvailability();
                    updateAvailable = (availability == UpdateAvailability.UPDATE_AVAILABLE
                            || availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS);
                    hasChecked = true;
                    Log.d(TAG, "Update check complete. Availability=" + availability + ", updateAvailable=" + updateAvailable);
                    if (callback != null) {
                        callback.onResult(updateAvailable);
                    }
                })
                .addOnFailureListener(e -> {
                    // Failure is expected during development / sideloaded installs
                    Log.w(TAG, "Update check failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    updateAvailable = false;
                    hasChecked = true;
                    if (callback != null) {
                        callback.onResult(false);
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error creating AppUpdateManager", e);
            updateAvailable = false;
            hasChecked = true;
            if (callback != null) {
                callback.onResult(false);
            }
        }
    }

    /**
     * Returns the cached update availability.
     * Call {@link #checkForUpdate(Context, UpdateCheckCallback)} at least once first.
     */
    public boolean isUpdateAvailable() {
        return DEBUG_TEST_UPDATE || updateAvailable;
    }

    /**
     * Whether we've completed at least one check.
     */
    public boolean hasChecked() {
        return hasChecked;
    }

    /**
     * Resets the cached state so the next call to checkForUpdate
     * will hit the Play Store again.
     */
    public void reset() {
        updateAvailable = false;
        hasChecked = false;
    }
}
