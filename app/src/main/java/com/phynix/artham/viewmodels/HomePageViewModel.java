package com.phynix.artham.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.models.Users;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomePageViewModel extends AndroidViewModel {

    private static final String TAG = "HomePageViewModel";

    // Firebase
    private final DatabaseReference userDatabaseRef;
    private String currentCashbookId;
    private String currentUserId;

    // LiveData
    private final MutableLiveData<List<TransactionModel>> transactions = new MutableLiveData<>();
    private final MutableLiveData<List<CashbookModel>> cashbooks = new MutableLiveData<>();
    private final MutableLiveData<CashbookModel> activeCashbook = new MutableLiveData<>();
    private final MutableLiveData<Users> userProfile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Listeners
    private ValueEventListener transactionsListener;
    private ValueEventListener cashbooksListener;
    private ValueEventListener userProfileListener;
    private DatabaseReference previousTransactionsRef;

    public HomePageViewModel(@NonNull Application application) {
        super(application);
        this.transactions.setValue(new ArrayList<>());
        this.cashbooks.setValue(new ArrayList<>());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            userDatabaseRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId);
            loadUserProfile();
            loadCashbooks();
        } else {
            userDatabaseRef = null;
            errorMessage.setValue("User not logged in.");
        }
    }

    // --- Getters ---
    public LiveData<List<TransactionModel>> getTransactions() { return transactions; }
    public LiveData<List<CashbookModel>> getCashbooks() { return cashbooks; }
    public LiveData<CashbookModel> getActiveCashbook() { return activeCashbook; }
    public LiveData<Users> getUserProfile() { return userProfile; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public String getCurrentCashbookId() { return currentCashbookId; }

    // --- Logic ---

    private void loadUserProfile() {
        if (userDatabaseRef == null) return;
        userProfileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Users user = snapshot.getValue(Users.class);
                userProfile.setValue(user);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User profile error", error.toException());
            }
        };
        userDatabaseRef.addValueEventListener(userProfileListener);
    }

    private void loadCashbooks() {
        if (userDatabaseRef == null) return;
        isLoading.setValue(true);

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<CashbookModel> cashbookList = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    try {
                        CashbookModel cashbook = s.getValue(CashbookModel.class);
                        if (cashbook != null) {
                            cashbook.setCashbookId(s.getKey());
                            cashbookList.add(cashbook);
                        }
                    } catch (Exception e) { Log.e(TAG, "Error parsing cashbook", e); }
                }
                cashbooks.setValue(cashbookList);

                // Determine active cashbook
                currentCashbookId = getActiveCashbookIdFromPrefs();
                boolean activeFound = false;
                if (currentCashbookId != null) {
                    for (CashbookModel book : cashbookList) {
                        if (book.getCashbookId().equals(currentCashbookId)) {
                            activeFound = true;
                            break;
                        }
                    }
                }

                if (!activeFound && !cashbookList.isEmpty()) {
                    currentCashbookId = cashbookList.get(0).getCashbookId();
                    saveActiveCashbookIdToPrefs(currentCashbookId);
                }

                if (currentCashbookId != null) {
                    switchCashbook(currentCashbookId);
                } else {
                    isLoading.setValue(false); // No cashbooks, stop loading so UI shows create dialog
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLoading.setValue(false);
                errorMessage.setValue("Failed to load cashbooks: " + error.getMessage());
            }
        };
        userDatabaseRef.child("cashbooks").addValueEventListener(cashbooksListener);
    }

    public void switchCashbook(String cashbookId) {
        if (userDatabaseRef == null || cashbookId == null) return;

        isLoading.setValue(true);
        currentCashbookId = cashbookId;
        saveActiveCashbookIdToPrefs(cashbookId);

        // Update active model
        if (cashbooks.getValue() != null) {
            for (CashbookModel book : cashbooks.getValue()) {
                if (book.getCashbookId().equals(cashbookId)) {
                    activeCashbook.setValue(book);
                    break;
                }
            }
        }

        // Transactions Listener Switch
        if (previousTransactionsRef != null && transactionsListener != null) {
            previousTransactionsRef.removeEventListener(transactionsListener);
        }

        DatabaseReference newTransactionsRef = userDatabaseRef.child("cashbooks").child(cashbookId).child("transactions");
        previousTransactionsRef = newTransactionsRef;

        transactionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TransactionModel> transactionList = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    try {
                        TransactionModel txn = s.getValue(TransactionModel.class);
                        if (txn != null) {
                            txn.setTransactionId(s.getKey());
                            transactionList.add(txn);
                        }
                    } catch (Exception e) { Log.e(TAG, "Error parsing txn", e); }
                }
                Collections.sort(transactionList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                transactions.setValue(transactionList);
                isLoading.setValue(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLoading.setValue(false);
                errorMessage.setValue("Error loading transactions");
            }
        };
        newTransactionsRef.addValueEventListener(transactionsListener);
    }

    public void createNewCashbook(String name) {
        if (userDatabaseRef == null) return;
        DatabaseReference cashbooksRef = userDatabaseRef.child("cashbooks");
        String cashbookId = cashbooksRef.push().getKey();
        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            newCashbook.setUserId(currentUserId);
            cashbooksRef.child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> switchCashbook(cashbookId))
                    .addOnFailureListener(e -> errorMessage.setValue("Failed to create cashbook"));
        }
    }

    private void saveActiveCashbookIdToPrefs(String cashbookId) {
        SharedPreferences prefs = getApplication().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUserId, cashbookId).apply();
    }

    private String getActiveCashbookIdFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        return prefs.getString("active_cashbook_id_" + currentUserId, null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (userDatabaseRef != null) {
            if (cashbooksListener != null) userDatabaseRef.child("cashbooks").removeEventListener(cashbooksListener);
            if (userProfileListener != null) userDatabaseRef.removeEventListener(userProfileListener);
            if (transactionsListener != null && previousTransactionsRef != null) previousTransactionsRef.removeEventListener(transactionsListener);
        }
    }
}