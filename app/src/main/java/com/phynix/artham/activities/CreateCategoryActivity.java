package com.phynix.artham.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.R;
import com.phynix.artham.adapters.CategoryIconAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.ThemeManager;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import com.skydoves.colorpickerview.sliders.AlphaSlideBar;

import java.util.ArrayList;
import java.util.List;

public class CreateCategoryActivity extends AppCompatActivity {

    private ImageView backButton;
    private TextView titleText;
    private TextInputEditText categoryNameInput;
    private ColorPickerView colorPickerView;
    private AlphaSlideBar alphaSlideBar;
    private View colorPreview;
    private TextView colorHexText;
    private RecyclerView iconsRecyclerView;
    private MaterialButton saveButton;

    private DatabaseReference categoryRef;
    private String categoryId = null; // Null means create new, otherwise edit existing
    private String selectedHexColor = "#FF5252"; // Default fallback
    private int selectedIconResId = R.drawable.ic_category; // Default icon

    private CategoryIconAdapter iconAdapter;
    private List<Integer> availableIcons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply the theme (Dark, Light, or Purple) BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_category);

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            categoryRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(userId).child("categories");
        }

        if (getIntent() != null && getIntent().hasExtra("CATEGORY_ID")) {
            categoryId = getIntent().getStringExtra("CATEGORY_ID");
        }

        initViews();
        setupColorPicker();
        setupIconRecyclerView();

        if (categoryId != null) {
            titleText.setText("Edit Category");
            loadExistingCategory();
        }

        setupListeners();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        titleText = findViewById(R.id.titleText);
        categoryNameInput = findViewById(R.id.categoryNameInput);
        colorPickerView = findViewById(R.id.colorPickerView);
        alphaSlideBar = findViewById(R.id.alphaSlideBar);
        colorPreview = findViewById(R.id.colorPreview);
        colorHexText = findViewById(R.id.colorHexText);
        iconsRecyclerView = findViewById(R.id.iconsRecyclerView);
        saveButton = findViewById(R.id.saveButton);

        // Attach alpha slider to color picker
        colorPickerView.attachAlphaSlider(alphaSlideBar);
    }

    private void setupColorPicker() {
        colorPickerView.setColorListener(new ColorEnvelopeListener() {
            @Override
            public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
                selectedHexColor = "#" + envelope.getHexCode();
                colorHexText.setText(selectedHexColor);
                colorPreview.setBackgroundTintList(ColorStateList.valueOf(envelope.getColor()));
            }
        });
    }

    private void setupIconRecyclerView() {
        availableIcons = new ArrayList<>();
        // Comprehensive list of app icons
        availableIcons.add(R.drawable.ic_category);
        availableIcons.add(R.drawable.ic_food_dining);
        availableIcons.add(R.drawable.ic_transportation);
        availableIcons.add(R.drawable.ic_shopping_cart);
        availableIcons.add(R.drawable.ic_home);
        availableIcons.add(R.drawable.ic_entertainment);
        availableIcons.add(R.drawable.ic_medicine);
        availableIcons.add(R.drawable.ic_book);
        availableIcons.add(R.drawable.ic_work);
        availableIcons.add(R.drawable.ic_flight);
        availableIcons.add(R.drawable.ic_close);
        availableIcons.add(R.drawable.ic_delete);
        availableIcons.add(R.drawable.ic_image);
        availableIcons.add(R.drawable.ic_language);
        availableIcons.add(R.drawable.ic_note);
        availableIcons.add(R.drawable.ic_schedule);
        availableIcons.add(R.drawable.ic_share);
        availableIcons.add(R.drawable.ic_storage);
        availableIcons.add(R.drawable.ic_theme);
        availableIcons.add(R.drawable.ic_toggle);
        availableIcons.add(R.drawable.ic_tune);
        availableIcons.add(R.drawable.ic_account_balance);
        availableIcons.add(R.drawable.ic_all_inclusive);
        availableIcons.add(R.drawable.ic_attach_file);
        availableIcons.add(R.drawable.ic_coins_outline);
        availableIcons.add(R.drawable.ic_date_range);
        availableIcons.add(R.drawable.ic_empty_state);
        availableIcons.add(R.drawable.ic_expand_more);
        availableIcons.add(R.drawable.ic_group_outline);
        availableIcons.add(R.drawable.ic_qr_code);
        availableIcons.add(R.drawable.ic_receipt_outline);
        availableIcons.add(R.drawable.ic_receipt);
        availableIcons.add(R.drawable.ic_star_outline);
        availableIcons.add(R.drawable.ic_your_profile);
        availableIcons.add(R.drawable.ic_about_info);
        availableIcons.add(R.drawable.ic_account_balance_wallet);
        availableIcons.add(R.drawable.ic_app_settings);
        availableIcons.add(R.drawable.ic_assignment_return);
        availableIcons.add(R.drawable.ic_bar_graph);
        availableIcons.add(R.drawable.ic_block);
        availableIcons.add(R.drawable.ic_calculator);
        availableIcons.add(R.drawable.ic_camera);
        availableIcons.add(R.drawable.ic_card_giftcard);
        availableIcons.add(R.drawable.ic_pie_chart);
        availableIcons.add(R.drawable.ic_filter);
        availableIcons.add(R.drawable.ic_credit_card);
        availableIcons.add(R.drawable.ic_excel);
        availableIcons.add(R.drawable.ic_edit);
        availableIcons.add(R.drawable.ic_label);
        availableIcons.add(R.drawable.ic_exit_to_app);
        availableIcons.add(R.drawable.ic_location);

        iconAdapter = new CategoryIconAdapter(availableIcons, iconResId -> selectedIconResId = iconResId);

        // Use GridLayoutManager with 5 columns to match the updated XML layout
        iconsRecyclerView.setLayoutManager(new GridLayoutManager(this, 5));
        iconsRecyclerView.setAdapter(iconAdapter);
    }

    private void loadExistingCategory() {
        if (categoryRef == null || categoryId == null) return;

        categoryRef.child(categoryId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                CategoryModel model = snapshot.getValue(CategoryModel.class);
                if (model != null) {
                    categoryNameInput.setText(model.getName());
                    selectedHexColor = model.getColorHex();
                    selectedIconResId = model.getIconResId();

                    colorHexText.setText(selectedHexColor);
                    try {
                        colorPreview.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(selectedHexColor)));
                    } catch (Exception ignored) {
                    }

                    int iconIndex = availableIcons.indexOf(selectedIconResId);
                    if (iconIndex != -1) {
                        iconAdapter.setSelectedIndex(iconIndex);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CreateCategoryActivity.this, "Failed to load category", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> onBackPressed());

        saveButton.setOnClickListener(v -> {
            String catName = categoryNameInput.getText() != null ? categoryNameInput.getText().toString().trim() : "";

            if (catName.isEmpty()) {
                categoryNameInput.setError("Category name is required");
                return;
            }

            saveCategoryToFirebase(catName);
        });
    }

    private void saveCategoryToFirebase(String name) {
        if (categoryRef == null) return;

        // Note: Defaulting to "OUT" (Expense) for custom categories, or you can add a toggle in UI.
        CategoryModel newCategory = new CategoryModel(
                name,
                "OUT",
                selectedHexColor,
                selectedIconResId,
                true // Requirement #4: Mark as user-created
        );

        if (categoryId == null) {
            // Create new
            String id = categoryRef.push().getKey();
            if (id != null) {
                newCategory.setId(id);
                categoryRef.child(id).setValue(newCategory)
                        .addOnSuccessListener(aVoid -> finishWithSuccess("Category Created"))
                        .addOnFailureListener(e -> showError("Failed to create category"));
            }
        } else {
            // Update existing
            newCategory.setId(categoryId);
            categoryRef.child(categoryId).setValue(newCategory)
                    .addOnSuccessListener(aVoid -> finishWithSuccess("Category Updated"))
                    .addOnFailureListener(e -> showError("Failed to update category"));
        }
    }

    private void finishWithSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}