package com.phynix.artham.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.phynix.artham.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen overlay that shows a spotlight + tooltip for onboarding.
 *
 * Draws a dark scrim over the activity, cuts out a rounded-rect spotlight
 * around the target view, and positions a tooltip card near the spotlight.
 *
 * Usage:
 *   OnboardingOverlay.builder(activity)
 *       .addStep(R.id.myView, "Title", "Description")
 *       .addStep(R.id.otherView, "Title2", "Desc2")
 *       .setOnCompleteListener(() -> { ... })
 *       .start();
 */
public class OnboardingOverlay extends FrameLayout {

    // ─── Scrim & spotlight config ────────────────────────────────────
    private static final int SCRIM_COLOR = 0xD9000000; // 85% black — much darker for clear contrast
    private static final float SPOTLIGHT_CORNER_RADIUS_DP = 12f;
    private static final float SPOTLIGHT_PADDING_DP = 10f;
    private static final float SPOTLIGHT_BORDER_WIDTH_DP = 2.5f;

    // ─── Tooltip config ──────────────────────────────────────────────
    private static final float TOOLTIP_MARGIN_DP = 16f;
    private static final float TOOLTIP_CORNER_RADIUS_DP = 20f;
    private static final float TOOLTIP_PADDING_DP = 20f;
    private static final float TOOLTIP_MAX_WIDTH_DP = 320f;

    // ─── State ───────────────────────────────────────────────────────
    private final Activity activity;
    private final List<OnboardingStep> steps;
    private int currentStepIndex = 0;
    private Runnable onCompleteListener;

    // ─── Drawing ─────────────────────────────────────────────────────
    private final Paint scrimPaint;
    private final Paint clearPaint;
    private final Paint borderPaint;
    private final Paint glowPaint;
    private final RectF spotlightRect = new RectF();
    private float spotlightCornerRadius;
    private float spotlightPadding;

    // ─── Tooltip views ───────────────────────────────────────────────
    private LinearLayout tooltipCard;
    private TextView tooltipTitle;
    private TextView tooltipDescription;
    private TextView tooltipStepCounter;
    private TextView btnSkip;
    private TextView btnNext;

    // ─── Step model ──────────────────────────────────────────────────
    public static class OnboardingStep {
        public final int targetViewId;
        public final String title;
        public final String description;

        public OnboardingStep(int targetViewId, String title, String description) {
            this.targetViewId = targetViewId;
            this.title = title;
            this.description = description;
        }
    }

    // ─── Builder ─────────────────────────────────────────────────────
    public static class Builder {
        private final Activity activity;
        private final List<OnboardingStep> steps = new ArrayList<>();
        private Runnable onCompleteListener;

        public Builder(Activity activity) {
            this.activity = activity;
        }

        public Builder addStep(int targetViewId, String title, String description) {
            steps.add(new OnboardingStep(targetViewId, title, description));
            return this;
        }

        public Builder setOnCompleteListener(Runnable listener) {
            this.onCompleteListener = listener;
            return this;
        }

        public void start() {
            if (steps.isEmpty() || activity == null || activity.isFinishing() || activity.isDestroyed())
                return;

            OnboardingOverlay overlay = new OnboardingOverlay(activity, steps);
            overlay.onCompleteListener = onCompleteListener;

            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            decorView.addView(overlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            overlay.showStep(0);
        }
    }

    public static Builder builder(Activity activity) {
        return new Builder(activity);
    }

    // ─── Constructor ─────────────────────────────────────────────────
    private OnboardingOverlay(Activity activity, List<OnboardingStep> steps) {
        super(activity);
        this.activity = activity;
        this.steps = steps;

        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setElevation(dpToPx(24));

        // Layer type for clear paint to work (cuts hole in scrim)
        setLayerType(LAYER_TYPE_HARDWARE, null);

        scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scrimPaint.setColor(SCRIM_COLOR);
        scrimPaint.setStyle(Paint.Style.FILL);

        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // Bright border around the spotlight cutout
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0xCCFFFFFF); // bright white, 80% opacity
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(SPOTLIGHT_BORDER_WIDTH_DP));

