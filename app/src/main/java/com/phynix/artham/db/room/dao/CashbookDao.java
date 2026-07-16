package com.phynix.artham.db.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.phynix.artham.db.room.entity.CashbookEntity;

import java.util.List;

/**
 * Data Access Object for cashbook operations.
 */
@Dao
public interface CashbookDao {

    @Query("SELECT * FROM cashbooks WHERE isDeleted = 0")
    List<CashbookEntity> getAll();

    @Query("SELECT * FROM cashbooks WHERE id = :id")
    CashbookEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CashbookEntity cashbook);

    @Update
    void update(CashbookEntity cashbook);

    @Query("UPDATE cashbooks SET isDeleted = 1, syncStatus = 'DELETED', lastModified = :now WHERE id = :id")
    void softDelete(String id, long now);

    @Query("DELETE FROM cashbooks WHERE id = :id")
    void hardDelete(String id);

    @Query("SELECT * FROM cashbooks WHERE syncStatus != 'SYNCED' AND isDeleted = 0")
    List<CashbookEntity> getUnsynced();

    @Query("SELECT * FROM cashbooks WHERE syncStatus = 'DELETED'")
    List<CashbookEntity> getDeleted();
}
