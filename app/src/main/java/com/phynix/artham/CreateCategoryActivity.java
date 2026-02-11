package com.phynix.artham;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.adapters.ColorSelectionAdapter;
import com.phynix.artham.adapters.IconSelectionAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;

import java.util.Arrays;
import java.util.List;

public class CreateCategoryActivity extends AppCompatActivity {

    private TextInputEditText nameInput;
    private RecyclerView iconsRecycler, colorsRecycler;
    private IconSelectionAdapter iconAdapter;
    private ColorSelectionAdapter colorAdapter;
    private TextView titleText;

    private DatabaseReference userCategoriesRef;
    private String currentCashbookId;
    private String editCategoryName; // Will be null if creating new, populated if editing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_category);

        // Retrieve Intents
        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        editCategoryName = getIntent().getStringExtra("EDIT_NAME");

        String uid = FirebaseAuth.getInstance().getUid();

        // 1. Initialize Firebase Reference (Universal path for both IN and OUT)
        if (uid != null) {
            if (currentCashbookId != null && !currentCashbookId.isEmpty()) {
                userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(uid).child("cashbooks")
                        .child(currentCashbookId).child("categories");
            } else {
                userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(uid).child("categories");
            }
        }

        initViews();
        setupLists();

        // 2. Handle Edit Mode
        if (editCategoryName != null && !editCategoryName.isEmpty()) {
            titleText.setText("Edit Category");
            nameInput.setText(editCategoryName);
            // Optionally fetch existing color/icon from Firebase here to pre-select them in the adapters
        }
    }

    private void initViews() {
        nameInput = findViewById(R.id.categoryNameInput);
        iconsRecycler = findViewById(R.id.iconsRecyclerView);
        colorsRecycler = findViewById(R.id.colorsRecyclerView);
        titleText = findViewById(R.id.titleText); // Ensure you add android:id="@+id/titleText" to the title TextView in XML

        Button saveButton = findViewById(R.id.saveButton);
        ImageView backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveCategoryToFirebase());
    }

    private void setupLists() {
        // List of available Icons (Make sure these drawables exist in your project)
        List<Integer> icons = Arrays.asList(
                R.drawable.ic_category, R.drawable.ic_food_dining, R.drawable.ic_transportation,
                R.drawable.ic_home, R.drawable.ic_entertainment, R.drawable.ic_receipt,
                R.drawable.ic_medicine, R.drawable.ic_book, R.drawable.ic_money
        );

        // List of available Hex Colors converted to Int
        List<Integer> colors = Arrays.asList(
                0xFFFF7043, 0xFF26A69A, 0xFFAB47BC, 0xFF29B6F6,
                0xFFFFA726, 0xFFEC407A, 0xFFEF5350, 0xFF5C6BC0,
                0xFF8BC34A, 0xFF009688
        );

        iconAdapter = new IconSelectionAdapter(this, icons);
        iconsRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        iconsRecycler.setAdapter(iconAdapter);

        colorAdapter = new ColorSelectionAdapter(this, colors);
        colorsRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        colorsRecycler.setAdapter(colorAdapter);
    }

    private void saveCategoryToFirebase() {
        String name = nameInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Category name is required");
            nameInput.requestFocus();
            return;
        }

        if (userCategoriesRef == null) {
            Toast.makeText(this, "Authentication error. Cannot save.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Convert selected color Int to Hex String (e.g., "#FF5722")
        String colorHex = String.format("#%06X", (0xFFFFFF & colorAdapter.getSelectedColor()));
        int iconResId = iconAdapter.getSelectedIcon();

        // 4. Create the Category Model (Type = "UNIVERSAL", isCustom = true)
        CategoryModel newCat = new CategoryModel(name, "UNIVERSAL", colorHex, iconResId, true);
        newCat.setId(name); // Using the name as the ID

        // 5. If editing and the name changed, we must delete the old Firebase node first
        if (editCategoryName != null && !editCategoryName.equals(name)) {
            userCategoriesRef.child(editCategoryName).removeValue();
        }

        // 6. Save to Firebase
        userCategoriesRef.child(name).setValue(newCat)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Category Saved successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save category", Toast.LENGTH_SHORT).show();
                });
    }
}