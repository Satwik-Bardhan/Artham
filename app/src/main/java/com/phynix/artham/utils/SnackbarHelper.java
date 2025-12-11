package com.phynix.artham.utils;

import android.app.Activity;
import android.view.View;
import com.google.android.material.snackbar.Snackbar;

public class SnackbarHelper {

    /**
     * Shows a Snackbar anchored above a specific view object.
     */
    public static void show(Activity context, String message, View anchorView) {
        if (context == null) return;

        View rootView = context.findViewById(android.R.id.content);
        if (rootView == null) return;

        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT);

        if (anchorView != null) {
            snackbar.setAnchorView(anchorView);
        }

        snackbar.show();
    }

    /**
     * Shows a Snackbar anchored above a view found by its ID.
     */
    public static void show(Activity context, String message, int anchorViewId) {
        if (context == null) return;
        View anchor = context.findViewById(anchorViewId);
        show(context, message, anchor);
    }
}