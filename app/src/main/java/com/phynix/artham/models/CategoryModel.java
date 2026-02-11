package com.phynix.artham.models;

import java.io.Serializable;

public class CategoryModel implements Serializable {
    private String id;
    private String name;
    private String type; // "UNIVERSAL", "INCOME", "EXPENSE"
    private String colorHex; // For custom colors (e.g. "#FF5733")
    private int iconResId;   // For custom icons
    private boolean isCustom;

    // 1. Required Empty Constructor for Firebase
    public CategoryModel() {
    }

    // 2. Full Constructor
    public CategoryModel(String name, String type, String colorHex, int iconResId, boolean isCustom) {
        this.name = name;
        this.type = type;
        this.colorHex = colorHex;
        this.iconResId = iconResId;
        this.isCustom = isCustom;
    }

    // 3. Convenience Constructor (For default categories)
    public CategoryModel(String name, String type) {
        this.name = name;
        this.type = type;
        this.isCustom = false;
        this.colorHex = null;
        this.iconResId = 0;
    }

    // 4. [NEW FIX] Constructor specifically for CategoryFilterFragment
    // Maps (name, colorHex, isCustom) -> sets Type to "UNIVERSAL"
    public CategoryModel(String name, String colorHex, boolean isCustom) {
        this.name = name;
        this.type = "UNIVERSAL";
        this.colorHex = colorHex;
        this.isCustom = isCustom;
        this.iconResId = 0; // 0 defaults to the standard category icon in our Adapter
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public int getIconResId() { return iconResId; }
    public void setIconResId(int iconResId) { this.iconResId = iconResId; }

    public boolean isCustom() { return isCustom; }
    public void setCustom(boolean custom) { isCustom = custom; }
}