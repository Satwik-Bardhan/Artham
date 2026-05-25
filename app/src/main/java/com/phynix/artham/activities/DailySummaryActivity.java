package com.phynix.artham.activities;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.BaseActivity;
import com.phynix.artham.adapters.DailyBalanceAdapter;
import com.phynix.artham.databinding.ActivityDailySummaryBinding;
import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.Constants;
import com.phynix.artham.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DailySummaryActivity extends BaseActivity {

    private ActivityDailySummaryBinding binding;
    private DataRepository repository;
    private String cashbookId;

    private DatabaseReference transactionsRef;
    private ValueEventListener transactionsListener;
    private DailyBalanceAdapter adapter;
    private final List<DailyBalanceAdapter.DailyBalanceItem> dailyBalancesList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);

        binding = ActivityDailySummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        repository = DataRepository.getInstance(getApplication());
        cashbookId = getIntent().getStringExtra(Constants.EXTRA_CASHBOOK_ID);

        setupViews();
        subscribeToTransactions();
    }

    private void setupViews() {
        binding.backButton.setOnClickListener(v -> finish());

        adapter = new DailyBalanceAdapter(dailyBalancesList);
        binding.recyclerViewDailyBalances.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewDailyBalances.setAdapter(adapter);
    }

    private void subscribeToTransactions() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || cashbookId == null) {
            showEmptyState(true);
            return;
        }

        transactionsRef = FirebaseDatabase.getInstance().getReference()
                .child(Constants.NODE_USERS)
                .child(currentUser.getUid())
                .child(Constants.NODE_CASHBOOKS)
                .child(cashbookId)
                .child(Constants.NODE_TRANSACTIONS);

        transactionsListener = repository.subscribeToTransactions(cashbookId, transactions -> {
            processTransactions(transactions);
        }, error -> {
            showEmptyState(dailyBalancesList.isEmpty());
        });
    }

    private void processTransactions(List<TransactionModel> transactions) {
        if (transactions == null) {
            transactions = new ArrayList<>();
        }

        // Group by Date string
        Map<String, List<TransactionModel>> groupedByDate = new LinkedHashMap<>();

        // Sort transactions by timestamp descending first
        List<TransactionModel> sortedTransactions = new ArrayList<>(transactions);
        Collections.sort(sortedTransactions, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        SimpleDateFormat dateKeyFormat = new SimpleDateFormat(Constants.DATE_FORMAT_DISPLAY, Locale.US);

        for (TransactionModel t : sortedTransactions) {
            String dateStr = dateKeyFormat.format(new Date(t.getTimestamp()));
            if (!groupedByDate.containsKey(dateStr)) {
                groupedByDate.put(dateStr, new ArrayList<>());
            }
            groupedByDate.get(dateStr).add(t);
        }

        dailyBalancesList.clear();

        for (Map.Entry<String, List<TransactionModel>> entry : groupedByDate.entrySet()) {
            double income = 0;
            double expense = 0;
            for (TransactionModel t : entry.getValue()) {
                if (Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(t.getType())) {
                    income += t.getAmount();
                } else {
                    expense += t.getAmount();
                }
            }
            double net = income - expense;
            dailyBalancesList.add(new DailyBalanceAdapter.DailyBalanceItem(
                    entry.getKey(), income, expense, net
            ));
        }

        adapter.notifyDataSetChanged();
        showEmptyState(dailyBalancesList.isEmpty());
    }

    private void showEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.recyclerViewDailyBalances.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.VISIBLE);
        } else {
            binding.recyclerViewDailyBalances.setVisibility(View.VISIBLE);
            binding.emptyStateView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transactionsRef != null && transactionsListener != null) {
            transactionsRef.removeEventListener(transactionsListener);
        }
        binding = null;
    }
}
