package com.phynix.artham;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Back Button
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // Check for Updates
        TextView checkUpdates = findViewById(R.id.checkUpdates);
        checkUpdates.setOnClickListener(v -> {
            // Opens the app page in the Play Store
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });

        // Privacy Policy Link
        TextView privacyPolicy = findViewById(R.id.privacyPolicy);
        privacyPolicy.setOnClickListener(v -> openWebPage("https://www.google.com/policies/privacy/")); // Replace with your actual URL

        // Terms of Service Link
        TextView termsOfService = findViewById(R.id.termsOfService);
        termsOfService.setOnClickListener(v -> openWebPage("https://www.google.com/policies/terms/")); // Replace with your actual URL

        // Rate App
        TextView rateApp = findViewById(R.id.rateApp);
        rateApp.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        });
    }

    private void openWebPage(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open browser.", Toast.LENGTH_SHORT).show();
        }
    }
}