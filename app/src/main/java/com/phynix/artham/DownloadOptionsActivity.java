package com.phynix.artham;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue; // [FIX] Added for ThemeUtil
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DownloadOptionsActivity extends AppCompatActivity {

    private TextView startDateText, endDateText;
    private LinearLayout startDateLayout, endDateLayout;
    private Button todayButton, thisWeekButton, thisMonthButton, formatPdfButton, formatExcelButton, downloadActionButton;
    private RadioGroup entryTypeRadioGroup, paymentModeRadioGroup;
    private ImageView backButton, shareButton;

    private Calendar startCalendar, endCalendar;
    private String selectedFormat = "PDF"; // Default format

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_options);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUI();
        initializeDateTime();
        setupClickListeners();
    }

    private void initializeUI() {
        // Header
        backButton = findViewById(R.id.backButton);
        shareButton = findViewById(R.id.shareButton);

        // Date Range
        startDateText = findViewById(R.id.startDateText);
        endDateText = findViewById(R.id.endDateText);
        startDateLayout = findViewById(R.id.startDateLayout);
        endDateLayout = findViewById(R.id.endDateLayout);
        todayButton = findViewById(R.id.todayButton);
        thisWeekButton = findViewById(R.id.thisWeekButton);
        thisMonthButton = findViewById(R.id.thisMonthButton);

        // Entry Type & Payment Mode
        entryTypeRadioGroup = findViewById(R.id.entryTypeRadioGroup);
        paymentModeRadioGroup = findViewById(R.id.paymentModeRadioGroup);

        // Format Selection
        formatPdfButton = findViewById(R.id.formatPdfButton);
        formatExcelButton = findViewById(R.id.formatExcelButton);

        // Action Button
        downloadActionButton = findViewById(R.id.downloadActionButton);
    }

    private void initializeDateTime() {
        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();
        // Default to the current month
        setDateRangeToThisMonth();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        shareButton.setOnClickListener(v -> Toast.makeText(this, "Share functionality coming soon!", Toast.LENGTH_SHORT).show());

        // Date Listeners
        startDateLayout.setOnClickListener(v -> showDatePicker(true));
        endDateLayout.setOnClickListener(v -> showDatePicker(false));
        todayButton.setOnClickListener(v -> setDateRangeToToday());
        thisWeekButton.setOnClickListener(v -> setDateRangeToThisWeek());
        thisMonthButton.setOnClickListener(v -> setDateRangeToThisMonth());

        // Format Selection Listeners
        formatPdfButton.setOnClickListener(v -> updateFormatSelection(formatPdfButton));
        formatExcelButton.setOnClickListener(v -> updateFormatSelection(formatExcelButton));

        // Download Action
        downloadActionButton.setOnClickListener(v -> returnDownloadOptions());
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendarToShow = isStartDate ? startCalendar : endCalendar;
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (isStartDate) {
                        startCalendar.set(year, month, dayOfMonth, 0, 0, 0);
                    } else {
                        endCalendar.set(year, month, dayOfMonth, 23, 59, 59);
                    }
                    updateDateTextViews();
                },
                calendarToShow.get(Calendar.YEAR),
                calendarToShow.get(Calendar.MONTH),
                calendarToShow.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateDateTextViews() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        startDateText.setText(sdf.format(startCalendar.getTime()));
        endDateText.setText(sdf.format(endCalendar.getTime()));

        // [FIX] Use theme-aware colors
        int textColor = ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary);
        startDateText.setTextColor(textColor);
        endDateText.setTextColor(textColor);
    }

    private void setDateRangeToToday() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);
        updateDateTextViews();
    }

    private void setDateRangeToThisWeek() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.DAY_OF_WEEK, startCalendar.getFirstDayOfWeek());
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = (Calendar) startCalendar.clone();
        endCalendar.add(Calendar.DAY_OF_WEEK, 6);
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);
        updateDateTextViews();
    }

    private void setDateRangeToThisMonth() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);
        updateDateTextViews();
    }

    private void updateFormatSelection(Button selectedButton) {
        if (selectedButton.getId() == R.id.formatPdfButton) {
            selectedFormat = "PDF";
            formatPdfButton.setBackgroundResource(R.drawable.format_button_selected);
            formatPdfButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            formatExcelButton.setBackgroundResource(R.drawable.format_button_unselected);
            formatExcelButton.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorSecondary));
        } else {
            selectedFormat = "Excel";
            formatExcelButton.setBackgroundResource(R.drawable.format_button_selected);
            formatExcelButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            formatPdfButton.setBackgroundResource(R.drawable.format_button_unselected);
            formatPdfButton.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorSecondary));
        }
    }

    private void returnDownloadOptions() {
        // Get selected entry type
        String entryType = "All";
        int selectedEntryTypeId = entryTypeRadioGroup.getCheckedRadioButtonId();
        if (selectedEntryTypeId == R.id.radioCashIn) {
            entryType = "IN"; // [FIX] Use "IN" to match TransactionModel
        } else if (selectedEntryTypeId == R.id.radioCashOut) {
            entryType = "OUT"; // [FIX] Use "OUT" to match TransactionModel
        }

        // Get selected payment mode
        String paymentMode = "All";
        int selectedPaymentModeId = paymentModeRadioGroup.getCheckedRadioButtonId();
        if (selectedPaymentModeId == R.id.radioCashMode) {
            paymentMode = "Cash";
        } else if (selectedPaymentModeId == R.id.radioOnlineMode) {
            paymentMode = "Online";
        }

        // Create result intent and pass back the data
        Intent resultIntent = new Intent();
        resultIntent.putExtra("startDate", startCalendar.getTimeInMillis());
        resultIntent.putExtra("endDate", endCalendar.getTimeInMillis());
        resultIntent.putExtra("entryType", entryType);
        resultIntent.putExtra("paymentMode", paymentMode);
        resultIntent.putExtra("format", selectedFormat);

        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}