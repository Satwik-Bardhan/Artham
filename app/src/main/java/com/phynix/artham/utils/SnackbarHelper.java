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

    /**
     * Shows a Snackbar with an action button (e.g., "UNDO"), anchored above a specific view.
     */
    public static void showWithAction(Activity context, String message,
                                      String actionText, View.OnClickListener actionListener,
                                      View anchorView) {
        if (context == null) return;

        View rootView = context.findViewById(android.R.id.content);
        if (rootView == null) return;

        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.setAction(actionText, actionListener);

        if (anchorView != null) {
            snackbar.setAnchorView(anchorView);
        }

        snackbar.show();
    }
}