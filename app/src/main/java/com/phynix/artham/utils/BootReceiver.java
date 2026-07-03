package com.phynix.artham.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * BootReceiver — Re-schedules all active reminder alarms after the device reboots.
 * Android clears all alarms on reboot, so this receiver restores them based on
 * the user's saved preferences.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String PREF_NAME = "AppPrefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action, ignoring");
            return;
        }

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Ignoring non-boot action: " + intent.getAction());
            return;
        }

        Log.d(TAG, "Boot completed — re-scheduling active alarms");

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 1. Re-schedule Daily Reminder if enabled
        try {
            boolean dailyEnabled = prefs.getBoolean(
                    DailyReminderReceiver.KEY_DAILY_REMINDER_ENABLED, false);
            if (dailyEnabled) {
                DailyReminderReceiver.scheduleDailyAlarm(context);
                Log.d(TAG, "Daily reminder alarm re-scheduled");
            } else {
                Log.d(TAG, "Daily reminder is disabled, skipping");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to re-schedule daily reminder", e);
        }

        // 2. Re-schedule Monthly Summary if enabled
        try {
            boolean monthlyEnabled = prefs.getBoolean("monthly_summary_enabled", true);
            if (monthlyEnabled) {
                MonthlySummaryReceiver.scheduleMonthlyAlarm(context);
                Log.d(TAG, "Monthly summary alarm re-scheduled");
            } else {
                Log.d(TAG, "Monthly summary is disabled, skipping");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to re-schedule monthly summary", e);
        }

        // 3. Re-schedule Interval Reminder if enabled
        try {
            boolean intervalEnabled = prefs.getBoolean(
                    IntervalReminderReceiver.KEY_INTERVAL_REMINDER_ENABLED, true);
            if (intervalEnabled) {
                IntervalReminderReceiver.scheduleNextAlarm(context);
                Log.d(TAG, "Interval reminder alarm re-scheduled");
            } else {
                Log.d(TAG, "Interval reminder is disabled, skipping");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to re-schedule interval reminder", e);
        }

        Log.d(TAG, "Boot alarm re-scheduling complete");
    }
}
