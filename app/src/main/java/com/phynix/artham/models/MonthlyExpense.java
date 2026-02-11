package com.phynix.artham.models;

import java.util.List;

public class MonthlyExpense {
    private String month; // Format: yyyy-MM
    private double totalExpense;
    private List<TransactionModel> transactions;

    public MonthlyExpense(String month, double totalExpense, List<TransactionModel> transactions) {
        this.month = month;
        this.totalExpense = totalExpense;
        this.transactions = transactions;
    }

    public String getMonth() {
        return month;
    }

    public double getTotalExpense() {
        return totalExpense;
    }

    public List<TransactionModel> getTransactions() {
        return transactions;
    }
}