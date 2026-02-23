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
    private boolean custom; // True if user-created, False if predefined default

    // Required empty constructor for Firebase DataSnapshot deserialization
    public CategoryModel() {}

    /**
     * Constructor specifically for auto-seeding Default Categories.
     * Enforces custom = false.
     * * @param name Name of the category
     * @param colorHex Color in Hex format mapping to the pie chart/legend
     * @param type Transaction type (IN/OUT/UNIVERSAL)
     * @param iconResId Specific icon resource ID for this default category
     */
    public CategoryModel(String name, String colorHex, String type, int iconResId) {
        this.name = name;
        this.colorHex = colorHex;
        this.type = type;
        this.iconResId = iconResId;
        this.custom = false; // Strictly a system default
    }

    /**
     * Constructor for User-Created Custom Categories.
     * * @param name Name of the category
     * @param type Transaction type (IN/OUT/UNIVERSAL)
     * @param colorHex Color chosen by the user in Hex format
     * @param iconResId Resource ID of the icon chosen by the user
     * @param custom Whether it is a user-created category (should be true here)
     */
    public CategoryModel(String name, String type, String colorHex, int iconResId, boolean custom) {
        this.name = name;
        this.type = type;
        this.colorHex = colorHex;
        this.iconResId = iconResId;
        this.custom = custom;
    }

    // Secondary simplified constructor
    public CategoryModel(String name, String type) {
        this.name = name;
        this.type = type;
        this.colorHex = "#9E9E9E"; // Theme-safe fallback Grey
        this.iconResId = R.drawable.ic_category;
        this.custom = true; // Assumed user-created if using basic constructor
    }

    // --- Getters and Setters ---

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