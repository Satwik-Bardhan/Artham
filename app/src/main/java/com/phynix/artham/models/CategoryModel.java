package com.phynix.artham.models;

import java.io.Serializable;

public class CategoryModel implements Serializable {
    private String id;
    private String name;
    private String type; // "Income" or "Expense"
    private String colorHex;
    private int iconResId; // Resource ID for the fixed logo
    private boolean isCustom;

    // 1. Required Empty Constructor for Firebase
    public CategoryModel() {
    }

    // 2. Simple Constructor for default categories
    public CategoryModel(String name, String type) {
        this.name = name;
        this.type = type;
        this.colorHex = "#9E9E9E"; // Default Grey
        this.iconResId = 0;
        this.isCustom = false;
    }

    // 3. Full Constructor
    public CategoryModel(String name, String type, String colorHex, int iconResId, boolean isCustom) {
        this.name = name;
        this.type = type;
        this.colorHex = colorHex;
        this.iconResId = iconResId;
        this.isCustom = isCustom;
    }

    // 4. Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    // This is the method the Adapter is looking for
    public int getIconResId() { return iconResId; }
    public void setIconResId(int iconResId) { this.iconResId = iconResId; }

    public boolean isCustom() { return isCustom; }
    public void setCustom(boolean custom) { isCustom = custom; }
}
