package com.phynix.artham.db;

import android.app.Application;
import android.content.Context;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.UUID;

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
    private final Context context;

    public interface DataCallback<T> {
        void onCallback(T data);
    }

    public interface ErrorCallback {
        void onError(String error);
    }

    // --- Local Storage Configuration ---
    public static class LocalDataWrapper {
        public List<CashbookModel> cashbooks = new ArrayList<>();
        public Map<String, List<CategoryModel>> categories = new HashMap<>();
    }

    private static final String LOCAL_FILE_NAME = "local_data.json";
    private final Gson gson = new Gson();
    private final Map<String, List<DataCallback<List<TransactionModel>>>> localCallbacks = new HashMap<>();

    public boolean isLocalMode() {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean("is_local_mode", false);
    }

    public synchronized LocalDataWrapper loadLocalData() {
        try {
            File file = new File(context.getFilesDir(), LOCAL_FILE_NAME);
            if (!file.exists()) {
                return new LocalDataWrapper();
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            Type type = new TypeToken<LocalDataWrapper>(){}.getType();
            LocalDataWrapper wrapper = gson.fromJson(sb.toString(), type);
            return wrapper != null ? wrapper : new LocalDataWrapper();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load local data", e);
            return new LocalDataWrapper();
        }
    }

    public synchronized void saveLocalData(LocalDataWrapper wrapper) {
        try {
            File file = new File(context.getFilesDir(), LOCAL_FILE_NAME);
            String json = gson.toJson(wrapper);
            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(json);
                writer.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save local data", e);
        }
    }

    private synchronized List<TransactionModel> getLocalTransactions(String cashbookId) {
        LocalDataWrapper data = loadLocalData();
        for (CashbookModel cb : data.cashbooks) {
            if (cb.getCashbookId().equals(cashbookId)) {
                List<TransactionModel> txs = cb.getTransactionList();
                txs.sort((t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                return txs;
            }
        }
        return new ArrayList<>();
    }

    private void notifyLocalCallbacks(String cashbookId) {
        List<TransactionModel> txs = getLocalTransactions(cashbookId);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            List<DataCallback<List<TransactionModel>>> callbacks;
            synchronized (localCallbacks) {
                List<DataCallback<List<TransactionModel>>> list = localCallbacks.get(cashbookId);
                callbacks = list != null ? new ArrayList<>(list) : null;
            }
            if (callbacks != null) {
                for (DataCallback<List<TransactionModel>> cb : callbacks) {
                    cb.onCallback(txs);
                }
            }
        });
    }

    private DataRepository(Application application) {
        this.context = application.getApplicationContext();
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
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            List<CategoryModel> defaults = getStandardCategories();
            for (CategoryModel cat : defaults) {
                cat.setId(UUID.randomUUID().toString());
            }
            data.categories.put(cashbookId, defaults);
            saveLocalData(data);
            if (callback != null) callback.onCallback(true);
            return;
        }

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

    public ValueEventListener getCategories(String cashbookId, DataCallback<List<CategoryModel>> callback) {
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            List<CategoryModel> cats = data.categories.get(cashbookId);
            if (cats == null || cats.isEmpty()) {
                List<CategoryModel> defaults = getStandardCategories();
                for (CategoryModel cat : defaults) {
                    cat.setId(UUID.randomUUID().toString());
                }
                data.categories.put(cashbookId, defaults);
                saveLocalData(data);
                callback.onCallback(defaults);
            } else {
                callback.onCallback(cats);
            }
            // Return dummy listener for local mode (consistent with subscribeToTransactions pattern)
            return new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {}
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
        }

        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null || cashbookId == null) {
            if (callback != null) callback.onCallback(new ArrayList<>());
            return null;
        }

        DatabaseReference categoriesRef = userDatabase.child(Constants.NODE_CASHBOOKS).child(cashbookId).child("categories");

        ValueEventListener listener = new ValueEventListener() {
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
                };

        categoriesRef.addValueEventListener(listener);
        return listener;
    }

    public void addCategory(String cashbookId, CategoryModel category, DataCallback<Boolean> callback) {
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            List<CategoryModel> cats = data.categories.get(cashbookId);
            if (cats == null) {
                cats = new ArrayList<>();
            }
            category.setId(UUID.randomUUID().toString());
            cats.add(category);
            data.categories.put(cashbookId, cats);
            saveLocalData(data);
            if (callback != null) callback.onCallback(true);
            return;
        }

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
        if (isLocalMode()) {
            synchronized (localCallbacks) {
                if (!localCallbacks.containsKey(cashbookId)) {
                    localCallbacks.put(cashbookId, new ArrayList<>());
                }
                localCallbacks.get(cashbookId).add(callback);
            }
            callback.onCallback(getLocalTransactions(cashbookId));
            return new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {}
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
        }

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
        if (isLocalMode()) {
            callback.onCallback(getLocalTransactions(cashbookId));
            return;
        }

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
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            for (CashbookModel cb : data.cashbooks) {
                if (cb.getCashbookId().equals(cashbookId)) {
                    String txId = "local_tx_" + UUID.randomUUID().toString();
                    transaction.setTransactionId(txId);
                    cb.getTransactions().put(txId, transaction);
                    cb.setTransactionCount(cb.getTransactions().size());
                    double in = 0, out = 0;
                    for (TransactionModel t : cb.getTransactions().values()) {
                        if ("IN".equalsIgnoreCase(t.getType())) in += t.getAmount();
                        else out += t.getAmount();
                    }
                    cb.setTotalBalance(in - out);
                    break;
                }
            }
            saveLocalData(data);
            notifyLocalCallbacks(cashbookId);
            if (callback != null) callback.onCallback(true);
            return;
        }

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

    /**
     * Offline-aware transaction save.
     * - If online: saves directly to Firebase.
     * - If offline: queues locally for later sync.
     *
     * @param context    Application context for network checks and local storage
     * @param cashbookId Target cashbook ID
     * @param transaction The transaction to save
     * @param callback   Returns true on success (either Firebase or local queue)
     * @param offlineCallback Returns true if the entry was saved offline (for distinct UI feedback)
     */
    public void addTransactionOfflineAware(Context context, String cashbookId, TransactionModel transaction,
                                           DataCallback<Boolean> callback, DataCallback<Boolean> offlineCallback) {
        if (isLocalMode()) {
            addTransaction(cashbookId, transaction, callback);
            return;
        }

        if (cashbookId == null || transaction == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        if (com.phynix.artham.utils.NetworkMonitor.isOnline(context)) {
            // Online — save directly to Firebase
            addTransaction(cashbookId, transaction, success -> {
                if (success) {
                    if (callback != null) callback.onCallback(true);
                } else {
                    // Firebase failed even though online (e.g., auth issue) — queue offline as fallback
                    Log.w(TAG, "Firebase save failed while online. Queueing offline as fallback.");
                    com.phynix.artham.utils.OfflineTransactionManager.queueTransaction(context, cashbookId, transaction);
                    if (callback != null) callback.onCallback(true);
                    if (offlineCallback != null) offlineCallback.onCallback(true);
                }
            });
        } else {
            // Offline — save to local queue
            Log.d(TAG, "Device offline. Saving transaction to local queue.");
            com.phynix.artham.utils.OfflineTransactionManager.queueTransaction(context, cashbookId, transaction);
            if (callback != null) callback.onCallback(true);
            if (offlineCallback != null) offlineCallback.onCallback(true);
        }
    }

    public void updateTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            for (CashbookModel cb : data.cashbooks) {
                if (cb.getCashbookId().equals(cashbookId)) {
                    cb.getTransactions().put(transaction.getTransactionId(), transaction);
                    double in = 0, out = 0;
                    for (TransactionModel t : cb.getTransactions().values()) {
                        if ("IN".equalsIgnoreCase(t.getType())) in += t.getAmount();
                        else out += t.getAmount();
                    }
                    cb.setTotalBalance(in - out);
                    break;
                }
            }
            saveLocalData(data);
            notifyLocalCallbacks(cashbookId);
            if (callback != null) callback.onCallback(true);
            return;
        }

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
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            for (CashbookModel cb : data.cashbooks) {
                if (cb.getCashbookId().equals(cashbookId)) {
                    cb.getTransactions().remove(transactionId);
                    cb.setTransactionCount(cb.getTransactions().size());
                    double in = 0, out = 0;
                    for (TransactionModel t : cb.getTransactions().values()) {
                        if ("IN".equalsIgnoreCase(t.getType())) in += t.getAmount();
                        else out += t.getAmount();
                    }
                    cb.setTotalBalance(in - out);
                    break;
                }
            }
            saveLocalData(data);
            notifyLocalCallbacks(cashbookId);
            if (callback != null) callback.onCallback(true);
            return;
        }

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
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            if (callback != null) {
                callback.onCallback(data.cashbooks != null ? data.cashbooks : new ArrayList<>());
            }
            return;
        }

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
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            String cashbookId = "local_cb_" + UUID.randomUUID().toString();
            CashbookModel newCashbook = new CashbookModel(cashbookId, name.trim());
            newCashbook.setUserId("local_user");
            newCashbook.setCreatedDate(System.currentTimeMillis());
            newCashbook.setLastModified(System.currentTimeMillis());
            newCashbook.setActive(true);
            data.cashbooks.add(newCashbook);
            saveLocalData(data);
            createDefaultCategories(cashbookId, success -> {
                if (callback != null) callback.onCallback(cashbookId);
            });
            return;
        }

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
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            CashbookModel toRemove = null;
            for (CashbookModel cb : data.cashbooks) {
                if (cb.getCashbookId().equals(cashbookId)) {
                    toRemove = cb;
                    break;
                }
            }
            if (toRemove != null) {
                data.cashbooks.remove(toRemove);
                data.categories.remove(cashbookId);
                saveLocalData(data);
                if (callback != null) callback.onCallback(true);
            } else {
                if (errorCallback != null) errorCallback.onError("Cashbook not found");
                if (callback != null) callback.onCallback(false);
            }
            return;
        }

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
        if (isLocalMode()) {
            LocalDataWrapper data = loadLocalData();
            CashbookModel original = null;
            for (CashbookModel cb : data.cashbooks) {
                if (cb.getCashbookId().equals(originalCashbookId)) {
                    original = cb;
                    break;
                }
            }
            if (original != null) {
                String newCashbookId = "local_cb_" + UUID.randomUUID().toString();
                // Copy original attributes
                CashbookModel copy = new CashbookModel(newCashbookId, newName.trim());
                copy.setUserId("local_user");
                copy.setDescription(original.getDescription());
                copy.setCategory(original.getCategory());
                copy.setThemeColor(original.getThemeColor());
                copy.setThemeIcon(original.getThemeIcon());
                copy.setCurrent(false);
                copy.setActive(true);
                copy.setCreatedDate(System.currentTimeMillis());
                copy.setLastModified(System.currentTimeMillis());

                // Copy transactions
                for (Map.Entry<String, TransactionModel> entry : original.getTransactions().entrySet()) {
                    TransactionModel origTx = entry.getValue();
                    TransactionModel txCopy = new TransactionModel();
                    txCopy.setTransactionId("local_tx_" + UUID.randomUUID().toString());
                    txCopy.setAmount(origTx.getAmount());
                    txCopy.setType(origTx.getType());
                    txCopy.setTransactionCategory(origTx.getTransactionCategory());
                    txCopy.setRemark(origTx.getRemark());
                    txCopy.setPaymentMode(origTx.getPaymentMode());
                    txCopy.setTimestamp(origTx.getTimestamp());
                    copy.getTransactions().put(txCopy.getTransactionId(), txCopy);
                }
                copy.setTransactionCount(copy.getTransactions().size());
                copy.setTotalBalance(original.getTotalBalance());

                data.cashbooks.add(copy);

                // Copy categories
                List<CategoryModel> origCats = data.categories.get(originalCashbookId);
                if (origCats != null) {
                    List<CategoryModel> catsCopy = new ArrayList<>();
                    for (CategoryModel origCat : origCats) {
                        CategoryModel catCopy = new CategoryModel(origCat.getName(), origCat.getType(), origCat.getColorHex(), origCat.getIconResId(), origCat.isCustom());
                        catCopy.setId(UUID.randomUUID().toString());
                        catsCopy.add(catCopy);
                    }
                    data.categories.put(newCashbookId, catsCopy);
                }

                saveLocalData(data);

                if (callback != null) callback.onCallback(newCashbookId);
            } else {
                if (errorCallback != null) errorCallback.onError("Original cashbook not found");
                if (callback != null) callback.onCallback(null);
            }
            return;
        }

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

    /**
     * Migrates local guest data to Firebase.
     */
    public void migrateLocalDataToFirebase(DataCallback<Boolean> callback) {
        LocalDataWrapper localData = loadLocalData();
        if (localData == null || localData.cashbooks == null || localData.cashbooks.isEmpty()) {
            if (callback != null) callback.onCallback(true);
            return;
        }

        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        DatabaseReference cashbooksRef = userDatabase.child(Constants.NODE_CASHBOOKS);
        
        final int totalCashbooks = localData.cashbooks.size();
        final java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (CashbookModel localCb : localData.cashbooks) {
            String firebaseCashbookId = cashbooksRef.push().getKey();
            if (firebaseCashbookId == null) {
                if (completedCount.incrementAndGet() == totalCashbooks) {
                    finalizeMigration(callback, false);
                }
                continue;
            }

            String oldLocalId = localCb.getCashbookId();
            
            CashbookModel migratedCb = new CashbookModel(firebaseCashbookId, localCb.getName());
            migratedCb.setUserId(userDatabase.getKey());
            migratedCb.setDescription(localCb.getDescription());
            migratedCb.setCategory(localCb.getCategory());
            migratedCb.setThemeColor(localCb.getThemeColor());
            migratedCb.setThemeIcon(localCb.getThemeIcon());
            migratedCb.setCreatedDate(localCb.getCreatedDate() > 0 ? localCb.getCreatedDate() : System.currentTimeMillis());
            migratedCb.setLastModified(System.currentTimeMillis());
            migratedCb.setActive(localCb.isActive());
            migratedCb.setFavorite(localCb.isFavorite());
            migratedCb.setTotalBalance(localCb.getTotalBalance());
            migratedCb.setTransactionCount(localCb.getTransactionCount());

            Map<String, TransactionModel> transactionsToUpload = new HashMap<>();
            if (localCb.getTransactions() != null) {
                for (Map.Entry<String, TransactionModel> entry : localCb.getTransactions().entrySet()) {
                    TransactionModel localTx = entry.getValue();
                    String firebaseTxId = cashbooksRef.child(firebaseCashbookId).child(Constants.NODE_TRANSACTIONS).push().getKey();
                    if (firebaseTxId != null) {
                        TransactionModel migratedTx = new TransactionModel();
                        migratedTx.setTransactionId(firebaseTxId);
                        migratedTx.setAmount(localTx.getAmount());
                        migratedTx.setType(localTx.getType());
                        migratedTx.setTransactionCategory(localTx.getTransactionCategory());
                        migratedTx.setRemark(localTx.getRemark());
                        migratedTx.setPaymentMode(localTx.getPaymentMode());
                        migratedTx.setTimestamp(localTx.getTimestamp());
                        migratedTx.setPartyName(localTx.getPartyName());
                        migratedTx.setTags(localTx.getTags());
                        migratedTx.setLocation(localTx.getLocation());
                        migratedTx.setAttachmentUri(localTx.getAttachmentUri());
                        migratedTx.setAutoFrequency(localTx.getAutoFrequency());
                        migratedTx.setTaxRate(localTx.getTaxRate());
                        migratedTx.setTaxAmount(localTx.getTaxAmount());
                        migratedTx.setTaxInclusive(localTx.isTaxInclusive());
                        transactionsToUpload.put(firebaseTxId, migratedTx);
                    }
                }
            }
            migratedCb.setTransactions(transactionsToUpload);

            cashbooksRef.child(firebaseCashbookId).setValue(migratedCb)
                    .addOnSuccessListener(aVoid -> {
                        List<CategoryModel> localCats = localData.categories.get(oldLocalId);
                        if (localCats != null && !localCats.isEmpty()) {
                            DatabaseReference categoriesRef = cashbooksRef.child(firebaseCashbookId).child("categories");
                            Map<String, Object> categoryUpdates = new HashMap<>();
                            for (CategoryModel cat : localCats) {
                                String catKey = categoriesRef.push().getKey();
                                if (catKey != null) {
                                    CategoryModel migratedCat = new CategoryModel(cat.getName(), cat.getType(), cat.getColorHex(), cat.getIconResId(), cat.isCustom());
                                    migratedCat.setId(catKey);
                                    categoryUpdates.put(catKey, migratedCat);
                                }
                            }
                            if (!categoryUpdates.isEmpty()) {
                                categoriesRef.updateChildren(categoryUpdates);
                            }
                        }

                        if (completedCount.incrementAndGet() == totalCashbooks) {
                            finalizeMigration(callback, !hasError.get());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to upload cashbook: " + migratedCb.getName(), e);
                        hasError.set(true);
                        if (completedCount.incrementAndGet() == totalCashbooks) {
                            finalizeMigration(callback, false);
                        }
                    });
        }
    }

    private void finalizeMigration(DataCallback<Boolean> callback, boolean success) {
        if (success) {
            saveLocalData(new LocalDataWrapper());
            context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_local_mode", false)
                    .apply();
            Log.d(TAG, "Migration of local guest data completed successfully.");
        }
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onCallback(success));
        }
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