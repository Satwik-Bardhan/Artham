package com.phynix.artham;

import android.content.SharedPreferences;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Base activity that provides global haptic feedback on every clickable element tap.
 * All activities in the app should extend this instead of AppCompatActivity.
 *
 * Font handling is done via theme overlays in ThemeManager.applyActivityTheme(),
 * which applies the font to the theme BEFORE views are inflated.
 *
 * How it works:
 * - Intercepts all touch events via dispatchTouchEvent()
 * - On ACTION_DOWN, checks if the touched view is clickable
 * - If so, triggers a light haptic tick for tactile feedback
 * - Can be disabled by the user via App Settings → Haptic Feedback toggle
 */
public class BaseActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_HAPTIC = "haptic_feedback_enabled";

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Check user preference — haptic is ON by default
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (prefs.getBoolean(KEY_HAPTIC, true)) {
                View touchedView = findViewAtPosition(
                        getWindow().getDecorView(),
                        (int) event.getRawX(),
                        (int) event.getRawY()
                );
                if (touchedView != null && (touchedView.isClickable() || touchedView.isLongClickable())) {
                    touchedView.performHapticFeedback(
                            HapticFeedbackConstants.CLOCK_TICK,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    );
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Recursively finds the deepest view at the given screen coordinates.
     * Returns the most specific (deepest) clickable view under the touch point.
     */
    private View findViewAtPosition(View parent, int x, int y) {
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            // Iterate in reverse to match Android's drawing order (top-most first)
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;

                int[] location = new int[2];
                child.getLocationOnScreen(location);

                if (x >= location[0] && x < location[0] + child.getWidth()
                        && y >= location[1] && y < location[1] + child.getHeight()) {
                    View result = findViewAtPosition(child, x, y);
                    if (result != null && (result.isClickable() || result.isLongClickable())) {
                        return result;
                    }
                    // If the child itself is clickable, return it
                    if (child.isClickable() || child.isLongClickable()) {
                        return child;
                    }
                }
            }
        }
        // Check the parent itself
        if (parent.isClickable() || parent.isLongClickable()) {
            return parent;
        }
        return null;
    }
}
