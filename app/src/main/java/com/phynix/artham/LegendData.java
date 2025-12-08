package com.phynix.artham;

// [FIX] Implemented Serializable in case you ever need to pass it
import java.io.Serializable;

public class LegendData implements Serializable {
    private String categoryName;
    private String amount;
    private float percentage; // [FIX] Changed from int to float for precision
    private int color;

    public LegendData(String categoryName, String amount, float percentage, int color) {
        this.categoryName = categoryName;
        this.amount = amount;
        this.percentage = percentage;
        this.color = color;
    }

    // Getters
    public String getCategoryName() { return categoryName; }
    public String getAmount() { return amount; }
    public float getPercentage() { return percentage; } // [FIX]
    public int getColor() { return color; }
}