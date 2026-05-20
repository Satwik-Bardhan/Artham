package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import com.phynix.artham.utils.ThemeUtil;
import com.phynix.artham.utils.ThemeManager;
public class TransactionExportActivity extends BaseActivity {

    // UI Elements
    private TextView startDateText, endDateText;
    private LinearLayout startDateLayout, endDateLayout;
    private Button todayButton, thisWeekButton, thisMonthButton, allEntriesButton, formatPdfButton, formatExcelButton, downloadActionButton;
    private RadioGroup entryTypeRadioGroup, paymentModeRadioGroup;
    private ImageView backButton;
    private CheckBox greyscaleCheckbox;

    // Logic
    private Calendar startCalendar, endCalendar;
    private String selectedFormat = "PDF";

    // [FIX] Reference to dialog to prevent WindowLeaked crash
    private DatePickerDialog currentDatePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_export);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUI();
        initializeDateTime();
        setupClickListeners();
    }

    private void initializeUI() {
        backButton = findViewById(R.id.backButton);

        startDateText = findViewById(R.id.startDateText);
        endDateText = findViewById(R.id.endDateText);
        startDateLayout = findViewById(R.id.startDateLayout);
        endDateLayout = findViewById(R.id.endDateLayout);

        todayButton = findViewById(R.id.todayButton);
        thisWeekButton = findViewById(R.id.thisWeekButton);
        thisMonthButton = findViewById(R.id.thisMonthButton);
        allEntriesButton = findViewById(R.id.allEntriesButton);

        entryTypeRadioGroup = findViewById(R.id.entryTypeRadioGroup);
        paymentModeRadioGroup = findViewById(R.id.paymentModeRadioGroup);

        greyscaleCheckbox = findViewById(R.id.greyscaleCheckbox);

        formatPdfButton = findViewById(R.id.formatPdfButton);
        formatExcelButton = findViewById(R.id.formatExcelButton);
        downloadActionButton = findViewById(R.id.downloadActionButton);
    }

    private void initializeDateTime() {
        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();

        // Reset to start of day / end of day for safety
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        // Default to This Month view
        setDateRangeToThisMonth();
    }

    private void setupClickListeners() {
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        // Date Picker Logic
        if (startDateLayout != null) {
            startDateLayout.setOnClickListener(v -> showDatePicker(true));
        }

        if (endDateLayout != null) {
            endDateLayout.setOnClickListener(v -> showDatePicker(false));
        }

        // Quick Options
        if (todayButton != null) todayButton.setOnClickListener(v -> setDateRangeToToday());
        if (thisWeekButton != null) thisWeekButton.setOnClickListener(v -> setDateRangeToThisWeek());
        if (thisMonthButton != null) thisMonthButton.setOnClickListener(v -> setDateRangeToThisMonth());
        if (allEntriesButton != null) allEntriesButton.setOnClickListener(v -> setDateRangeToAllEntries());

        // Format Selection
        if (formatPdfButton != null) formatPdfButton.setOnClickListener(v -> updateFormatSelection(formatPdfButton));
        if (formatExcelButton != null) formatExcelButton.setOnClickListener(v -> updateFormatSelection(formatExcelButton));

        // Submit
        if (downloadActionButton != null) downloadActionButton.setOnClickListener(v -> returnDownloadOptions());
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendarToShow = isStartDate ? startCalendar : endCalendar;

        // [FIX] Assign dialog to variable so we can dismiss it in onDestroy
        currentDatePicker = new DatePickerDialog(
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

        currentDatePicker.show();
    }

    private void updateDateTextViews() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        if (startDateText != null) {
            startDateText.setText(sdf.format(startCalendar.getTime()));
            // Ensure text color is visible (using ThemeUtil)
            startDateText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorPrimary));
        }

        if (endDateText != null) {
            endDateText.setText(sdf.format(endCalendar.getTime()));
            endDateText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorPrimary));
        }
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
        // Set to first day of week (e.g., Sunday or Monday based on locale)
        startCalendar.set(Calendar.DAY_OF_WEEK, startCalendar.getFirstDayOfWeek());
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = (Calendar) startCalendar.clone();
        endCalendar.add(Calendar.DAY_OF_WEEK, 6); // Add 6 days to get to end of week
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

    private void setDateRangeToAllEntries() {
        // Set start to epoch (Jan 1, 2000) and end to far future to capture all entries
        startCalendar = Calendar.getInstance();
        startCalendar.set(2000, Calendar.JANUARY, 1, 0, 0, 0);

        endCalendar = Calendar.getInstance();
        endCalendar.set(2099, Calendar.DECEMBER, 31, 23, 59, 59);

        updateDateTextViews();
    }

    private void updateFormatSelection(Button selectedButton) {
        if (selectedButton.getId() == R.id.formatPdfButton) {
            selectedFormat = "PDF";
            formatPdfButton.setBackgroundResource(R.drawable.format_button_selected);
            formatPdfButton.setTextColor(ContextCompat.getColor(this, R.color.white));

            formatExcelButton.setBackgroundResource(R.drawable.format_button_unselected);
            formatExcelButton.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorSecondary));
        } else {
            selectedFormat = "Excel";
            formatExcelButton.setBackgroundResource(R.drawable.format_button_selected);
            formatExcelButton.setTextColor(ContextCompat.getColor(this, R.color.white));

            formatPdfButton.setBackgroundResource(R.drawable.format_button_unselected);
            formatPdfButton.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.chk_textColorSecondary));
        }
    }

    private void returnDownloadOptions() {
        String entryType = "All";
        if (entryTypeRadioGroup != null) {
            int id = entryTypeRadioGroup.getCheckedRadioButtonId();
            if (id == R.id.radioCashIn) entryType = "IN";
            else if (id == R.id.radioCashOut) entryType = "OUT";
        }

        String paymentMode = "All";
        if (paymentModeRadioGroup != null) {
            int id = paymentModeRadioGroup.getCheckedRadioButtonId();
            if (id == R.id.radioCashMode) paymentMode = "Cash";
            else if (id == R.id.radioOnlineMode) paymentMode = "Online";
            // Ensure IDs match your XML layout exactly
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra("startDate", startCalendar.getTimeInMillis());
        resultIntent.putExtra("endDate", endCalendar.getTimeInMillis());
        resultIntent.putExtra("entryType", entryType);
        resultIntent.putExtra("paymentMode", paymentMode);
        resultIntent.putExtra("format", selectedFormat);
        resultIntent.putExtra("greyscale", greyscaleCheckbox != null && greyscaleCheckbox.isChecked());

        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    // [FIX] Clean up dialogs to prevent crashes on rotation or finish
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentDatePicker != null && currentDatePicker.isShowing()) {
            currentDatePicker.dismiss();
        }
    }
}