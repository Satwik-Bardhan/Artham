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

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3000; // 3 seconds
    private ProgressBar splashProgress;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_splash);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();


        // Initialize and show progress bar
        splashProgress = findViewById(R.id.splash_progress);
        splashProgress.setVisibility(View.VISIBLE);

        // Use a Handler to delay the next action
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check if a user is already signed in
            FirebaseUser currentUser = mAuth.getCurrentUser();

            // Decide which activity to launch
            if (currentUser != null) {
                // User is signed in, go to HomePage
                launchActivity(HomePage.class);
            } else {
                // No user is signed in, go to SigninActivity
                launchActivity(SigninActivity.class);
            }
        }, SPLASH_DELAY);
    }

    private void launchActivity(Class<?> activityClass) {
        // Hide progress bar before transitioning
        splashProgress.setVisibility(View.GONE);

        // Start the determined activity
        Intent intent = new Intent(SplashActivity.this, activityClass);
        startActivity(intent);


        // Finish this splash activity so the user can't navigate back to it
        finish();
    }
}