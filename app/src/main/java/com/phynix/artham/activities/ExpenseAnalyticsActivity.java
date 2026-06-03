package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.DefaultCategoryManager;
import com.phynix.artham.utils.CategoryColorUtil;
import com.phynix.artham.utils.ThemeManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.phynix.artham.utils.ThemeUtil;
public class ExpenseAnalyticsActivity extends BaseActivity {

    private static final String TAG = "ExpenseAnalytics";

    // UI
    private PieChart fullScreenPieChart;
    private RecyclerView monthlyCardsRecyclerView, detailedLegendRecyclerView;
    private ImageButton closeButton;
    private View noDataTextView;
    private TextView totalExpenseValue, stickyTotalExpenseValue;
    private View stickyTotalExpenseCard;
    private androidx.core.widget.NestedScrollView analyticsScrollView;
    private ProgressBar loadingProgressBar;
    private View contentLayout; // FrameLayout is a subclass of View, so View is fine!
    private String currentMonthLabel = "";

    // Data
    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<MonthlyExpense> monthlyExpenses = new ArrayList<>();

    // Map to store categories fetched from Firebase
    private final Map<String, CategoryModel> categoryMap = new HashMap<>();

    private MonthlyCardAdapter monthlyAdapter;
    private LegendAdapter legendAdapter;

