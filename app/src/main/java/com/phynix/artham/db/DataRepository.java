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

import com.phynix.artham.db.room.ArthamDatabase;
import com.phynix.artham.db.room.EntityMapper;
import com.phynix.artham.db.room.JsonToRoomMigrator;
import com.phynix.artham.db.room.dao.CashbookDao;
import com.phynix.artham.db.room.dao.CategoryDao;
import com.phynix.artham.db.room.dao.TransactionDao;
import com.phynix.artham.db.room.entity.CashbookEntity;
import com.phynix.artham.db.room.entity.CategoryEntity;
import com.phynix.artham.db.room.entity.TransactionEntity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // --- Room Database ---
    private final ArthamDatabase roomDb;
    private final TransactionDao transactionDao;
    private final CashbookDao cashbookDao;
    private final CategoryDao categoryDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

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

        // Initialize Room Database
        roomDb = ArthamDatabase.getInstance(context);
        transactionDao = roomDb.transactionDao();
        cashbookDao = roomDb.cashbookDao();
        categoryDao = roomDb.categoryDao();

        // Run one-time JSON → Room migration on background thread
        executorService.execute(() -> JsonToRoomMigrator.migrateIfNeeded(context, roomDb));
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
        executorService.execute(() -> {
            List<CategoryModel> defaults = getStandardCategories();
            List<CategoryEntity> entities = new ArrayList<>();
            for (CategoryModel cat : defaults) {
                cat.setId(UUID.randomUUID().toString());
                entities.add(EntityMapper.toEntity(cat, cashbookId));
            }
            categoryDao.insertAll(entities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(true);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
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
        executorService.execute(() -> {
            List<CategoryEntity> entities = categoryDao.getByCashbook(cashbookId);
            if (entities == null || entities.isEmpty()) {
                // Create defaults first, then return them
                List<CategoryModel> defaults = getStandardCategories();
                List<CategoryEntity> defaultEntities = new ArrayList<>();
                for (CategoryModel cat : defaults) {
                    cat.setId(UUID.randomUUID().toString());
                    defaultEntities.add(EntityMapper.toEntity(cat, cashbookId));
                }
                categoryDao.insertAll(defaultEntities);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onCallback(defaults);
                });
            } else {
                List<CategoryModel> models = EntityMapper.toCategoryModelList(entities);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onCallback(models);
                });
            }
        });
        // Return a no-op listener for compatibility
        return new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
    }

    public void addCategory(String cashbookId, CategoryModel category, DataCallback<Boolean> callback) {
        executorService.execute(() -> {
            category.setId(UUID.randomUUID().toString());
            categoryDao.insert(EntityMapper.toEntity(category, cashbookId));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(true);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    // --- TRANSACTION METHODS ---

    /**
     * Subscribes to real-time transaction updates for a specific cashbook.
     * Returns the ValueEventListener so it can be removed by the ViewModel when switching cashbooks.
     */
    public ValueEventListener subscribeToTransactions(String cashbookId, DataCallback<List<TransactionModel>> callback, ErrorCallback errorCallback) {
        executorService.execute(() -> {
            List<TransactionEntity> entities = transactionDao.getByCashbook(cashbookId);
            Log.d(TAG, "subscribeToTransactions: cashbookId=" + cashbookId + " found=" + (entities != null ? entities.size() : "null"));
            List<TransactionModel> models = EntityMapper.toModelList(entities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                synchronized (localCallbacks) {
                    if (!localCallbacks.containsKey(cashbookId)) {
                        localCallbacks.put(cashbookId, new ArrayList<>());
                    }
                    localCallbacks.get(cashbookId).add(callback);
                }
                Log.d(TAG, "subscribeToTransactions: calling callback with " + models.size() + " transactions");
                callback.onCallback(models);
            });
        });
        // Return a no-op listener for compatibility
        return new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
    }

    public void getAllTransactions(String cashbookId, DataCallback<List<TransactionModel>> callback, ErrorCallback errorCallback) {
        executorService.execute(() -> {
            List<TransactionEntity> entities = transactionDao.getByCashbook(cashbookId);
            List<TransactionModel> models = EntityMapper.toModelList(entities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(models);
            });
        });
    }

    public void addTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        executorService.execute(() -> {
            String txId = "local_tx_" + UUID.randomUUID().toString();
            transaction.setTransactionId(txId);
            TransactionEntity entity = EntityMapper.toEntity(transaction, cashbookId);
            transactionDao.insert(entity);
            updateCashbookStats(cashbookId);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                notifyLocalCallbacksFromRoom(cashbookId);
                if (callback != null) callback.onCallback(true);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    /**
     * Offline-aware transaction save.
     * Now simplified: always saves to Room (which is offline-capable), then triggers sync.
     */
    public void addTransactionOfflineAware(Context context, String cashbookId, TransactionModel transaction,
                                           DataCallback<Boolean> callback, DataCallback<Boolean> offlineCallback) {
        if (cashbookId == null || transaction == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }
        // Room is always available (offline-capable), just delegate
        addTransaction(cashbookId, transaction, callback);
    }

    public void updateTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        executorService.execute(() -> {
            TransactionEntity entity = EntityMapper.toEntity(transaction, cashbookId);
            entity.syncStatus = "MODIFIED";
            transactionDao.update(entity);
            updateCashbookStats(cashbookId);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                notifyLocalCallbacksFromRoom(cashbookId);
                if (callback != null) callback.onCallback(true);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    public void deleteTransaction(String cashbookId, String transactionId, DataCallback<Boolean> callback) {
        executorService.execute(() -> {
            transactionDao.softDelete(transactionId, System.currentTimeMillis());
            updateCashbookStats(cashbookId);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                notifyLocalCallbacksFromRoom(cashbookId);
                if (callback != null) callback.onCallback(true);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    // --- CASHBOOK METHODS ---

    public void getCashbooks(DataCallback<List<CashbookModel>> callback, ErrorCallback errorCallback) {
        executorService.execute(() -> {
            List<CashbookEntity> entities = cashbookDao.getAll();
            List<CashbookModel> models = EntityMapper.toCashbookModelList(entities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(models);
            });
        });
    }

    public void createNewCashbook(String name, DataCallback<String> callback, ErrorCallback errorCallback) {
        if (name == null || name.trim().isEmpty()) {
            if (errorCallback != null) errorCallback.onError("Cashbook name cannot be empty");
            if (callback != null) callback.onCallback(null);
            return;
        }

        executorService.execute(() -> {
            String cashbookId = "local_cb_" + UUID.randomUUID().toString();
            CashbookModel newCashbook = new CashbookModel(cashbookId, name.trim());
            newCashbook.setUserId("local_user");
            newCashbook.setCreatedDate(System.currentTimeMillis());
            newCashbook.setLastModified(System.currentTimeMillis());
            newCashbook.setActive(true);
            cashbookDao.insert(EntityMapper.toEntity(newCashbook));
            // Create default categories on background thread too
            List<CategoryModel> defaults = getStandardCategories();
            List<CategoryEntity> catEntities = new ArrayList<>();
            for (CategoryModel cat : defaults) {
                cat.setId(UUID.randomUUID().toString());
                catEntities.add(EntityMapper.toEntity(cat, cashbookId));
            }
            categoryDao.insertAll(catEntities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(cashbookId);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    public void createNewCashbook(CashbookModel cashbook, DataCallback<String> callback, ErrorCallback errorCallback) {
        executorService.execute(() -> {
            cashbookDao.insert(EntityMapper.toEntity(cashbook));
            // Create default categories on background thread
            List<CategoryModel> defaults = getStandardCategories();
            List<CategoryEntity> catEntities = new ArrayList<>();
            for (CategoryModel cat : defaults) {
                cat.setId(UUID.randomUUID().toString());
                catEntities.add(EntityMapper.toEntity(cat, cashbook.getCashbookId()));
            }
            categoryDao.insertAll(catEntities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(cashbook.getCashbookId());
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    public void updateCashbook(CashbookModel cashbook, DataCallback<Boolean> callback) {
        executorService.execute(() -> {
            cashbookDao.insert(EntityMapper.toEntity(cashbook));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(true);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    public void deleteCashbook(String cashbookId, DataCallback<Boolean> callback, ErrorCallback errorCallback) {
        executorService.execute(() -> {
            cashbookDao.softDelete(cashbookId, System.currentTimeMillis());
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(true);
            });
            // Trigger Supabase sync for authenticated users
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    public void duplicateCashbook(String originalCashbookId, String newName, DataCallback<String> callback, ErrorCallback errorCallback) {
        executorService.execute(() -> {
            CashbookEntity original = cashbookDao.getById(originalCashbookId);
            if (original != null) {
                String newCashbookId = "local_cb_" + UUID.randomUUID().toString();
                CashbookEntity copy = new CashbookEntity();
                copy.id = newCashbookId;
                copy.name = newName.trim();
                copy.userId = "local_user";
                copy.description = original.description;
                copy.category = original.category;
                copy.themeColor = original.themeColor;
                copy.themeIcon = original.themeIcon;
                copy.isCurrent = false;
                copy.isActive = true;
                copy.createdDate = System.currentTimeMillis();
                copy.lastModified = System.currentTimeMillis();
                copy.syncStatus = "PENDING";

                // Copy transactions
                List<TransactionEntity> origTxs = transactionDao.getByCashbook(originalCashbookId);
                List<TransactionEntity> copiedTxs = new ArrayList<>();
                double balance = 0;
                for (TransactionEntity origTx : origTxs) {
                    TransactionEntity txCopy = new TransactionEntity();
                    txCopy.id = "local_tx_" + UUID.randomUUID().toString();
                    txCopy.cashbookId = newCashbookId;
                    txCopy.amount = origTx.amount;
                    txCopy.type = origTx.type;
                    txCopy.transactionCategory = origTx.transactionCategory;
                    txCopy.remark = origTx.remark;
                    txCopy.paymentMode = origTx.paymentMode;
                    txCopy.timestamp = origTx.timestamp;
                    txCopy.partyName = origTx.partyName;
                    txCopy.tags = origTx.tags;
                    txCopy.location = origTx.location;
                    txCopy.attachmentUri = origTx.attachmentUri;
                    txCopy.autoFrequency = origTx.autoFrequency;
                    txCopy.taxRate = origTx.taxRate;
                    txCopy.taxAmount = origTx.taxAmount;
                    txCopy.taxInclusive = origTx.taxInclusive;
                    txCopy.lastModified = System.currentTimeMillis();
                    txCopy.syncStatus = "PENDING";
                    copiedTxs.add(txCopy);
                    if ("IN".equalsIgnoreCase(txCopy.type)) balance += txCopy.amount;
                    else balance -= txCopy.amount;
                }

                // Copy categories
                List<CategoryEntity> origCats = categoryDao.getByCashbook(originalCashbookId);
                List<CategoryEntity> copiedCats = new ArrayList<>();
                for (CategoryEntity origCat : origCats) {
                    CategoryEntity catCopy = new CategoryEntity();
                    catCopy.id = UUID.randomUUID().toString();
                    catCopy.cashbookId = newCashbookId;
                    catCopy.name = origCat.name;
                    catCopy.type = origCat.type;
                    catCopy.colorHex = origCat.colorHex;
                    catCopy.iconResId = origCat.iconResId;
                    catCopy.isCustom = origCat.isCustom;
                    catCopy.lastModified = System.currentTimeMillis();
                    catCopy.syncStatus = "PENDING";
                    copiedCats.add(catCopy);
                }

                copy.totalBalance = balance;
                copy.transactionCount = copiedTxs.size();

                cashbookDao.insert(copy);
                transactionDao.insertAll(copiedTxs);
                categoryDao.insertAll(copiedCats);

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onCallback(newCashbookId);
                });
                // Trigger Supabase sync for authenticated users
                if (!isLocalMode()) {
                    com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
                }
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (errorCallback != null) errorCallback.onError("Original cashbook not found");
                    if (callback != null) callback.onCallback(null);
                });
            }
        });
    }

    /**
     * One-time migration: Pulls existing Firebase RTDB data into Room for signed-in users.
     * This is needed because Phase 3 switched to Room-first, but existing users
     * have all their data in Firebase RTDB.
     */
    public void migrateFirebaseDataToRoom(DataCallback<Boolean> callback) {
        if (isLocalMode()) {
            if (callback != null) callback.onCallback(true);
            return;
        }

        // Check if we already migrated
        boolean alreadyMigrated = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean("firebase_to_room_migrated", false);
        if (alreadyMigrated) {
            if (callback != null) callback.onCallback(true);
            return;
        }

        DatabaseReference userDatabase = getUserDatabaseRef();
        if (userDatabase == null) {
            if (callback != null) callback.onCallback(false);
            return;
        }

        Log.d(TAG, "Starting Firebase → Room migration...");

        DatabaseReference cashbooksRef = userDatabase.child(Constants.NODE_CASHBOOKS);
        cashbooksRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                executorService.execute(() -> {
                    int cashbookCount = 0;
                    int txCount = 0;
                    int catCount = 0;

                    for (DataSnapshot cbSnapshot : snapshot.getChildren()) {
                        String cashbookId = cbSnapshot.getKey();
                        if (cashbookId == null) continue;

                        // Check if cashbook already exists in Room
                        CashbookEntity existing = cashbookDao.getById(cashbookId);
                        if (existing != null) {
                            Log.d(TAG, "Cashbook " + cashbookId + " already in Room, skipping");
                            continue;
                        }

                        try {
                            // Parse cashbook
                            CashbookEntity cbEntity = new CashbookEntity();
                            cbEntity.id = cashbookId;
                            cbEntity.name = cbSnapshot.child("name").getValue(String.class);
                            cbEntity.description = cbSnapshot.child("description").getValue(String.class);
                            cbEntity.category = cbSnapshot.child("category").getValue(String.class);
                            cbEntity.themeColor = cbSnapshot.child("themeColor").getValue(String.class);
                            cbEntity.themeIcon = cbSnapshot.child("themeIcon").getValue(String.class);
                            cbEntity.currency = cbSnapshot.child("currency").getValue(String.class);
                            cbEntity.userId = cbSnapshot.child("userId").getValue(String.class);
                            
                            Double balance = cbSnapshot.child("totalBalance").getValue(Double.class);
                            cbEntity.totalBalance = balance != null ? balance : 0;
                            
                            Integer txCountVal = cbSnapshot.child("transactionCount").getValue(Integer.class);
                            cbEntity.transactionCount = txCountVal != null ? txCountVal : 0;
                            
                            Long created = cbSnapshot.child("createdDate").getValue(Long.class);
                            cbEntity.createdDate = created != null ? created : System.currentTimeMillis();
                            
                            Long modified = cbSnapshot.child("lastModified").getValue(Long.class);
                            cbEntity.lastModified = modified != null ? modified : System.currentTimeMillis();
                            
                            Boolean active = cbSnapshot.child("active").getValue(Boolean.class);
                            cbEntity.isActive = active != null ? active : true;
                            
                            Boolean current = cbSnapshot.child("current").getValue(Boolean.class);
                            cbEntity.isCurrent = current != null ? current : false;
                            
                            Boolean favorite = cbSnapshot.child("favorite").getValue(Boolean.class);
                            cbEntity.isFavorite = favorite != null ? favorite : false;

                            if (cbEntity.name == null) cbEntity.name = "Unnamed";
                            cbEntity.syncStatus = "PENDING";
                            cbEntity.isDeleted = false;

                            cashbookDao.insert(cbEntity);
                            cashbookCount++;
                            Log.d(TAG, "Migrated cashbook: " + cbEntity.name + " (" + cashbookId + ")");

                            // Parse transactions
                            DataSnapshot txsSnapshot = cbSnapshot.child(Constants.NODE_TRANSACTIONS);
                            List<TransactionEntity> txEntities = new ArrayList<>();
                            for (DataSnapshot txSnapshot : txsSnapshot.getChildren()) {
                                String txId = txSnapshot.getKey();
                                if (txId == null) continue;

                                TransactionEntity txEntity = new TransactionEntity();
                                txEntity.id = txId;
                                txEntity.cashbookId = cashbookId;
                                
                                Double amount = txSnapshot.child("amount").getValue(Double.class);
                                txEntity.amount = amount != null ? amount : 0;
                                
                                txEntity.type = txSnapshot.child("type").getValue(String.class);
                                txEntity.transactionCategory = txSnapshot.child("transactionCategory").getValue(String.class);
                                txEntity.partyName = txSnapshot.child("partyName").getValue(String.class);
                                txEntity.paymentMode = txSnapshot.child("paymentMode").getValue(String.class);
                                txEntity.remark = txSnapshot.child("remark").getValue(String.class);
                                
                                Long ts = txSnapshot.child("timestamp").getValue(Long.class);
                                txEntity.timestamp = ts != null ? ts : System.currentTimeMillis();
                                
                                txEntity.tags = txSnapshot.child("tags").getValue(String.class);
                                txEntity.location = txSnapshot.child("location").getValue(String.class);
                                txEntity.attachmentUri = txSnapshot.child("attachmentUri").getValue(String.class);
                                txEntity.autoFrequency = txSnapshot.child("autoFrequency").getValue(String.class);
                                
                                Double taxRate = txSnapshot.child("taxRate").getValue(Double.class);
                                txEntity.taxRate = taxRate != null ? taxRate : 0;
                                Double taxAmount = txSnapshot.child("taxAmount").getValue(Double.class);
                                txEntity.taxAmount = taxAmount != null ? taxAmount : 0;
                                Boolean taxInc = txSnapshot.child("taxInclusive").getValue(Boolean.class);
                                txEntity.taxInclusive = taxInc != null ? taxInc : false;
                                
                                txEntity.lastModified = System.currentTimeMillis();
                                txEntity.syncStatus = "PENDING";
                                txEntity.isDeleted = false;

                                txEntities.add(txEntity);
                            }
                            if (!txEntities.isEmpty()) {
                                transactionDao.insertAll(txEntities);
                                txCount += txEntities.size();
                            }

                            // Parse categories
                            DataSnapshot catsSnapshot = cbSnapshot.child("categories");
                            List<CategoryEntity> catEntities = new ArrayList<>();
                            for (DataSnapshot catSnapshot : catsSnapshot.getChildren()) {
                                String catId = catSnapshot.getKey();
                                if (catId == null) continue;

                                CategoryEntity catEntity = new CategoryEntity();
                                catEntity.id = catId;
                                catEntity.cashbookId = cashbookId;
                                catEntity.name = catSnapshot.child("name").getValue(String.class);
                                catEntity.type = catSnapshot.child("type").getValue(String.class);
                                catEntity.colorHex = catSnapshot.child("colorHex").getValue(String.class);
                                
                                Integer iconRes = catSnapshot.child("iconResId").getValue(Integer.class);
                                catEntity.iconResId = iconRes != null ? iconRes : 0;
                                
                                Boolean custom = catSnapshot.child("custom").getValue(Boolean.class);
                                catEntity.isCustom = custom != null ? custom : false;
                                
                                if (catEntity.name == null) catEntity.name = "Unknown";
                                catEntity.lastModified = System.currentTimeMillis();
                                catEntity.syncStatus = "PENDING";
                                catEntity.isDeleted = false;

                                catEntities.add(catEntity);
                            }
                            if (!catEntities.isEmpty()) {
                                categoryDao.insertAll(catEntities);
                                catCount += catEntities.size();
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error migrating cashbook " + cashbookId, e);
                        }
                    }

                    // Mark migration as complete
                    context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("firebase_to_room_migrated", true)
                            .apply();

                    int finalCbCount = cashbookCount;
                    int finalTxCount = txCount;
                    int finalCatCount = catCount;
                    Log.d(TAG, "Firebase → Room migration complete: " + finalCbCount + " cashbooks, " + finalTxCount + " transactions, " + finalCatCount + " categories");

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (callback != null) callback.onCallback(true);
                        // Trigger sync to push migrated data to Supabase
                        com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
                    });
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase → Room migration failed: " + error.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onCallback(false);
                });
            }
        });
    }

    /**
     * Helper holder class for migration data to prevent race conditions during deletion.
     */
    private static class MigratedCashbookData {
        final CashbookEntity cashbook;
        final List<TransactionEntity> transactions;
        final List<CategoryEntity> categories;

        MigratedCashbookData(CashbookEntity cashbook, List<TransactionEntity> transactions, List<CategoryEntity> categories) {
            this.cashbook = cashbook;
            this.transactions = transactions;
            this.categories = categories;
        }
    }

    /**
     * Migrates local guest data to Firebase.
     */
    public void migrateLocalDataToFirebase(DataCallback<Boolean> callback) {
        if (isLocalMode()) {
            // In local mode, nothing to migrate to Firebase
            if (callback != null) callback.onCallback(true);
            return;
        }

        executorService.execute(() -> {
            List<CashbookEntity> localCashbooks = cashbookDao.getAll();
            if (localCashbooks == null || localCashbooks.isEmpty()) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onCallback(true);
                });
                return;
            }

            DatabaseReference userDatabase = getUserDatabaseRef();
            if (userDatabase == null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onCallback(false);
                });
                return;
            }

            DatabaseReference cashbooksRef = userDatabase.child(Constants.NODE_CASHBOOKS);

            final int totalCashbooks = localCashbooks.size();
            final java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);

            // Read all data from Room up front on background thread to prevent race conditions with deletion
            List<MigratedCashbookData> dataToMigrate = new ArrayList<>();
            for (CashbookEntity localCb : localCashbooks) {
                List<TransactionEntity> txs = transactionDao.getByCashbook(localCb.id);
                List<CategoryEntity> cats = categoryDao.getByCashbook(localCb.id);
                dataToMigrate.add(new MigratedCashbookData(localCb, txs, cats));
            }

            for (MigratedCashbookData data : dataToMigrate) {
                String firebaseCashbookId = cashbooksRef.push().getKey();
                if (firebaseCashbookId == null) {
                    hasError.set(true);
                    if (completedCount.incrementAndGet() == totalCashbooks) {
                        finalizeMigration(callback, false, localCashbooks);
                    }
                    continue;
                }

                CashbookModel migratedCb = EntityMapper.toModel(data.cashbook);
                migratedCb.setCashbookId(firebaseCashbookId);
                migratedCb.setUserId(userDatabase.getKey());
                migratedCb.setLastModified(System.currentTimeMillis());

                // Map transactions
                Map<String, TransactionModel> transactionsToUpload = new HashMap<>();
                for (TransactionEntity txEntity : data.transactions) {
                    String firebaseTxId = cashbooksRef.child(firebaseCashbookId).child(Constants.NODE_TRANSACTIONS).push().getKey();
                    if (firebaseTxId != null) {
                        TransactionModel migratedTx = EntityMapper.toModel(txEntity);
                        migratedTx.setTransactionId(firebaseTxId);
                        transactionsToUpload.put(firebaseTxId, migratedTx);
                    }
                }
                migratedCb.setTransactions(transactionsToUpload);

                // Map categories
                Map<String, CategoryModel> categoriesToUpload = new HashMap<>();
                for (CategoryEntity catEntity : data.categories) {
                    String firebaseCatId = cashbooksRef.child(firebaseCashbookId).child("categories").push().getKey();
                    if (firebaseCatId != null) {
                        CategoryModel migratedCat = EntityMapper.toModel(catEntity);
                        migratedCat.setId(firebaseCatId);
                        categoriesToUpload.put(firebaseCatId, migratedCat);
                    }
                }

                // Upload Cashbook and Transactions
                cashbooksRef.child(firebaseCashbookId).setValue(migratedCb)
                        .addOnSuccessListener(aVoid -> {
                            // Upload Categories if any
                            if (!categoriesToUpload.isEmpty()) {
                                cashbooksRef.child(firebaseCashbookId).child("categories").setValue(categoriesToUpload)
                                        .addOnCompleteListener(task -> {
                                            if (completedCount.incrementAndGet() == totalCashbooks) {
                                                finalizeMigration(callback, !hasError.get(), localCashbooks);
                                            }
                                        });
                            } else {
                                if (completedCount.incrementAndGet() == totalCashbooks) {
                                    finalizeMigration(callback, !hasError.get(), localCashbooks);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to upload cashbook: " + migratedCb.getName(), e);
                            hasError.set(true);
                            if (completedCount.incrementAndGet() == totalCashbooks) {
                                finalizeMigration(callback, false, localCashbooks);
                            }
                        });
            }
        });
    }

    private void finalizeMigration(DataCallback<Boolean> callback, boolean success, List<CashbookEntity> migratedCashbooks) {
        if (success) {
            // Clean up local Room data after successful migration
            executorService.execute(() -> {
                try {
                    // Clear all Room database tables completely
                    roomDb.clearAllTables();

                    // Delete legacy JSON file just in case it still exists
                    File jsonFile = new File(context.getFilesDir(), "local_data.json");
                    if (jsonFile.exists()) {
                        jsonFile.delete();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing local database during reset", e);
                }
            });

            // Clear local user shared preferences (like last selected cashbook)
            context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_local_mode", false)
                    .remove(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + "local_user")
                    .apply();

            Log.d(TAG, "Migration of local guest data completed successfully. Guest database reset.");
        }
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onCallback(success));
        }
    }

    // --- ROOM HELPER METHODS ---

    /**
     * Recalculates and updates cashbook balance and transaction count from Room DB.
     * MUST be called on a background thread.
     */
    private void updateCashbookStats(String cashbookId) {
        double balance = transactionDao.calculateBalance(cashbookId);
        int count = transactionDao.countByCashbook(cashbookId);
        CashbookEntity cb = cashbookDao.getById(cashbookId);
        if (cb != null) {
            cb.totalBalance = balance;
            cb.transactionCount = count;
            cb.lastModified = System.currentTimeMillis();
            cashbookDao.update(cb);
        }
    }

    /**
     * Notifies local transaction subscribers by reading fresh data from Room.
     * MUST be called on the main thread.
     */
    private void notifyLocalCallbacksFromRoom(String cashbookId) {
        executorService.execute(() -> {
            List<TransactionEntity> entities = transactionDao.getByCashbook(cashbookId);
            List<TransactionModel> models = EntityMapper.toModelList(entities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                List<DataCallback<List<TransactionModel>>> callbacks;
                synchronized (localCallbacks) {
                    List<DataCallback<List<TransactionModel>>> list = localCallbacks.get(cashbookId);
                    callbacks = list != null ? new ArrayList<>(list) : null;
                }
                if (callbacks != null) {
                    for (DataCallback<List<TransactionModel>> cb : callbacks) {
                        cb.onCallback(models);
                    }
                }
            });
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