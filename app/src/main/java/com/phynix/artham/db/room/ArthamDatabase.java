package com.phynix.artham.db.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.phynix.artham.db.room.dao.CashbookDao;
import com.phynix.artham.db.room.dao.CategoryDao;
import com.phynix.artham.db.room.dao.TransactionDao;
import com.phynix.artham.db.room.entity.CashbookEntity;
import com.phynix.artham.db.room.entity.CategoryEntity;
import com.phynix.artham.db.room.entity.TransactionEntity;

/**
 * ArthamDatabase — Room database for offline-first local storage.
 * Replaces the previous JSON file approach with a proper SQLite database.
 *
 * Thread-safe singleton pattern ensures only one instance exists app-wide.
 */
@Database(
    entities = {
        TransactionEntity.class,
        CashbookEntity.class,
        CategoryEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class ArthamDatabase extends RoomDatabase {

    private static final String DB_NAME = "artham_database";
    private static volatile ArthamDatabase INSTANCE;

    // --- DAO Access ---
    public abstract TransactionDao transactionDao();
    public abstract CashbookDao cashbookDao();
    public abstract CategoryDao categoryDao();

    /**
     * Get the singleton database instance.
     * Uses double-checked locking for thread safety.
     */
    public static ArthamDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ArthamDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ArthamDatabase.class,
                            DB_NAME
                    )
                    // NEVER use fallbackToDestructiveMigration() — it wipes ALL data on schema changes!
                    // Use OnDowngrade only, and add proper migrations for upgrades.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
