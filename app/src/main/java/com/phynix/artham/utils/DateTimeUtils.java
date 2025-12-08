package com.phynix.artham.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DateTimeUtils {

    /**
     * Get relative time span from a timestamp
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted relative time string (e.g., "3 hours ago")
     */
    public static String getRelativeTimeSpan(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        // Less than a minute
        if (diff < TimeUnit.MINUTES.toMillis(1)) {
            return "Just now";
        }

        // Less than an hour
        if (diff < TimeUnit.HOURS.toMillis(1)) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }

        // Less than a day
        if (diff < TimeUnit.DAYS.toMillis(1)) {
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }

        // Less than a week
        if (diff < TimeUnit.DAYS.toMillis(7)) {
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            return days + (days == 1 ? " day ago" : " days ago");
        }

        // Less than a month
        if (diff < TimeUnit.DAYS.toMillis(30)) {
            long weeks = TimeUnit.MILLISECONDS.toDays(diff) / 7;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }

        // Less than a year
        if (diff < TimeUnit.DAYS.toMillis(365)) {
            long months = TimeUnit.MILLISECONDS.toDays(diff) / 30;
            return months + (months == 1 ? " month ago" : " months ago");
        }

        // Years
        long years = TimeUnit.MILLISECONDS.toDays(diff) / 365;
        return years + (years == 1 ? " year ago" : " years ago");
    }

    /**
     * Format timestamp to readable date string
     * @param timestamp Unix timestamp in milliseconds
     * @param pattern Date pattern (e.g., "MMM dd, yyyy")
     * @return Formatted date string
     */
    public static String formatDate(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}