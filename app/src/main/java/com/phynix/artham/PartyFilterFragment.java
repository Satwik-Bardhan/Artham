package com.phynix.artham;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.ErrorHandler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PartyFilterFragment extends Fragment {

    private static final String TAG = "PartyFilterFragment";

    private RecyclerView partiesRecyclerView;
    // private PartySelectionAdapter adapter; // [FIX] You need to create this adapter
    private List<String> allParties = new ArrayList<>();
    private Set<String> selectedParties = new HashSet<>();

    private LinearLayout emptyStateLayout;
    private Button addNewPartyButton;

    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;
    private String currentCashbookId;

    // Interface to communicate back to the FiltersActivity
    public interface PartyFilterListener {
        void onPartiesSelected(Set<String> parties);
    }

    private PartyFilterListener listener;

    public static PartyFilterFragment newInstance(ArrayList<String> currentParties) {
        PartyFilterFragment fragment = new PartyFilterFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("currentParties", currentParties);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PartyFilterListener) {
            listener = (PartyFilterListener) context;
        } else {
            // [FIX] This fragment might not have a listener in your current FiltersActivity.java
            // throw new RuntimeException(context.toString() + " must implement PartyFilterListener");
            Log.w(TAG, context.toString() + " does not implement PartyFilterListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ArrayList<String> currentParties = getArguments().getStringArrayList("currentParties");
            if (currentParties != null) {
                selectedParties = new HashSet<>(currentParties);
            }
        }

        // [FIX] Get Firebase user and cashbook ID
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        if (getActivity() != null) {
            currentCashbookId = getActivity().getIntent().getStringExtra("cashbook_id");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_select_party, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        partiesRecyclerView = view.findViewById(R.id.parties_recycler_view);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        addNewPartyButton = view.findViewById(R.id.add_new_party_button);

        addNewPartyButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Add New Party clicked", Toast.LENGTH_SHORT).show();
            // TODO: Implement logic to add a new party
        });

        setupRecyclerView();
        loadParties();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Send the final selection back to the activity
        if (listener != null) {
            listener.onPartiesSelected(selectedParties);
        }
    }

    private void setupRecyclerView() {
        partiesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // [FIX] You need to create an adapter similar to CategorySelectionAdapter
        // adapter = new PartySelectionAdapter(allParties, selectedParties);
        // partiesRecyclerView.setAdapter(adapter);
        Log.w(TAG, "RecyclerView setup is incomplete. 'PartySelectionAdapter.java' is missing.");
    }

    private void loadParties() {
        if (currentUser == null || currentCashbookId == null) {
            Log.w(TAG, "User or CashbookId is null. Cannot load parties.");
            updateUI(); // Show empty state
            return;
        }

        DatabaseReference transactionsRef = mDatabase.child("users")
                .child(currentUser.getUid())
                .child("cashbooks")
                .child(currentCashbookId)
                .child("transactions");

        transactionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Set<String> partySet = new HashSet<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                    if (transaction != null &&
                            transaction.getPartyName() != null &&
                            !transaction.getPartyName().isEmpty() &&
                            !transaction.getPartyName().equals("Select Party (Customer/Supplier)")) {

                        partySet.add(transaction.getPartyName());
                    }
                }
                allParties.clear();
                allParties.addAll(partySet);
                updateUI();
                // [FIX] If adapter existed, you would update it here:
                // if (adapter != null) {
                //     adapter.notifyDataSetChanged();
                // }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                if (getContext() != null) {
                    ErrorHandler.handleFirebaseError(getContext(), databaseError);
                }
            }
        });
    }

    private void updateUI() {
        if (allParties.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            partiesRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            partiesRecyclerView.setVisibility(View.VISIBLE);
        }
    }
}