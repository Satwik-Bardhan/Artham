package com.phynix.artham.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CashbookModel implements Serializable {
    private String cashbookId;
    private String name;
    private String description;
    private double totalBalance;
    private int transactionCount;
    private long createdDate;
    private long lastModified;
    private boolean isActive;
    private boolean isCurrent;
    private boolean isFavorite;
    private String userId;
    private String currency;

    // [FIX] Added to resolve "No setter/field for transactions" warning
    private List<TransactionModel> transactions;

    // Empty constructor for Firebase
    public CashbookModel() {
        this.isCurrent = false;
        this.transactions = new ArrayList<>(); // Initialize to avoid null pointers
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
        this.transactions = new ArrayList<>();
    }

    // --- Getters and Setters ---

    // [FIX] Primary ID handling
    public String getCashbookId() {
        return cashbookId;
    }

    public void setCashbookId(String cashbookId) {
        this.cashbookId = cashbookId;
    }

    // [FIX] Aliases for "id" field to resolve Firebase case-sensitive warning
    public String getId() {
        return cashbookId;
    }

    public void setId(String id) {
        this.cashbookId = id;
    }

    // [FIX] Transaction List handling
    public List<TransactionModel> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<TransactionModel> transactions) {
        this.transactions = transactions;
    }

    // Standard Fields
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

    // Balance handling
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

    // Counts & Dates
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

    // Booleans
    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        isCurrent = current;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    // User & Currency
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

    // Equality Checks for Adapter DiffUtil
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
}