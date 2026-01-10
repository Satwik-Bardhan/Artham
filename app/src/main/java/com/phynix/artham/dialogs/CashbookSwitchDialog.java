package com.phynix.artham.dialogs;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.R;
import com.phynix.artham.adapters.CashbookAdapter;
import com.phynix.artham.utils.ErrorHandler;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CashbookSwitchDialog extends DialogFragment {

    private static final String TAG = "CashbookSwitchDialog";

    private RecyclerView cashbookRecyclerView;
    private EditText searchCashbook;
    private LinearLayout emptyStateDialog;
    private ProgressBar loadingStateDialog;
    private Button cancelDialogButton;
    private Button confirmDialogButton; // This ID is NOT in the layout, it will crash.
    private ImageView closeDialog;

    private CashbookAdapter adapter;
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private CashbookModel selectedCashbook;
    private String currentCashbookId;

    private OnCashbookSelectedListener listener;

    public interface OnCashbookSelectedListener {
        void onCashbookSelected(CashbookModel cashbook);
    }

    public void setListener(OnCashbookSelectedListener listener) {
        this.listener = listener;
    }

    public static CashbookSwitchDialog newInstance(String currentCashbookId) {
        CashbookSwitchDialog dialog = new CashbookSwitchDialog();
        Bundle args = new Bundle();
        args.putString("current_cashbook_id", currentCashbookId);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentCashbookId = getArguments().getString("current_cashbook_id");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // [FIX #1] Inflate the layout that actually has the RecyclerView
        View view = inflater.inflate(R.layout.activity_cashbook_switch, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupRecyclerView();
        setupListeners();
        loadCashbooks();
    }

    private void initViews(View view) {
        // [FIX #2] Use the correct ID from the layout
        cashbookRecyclerView = view.findViewById(R.id.cashbookRecyclerView);

        // These IDs are all correct from 'activity_cashbook_switch.xml'
        searchCashbook = view.findViewById(R.id.searchEditText);
        emptyStateDialog = view.findViewById(R.id.emptyStateLayout);
        cancelDialogButton = view.findViewById(R.id.cancelButton);
        closeDialog = view.findViewById(R.id.closeButton);

        // [CRITICAL ERROR] This ID does not exist in 'activity_cashbook_switch.xml'
        // confirmDialogButton = view.findViewById(R.id.confirmDialogButton);
        // I will use 'addNewButton' as the confirm button
        confirmDialogButton = view.findViewById(R.id.addNewButton);
        confirmDialogButton.setText("Select"); // Change text from "Create New" to "Select"

        confirmDialogButton.setEnabled(false);
        confirmDialogButton.setAlpha(0.5f);
        Log.d(TAG, "Views initialized");
    }

    private void setupRecyclerView() {
        if (getContext() == null) return;
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new CashbookAdapter(getContext(), allCashbooks, new CashbookAdapter.OnCashbookClickListener() {
            @Override
            public void onCashbookClick(CashbookModel cashbook) {
                if (cashbook.isCurrent()) {
                    dismiss();
                    return;
                }
                selectedCashbook = cashbook;
                confirmDialogButton.setEnabled(true);
                confirmDialogButton.setAlpha(1.0f);

                for (CashbookModel cb : allCashbooks) {
                    cb.setCurrent(cb.getCashbookId().equals(cashbook.getCashbookId()));
                }
                adapter.updateCashbooks(allCashbooks);
            }

            @Override
            public void onFavoriteClick(CashbookModel cashbook) {
                // Not used in dialog
            }

            @Override
            public void onMenuClick(CashbookModel cashbook, View anchorView) {
                // Not used in dialog
            }
        });

        cashbookRecyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        closeDialog.setOnClickListener(v -> dismiss());
        cancelDialogButton.setOnClickListener(v -> dismiss());

        confirmDialogButton.setOnClickListener(v -> {
            if (selectedCashbook != null && listener != null) {
                listener.onCashbookSelected(selectedCashbook);
                dismiss();
            }
        });

        searchCashbook.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCashbooks(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCashbooks() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showError("Not authenticated");
            return;
        }

        showLoading(true);
        String userId = currentUser.getUid();

        FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("cashbooks")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        allCashbooks.clear();

                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                            if (cashbook != null) {
                                if (cashbook.getCashbookId() == null) {
                                    cashbook.setCashbookId(snapshot.getKey());
                                }
                                cashbook.setCurrent(cashbook.getCashbookId().equals(currentCashbookId));
                                allCashbooks.add(cashbook);
                            }
                        }

                        showLoading(false);
                        if (allCashbooks.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                            adapter.updateCashbooks(allCashbooks);
                        }

                        Log.d(TAG, "Loaded " + allCashbooks.size() + " cashbooks");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showLoading(false);
                        if(getContext() != null) {
                            ErrorHandler.handleFirebaseError(getContext(), error);
                        }
                    }
                });
    }

    private void filterCashbooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateCashbooks(allCashbooks);
            return;
        }

        String lowerQuery = query.toLowerCase().trim();

        List<CashbookModel> filtered = allCashbooks.stream()
                .filter(cashbook -> (cashbook.getName() != null && cashbook.getName().toLowerCase().contains(lowerQuery)) ||
                        (cashbook.getDescription() != null && cashbook.getDescription().toLowerCase().contains(lowerQuery)))
                .collect(Collectors.toList());

        adapter.updateCashbooks(filtered);
    }

    private void showLoading(boolean show) {
        if (loadingStateDialog != null) {
            loadingStateDialog.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (cashbookRecyclerView != null) {
            cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (emptyStateDialog != null) {
            emptyStateDialog.setVisibility(View.GONE); // Always hide empty state when loading
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateDialog != null) {
            emptyStateDialog.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (cashbookRecyclerView != null) {
            cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (loadingStateDialog != null) {
            loadingStateDialog.setVisibility(View.GONE); // Always hide loading when showing empty
        }
    }

    private void showError(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
        } else if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}