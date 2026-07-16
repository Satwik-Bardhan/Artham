package com.phynix.artham.db.room.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a financial transaction.
 * Maps to the 'transactions' SQLite table.
 */
@Entity(tableName = "transactions", indices = {@Index("cashbookId")})
public class TransactionEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String cashbookId;

    public double amount;

    public String type; // "IN" or "OUT"

    public String transactionCategory;

    public String partyName;

    public String paymentMode;

    public String remark;

    public long timestamp;

    // Extended fields
    public String tags;
    public String location;
    public String attachmentUri;
    public String autoFrequency;
    public double taxRate;
    public double taxAmount;
    public boolean taxInclusive;

    // Sync tracking (for future Phase 3)
    @NonNull
    @ColumnInfo(defaultValue = "PENDING")
    public String syncStatus = "PENDING";

    public long lastModified;

    @ColumnInfo(defaultValue = "0")
    public boolean isDeleted = false;
}
