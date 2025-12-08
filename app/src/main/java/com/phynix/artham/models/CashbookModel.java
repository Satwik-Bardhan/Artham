package com.phynix.artham.models;

import java.io.Serializable;
import java.util.Objects;

// [FIX] Implement Serializable so this object can be passed in an Intent
public class CashbookModel implements Serializable {
    private String cashbookId;
    private String name;
    private String description;
    private double totalBalance;
    private int transactionCount;
    private long createdDate;
    private long lastModified;
    private boolean isActive;
    // [FIX] Use a primitive boolean for isCurrent, default to false
    private boolean isCurrent;
    private boolean isFavorite;
    private String userId;
    private String currency;

    // Empty constructor for Firebase
    public CashbookModel() {
        this.isCurrent = false; // Ensure default
    }

    // Constructor for simple creation
    public CashbookModel(String cashbookId, String name) {
        this.cashbookId = cashbookId;
        this.name = name;
        this.description = "";
        this.totalBalance = 0.0;
        this.transactionCount = 0;
        this.createdDate = System.currentTimeMillis();
        this.lastModified = System.currentTimeMillis();
        this.isActive = true;
        this.isCurrent = false;
        this.isFavorite = false;
        this.currency = "INR";
        // [FIX] No need to set userId here, it's set when saved if needed
    }

    // Getters and Setters
    public String getCashbookId() {
        return cashbookId;
    }

    public void setCashbookId(String cashbookId) {
        this.cashbookId = cashbookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // [FIX] Renamed to getBalance() to match your Java code's usage
    public double getBalance() {
        return totalBalance;
    }

    public void setBalance(double totalBalance) {
        this.totalBalance = totalBalance;
    }

    public double getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(double totalBalance) {
        this.totalBalance = totalBalance;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    // [FIX] Changed from Boolean to boolean
    public boolean isCurrent() {
        return isCurrent;
    }

    // [FIX] Changed from Boolean to boolean
    public void setCurrent(boolean current) {
        isCurrent = current;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    // [FIX] Added equals() and hashCode() for proper list management
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CashbookModel that = (CashbookModel) o;
        return Objects.equals(cashbookId, that.cashbookId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cashbookId);
    }

    // [FIX] Added getId() method as it was referenced in your old adapter
    public String getId() {
        return cashbookId;
    }
}