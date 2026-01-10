package com.phynix.artham;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PaymentModeFilterFragment extends Fragment {

    private CheckBox checkboxNoMode, checkboxCash, checkboxOnline;
    private Set<String> selectedModes = new HashSet<>();

    // Interface to communicate back to the FiltersActivity
    public interface PaymentModeListener {
        void onPaymentModesSelected(Set<String> modes);
    }

    private PaymentModeListener listener;

    public static PaymentModeFilterFragment newInstance(ArrayList<String> currentModes) {
        PaymentModeFilterFragment fragment = new PaymentModeFilterFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("currentModes", currentModes);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PaymentModeListener) {
            listener = (PaymentModeListener) context;
        } else {
            // [FIX] Make this optional, as FiltersActivity might not implement all listeners
            // throw new RuntimeException(context.toString() + " must implement PaymentModeListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ArrayList<String> currentModes = getArguments().getStringArrayList("currentModes");
            if (currentModes != null) {
                selectedModes = new HashSet<>(currentModes);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_filter_payment_mode, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        checkboxNoMode = view.findViewById(R.id.checkbox_no_mode);
        checkboxCash = view.findViewById(R.id.checkbox_cash);
        checkboxOnline = view.findViewById(R.id.checkbox_online);

        // Set initial state of checkboxes
        checkboxNoMode.setChecked(selectedModes.contains("No Payment Mode"));
        checkboxCash.setChecked(selectedModes.contains("Cash"));
        checkboxOnline.setChecked(selectedModes.contains("Online"));

        // Set listeners
        setupCheckboxListener(checkboxNoMode, "No Payment Mode");
        setupCheckboxListener(checkboxCash, "Cash");
        setupCheckboxListener(checkboxOnline, "Online");
    }

    @Override
    public void onPause() {
        super.onPause();
        // Send the final selection back to the activity
        if (listener != null) {
            listener.onPaymentModesSelected(selectedModes);
        }
    }

    private void setupCheckboxListener(CheckBox checkBox, final String mode) {
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedModes.add(mode);
            } else {
                selectedModes.remove(mode);
            }
        });
    }
}