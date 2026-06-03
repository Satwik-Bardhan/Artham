package com.phynix.artham.utils;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.phynix.artham.R;
import com.phynix.artham.SplashActivity;

import java.util.Random;

/**
 * IntervalReminderReceiver — Sends repeating reminder notifications at a user-configured interval.
 * Default interval is every 2 hours. Shows a polished notification with the app logo,
 * app name, and a motivational reminder message.
 */
public class IntervalReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "IntervalReminder";
    private static final String CHANNEL_ID = "interval_reminder";
    private static final int NOTIFICATION_ID = 9003;
    private static final String PREF_NAME = "AppPrefs";

    // SharedPreferences keys
    public static final String KEY_INTERVAL_REMINDER_ENABLED = "interval_reminder_enabled";
    public static final String KEY_REMINDER_INTERVAL_MINUTES = "reminder_interval_minutes";

    // Default interval: 2 hours (in minutes)
    public static final int DEFAULT_INTERVAL_MINUTES = 120;

    // Available interval options (in minutes) — labels are generated dynamically
    public static final int[] INTERVAL_OPTIONS = {30, 60, 120, 180, 240, 360, 480, 720};

    // Motivational reminder messages (rotated randomly)
    private static final String[] REMINDER_MESSAGES = {
            "Don't forget to log your transactions! 📝",
            "Keep your finances on track — add today's entries now.",
            "A quick update keeps your cashbook accurate! 💰",
            "Have you made any transactions? Log them before you forget!",
            "Stay organized — record your spending and income now.",
            "Your cashbook is waiting! Tap to add entries. 📊",
            "Small habit, big impact — log your transactions daily.",
            "Track every rupee! Open Artham and update your records.",
            "Financial clarity starts with consistent logging. ✨",
            "Time to update your cashbook — it only takes a moment!"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Interval reminder alarm fired");

        // Verify if enabled
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_INTERVAL_REMINDER_ENABLED, true)) {
            Log.d(TAG, "Interval reminder disabled by user preference");
            return;
        }

        // Show the notification
        showNotification(context);

        // Schedule the next alarm at the configured interval
        scheduleNextAlarm(context);
    }

    private void showNotification(Context context) {
        createNotificationChannel(context);

        // Create intent to open the app
        Intent openIntent = new Intent(context, SplashActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Pick a random motivational message
        String message = REMINDER_MESSAGES[new Random().nextInt(REMINDER_MESSAGES.length)];

        // Load the app logo as large icon for a polished look
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)                      // Status bar icon
                .setLargeIcon(largeIcon)                             // App logo next to notification
                .setContentTitle("Artham")                           // App name as title
                .setContentText(message)                             // Reminder message
                .setSubText("Transaction Reminder")                  // Subtle label below
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message)
                        .setBigContentTitle("Artham")
                        .setSummaryText("Transaction Reminder"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Transaction Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Repeating reminders to log your transactions in Artham");
            channel.setShowBadge(true);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    // ─── Static scheduling helpers ───

    /**
     * Schedule the next interval alarm based on the user's configured interval.
     */
    public static void scheduleNextAlarm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_INTERVAL_REMINDER_ENABLED, true);
        if (!enabled) {
            cancelAlarm(context);
            return;
        }

        int intervalMinutes = prefs.getInt(KEY_REMINDER_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, IntervalReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAt = System.currentTimeMillis() + ((long) intervalMinutes * 60 * 1000);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        Log.d(TAG, "Interval reminder scheduled in " + intervalMinutes + " minutes (at " + new java.util.Date(triggerAt) + ")");
    }

    /**
     * Cancel the interval reminder alarm.
     */
    public static void cancelAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, IntervalReminderReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    NOTIFICATION_ID,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(pi);
            Log.d(TAG, "Interval reminder alarm cancelled");
        }
    }

    /**
     * Get a human-readable label for the given interval in minutes.
     */
    public static String getIntervalLabel(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        } else {
            int hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
    }

    /**
     * Get the labels array for all interval options.
     */
    public static String[] getIntervalLabels() {
        String[] labels = new String[INTERVAL_OPTIONS.length];
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            labels[i] = "Every " + getIntervalLabel(INTERVAL_OPTIONS[i]);
        }
        return labels;
    }

    /**
     * Get the index of the given interval in the INTERVAL_OPTIONS array.
     * Returns the index of DEFAULT_INTERVAL_MINUTES if not found.
     */
    public static int getIntervalIndex(int minutes) {
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] == minutes) return i;
        }
        return 2; // Default: 2 hours (index 2)
    }
}
