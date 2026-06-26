package com.phynix.artham.utils;

import android.content.Context;
import android.util.Log;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.gms.tasks.Task;

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
        try {
            AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(context);
            Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

            appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
                updateAvailable = (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE);
                hasChecked = true;
                Log.d(TAG, "Update check complete. Available: " + updateAvailable);
                if (callback != null) {
                    callback.onResult(updateAvailable);
                }
            }).addOnFailureListener(e -> {
                // Failure is expected during development / sideloaded installs
                Log.w(TAG, "Update check failed (expected if not installed from Play Store): " + e.getMessage());
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
        return updateAvailable;
    }

    /**
     * Whether we've completed at least one check.
     */
    public boolean hasChecked() {
        return hasChecked;
    }

    /**
     * Resets the cached state (e.g. after the user updates the app).
     */
    public void reset() {
        updateAvailable = false;
        hasChecked = false;
    }
}
