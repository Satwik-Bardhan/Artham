package com.phynix.artham;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.utils.ThemeManager;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3000;
    private ProgressBar splashProgress;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply the USER'S selected theme (Light, Dark, or Purple)
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();
        splashProgress = findViewById(R.id.progressBar);

        if (splashProgress != null) {
            splashProgress.setVisibility(View.VISIBLE);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                launchActivity(HomePage.class);
            } else {
                launchActivity(SigninActivity.class);
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