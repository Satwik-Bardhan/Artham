package com.phynix.artham.models;

import com.phynix.artham.R;

public class CategoryModel {
    private String id;
    private String name;
    private String type; // "IN", "OUT", "UNIVERSAL"
    private String colorHex;
    private int iconResId;
    private boolean custom;

    // 1. Firebase requires an empty constructor. We set safe defaults here!
    public CategoryModel() {
        this.iconResId = R.drawable.ic_category; // Default fallback icon
        this.colorHex = "#78909C"; // Default grey-blue color
        this.custom = true;
    }

    // 2. Full Constructor
    public CategoryModel(String name, String type, String colorHex, int iconResId, boolean custom) {
        this.name = name;
        this.type = type;
        this.colorHex = colorHex;
        this.iconResId = iconResId == 0 ? R.drawable.ic_category : iconResId;
        this.custom = custom;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name != null ? name : "Other"; }
    public String getType() { return type != null ? type : "UNIVERSAL"; }
    public String getColorHex() { return colorHex != null ? colorHex : "#78909C"; }
    public boolean isCustom() { return custom; }

    // THE MOST IMPORTANT FIX: Prevent returning 0 for missing icons
    public int getIconResId() {
        if (iconResId == 0) {
            return R.drawable.ic_category; // Fallback Icon
        }
        return iconResId;
    }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public void setIconResId(int iconResId) { this.iconResId = iconResId; }
    public void setCustom(boolean custom) { this.custom = custom; }
}