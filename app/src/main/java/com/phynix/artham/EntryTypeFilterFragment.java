package com.phynix.artham;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class EntryTypeFilterFragment extends Fragment {

    private RadioGroup entryTypeGroup;
    private String currentEntryType;

    // Interface to communicate back to the FiltersActivity
    public interface EntryTypeListener {
        void onEntryTypeSelected(String entryType);
    }

    private EntryTypeListener listener;

    // Use a newInstance pattern to pass the current filter selection to the fragment
    public static EntryTypeFilterFragment newInstance(String currentType) {
        EntryTypeFilterFragment fragment = new EntryTypeFilterFragment();
        Bundle args = new Bundle();
        args.putString("currentType", currentType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof EntryTypeListener) {
            listener = (EntryTypeListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement EntryTypeListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentEntryType = getArguments().getString("currentType", "All");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_entry_type_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        entryTypeGroup = view.findViewById(R.id.entry_type_group);

        // Set the initial state of the radio buttons based on the current filter
        if ("IN".equalsIgnoreCase(currentEntryType)) {
            entryTypeGroup.check(R.id.radio_cash_in);
        } else if ("OUT".equalsIgnoreCase(currentEntryType)) {
            entryTypeGroup.check(R.id.radio_cash_out);
        } else {
            entryTypeGroup.check(R.id.radio_all_types);
        }

        entryTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selection;
            if (checkedId == R.id.radio_cash_in) {
                selection = "IN";
            } else if (checkedId == R.id.radio_cash_out) {
                selection = "OUT";
            } else {
                selection = "All";
            }
            // Send the selection back to the activity
            if (listener != null) {
                listener.onEntryTypeSelected(selection);
            }
        });
    }
}