package com.phynix.artham;

import android.annotation.SuppressLint;
import android.content.Context;
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

public class ExpenseAnalyticsActivity extends AppCompatActivity {

    private static final String TAG = "ExpenseAnalytics";

    // UI
    private PieChart fullScreenPieChart;
    private RecyclerView monthlyCardsRecyclerView, detailedLegendRecyclerView;
    private ImageButton closeButton;
    private TextView noDataTextView;
    private TextView totalExpenseValue;
    private ProgressBar loadingProgressBar;
    private LinearLayout contentLayout;

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

        if (currentUser != null) {
            userId = currentUser.getUid();
            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            cashbookId = prefs.getString("active_cashbook_id_" + userId, getIntent().getStringExtra("cashbook_id"));
        } else {
            cashbookId = getIntent().getStringExtra("cashbook_id");
        }

        if (cashbookId == null || currentUser == null) {
            Log.e(TAG, "Missing cashbookId or User");
            Toast.makeText(this, "Error: Invalid session.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        transactionsRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userId).child("cashbooks")
                .child(cashbookId).child("transactions");

        categoriesRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userId).child("categories");

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

        closeButton.setOnClickListener(v -> finish());
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
        fullScreenPieChart.setExtraOffsets(30.f, 10.f, 30.f, 10.f);
    }

    private void setupRecyclerViews() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        monthlyCardsRecyclerView.setLayoutManager(layoutManager);
        monthlyAdapter = new MonthlyCardAdapter(new ArrayList<>(), this::updatePieChartForMonth);
        monthlyCardsRecyclerView.setAdapter(monthlyAdapter);

        detailedLegendRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        legendAdapter = new LegendAdapter(new ArrayList<>());
        detailedLegendRecyclerView.setAdapter(legendAdapter);
    }

    private void loadCategoriesAndTransactions() {
        loadingProgressBar.setVisibility(View.VISIBLE);

        categoriesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryMap.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        CategoryModel category = dataSnapshot.getValue(CategoryModel.class);
                        if (category != null && category.getName() != null) {
                            // Store user's custom or synced categories
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
        if (totalExpenseValue != null) {
            totalExpenseValue.setText("₹" + String.format(Locale.US, "%.2f", monthlyExpense.getTotalExpense()));
        }

        Map<String, Double> expenseByCategory;
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

            int categoryColor = Color.parseColor("#9E9E9E"); // Ultimate fallback grey
            int categoryIcon = R.drawable.ic_category; // Ultimate fallback icon

            // 1. Check Firebase Map First (prioritizes user custom choices)
            if (categoryMap.containsKey(categoryName)) {
                CategoryModel model = categoryMap.get(categoryName);
                if (model != null) {
                    try {
                        categoryColor = Color.parseColor(model.getColorHex());
                    } catch (Exception ignored) {}
                    categoryIcon = model.getIconResId();
                }
            }
            // 2. Check Static Default Map Second (covers default categories perfectly)
            else {
                CategoryModel defaultModel = getFallbackDefaultCategory(categoryName);
                if (defaultModel != null) {
                    try {
                        categoryColor = Color.parseColor(defaultModel.getColorHex());
                    } catch (Exception ignored) {}
                    categoryIcon = defaultModel.getIconResId();
                }
            }

            entries.add(new PieEntry(amount, categoryName));
            colors.add(categoryColor);

            legendItems.add(new LegendItem(
                    categoryName,
                    amount,
                    (float) (amount / monthlyExpense.getTotalExpense() * 100),
                    categoryColor,
                    categoryIcon
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
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.5f);
        dataSet.setValueLineColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorSecondary));
        dataSet.setValueLineWidth(1f);

        PieData pieData = new PieData(dataSet);
        fullScreenPieChart.setData(pieData);

        String centerText = "Monthly Total\n₹" + String.format(Locale.US, "%.0f", monthlyExpense.getTotalExpense());
        fullScreenPieChart.setCenterText(centerText);
        fullScreenPieChart.setCenterTextSize(14f);
        fullScreenPieChart.setCenterTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorPrimary));

        fullScreenPieChart.animateY(1000, Easing.EaseInOutQuad);
        fullScreenPieChart.invalidate();

        legendAdapter.updateData(legendItems);
    }

    /**
     * Exact mapping from CategorySeeder to ensure Default categories ALWAYS have
     * the correct color and icon if they fail to fetch from Firebase.
     */
    @SuppressLint("DiscouragedApi")
    private CategoryModel getFallbackDefaultCategory(String name) {
        if (name == null) return null;
        String normalized = name.toLowerCase(Locale.US).trim();

        int icFood = getResources().getIdentifier("ic_food_dining", "drawable", getPackageName());
        int icCart = getResources().getIdentifier("ic_groceries", "drawable", getPackageName());
        int icUtil = getResources().getIdentifier("ic_utilities", "drawable", getPackageName());
        int icSubs = getResources().getIdentifier("ic_subscriptions", "drawable", getPackageName());
        int icTran = getResources().getIdentifier("ic_transportation", "drawable", getPackageName());
        int icFlgt = getResources().getIdentifier("ic_flight", "drawable", getPackageName());
        int icHome = getResources().getIdentifier("ic_home", "drawable", getPackageName());
        int icSecu = getResources().getIdentifier("ic_security", "drawable", getPackageName());
        int icRcpt = getResources().getIdentifier("ic_receipt", "drawable", getPackageName());
        int icEntr = getResources().getIdentifier("ic_entertainment", "drawable", getPackageName());
        int icMedc = getResources().getIdentifier("ic_medicine", "drawable", getPackageName());
        int icBook = getResources().getIdentifier("ic_book", "drawable", getPackageName());
        int icMony = getResources().getIdentifier("ic_money", "drawable", getPackageName());
        int icWork = getResources().getIdentifier("ic_work", "drawable", getPackageName());
        int icCatg = getResources().getIdentifier("ic_assignment_return", "drawable", getPackageName());
        int icAllI = getResources().getIdentifier("ic_trending_up", "drawable", getPackageName());

        switch (normalized) {
            case "food & dining": return new CategoryModel("Food & Dining", "OUT", "#FF7043", icFood != 0 ? icFood : R.drawable.ic_food_dining, false);
            case "groceries": return new CategoryModel("Groceries", "OUT", "#8BC34A", icCart != 0 ? icCart : R.drawable.ic_groceries, false);
            case "bills & utility": return new CategoryModel("Bills & Utility", "OUT", "#26A69A", icUtil != 0 ? icUtil : R.drawable.ic_utilities, false);
            case "subscriptions": return new CategoryModel("Subscriptions", "OUT", "#3F51B5", icSubs != 0 ? icSubs : R.drawable.ic_subscriptions, false);
            case "transport": return new CategoryModel("Transport", "OUT", "#29B6F6", icTran != 0 ? icTran : R.drawable.ic_transportation, false);
            case "travel": return new CategoryModel("Travel", "OUT", "#03A9F4", icFlgt != 0 ? icFlgt : R.drawable.ic_flight, false);
            case "rent": return new CategoryModel("Rent", "OUT", "#FFA726", icHome != 0 ? icHome : R.drawable.ic_home, false);
            case "insurance": return new CategoryModel("Insurance", "OUT", "#795548", icSecu != 0 ? icSecu : R.drawable.ic_security, false);
            case "shopping": return new CategoryModel("Shopping", "OUT", "#EC407A", icRcpt != 0 ? icRcpt : R.drawable.ic_shopping_cart, false);
            case "entertainment": return new CategoryModel("Entertainment", "OUT", "#AB47BC", icEntr != 0 ? icEntr : R.drawable.ic_entertainment, false);
            case "health": return new CategoryModel("Health", "OUT", "#EF5350", icMedc != 0 ? icMedc : R.drawable.ic_medicine, false);
            case "education": return new CategoryModel("Education", "OUT", "#5C6BC0", icBook != 0 ? icBook : R.drawable.ic_book, false);
            case "salary": return new CategoryModel("Salary", "IN", "#66BB6A", icMony != 0 ? icMony : R.drawable.ic_money, false);
            case "freelance": return new CategoryModel("Freelance", "IN", "#CDDC39", icWork != 0 ? icWork : R.drawable.ic_work, false);
            case "refunds": return new CategoryModel("Refunds", "IN", "#4DB6AC", icCatg != 0 ? icCatg : R.drawable.ic_assignment_return, false);
            case "investment": return new CategoryModel("Investment", "IN", "#009688", icAllI != 0 ? icAllI : R.drawable.ic_trending_up, false);
        }
        return null;
    }

    private void showEmptyState() {
        noDataTextView.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        if (totalExpenseValue != null) {
            totalExpenseValue.setText("₹0.00");
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
        public LegendItem(String category, float amount, float percentage, int color, int iconResId) {
            this.category = category; this.amount = amount; this.percentage = percentage;
            this.color = color; this.iconResId = iconResId;
        }
    }

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
                int selectedColor = Color.parseColor("#2196F3");

                if(isSel) {
                    bg.setBackgroundColor(selectedColor);
                    month.setTextColor(Color.WHITE);
                    year.setTextColor(Color.parseColor("#E0E0E0"));
                } else {
                    bg.setBackgroundColor(cardBgColor);
                    month.setTextColor(primaryColor);
                    year.setTextColor(secondaryColor);
                }
            }
        }
    }

    static class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        private List<LegendItem> list;
        LegendAdapter(List<LegendItem> list) { this.list = list; }

        @SuppressLint("NotifyDataSetChanged")
        public void updateData(List<LegendItem> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_report, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { holder.bind(list.get(position)); }
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
                amt.setText("₹" + String.format(Locale.US, "%.2f", i.amount));
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

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            if(context.getTheme().resolveAttribute(attr, typedValue, true)) return typedValue.data;
            return Color.BLACK;
        }
    }
}