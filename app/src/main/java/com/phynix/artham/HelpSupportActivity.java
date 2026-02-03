package com.phynix.artham;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.phynix.artham.utils.ThemeManager;

/**
 * Artham Help & Support Activity
 * Handles support emails and quick actions like reporting bugs or viewing creator info.
 */
public class HelpSupportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply activity-specific theme before super.onCreate
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setupHeader();
        setupQuickActions();
        setupFaqListeners();
    }

    private void setupHeader() {
        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    /**
     * Initializes quick action buttons. Redundant FAQ button logic has been removed
     * as the FAQ section is now directly accessible via scrolling.
     */
    private void setupQuickActions() {
        View btnContactUs = findViewById(R.id.btnContactUs);
        View btnReportBug = findViewById(R.id.btnReportBug);
        View btnCreator = findViewById(R.id.btnAboutDev);

        // Contact Us
        if (btnContactUs != null) {
            btnContactUs.setOnClickListener(v -> sendEmail("support@artham.com", "Support Request: Artham App"));
        }

        // Report Bug
        if (btnReportBug != null) {
            btnReportBug.setOnClickListener(v -> sendEmail("bugs@artham.com", "Bug Report: Artham App"));
        }

        // Creator Profile
        if (btnCreator != null) {
            btnCreator.setOnClickListener(v -> {
                Intent intent = new Intent(HelpSupportActivity.this, AboutActivity.class);
                startActivity(intent);
            });
        }
    }

    /**
     * Maps FAQ containers to destination Activities for deep-linking.
     */
    private void setupFaqListeners() {
        setRedirect(R.id.faqAllTransactions, TransactionActivity.class);
        setRedirect(R.id.faqAnalytics, ExpenseAnalyticsActivity.class);
        setRedirect(R.id.faqFilters, FiltersActivity.class);
        setRedirect(R.id.faqManageCategory, CategoryActivity.class);
        setRedirect(R.id.faqSwitchBook, CashbookSwitchActivity.class);
        setRedirect(R.id.faqEditProfile, EditProfileActivity.class);
        setRedirect(R.id.faqSecuritySettings, SettingsActivity.class);
        setRedirect(R.id.faqDeleteData, SettingsActivity.class);
        setRedirect(R.id.faqThemeSync, AppSettingsActivity.class);
        setRedirect(R.id.faqAboutDev, AboutActivity.class);
    }

    private void setRedirect(int viewId, Class<?> destinationClass) {
        LinearLayout layout = findViewById(viewId);
        if (layout != null) {
            layout.setOnClickListener(v -> {
                Intent intent = new Intent(HelpSupportActivity.this, destinationClass);
                startActivity(intent);
            });
        }
    }

    private void sendEmail(String recipient, String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
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