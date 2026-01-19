package com.phynix.artham;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.models.CategoryModel;

import java.util.Random;

public class CategoryActivity extends AppCompatActivity {

    private ChipGroup categoryChipGroup;
    private DatabaseReference userCategoriesRef;
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);

        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && currentCashbookId != null) {
            // Defaulting to "OUT" (Expense) categories for management.
            // Ideally, pass "IN" or "OUT" via intent if you manage both separately.
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(uid).child("cashbooks")
                    .child(currentCashbookId).child("categories").child("OUT");
        }

        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        findViewById(R.id.fab_add_category).setOnClickListener(v -> showAddDialog(null));

        loadCategories();
    }

    private void loadCategories() {
        if (userCategoriesRef == null) return;
        userCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryChipGroup.removeAllViews();
                for (DataSnapshot s : snapshot.getChildren()) {
                    CategoryModel c = s.getValue(CategoryModel.class);
                    if (c != null) addChip(c);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void addChip(CategoryModel category) {
        // Inflate the custom layout to match your theme
        View view = LayoutInflater.from(this).inflate(R.layout.item_category_chip, categoryChipGroup, false);

        TextView nameView = view.findViewById(R.id.categoryName);
        ImageView iconView = view.findViewById(R.id.categoryIcon);
        ImageView menuView = view.findViewById(R.id.categoryMenu);

        nameView.setText(category.getName());

        // Apply Category Color to Icon
        try {
            int color = Color.parseColor(category.getColorHex());
            iconView.setColorFilter(color);
        } catch (Exception e) {
            iconView.setColorFilter(getThemeColor(R.attr.chk_textColorSecondary));
        }

        // Handle Clicks
        view.setOnClickListener(v -> showAddDialog(category));

        // Setup Menu
        menuView.setVisibility(View.VISIBLE);
        menuView.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(CategoryActivity.this, menuView);
            popup.getMenu().add("Edit");
            popup.getMenu().add("Delete");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Edit")) {
                    showAddDialog(category);
                } else if (item.getTitle().equals("Delete")) {
                    deleteCategory(category);
                }
                return true;
            });
            popup.show();
        });

        categoryChipGroup.addView(view);
    }

    private void showAddDialog(CategoryModel category) {
        boolean isEdit = category != null;

        // [UPDATED] Programmatic View Styling for Theme Compatibility
        EditText input = new EditText(this);
        input.setHint("Category Name");
        input.setBackgroundResource(R.drawable.rounded_input_background);

        // Explicitly set text colors from attributes so it works in Dark Mode
        input.setTextColor(getThemeColor(R.attr.chk_textColorPrimary));
        input.setHintTextColor(getThemeColor(R.attr.chk_textColorHint));

        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        input.setPadding(padding, padding, padding, padding);

        if (isEdit) input.setText(category.getName());

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        container.setPadding(pad, pad, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Category" : "Add Category")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        // Preserve color if editing, otherwise random
                        String color = isEdit ? category.getColorHex() : getRandomColor();

                        // If name changed during edit, remove old key
                        if (isEdit && !category.getName().equals(name)) {
                            userCategoriesRef.child(category.getName()).removeValue();
                        }

                        // Save new/updated category
                        userCategoriesRef.child(name).setValue(new CategoryModel(name, color, true));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategory(CategoryModel c) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete " + c.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> userCategoriesRef.child(c.getName()).removeValue())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getRandomColor() {
        Random r = new Random();
        // Generates bright/pastel colors suitable for dark & light themes
        return String.format("#%02X%02X%02X", r.nextInt(200) + 55, r.nextInt(200) + 55, r.nextInt(200) + 55);
    }

    /**
     * Helper to resolve colors from the current theme attributes.
     */
    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }
}