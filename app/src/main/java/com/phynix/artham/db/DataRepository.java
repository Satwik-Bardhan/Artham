package com.phynix.artham.db;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.phynix.artham.R;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (currentUser != null) {
            DatabaseReference userRef = rootRef.child(Constants.NODE_USERS).child(currentUser.getUid());
            // Keep this data synced for offline usage
            userRef.keepSynced(true);
            return userRef;
        }
        return null;
    }

    // --- CATEGORY MANAGEMENT ---

    /**
     * Creates default categories using the centralized list.
     */
    public void createDefaultCategories(String cashbookId, DataCallback<Boolean> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        DatabaseReference categoriesRef = userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child("categories");

        // Retrieve the centralized list of default categories
        List<CategoryModel> defaults = getStandardCategories();

        Map<String, Object> updates = new HashMap<>();
        for (CategoryModel cat : defaults) {
            String key = categoriesRef.push().getKey();
            cat.setId(key);
            if (key != null) {
                updates.put(key, cat);
            }
        }

        categoriesRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onCallback(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create default categories", e);
                    if (callback != null) callback.onCallback(false);
                });
    }

    /**
     * CENTRALIZED CONFIGURATION: Define your default categories here.
     * Stacked in one place for easy modification of Names, Types, Colors, and Icons.
     */
    private List<CategoryModel> getStandardCategories() {
        List<CategoryModel> categories = new ArrayList<>();

        // --- EXPENSES ---
        categories.add(new CategoryModel("Food & Dining", "Expense", "#FF5722", R.drawable.ic_food_dining, false));
        categories.add(new CategoryModel("Bills & Utility", "Expense", "#FFC107", R.drawable.ic_utilities, false));
        categories.add(new CategoryModel("Transportation", "Expense", "#3F51B5", R.drawable.ic_transportation, false));
        categories.add(new CategoryModel("Rent", "Expense", "#009688", R.drawable.ic_home, false));
        categories.add(new CategoryModel("Entertainment", "Expense", "#9C27B0", R.drawable.ic_entertainment, false));
        categories.add(new CategoryModel("Shopping", "Expense", "#E91E63", R.drawable.ic_receipt, false));
        categories.add(new CategoryModel("Medical", "Expense", "#F44336", R.drawable.ic_shield_check, false));
        categories.add(new CategoryModel("Education", "Expense", "#795548", R.drawable.ic_book, false));
        categories.add(new CategoryModel("Personal", "Expense", "#607D8B", R.drawable.ic_person, false));
        categories.add(new CategoryModel("Other", "Expense", "#9E9E9E", R.drawable.ic_all_inclusive, false));

        // --- INCOME ---
        categories.add(new CategoryModel("Salary", "Income", "#4CAF50", R.drawable.ic_money, false));
        categories.add(new CategoryModel("Business", "Income", "#2196F3", R.drawable.ic_bar_graph, false));
        categories.add(new CategoryModel("Investment", "Income", "#00BCD4", R.drawable.ic_trending_up, false));
        categories.add(new CategoryModel("Gifts", "Income", "#FFEB3B", R.drawable.ic_star_filled, false));

        return categories;
    }

    public void getCategories(String cashbookId, DataCallback<List<CategoryModel>> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (callback != null) callback.onCallback(new ArrayList<>());
            return;
        }

        userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child("categories")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<CategoryModel> categories = new ArrayList<>();
                        for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                            CategoryModel category = postSnapshot.getValue(CategoryModel.class);
                            if (category != null) {
                                category.setId(postSnapshot.getKey());
                                categories.add(category);
                            }
                        }
                        if (categories.isEmpty()) {
                            // If no categories exist, initialize defaults
                            createDefaultCategories(cashbookId, null);
                        } else {
                            callback.onCallback(categories);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "getCategories cancelled", error.toException());
                    }
                });
    }

    public void addCategory(String cashbookId, CategoryModel category, DataCallback<Boolean> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        DatabaseReference ref = userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child("categories").push();
        category.setId(ref.getKey());
        ref.setValue(category)
                .addOnSuccessListener(aVoid -> callback.onCallback(true))
                .addOnFailureListener(e -> callback.onCallback(false));
    }

    // --- TRANSACTION METHODS ---

    /**
     * Subscribes to real-time transaction updates for a specific cashbook.
     * Returns the ValueEventListener so it can be removed by the ViewModel when switching cashbooks.
     */
    public ValueEventListener subscribeToTransactions(String cashbookId, DataCallback<List<TransactionModel>> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (errorCallback != null) errorCallback.onError("User not authenticated or cashbook missing.");
            return null;
        }

        ValueEventListener listener = new ValueEventListener() {
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
                if (errorCallback != null) errorCallback.onError(databaseError.getMessage());
            }
        };

        userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child(Constants.NODE_TRANSACTIONS)
                .addValueEventListener(listener);

        return listener;
    }

    public void getAllTransactions(String cashbookId, DataCallback<List<TransactionModel>> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (errorCallback != null) errorCallback.onError("User not authenticated or cashbook missing.");
            callback.onCallback(new ArrayList<>());
            return;
        }

        userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child(Constants.NODE_TRANSACTIONS)
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
                        callback.onCallback(new ArrayList<>());
                        if (errorCallback != null) errorCallback.onError(databaseError.getMessage());
                    }
                });
    }

    public void addTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        DatabaseReference transactionsRef = userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child(Constants.NODE_TRANSACTIONS);
        String transactionId = transactionsRef.push().getKey();

        if (transactionId != null) {
            transaction.setTransactionId(transactionId);
            transactionsRef.child(transactionId)
                    .setValue(transaction)
                    .addOnSuccessListener(aVoid -> {
                        if (callback != null) callback.onCallback(true);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error adding transaction", e);
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

        userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child(Constants.NODE_TRANSACTIONS)
                .child(transaction.getTransactionId())
                .setValue(transaction)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onCallback(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating transaction", e);
                    if (callback != null) callback.onCallback(false);
                });
    }

    public void deleteTransaction(String cashbookId, String transactionId, DataCallback<Boolean> callback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null || transactionId == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child(Constants.NODE_TRANSACTIONS).child(transactionId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onCallback(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting transaction", e);
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

        userDatabase.child(Constants.NODE_CASHBOOKS).addListenerForSingleValueEvent(new ValueEventListener() {
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
                callback.onCallback(new ArrayList<>());
                if (errorCallback != null) errorCallback.onError(error.getMessage());
            }
        });
    }

    public void createNewCashbook(String name, DataCallback<String> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null) {
            if (errorCallback != null) errorCallback.onError("User not authenticated");
            if (callback != null) callback.onCallback(null);
            return;
        }

        if (name == null || name.trim().isEmpty()) {
            if (errorCallback != null) errorCallback.onError("Cashbook name cannot be empty");
            if (callback != null) callback.onCallback(null);
            return;
        }

        String cashbookId = userDatabase.child(Constants.NODE_CASHBOOKS).push().getKey();
        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name.trim());
            newCashbook.setUserId(userDatabase.getKey());
            newCashbook.setCreatedDate(System.currentTimeMillis());
            newCashbook.setLastModified(System.currentTimeMillis());

            userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        // Initialize Categories for the new cashbook
                        createDefaultCategories(cashbookId, success -> {
                            if (callback != null) callback.onCallback(cashbookId);
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating cashbook", e);
                        if (errorCallback != null) errorCallback.onError("Failed to create cashbook");
                        if (callback != null) callback.onCallback(null);
                    });
        } else {
            if (errorCallback != null) errorCallback.onError("Failed to generate cashbook ID");
            if (callback != null) callback.onCallback(null);
        }
    }

    public void deleteCashbook(String cashbookId, DataCallback<Boolean> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (errorCallback != null) errorCallback.onError("Invalid request");
            if (callback != null) callback.onCallback(false);
            return;
        }

        userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onCallback(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting cashbook", e);
                    if (errorCallback != null) errorCallback.onError("Failed to delete cashbook");
                    if (callback != null) callback.onCallback(false);
                });
    }

    public void duplicateCashbook(String originalCashbookId, String newName, DataCallback<String> callback, ErrorCallback errorCallback) {
        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || originalCashbookId == null || newName == null) {
            if (errorCallback != null) errorCallback.onError("Invalid request");
            if (callback != null) callback.onCallback(null);
            return;
        }

        userDatabase.child(Constants.NODE_CASHBOOKS).child(originalCashbookId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        CashbookModel originalCashbook = dataSnapshot.getValue(CashbookModel.class);
                        if (originalCashbook != null) {
                            String newCashbookId = userDatabase.child(Constants.NODE_CASHBOOKS).push().getKey();
                            if (newCashbookId != null) {
                                originalCashbook.setCashbookId(newCashbookId);
                                originalCashbook.setName(newName.trim());
                                originalCashbook.setCurrent(false);
                                originalCashbook.setLastModified(System.currentTimeMillis());
                                originalCashbook.setCreatedDate(System.currentTimeMillis());

                                userDatabase.child(Constants.NODE_CASHBOOKS).child(newCashbookId).setValue(originalCashbook)
                                        .addOnSuccessListener(aVoid -> {
                                            // Initialize categories for duplicated cashbook
                                            createDefaultCategories(newCashbookId, success -> {
                                                if (callback != null) callback.onCallback(newCashbookId);
                                            });
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error duplicating cashbook", e);
                                            if (errorCallback != null) errorCallback.onError("Failed to duplicate cashbook");
                                            if (callback != null) callback.onCallback(null);
                                        });
                            } else {
                                if (errorCallback != null) errorCallback.onError("Failed to generate new cashbook ID");
                                if (callback != null) callback.onCallback(null);
                            }
                        } else {
                            if (errorCallback != null) errorCallback.onError("Original cashbook not found");
                            if (callback != null) callback.onCallback(null);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error reading original cashbook", databaseError.toException());
                        if (errorCallback != null) errorCallback.onError(databaseError.getMessage());
                        if (callback != null) callback.onCallback(null);
                    }
                });
    }

    // --- UTILITY METHODS ---

    public boolean isUserAuthenticated() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null;
    }

    public String getCurrentUserId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null ? currentUser.getUid() : null;
    }
}