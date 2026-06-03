package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.phynix.artham.utils.ThemeManager;

import java.util.Random;

public class AboutActivity extends BaseActivity {

    private static final String UPI_ID = "9777122601@okaxis";
    private static final String UPI_NAME = "Satwik Bardhan Behera";
    private static final Random random = new Random();

    private static final String[] CHAI_MESSAGES = {
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

    private static final String[] COFFEE_MESSAGES = {
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

    private static final String[] BOBA_MESSAGES = {
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

        // Support the Creator
        View btnSupportCreator = findViewById(R.id.btnSupportCreator);
        if (btnSupportCreator != null) {
            btnSupportCreator.setOnClickListener(v -> showSupportDialog());
        }
    }

    /**
     * Shows the "Buy Me a Coffee" center dialog with UPI payment tiers.
     */
    public void showSupportDialog() {
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

        // Chai ₹19
        dialog.findViewById(R.id.optionChai).setOnClickListener(v -> {
            dialog.dismiss();
            launchUpiPayment("19", CHAI_MESSAGES[random.nextInt(CHAI_MESSAGES.length)]);
        });

        // Coffee ₹49
        dialog.findViewById(R.id.optionCoffee).setOnClickListener(v -> {
            dialog.dismiss();
            launchUpiPayment("49", COFFEE_MESSAGES[random.nextInt(COFFEE_MESSAGES.length)]);
        });

        // Boba Tea ₹199
        dialog.findViewById(R.id.optionBoba).setOnClickListener(v -> {
            dialog.dismiss();
            launchUpiPayment("199", BOBA_MESSAGES[random.nextInt(BOBA_MESSAGES.length)]);
        });

        dialog.show();
    }

    /**
     * Launches a UPI payment intent with the given amount and a random appreciation note.
     */
    private void launchUpiPayment(String amount, String note) {
        Uri uri = Uri.parse("upi://pay")
                .buildUpon()
                .appendQueryParameter("pa", UPI_ID)
                .appendQueryParameter("pn", UPI_NAME)
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

    private void openWebPage(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open browser.", Toast.LENGTH_SHORT).show();
        }
    }
}