        // Subtle outer glow around spotlight
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(0x40FFFFFF); // 25% white
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dpToPx(SPOTLIGHT_BORDER_WIDTH_DP * 3));

        spotlightCornerRadius = dpToPx(SPOTLIGHT_CORNER_RADIUS_DP);
        spotlightPadding = dpToPx(SPOTLIGHT_PADDING_DP);

        buildTooltipCard();

        // Intercept all touches to prevent interaction with underlying views
        setOnClickListener(v -> { /* absorb */ });
    }

    // ─── Build the tooltip UI programmatically ───────────────────────
    private void buildTooltipCard() {
        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        // Root card
        tooltipCard = new LinearLayout(ctx);
        tooltipCard.setOrientation(LinearLayout.VERTICAL);
        tooltipCard.setBackgroundResource(R.drawable.bg_spotlight_tooltip);
        int pad = (int) dpToPx(TOOLTIP_PADDING_DP);
        tooltipCard.setPadding(pad, pad, pad, pad);
        tooltipCard.setElevation(dpToPx(8));
        tooltipCard.setVisibility(INVISIBLE);

        // Step counter (e.g., "Step 1 of 5")
        tooltipStepCounter = new TextView(ctx);
        tooltipStepCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tooltipStepCounter.setTextColor(resolveThemeColor(R.attr.chk_textColorHint));
        tooltipStepCounter.setLetterSpacing(0.06f);
        tooltipCard.addView(tooltipStepCounter);

        // Title
        tooltipTitle = new TextView(ctx);
        tooltipTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tooltipTitle.setTextColor(resolveThemeColor(R.attr.chk_textColorPrimary));
        tooltipTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = (int) (6 * density);
        tooltipCard.addView(tooltipTitle, titleParams);

        // Description
        tooltipDescription = new TextView(ctx);
        tooltipDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tooltipDescription.setTextColor(resolveThemeColor(R.attr.chk_textColorSecondary));
        tooltipDescription.setLineSpacing(3 * density, 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = (int) (6 * density);
        tooltipCard.addView(tooltipDescription, descParams);

        // Button row
        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (16 * density);

        // Skip button
        btnSkip = new TextView(ctx);
        btnSkip.setText("Skip");
        btnSkip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnSkip.setTextColor(resolveThemeColor(R.attr.chk_textColorHint));
        btnSkip.setPadding((int) (16 * density), (int) (10 * density),
                (int) (16 * density), (int) (10 * density));
        btnSkip.setOnClickListener(v -> dismiss());

        // Next / Got it button
        btnNext = new TextView(ctx);
        btnNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnNext.setTextColor(resolveThemeColor(R.attr.chk_balanceColor));
        btnNext.setTypeface(null, android.graphics.Typeface.BOLD);
        btnNext.setPadding((int) (16 * density), (int) (10 * density),
                (int) (16 * density), (int) (10 * density));
        btnNext.setOnClickListener(v -> advanceStep());

        buttonRow.addView(btnSkip);
        buttonRow.addView(btnNext);

        tooltipCard.addView(buttonRow, rowParams);

        // Add to overlay with max width constraint
        int maxWidth = (int) dpToPx(TOOLTIP_MAX_WIDTH_DP);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                maxWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
        addView(tooltipCard, cardParams);
    }

    // ─── Show a specific step ────────────────────────────────────────
    private void showStep(int index) {
        if (index < 0 || index >= steps.size()) {
            dismiss();
            return;
        }

        currentStepIndex = index;
        OnboardingStep step = steps.get(index);

        // Find target view
        View targetView = activity.findViewById(step.targetViewId);

        if (targetView == null || targetView.getVisibility() != VISIBLE
                || targetView.getWidth() == 0 || targetView.getHeight() == 0) {
            // Target not visible, skip this step
            if (index + 1 < steps.size()) {
                showStep(index + 1);
            } else {
                dismiss();
            }
            return;
        }

        // Calculate spotlight rect
        int[] loc = new int[2];
        targetView.getLocationOnScreen(loc);

        // Adjust for overlay's own position
        int[] overlayLoc = new int[2];
        getLocationOnScreen(overlayLoc);

        float left = loc[0] - overlayLoc[0] - spotlightPadding;
        float top = loc[1] - overlayLoc[1] - spotlightPadding;
        float right = left + targetView.getWidth() + spotlightPadding * 2;
        float bottom = top + targetView.getHeight() + spotlightPadding * 2;

        spotlightRect.set(left, top, right, bottom);
        invalidate();

        // Update tooltip content
        tooltipStepCounter.setText("Step " + (index + 1) + " of " + steps.size());
        tooltipTitle.setText(step.title);
        tooltipDescription.setText(step.description);

        if (index == steps.size() - 1) {
            btnNext.setText("Got it! ✓");
            btnSkip.setVisibility(GONE);
        } else {
            btnNext.setText("Next →");
            btnSkip.setVisibility(VISIBLE);
        }

        // Position tooltip above or below the spotlight
        positionTooltip();

        // Animate in
        animateTooltipIn();
    }

    private void positionTooltip() {
        tooltipCard.measure(
                MeasureSpec.makeMeasureSpec((int) dpToPx(TOOLTIP_MAX_WIDTH_DP), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

        int tooltipHeight = tooltipCard.getMeasuredHeight();
        int tooltipWidth = tooltipCard.getMeasuredWidth();
        float margin = dpToPx(TOOLTIP_MARGIN_DP);

        int screenHeight = getHeight();
        int screenWidth = getWidth();
        if (screenHeight == 0) screenHeight = getResources().getDisplayMetrics().heightPixels;
        if (screenWidth == 0) screenWidth = getResources().getDisplayMetrics().widthPixels;

        float tooltipX, tooltipY;

        // Check if there's more space above or below the spotlight
        float spaceBelow = screenHeight - spotlightRect.bottom;
        float spaceAbove = spotlightRect.top;

        if (spaceBelow >= tooltipHeight + margin * 2) {
            // Position below
            tooltipY = spotlightRect.bottom + margin;
        } else if (spaceAbove >= tooltipHeight + margin * 2) {
            // Position above
            tooltipY = spotlightRect.top - tooltipHeight - margin;
        } else {
            // Center vertically
            tooltipY = (screenHeight - tooltipHeight) / 2f;
        }

        // Center horizontally relative to spotlight, but stay within screen
        tooltipX = spotlightRect.centerX() - tooltipWidth / 2f;
        tooltipX = Math.max(margin, Math.min(tooltipX, screenWidth - tooltipWidth - margin));
        tooltipY = Math.max(margin, Math.min(tooltipY, screenHeight - tooltipHeight - margin));

        tooltipCard.setTranslationX(tooltipX);
        tooltipCard.setTranslationY(tooltipY);
    }

    private void animateTooltipIn() {
        tooltipCard.setAlpha(0f);
        tooltipCard.setScaleX(0.85f);
        tooltipCard.setScaleY(0.85f);
        tooltipCard.setVisibility(VISIBLE);

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(
                ObjectAnimator.ofFloat(tooltipCard, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(tooltipCard, "scaleX", 0.85f, 1f),
                ObjectAnimator.ofFloat(tooltipCard, "scaleY", 0.85f, 1f)
        );
        animSet.setDuration(300);
        animSet.setInterpolator(new DecelerateInterpolator());
        animSet.start();
    }

    // ─── Navigation ──────────────────────────────────────────────────
    private void advanceStep() {
        if (currentStepIndex + 1 < steps.size()) {
            // Quick fade out, then show next
            tooltipCard.animate()
                    .alpha(0f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(150)
                    .withEndAction(() -> showStep(currentStepIndex + 1))
                    .start();
        } else {
            dismiss();
        }
    }

    private void dismiss() {
        animate()
                .alpha(0f)
                .setDuration(250)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            parent.removeView(OnboardingOverlay.this);
                        }
                        if (onCompleteListener != null) {
                            onCompleteListener.run();
                        }
                    }
                })
                .start();
    }

    // ─── Drawing ─────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw full-screen scrim
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

        // Cut out spotlight
        if (!spotlightRect.isEmpty()) {
            canvas.drawRoundRect(spotlightRect, spotlightCornerRadius,
                    spotlightCornerRadius, clearPaint);

            // Draw outer glow ring (wider, subtle white)
            canvas.drawRoundRect(spotlightRect, spotlightCornerRadius,
                    spotlightCornerRadius, glowPaint);

            // Draw crisp border around spotlight
            canvas.drawRoundRect(spotlightRect, spotlightCornerRadius,
                    spotlightCornerRadius, borderPaint);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────
    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }

    private int resolveThemeColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return typedValue.data;
        }
        return Color.WHITE;
    }
}
