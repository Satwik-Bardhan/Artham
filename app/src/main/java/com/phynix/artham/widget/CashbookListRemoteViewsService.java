package com.phynix.artham.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.phynix.artham.R;
import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.Constants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            // 1. Parse the selected cashbook IDs and metadata from cache
            List<String> selectedIds = new ArrayList<>();
            Map<String, CashbookData> selectedMetaData = new HashMap<>();
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String id = obj.optString("id", "");
                    if (!id.isEmpty()) {
                        selectedIds.add(id);
                        CashbookData data = new CashbookData();
                        data.id = id;
                        data.name = obj.optString("name", "Cashbook");
                        data.color = obj.optString("color", "#3F51B5");
                        data.balance = obj.optDouble("balance", 0.0); // fallback
                        selectedMetaData.put(id, data);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (selectedIds.isEmpty()) return;

            // 2. Fetch the latest cashbooks from database synchronously
            DataRepository repo = DataRepository.getInstance((android.app.Application) context.getApplicationContext());
            List<CashbookModel> updatedCashbooks = new ArrayList<>();

            if (repo.isLocalMode()) {
                DataRepository.LocalDataWrapper localData = repo.loadLocalData();
                if (localData != null && localData.cashbooks != null) {
                    updatedCashbooks.addAll(localData.cashbooks);
                }
            } else {
                // Firebase mode: query synchronously using a CountDownLatch
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                repo.getCashbooks(
                    cashbooksList -> {
                        if (cashbooksList != null) {
                            updatedCashbooks.addAll(cashbooksList);
                        }
                        latch.countDown();
                    },
                    error -> latch.countDown()
                );
                try {
                    // Block for max 2.5 seconds to avoid UI freeze if completely offline
                    latch.await(2500, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // 3. Compute the accurate balance for all selected cashbooks
            boolean isLocal = repo.isLocalMode();
            for (CashbookModel cb : updatedCashbooks) {
                if (selectedIds.contains(cb.getCashbookId())) {
                    double balance;
                    if (isLocal) {
                        balance = cb.getTotalBalance();
                    } else {
                        double totalIncome = 0;
                        double totalExpense = 0;
                        for (TransactionModel t : cb.getTransactionList()) {
                            if ("IN".equalsIgnoreCase(t.getType())) {
                                totalIncome += t.getAmount();
                            } else {
                                totalExpense += t.getAmount();
                            }
                        }
                        balance = totalIncome - totalExpense;
                    }

                    CashbookData data = selectedMetaData.get(cb.getCashbookId());
                    if (data != null) {
                        data.name = cb.getName();
                        data.balance = balance;
                    }
                }
            }

            // 4. Retrieve active cashbook ID for sorting
            String activeCashbookId = prefs.getString("last_selected_cashbook_id", null);
            if (activeCashbookId == null) {
                com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    activeCashbookId = prefs.getString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + user.getUid(), null);
                } else {
                    activeCashbookId = prefs.getString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + "local_user", null);
                }
            }

            // 5. Gather valid cashbooks and sort alphabetically by name
            List<CashbookData> sortedList = new ArrayList<>();
            for (String id : selectedIds) {
                CashbookData data = selectedMetaData.get(id);
                if (data != null) {
                    sortedList.add(data);
                }
            }
            Collections.sort(sortedList, (d1, d2) -> d1.name.compareToIgnoreCase(d2.name));

            // 6. Move active cashbook to the very top (index 0)
            if (activeCashbookId != null) {
                CashbookData activeData = null;
                for (CashbookData d : sortedList) {
                    if (d.id.equals(activeCashbookId)) {
                        activeData = d;
                        break;
                    }
                }
                if (activeData != null) {
                    sortedList.remove(activeData);
                    sortedList.add(0, activeData);
                }
            }

            // 7. Add sorted books to list and format balance
            for (CashbookData data : sortedList) {
                data.balanceFormatted = AmountFormatter.formatCompact(data.balance);
                cashbooks.add(data);
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

            // Highlight active cashbook with a distinct border/background drawable
            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            String activeCashbookId = prefs.getString("last_selected_cashbook_id", null);
            if (activeCashbookId == null) {
                com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                String key = Constants.PREF_ACTIVE_CASHBOOK_PREFIX + (user != null ? user.getUid() : "local_user");
                activeCashbookId = prefs.getString(key, null);
            }

            boolean isActive = item.id.equals(activeCashbookId);
            if (isActive) {
                views.setInt(R.id.widget_cashbook_list_item_root, "setBackgroundResource", R.drawable.bg_widget_item_border_active);
            } else {
                views.setInt(R.id.widget_cashbook_list_item_root, "setBackgroundResource", R.drawable.bg_widget_item_border);
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
