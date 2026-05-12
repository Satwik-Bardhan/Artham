package com.phynix.artham.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;

/**
 * Utility to position and animate the pill indicator in the bottom navigation.
 *
 * Since each tab is a separate Activity, cross-activity sliding would always lag.
 * Instead, we use an instant position + subtle scale-pop animation for a snappy,
 * premium feel without any jank.
 */
public class NavPillAnimator {

    // Tab index constants
    public static final int TAB_CASHBOOKS = 0;
    public static final int TAB_HOME = 1;
    public static final int TAB_TRANSACTIONS = 2;
    public static final int TAB_SETTINGS = 3;

    public static final String EXTRA_PREVIOUS_TAB = "nav_previous_tab";

    /**
     * Positions the pill at the target container and plays a quick scale-pop animation.
     * This is the primary method — used for all tab switches.
     */
    public static void positionAt(View pill, View targetContainer) {
        targetContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        targetContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        alignPill(pill, targetContainer);

                        // Start invisible, then pop in
                        pill.setScaleX(0.5f);
                        pill.setScaleY(0.5f);
                        pill.setAlpha(0f);
                        pill.setVisibility(View.VISIBLE);

                        // Scale + fade in animation (150ms — fast & snappy)
                        AnimatorSet animSet = new AnimatorSet();
                        animSet.playTogether(
                                ObjectAnimator.ofFloat(pill, "scaleX", 0.5f, 1f),
                                ObjectAnimator.ofFloat(pill, "scaleY", 0.5f, 1f),
                                ObjectAnimator.ofFloat(pill, "alpha", 0f, 1f)
                        );
                        animSet.setDuration(150);
                        animSet.setInterpolator(new OvershootInterpolator(1.2f));
                        animSet.start();
                    }
                }
        );
    }

    /**
     * Kept for backward compatibility — now just delegates to positionAt
     * since cross-activity sliding always causes jank.
     */
    public static void slideFromTo(View pill, View fromContainer, View toContainer) {
        positionAt(pill, toContainer);
    }

    /**
     * Aligns the pill exactly over the target container (centered).
     */
    private static void alignPill(View pill, View targetContainer) {
        View parent = (View) pill.getParent();

        int[] parentLoc = new int[2];
        int[] targetLoc = new int[2];
        parent.getLocationOnScreen(parentLoc);
        targetContainer.getLocationOnScreen(targetLoc);

        float x = targetLoc[0] - parentLoc[0] + (targetContainer.getWidth() - pill.getWidth()) / 2f;
        float y = targetLoc[1] - parentLoc[1] + (targetContainer.getHeight() - pill.getHeight()) / 2f;

        pill.setX(x);
        pill.setY(y);
    }

    /**
     * Returns the pill container (FrameLayout wrapping the icon) for a given tab index.
     */
    public static View getPillContainerForTab(View navRoot, int tabIndex) {
        switch (tabIndex) {
            case TAB_CASHBOOKS:
                return navRoot.findViewById(com.phynix.artham.R.id.pillContainerCashbook);
            case TAB_HOME:
                return navRoot.findViewById(com.phynix.artham.R.id.pillContainerHome);
            case TAB_TRANSACTIONS:
                return navRoot.findViewById(com.phynix.artham.R.id.pillContainerTransactions);
            case TAB_SETTINGS:
                return navRoot.findViewById(com.phynix.artham.R.id.pillContainerSettings);
            default:
                return navRoot.findViewById(com.phynix.artham.R.id.pillContainerHome);
        }
    }
}
