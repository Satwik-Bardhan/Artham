package com.phynix.artham.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.phynix.artham.R;

/**
 * Manages the user's font preference across the entire app.
 *
 * Approach: Since Android's theme-level fontFamily is baked at inflation time
 * and can't be swapped dynamically without recreating, we apply fonts
 * programmatically via a view-tree walk after setContentView().
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
     * Returns the Typeface for the given font key.
     * Returns null for FONT_SYSTEM (which means Android default).
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

    /**
     * Returns the bold variant Typeface for the given font key.
     */
    public static Typeface getBoldTypeface(Context context, String fontKey) {
        try {
            switch (fontKey) {
                case FONT_INTER:
                    return ResourcesCompat.getFont(context, R.font.inter_bold);
                case FONT_POPPINS:
                    return ResourcesCompat.getFont(context, R.font.poppins_bold);
                case FONT_SPARTAN:
                    return ResourcesCompat.getFont(context, R.font.spartan_bold);
                case FONT_KHAND:
                    return ResourcesCompat.getFont(context, R.font.khand_medium);
                case FONT_TINOS:
                    return ResourcesCompat.getFont(context, R.font.tinos_bold);
                case FONT_SYSTEM:
                default:
                    return Typeface.DEFAULT_BOLD;
            }
        } catch (Exception e) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    /**
     * Applies the user's selected font to ALL TextViews in the given Activity's
     * view hierarchy. Call this AFTER setContentView().
     */
    public static void applyFontToActivity(Activity activity) {
        String fontKey = getFont(activity);
        Typeface regular = getTypeface(activity, fontKey);
        Typeface bold = getBoldTypeface(activity, fontKey);
        if (regular == null) return;

        View rootView = activity.getWindow().getDecorView().getRootView();
        applyFontToViewTree(rootView, regular, bold);
    }

    /**
     * Recursively walks the view tree and sets the Typeface on every TextView.
     * Preserves the original bold/italic style.
     */
    private static void applyFontToViewTree(View view, Typeface regular, Typeface bold) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int style = tv.getTypeface() != null ? tv.getTypeface().getStyle() : Typeface.NORMAL;
            boolean isBold = (style & Typeface.BOLD) != 0;
            tv.setTypeface(isBold ? bold : regular, style);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyFontToViewTree(group.getChildAt(i), regular, bold);
            }
        }
    }
}
