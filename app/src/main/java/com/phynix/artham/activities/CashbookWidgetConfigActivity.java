package com.phynix.artham.activities;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.widget.CashbookListWidgetProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration activity for the Cashbook List Widget.
 * Shown when the widget is first placed on the home screen.
 * Allows users to select which cashbooks to display in the widget.
 */
public class CashbookWidgetConfigActivity extends AppCompatActivity {

    private static final String PREFS_APP = "AppPrefs";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private RecyclerView recyclerView;
    private CheckBox selectAllCheckbox;
    private ProgressBar loadingIndicator;
    private TextView doneButton;

    private List<CashbookModel> allCashbooks = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private CashbookConfigAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED);

        setContentView(R.layout.activity_widget_cashbook_config);

        // Dark status bar
        getWindow().setStatusBarColor(Color.parseColor("#0D0D1A"));
        getWindow().setNavigationBarColor(Color.parseColor("#0D0D1A"));

        // Get the widget ID from the intent
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        // Initialize views
        recyclerView = findViewById(R.id.config_cashbook_list);
        selectAllCheckbox = findViewById(R.id.config_select_all);
        loadingIndicator = findViewById(R.id.config_loading);
        doneButton = findViewById(R.id.config_done_btn);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CashbookConfigAdapter();
        recyclerView.setAdapter(adapter);

        // Select All toggle
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return; // only respond to user clicks
            selectedIds.clear();
            if (isChecked) {
                for (CashbookModel cb : allCashbooks) {
                    selectedIds.add(cb.getCashbookId());
                }
            }
            adapter.notifyDataSetChanged();
        });

        // Done button
        doneButton.setOnClickListener(v -> saveAndFinish());

        // Load cashbooks
        loadCashbooks();
    }

    private void loadCashbooks() {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        DataRepository.getInstance(getApplication()).getCashbooks(
                cashbooks -> new Handler(Looper.getMainLooper()).post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                    allCashbooks.clear();
                    if (cashbooks != null) {
                        boolean isLocal = DataRepository.getInstance(getApplication()).isLocalMode();
                        for (CashbookModel cb : cashbooks) {
                            if (!isLocal) {
                                double totalIncome = 0;
                                double totalExpense = 0;
                                for (TransactionModel t : cb.getTransactionList()) {
                                    if ("IN".equalsIgnoreCase(t.getType())) {
                                        totalIncome += t.getAmount();
                                    } else {
                                        totalExpense += t.getAmount();
                                    }
                                }
                                cb.setTotalBalance(totalIncome - totalExpense);
                            }
                            allCashbooks.add(cb);
                        }
                    }

                    // Sort cashbooks alphabetically by name for easy lookup
                    allCashbooks.sort((a, b) -> {
                        String nameA = a.getName() != null ? a.getName() : "";
                        String nameB = b.getName() != null ? b.getName() : "";
                        return nameA.compareToIgnoreCase(nameB);
                    });

                    // Load previously selected cashbooks if reconfiguring
                    loadPreviousSelection();

                    adapter.notifyDataSetChanged();
                    updateSelectAllState();
                }),
                error -> new Handler(Looper.getMainLooper()).post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load cashbooks", Toast.LENGTH_SHORT).show();
                })
        );
    }

    private void loadPreviousSelection() {
        SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        String json = prefs.getString("widget_cashbooks_" + appWidgetId, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    selectedIds.add(obj.optString("id", ""));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void updateSelectAllState() {
        if (allCashbooks.isEmpty()) {
            selectAllCheckbox.setChecked(false);
        } else {
            selectAllCheckbox.setChecked(selectedIds.size() == allCashbooks.size());
        }
    }

    private void saveAndFinish() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one cashbook", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray();
            for (CashbookModel cb : allCashbooks) {
                if (selectedIds.contains(cb.getCashbookId())) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", cb.getCashbookId());
                    obj.put("name", cb.getName());
                    obj.put("balance", cb.getTotalBalance());
                    obj.put("color", cb.getThemeColor() != null ? cb.getThemeColor() : "#3F51B5");
                    jsonArray.put(obj);
                }
            }

            // Save to SharedPreferences
            SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
            prefs.edit()
                    .putString("widget_cashbooks_" + appWidgetId, jsonArray.toString())
                    .apply();

            // Trigger widget update directly using the static method
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            CashbookListWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId);

            // Set result OK
            Intent resultIntent = new Intent();
            resultIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultIntent);
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "Error saving configuration", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ADAPTER
    // ═══════════════════════════════════════════════════════════

    private class CashbookConfigAdapter extends RecyclerView.Adapter<CashbookConfigAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_widget_cashbook_config, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CashbookModel cashbook = allCashbooks.get(position);

            holder.nameText.setText(cashbook.getName());
            holder.balanceText.setText(AmountFormatter.formatCompact(cashbook.getTotalBalance()));

            // Set checkbox state without triggering listener
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(selectedIds.contains(cashbook.getCashbookId()));

            // Checkbox listener
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedIds.add(cashbook.getCashbookId());
                } else {
                    selectedIds.remove(cashbook.getCashbookId());
                }
                updateSelectAllState();
            });

            // Clicking the entire row toggles the checkbox
            holder.itemView.setOnClickListener(v -> holder.checkbox.toggle());
        }

        @Override
        public int getItemCount() {
            return allCashbooks.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkbox;
            TextView nameText;
            TextView balanceText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                checkbox = itemView.findViewById(R.id.config_item_checkbox);
                nameText = itemView.findViewById(R.id.config_item_name);
                balanceText = itemView.findViewById(R.id.config_item_balance);
            }
        }
    }
}
