package com.phynix.artham.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.phynix.artham.R;
import com.phynix.artham.adapters.CategoryIconAdapter;
import com.phynix.artham.adapters.ColorSelectionAdapter;
import com.phynix.artham.models.CategoryModel;

import java.util.Arrays;
import java.util.List;

public class QuickAddCategoryDialog extends Dialog {

    private EditText inputCategoryName;
    private View previewCircle;
    private RecyclerView recyclerColors;
    private RecyclerView recyclerIcons;
    private TextView btnCancel, btnSave;

    private String selectedHexColor = "#FF5252"; // Default Red
    private int selectedIconResId = R.drawable.ic_category; // Default Icon
    private DatabaseReference categoryRef;
    private OnCategoryAddedListener listener;

    public interface OnCategoryAddedListener {
        void onCategoryAdded(CategoryModel newCategory);
    }

    public QuickAddCategoryDialog(@NonNull Context context, OnCategoryAddedListener listener) {
        super(context);
        this.listener = listener;

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            categoryRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(userId).child("categories");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_add_category);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        initViews();
        setupColorRecycler();
        setupIconRecycler();
        updatePreview();
        setupListeners();
    }

    private void initViews() {
        inputCategoryName = findViewById(R.id.inputCategoryName);
        previewCircle = findViewById(R.id.previewCircle);
        recyclerColors = findViewById(R.id.recyclerColors);
        recyclerIcons = findViewById(R.id.recyclerIcons);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
    }

    private void setupColorRecycler() {
        // A nice default palette for quick selection
        List<String> presetColors = Arrays.asList(
                "#FF5252", "#FF4081", "#E040FB", "#7C4DFF",
                "#536DFE", "#448AFF", "#40C4FF", "#18FFFF",
                "#64FFDA", "#69F0AE", "#B2FF59", "#EEFF41",
                "#FFFF00", "#FFD740", "#FFAB40", "#FF6E40"
        );
        selectedHexColor = presetColors.get(0);

        ColorSelectionAdapter colorAdapter = new ColorSelectionAdapter(presetColors, hexColor -> {
            selectedHexColor = hexColor;
            updatePreview();
        });
        recyclerColors.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerColors.setAdapter(colorAdapter);
    }

    private void setupIconRecycler() {
        List<Integer> presetIcons = Arrays.asList(
                R.drawable.ic_category, R.drawable.ic_food_dining, R.drawable.ic_transportation,
                R.drawable.ic_shopping_cart, R.drawable.ic_home, R.drawable.ic_entertainment,
                R.drawable.ic_medicine, R.drawable.ic_book, R.drawable.ic_work, R.drawable.ic_flight
        );
        selectedIconResId = presetIcons.get(0);

        CategoryIconAdapter iconAdapter = new CategoryIconAdapter(presetIcons, iconResId -> {
            selectedIconResId = iconResId;
            updatePreview();
        });
        recyclerIcons.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerIcons.setAdapter(iconAdapter);
    }

    private void updatePreview() {
        try {
            previewCircle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(selectedHexColor)));
        } catch (Exception ignored) {}
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> dismiss());

        btnSave.setOnClickListener(v -> {
            String catName = inputCategoryName.getText().toString().trim();
            if (catName.isEmpty()) {
                inputCategoryName.setError("Name is required");
                return;
            }

            saveCategory(catName);
        });
    }

    private void saveCategory(String name) {
        if (categoryRef == null) return;

        CategoryModel newCategory = new CategoryModel(
                name,
                "OUT", // Defaulting to expense
                selectedHexColor,
                selectedIconResId,
                true // Requirement #4: Marking as custom user-created category
        );

        String id = categoryRef.push().getKey();
        if (id != null) {
            newCategory.setId(id);
            categoryRef.child(id).setValue(newCategory).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (listener != null) listener.onCategoryAdded(newCategory);
                    dismiss();
                } else {
                    Toast.makeText(getContext(), "Failed to save category", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}