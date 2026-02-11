package com.phynix.artham;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.phynix.artham.adapters.CategorySelectionAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryFilterFragment extends BottomSheetDialogFragment {

    private RecyclerView categoriesRecyclerView;
    private EditText searchEditText;
    private LinearLayout noCategoriesLayout;

    private CategorySelectionAdapter adapter;
    private List<CategoryModel> allCategories = new ArrayList<>();
    private List<CategoryModel> filteredCategories = new ArrayList<>();
    private Set<String> selectedCategories = new HashSet<>();

    private DatabaseReference userCategoriesRef;

    // Required empty public constructor
    public CategoryFilterFragment() {}

    // Static factory method if you need to pass data
    public static CategoryFilterFragment newInstance(List<CategoryModel> categories, Set<String> selected) {
        CategoryFilterFragment fragment = new CategoryFilterFragment();
        fragment.allCategories = categories != null ? new ArrayList<>(categories) : new ArrayList<>();
        fragment.selectedCategories = selected != null ? selected : new HashSet<>();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_category_selection, container, false);

        // Map views to IDs in dialog_category_selection.xml
        categoriesRecyclerView = view.findViewById(R.id.recyclerViewCategories);
        searchEditText = view.findViewById(R.id.searchEditText);
        noCategoriesLayout = view.findViewById(R.id.noCategoriesLayout);

        setupRecyclerView();
        setupSearch();

        // If no categories passed via newInstance, load them
        if (allCategories.isEmpty()) {
            loadCategories();
        } else {
            filterCategories(""); // Initialize filtered list with existing data
        }

        return view;
    }

    private void setupRecyclerView() {
        // Use StaggeredGridLayoutManager to avoid Flexbox dependency issues
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL);

        categoriesRecyclerView.setLayoutManager(layoutManager);

        // This requires the updated CategorySelectionAdapter with the (List, Set) constructor
        adapter = new CategorySelectionAdapter(filteredCategories, selectedCategories);
        categoriesRecyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        if (searchEditText == null) return;

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCategories(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCategories() {
        // 1. Load Defaults from Resources
        String[] predefined = getResources().getStringArray(R.array.transaction_categories);
        for (String name : predefined) {
            if (!"Select Category".equals(name) && !"No Category".equals(name)) {
                int color = CategoryColorUtil.getCategoryColor(getContext(), name);
                String hex = String.format("#%06X", (0xFFFFFF & color));
                allCategories.add(new CategoryModel(name, "UNIVERSAL", hex, 0, false));            }
        }

        // 2. Load Custom from Firebase
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(uid).child("categories");

            userCategoriesRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot s : snapshot.getChildren()) {
                        CategoryModel c = s.getValue(CategoryModel.class);
                        if (c != null) {
                            boolean exists = false;
                            for (CategoryModel existing : allCategories) {
                                if (existing.getName().equals(c.getName())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                allCategories.add(c);
                            }
                        }
                    }
                    if (searchEditText != null) {
                        filterCategories(searchEditText.getText().toString());
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }

        filterCategories("");
    }

    private void filterCategories(String query) {
        filteredCategories.clear();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            filteredCategories.addAll(allCategories);
        } else {
            for (CategoryModel cat : allCategories) {
                if (cat.getName().toLowerCase().contains(lowerQuery)) {
                    filteredCategories.add(cat);
                }
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();

        if (noCategoriesLayout != null) {
            noCategoriesLayout.setVisibility(filteredCategories.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (categoriesRecyclerView != null) {
            categoriesRecyclerView.setVisibility(filteredCategories.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}