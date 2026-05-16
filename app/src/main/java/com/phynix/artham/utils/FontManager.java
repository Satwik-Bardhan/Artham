package com.phynix.artham.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;

import com.phynix.artham.R;

/**
 * Manages the user's font preference across the entire app.
 *
 * Approach: Uses Android theme overlays — the selected font is applied to the
 * Activity's theme BEFORE views are inflated (via applyFontOverlay), so ALL views
 * including RecyclerView items, dialogs, and dynamically inflated layouts
 * automatically inherit the correct font. No view-tree walking needed.
 *
 * This mirrors the ThemeManager pattern (SharedPreferences-based).
 */
public class FontManager {

    // Font keys — stored in SharedPreferences
    public static final String FONT_SYSTEM = "system_default";
    public static final String FONT_INTER = "inter";
    public static final String FONT_POPPINS = "poppins";
    public static final String FONT_SPARTAN = "spartan";
    public static final String FONT_KHAND = "khand";
    public static final String FONT_TINOS = "tinos";

    private static final String PREF_NAME = "app_font";
    private static final String KEY_FONT = "selected_font";

    /**
     * Saves the user's font selection to SharedPreferences.
     */
    public static void saveFont(Context context, String fontKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FONT, fontKey).apply();
    }

    /**
     * Returns the currently saved font key.
     * Default is FONT_SYSTEM (device default).
     */
    public static String getFont(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_FONT, FONT_SYSTEM);
    }

    /**
     * Returns a human-readable display name for the given font key.
     */
    public static String getDisplayName(String fontKey) {
        switch (fontKey) {
            case FONT_INTER:
                return "Inter";
            case FONT_POPPINS:
                return "Poppins";
            case FONT_SPARTAN:
                return "Spartan";
            case FONT_KHAND:
                return "Khand";
            case FONT_TINOS:
                return "Tinos";
            case FONT_SYSTEM:
            default:
                return "System Default";
        }
    }

    /**
     * Returns the R.style resource for the font overlay corresponding to the given key.
     * Returns 0 for FONT_SYSTEM (no overlay needed — system default).
     */
    public static int getFontOverlayStyle(String fontKey) {
        switch (fontKey) {
            case FONT_INTER:
                return R.style.FontOverlay_Inter;
            case FONT_POPPINS:
                return R.style.FontOverlay_Poppins;
            case FONT_SPARTAN:
                return R.style.FontOverlay_Spartan;
            case FONT_KHAND:
                return R.style.FontOverlay_Khand;
            case FONT_TINOS:
                return R.style.FontOverlay_Tinos;
            case FONT_SYSTEM:
            default:
                return 0;
        }
    }

    /**
     * Applies the user's selected font as a theme overlay on the Activity.
     * Must be called BEFORE super.onCreate() / setContentView() so that all
     * inflated views (including RecyclerView items) inherit the font.
     *
     * Call this right after ThemeManager.applyActivityTheme().
     */
    public static void applyFontOverlay(Activity activity) {
        String fontKey = getFont(activity);
        int overlayStyle = getFontOverlayStyle(fontKey);
        if (overlayStyle != 0) {
            activity.getTheme().applyStyle(overlayStyle, true);
        }
        // If FONT_SYSTEM (overlayStyle == 0), no overlay is applied.
        // The theme has no fontFamily set, so Android uses the system default.
    }

    /**
     * Returns the Typeface for the given font key (for programmatic use).
     */
    public static Typeface getTypeface(Context context, String fontKey) {
        try {
            switch (fontKey) {
                case FONT_INTER:
                    return ResourcesCompat.getFont(context, R.font.app_font);
                case FONT_POPPINS:
                    return ResourcesCompat.getFont(context, R.font.poppins_regular);
                case FONT_SPARTAN:
                    return ResourcesCompat.getFont(context, R.font.spartan_regular);
                case FONT_KHAND:
                    return ResourcesCompat.getFont(context, R.font.khand_medium);
                case FONT_TINOS:
                    return ResourcesCompat.getFont(context, R.font.tinos_regular);
                case FONT_SYSTEM:
                default:
                    return Typeface.DEFAULT;
            }
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }
}
