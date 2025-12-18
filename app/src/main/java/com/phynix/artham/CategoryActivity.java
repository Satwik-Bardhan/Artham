package com.phynix.artham;

import android.os.Bundle;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
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
        setContentView(R.layout.activity_category);

        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && currentCashbookId != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(uid).child("cashbooks")
                    .child(currentCashbookId).child("categories").child("OUT"); // Defaulting to OUT for management
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
        Chip chip = new Chip(this);
        chip.setText(category.getName());
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> deleteCategory(category));
        chip.setOnClickListener(v -> showAddDialog(category));
        categoryChipGroup.addView(chip);
    }

    private void showAddDialog(CategoryModel category) {
        boolean isEdit = category != null;
        EditText input = new EditText(this);
        input.setHint("Category Name");
        if (isEdit) input.setText(category.getName());

        LinearLayout container = new LinearLayout(this);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        container.setPadding(pad, pad, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit" : "Add")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        String color = isEdit ? category.getColorHex() : getRandomColor();
                        if (isEdit && !category.getName().equals(name)) {
                            userCategoriesRef.child(category.getName()).removeValue();
                        }
                        userCategoriesRef.child(name).setValue(new CategoryModel(name, color, true));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategory(CategoryModel c) {
        new AlertDialog.Builder(this).setMessage("Delete " + c.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> userCategoriesRef.child(c.getName()).removeValue())
                .show();
    }

    private String getRandomColor() {
        Random r = new Random();
        return String.format("#%02X%02X%02X", r.nextInt(200)+55, r.nextInt(200)+55, r.nextInt(200)+55);
    }
}