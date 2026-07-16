package com.phynix.artham.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.MainActivity;
import com.phynix.artham.activities.CashInOutActivity;
import com.phynix.artham.R;
import com.phynix.artham.SignInActivity;
import com.phynix.artham.utils.Constants;

/**
 * Home screen widget that provides quick Cash In / Cash Out buttons.
 *
 * Behaviour:
 * - If user is signed in → launches CashInOutActivity with the active cashbook ID
 *   and the correct transaction type (IN or OUT).
 * - If user is NOT signed in → launches SignInActivity so they can authenticate first.
 *
 * No periodic updates needed — the widget is purely a launcher shortcut.
 */
public class CashInOutWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_APP = "AppPrefs";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_cash_in_out);

        // Display current cashbook name in widget header
        SharedPreferences prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);
        String cashbookName = prefs.getString("last_selected_cashbook_name", null);
        if (cashbookName != null && !cashbookName.isEmpty()) {
            views.setTextViewText(R.id.widget_cashbook_name, "📖 " + cashbookName);
        } else {
            views.setTextViewText(R.id.widget_cashbook_name, "Quick Entry");
        }

        // Cash In button → opens CashInOutActivity with type=IN
        PendingIntent cashInPending = buildTransactionIntent(context, Constants.TRANSACTION_TYPE_IN, appWidgetId * 10);
        views.setOnClickPendingIntent(R.id.widget_btn_cash_in, cashInPending);

        // Cash Out button → opens CashInOutActivity with type=OUT
        PendingIntent cashOutPending = buildTransactionIntent(context, Constants.TRANSACTION_TYPE_OUT, appWidgetId * 10 + 1);
        views.setOnClickPendingIntent(R.id.widget_btn_cash_out, cashOutPending);

        // Logo / App Name click → opens the app (MainActivity)
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent appLaunchPending = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 2, // Unique request code for launcher
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_logo_container, appLaunchPending);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /**
     * Builds a PendingIntent that either:
     * - Launches CashInOutActivity (if user is authenticated)
     * - launches SignInActivity (if not authenticated)
     */
    private PendingIntent buildTransactionIntent(Context context, String transactionType, int requestCode) {
        Intent intent;
        if (AuthManager.isSignedIn(context)) {
            // User is signed in — get active cashbook and launch CashInOutActivity
            SharedPreferences prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);
            String activeCashbookId = prefs.getString("active_cashbook_id_" + AuthManager.getUserId(context), "");

            intent = new Intent(context, CashInOutActivity.class);
            intent.putExtra(Constants.EXTRA_CASHBOOK_ID, activeCashbookId);
            intent.putExtra(Constants.EXTRA_TRANSACTION_TYPE, transactionType);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else {
            // Not signed in — redirect to sign in
            intent = new Intent(context, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
