package com.phynix.artham.utils;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.phynix.artham.R;
import com.phynix.artham.SplashActivity;

import java.util.Calendar;

/**
 * DailyReminderReceiver — Receives daily alarms and sends a notification to prompt transaction logging.
 */
public class DailyReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyReminder";
    private static final String CHANNEL_ID = "daily_reminder";
    private static final int NOTIFICATION_ID = 9002;
    private static final String PREF_NAME = "AppPrefs";
    
    public static final String KEY_DAILY_REMINDER_ENABLED = "reminder_enabled";
    public static final String KEY_REMINDER_HOUR = "reminder_hour";
    public static final String KEY_REMINDER_MINUTE = "reminder_minute";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily reminder alarm fired");

        // Verify if enabled
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, false)) {
            Log.d(TAG, "Daily reminder disabled by user preference");
            return;
        }

        // Show the push notification
        showNotification(context);

        // Schedule the alarm for the next day
        scheduleDailyAlarm(context);
    }

    private void showNotification(Context context) {
        createNotificationChannel(context);

        // Create an intent to open SplashActivity (launcher routing)
        Intent openIntent = new Intent(context, SplashActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                openIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("📝 Artham Reminder")
                .setContentText("Keep your cashbooks up to date. Tap to log today's transactions.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Daily Reminder",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Daily transaction logging reminder");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    // ─── Static scheduling helpers ───

    /**
     * Schedule the daily alarm based on saved hour and minute preferences.
     */
    public static void scheduleDailyAlarm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, false);
        if (!enabled) {
            cancelDailyAlarm(context);
            return;
        }

        int hour = prefs.getInt(KEY_REMINDER_HOUR, 20); // Default to 8 PM
        int minute = prefs.getInt(KEY_REMINDER_MINUTE, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                NOTIFICATION_ID, 
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If time is already in the past today, schedule for tomorrow
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
        }
        Log.d(TAG, "Daily reminder scheduled for hour " + hour + " minute " + minute + " | Actual time: " + cal.getTime());
    }

    /**
     * Cancel the daily reminder alarm.
     */
    public static void cancelDailyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, DailyReminderReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context, 
                    NOTIFICATION_ID, 
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(pi);
            Log.d(TAG, "Daily reminder alarm cancelled");
        }
    }
}