    // Firebase
    private String cashbookId;
    private String userId;
    private DatabaseReference transactionsRef;
    private DatabaseReference categoriesRef;
    private ValueEventListener transactionsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_analytics);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean isLocal = com.phynix.artham.db.DataRepository.getInstance(getApplication()).isLocalMode();

        if (currentUser != null) {
            userId = currentUser.getUid();
            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            cashbookId = prefs.getString("active_cashbook_id_" + userId, getIntent().getStringExtra("cashbook_id"));
        } else {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            cashbookId = prefs.getString("active_cashbook_id_local_user", getIntent().getStringExtra("cashbook_id"));
        }

        if (cashbookId == null || (!isLocal && currentUser == null)) {
            Log.e(TAG, "Missing cashbookId or User");
            Toast.makeText(this, "Error: Invalid session.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!isLocal) {
            transactionsRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(userId).child("cashbooks")
                    .child(cashbookId).child("transactions");

            categoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(userId).child("categories");
        }

        initializeUI();
        setupRecyclerViews();
        setupPieChart();

        loadCategoriesAndTransactions();
    }

    private void initializeUI() {
        fullScreenPieChart = findViewById(R.id.fullScreenPieChart);
        monthlyCardsRecyclerView = findViewById(R.id.monthlyCardsRecyclerView);
        detailedLegendRecyclerView = findViewById(R.id.detailedLegendRecyclerView);
        closeButton = findViewById(R.id.closeButton);
        noDataTextView = findViewById(R.id.noDataTextView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        contentLayout = findViewById(R.id.contentLayout);
        totalExpenseValue = findViewById(R.id.totalExpenseValue);
        stickyTotalExpenseValue = findViewById(R.id.stickyTotalExpenseValue);
        stickyTotalExpenseCard = findViewById(R.id.stickyTotalExpenseCard);
        analyticsScrollView = findViewById(R.id.analyticsScrollView);

        closeButton.setOnClickListener(v -> finish());

        if (analyticsScrollView != null && stickyTotalExpenseCard != null) {
            analyticsScrollView.setOnScrollChangeListener(new androidx.core.widget.NestedScrollView.OnScrollChangeListener() {
                @Override
                public void onScrollChange(androidx.core.widget.NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    View inlineCard = findViewById(R.id.totalExpenseCard);
                    if (inlineCard != null) {
                        int inlineCardTop = inlineCard.getTop();
                        if (scrollY >= inlineCardTop) {
                            stickyTotalExpenseCard.setVisibility(View.VISIBLE);
                        } else {
                            stickyTotalExpenseCard.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }
    }

    private void setupPieChart() {
        fullScreenPieChart.setRotationEnabled(true);
        fullScreenPieChart.setHoleRadius(55f);
        fullScreenPieChart.setTransparentCircleRadius(60f);
        fullScreenPieChart.setHoleColor(Color.TRANSPARENT);
        fullScreenPieChart.setDrawCenterText(true);
        fullScreenPieChart.getDescription().setEnabled(false);
        fullScreenPieChart.getLegend().setEnabled(false);
        fullScreenPieChart.setDrawEntryLabels(true);
        fullScreenPieChart.setEntryLabelTextSize(9f);
        fullScreenPieChart.setEntryLabelColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorPrimary));
        fullScreenPieChart.setExtraOffsets(24f, 24f, 24f, 36f);
    }

    private void setupRecyclerViews() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        monthlyCardsRecyclerView.setLayoutManager(layoutManager);
        monthlyAdapter = new MonthlyCardAdapter(new ArrayList<>(), this::updatePieChartForMonth);
        monthlyCardsRecyclerView.setAdapter(monthlyAdapter);

        detailedLegendRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        legendAdapter = new LegendAdapter(new ArrayList<>(), this::onCategoryClicked);
        detailedLegendRecyclerView.setAdapter(legendAdapter);
    }

    private void onCategoryClicked(LegendItem item) {
        Intent intent = new Intent(this, CategoryDetailActivity.class);
        intent.putExtra(CategoryDetailActivity.EXTRA_CATEGORY_NAME, item.category);
        intent.putExtra(CategoryDetailActivity.EXTRA_CATEGORY_AMOUNT, item.amount);
        intent.putExtra(CategoryDetailActivity.EXTRA_CATEGORY_PERCENTAGE, item.percentage);
        intent.putExtra(CategoryDetailActivity.EXTRA_CATEGORY_COLOR, item.color);
        intent.putExtra(CategoryDetailActivity.EXTRA_CATEGORY_ICON_RES_ID, item.iconResId);
        intent.putExtra(CategoryDetailActivity.EXTRA_MONTH_LABEL, currentMonthLabel);
        intent.putExtra(CategoryDetailActivity.EXTRA_TRANSACTIONS, item.transactions);
        intent.putExtra(CategoryDetailActivity.EXTRA_CASHBOOK_ID, cashbookId);
        startActivity(intent);
    }

    private void loadCategoriesAndTransactions() {
        boolean isLocal = com.phynix.artham.db.DataRepository.getInstance(getApplication()).isLocalMode();
        if (isLocal) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            com.phynix.artham.db.DataRepository.getInstance(getApplication()).getCategories(cashbookId, categories -> {
                categoryMap.clear();
                for (CategoryModel cat : categories) {
                    if (cat.getName() != null) {
                        categoryMap.put(cat.getName(), cat);
                    }
                }
                com.phynix.artham.db.DataRepository.getInstance(getApplication()).getAllTransactions(cashbookId, transactions -> {
                    allTransactions.clear();
                    allTransactions.addAll(transactions);
                    processTransactionData();
                }, error -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    Toast.makeText(ExpenseAnalyticsActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
                });
            });
            return;
        }

        loadingProgressBar.setVisibility(View.VISIBLE);

        categoriesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryMap.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        CategoryModel category = dataSnapshot.getValue(CategoryModel.class);
                        if (category != null && category.getName() != null) {
                            categoryMap.put(category.getName(), category);
                        }
                    }
                }
                loadTransactionData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load categories: " + error.getMessage());
                loadTransactionData();
            }
        });
    }

    private void loadTransactionData() {
        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allTransactions.clear();
                if (dataSnapshot.exists()) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                        if (transaction != null) {
                            allTransactions.add(transaction);
                        }
                    }
                }
                processTransactionData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                loadingProgressBar.setVisibility(View.GONE);
                Toast.makeText(ExpenseAnalyticsActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processTransactionData() {
        loadingProgressBar.setVisibility(View.GONE);

        List<TransactionModel> expenses = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            expenses = allTransactions.stream()
                    .filter(t -> "OUT".equalsIgnoreCase(t.getType()))
                    .collect(Collectors.toList());
        } else {
            for (TransactionModel t : allTransactions) {
                if ("OUT".equalsIgnoreCase(t.getType())) {
                    expenses.add(t);
                }
            }
        }

        if (expenses.isEmpty()) {
            showEmptyState();
            return;
        }

        showContentState();

        Map<String, List<TransactionModel>> transactionsByMonth = new HashMap<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            transactionsByMonth = expenses.stream()
                    .collect(Collectors.groupingBy(t -> {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(t.getTimestamp());
                        return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.getTime());
                    }));
        } else {
            for (TransactionModel t : expenses) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(t.getTimestamp());
                String monthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.getTime());
                if (!transactionsByMonth.containsKey(monthKey)) {
                    transactionsByMonth.put(monthKey, new ArrayList<>());
                }
                transactionsByMonth.get(monthKey).add(t);
            }
        }

        monthlyExpenses.clear();
        for (Map.Entry<String, List<TransactionModel>> entry : transactionsByMonth.entrySet()) {
            double total = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                total = entry.getValue().stream().mapToDouble(TransactionModel::getAmount).sum();
            } else {
                for (TransactionModel t : entry.getValue()) total += t.getAmount();
            }
            monthlyExpenses.add(new MonthlyExpense(entry.getKey(), total, entry.getValue()));
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            monthlyExpenses.sort(Comparator.comparing(MonthlyExpense::getMonth));
        } else {
            Collections.sort(monthlyExpenses, (o1, o2) -> o1.getMonth().compareTo(o2.getMonth()));
        }

        monthlyAdapter.updateData(monthlyExpenses);

        if (!monthlyExpenses.isEmpty()) {
            int newestMonthIndex = monthlyExpenses.size() - 1;
            updatePieChartForMonth(monthlyExpenses.get(newestMonthIndex));
            monthlyCardsRecyclerView.scrollToPosition(newestMonthIndex);
        }
    }

    private void updatePieChartForMonth(MonthlyExpense monthlyExpense) {
        // Format and store month label for category detail navigation
        try {
            java.util.Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(monthlyExpense.getMonth());
            currentMonthLabel = new SimpleDateFormat("MMMM yyyy", Locale.US).format(date);
        } catch (ParseException e) {
            currentMonthLabel = monthlyExpense.getMonth();
        }

        if (totalExpenseValue != null) {
            AmountFormatter.setAdaptiveAmount(totalExpenseValue, monthlyExpense.getTotalExpense(), 20f, 12f);
        }
        if (stickyTotalExpenseValue != null) {
            AmountFormatter.setAdaptiveAmount(stickyTotalExpenseValue, monthlyExpense.getTotalExpense(), 20f, 12f);
        }

        Map<String, Double> expenseByCategory;
        Map<String, ArrayList<TransactionModel>> transactionsByCategory = new HashMap<>();

        // Group transactions by category (both amounts and transaction lists)
        for (TransactionModel t : monthlyExpense.getTransactions()) {
            String cat = t.getTransactionCategory() != null ? t.getTransactionCategory() : "Others";
            if (!transactionsByCategory.containsKey(cat)) {
                transactionsByCategory.put(cat, new ArrayList<>());
            }
            transactionsByCategory.get(cat).add(t);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            expenseByCategory = monthlyExpense.getTransactions().stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getTransactionCategory() != null ? t.getTransactionCategory() : "Others",
                            Collectors.summingDouble(TransactionModel::getAmount)
                    ));
        } else {
            expenseByCategory = new HashMap<>();
            for (TransactionModel t : monthlyExpense.getTransactions()) {
                String cat = t.getTransactionCategory() != null ? t.getTransactionCategory() : "Others";
                expenseByCategory.put(cat, expenseByCategory.getOrDefault(cat, 0.0) + t.getAmount());
            }
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<LegendItem> legendItems = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        for (Map.Entry<String, Double> entry : expenseByCategory.entrySet()) {
            String categoryName = entry.getKey();
            float amount = entry.getValue().floatValue();

            // 1. Use centralized CategoryColorUtil for consistent resolution
            //    (checks Firebase custom category cache → DefaultCategoryManager → hardcoded fallbacks)
            int categoryColor = CategoryColorUtil.getCategoryColor(ExpenseAnalyticsActivity.this, categoryName);
            int categoryIcon = CategoryColorUtil.getCategoryIcon(categoryName);

            // 2. Overlay with locally-loaded categoryMap for most accurate result
            //    This catches categories that might not yet be in CategoryColorUtil's cache
            if (categoryMap.containsKey(categoryName)) {
                CategoryModel fbModel = categoryMap.get(categoryName);
                if (fbModel != null) {
                    if (fbModel.isCustom()) {
                        // Custom categories: use stored color and icon from Firebase
                        if (fbModel.getColorHex() != null) {
                            try {
                                categoryColor = Color.parseColor(fbModel.getColorHex());
                            } catch (Exception ignored) {}
                        }
                        if (fbModel.getIconResId() != 0) {
                            categoryIcon = fbModel.getIconResId();
                        }
                    } else {
                        // Default categories: resolve icon by name (Firebase iconResId is stale)
                        // but allow color override if user changed it
                        CategoryModel defaultModel = DefaultCategoryManager.getCategoryByName(categoryName);
                        if (defaultModel != null) {
                            categoryIcon = defaultModel.getIconResId();
                            try { categoryColor = Color.parseColor(defaultModel.getColorHex()); } catch (Exception ignored) {}
                        }
                    }
                }
            }

            entries.add(new PieEntry(amount, categoryName));
            colors.add(categoryColor);

            ArrayList<TransactionModel> catTransactions = transactionsByCategory.containsKey(categoryName)
                    ? transactionsByCategory.get(categoryName)
                    : new ArrayList<>();

            legendItems.add(new LegendItem(
                    categoryName,
                    amount,
                    (float) (amount / monthlyExpense.getTotalExpense() * 100),
                    categoryColor,
                    categoryIcon,
                    catTransactions
            ));
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            legendItems.sort((a, b) -> Float.compare(b.amount, a.amount));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setDrawValues(false);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.3f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setValueLineColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorSecondary));
        dataSet.setValueLineWidth(1f);

        PieData pieData = new PieData(dataSet);
        fullScreenPieChart.setData(pieData);

        String centerText = "Monthly Total\n" + AmountFormatter.formatCompact(monthlyExpense.getTotalExpense());
        fullScreenPieChart.setCenterText(centerText);
        float centerTextSize = 14f;
        if (centerText.length() > 22) {
            centerTextSize = 10f;
        } else if (centerText.length() > 18) {
            centerTextSize = 12f;
        }
        fullScreenPieChart.setCenterTextSize(centerTextSize);
        fullScreenPieChart.setCenterTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorPrimary));

        fullScreenPieChart.animateY(1000, Easing.EaseInOutQuad);
        fullScreenPieChart.invalidate();

        legendAdapter.updateData(legendItems);
    }

    private void showEmptyState() {
        noDataTextView.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        if (totalExpenseValue != null) {
            AmountFormatter.setAdaptiveAmount(totalExpenseValue, 0, 20f, 12f);
        }
        if (stickyTotalExpenseValue != null) {
            AmountFormatter.setAdaptiveAmount(stickyTotalExpenseValue, 0, 20f, 12f);
        }
    }

    private void showContentState() {
        noDataTextView.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transactionsListener != null && transactionsRef != null) {
            transactionsRef.removeEventListener(transactionsListener);
        }
    }

    // --- Inner Classes ---

    static class MonthlyExpense {
        private String month; private double totalExpense; private List<TransactionModel> transactions;
        public MonthlyExpense(String month, double totalExpense, List<TransactionModel> transactions) {
            this.month = month; this.totalExpense = totalExpense; this.transactions = transactions;
        }
        public String getMonth() { return month; }
        public double getTotalExpense() { return totalExpense; }
        public List<TransactionModel> getTransactions() { return transactions; }
    }

    static class LegendItem {
        String category; float amount; float percentage; int color; int iconResId;
        ArrayList<TransactionModel> transactions;
        public LegendItem(String category, float amount, float percentage, int color, int iconResId, ArrayList<TransactionModel> transactions) {
            this.category = category; this.amount = amount; this.percentage = percentage;
            this.color = color; this.iconResId = iconResId; this.transactions = transactions;
        }
    }

    interface OnCategoryClickListener { void onCategoryClick(LegendItem item); }

    interface OnMonthClickListener { void onMonthClick(MonthlyExpense monthlyExpense); }

    // --- Adapters ---

    static class MonthlyCardAdapter extends RecyclerView.Adapter<MonthlyCardAdapter.ViewHolder> {
        private List<MonthlyExpense> list;
        private OnMonthClickListener listener;
        private int selectedPosition = -1;

        MonthlyCardAdapter(List<MonthlyExpense> list, OnMonthClickListener listener) {
            this.list = list; this.listener = listener;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void updateData(List<MonthlyExpense> newList) {
            this.list = newList;
            this.selectedPosition = !list.isEmpty() ? list.size() - 1 : -1;
            notifyDataSetChanged();
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monthly_expense_card, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MonthlyExpense item = list.get(position);
            holder.bind(item, position == selectedPosition);
            holder.itemView.setOnClickListener(v -> {
                int prev = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                listener.onMonthClick(item);
            });
        }

        @Override public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView month, year; LinearLayout bg;

            ViewHolder(View v) {
                super(v);
                month = v.findViewById(R.id.monthNameTextView);
                year = v.findViewById(R.id.yearTextView);
                bg = v.findViewById(R.id.cardContainer);
            }

            void bind(MonthlyExpense data, boolean isSel) {
                try {
                    Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(data.getMonth());
                    month.setText(new SimpleDateFormat("MMMM", Locale.US).format(date));
                    year.setText(new SimpleDateFormat("yyyy", Locale.US).format(date));
                } catch (ParseException e) {
                    month.setText(data.getMonth());
                    year.setText("");
                }

                Context ctx = itemView.getContext();
                int primaryColor = ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_textColorPrimary);
                int secondaryColor = ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_textColorSecondary);
                int cardBgColor = ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_surfaceColor);
                int dividerColor = ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_dividerHorizontal);
                int selectedColor = ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_primary_blue);

                com.google.android.material.card.MaterialCardView cardRoot = (com.google.android.material.card.MaterialCardView) itemView;
                cardRoot.setCardBackgroundColor(isSel ? selectedColor : cardBgColor);
                cardRoot.setStrokeColor(isSel ? selectedColor : dividerColor);

                // Reset the inner layout's background to avoid solid color overriding the card
                bg.setBackgroundColor(Color.TRANSPARENT);

                if(isSel) {
                    month.setTextColor(Color.WHITE);
                    year.setTextColor(Color.parseColor("#E0E0E0"));
                } else {
                    month.setTextColor(primaryColor);
                    year.setTextColor(secondaryColor);
                }
            }
        }
    }

    static class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        private List<LegendItem> list;
        private final OnCategoryClickListener clickListener;

        LegendAdapter(List<LegendItem> list, OnCategoryClickListener clickListener) {
            this.list = list;
            this.clickListener = clickListener;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void updateData(List<LegendItem> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_report, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LegendItem item = list.get(position);
            holder.bind(item);
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onCategoryClick(item);
            });
        }
        @Override public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View iconContainer;
            ImageView icon;
            TextView cat, amt, pct;
            ProgressBar progressBar;

            ViewHolder(View v) {
                super(v);
                iconContainer = v.findViewById(R.id.iconContainer);
                icon = v.findViewById(R.id.categoryIcon);
                cat = v.findViewById(R.id.categoryName);
                amt = v.findViewById(R.id.categoryAmount);
                pct = v.findViewById(R.id.categoryPercentage);
                progressBar = v.findViewById(R.id.categoryProgressBar);
            }
            void bind(LegendItem i) {
                cat.setText(i.category);
                AmountFormatter.setAdaptiveAmount(amt, i.amount, 14f, 9f);
                pct.setText(String.format(Locale.US, "%.1f%%", i.percentage));

                if (progressBar != null) {
                    progressBar.setProgress((int) i.percentage);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(i.color));
                    }
                }

                if (iconContainer != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        iconContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(i.color));
                    } else {
                        iconContainer.getBackground().setColorFilter(i.color, PorterDuff.Mode.SRC_IN);
                    }
                }

                if (icon != null) {
                    if (i.iconResId != 0) {
                        icon.setImageResource(i.iconResId);
                    } else {
                        icon.setImageResource(R.drawable.ic_category);
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    }
                }

                Context ctx = itemView.getContext();
                cat.setTextColor(ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_textColorPrimary));
                amt.setTextColor(ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_textColorPrimary));
                pct.setTextColor(ThemeUtil.getThemeAttrColor(ctx, R.attr.chk_textColorSecondary));
            }
        }
    }
}