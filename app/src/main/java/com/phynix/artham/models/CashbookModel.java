package com.phynix.artham.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // Use Map to handle Firebase's JSON structure for lists/objects correctly
    private Map<String, TransactionModel> transactions;

    // --- Constructors ---

    // Empty constructor required for Firebase DataSnapshot.getValue()
    public CashbookModel() {
        this.transactions = new HashMap<>();
        this.isCurrent = false;
        this.totalBalance = 0.0;
        this.transactionCount = 0;
    }

    // Constructor for creating a new Cashbook manually
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
        this.transactions = new HashMap<>();
    }

    // --- IDs ---

    public String getCashbookId() {
        return cashbookId;
    }

    public void setCashbookId(String cashbookId) {
        this.cashbookId = cashbookId;
    }

    // Helper alias for getCashbookId()
    public String getId() {
        return cashbookId;
    }

    public void setId(String id) {
        this.cashbookId = id;
    }

    // --- Transactions (Map Handling) ---

    public Map<String, TransactionModel> getTransactions() {
        if (transactions == null) {
            transactions = new HashMap<>();
        }
        return transactions;
    }

    public void setTransactions(Map<String, TransactionModel> transactions) {
        this.transactions = transactions;
    }

    // Helper to get a List for RecyclerView Adapters
    public List<TransactionModel> getTransactionList() {
        if (transactions == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(transactions.values());
    }

    // --- Basic Fields ---

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

    // --- Balance & Counts ---

    public double getBalance() {
        return totalBalance;
    }

    public void setBalance(double totalBalance) {
        this.totalBalance = totalBalance;
    }

    // Alias for Firebase property mapping
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

    // --- Dates ---

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

    // --- Status Flags ---

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

    // --- User & Settings ---

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

    // --- Utilities ---

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