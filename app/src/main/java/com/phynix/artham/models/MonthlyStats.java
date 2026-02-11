package com.phynix.artham.models;

public class MonthlyStats {
    private String month;
    private String year;
    private double income;
    private double expense;

    public MonthlyStats(String month, String year, double income, double expense) {
        this.month = month;
        this.year = year;
        this.income = income;
        this.expense = expense;
    }

    public String getMonth() { return month; }
    public String getYear() { return year; }
    public double getIncome() { return income; }
    public double getExpense() { return expense; }
}