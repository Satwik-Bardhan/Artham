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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.R;
import com.phynix.artham.models.TransactionModel;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * MonthlySummaryReceiver — Sends a rich monthly summary notification on the 1st of each month.
 * Shows: Total In, Total Out, Net Saved, Top spending category.
 */
public class MonthlySummaryReceiver extends BroadcastReceiver {

    private static final String TAG = "MonthlySummary";
    private static final String CHANNEL_ID = "monthly_summary";
    private static final int NOTIFICATION_ID = 9001;
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_MONTHLY_SUMMARY_ENABLED = "monthly_summary_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Monthly summary alarm fired");

        // Check if feature is enabled
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_MONTHLY_SUMMARY_ENABLED, true)) {
            Log.d(TAG, "Monthly summary disabled by user");
            return;
        }

        // Fetch last month's transactions from Firebase and build the notification
        fetchAndNotify(context);

        // Reschedule for next month
        scheduleMonthlyAlarm(context);
    }

    private void fetchAndNotify(Context context) {
        if (!AuthManager.isSignedIn(context)) return;
        // Stubbed completely
    }

    private void showNotification(Context context, String title, String body, String expanded) {
        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(expanded))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Monthly Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Monthly spending summary notification");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private static final java.text.NumberFormat INR_FORMAT = java.text.NumberFormat.getNumberInstance(new java.util.Locale("en", "IN"));
    
    private String formatAmount(double amount) {
        return INR_FORMAT.format(amount);
    }

    // ─── Static scheduling helpers ───

    /**
     * Schedule the monthly summary alarm for the 1st of next month at 9:00 AM.
     */
    public static void scheduleMonthlyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, MonthlySummaryReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Schedule for 1st of next month, 9:00 AM
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
        Log.d(TAG, "Monthly summary scheduled for: " + cal.getTime());
    }

    /**
     * Check if monthly summary is enabled in prefs.
     */
    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MONTHLY_SUMMARY_ENABLED, true);
    }

    /**
     * Enable or disable monthly summary notifications.
     */
    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MONTHLY_SUMMARY_ENABLED, enabled).apply();

        if (enabled) {
            scheduleMonthlyAlarm(context);
        } else {
            // Cancel alarm
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                Intent intent = new Intent(context, MonthlySummaryReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(context, NOTIFICATION_ID, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                alarmManager.cancel(pi);
            }
        }
    }
}
