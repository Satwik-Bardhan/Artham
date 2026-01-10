package com.phynix.artham;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import java.text.NumberFormat; // [FIX] Added NumberFormat import
import java.util.Locale;

public class AnimatedBalanceCard {

    private static final String TAG = "AnimatedBalanceCard";

    private CardView balanceCardView;
    private TextView balanceText;
    private TextView moneyInText;
    private TextView moneyOutText;
    private TextView uidText;
    private TextView userNameText;

    private double currentBalance = 0.0;
    private double currentIncome = 0.0;
    private double currentExpense = 0.0;

    // [FIX] Added currency formatter
    private NumberFormat currencyFormat;

    public AnimatedBalanceCard(View rootView) {
        initializeViews(rootView);
        setupAnimations();
        // [FIX] Initialize formatter
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    }

    private void initializeViews(View rootView) {
        balanceCardView = rootView.findViewById(R.id.balanceCardView);
        balanceText = rootView.findViewById(R.id.balanceText);
        moneyInText = rootView.findViewById(R.id.moneyIn);
        moneyOutText = rootView.findViewById(R.id.moneyOut);
        uidText = rootView.findViewById(R.id.uidText);
        userNameText = rootView.findViewById(R.id.userNameBottom);
    }

    private void setupAnimations() {
        // Setup touch animation for card
        setupCardTouchAnimation();
    }

    // ========== ANIMATION METHODS ==========

    /**
     * Entry animation when card first appears
     */
    public void showWithAnimation() {
        // Set initial state
        balanceCardView.setAlpha(0f);
        balanceCardView.setScaleX(0.8f);
        balanceCardView.setScaleY(0.8f);
        balanceCardView.setTranslationY(100f);
        balanceCardView.setVisibility(View.VISIBLE);

        // Animate to final state
        balanceCardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    /**
     * Animate balance change with counter effect
     */
    public void updateBalanceWithAnimation(double newBalance, double newIncome, double newExpense) {
        // Animate balance counter
        animateBalanceCounter(currentBalance, newBalance);

        // Animate income counter
        animateIncomeCounter(currentIncome, newIncome);

        // Animate expense counter
        animateExpenseCounter(currentExpense, newExpense);

        // Update stored values
        currentBalance = newBalance;
        currentIncome = newIncome;
        currentExpense = newExpense;

        // Add pulse effect after animation
        balanceText.postDelayed(() -> pulseAnimation(balanceText), 800);
    }

    /**
     * Balance counter animation
     */
    private void animateBalanceCounter(double oldBalance, double newBalance) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            double currentValue = oldBalance + (newBalance - oldBalance) * progress;
            // [FIX] Use currency formatter
            balanceText.setText(currencyFormat.format(currentValue));

            // Add subtle scale effect during animation
            float scale = 1f + (0.05f * (float) Math.sin(progress * Math.PI));
            balanceText.setScaleX(scale);
            balanceText.setScaleY(scale);
        });

        animator.start();
    }

    /**
     * Income counter animation
     */
    private void animateIncomeCounter(double oldIncome, double newIncome) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        animator.setStartDelay(200);

        animator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            double currentValue = oldIncome + (newIncome - oldIncome) * progress;
            // [FIX] Use currency formatter
            moneyInText.setText(currencyFormat.format(currentValue));
        });

        animator.start();
    }

    /**
     * Expense counter animation
     */
    private void animateExpenseCounter(double oldExpense, double newExpense) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        animator.setStartDelay(400);

        animator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            double currentValue = oldExpense + (newExpense - oldExpense) * progress;
            // [FIX] Use currency formatter
            moneyOutText.setText(currencyFormat.format(currentValue));
        });

        animator.start();
    }

    /**
     * Pulse animation for emphasis
     */
    private void pulseAnimation(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.15f, 1f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(scaleX).with(scaleY);
        animatorSet.setDuration(400);
        animatorSet.setInterpolator(new BounceInterpolator());
        animatorSet.start();
    }

    /**
     * Touch animation for card press
     */
    private void setupCardTouchAnimation() {
        balanceCardView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(100)
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start();
                    break;
            }
            return false; // Allow other touch events
        });
    }

    /**
     * Highlight animation for new transactions
     */
    public void highlightNewTransaction() {
        ObjectAnimator flash = ObjectAnimator.ofFloat(balanceCardView, "alpha", 1f, 0.7f, 1f);
        flash.setDuration(300);
        flash.setRepeatCount(1);
        flash.start();
    }

    /**
     * Shake animation for errors
     */
    public void shakeAnimation() {
        ObjectAnimator shake = ObjectAnimator.ofFloat(balanceCardView, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f);
        shake.setDuration(600);
        shake.start();
    }

    // ========== SETTER METHODS ==========

    public void setUserInfo(String uid, String userName) {
        uidText.setText(uid);
        userNameText.setText(userName);
    }

    public void setBalanceData(double balance, double income, double expense) {
        currentBalance = balance;
        currentIncome = income;
        currentExpense = expense;

        // [FIX] Use currency formatter
        balanceText.setText(currencyFormat.format(balance));
        moneyInText.setText(currencyFormat.format(income));
        moneyOutText.setText(currencyFormat.format(expense));
    }

    public void setOnCardClickListener(View.OnClickListener listener) {
        balanceCardView.setOnClickListener(listener);
    }

    // ========== GETTER METHODS ==========

    public CardView getCardView() {
        return balanceCardView;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }
}