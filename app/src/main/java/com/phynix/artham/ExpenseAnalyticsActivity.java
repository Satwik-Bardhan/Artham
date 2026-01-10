package com.phynix.artham;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.models.TransactionModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
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
    private ProgressBar loadingProgressBar;
    private LinearLayout contentLayout;

    // Data
    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<MonthlyExpense> monthlyExpenses = new ArrayList<>();
    private MonthlyCardAdapter monthlyAdapter;
    private LegendAdapter legendAdapter;

    // Firebase
    private String cashbookId;
    private DatabaseReference transactionsRef;
    private ValueEventListener transactionsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_analytics);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        cashbookId = getIntent().getStringExtra("cashbook_id");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (cashbookId == null || currentUser == null) {
            Log.e(TAG, "Missing cashbookId or User");
            Toast.makeText(this, "Error: Invalid session.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        transactionsRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid()).child("cashbooks")
                .child(cashbookId).child("transactions");

        initializeUI();
        setupRecyclerViews();
        setupPieChart();
        loadTransactionData();
    }

    private void initializeUI() {
        fullScreenPieChart = findViewById(R.id.fullScreenPieChart);
        monthlyCardsRecyclerView = findViewById(R.id.monthlyCardsRecyclerView);
        detailedLegendRecyclerView = findViewById(R.id.detailedLegendRecyclerView);
        closeButton = findViewById(R.id.closeButton);
        noDataTextView = findViewById(R.id.noDataTextView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        contentLayout = findViewById(R.id.contentLayout);

        closeButton.setOnClickListener(v -> finish());
    }

    private void setupPieChart() {
        fullScreenPieChart.setRotationEnabled(true);

        // [FIX] Larger Doughnut: Reduced hole radius (was 60f)
        fullScreenPieChart.setHoleRadius(55f);
        fullScreenPieChart.setTransparentCircleRadius(60f);

        fullScreenPieChart.setHoleColor(Color.TRANSPARENT);
        fullScreenPieChart.setDrawCenterText(true);
        fullScreenPieChart.getDescription().setEnabled(false);
        fullScreenPieChart.getLegend().setEnabled(false);

        // [FIX] Show Labels: Enable category names on chart
        fullScreenPieChart.setDrawEntryLabels(true);

        // [FIX] Small Text: Set label size to 9f
        fullScreenPieChart.setEntryLabelTextSize(9f);
        fullScreenPieChart.setEntryLabelColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorPrimary));

        // [FIX] Larger Chart: Reduced offsets (was 55f) to give chart more screen space
        fullScreenPieChart.setExtraOffsets(30.f, 10.f, 30.f, 10.f);
    }

    private void setupRecyclerViews() {
        monthlyCardsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        monthlyAdapter = new MonthlyCardAdapter(new ArrayList<>(), this::updatePieChartForMonth);
        monthlyCardsRecyclerView.setAdapter(monthlyAdapter);

        detailedLegendRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        legendAdapter = new LegendAdapter(new ArrayList<>());
        detailedLegendRecyclerView.setAdapter(legendAdapter);
    }

    private void loadTransactionData() {
        loadingProgressBar.setVisibility(View.VISIBLE);

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

        List<TransactionModel> expenses = allTransactions.stream()
                .filter(t -> "OUT".equalsIgnoreCase(t.getType()))
                .collect(Collectors.toList());

        if (expenses.isEmpty()) {
            showEmptyState();
            return;
        }

        showContentState();

        Map<String, List<TransactionModel>> transactionsByMonth = expenses.stream()
                .collect(Collectors.groupingBy(t -> {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(t.getTimestamp());
                    return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.getTime());
                }));

        monthlyExpenses.clear();
        for (Map.Entry<String, List<TransactionModel>> entry : transactionsByMonth.entrySet()) {
            double total = entry.getValue().stream().mapToDouble(TransactionModel::getAmount).sum();
            monthlyExpenses.add(new MonthlyExpense(entry.getKey(), total, entry.getValue()));
        }

        monthlyExpenses.sort(Comparator.comparing(MonthlyExpense::getMonth).reversed());
        monthlyAdapter.updateData(monthlyExpenses);

        if (!monthlyExpenses.isEmpty()) {
            updatePieChartForMonth(monthlyExpenses.get(0));
        }
    }

    private void updatePieChartForMonth(MonthlyExpense monthlyExpense) {
        Map<String, Double> expenseByCategory = monthlyExpense.getTransactions().stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionCategory() != null ? t.getTransactionCategory() : "Others",
                        Collectors.summingDouble(TransactionModel::getAmount)
                ));

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<LegendItem> legendItems = new ArrayList<>();
        ArrayList<Integer> colors = getChartColors();

        int colorIndex = 0;
        for (Map.Entry<String, Double> entry : expenseByCategory.entrySet()) {
            float amount = entry.getValue().floatValue();

            // [FIX] Always add the label (Category Name) for every slice
            entries.add(new PieEntry(amount, entry.getKey()));

            int color = colors.get(colorIndex % colors.size());
            legendItems.add(new LegendItem(
                    entry.getKey(),
                    amount,
                    (float) (amount / monthlyExpense.getTotalExpense() * 100),
                    color
            ));
            colorIndex++;
        }

        legendItems.sort((a, b) -> Float.compare(b.amount, a.amount));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);

        // Hide numbers (values) on chart to avoid clutter, showing only Labels
        dataSet.setDrawValues(false);

        // [FIX] Label Positioning: Outside Slice
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        // Line Settings
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.5f);
        dataSet.setValueLineColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorSecondary));
        dataSet.setValueLineWidth(1f);

        PieData pieData = new PieData(dataSet);
        fullScreenPieChart.setData(pieData);

        String centerText = "Total\n₹" + String.format(Locale.US, "%.0f", monthlyExpense.getTotalExpense());
        fullScreenPieChart.setCenterText(centerText);
        fullScreenPieChart.setCenterTextSize(16f);
        fullScreenPieChart.setCenterTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorPrimary));

        fullScreenPieChart.animateY(1000, Easing.EaseInOutQuad);
        fullScreenPieChart.invalidate();

        legendAdapter.updateData(legendItems);
    }

    private void showEmptyState() {
        noDataTextView.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
    }

    private void showContentState() {
        noDataTextView.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
    }

    private ArrayList<Integer> getChartColors() {
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#FF5252"));
        colors.add(Color.parseColor("#448AFF"));
        colors.add(Color.parseColor("#69F0AE"));
        colors.add(Color.parseColor("#FFD740"));
        colors.add(Color.parseColor("#E040FB"));
        colors.add(Color.parseColor("#FF5722"));
        colors.add(Color.parseColor("#00BCD4"));
        colors.add(Color.parseColor("#8BC34A"));
        colors.add(Color.parseColor("#9C27B0"));
        colors.add(Color.parseColor("#795548"));
        return colors;
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
        String category; float amount; float percentage; int color;
        public LegendItem(String category, float amount, float percentage, int color) {
            this.category = category; this.amount = amount; this.percentage = percentage; this.color = color;
        }
    }

    interface OnMonthClickListener { void onMonthClick(MonthlyExpense monthlyExpense); }

    // --- Adapters ---

    static class MonthlyCardAdapter extends RecyclerView.Adapter<MonthlyCardAdapter.ViewHolder> {
        private List<MonthlyExpense> list;
        private OnMonthClickListener listener;
        private int selectedPosition = 0;

        MonthlyCardAdapter(List<MonthlyExpense> list, OnMonthClickListener listener) {
            this.list = list; this.listener = listener;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void updateData(List<MonthlyExpense> newList) {
            this.list = newList;
            this.selectedPosition = 0;
            notifyDataSetChanged();
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monthly_card, parent, false));
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
            TextView month, year, total; LinearLayout bg;

            ViewHolder(View v) {
                super(v);
                month = v.findViewById(R.id.monthNameTextView);
                year = v.findViewById(R.id.yearTextView);
                total = v.findViewById(R.id.totalExpenseTextView);
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

                total.setText("₹" + String.format(Locale.US, "%.0f", data.getTotalExpense()));

                if(isSel) {
                    bg.setBackgroundColor(Color.parseColor("#2196F3"));
                    month.setTextColor(Color.WHITE);
                    year.setTextColor(Color.parseColor("#E0E0E0"));
                    total.setTextColor(Color.WHITE);
                } else {
                    bg.setBackgroundColor(Color.WHITE);
                    month.setTextColor(Color.BLACK);
                    year.setTextColor(Color.DKGRAY);
                    total.setTextColor(Color.BLACK);
                }
            }
        }
    }

    static class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        private List<LegendItem> list;
        LegendAdapter(List<LegendItem> list) { this.list = list; }
        public void updateData(List<LegendItem> newList) { this.list = newList; notifyDataSetChanged(); }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_legend_detail, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { holder.bind(list.get(position)); }
        @Override public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View color; TextView cat, amt, pct;
            ViewHolder(View v) { super(v); color=v.findViewById(R.id.categoryColorIndicator); cat=v.findViewById(R.id.categoryName); amt=v.findViewById(R.id.categoryAmount); pct=v.findViewById(R.id.categoryPercentage); }
            void bind(LegendItem i) {
                color.setBackgroundColor(i.color);
                cat.setText(i.category);
                amt.setText("₹" + String.format(Locale.US, "%.2f", i.amount));
                pct.setText(String.format(Locale.US, "(%.1f%%)", i.percentage));
                cat.setTextColor(Color.BLACK);
                amt.setTextColor(Color.BLACK);
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