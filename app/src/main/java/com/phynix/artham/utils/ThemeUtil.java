package com.phynix.artham.utils;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

/**
 * Shared utility class for resolving theme attribute colors.
 * Extracted from multiple inner classes to avoid code duplication.
 */
public class ThemeUtil {

    /**
     * Resolves a theme attribute (like android.R.attr.textColorPrimary)
     * to an actual color int value from the current theme.
     *
     * @param context The context to resolve the theme from
     * @param attr    The theme attribute resource ID
     * @return The resolved color, or Color.BLACK if context is null
     */
    public static int getThemeAttrColor(Context context, int attr) {
        if (context == null) return Color.BLACK;
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}
