package com.phynix.artham.models;

public class LegendData {
    private String categoryName;
    private double amount;
    private float percentage;
    private int color; // Resolved Int color (Hex parsed or Resource ID converted)
    private int iconResId; // Specific Icon Resource ID for this category

    public LegendData(String categoryName, double amount, float percentage, int color, int iconResId) {
        this.categoryName = categoryName;
        this.amount = amount;
        this.percentage = percentage;
        this.color = color;
        this.iconResId = iconResId;
    }

    public String getCategoryName() { return categoryName; }
    public double getAmount() { return amount; }
    public float getPercentage() { return percentage; }
    public int getColor() { return color; }
    public int getIconResId() { return iconResId; } // NEW: Returns the exact icon
}