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
import com.phynix.artham.auth.AuthManager;

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
 * DataRepository - Centralized data access layer for Artham app.
 * Uses Room for local storage and Supabase for cloud sync.
 */
public class DataRepository {

    private static final String TAG = "DataRepository";
    private static volatile DataRepository INSTANCE;

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

        // ═══════════════ EXPENSES ═══════════════
        categories.add(new CategoryModel("Food & Dining",    "Expense", "#FF7043", R.drawable.ic_food_dining,      false));
        categories.add(new CategoryModel("Groceries",        "Expense", "#8BC34A", R.drawable.ic_groceries,        false));
        categories.add(new CategoryModel("Bills & Utility",  "Expense", "#FFDE21", R.drawable.ic_utilities,       false));
        categories.add(new CategoryModel("Subscriptions",    "Expense", "#3F51B5", R.drawable.ic_subscriptions,    false));
        categories.add(new CategoryModel("Transport",        "Expense", "#29B6F6", R.drawable.ic_transportation,   false));
        categories.add(new CategoryModel("Travel",           "Expense", "#03A9F4", R.drawable.ic_flight,           false));
        categories.add(new CategoryModel("Rent",             "Expense", "#FFA726", R.drawable.ic_home,             false));
        categories.add(new CategoryModel("Insurance",        "Expense", "#795548", R.drawable.ic_security,         false));
        categories.add(new CategoryModel("Shopping",         "Expense", "#EC407A", R.drawable.ic_shopping_cart,     false));
        categories.add(new CategoryModel("Entertainment",    "Expense", "#AB47BC", R.drawable.ic_entertainment,    false));
        categories.add(new CategoryModel("Health",           "Expense", "#EF5350", R.drawable.ic_medicine,         false));
        categories.add(new CategoryModel("Education",        "Expense", "#5C6BC0", R.drawable.ic_book,             false));
        categories.add(new CategoryModel("Personal",         "Expense", "#607D8B", R.drawable.ic_person,           false));
        categories.add(new CategoryModel("Gifts & Charity",  "Expense", "#E91E63", R.drawable.ic_card_giftcard,    false));
        categories.add(new CategoryModel("Business",         "Expense", "#78909C", R.drawable.ic_work,             false));
        categories.add(new CategoryModel("Taxes",            "Expense", "#E53935", R.drawable.ic_receipt_outline,  false));
        categories.add(new CategoryModel("EMI & Loans",      "Expense", "#FF8A65", R.drawable.ic_account_balance,  false));
        categories.add(new CategoryModel("Kids & Family",    "Expense", "#CE93D8", R.drawable.ic_group_outline,    false));
        categories.add(new CategoryModel("Pets",             "Expense", "#A1887F", R.drawable.ic_star_outline,     false));
        categories.add(new CategoryModel("Other Expenses",   "Expense", "#9E9E9E", R.drawable.ic_category,         false));

        // ═══════════════ INCOME ═══════════════
        categories.add(new CategoryModel("Salary",              "Income", "#66BB6A", R.drawable.ic_money,            false));
        categories.add(new CategoryModel("Freelance",           "Income", "#CDDC39", R.drawable.ic_work,             false));
        categories.add(new CategoryModel("Business Revenue",    "Income", "#42A5F5", R.drawable.ic_bar_graph,        false));
        categories.add(new CategoryModel("Investment",          "Income", "#009688", R.drawable.ic_trending_up,      false));
        categories.add(new CategoryModel("Rental Income",       "Income", "#FFA726", R.drawable.ic_home,             false));
        categories.add(new CategoryModel("Interest & Dividends","Income", "#00ACC1", R.drawable.ic_coins_outline,    false));
        categories.add(new CategoryModel("Gifts",               "Income", "#FFEB3B", R.drawable.ic_card_giftcard,    false));
        categories.add(new CategoryModel("Refunds",             "Income", "#4DB6AC", R.drawable.ic_assignment_return,false));
        categories.add(new CategoryModel("Other Income",        "Income", "#9E9E9E", R.drawable.ic_category,         false));

