package com.phynix.artham;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.util.Calendar;
import java.util.TimeZone;

public class DateFilterFragment extends Fragment {

    private RadioGroup dateOptionsGroup;
    private long currentStartDate = 0;
    private long currentEndDate = 0;

    // Interface to communicate back to the FiltersActivity
    public interface DateFilterListener {
        void onDateRangeSelected(long startDate, long endDate);
    }

    private DateFilterListener listener;

    public static DateFilterFragment newInstance(long startDate, long endDate) {
        DateFilterFragment fragment = new DateFilterFragment();
        Bundle args = new Bundle();
        args.putLong("startDate", startDate);
        args.putLong("endDate", endDate);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof DateFilterListener) {
            listener = (DateFilterListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement DateFilterListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentStartDate = getArguments().getLong("startDate", 0);
            currentEndDate = getArguments().getLong("endDate", 0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_date_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dateOptionsGroup = view.findViewById(R.id.date_options_group);
        // [FIX] Logic to set the initial radio button selection based on current dates
        // This is complex, so we'll just default to 'All Time' if no custom range
        if (currentStartDate == 0 && currentEndDate == 0) {
            dateOptionsGroup.check(R.id.radio_all_time);
        } // Add more logic here to check for Today, This Month, etc. if needed

        dateOptionsGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

            // Set start to beginning of the day
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            // Set end to end of the day
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            if (checkedId == R.id.radio_all_time) {
                if (listener != null) listener.onDateRangeSelected(0, 0);
            } else if (checkedId == R.id.radio_today) {
                // startCal and endCal are already set to today
                if (listener != null) listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_yesterday) {
                startCal.add(Calendar.DAY_OF_YEAR, -1);
                endCal.add(Calendar.DAY_OF_YEAR, -1);
                if (listener != null) listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_this_month) {
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
                if (listener != null) listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_last_month) {
                startCal.add(Calendar.MONTH, -1);
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                endCal.add(Calendar.MONTH, -1);
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
                if (listener != null) listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_single_day) {
                showSingleDatePicker();
            } else if (checkedId == R.id.radio_date_range) {
                showDateRangePicker();
            }
        });
    }

    private void showSingleDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startCal.setTimeInMillis(selection);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endCal.setTimeInMillis(selection);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            if (listener != null) listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> dateRangePicker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select dates")
                        .setSelection(
                                new Pair<>(
                                        MaterialDatePicker.todayInUtcMilliseconds(),
                                        MaterialDatePicker.todayInUtcMilliseconds()
                                )
                        )
                        .build();

        dateRangePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startCal.setTimeInMillis(selection.first);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endCal.setTimeInMillis(selection.second);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            if (listener != null) listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
        });

        dateRangePicker.show(getParentFragmentManager(), "DATE_RANGE_PICKER");
    }
}