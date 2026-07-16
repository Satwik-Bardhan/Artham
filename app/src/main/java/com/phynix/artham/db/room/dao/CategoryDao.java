package com.phynix.artham.db.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.phynix.artham.db.room.entity.CategoryEntity;

import java.util.List;

/**
 * Data Access Object for category operations.
 */
@Dao
public interface CategoryDao {

    @Query("SELECT * FROM categories WHERE cashbookId = :cashbookId AND isDeleted = 0")
    List<CategoryEntity> getByCashbook(String cashbookId);

    @Query("SELECT * FROM categories WHERE id = :id")
    CategoryEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CategoryEntity category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CategoryEntity> categories);

    @Update
    void update(CategoryEntity category);

    @Query("UPDATE categories SET isDeleted = 1, syncStatus = 'DELETED', lastModified = :now WHERE id = :id")
    void softDelete(String id, long now);

    @Query("DELETE FROM categories WHERE cashbookId = :cashbookId")
    void deleteAllByCashbook(String cashbookId);

    @Query("SELECT * FROM categories WHERE syncStatus != 'SYNCED' AND isDeleted = 0")
    List<CategoryEntity> getUnsynced();

    @Query("SELECT * FROM categories WHERE syncStatus = 'DELETED'")
    List<CategoryEntity> getDeleted();

    @Query("DELETE FROM categories WHERE id = :id")
    void hardDelete(String id);
}
