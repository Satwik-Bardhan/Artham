package com.phynix.artham.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.phynix.artham.auth.AuthManager;
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
import com.phynix.artham.BaseActivity;

public class CreateCategoryActivity extends BaseActivity {

    private ImageView backButton;
    private TextView titleText;
    private TextInputEditText categoryNameInput;
    private ColorPickerView colorPickerView;
    private AlphaSlideBar alphaSlideBar;
    private View colorPreview;
    private TextView colorHexText;
    private RecyclerView iconsRecyclerView;
    private MaterialButton saveButton;
    private ImageView editHexButton;

    // private DatabaseReference categoryRef;
    private String categoryId = null; // Null means create new, otherwise edit existing
    private String selectedHexColor = "#FF5252"; // Default fallback
    private int selectedIconResId = R.drawable.ic_category; // Default icon
    private String categoryType = "UNIVERSAL";

    private CategoryIconAdapter iconAdapter;
    private List<Integer> availableIcons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply the theme (Dark, Light, or Purple) BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_category);

        String userId = AuthManager.getUserId(this);
        // Firebase initialization removed

        if (getIntent() != null) {
            if (getIntent().hasExtra("CATEGORY_ID")) {
                categoryId = getIntent().getStringExtra("CATEGORY_ID");
            }
            if (getIntent().hasExtra("type")) {
                categoryType = getIntent().getStringExtra("type");
            }
        }

        initViews();
        setupColorPicker();
        setupIconRecyclerView();

        if (categoryId != null) {
            titleText.setText("Edit Category");
            loadExistingCategory();
        } else {
            String[] randomPalette = {
                    "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
                    "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
                    "#FFC107", "#FF9800", "#FF5722", "#795548"
            };
            int randomIndex = new java.util.Random().nextInt(randomPalette.length);
            selectedHexColor = randomPalette[randomIndex];
        }

        setupListeners();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        titleText = findViewById(R.id.headerTitle);
        categoryNameInput = findViewById(R.id.categoryNameInput);
        colorPickerView = findViewById(R.id.colorPickerView);
        alphaSlideBar = findViewById(R.id.alphaSlideBar);
        colorPreview = findViewById(R.id.colorPreview);
        colorHexText = findViewById(R.id.colorHexText);
        iconsRecyclerView = findViewById(R.id.iconsRecyclerView);
        saveButton = findViewById(R.id.saveButton);
        editHexButton = findViewById(R.id.editHexButton);

        // Attach alpha slider to color picker
        colorPickerView.attachAlphaSlider(alphaSlideBar);

        // Setup manual hex code edit button
        if (editHexButton != null) {
            editHexButton.setOnClickListener(v -> showHexInputDialog());
        }
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

        try {
            colorPickerView.setInitialColor(Color.parseColor(selectedHexColor));
        } catch (Exception ignored) {
        }
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
        availableIcons.add(R.drawable.ic_groceries);
        availableIcons.add(R.drawable.ic_utilities);
        availableIcons.add(R.drawable.ic_subscriptions);
        availableIcons.add(R.drawable.ic_security);
        availableIcons.add(R.drawable.ic_auto_repeat);
        availableIcons.add(R.drawable.ic_file_download);
        availableIcons.add(R.drawable.ic_receipt_long);
        availableIcons.add(R.drawable.ic_shuffle);
        availableIcons.add(R.drawable.ic_vibration);
        availableIcons.add(R.drawable.ic_widget_cash_in);
        availableIcons.add(R.drawable.ic_widget_cash_out);
        availableIcons.add(R.drawable.ic_net_balance);

        iconAdapter = new CategoryIconAdapter(availableIcons, iconResId -> selectedIconResId = iconResId);

        // Use horizontal GridLayoutManager with 3 rows for left-right scrolling
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false);
        iconsRecyclerView.setLayoutManager(gridLayoutManager);
        iconsRecyclerView.setAdapter(iconAdapter);
    }

    private void loadExistingCategory() {
        // Firebase logic removed
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

    private String targetCashbookId;

    private void saveCategoryToFirebase(String name) {
        if (targetCashbookId == null || targetCashbookId.isEmpty()) {
            android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
            String uid = AuthManager.getUserId(this);
            targetCashbookId = prefs.getString("active_cashbook_id_" + uid, getIntent().getStringExtra("cashbook_id"));
        }

        if (targetCashbookId == null || targetCashbookId.isEmpty()) {
            Toast.makeText(this, "No cashbook selected", Toast.LENGTH_SHORT).show();
            return;
        }

        CategoryModel category = new CategoryModel();
        category.setName(name);
        category.setType(categoryType != null ? categoryType : "Expense");
        category.setColorHex(selectedHexColor != null ? selectedHexColor : "#FF5722");
        category.setIconResId(selectedIconResId != 0 ? selectedIconResId : R.drawable.ic_category);
        category.setCustom(true);

        com.phynix.artham.db.DataRepository.getInstance(getApplication()).addCategory(targetCashbookId, category, success -> {
            if (!isAlive()) return;
            finishWithSuccess("Category saved successfully");
        });
    }

    private void finishWithSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showHexInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Hex Color Code");

        // Create input field
        final EditText hexInput = new EditText(this);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hexInput.setHint("e.g. #FF5252");
        hexInput.setText(selectedHexColor);
        hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)}); // Max #AARRGGBB
        hexInput.setSelection(hexInput.getText().length());
        hexInput.setTextColor(colorHexText.getCurrentTextColor());

        // Wrap in a FrameLayout for padding
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) (20 * getResources().getDisplayMetrics().density);
        params.setMargins(margin, margin / 2, margin, 0);
        hexInput.setLayoutParams(params);
        container.addView(hexInput);

        builder.setView(container);

        builder.setPositiveButton("Apply", (dialog, which) -> {
            String hex = hexInput.getText().toString().trim();
            if (!hex.startsWith("#")) {
                hex = "#" + hex;
            }
            try {
                int color = Color.parseColor(hex);
                selectedHexColor = hex;
                colorHexText.setText(selectedHexColor);
                colorPreview.setBackgroundTintList(ColorStateList.valueOf(color));
            } catch (Exception e) {
                Toast.makeText(this, "Invalid hex color code", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}