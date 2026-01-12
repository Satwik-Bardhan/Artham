package com.phynix.artham.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.R;
import com.phynix.artham.adapters.CashbookAdapter;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.utils.ErrorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CashbookSwitchDialog extends DialogFragment {

    private static final String TAG = "CashbookSwitchDialog";

    private RecyclerView cashbookRecyclerView;
    private EditText searchCashbook;

    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;

    private ImageView closeDialog;

    private CashbookAdapter adapter;
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
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
        // Optional: Make dialog full screen or specific style
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Artham_Dialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the existing layout used for the activity
        return inflater.inflate(R.layout.activity_cashbook_switch, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupRecyclerView();
        setupListeners();
        loadCashbooks();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Ensure dialog takes up enough space
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void initViews(View view) {
        cashbookRecyclerView = view.findViewById(R.id.cashbookRecyclerView);
        searchCashbook = view.findViewById(R.id.searchEditText);

        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        loadingLayout = view.findViewById(R.id.loadingLayout);

        closeDialog = view.findViewById(R.id.closeButton);

        // Hide elements not needed for the dialog version (like FAB)
        View fab = view.findViewById(R.id.quickAddFab);
        if (fab != null) fab.setVisibility(View.GONE);
    }

    private void setupRecyclerView() {
        if (getContext() == null) return;

        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // Disable nested scrolling to fix potential scrolling issues in dialogs
        cashbookRecyclerView.setNestedScrollingEnabled(false);

        adapter = new CashbookAdapter(getContext(), allCashbooks, new CashbookAdapter.OnCashbookClickListener() {
            @Override
            public void onCashbookClick(CashbookModel cashbook) {
                // If clicking currently active one, just dismiss
                if (cashbook.getCashbookId().equals(currentCashbookId)) {
                    dismiss();
                    return;
                }

                // Immediately select and notify listener
                if (listener != null) {
                    listener.onCashbookSelected(cashbook);
                }
                dismiss();
            }

            @Override
            public void onFavoriteClick(CashbookModel cashbook) {
                // Feature disabled in dialog selection mode
            }

            @Override
            public void onMenuClick(CashbookModel cashbook, View anchorView) {
                // Feature disabled in dialog selection mode
            }
        });

        cashbookRecyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        if (closeDialog != null) {
            closeDialog.setOnClickListener(v -> dismiss());
        }

        if (searchCashbook != null) {
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
                            try {
                                CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                                if (cashbook != null) {
                                    cashbook.setCashbookId(snapshot.getKey());
                                    // Set 'current' status for visual checkmark in adapter
                                    cashbook.setCurrent(cashbook.getCashbookId().equals(currentCashbookId));
                                    allCashbooks.add(cashbook);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing cashbook", e);
                            }
                        }

                        showLoading(false);
                        if (allCashbooks.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                            adapter.updateCashbooks(allCashbooks);
                        }
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            List<CashbookModel> filtered = allCashbooks.stream()
                    .filter(cashbook -> (cashbook.getName() != null && cashbook.getName().toLowerCase().contains(lowerQuery)) ||
                            (cashbook.getDescription() != null && cashbook.getDescription().toLowerCase().contains(lowerQuery)))
                    .collect(Collectors.toList());
            adapter.updateCashbooks(filtered);
        } else {
            // Fallback for older Android versions
            List<CashbookModel> filtered = new ArrayList<>();
            for (CashbookModel cashbook : allCashbooks) {
                if ((cashbook.getName() != null && cashbook.getName().toLowerCase().contains(lowerQuery)) ||
                        (cashbook.getDescription() != null && cashbook.getDescription().toLowerCase().contains(lowerQuery))) {
                    filtered.add(cashbook);
                }
            }
            adapter.updateCashbooks(filtered);
        }
    }

    private void showLoading(boolean show) {
        if (loadingLayout != null) {
            loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (cashbookRecyclerView != null) {
            cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (cashbookRecyclerView != null) {
            cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.GONE);
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