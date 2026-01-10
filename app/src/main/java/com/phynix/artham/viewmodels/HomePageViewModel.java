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
import com.phynix.artham.models.Users; // [FIX] Corrected package
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
    private String currentUserId; // [FIX] Added user ID field

    // LiveData
    private final MutableLiveData<List<TransactionModel>> transactions = new MutableLiveData<>();
    private final MutableLiveData<List<CashbookModel>> cashbooks = new MutableLiveData<>();
    private final MutableLiveData<CashbookModel> activeCashbook = new MutableLiveData<>(); // [FIX] Changed to return full model
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

        Log.d(TAG, "ViewModel initialized.");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            userDatabaseRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUserId);
            loadUserProfile();
            loadCashbooks();
        } else {
            userDatabaseRef = null;
            Log.e(TAG, "ViewModel created, but user is not authenticated!");
            errorMessage.setValue("User not logged in.");
        }
    }

    // ============================================
    // LiveData Getters
    // ============================================

    public LiveData<List<TransactionModel>> getTransactions() {
        return transactions;
    }

    public LiveData<List<CashbookModel>> getCashbooks() {
        return cashbooks;
    }

    public LiveData<CashbookModel> getActiveCashbook() {
        return activeCashbook;
    }

    public LiveData<Users> getUserProfile() {
        return userProfile;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public String getCurrentCashbookId() {
        return currentCashbookId;
    }

    // ============================================
    // User Profile
    // ============================================

    private void loadUserProfile() {
        if (userDatabaseRef == null) return;

        userProfileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Users user = snapshot.getValue(Users.class);
                userProfile.setValue(user);
                Log.d(TAG, "User profile loaded.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User profile loading cancelled", error.toException());
                errorMessage.setValue("Error: " + error.getMessage());
            }
        };
        userDatabaseRef.addValueEventListener(userProfileListener);
    }

    // ============================================
    // Cashbook Management
    // ============================================

    private void loadCashbooks() {
        if (userDatabaseRef == null) return;

        isLoading.setValue(true);

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<CashbookModel> cashbookList = new ArrayList<>();
                for (DataSnapshot cashbookSnapshot : snapshot.getChildren()) {
                    CashbookModel cashbook = cashbookSnapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        cashbook.setCashbookId(cashbookSnapshot.getKey());
                        cashbookList.add(cashbook);
                    }
                }
                cashbooks.setValue(cashbookList);
                Log.d(TAG, "Loaded " + cashbookList.size() + " cashbooks");

                // Get the last active cashbook ID from SharedPreferences
                currentCashbookId = getActiveCashbookIdFromPrefs();

                boolean activeCashbookFound = false;
                if (currentCashbookId != null) {
                    for (CashbookModel book : cashbookList) {
                        if (book.getCashbookId().equals(currentCashbookId)) {
                            activeCashbookFound = true;
                            break;
                        }
                    }
                }

                if (!activeCashbookFound && !cashbookList.isEmpty()) {
                    // Default to the first cashbook if last active one isn't found or isn't set
                    currentCashbookId = cashbookList.get(0).getCashbookId();
                    saveActiveCashbookIdToPrefs(currentCashbookId);
                }

                if (currentCashbookId != null) {
                    switchCashbook(currentCashbookId);
                } else {
                    // This is a new user with no cashbooks
                    isLoading.setValue(false);
                    // HomePage will detect this and show the "Create First Cashbook" dialog
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Cashbooks listener cancelled", error.toException());
                errorMessage.setValue("Error: " + error.getMessage());
                isLoading.setValue(false);
            }
        };
        userDatabaseRef.child("cashbooks").addValueEventListener(cashbooksListener);
    }

    public void switchCashbook(String cashbookId) {
        if (userDatabaseRef == null || cashbookId == null) {
            return;
        }

        isLoading.setValue(true);
        currentCashbookId = cashbookId;
        saveActiveCashbookIdToPrefs(cashbookId);

        // Update the active cashbook LiveData
        if (cashbooks.getValue() != null) {
            for (CashbookModel book : cashbooks.getValue()) {
                if (book.getCashbookId().equals(cashbookId)) {
                    activeCashbook.setValue(book);
                    Log.d(TAG, "Switched to cashbook: " + book.getName());
                    break;
                }
            }
        }

        // Detach the old listener if it exists
        if (previousTransactionsRef != null && transactionsListener != null) {
            previousTransactionsRef.removeEventListener(transactionsListener);
            Log.d(TAG, "Removed previous transactions listener");
        }

        // Attach new listener
        DatabaseReference newTransactionsRef = userDatabaseRef.child("cashbooks").child(cashbookId).child("transactions");
        previousTransactionsRef = newTransactionsRef;

        transactionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TransactionModel> transactionList = new ArrayList<>();
                for (DataSnapshot transactionSnapshot : snapshot.getChildren()) {
                    TransactionModel transaction = transactionSnapshot.getValue(TransactionModel.class);
                    if (transaction != null) {
                        transaction.setTransactionId(transactionSnapshot.getKey());
                        transactionList.add(transaction);
                    }
                }
                // Sort transactions by timestamp (newest first)
                Collections.sort(transactionList, (t1, t2) ->
                        Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                transactions.setValue(transactionList);
                Log.d(TAG, "Loaded " + transactionList.size() + " transactions");
                isLoading.setValue(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Transactions listener cancelled", error.toException());
                errorMessage.setValue("Error: " + error.getMessage());
                isLoading.setValue(false);
            }
        };
        newTransactionsRef.addValueEventListener(transactionsListener);
    }

    // ============================================
    // SharedPreferences for saving active cashbook
    // ============================================

    private void saveActiveCashbookIdToPrefs(String cashbookId) {
        SharedPreferences prefs = getApplication().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUserId, cashbookId).apply();
    }

    private String getActiveCashbookIdFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        return prefs.getString("active_cashbook_id_" + currentUserId, null);
    }

    // ============================================
    // Cleanup
    // ============================================

    @Override
    protected void onCleared() {
        super.onCleared();
        try {
            if (userDatabaseRef != null) {
                if (cashbooksListener != null) {
                    userDatabaseRef.child("cashbooks").removeEventListener(cashbooksListener);
                    Log.d(TAG, "Removed cashbooks listener");
                }
                if (transactionsListener != null && previousTransactionsRef != null) {
                    previousTransactionsRef.removeEventListener(transactionsListener);
                    Log.d(TAG, "Removed transactions listener");
                }
                if (userProfileListener != null) {
                    userDatabaseRef.removeEventListener(userProfileListener);
                    Log.d(TAG, "Removed user profile listener");
                }
            }
            Log.d(TAG, "ViewModel cleaned up successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up ViewModel", e);
        }
    }
}