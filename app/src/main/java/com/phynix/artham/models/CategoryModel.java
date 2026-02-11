package com.phynix.artham.models;

import android.graphics.Color;

public class CategoryModel {
    private String id;
    private String name;
    private String type; // "Expense" or "Income"
    private int color;
    private boolean isCustom;

    // Default constructor required for calls to DataSnapshot.getValue(CategoryModel.class)
    public CategoryModel() {
    }

    // Main constructor
    public CategoryModel(String id, String name, String type, int color, boolean isCustom) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.color = color;
        this.isCustom = isCustom;
    }

    // Constructor to fix the error in CategoryActivity
    // Parses String color to int and sets default type (can be updated later or passed as arg)
    public CategoryModel(String name, String colorHex, boolean isCustom) {
        this.id = name; // Defaulting ID to name if not provided
        this.name = name;
        this.type = "Expense"; // Default type, logic in Activity should handle setting this correctly
        try {
            this.color = Color.parseColor(colorHex);
        } catch (IllegalArgumentException e) {
            this.color = Color.GRAY; // Fallback color
        }
        this.isCustom = isCustom;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }
}