package com.phynix.artham;


import com.phynix.artham.activities.HomeActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.utils.ThemeManager;

public class SplashActivity extends BaseActivity {

    // Changed from 3000 to 2000 for a 2-second delay
    private static final int SPLASH_DELAY = 2000;
    private ProgressBar splashProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply the USER'S selected theme (Light, Dark, or Purple)
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        splashProgress = findViewById(R.id.progressBar);

        if (splashProgress != null) {
            splashProgress.setVisibility(View.VISIBLE);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (AuthManager.isSignedIn(this)) {
                launchActivity(HomeActivity.class);
            } else {
                launchActivity(SignInActivity.class);
            }
        }, SPLASH_DELAY);
    }

    private void launchActivity(Class<?> activityClass) {
        if (splashProgress != null) {
            splashProgress.setVisibility(View.GONE);
        }
        Intent intent = new Intent(SplashActivity.this, activityClass);
        startActivity(intent);
        finish();
    }
}