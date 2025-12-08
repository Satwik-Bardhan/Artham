package com.phynix.artham;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.phynix.artham.utils.ErrorHandler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * MainActivity serves as the application entry point and authentication router.
 * This activity determines whether to navigate to SigninActivity or HomePage
 * based on the user's authentication status.
 *
 * [FIXED] Removed all Guest Mode logic for a cleaner, auth-focused flow.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The theme is applied by MyApplication before this activity starts
        super.onCreate(savedInstanceState);
        // [FIX] No layout is needed for this router activity
        // setContentView(R.layout.activity_main);

        Log.d(TAG, getString(R.string.log_main_activity_started));

        try {
            FirebaseCrashlytics.getInstance().log(getString(R.string.log_app_startup_initiated));
        } catch (Exception e) {
            Log.d(TAG, getString(R.string.log_crashlytics_unavailable));
        }

        checkAuthenticationAndNavigate();
    }

    /**
     * Checks the current user's authentication status and navigates to the appropriate activity.
     * Navigates to HomePage if user is authenticated, SigninActivity otherwise.
     */
    private void checkAuthenticationAndNavigate() {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            Intent navigationIntent;

            if (currentUser == null) {
                // No user is signed in, navigate to Sign-in
                Log.d(TAG, getString(R.string.log_no_authenticated_user));
                navigationIntent = new Intent(this, SigninActivity.class);
            } else {
                // User is signed in, navigate to Home
                Log.d(TAG, getString(R.string.log_authenticated_user_found, currentUser.getUid()));
                navigationIntent = new Intent(this, HomePage.class);
                // [FIX] Removed isGuest flag, it's no longer needed
                // navigationIntent.putExtra("isGuest", false);

                try {
                    FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                    crashlytics.setUserId(currentUser.getUid());
                    crashlytics.setCustomKey("user_email",
                            currentUser.getEmail() != null ? currentUser.getEmail() : getString(R.string.unknown_email));
                } catch (Exception e) {
                    Log.d(TAG, getString(R.string.log_crashlytics_unavailable));
                }
            }

            startActivity(navigationIntent);
            // Optional: Add a fade-in/out transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        } catch (Exception e) {
            Log.e(TAG, getString(R.string.error_authentication_check), e);

            // Use ErrorHandler for consistent error reporting
            ErrorHandler.handleAuthError(this, e);

            // Fallback to Sign-in on any error
            Intent fallbackIntent = new Intent(this, SigninActivity.class);
            startActivity(fallbackIntent);

        } finally {
            // Finish this activity so the user can't navigate back to it
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, getString(R.string.log_main_activity_destroyed));
    }
}