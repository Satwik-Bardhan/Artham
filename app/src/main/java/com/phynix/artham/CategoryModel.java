package com.phynix.artham;

import java.io.Serializable;

// [FIX] Implement Serializable to pass this object in Intents
public class CategoryModel implements Serializable {
    private String name;
    private String colorHex;
    private boolean isCustom; // To distinguish between predefined and user-added

    public CategoryModel() {
        // Default constructor required for Firebase
    }

    // Constructor for easier object creation
    public CategoryModel(String name, String colorHex) {
        this.name = name;
        this.colorHex = colorHex;
        this.isCustom = false; // Default to not custom
    }

    public CategoryModel(String name, String colorHex, boolean isCustom) {
        this.name = name;
        this.colorHex = colorHex;
        this.isCustom = isCustom;
    }

    // --- Getters and Setters ---
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }
}