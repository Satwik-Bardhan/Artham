package com.phynix.artham.db.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.phynix.artham.db.room.entity.TransactionEntity;

import java.util.List;

/**
 * Data Access Object for transaction operations.
 */
@Dao
public interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE cashbookId = :cashbookId AND isDeleted = 0 ORDER BY timestamp DESC")
    List<TransactionEntity> getByCashbook(String cashbookId);

    @Query("SELECT * FROM transactions WHERE id = :id")
    TransactionEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TransactionEntity transaction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TransactionEntity> transactions);

    @Update
    void update(TransactionEntity transaction);

    @Query("UPDATE transactions SET isDeleted = 1, syncStatus = 'DELETED', lastModified = :now WHERE id = :id")
    void softDelete(String id, long now);

    @Query("DELETE FROM transactions WHERE id = :id")
    void hardDelete(String id);

    @Query("DELETE FROM transactions WHERE cashbookId = :cashbookId")
    void deleteAllByCashbook(String cashbookId);

    @Query("SELECT COUNT(*) FROM transactions WHERE cashbookId = :cashbookId AND isDeleted = 0")
    int countByCashbook(String cashbookId);

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'IN' THEN amount ELSE 0 END), 0) - COALESCE(SUM(CASE WHEN type != 'IN' THEN amount ELSE 0 END), 0) FROM transactions WHERE cashbookId = :cashbookId AND isDeleted = 0")
    double calculateBalance(String cashbookId);

    @Query("SELECT * FROM transactions WHERE syncStatus != 'SYNCED' AND isDeleted = 0")
    List<TransactionEntity> getUnsynced();

    @Query("SELECT * FROM transactions WHERE isDeleted = 0")
    List<TransactionEntity> getAll();

    @Query("SELECT * FROM transactions WHERE syncStatus = 'DELETED'")
    List<TransactionEntity> getDeleted();
}
