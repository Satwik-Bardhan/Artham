package com.phynix.artham;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout; // [FIX] Added
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.adapters.TransactionAdapter;
import com.phynix.artham.models.TransactionModel;

import java.util.ArrayList;
import java.util.List;

public class TransactionItemFragment extends Fragment {

    private static final String TAG = "TransactionItemFragment";
    private RecyclerView transactionRecyclerView;
    private TransactionAdapter transactionAdapter;
    private List<TransactionModel> transactionList;
    private TransactionAdapter.OnItemClickListener clickListener;

    // [FIX] Added views from the layout
    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;
    private Button addTransactionButton;
    private TextView emptyStateText;


    public static TransactionItemFragment newInstance(ArrayList<TransactionModel> transactions) {
        TransactionItemFragment fragment = new TransactionItemFragment();
        Bundle args = new Bundle();
        args.putSerializable("transactions", transactions);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transaction_list, container, false);

        transactionRecyclerView = view.findViewById(R.id.transactionRecyclerView);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        loadingLayout = view.findViewById(R.id.loadingLayout);
        addTransactionButton = view.findViewById(R.id.addTransactionButton);
        emptyStateText = view.findViewById(R.id.emptyStateText); // This is inside emptyStateLayout

        if (getArguments() != null) {
            try {
                transactionList = (List<TransactionModel>) getArguments().getSerializable("transactions");
            } catch (Exception e) {
                Log.e(TAG, "Failed to deserialize transactions", e);
                transactionList = new ArrayList<>();
            }
        } else {
            transactionList = new ArrayList<>();
        }

        setupRecyclerView();

        // [FIX] Set up button listener for empty state
        addTransactionButton.setOnClickListener(v -> {
            // Start the CashInOutActivity
            if (getActivity() != null) {
                Intent intent = new Intent(getActivity(), CashInOutActivity.class);
                intent.putExtra("cashbook_id", getActivity().getIntent().getStringExtra("cashbook_id"));
                intent.putExtra("transaction_type", "OUT"); // Default to expense
                startActivity(intent);
            }
        });

        Log.d(TAG, "Fragment created with " + (transactionList != null ? transactionList.size() : 0) + " transactions");

        return view;
    }

    private void setupRecyclerView() {
        if (transactionList == null) {
            transactionList = new ArrayList<>();
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        transactionRecyclerView.setLayoutManager(layoutManager);

        // [FIX] Use the existing clickListener if it was set before adapter was created
        transactionAdapter = new TransactionAdapter(transactionList, clickListener);
        transactionRecyclerView.setAdapter(transactionAdapter);

        // Update empty state
        updateEmptyState();

        Log.d(TAG, "RecyclerView setup with " + transactionList.size() + " transactions");
    }

    public void updateTransactions(List<TransactionModel> newTransactions) {
        if (newTransactions != null) {
            this.transactionList = newTransactions;

            if (transactionAdapter != null) {
                transactionAdapter.updateTransactions(newTransactions);
                updateEmptyState();
                Log.d(TAG, "Transactions updated: " + newTransactions.size() + " items");
            }
        }
    }

    private void updateEmptyState() {
        if (transactionList == null || transactionList.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            transactionRecyclerView.setVisibility(View.GONE);
            // [FIX] Update text based on why it's empty
            if (emptyStateText != null) {
                // We assume if list is empty, it's due to filters.
                // The TransactionActivity can update this text if needed.
                emptyStateText.setText("No transactions match your current filters");
            }
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            transactionRecyclerView.setVisibility(View.VISIBLE);
        }
        // Hide loading layout
        loadingLayout.setVisibility(View.GONE);
    }

    // [FIX] Show a loading spinner
    public void showLoading(boolean isLoading) {
        if (loadingLayout != null) {
            loadingLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            // Hide other views when loading
            if(isLoading) {
                emptyStateLayout.setVisibility(View.GONE);
                transactionRecyclerView.setVisibility(View.GONE);
            }
        }
    }

    public void setOnItemClickListener(TransactionAdapter.OnItemClickListener listener) {
        this.clickListener = listener;
        // [FIX] If adapter already exists, just update its listener
        if (transactionAdapter != null) {
            // This is not ideal, but TransactionAdapter doesn't have a setter, so we re-create
            transactionAdapter = new TransactionAdapter(transactionList, listener);
            transactionRecyclerView.setAdapter(transactionAdapter);
        }
    }
}