        return categories;
    }

    public void getCategories(String cashbookId, DataCallback<List<CategoryModel>> callback) {
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
                // Cache custom categories so pie chart and legend resolve colors/icons correctly
                for (CategoryModel cat : models) {
                    if (cat.isCustom()) {
                        com.phynix.artham.utils.CategoryColorUtil.cacheUserCategory(cat);
                    }
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onCallback(models);
                });
            }
        });
    }

    public void addCategory(String cashbookId, CategoryModel category, DataCallback<Boolean> callback) {
        executorService.execute(() -> {
            if (category.getId() == null || category.getId().isEmpty()) {
                category.setId(UUID.randomUUID().toString());
            }
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

    public void deleteCategory(String categoryId, DataCallback<Boolean> callback) {
        executorService.execute(() -> {
            categoryDao.softDelete(categoryId, System.currentTimeMillis());
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onCallback(true);
            });
            if (!isLocalMode()) {
                com.phynix.artham.db.sync.SyncEngine.triggerSync(context);
            }
        });
    }

    // --- TRANSACTION METHODS ---

    /**
     * Subscribes to real-time transaction updates for a specific cashbook.
     * Subscribes to transaction updates for a specific cashbook from Room database.
     */
    public void subscribeToTransactions(String cashbookId, DataCallback<List<TransactionModel>> callback, ErrorCallback errorCallback) {
        executorService.execute(() -> {
            List<TransactionEntity> entities = transactionDao.getByCashbook(cashbookId);
            Log.d(TAG, "subscribeToTransactions: cashbookId=" + cashbookId + " found=" + (entities != null ? entities.size() : "null"));
            List<TransactionModel> models = EntityMapper.toModelList(entities);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                synchronized (localCallbacks) {
                    if (!localCallbacks.containsKey(cashbookId)) {
                        localCallbacks.put(cashbookId, new ArrayList<>());
                    }
                    List<DataCallback<List<TransactionModel>>> cbList = localCallbacks.get(cashbookId);
                    if (cbList != null && !cbList.contains(callback)) {
                        cbList.add(callback);
                    }
                }
                Log.d(TAG, "subscribeToTransactions: calling callback with " + models.size() + " transactions");
                callback.onCallback(models);
            });
        });
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
            // Recalculate balance and transaction count from actual transaction data
            // to ensure stats are always accurate (fixes stale values after sync/migration)
            List<CashbookEntity> entities = cashbookDao.getAll();
            for (CashbookEntity cb : entities) {
                int freshCount = transactionDao.countByCashbook(cb.id);
                if (freshCount == 0 && (cb.totalBalance != 0.0 || cb.transactionCount != 0)) {
                    // Preserving pulled cloud balance and transaction count
                    continue;
                }
                double freshBalance = transactionDao.calculateBalance(cb.id);
                if (cb.totalBalance != freshBalance || cb.transactionCount != freshCount) {
                    cb.totalBalance = freshBalance;
                    cb.transactionCount = freshCount;
                    cashbookDao.update(cb);
                }
            }
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
            String prefix = isLocalMode() ? "local_cb_" : "cb_";
            String cashbookId = prefix + UUID.randomUUID().toString();
            CashbookModel newCashbook = new CashbookModel(cashbookId, name.trim());
            newCashbook.setUserId(AuthManager.getUserId(context));
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
            if (cashbook.getUserId() == null || cashbook.getUserId().isEmpty()) {
                cashbook.setUserId(AuthManager.getUserId(context));
            }
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
     * Migration from Firebase has already been completed.
     * This method now just returns true immediately.
     */
    public void migrateFirebaseDataToRoom(DataCallback<Boolean> callback) {
        if (callback != null) callback.onCallback(true);
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
            // Mark as MODIFIED so SyncEngine pushes the updated balance to Supabase
            cb.syncStatus = "MODIFIED";
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
        return AuthManager.isSignedIn(context);
    }

    public String getCurrentUserId() {
        return AuthManager.getUserId(context);
    }

    public void clearLocalDatabase() {
        executorService.execute(() -> {
            try {
                roomDb.clearAllTables();
                synchronized (localCallbacks) {
                    localCallbacks.clear();
                }
                Log.d(TAG, "Local database and callbacks cleared successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear local database", e);
            }
        });
    }
}
