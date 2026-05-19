package com.phynix.artham.utils;

import com.phynix.artham.models.TransactionModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InsightsEngine — Smart spending analysis engine
 * Computes contextual, human-readable insights from transaction data.
 * No server/AI needed — pure local computation.
 */
public class InsightsEngine {

    public static class Insight {
        public final String emoji;
        public final String title;
        public final String message;

        public Insight(String emoji, String title, String message) {
            this.emoji = emoji;
            this.title = title;
            this.message = message;
        }
    }

    /**
     * Generate a list of smart insights from the given transactions.
     * Returns 3-6 insights depending on available data.
     */
    public static List<Insight> generate(List<TransactionModel> allTransactions) {
        List<Insight> insights = new ArrayList<>();

        if (allTransactions == null || allTransactions.isEmpty()) {
            insights.add(new Insight("📝", "Get Started", "Add your first transaction to see smart insights here!"));
            return insights;
        }

        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();

        // Time boundaries
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_YEAR, -7);
        long weekStart = cal.getTimeInMillis();

        cal.setTimeInMillis(now);
        cal.add(Calendar.DAY_OF_YEAR, -14);
        long prevWeekStart = cal.getTimeInMillis();

        cal.setTimeInMillis(now);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long monthStart = cal.getTimeInMillis();

        cal.add(Calendar.MONTH, -1);
        long prevMonthStart = cal.getTimeInMillis();

        // Categorize transactions
        double todayIn = 0, todayOut = 0;
        double weekIn = 0, weekOut = 0;
        double prevWeekOut = 0;
        double monthIn = 0, monthOut = 0;
        double prevMonthIn = 0, prevMonthOut = 0;
        double totalIn = 0, totalOut = 0;

        Map<String, Double> monthCategorySpending = new HashMap<>();
        Map<String, Double> todayCategorySpending = new HashMap<>();

