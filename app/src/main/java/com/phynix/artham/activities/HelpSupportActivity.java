package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.phynix.artham.utils.ThemeManager;

// [FIX] Import the CategoryActivity from its new sub-package


/**
 * Artham Help & Support Activity
 * Handles support emails and quick actions like reporting bugs or viewing creator info.
 */
public class HelpSupportActivity extends BaseActivity {

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
        setupSupportCreator();
        setupSearchFilter();
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

        // Contact Us
        if (btnContactUs != null) {
            btnContactUs.setOnClickListener(v -> sendEmail("arthamhq@gmail.com", "Support Request: Artham App"));
        }

        // Report Bug
        if (btnReportBug != null) {
            btnReportBug.setOnClickListener(v -> sendEmail("arthamhq@gmail.com", "Bug Report: Artham App"));
        }
    }

    /**
     * Maps FAQ containers to destination Activities for deep-linking.
     */
    private void setupFaqListeners() {
        setRedirect(R.id.faqAllTransactions, TransactionActivity.class);
        setRedirect(R.id.faqAnalytics, ExpenseAnalyticsActivity.class);
        setRedirect(R.id.faqFilters, SearchFiltersActivity.class);
        setRedirect(R.id.faqAddTransaction, CashInOutActivity.class);
        setRedirect(R.id.faqDownload, TransactionExportActivity.class);
        setRedirect(R.id.faqManageCategory, CategoryActivity.class);
        setRedirect(R.id.faqSwitchBook, CashbookSwitchActivity.class);
        setRedirect(R.id.faqEditProfile, EditProfileActivity.class);
        setRedirect(R.id.faqSecuritySettings, SettingsActivity.class);
        setRedirect(R.id.faqDeleteData, SettingsActivity.class);
        setRedirect(R.id.faqThemeSelection, ThemeSelectionActivity.class);
        setRedirect(R.id.faqThemeSync, AppSettingsActivity.class);
        setRedirect(R.id.faqAboutDev, AboutActivity.class);
        setRedirect(R.id.faqRecurringTransactions, CashInOutActivity.class);
        setRedirect(R.id.faqEditTransaction, TransactionActivity.class);
        setRedirect(R.id.faqOfflineMode, SettingsActivity.class);
        setRedirect(R.id.faqHomeWidget, SettingsActivity.class);
    }

    private void setRedirect(int viewId, Class<?> destinationClass) {
        LinearLayout layout = findViewById(viewId);
        if (layout != null) {
            layout.setOnClickListener(v -> {
                Intent intent = new Intent(HelpSupportActivity.this, destinationClass);

                // Add cashbook_id if navigating to CategoryActivity, as it expects it
                if (destinationClass == CategoryActivity.class) {
                    if (com.phynix.artham.auth.AuthManager.isSignedIn(HelpSupportActivity.this)) {
                        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                        String currentCashbookId = prefs.getString("active_cashbook_id_" + com.phynix.artham.auth.AuthManager.getUserId(HelpSupportActivity.this), "");
                        if (!currentCashbookId.isEmpty()) {
                            intent.putExtra("cashbook_id", currentCashbookId);
                        }
                    }
                }

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

    /**
     * Sets up the "Enjoying Artham?" support card at the bottom of the FAQ section.
     */
    private void setupSupportCreator() {
        android.view.View btnSupport = findViewById(R.id.btnSupportCreatorFaq);
        if (btnSupport != null) {
            btnSupport.setOnClickListener(v -> showSupportDialog());
        }
    }

    /**
     * Shows the "Buy Me a Coffee" center dialog with UPI payment tiers.
     */
    private void showSupportDialog() {
        final String[] chaiMessages = {
                "Here's a chai for building something I love ☕",
                "Keep going! This chai is on me 💛",
                "A small chai for the genius behind Artham 🫖",
                "You deserve a chai break — thank you! ☕",
                "Artham makes my life easier, enjoy this chai 💛",
                "One chai to say thanks for everything ☕🙏",
                "Cheers to you with a warm cutting chai 🫖",
                "Artham is amazing — grab a chai on me! ☕",
                "Just a chai to show some love for your work 💛",
                "Thanks for Artham! Hope this chai makes your day ☕"
        };
        final String[] coffeeMessages = {
                "Grab a coffee, you've earned it! ☕",
                "Your hard work deserves a good coffee 💛",
                "Coffee for the late nights you put into Artham 🌙",
                "Thanks for making finance easy — coffee's on me ☕",
                "Keep shipping updates, keep sipping coffee 💛",
                "A coffee to fuel your next great feature ☕✨",
                "You built something special — enjoy this coffee 🍵",
                "Artham saved me time, this coffee saves your energy 💛",
                "Cheers to the dev who made money tracking fun ☕",
                "One coffee for one amazing developer — thank you! 💛"
        };
        final String[] bobaMessages = {
                "You deserve a boba tea for this amazing app 🧋🔥",
                "Boba tea for the legend behind Artham 💛",
                "Take a break and sip some boba — you've earned it! 🧋",
                "Artham is top-tier, just like this boba tea 🏆",
                "A boba tea to say thank you for everything 🧋💛",
                "Best app deserves the best drink — enjoy! 🧋",
                "You made my finances stress-free, boba's on me 💛",
                "Hats off to you — grab a boba, champ 🧋👑",
                "Artham is a masterpiece, this boba is my applause 🧋",
                "Here's a boba tea because you're truly awesome 💛🔥"
        };

        java.util.Random random = new java.util.Random();

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_support_creator);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        com.phynix.artham.utils.DialogUtils.applyBlurEffect(dialog, this);

        String upiId = "9777122601@uboi";
        String upiName = "Satwik Bardhan Behera";

        dialog.findViewById(R.id.optionChai).setOnClickListener(v -> {
            dialog.dismiss();
            launchUpiPayment(upiId, upiName, "19", chaiMessages[random.nextInt(chaiMessages.length)]);
        });

        dialog.findViewById(R.id.optionCoffee).setOnClickListener(v -> {
            dialog.dismiss();
            launchUpiPayment(upiId, upiName, "49", coffeeMessages[random.nextInt(coffeeMessages.length)]);
        });

        dialog.findViewById(R.id.optionBoba).setOnClickListener(v -> {
            dialog.dismiss();
            launchUpiPayment(upiId, upiName, "199", bobaMessages[random.nextInt(bobaMessages.length)]);
        });

        dialog.show();
    }

    private void launchUpiPayment(String upiId, String name, String amount, String note) {
        Uri uri = Uri.parse("upi://pay")
                .buildUpon()
                .appendQueryParameter("pa", upiId)
                .appendQueryParameter("pn", name)
                .appendQueryParameter("tn", note)
                .appendQueryParameter("am", amount)
                .appendQueryParameter("cu", "INR")
                .build();

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(Intent.createChooser(intent, "Pay with..."));
        } catch (Exception e) {
            Toast.makeText(this, "No UPI app found on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sets up the search bar to dynamically filter FAQ items.
     * Searches through child TextViews of each FAQ LinearLayout item.
     */
    private void setupSearchFilter() {
        EditText searchEditText = findViewById(R.id.helpSearchEditText);
        ImageView clearButton = findViewById(R.id.btnClearHelpSearch);
        View faqLayout = findViewById(R.id.faqLayout);

        if (searchEditText == null || faqLayout == null) return;

        // Collect all FAQ item views (LinearLayouts with IDs like faqAllTransactions, etc.)
        int[] faqIds = {
                R.id.faqAllTransactions, R.id.faqAnalytics, R.id.faqFilters,
                R.id.faqAddTransaction, R.id.faqDownload, R.id.faqManageCategory,
                R.id.faqSwitchBook, R.id.faqEditProfile, R.id.faqSecuritySettings,
                R.id.faqDeleteData, R.id.faqThemeSelection, R.id.faqThemeSync,
                R.id.faqAboutDev, R.id.faqRecurringTransactions, R.id.faqEditTransaction,
                R.id.faqOfflineMode, R.id.faqHomeWidget
        };

        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                searchEditText.setText("");
                clearButton.setVisibility(View.GONE);
            });
        }

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();

                if (clearButton != null) {
                    clearButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }

                for (int id : faqIds) {
                    View faqItem = findViewById(id);
                    if (faqItem == null) continue;

                    if (query.isEmpty()) {
                        faqItem.setVisibility(View.VISIBLE);
                        continue;
                    }

                    // Search through all TextViews in this FAQ item
                    String itemText = extractText(faqItem).toLowerCase();
                    faqItem.setVisibility(itemText.contains(query) ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Recursively extract all text from TextViews within a ViewGroup.
     */
    private String extractText(View view) {
        StringBuilder sb = new StringBuilder();
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) sb.append(text).append(" ");
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                sb.append(extractText(vg.getChildAt(i)));
            }
        }
        return sb.toString();
    }
}