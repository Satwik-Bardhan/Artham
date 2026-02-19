package com.phynix.artham.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.phynix.artham.R;

@IgnoreExtraProperties
public class CategoryModel {

    private String id;
    private String name;
    private String type; // "IN", "OUT", or "UNIVERSAL"
    private String colorHex; // Store full ARGB Hex (e.g., #80FF5722)
    private int iconResId;
    private boolean custom; // True if user-created, False if predefined

    // Required empty constructor for Firebase
    public CategoryModel() {}

    /**
     * NEW: Constructor used for auto-seeding default categories
     * This fixes the "no suitable constructor found" error.
     */
    public CategoryModel(String name, String colorHex, String type) {
        this.name = name;
        this.colorHex = colorHex;
        this.type = type;
        this.iconResId = R.drawable.ic_category; // Default category icon
        this.custom = false; // Defaults are not user-custom
    }

    /**
     * Standard constructor for creating categories
     * @param name Name of the category
     * @param type Transaction type (IN/OUT/UNIVERSAL)
     * @param colorHex Color in Hex format
     * @param iconResId Resource ID of the icon
     * @param custom Whether it is a user-created category
     */
    public CategoryModel(String name, String type, String colorHex, int iconResId, boolean custom) {
        this.name = name;
        this.type = type;
        this.colorHex = colorHex;
        this.iconResId = iconResId;
        this.custom = custom;
    }

    // Secondary constructor for simple initialization
    public CategoryModel(String name, String type) {
        this.name = name;
        this.type = type;
        this.colorHex = "#9E9E9E"; // Default Grey
        this.iconResId = R.drawable.ic_category;
        this.custom = false;
    }

    // Getters and Setters
    @Exclude
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

    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }
}