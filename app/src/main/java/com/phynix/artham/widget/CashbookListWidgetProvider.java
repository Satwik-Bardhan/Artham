package com.phynix.artham.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.MainActivity;
import com.phynix.artham.R;
import com.phynix.artham.activities.CashbookWidgetConfigActivity;
import android.graphics.Color;
import com.phynix.artham.activities.HomeActivity;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.ThemeManager;

/**
 * Widget provider for the scrollable Cashbook List Widget.
 * Displays user-selected cashbooks in a scrollable ListView.
 * Tapping a cashbook switches to it and opens the app.
 */
public class CashbookListWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_CASHBOOK_CLICK = "com.phynix.artham.ACTION_CASHBOOK_CLICK";
    private static final String PREFS_APP = "AppPrefs";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_cashbook_list);

        // Set up the RemoteViewsService intent for the ListView
        Intent serviceIntent = new Intent(context, CashbookListRemoteViewsService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // Unique data URI so each widget instance gets its own service
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_cashbook_listview, serviceIntent);
        views.setEmptyView(R.id.widget_cashbook_listview, R.id.widget_empty_text);

        // PendingIntent template for list item clicks (MUST use FLAG_MUTABLE for fill-in intents)
        Intent clickIntent = new Intent(context, CashbookListWidgetProvider.class);
        clickIntent.setAction(ACTION_CASHBOOK_CLICK);
        clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent clickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.widget_cashbook_listview, clickPendingIntent);

        // Header click → open app
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent appPending = PendingIntent.getActivity(
                context,
                appWidgetId * 100,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_header, appPending);

        // Settings button → open config activity for reconfiguration
        Intent configIntent = new Intent(context, CashbookWidgetConfigActivity.class);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Unique data URI to avoid PendingIntent reuse
        configIntent.setData(Uri.parse("artham://config/" + appWidgetId));
        PendingIntent configPending = PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 1,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_settings_btn, configPending);

        appWidgetManager.updateAppWidget(appWidgetId, views);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_cashbook_listview);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_CASHBOOK_CLICK.equals(intent.getAction())) {
            String cashbookId = intent.getStringExtra(Constants.EXTRA_CASHBOOK_ID);
            String cashbookName = intent.getStringExtra("cashbook_name");

            if (cashbookId != null && !cashbookId.isEmpty()) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("last_selected_cashbook_id", cashbookId);
                if (cashbookName != null) {
                    editor.putString("last_selected_cashbook_name", cashbookName);
                }

                // Also set as the active cashbook for the current user
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    editor.putString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + user.getUid(), cashbookId);
                } else {
                    // Local mode
                    editor.putString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + "local_user", cashbookId);
                }
                editor.apply();

                // Launch HomeActivity
                Intent homeIntent = new Intent(context, HomeActivity.class);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                homeIntent.putExtra(Constants.EXTRA_CASHBOOK_ID, cashbookId);
                context.startActivity(homeIntent);
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // Clean up SharedPreferences for deleted widget instances
        SharedPreferences prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int widgetId : appWidgetIds) {
            editor.remove("widget_cashbooks_" + widgetId);
        }
        editor.apply();
    }
}
