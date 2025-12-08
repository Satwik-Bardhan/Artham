package com.phynix.artham;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.CustomPieChartValueFormatter;
import com.phynix.artham.utils.ErrorHandler;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ExpenseAnalyticsActivity extends AppCompatActivity implements OnChartValueSelectedListener {

    private static final String TAG = "ExpenseAnalytics";

    private PieChart fullScreenPieChart;
    private RecyclerView monthlyCardsRecyclerView, detailedLegendRecyclerView;
    private ImageButton closeButton;

    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<MonthlyExpense> monthlyExpenses;
    private MonthlyExpense currentSelectedMonth;
    private LegendAdapter legendAdapter;
    private MonthlyCardAdapter monthlyAdapter;

    private String cashbookId;
    private DatabaseReference transactionsRef;
    private ValueEventListener transactionsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_analytics);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        cashbookId = getIntent().getStringExtra("cashbook_id");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (cashbookId == null || currentUser == null) {
            Toast.makeText(this, "Error: No cashbook specified or user not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        transactionsRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid()).child("cashbooks")
                .child(cashbookId).child("transactions");

        initializeUI();
        setupRecyclerViews();
        setupClickListeners();
        setupPieChart();
        loadTransactionData();
    }

    private void initializeUI() {
        fullScreenPieChart = findViewById(R.id.fullScreenPieChart);
        monthlyCardsRecyclerView = findViewById(R.id.monthlyCardsRecyclerView);
        detailedLegendRecyclerView = findViewById(R.id.detailedLegendRecyclerView);
        closeButton = findViewById(R.id.closeButton);
    }

    private void setupPieChart() {
        fullScreenPieChart.setOnChartValueSelectedListener(this);
        fullScreenPieChart.setRotationEnabled(true);
        fullScreenPieChart.setHighlightPerTapEnabled(true);

        int textColor = ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary);

        fullScreenPieChart.setHoleRadius(58f); // Slightly smaller hole to allow more room for labels
        fullScreenPieChart.setTransparentCircleRadius(62f);
        fullScreenPieChart.setHoleColor(Color.TRANSPARENT);

        fullScreenPieChart.setDrawCenterText(true);
        fullScreenPieChart.setRotationAngle(270);

        fullScreenPieChart.getDescription().setEnabled(false);
        fullScreenPieChart.getLegend().setEnabled(false);

        // Disable standard entry labels (we use the formatter for combined labels)
        fullScreenPieChart.setDrawEntryLabels(false);

        fullScreenPieChart.setNoDataText("No expense data available");
        fullScreenPieChart.setNoDataTextColor(textColor);

        // [FIX] Increased offsets to 50dp to prevent overlap
        fullScreenPieChart.setExtraOffsets(50.f, 10.f, 50.f, 10.f);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e instanceof PieEntry) {
            PieEntry pieEntry = (PieEntry) e;
            String category = pieEntry.getLabel();
            Toast.makeText(this, category + ": ₹" + String.format(Locale.US, "%.2f", pieEntry.getValue()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onNothingSelected() {}

    private void loadTransactionData() {
        if (transactionsRef == null) return;

        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allTransactions.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                    if (transaction != null) {
                        allTransactions.add(transaction);
                    }
                }
                processTransactionData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                ErrorHandler.handleFirebaseError(ExpenseAnalyticsActivity.this, databaseError);
            }
        });
    }

    private void processTransactionData() {
        if (allTransactions == null || allTransactions.isEmpty()) {
            if(monthlyAdapter != null) monthlyAdapter.updateData(new ArrayList<>());
            if(legendAdapter != null) legendAdapter.updateData(new ArrayList<>());
            fullScreenPieChart.clear();
            fullScreenPieChart.invalidate();
            return;
        }

        Map<String, List<TransactionModel>> transactionsByMonth = allTransactions.stream()
                .filter(t -> "OUT".equalsIgnoreCase(t.getType()))
                .collect(Collectors.groupingBy(t -> {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(t.getTimestamp());
                    return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.getTime());
                }));

        monthlyExpenses = new ArrayList<>();
        for (Map.Entry<String, List<TransactionModel>> entry : transactionsByMonth.entrySet()) {
            double total = entry.getValue().stream().mapToDouble(TransactionModel::getAmount).sum();
            monthlyExpenses.add(new MonthlyExpense(entry.getKey(), total, entry.getValue()));
        }

        monthlyExpenses.sort(Comparator.comparing(MonthlyExpense::getMonth).reversed());

        monthlyAdapter.updateData(monthlyExpenses);

        if (!monthlyExpenses.isEmpty()) {
            updatePieChartForMonth(monthlyExpenses.get(0));
            monthlyAdapter.setSelectedPosition(0);
            monthlyCardsRecyclerView.scrollToPosition(0);
        } else {
            if(legendAdapter != null) legendAdapter.updateData(new ArrayList<>());
            fullScreenPieChart.clear();
            fullScreenPieChart.invalidate();
        }
    }

    private void setupRecyclerViews() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, true);
        layoutManager.setStackFromEnd(true);

        monthlyCardsRecyclerView.setLayoutManager(layoutManager);
        monthlyAdapter = new MonthlyCardAdapter(new ArrayList<>(), this::updatePieChartForMonth);
        monthlyCardsRecyclerView.setAdapter(monthlyAdapter);

        detailedLegendRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        legendAdapter = new LegendAdapter(new ArrayList<>());
        detailedLegendRecyclerView.setAdapter(legendAdapter);
    }

    private void setupClickListeners() {
        closeButton.setOnClickListener(v -> finish());
    }

    private void updatePieChartForMonth(MonthlyExpense monthlyExpense) {
        currentSelectedMonth = monthlyExpense;

        Map<String, Double> expenseByCategory = monthlyExpense.getTransactions().stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionCategory() != null ? t.getTransactionCategory() : "Others",
                        Collectors.summingDouble(TransactionModel::getAmount)
                ));

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<LegendItem> legendItems = new ArrayList<>();

        int primaryTextColor = ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary);
        int secondaryTextColor = ThemeUtil.getThemeAttrColor(this, R.attr.textColorSecondary);

        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#FF5252"));
        colors.add(Color.parseColor("#448AFF"));
        colors.add(Color.parseColor("#69F0AE"));
        colors.add(Color.parseColor("#FFD740"));
        colors.add(Color.parseColor("#E040FB"));
        colors.add(Color.parseColor("#18FFFF"));
        colors.add(Color.parseColor("#FF6E40"));
        colors.add(Color.parseColor("#BCAAA4"));
        colors.add(Color.parseColor("#7C4DFF"));
        colors.add(Color.parseColor("#B2FF59"));

        int colorIndex = 0;
        for (Map.Entry<String, Double> entry : expenseByCategory.entrySet()) {
            float amount = entry.getValue().floatValue();
            // PieEntry label is "Category Name"
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

        PieDataSet dataSet = new PieDataSet(entries, "Monthly Expenses");
        dataSet.setColors(colors);

        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        // [FIX] Optimized Line Settings for minimal overlap
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.5f);
        dataSet.setValueLinePart2Length(0.6f); // Long tail to push text away

        dataSet.setValueLineColor(secondaryTextColor);
        dataSet.setValueLineWidth(0.8f);

        dataSet.setValueTextColor(primaryTextColor);
        dataSet.setValueTextSize(10f); // Smaller text

        // [FIX] Use Custom Formatter (Percent \n Label)
        dataSet.setValueFormatter(new CustomPieChartValueFormatter());

        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);

        PieData pieData = new PieData(dataSet);
        fullScreenPieChart.setData(pieData);
        fullScreenPieChart.setUsePercentValues(true);

        // Ensure we do NOT draw separate entry labels, rely on Formatter
        fullScreenPieChart.setDrawEntryLabels(false);

        String centerText = String.format(Locale.US, "Total\n₹%.0f", monthlyExpense.getTotalExpense());
        fullScreenPieChart.setCenterText(centerText);
        fullScreenPieChart.setCenterTextColor(primaryTextColor);
        fullScreenPieChart.setCenterTextSize(18f);

        fullScreenPieChart.animateY(1000);
        fullScreenPieChart.invalidate();

        legendAdapter.updateData(legendItems);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transactionsListener != null && transactionsRef != null) {
            transactionsRef.removeEventListener(transactionsListener);
        }
    }

    // ... (Inner classes remain the same) ...
    // Copy MonthlyExpense, LegendItem, MonthlyCardAdapter, LegendAdapter, ThemeUtil from previous response
    // To save space, I am not re-printing them as they haven't changed,
    // but ensure you keep them in the file.

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

    static class MonthlyCardAdapter extends RecyclerView.Adapter<MonthlyCardAdapter.ViewHolder> {
        private List<MonthlyExpense> monthlyExpenses;
        private OnMonthClickListener clickListener;
        private int selectedPosition = -1;

        MonthlyCardAdapter(List<MonthlyExpense> monthlyExpenses, OnMonthClickListener listener) {
            this.monthlyExpenses = monthlyExpenses; this.clickListener = listener;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void updateData(List<MonthlyExpense> newItems) {
            this.monthlyExpenses = newItems;
            this.selectedPosition = 0;
            notifyDataSetChanged();
        }

        public void setSelectedPosition(int position) {
            int oldPosition = selectedPosition; selectedPosition = position;
            notifyItemChanged(oldPosition); notifyItemChanged(selectedPosition);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monthly_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MonthlyExpense monthlyExpense = monthlyExpenses.get(position);
            holder.bind(monthlyExpense, position == selectedPosition);
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onMonthClick(monthlyExpense);
                int oldPosition = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(oldPosition); notifyItemChanged(selectedPosition);
            });
        }

        @Override
        public int getItemCount() { return monthlyExpenses.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView monthName, totalExpense; LinearLayout cardContainer;
            int primaryTextColor, balanceColor, surfaceColor, expenseColor;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                monthName = itemView.findViewById(R.id.monthNameTextView);
                totalExpense = itemView.findViewById(R.id.totalExpenseTextView);
                cardContainer = itemView.findViewById(R.id.cardContainer);

                primaryTextColor = ThemeUtil.getThemeAttrColor(itemView.getContext(), R.attr.textColorPrimary);
                balanceColor = ThemeUtil.getThemeAttrColor(itemView.getContext(), R.attr.balanceColor);
                surfaceColor = ThemeUtil.getThemeAttrColor(itemView.getContext(), R.attr.surfaceColor);
                expenseColor = ThemeUtil.getThemeAttrColor(itemView.getContext(), R.attr.expenseColor);
            }

            void bind(MonthlyExpense data, boolean isSelected) {
                try {
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
                    SimpleDateFormat formatter = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
                    monthName.setText(formatter.format(parser.parse(data.getMonth())));
                } catch (ParseException e) { monthName.setText(data.getMonth()); }

                totalExpense.setText(String.format(Locale.US, "₹%.0f", data.getTotalExpense()));

                if (isSelected) {
                    cardContainer.setBackgroundColor(balanceColor);
                    monthName.setTextColor(Color.WHITE); totalExpense.setTextColor(Color.WHITE);
                } else {
                    cardContainer.setBackgroundColor(surfaceColor);
                    monthName.setTextColor(primaryTextColor); totalExpense.setTextColor(expenseColor);
                }
            }
        }
    }

    static class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        private List<LegendItem> legendItems;
        LegendAdapter(List<LegendItem> items) { this.legendItems = items; }

        @SuppressLint("NotifyDataSetChanged")
        public void updateData(List<LegendItem> newItems) {
            this.legendItems = newItems; notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_legend_detail, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(legendItems.get(position));
        }

        @Override
        public int getItemCount() { return legendItems.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View colorSwatch; TextView categoryName, amount, percentage;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                colorSwatch = itemView.findViewById(R.id.categoryColorIndicator);
                categoryName = itemView.findViewById(R.id.categoryName);
                amount = itemView.findViewById(R.id.categoryAmount);
                percentage = itemView.findViewById(R.id.categoryPercentage);
            }

            void bind(LegendItem item) {
                colorSwatch.setBackgroundColor(item.color);
                categoryName.setText(item.category);
                amount.setText(String.format(Locale.US, "₹%.2f", item.amount));
                percentage.setText(String.format(Locale.US, "(%.1f%%)", item.percentage));

                categoryName.setTextColor(ThemeUtil.getThemeAttrColor(itemView.getContext(), R.attr.textColorPrimary));
                amount.setTextColor(ThemeUtil.getThemeAttrColor(itemView.getContext(), R.attr.textColorPrimary));
                percentage.setTextColor(ThemeUtil.getThemeAttrColor(itemView.getContext(), R.attr.textColorSecondary));
            }
        }
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}