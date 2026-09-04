package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.phynix.artham.utils.ThemeManager;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);

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
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });

        // Privacy Policy Link
        TextView privacyPolicy = findViewById(R.id.privacyPolicy);
        privacyPolicy.setOnClickListener(v -> openWebPage("https://www.google.com/policies/privacy/"));

        // Terms of Service Link
        TextView termsOfService = findViewById(R.id.termsOfService);
        termsOfService.setOnClickListener(v -> openWebPage("https://www.google.com/policies/terms/"));

        // Rate App
        TextView rateApp = findViewById(R.id.rateApp);
        rateApp.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        });

        // Developer LinkedIn
        TextView developerName = findViewById(R.id.developerName);
        developerName.setOnClickListener(v -> openWebPage("https://www.linkedin.com/in/satwik-bardhan/"));
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