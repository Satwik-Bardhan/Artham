package com.phynix.artham.db;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
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

/**
 * DataRepository - Centralized data access layer for CashFlow app
 * Handles Firebase (authenticated users) operations ONLY.
 */
public class DataRepository {

    private static final String TAG = "DataRepository";
    private static volatile DataRepository INSTANCE;

    private final DatabaseReference rootRef;
    private final FirebaseAuth mAuth;

    public interface DataCallback<T> {
        void onCallback(T data);
    }

    public interface ErrorCallback {
        void onError(String error);
    }

    private DataRepository(Application application) {
        mAuth = FirebaseAuth.getInstance();
        rootRef = FirebaseDatabase.getInstance().getReference();
    }

    public static DataRepository getInstance(Application application) {
        if (INSTANCE == null) {
            synchronized (DataRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DataRepository(application);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Helper to get the current user's DB reference.
     * Returns null if not authenticated.
     */
    private DatabaseReference getUserDatabaseRef() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !currentUser.isAnonymous()) {
            DatabaseReference userRef = rootRef.child("users").child(currentUser.getUid());

            // [UPDATED] Keep this data synced for offline usage and automatic backup
            userRef.keepSynced(true);

            return userRef;
        }
        return null;
    }

    // --- TRANSACTION METHODS ---

    public void getAllTransactions(String cashbookId, DataCallback<List<TransactionModel>> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (errorCallback != null) errorCallback.onError("User not authenticated or cashbook missing.");
            callback.onCallback(new ArrayList<>());
            return;
        }

        userDatabase.child("cashbooks").child(cashbookId).child("transactions")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        try {
                            List<TransactionModel> transactions = new ArrayList<>();
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                                if (transaction != null) {
                                    transaction.setTransactionId(snapshot.getKey());
                                    transactions.add(transaction);
                                }
                            }
                            // Sort by timestamp, newest first
                            Collections.sort(transactions, (t1, t2) ->
                                    Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                            callback.onCallback(transactions);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing Firebase transactions", e);
                            if (errorCallback != null) errorCallback.onError("Failed to process transaction data");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Firebase transaction query cancelled", databaseError.toException());
                        callback.onCallback(new ArrayList<>());
                        if (errorCallback != null) errorCallback.onError("Database connection failed");
                    }
                });
    }

    public void addTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        String transactionId = userDatabase.child("cashbooks").child(cashbookId).child("transactions").push().getKey();
        if (transactionId != null) {
            transaction.setTransactionId(transactionId);
            userDatabase.child("cashbooks").child(cashbookId).child("transactions").child(transactionId)
                    .setValue(transaction)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Transaction added successfully to Firebase");
                        if (callback != null) callback.onCallback(true);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error adding transaction to Firebase", e);
                        if (callback != null) callback.onCallback(false);
                    });
        } else {
            if (callback != null) callback.onCallback(false);
        }
    }

    public void updateTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null || transaction.getTransactionId() == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        userDatabase.child("cashbooks").child(cashbookId).child("transactions")
                .child(transaction.getTransactionId())
                .setValue(transaction)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Transaction updated successfully in Firebase");
                    if (callback != null) callback.onCallback(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating transaction in Firebase", e);
                    if (callback != null) callback.onCallback(false);
                });
    }

    public void deleteTransaction(String cashbookId, String transactionId, DataCallback<Boolean> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null || transactionId == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        userDatabase.child("cashbooks").child(cashbookId).child("transactions").child(transactionId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Transaction deleted successfully from Firebase");
                    if (callback != null) callback.onCallback(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting transaction from Firebase", e);
                    if (callback != null) callback.onCallback(false);
                });
    }

    // --- CASHBOOK METHODS ---

    public void getCashbooks(DataCallback<List<CashbookModel>> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null) {
            callback.onCallback(new ArrayList<>());
            return;
        }

        userDatabase.child("cashbooks").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    List<CashbookModel> cashbooks = new ArrayList<>();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                        if (cashbook != null) {
                            cashbook.setCashbookId(snapshot.getKey());
                            cashbooks.add(cashbook);
                        }
                    }
                    callback.onCallback(cashbooks);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing cashbooks", e);
                    if (errorCallback != null) errorCallback.onError("Failed to process cashbook data");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Cashbooks query cancelled", error.toException());
                callback.onCallback(new ArrayList<>());
                if (errorCallback != null) errorCallback.onError("Database connection failed");
            }
        });
    }

    public void createNewCashbook(String name, DataCallback<String> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null) {
            if (errorCallback != null) errorCallback.onError("User not authenticated");
            callback.onCallback(null);
            return;
        }

        if (name == null || name.trim().isEmpty()) {
            if (errorCallback != null) errorCallback.onError("Cashbook name cannot be empty");
            callback.onCallback(null);
            return;
        }

        String cashbookId = userDatabase.child("cashbooks").push().getKey();
        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name.trim());
            newCashbook.setUserId(userDatabase.getKey());

            userDatabase.child("cashbooks").child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Cashbook created successfully: " + name);
                        callback.onCallback(cashbookId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating cashbook: " + name, e);
                        if (errorCallback != null) errorCallback.onError("Failed to create cashbook");
                        callback.onCallback(null);
                    });
        } else {
            if (errorCallback != null) errorCallback.onError("Failed to generate cashbook ID");
            callback.onCallback(null);
        }
    }

    public void deleteCashbook(String cashbookId, DataCallback<Boolean> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (errorCallback != null) errorCallback.onError("Invalid request");
            if (callback != null) callback.onCallback(false);
            return;
        }

        userDatabase.child("cashbooks").child(cashbookId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Cashbook deleted successfully: " + cashbookId);
                    if (callback != null) callback.onCallback(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting cashbook: " + cashbookId, e);
                    if (errorCallback != null) errorCallback.onError("Failed to delete cashbook");
                    if (callback != null) callback.onCallback(false);
                });
    }

    public void duplicateCashbook(String originalCashbookId, String newName, DataCallback<String> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || originalCashbookId == null || newName == null) {
            if (errorCallback != null) errorCallback.onError("Invalid request");
            callback.onCallback(null);
            return;
        }

        userDatabase.child("cashbooks").child(originalCashbookId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        CashbookModel originalCashbook = dataSnapshot.getValue(CashbookModel.class);
                        if (originalCashbook != null) {
                            String newCashbookId = userDatabase.child("cashbooks").push().getKey();
                            if (newCashbookId != null) {
                                // Create new book based on old one
                                originalCashbook.setCashbookId(newCashbookId);
                                originalCashbook.setName(newName.trim());
                                originalCashbook.setCurrent(false);
                                originalCashbook.setLastModified(System.currentTimeMillis());
                                originalCashbook.setCreatedDate(System.currentTimeMillis());

                                userDatabase.child("cashbooks").child(newCashbookId).setValue(originalCashbook)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Cashbook duplicated successfully: " + newName);
                                            callback.onCallback(newCashbookId);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error duplicating cashbook", e);
                                            if (errorCallback != null) errorCallback.onError("Failed to duplicate cashbook");
                                            callback.onCallback(null);
                                        });
                            } else {
                                if (errorCallback != null) errorCallback.onError("Failed to generate new cashbook ID");
                                callback.onCallback(null);
                            }
                        } else {
                            if (errorCallback != null) errorCallback.onError("Original cashbook not found");
                            callback.onCallback(null);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error reading original cashbook", databaseError.toException());
                        if (errorCallback != null) errorCallback.onError("Failed to read original cashbook");
                        callback.onCallback(null);
                    }
                });
    }

    // --- UTILITY METHODS ---

    public boolean isUserAuthenticated() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null && !currentUser.isAnonymous();
    }

    public String getCurrentUserId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null ? currentUser.getUid() : null;
    }
}