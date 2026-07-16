package com.phynix.artham.db.room.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a transaction category.
 * Maps to the 'categories' SQLite table.
 */
@Entity(tableName = "categories", indices = {@Index("cashbookId")})
public class CategoryEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String cashbookId;

    @NonNull
    public String name;

    public String type; // "Income" or "Expense"

    public String colorHex;

    public int iconResId;

    @ColumnInfo(defaultValue = "0")
    public boolean isCustom = false;

    // Sync tracking (for future Phase 3)
    @NonNull
    @ColumnInfo(defaultValue = "PENDING")
    public String syncStatus = "PENDING";

    public long lastModified;

    @ColumnInfo(defaultValue = "0")
    public boolean isDeleted = false;
}
