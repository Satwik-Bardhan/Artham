package com.phynix.artham;

import android.content.SharedPreferences;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Base activity that provides global haptic feedback on click/tap only.
 * All activities in the app should extend this instead of AppCompatActivity.
 *
 * Font handling is done via theme overlays in ThemeManager.applyActivityTheme(),
 * which applies the font to the theme BEFORE views are inflated.
 *
 * How it works:
 * - Intercepts all touch events via dispatchTouchEvent()
 * - On ACTION_DOWN, records the initial touch position
 * - On ACTION_UP, checks if the finger stayed within touch slop (genuine click)
 * - Only then triggers a light haptic tick — scrolls and swipes are ignored
 * - Can be disabled by the user via App Settings → Haptic Feedback toggle
 */
public class BaseActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_HAPTIC = "haptic_feedback_enabled";

    /**
     * Returns true if this Activity is still alive (not finishing or destroyed).
     * Use this guard at the top of all async callbacks before updating UI.
     */
    protected boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    /**
     * Wraps a DataRepository callback with a lifecycle guard.
     * If the activity is finishing or destroyed when the callback fires, it's silently skipped.
     * Usage: repository.getCashbooks(safeCallback(data -> { ... }), ...);
     */
    protected <T> com.phynix.artham.db.DataRepository.DataCallback<T> safeCallback(
            com.phynix.artham.db.DataRepository.DataCallback<T> callback) {
        return data -> {
            if (isAlive()) {
                callback.onCallback(data);
            }
        };
    }

    /**
     * Wraps a DataRepository error callback with a lifecycle guard.
     */
    protected com.phynix.artham.db.DataRepository.ErrorCallback safeError(
            com.phynix.artham.db.DataRepository.ErrorCallback callback) {
        return error -> {
            if (isAlive()) {
                callback.onError(error);
            }
        };
    }

    // Track the initial touch position to distinguish clicks from scrolls/swipes
    private float downX, downY;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Record where the finger went down
                downX = event.getRawX();
                downY = event.getRawY();
                break;

            case MotionEvent.ACTION_UP:
                // Only fire haptic if the finger didn't move far (i.e. it was a click, not a scroll/swipe)
                float dx = Math.abs(event.getRawX() - downX);
                float dy = Math.abs(event.getRawY() - downY);
                int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

                if (dx <= touchSlop && dy <= touchSlop) {
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
                break;
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
