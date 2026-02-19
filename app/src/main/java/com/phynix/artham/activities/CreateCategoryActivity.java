package com.phynix.artham.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.phynix.artham.R;
import com.phynix.artham.adapters.IconSelectionAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import com.skydoves.colorpickerview.sliders.AlphaSlideBar;

import java.util.Arrays;
import java.util.List;

public class CreateCategoryActivity extends AppCompatActivity {

    private TextInputEditText nameInput;
    private ColorPickerView colorPickerView;
    private View colorPreview;
    private TextView colorHexText, titleText;
    private RecyclerView iconsRecycler;

    private IconSelectionAdapter iconAdapter;
    private DatabaseReference userCategoriesRef;

    private String currentCashbookId;
    private String editCategoryName; // Populated if editing
    private String selectedColorHex = "#FFFFFFFF";
    private String transactionType = "UNIVERSAL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_category);

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        editCategoryName = getIntent().getStringExtra("EDIT_NAME");
        if (getIntent().hasExtra("type")) {
            transactionType = getIntent().getStringExtra("type");
        }

        setupFirebase();
        initViews();
        setupColorPicker();
        setupIconList();

        if (editCategoryName != null) {
            titleText.setText("Edit Category");
            nameInput.setText(editCategoryName);
            // In a real scenario, you'd fetch existing color/icon here
        }
    }

    private void setupFirebase() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && currentCashbookId != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(uid).child("cashbooks")
                    .child(currentCashbookId).child("categories")
                    .child(transactionType);
        }
    }

    private void initViews() {
        nameInput = findViewById(R.id.categoryNameInput);
        colorPickerView = findViewById(R.id.colorPickerView);
        colorPreview = findViewById(R.id.colorPreview);
        colorHexText = findViewById(R.id.colorHexText);
        titleText = findViewById(R.id.titleText);
        iconsRecycler = findViewById(R.id.iconsRecyclerView);

        AlphaSlideBar alphaSlideBar = findViewById(R.id.alphaSlideBar);
        colorPickerView.attachAlphaSlider(alphaSlideBar);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.saveButton).setOnClickListener(v -> saveCategory());
    }

    private void setupColorPicker() {
        colorPickerView.setColorListener((ColorEnvelopeListener) (envelope, fromUser) -> {
            selectedColorHex = "#" + envelope.getHexCode();
            colorHexText.setText(selectedColorHex);
            colorPreview.setBackgroundColor(envelope.getColor());
        });
    }

    private void setupIconList() {
        List<Integer> icons = Arrays.asList(
                R.drawable.ic_category, R.drawable.ic_food_dining, R.drawable.ic_transportation,
                R.drawable.ic_home, R.drawable.ic_entertainment, R.drawable.ic_receipt,
                R.drawable.ic_medicine, R.drawable.ic_book, R.drawable.ic_money
        );

        iconAdapter = new IconSelectionAdapter(this, icons);
        iconsRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        iconsRecycler.setAdapter(iconAdapter);
    }

    private void saveCategory() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Name required");
            return;
        }

        if (userCategoriesRef == null) {
            Toast.makeText(this, "Error: Reference missing", Toast.LENGTH_SHORT).show();
            return;
        }

        CategoryModel category = new CategoryModel(name, transactionType, selectedColorHex, iconAdapter.getSelectedIcon(), true);

        // If name changed during edit, delete old entry
        if (editCategoryName != null && !editCategoryName.equals(name)) {
            userCategoriesRef.child(editCategoryName).removeValue();
        }

        userCategoriesRef.child(name).setValue(category)
                .addOnSuccessListener(aVoid -> finish())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show());
    }
}