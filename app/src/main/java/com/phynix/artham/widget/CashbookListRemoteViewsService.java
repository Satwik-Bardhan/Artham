package com.phynix.artham.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.phynix.artham.R;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.Constants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * RemoteViewsService that powers the scrollable cashbook ListView in the widget.
 * Reads cached cashbook data from SharedPreferences and inflates list items.
 */
public class CashbookListRemoteViewsService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CashbookListRemoteViewsFactory(getApplicationContext(), intent);
    }

    /**
     * Factory that creates RemoteViews for each cashbook item in the widget ListView.
     */
    private static class CashbookListRemoteViewsFactory implements RemoteViewsFactory {

        private final Context context;
        private final int appWidgetId;
        private final List<CashbookData> cashbooks = new ArrayList<>();

        CashbookListRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
            this.appWidgetId = intent.getIntExtra(
                    android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                    android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }

        @Override
        public void onCreate() {
            // Initial data load happens in onDataSetChanged
        }

        @Override
        public void onDataSetChanged() {
            cashbooks.clear();

            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            String json = prefs.getString("widget_cashbooks_" + appWidgetId, null);

            if (json == null || json.isEmpty()) return;

            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    CashbookData data = new CashbookData();
                    data.id = obj.optString("id", "");
                    data.name = obj.optString("name", "Cashbook");
                    data.balance = obj.optDouble("balance", 0.0);
                    data.color = obj.optString("color", "#3F51B5");
                    data.balanceFormatted = "₹" + AmountFormatter.formatCompact(data.balance);
                    cashbooks.add(data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= cashbooks.size()) {
                return null;
            }

            CashbookData item = cashbooks.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_cashbook_list_item);

            // Set cashbook name and balance
            views.setTextViewText(R.id.widget_item_name, item.name);
            views.setTextViewText(R.id.widget_item_balance, item.balanceFormatted);

            // Set color dot tint
            try {
                int color = Color.parseColor(item.color);
                views.setInt(R.id.widget_item_color_dot, "setColorFilter", color);
            } catch (Exception e) {
                views.setInt(R.id.widget_item_color_dot, "setColorFilter", Color.parseColor("#3F51B5"));
            }

            // Fill-in intent for this item's click
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(Constants.EXTRA_CASHBOOK_ID, item.id);
            fillInIntent.putExtra("cashbook_name", item.name);
            views.setOnClickFillInIntent(R.id.widget_cashbook_list_item_root, fillInIntent);

            return views;
        }

        @Override
        public int getCount() {
            return cashbooks.size();
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public void onDestroy() {
            cashbooks.clear();
        }

        /**
         * Simple data holder for a cashbook entry in the widget.
         */
        private static class CashbookData {
            String id;
            String name;
            double balance;
            String color;
            String balanceFormatted;
        }
    }
}
