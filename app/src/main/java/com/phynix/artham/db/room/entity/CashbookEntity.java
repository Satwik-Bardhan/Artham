package com.phynix.artham.db.room.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a cashbook (expense ledger).
 * Maps to the 'cashbooks' SQLite table.
 */
@Entity(tableName = "cashbooks")
public class CashbookEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String name;

    public String description;
    public String category;
    public String themeColor;
    public String themeIcon;

    @ColumnInfo(defaultValue = "INR")
    public String currency = "INR";

    @ColumnInfo(defaultValue = "0")
    public double totalBalance = 0;

    @ColumnInfo(defaultValue = "0")
    public int transactionCount = 0;

    public long createdDate;
    public long lastModified;
    public long lastOpenedAt;

    @ColumnInfo(defaultValue = "1")
    public boolean isActive = true;

    @ColumnInfo(defaultValue = "0")
    public boolean isCurrent = false;

    @ColumnInfo(defaultValue = "0")
    public boolean isFavorite = false;

    public String userId;

    // Sync tracking (for future Phase 3)
    @NonNull
    @ColumnInfo(defaultValue = "PENDING")
    public String syncStatus = "PENDING";

    @ColumnInfo(defaultValue = "0")
    public boolean isDeleted = false;
}
