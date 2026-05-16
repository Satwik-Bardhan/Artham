package com.phynix.artham.activities;

import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RadioButton;

import androidx.cardview.widget.CardView;

import com.phynix.artham.utils.FontManager;
import com.phynix.artham.utils.ThemeManager;

/**
 * Allows the user to select the app-wide font family.
 * Mirrors the ThemeSelectionActivity pattern.
 */
public class FontSelectionActivity extends BaseActivity {

    private CardView cardSystemDefault, cardInter, cardPoppins, cardSpartan, cardKhand, cardTinos;
    private RadioButton radioSystemDefault, radioInter, radioPoppins, radioSpartan, radioKhand, radioTinos;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_font_selection);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        setupCurrentState();
        setupClickListeners();
    }

    private void initViews() {
        backButton = findViewById(R.id.back_button);
        cardSystemDefault = findViewById(R.id.cardSystemDefault);
        cardInter = findViewById(R.id.cardInter);
        cardPoppins = findViewById(R.id.cardPoppins);
        cardSpartan = findViewById(R.id.cardSpartan);
        cardKhand = findViewById(R.id.cardKhand);
        cardTinos = findViewById(R.id.cardTinos);
        radioSystemDefault = findViewById(R.id.radioSystemDefault);
        radioInter = findViewById(R.id.radioInter);
        radioPoppins = findViewById(R.id.radioPoppins);
        radioSpartan = findViewById(R.id.radioSpartan);
        radioKhand = findViewById(R.id.radioKhand);
        radioTinos = findViewById(R.id.radioTinos);
    }

    private void setupCurrentState() {
        String currentFont = FontManager.getFont(this);
        clearRadios();

        switch (currentFont) {
            case FontManager.FONT_INTER:
                radioInter.setChecked(true);
                break;
            case FontManager.FONT_POPPINS:
                radioPoppins.setChecked(true);
                break;
            case FontManager.FONT_SPARTAN:
                radioSpartan.setChecked(true);
                break;
            case FontManager.FONT_KHAND:
                radioKhand.setChecked(true);
                break;
            case FontManager.FONT_TINOS:
                radioTinos.setChecked(true);
                break;
            case FontManager.FONT_SYSTEM:
            default:
                radioSystemDefault.setChecked(true);
                break;
        }
    }

    private void clearRadios() {
        radioSystemDefault.setChecked(false);
        radioInter.setChecked(false);
        radioPoppins.setChecked(false);
        radioSpartan.setChecked(false);
        radioKhand.setChecked(false);
        radioTinos.setChecked(false);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cardSystemDefault.setOnClickListener(v -> handleFontChange(FontManager.FONT_SYSTEM));
        cardInter.setOnClickListener(v -> handleFontChange(FontManager.FONT_INTER));
        cardPoppins.setOnClickListener(v -> handleFontChange(FontManager.FONT_POPPINS));
        cardSpartan.setOnClickListener(v -> handleFontChange(FontManager.FONT_SPARTAN));
        cardKhand.setOnClickListener(v -> handleFontChange(FontManager.FONT_KHAND));
        cardTinos.setOnClickListener(v -> handleFontChange(FontManager.FONT_TINOS));
    }

    private void handleFontChange(String newFont) {
        String currentFont = FontManager.getFont(this);

        if (!currentFont.equals(newFont)) {
            // 1. Save the preference
            FontManager.saveFont(this, newFont);

            // 2. Update radio buttons
            clearRadios();
            switch (newFont) {
                case FontManager.FONT_INTER:
                    radioInter.setChecked(true);
                    break;
                case FontManager.FONT_POPPINS:
                    radioPoppins.setChecked(true);
                    break;
                case FontManager.FONT_SPARTAN:
                    radioSpartan.setChecked(true);
                    break;
                case FontManager.FONT_KHAND:
                    radioKhand.setChecked(true);
                    break;
                case FontManager.FONT_TINOS:
                    radioTinos.setChecked(true);
                    break;
                default:
                    radioSystemDefault.setChecked(true);
                    break;
            }

            // 3. Restart app to apply font globally
            ThemeManager.restartApp(this);
        }
    }
}
