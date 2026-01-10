package com.phynix.artham;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

public class HelpSupportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Back Button
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // Quick Action Buttons
        View btnFAQ = findViewById(R.id.btnFAQ);
        View btnContactUs = findViewById(R.id.btnContactUs);
        View btnReportBug = findViewById(R.id.btnReportBug);

        // Scroll View and Target Layout for Auto-Scrolling
        NestedScrollView scrollView = findViewById(R.id.scrollView);
        View faqLayout = findViewById(R.id.faqLayout);

        // FAQ Button: Smooth scroll to questions
        btnFAQ.setOnClickListener(v -> {
            if (scrollView != null && faqLayout != null) {
                scrollView.smoothScrollTo(0, faqLayout.getTop());
            }
        });

        // Contact Us Button - [FIX] Updated to support@artham.com
        btnContactUs.setOnClickListener(v -> sendEmail("support@artham.com", "Support Request: Artham App"));

        // Report Bug Button - [FIX] Updated to bugs@artham.com
        btnReportBug.setOnClickListener(v -> sendEmail("bugs@artham.com", "Bug Report: Artham App"));
    }

    private void sendEmail(String recipient, String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // Only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, "Please describe the issue you are facing...\n\n");

        try {
            startActivity(Intent.createChooser(intent, "Send Email via..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }
}