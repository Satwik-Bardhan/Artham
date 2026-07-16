package com.phynix.artham.db.room;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.phynix.artham.db.room.dao.CashbookDao;
import com.phynix.artham.db.room.dao.CategoryDao;
import com.phynix.artham.db.room.dao.TransactionDao;
import com.phynix.artham.db.room.entity.CashbookEntity;
import com.phynix.artham.db.room.entity.CategoryEntity;
import com.phynix.artham.db.room.entity.TransactionEntity;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.models.TransactionModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JsonToRoomMigrator — One-time migration utility.
 *
 * Migrates existing local_data.json data (from the old Gson-based storage)
 * into the new Room database. Runs only once on app update.
 *
 * After successful migration, the old JSON file is deleted.
 */
public class JsonToRoomMigrator {

    private static final String TAG = "JsonToRoomMigrator";
    private static final String LOCAL_FILE_NAME = "local_data.json";
    private static final String PREF_KEY_MIGRATED = "json_to_room_migrated";
    private static final String PREF_NAME = "AppPrefs";

    /**
     * Wrapper class matching the old DataRepository.LocalDataWrapper structure.
     */
    private static class LocalDataWrapper {
        List<CashbookModel> cashbooks = new ArrayList<>();
        Map<String, List<CategoryModel>> categories = new java.util.HashMap<>();
    }

    /**
     * Run the migration if needed. Safe to call multiple times —
     * it only executes once.
     *
     * MUST be called on a background thread (Room doesn't allow main thread queries).
     */
    public static void migrateIfNeeded(Context context, ArthamDatabase db) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Already migrated — skip
        if (prefs.getBoolean(PREF_KEY_MIGRATED, false)) {
            return;
        }

        File jsonFile = new File(context.getFilesDir(), LOCAL_FILE_NAME);
        if (!jsonFile.exists()) {
            // No old data to migrate — mark as done
            prefs.edit().putBoolean(PREF_KEY_MIGRATED, true).apply();
            Log.d(TAG, "No local_data.json found. Skipping migration.");
            return;
        }

        try {
            Log.d(TAG, "Starting JSON → Room migration...");

            // 1. Read old JSON
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            Gson gson = new Gson();
            Type type = new TypeToken<LocalDataWrapper>(){}.getType();
            LocalDataWrapper wrapper = gson.fromJson(sb.toString(), type);

            if (wrapper == null || wrapper.cashbooks == null || wrapper.cashbooks.isEmpty()) {
                Log.d(TAG, "JSON data is empty. Skipping migration.");
                prefs.edit().putBoolean(PREF_KEY_MIGRATED, true).apply();
                jsonFile.delete();
                return;
            }

            // 2. Migrate to Room
            CashbookDao cashbookDao = db.cashbookDao();
            TransactionDao transactionDao = db.transactionDao();
            CategoryDao categoryDao = db.categoryDao();

            int totalCashbooks = 0;
            int totalTransactions = 0;
            int totalCategories = 0;

            for (CashbookModel cb : wrapper.cashbooks) {
                // Insert cashbook
                CashbookEntity cbEntity = EntityMapper.toEntity(cb);
                if (cbEntity.id == null || cbEntity.id.isEmpty()) {
                    cbEntity.id = "local_cb_" + UUID.randomUUID().toString();
                }
                cashbookDao.insert(cbEntity);
                totalCashbooks++;

                // Insert transactions
                if (cb.getTransactions() != null) {
                    for (Map.Entry<String, TransactionModel> entry : cb.getTransactions().entrySet()) {
                        TransactionModel tx = entry.getValue();
                        if (tx.getTransactionId() == null || tx.getTransactionId().isEmpty()) {
                            tx.setTransactionId(entry.getKey());
                        }
                        TransactionEntity txEntity = EntityMapper.toEntity(tx, cbEntity.id);
                        transactionDao.insert(txEntity);
                        totalTransactions++;
                    }
                }

                // Insert categories
                List<CategoryModel> cats = wrapper.categories.get(cb.getCashbookId());
                if (cats != null) {
                    List<CategoryEntity> catEntities = new ArrayList<>();
                    for (CategoryModel cat : cats) {
                        if (cat.getId() == null || cat.getId().isEmpty()) {
                            cat.setId(UUID.randomUUID().toString());
                        }
                        catEntities.add(EntityMapper.toEntity(cat, cbEntity.id));
                    }
                    categoryDao.insertAll(catEntities);
                    totalCategories += catEntities.size();
                }
            }

            // 3. Mark as migrated and delete old file
            prefs.edit().putBoolean(PREF_KEY_MIGRATED, true).apply();
            jsonFile.delete();

            Log.d(TAG, "✅ Migration complete: " + totalCashbooks + " cashbooks, "
                    + totalTransactions + " transactions, " + totalCategories + " categories.");

        } catch (Exception e) {
            Log.e(TAG, "❌ Migration failed. Old data preserved.", e);
            // Don't mark as migrated — will retry next app launch
        }
    }
}
