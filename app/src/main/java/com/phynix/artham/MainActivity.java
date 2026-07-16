package com.phynix.artham;


import com.phynix.artham.activities.HomeActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.utils.ErrorHandler;


/**
 * MainActivity serves as the application entry point and authentication router.
 * This activity determines whether to navigate to SignInActivity or HomeActivity
 * based on the user's authentication status.
 */
public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The theme is applied by MyApplication before this activity starts
        super.onCreate(savedInstanceState);
        // No layout is needed for this router activity

        Log.d(TAG, getString(R.string.log_main_activity_started));

        checkAuthenticationAndNavigate();
    }

    /**
     * Checks the current user's authentication status and navigates to the appropriate activity.
     * Navigates to HomePage if user is authenticated, SignInActivity otherwise.
     */
    private void checkAuthenticationAndNavigate() {
        try {
            Intent navigationIntent;
            String userId = AuthManager.getUserId(this);

            if (!AuthManager.isSignedIn(this)) {
                // No user is signed in, navigate to Sign-in
                Log.d(TAG, getString(R.string.log_no_authenticated_user));
                navigationIntent = new Intent(this, SignInActivity.class);
            } else {
                // User is signed in, navigate to Home
                Log.d(TAG, getString(R.string.log_authenticated_user_found, userId != null ? userId : "unknown"));
                navigationIntent = new Intent(this, HomeActivity.class);
            }

            startActivity(navigationIntent);
            // Optional: Add a fade-in/out transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        } catch (Exception e) {
            Log.e(TAG, getString(R.string.error_authentication_check), e);

            // Use ErrorHandler for consistent error reporting
            ErrorHandler.handleAuthError(this, e);

            // Fallback to Sign-in on any error
            Intent fallbackIntent = new Intent(this, SignInActivity.class);
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