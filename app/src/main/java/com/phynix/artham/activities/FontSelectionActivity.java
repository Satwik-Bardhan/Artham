package com.phynix.artham.activities;

import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;

import com.phynix.artham.utils.FontManager;
import com.phynix.artham.utils.ThemeManager;

/**
 * Allows the user to select the app-wide font family.
 * Mirrors the ThemeSelectionActivity pattern.
 *
 * Each font card previews its own typeface so users can see
 * what each font looks like before selecting it.
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
        applyFontPreviews();
        setupCurrentState();
        setupClickListeners();
    }

    /**
     * Programmatically applies each font's own Typeface to its card's TextViews.
     * This overrides the global font theme overlay so each card previews its own font.
     */
    private void applyFontPreviews() {
        // System Default — use device default
        applyTypefaceToCard(cardSystemDefault, Typeface.DEFAULT);

        // Each font card gets its own typeface
        Typeface interTf = ResourcesCompat.getFont(this, R.font.inter_regular);
        Typeface poppinsTf = ResourcesCompat.getFont(this, R.font.poppins_regular);
        Typeface spartanTf = ResourcesCompat.getFont(this, R.font.spartan_regular);
        Typeface khandTf = ResourcesCompat.getFont(this, R.font.khand_medium);
        Typeface tinosTf = ResourcesCompat.getFont(this, R.font.tinos_regular);

        applyTypefaceToCard(cardInter, interTf);
        applyTypefaceToCard(cardPoppins, poppinsTf);
        applyTypefaceToCard(cardSpartan, spartanTf);
        applyTypefaceToCard(cardKhand, khandTf);
        applyTypefaceToCard(cardTinos, tinosTf);
    }

    /**
     * Recursively sets the given Typeface on all TextViews within a card,
     * except RadioButtons (which should keep their default look).
     */
    private void applyTypefaceToCard(View view, Typeface typeface) {
        if (typeface == null) return;
        if (view instanceof RadioButton) return; // skip radio buttons
        if (view instanceof TextView) {
            ((TextView) view).setTypeface(typeface, ((TextView) view).getTypeface().getStyle());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTypefaceToCard(group.getChildAt(i), typeface);
            }
        }
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