        int lowSpendDays = 0;
        Map<String, Double> dailySpending = new HashMap<>();
        java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);

        for (TransactionModel t : allTransactions) {
            long ts = t.getTimestamp();
            double amt = t.getAmount();
            boolean isIn = "IN".equalsIgnoreCase(t.getType());
            String category = t.getTransactionCategory() != null ? t.getTransactionCategory() : "Other";

            if (isIn) {
                totalIn += amt;
                if (ts >= todayStart) todayIn += amt;
                if (ts >= weekStart) weekIn += amt;
                if (ts >= monthStart) monthIn += amt;
                if (ts >= prevMonthStart && ts < monthStart) prevMonthIn += amt;
            } else {
                totalOut += amt;
                if (ts >= todayStart) {
                    todayOut += amt;
                    todayCategorySpending.merge(category, amt, Double::sum);
                }
                if (ts >= weekStart) weekOut += amt;
                if (ts >= prevWeekStart && ts < weekStart) prevWeekOut += amt;
                if (ts >= monthStart) {
                    monthOut += amt;
                    monthCategorySpending.merge(category, amt, Double::sum);
                }
                if (ts >= prevMonthStart && ts < monthStart) prevMonthOut += amt;

                // Track daily spending for streak
                String dayKey = dayFormat.format(new java.util.Date(ts));
                dailySpending.merge(dayKey, amt, Double::sum);
            }
        }

        // ───── INSIGHT 1: Today's Summary ─────
        if (todayOut > 0 || todayIn > 0) {
            String msg;
            if (todayOut == 0 && todayIn > 0) {
                msg = "You earned " + formatAmount(todayIn) + " today with zero spending. Great day! 🌟";
            } else if (todayIn > todayOut) {
                msg = "Today: +" + formatAmount(todayIn) + " in, -" + formatAmount(todayOut) + " out. You're in the green!";
            } else {
                msg = "Today: You've spent " + formatAmount(todayOut) + " so far. Stay on track!";
            }
            insights.add(new Insight("📊", "Today's Snapshot", msg));
        }

        // ───── INSIGHT 2: Week vs Week Comparison ─────
        if (weekOut > 0 && prevWeekOut > 0) {
            double diff = weekOut - prevWeekOut;
            double pct = Math.abs(diff / prevWeekOut) * 100;
            if (diff > 0) {
                insights.add(new Insight("📈", "Spending Trend",
                        "You spent " + String.format("%.0f%%", pct) + " more this week (" + formatAmount(weekOut) + ") vs last week (" + formatAmount(prevWeekOut) + ")"));
            } else if (diff < 0) {
                insights.add(new Insight("📉", "Great Progress!",
                        "You cut spending by " + String.format("%.0f%%", pct) + " this week! Saved " + formatAmount(Math.abs(diff)) + " 🎉"));
            }
        }

        // ───── INSIGHT 3: Top Spending Category (This Month) ─────
        if (!monthCategorySpending.isEmpty()) {
            String topCat = null;
            double topAmt = 0;
            for (Map.Entry<String, Double> entry : monthCategorySpending.entrySet()) {
                if (entry.getValue() > topAmt) {
                    topAmt = entry.getValue();
                    topCat = entry.getKey();
                }
            }
            if (topCat != null && monthOut > 0) {
                double pct = (topAmt / monthOut) * 100;
                insights.add(new Insight("🏷️", "Top Category",
                        topCat + " takes " + String.format("%.0f%%", pct) + " of your spending (" + formatAmount(topAmt) + " this month)"));
            }
        }

        // ───── INSIGHT 4: Monthly Savings ─────
        if (monthIn > 0 && monthOut > 0) {
            double saved = monthIn - monthOut;
            if (saved > 0) {
                insights.add(new Insight("💰", "Monthly Savings",
                        "You've saved " + formatAmount(saved) + " this month! Keep it going 🚀"));
            } else {
                insights.add(new Insight("⚠️", "Over Budget",
                        "You've spent " + formatAmount(Math.abs(saved)) + " more than your income this month. Time to cut back!"));
            }
        }

        // ───── INSIGHT 5: Month vs Month ─────
        if (monthOut > 0 && prevMonthOut > 0) {
            double diff = monthOut - prevMonthOut;
            if (diff < 0) {
                insights.add(new Insight("🎯", "Month Comparison",
                        "You're spending " + formatAmount(Math.abs(diff)) + " less than last month. Well done!"));
            }
        }

        // ───── INSIGHT 6: Biggest Expense Today ─────
        if (!todayCategorySpending.isEmpty()) {
            String bigCat = null;
            double bigAmt = 0;
            for (Map.Entry<String, Double> entry : todayCategorySpending.entrySet()) {
                if (entry.getValue() > bigAmt) {
                    bigAmt = entry.getValue();
                    bigCat = entry.getKey();
                }
            }
            if (bigCat != null && bigAmt > 0) {
                insights.add(new Insight("💸", "Biggest Spend Today",
                        bigCat + " — " + formatAmount(bigAmt)));
            }
        }

        // ───── INSIGHT 7: Low-Spend Streak ─────
        Calendar streakCal = Calendar.getInstance();
        streakCal.set(Calendar.HOUR_OF_DAY, 0);
        streakCal.set(Calendar.MINUTE, 0);
        streakCal.set(Calendar.SECOND, 0);
        int streak = 0;
        double avgDailySpend = totalOut / Math.max(1, dailySpending.size());
        double threshold = avgDailySpend * 0.7; // 70% of average = "low spend"

        for (int i = 0; i < 30; i++) {
            String dayKey = dayFormat.format(streakCal.getTime());
            Double daySpend = dailySpending.get(dayKey);
            if (daySpend == null || daySpend <= threshold) {
                streak++;
            } else {
                break;
            }
            streakCal.add(Calendar.DAY_OF_YEAR, -1);
        }

        if (streak >= 3) {
            insights.add(new Insight("🔥", "Spending Streak",
                    streak + " days of low spending! You're on fire 🔥"));
        }

        // ───── INSIGHT 8: Total Overview ─────
        double netBalance = totalIn - totalOut;
        insights.add(new Insight("📋", "All-Time Summary",
                "Total In: " + formatAmount(totalIn) + " | Total Out: " + formatAmount(totalOut) +
                        " | Net: " + (netBalance >= 0 ? "+" : "-") + formatAmount(Math.abs(netBalance))));

        // Shuffle middle insights (keep first and last fixed)
        if (insights.size() > 3) {
            List<Insight> middle = new ArrayList<>(insights.subList(1, insights.size() - 1));
            Collections.shuffle(middle);
            for (int i = 0; i < middle.size(); i++) {
                insights.set(i + 1, middle.get(i));
            }
        }

        return insights;
    }

    private static final java.text.NumberFormat INR = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("en", "IN"));

    private static String formatAmount(double amount) {
        return INR.format(amount);
    }
}
