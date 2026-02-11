package com.phynix.artham.models;

public class LegendData {
    private String categoryName;
    private double amount;
    private float percentage;
    private int color;

    public LegendData(String categoryName, double amount, float percentage, int color) {
        this.categoryName = categoryName;
        this.amount = amount;
        this.percentage = percentage;
        this.color = color;
    }

    public String getCategoryName() { return categoryName; }
    public double getAmount() { return amount; }
    public float getPercentage() { return percentage; }
    public int getColor() { return color; }
}