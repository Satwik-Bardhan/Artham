package com.phynix.artham.models; // Change to com.phynix.artham if your package is different

import java.io.Serializable;

public class CategoryModel implements Serializable {
    private String name;
    private String colorHex;
    private boolean isCustom;

    // 1. Required Empty Constructor for Firebase
    public CategoryModel() {
    }

    // 2. Full Constructor
    public CategoryModel(String name, String colorHex, boolean isCustom) {
        this.name = name;
        this.colorHex = colorHex;
        this.isCustom = isCustom;
    }

    // 3. Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public boolean isCustom() { return isCustom; }
    public void setCustom(boolean custom) { isCustom = custom; }
}